package com.lootrush.trade;

import com.lootrush.common.BizException;
import com.lootrush.goods.GoodsEntity;
import com.lootrush.goods.GoodsMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抢购主线（技术总表场景5/6）：
 * 1. 时间窗校验（未开售/已结束快速拒绝）
 * 2. Redis Lua 原子完成「一人一单判重 + 查库存 + 扣减」——一次往返，不可插队
 * 3. 98% 注定失败者在缓存层即被拒绝，数据库零压力
 * 4. 赢家只做一件事：发 ORDER_CREATED 消息即返回；订单落库由消费者异步完成（削峰）
 */
@Service
public class PurchaseService {

    public static final String TOPIC_ORDER_CREATED = "ORDER_CREATED";
    public static final String STOCK_KEY_PREFIX = "stock:";
    public static final String BUYFLAG_KEY_FORMAT = "buyflag:%d:%d";

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;
    private final StockFlowMapper stockFlowMapper;
    private final StringRedisTemplate redis;
    private final RedisScript<Long> deductScript;
    private final RocketMQTemplate mqTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PurchaseService(GoodsMapper goodsMapper, OrderMapper orderMapper,
                           StockFlowMapper stockFlowMapper, StringRedisTemplate redis,
                           RedisScript<Long> deductScript, RocketMQTemplate mqTemplate) {
        this.goodsMapper = goodsMapper;
        this.orderMapper = orderMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.redis = redis;
        this.deductScript = deductScript;
        this.mqTemplate = mqTemplate;
    }

    /** 返回订单号；各种失败抛 BizException（门口快速拒绝） */
    public String purchase(Long userId, Long goodsId) {
        GoodsEntity goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw BizException.of(404, "商品不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(goods.getSaleStartTime())) {
            throw BizException.of(400, "还没开售");
        }
        if (now.isAfter(goods.getSaleEndTime())) {
            throw BizException.of(400, "活动已结束");
        }

        String orderNo = genOrderNo(userId);
        // 判重标记寿命 = 活动剩余时间 + 5 分钟缓冲（活动结束后标记自然失效，无副作用）
        long flagTtl = Duration.between(now, goods.getSaleEndTime()).getSeconds() + 300;

        Long r = redis.execute(deductScript,
                List.of(STOCK_KEY_PREFIX + goodsId, buyflagKey(goodsId, userId)),
                orderNo, String.valueOf(flagTtl));

        if (r == null || r == -2) {
            log.error("库存未预热 goodsId={}", goodsId);
            throw BizException.of(500, "活动数据异常，请联系运营");
        }
        if (r == -3) {
            throw BizException.of(409, "每人限购一份，你已抢购过");
        }
        if (r == -1) {
            throw BizException.of(409, "已售罄");
        }

        // r==0：扣减成功。只发消息不落库——订单写入由消费者匀速完成（削峰填谷）
        try {
            String json = objectMapper.writeValueAsString(new OrderMsg(orderNo, userId, goodsId, goods.getPriceDiamond()));
            mqTemplate.syncSend(TOPIC_ORDER_CREATED, MessageBuilder.withPayload(json).build());
        } catch (Exception e) {
            // 消息发不出去必须把库存补回去，否则这份库存被"蒸发"
            redis.opsForValue().increment(STOCK_KEY_PREFIX + goodsId);
            redis.delete(buyflagKey(goodsId, userId));
            log.error("订单消息发送失败，已回补库存 goodsId={} userId={}", goodsId, userId, e);
            throw BizException.of(500, "系统繁忙，请重试");
        }
        log.info("抢购成功 userId={} goodsId={} orderNo={} 剩余库存见Redis", userId, goodsId, orderNo);
        return orderNo;
    }

    /** 供消费者在"数据库铁闸拦下越线者"时做补偿：回补库存+删判重+记补偿流水 */
    public void compensate(Long goodsId, Long userId, String orderNo) {
        redis.opsForValue().increment(STOCK_KEY_PREFIX + goodsId);
        redis.delete(buyflagKey(goodsId, userId));
        StockFlowEntity flow = new StockFlowEntity();
        flow.setGoodsId(goodsId);
        flow.setChangeCount(1);
        flow.setReason("COMPENSATE");
        flow.setOrderNo(orderNo);
        stockFlowMapper.insert(flow);
    }

    public List<OrderEntity> myOrders(Long userId) {
        return orderMapper.selectByUser(userId);
    }

    static String buyflagKey(Long goodsId, Long userId) {
        return String.format(BUYFLAG_KEY_FORMAT, goodsId, userId);
    }

    private String genOrderNo(Long userId) {
        return "P" + System.currentTimeMillis() + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)) + userId;
    }
}
