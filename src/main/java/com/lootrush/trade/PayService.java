package com.lootrush.trade;

import com.lootrush.common.ApiResponse;
import com.lootrush.common.BizException;
import com.lootrush.goods.GoodsMapper;
import com.lootrush.rank.RankService;
import com.lootrush.user.UserMapper;
import com.lootrush.user.WalletFlowEntity;
import com.lootrush.user.WalletFlowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 支付回调处理（技术总表场景8/9 的核心）：验签 → 记通知账 → 幂等 → 状态机 → 钱包事务。
 *
 * 幂等三件套：
 * 1. 唯一通知号：pay_notify_log.notify_no 唯一索引——同一条通知重发 N 次只进一次；
 * 2. 状态机守卫：只有 CREATED(0)→PAID(1) 这一条边可走，重复回调 UPDATE 影响 0 行即拒绝；
 * 3. 钱包事务：扣款/入账与状态变更同事务，失败一起回滚，绝不出现"扣了钱订单没付"。
 * （发货 DELIVER_GOODS 消息属循环5，本循环回调链路止于"订单已支付+钱包已动"。）
 */
@Service
public class PayService {

    public static final String RESULT_PROCESSED = "PROCESSED";
    public static final String RESULT_BAD_SIGN = "BAD_SIGN";
    public static final String RESULT_NO_ORDER = "NO_ORDER";
    public static final String RESULT_AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String RESULT_DUP_STATE = "DUP_STATE";
    public static final String RESULT_NO_BALANCE = "NO_BALANCE";

    private static final Logger log = LoggerFactory.getLogger(PayService.class);

    private final String paySecret;
    private final OrderMapper orderMapper;
    private final PayNotifyLogMapper notifyMapper;
    private final UserMapper userMapper;
    private final WalletFlowMapper walletFlowMapper;
    private final GoodsMapper goodsMapper;
    private final RocketMQTemplate mqTemplate;
    private final RankService rankService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PayService(@Value("${pay.secret}") String paySecret,
                      OrderMapper orderMapper, PayNotifyLogMapper notifyMapper,
                      UserMapper userMapper, WalletFlowMapper walletFlowMapper,
                      GoodsMapper goodsMapper, RocketMQTemplate mqTemplate,
                      RankService rankService) {
        this.paySecret = paySecret;
        this.orderMapper = orderMapper;
        this.notifyMapper = notifyMapper;
        this.userMapper = userMapper;
        this.walletFlowMapper = walletFlowMapper;
        this.goodsMapper = goodsMapper;
        this.mqTemplate = mqTemplate;
        this.rankService = rankService;
    }

    /** 回调请求体（支付公司 POST 过来的） */
    public record PayCallbackReq(String notifyNo, String orderNo, int amount, String sign) {
    }

