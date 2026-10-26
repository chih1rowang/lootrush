package com.lootrush.trade;

import com.lootrush.goods.GoodsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关单服务：延迟消息消费者与扫表兜底任务共用的关闭逻辑。
 *
 * 顺序即正确性：必须先动数据库状态机（markClosedIfCreated），影响 1 行才回补 Redis——
 * 反过来（先回补再关单）会在并发重复关单时把同一份库存回补两次。
 * 关单动作：订单 0→3 ＋ active_flag 置 NULL（释放限购，玩家可再抢）＋
 * Redis 库存 +1 ＋ 删判重标记 ＋ 库存流水记账。
 */
@Service
public class OrderCloseService {

    public static final String TOPIC_ORDER_CLOSE_DELAY = "ORDER_CLOSE_DELAY";

    private static final Logger log = LoggerFactory.getLogger(OrderCloseService.class);

    private final OrderMapper orderMapper;
    private final StringRedisTemplate redis;
    private final StockFlowMapper stockFlowMapper;

    public OrderCloseService(OrderMapper orderMapper, StringRedisTemplate redis,
                             StockFlowMapper stockFlowMapper) {
        this.orderMapper = orderMapper;
        this.redis = redis;
        this.stockFlowMapper = stockFlowMapper;
    }

    /** @return true=本次调用真实关闭了订单；false=订单不存在/已支付/已关闭（幂等空转） */
    @Transactional
    public boolean closeIfUnpaid(String orderNo, String source) {
        OrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("[{}]关单目标不存在 orderNo={}", source, orderNo);
            return false;
        }
        // 状态机守卫先行：0(待支付)→3(已关闭) 且释放限购。0行=已支付/已关过，什么都不做。
        if (orderMapper.markClosedIfCreated(orderNo) == 0) {
            log.info("[{}]关单空转：订单不是待支付状态 orderNo={} 当前状态={}",
                    source, orderNo, order.getStatus());
            return false;
        }
        // 守卫通过才回补——同一订单无论多少并发关单，库存只会回补一次
        redis.opsForValue().increment(GoodsService.STOCK_KEY_PREFIX + order.getGoodsId());
        redis.delete(PurchaseService.buyflagKey(order.getGoodsId(), order.getUserId()));
        StockFlowEntity flow = new StockFlowEntity();
        flow.setGoodsId(order.getGoodsId());
        flow.setChangeCount(1);
        flow.setReason("CLOSE_BACK");
        flow.setOrderNo(orderNo);
        stockFlowMapper.insert(flow);
        log.info("[{}]关单完成 orderNo={} goodsId={} 库存已回补", source, orderNo, order.getGoodsId());
        return true;
    }
}
