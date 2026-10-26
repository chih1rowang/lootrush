package com.lootrush.trade;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * ORDER_CREATED 消费者：把抢购成功的订单真正写进数据库（削峰后的匀速写入端）。
 *
 * 幂等与铁闸：
 * - uk_order_no 冲突 = 同一条消息被重复投递 → 直接忽略（消费幂等）；
 * - uk_user_goods_active 冲突 = 越过了 Redis 判重的第二人（判重标记失效等极端情况）
 *   → 数据库唯一索引拦下，并补偿 Redis（库存+1、删判重标记）。
 * 消息体用 String 传输 + tools.jackson 手动序列化：绕开 rocketmq-spring 内置转换器
 * 对 Jackson2 的依赖（Boot 4 类路径上是 Jackson3），这条链路已在兼容性测试中验证。
 */
@Component
@RocketMQMessageListener(topic = PurchaseService.TOPIC_ORDER_CREATED, consumerGroup = "lootrush-order-group")
public class OrderCreatedListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final OrderMapper orderMapper;
    private final PurchaseService purchaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderCreatedListener(OrderMapper orderMapper, PurchaseService purchaseService) {
        this.orderMapper = orderMapper;
        this.purchaseService = purchaseService;
    }

    @Override
    @Transactional
    public void onMessage(String json) {
        OrderMsg msg;
        try {
            msg = objectMapper.readValue(json, OrderMsg.class);
        } catch (Exception e) {
            log.error("消息解析失败，丢弃：{}", json, e);
            return; // 格式坏的消息重试也无意义，吞掉并留痕
        }
        try {
            OrderEntity order = new OrderEntity();
            order.setOrderNo(msg.orderNo());
            order.setType("PURCHASE");
            order.setUserId(msg.userId());
            order.setGoodsId(msg.goodsId());
            order.setPriceDiamond(msg.priceDiamond());
            order.setStatus(0);
            order.setActiveFlag(1L); // 活跃标记=1；唯一约束按(user,goods,1)拦第二张活跃单，关单置NULL放行
            orderMapper.insert(order);
            log.info("订单已落库 orderNo={} userId={}", msg.orderNo(), msg.userId());
        } catch (DuplicateKeyException e) {
            String m = String.valueOf(e.getMessage());
            if (m.contains("uk_order_no")) {
                log.warn("重复投递的消息，幂等忽略 orderNo={}", msg.orderNo());
            } else if (m.contains("uk_user_goods_active")) {
                log.warn("数据库铁闸拦下越线者，补偿库存 userId={} goodsId={}", msg.userId(), msg.goodsId());
                purchaseService.compensate(msg.goodsId(), msg.userId());
            } else {
                log.error("订单插入唯一键冲突，未识别类型 orderNo={}", msg.orderNo(), e);
            }
        }
    }
}