    @Transactional
    public ApiResponse<Void> processCallback(PayCallbackReq req) {
        // 1. 验签：签名不对的根本不是"我们公司的通知"
        String data = req.orderNo() + "|" + req.amount() + "|" + req.notifyNo();
        if (!com.lootrush.trade.callback.HmacUtil.verify(paySecret, data, req.sign())) {
            log.warn("回调验签失败 orderNo={} notifyNo={}", req.orderNo(), req.notifyNo());
            recordQuietly(req, RESULT_BAD_SIGN);
            return ApiResponse.error(401, "验签失败");
        }

        // 2. 唯一通知号记账：插不进去 = 这条通知已经来过（幂等第一道）
        PayNotifyLogEntity logRow = new PayNotifyLogEntity();
        logRow.setNotifyNo(req.notifyNo());
        logRow.setOrderNo(req.orderNo());
        logRow.setAmount(req.amount());
        logRow.setResult("PENDING");
        try {
            notifyMapper.insert(logRow);
        } catch (DuplicateKeyException e) {
            log.info("重复通知，幂等忽略 notifyNo={}", req.notifyNo());
            return ApiResponse.ok(); // 告知支付公司"收到了"，它就不会一直重试
        }

        // 3. 订单存在性
        OrderEntity order = orderMapper.selectByOrderNo(req.orderNo());
        if (order == null) {
            notifyMapper.updateResult(req.notifyNo(), RESULT_NO_ORDER);
            return ApiResponse.error(404, "订单不存在");
        }

        // 4. 金额一致性：通知金额必须和订单应付一致（防篡改/防串单）
        if (req.amount() != order.getPriceDiamond()) {
            notifyMapper.updateResult(req.notifyNo(), RESULT_AMOUNT_MISMATCH);
            return ApiResponse.error(400, "回调金额与订单不符");
        }

        // 5. 状态机守卫：只有 0(待支付)→1(已支付) 一条边。重复回调 UPDATE 0 行 → 幂等忽略
        if (orderMapper.markPaidIfCreated(req.orderNo()) == 0) {
            notifyMapper.updateResult(req.notifyNo(), RESULT_DUP_STATE);
            log.info("订单已支付过，状态机拦下重复回调 orderNo={}", req.orderNo());
            return ApiResponse.ok();
        }

        // 6. 钱包事务：与状态变更同事务
        if ("RECHARGE".equals(order.getType())) {
            userMapper.addDiamond(order.getUserId(), order.getPriceDiamond());
            writeFlow(order.getUserId(), "RECHARGE", order.getPriceDiamond(), order.getOrderNo());
        } else {
            if (userMapper.deductDiamond(order.getUserId(), order.getPriceDiamond()) == 0) {
                // 事务回滚：状态变更一并撤销，订单回到待支付，留给关单循环去关闭并回补库存
                notifyMapper.updateResult(req.notifyNo(), RESULT_NO_BALANCE);
                throw BizException.of(400, "钻石余额不足，无法完成支付");
            }
            writeFlow(order.getUserId(), "PURCHASE", -order.getPriceDiamond(), order.getOrderNo());
            rankService.recordAfterCommit(order.getUserId(), order.getPriceDiamond()); // 消费计分（提交后生效）
            scheduleDeliveryAfterCommit(order);
        }

        notifyMapper.updateResult(req.notifyNo(), RESULT_PROCESSED);
        log.info("回调处理成功 orderNo={} type={} amount={}", req.orderNo(), order.getType(), req.amount());
        return ApiResponse.ok();
    }

    /**
     * 发货消息必须在【本事务成功提交之后】才发出去。
     * 若在事务内先发：消息发完、事务却回滚 → 消费者会为一笔"没付成功"的订单发货。
     * 注册 afterCommit 回调：提交后才发；万一发送失败（网络），支付已安全落地，
     * 发货缺口交给对账循环兜底——这就是"事务性发件箱"思想的朴素实现。
     */
    private void scheduleDeliveryAfterCommit(OrderEntity order) {
        Long itemId = goodsMapper.selectById(order.getGoodsId()).getItemId();
        String json;
        try {
            json = objectMapper.writeValueAsString(
                    new DeliverMsg(order.getOrderNo(), order.getUserId(), itemId, 1));
        } catch (Exception e) {
            throw new IllegalStateException("发货消息序列化失败", e);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mqTemplate.syncSend(DeliverMsg.TOPIC, MessageBuilder.withPayload(json).build());
                    log.info("发货消息已发送 orderNo={}", order.getOrderNo());
                } catch (Exception e) {
                    log.error("发货消息发送失败（对账循环会兜底） orderNo={}", order.getOrderNo(), e);
                }
            }
        });
    }

    private void writeFlow(Long userId, String type, int amount, String orderNo) {
        WalletFlowEntity flow = new WalletFlowEntity();
        flow.setUserId(userId);
        flow.setChangeType(type);
        flow.setAmount(amount);
        flow.setBalanceAfter(userMapper.selectById(userId).getDiamond());
        flow.setRefOrderNo(orderNo);
        walletFlowMapper.insert(flow);
    }

    /** 记账失败不影响主流程返回（证据表尽力而为；核心幂等靠唯一索引本身） */
    private void recordQuietly(PayCallbackReq req, String result) {
        try {
            PayNotifyLogEntity row = new PayNotifyLogEntity();
            row.setNotifyNo(req.notifyNo());
            row.setOrderNo(req.orderNo());
            row.setAmount(req.amount());
            row.setResult(result);
            notifyMapper.insert(row);
        } catch (DuplicateKeyException ignored) {
        }
    }
}
