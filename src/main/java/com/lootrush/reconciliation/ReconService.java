package com.lootrush.reconciliation;

import com.lootrush.config.LockService;
import com.lootrush.goods.GoodsMapper;
import com.lootrush.goods.GoodsService;
import com.lootrush.trade.DeliverMsg;
import com.lootrush.trade.OrderEntity;
import com.lootrush.trade.StockFlowEntity;
import com.lootrush.trade.StockFlowMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 对账服务（技术总表场景14）：定时核对＋差异登记＋以数据库为准修复。
 *
 * 三项核对：
 * A. 库存：Redis 现值 vs 数据库推导（上架总量+流水合计）——不一致则修复 Redis（库是事实源）
 * B. 发货缺口：已支付却无发货记录 → 重投 DELIVER_GOODS（幂等消费者兜底完成）——
 *    这兑现了循环5 afterCommit 设计里"发送失败由对账兜底"的承诺
 * C. 发了没付：发货记录存在但订单状态异常 → 只登记不修复（数据异常需人工）
 *
 * 幂等：recon_diff 的 (diff_type, order_no) 唯一索引——同一差异不会被重复登记/重复修复；
 * 本方法不加事务：每个修复动作自身幂等，崩溃后下轮对账自动重做（至少一次+幂等消费）。
 */
@Service
public class ReconService {

    public static final String DIFF_STOCK = "STOCK_MISMATCH";
    public static final String DIFF_DELIVER_GAP = "DELIVER_GAP";
    public static final String DIFF_DELIVER_NO_PAY = "DELIVER_WITHOUT_PAY";

    private static final Logger log = LoggerFactory.getLogger(ReconService.class);

    private final ReconMapper reconMapper;
    private final ReconDiffMapper diffMapper;
    private final GoodsMapper goodsMapper;
    private final StockFlowMapper stockFlowMapper;
    private final StringRedisTemplate redis;
    private final RocketMQTemplate mqTemplate;
    private final LockService lockService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReconService(ReconMapper reconMapper, ReconDiffMapper diffMapper,
                        GoodsMapper goodsMapper, StockFlowMapper stockFlowMapper,
                        StringRedisTemplate redis,
                        RocketMQTemplate mqTemplate, LockService lockService) {
        this.reconMapper = reconMapper;
        this.diffMapper = diffMapper;
        this.goodsMapper = goodsMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.redis = redis;
        this.mqTemplate = mqTemplate;
        this.lockService = lockService;
    }

    /** 对账结果摘要 */
    public record Summary(boolean skipped, int checkedGoods, int stockFixed,
                          int deliverResent, int anomalies) {
    }

    public Summary runOnce(String source) {
        if (!lockService.tryLock(LockService.RECON_LOCK, Duration.ofSeconds(120))) {
            log.info("[{}]对账由其他实例执行，本轮跳过", source);
            return new Summary(true, 0, 0, 0, 0);
        }
        try {
            int checkedGoods = 0, stockFixed = 0, deliverResent = 0, anomalies = 0;

            // 核对A：库存漂移
            for (ExpectedStockRow row : reconMapper.selectExpectedStock()) {
                checkedGoods++;
                String key = GoodsService.STOCK_KEY_PREFIX + row.getGoodsId();
                String actual = redis.opsForValue().get(key);
                Long actualVal = parseOrNull(actual);
                // 注意用 longValue() 按数值比较：Long 对象直接用 != 比较的是引用，
                // 超出 -128~127 缓存区的值（如库存5000）即使数值相等也永远"不相等"
                boolean mismatch = actualVal == null
                        || actualVal.longValue() != row.getExpectedStock().longValue();
                if (mismatch) {
                    redis.opsForValue().set(key, String.valueOf(row.getExpectedStock()));
                    insertDiff(DIFF_STOCK, "GOODS:" + row.getGoodsId(),
                            "redis=" + (actual == null ? "缺失" : actual)
                                    + ", 数据库推导=" + row.getExpectedStock() + ", 已按数据库为准修复",
                            "FIXED");
                    stockFixed++;
                    log.warn("[{}]库存漂移已修复 goodsId={} redis={} -> {}", source,
                            row.getGoodsId(), actual, row.getExpectedStock());
                }
            }

            // 核对B：付了没发 → 重投发货消息
            for (OrderEntity order : reconMapper.selectPaidWithoutDeliver()) {
                Long itemId = goodsMapper.selectById(order.getGoodsId()).getItemId();
                String json = objectMapper.writeValueAsString(
                        new DeliverMsg(order.getOrderNo(), order.getUserId(), itemId, 1));
                mqTemplate.syncSend(DeliverMsg.TOPIC, MessageBuilder.withPayload(json).build());
                insertDiff(DIFF_DELIVER_GAP, order.getOrderNo(),
                        "已支付未发货(发货消息疑似丢失), 已重投DELIVER_GOODS, 幂等消费者负责完成", "FIXED");
                deliverResent++;
                log.warn("[{}]发现发货缺口并重投 orderNo={}", source, order.getOrderNo());
            }

            // 核对C：发了没付 → 只登记，人工处理
            for (String orderNo : reconMapper.selectDeliveredWithoutPaid()) {
                insertDiff(DIFF_DELIVER_NO_PAY, orderNo,
                        "存在发货记录但订单状态异常, 不自动修复, 需人工核查", "NEW");
                anomalies++;
                log.error("[{}]数据异常:存在发货记录但订单未支付 orderNo={}", source, orderNo);
            }

            log.info("[{}]对账完成: 商品{} 库存修复{} 发货重投{} 异常{}",
                    source, checkedGoods, stockFixed, deliverResent, anomalies);
            return new Summary(false, checkedGoods, stockFixed, deliverResent, anomalies);
        } finally {
            lockService.unlock(LockService.RECON_LOCK);
        }
    }

    private Long parseOrNull(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void insertDiff(String type, String ref, String detail, String status) {
        ReconDiffEntity d = new ReconDiffEntity();
        d.setReconDate(LocalDate.now());
        d.setDiffType(type);
        d.setOrderNo(ref);
        d.setDetail(detail);
        d.setStatus(status);
        try {
            diffMapper.insert(d);
        } catch (DuplicateKeyException ignored) {
            // 同一差异此前已登记过（可能上轮刚修复又复发）——不覆盖，保留首次记录
        }
    }
}
