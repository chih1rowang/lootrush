package com.lootrush.trade;

import com.lootrush.config.LockService;
import com.lootrush.goods.GoodsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环6 测试：关单回补、状态机守卫、重复关单幂等、分布式锁互斥。
 * 直接调用 OrderCloseService（延迟消息/扫表最终都汇入它），Redis 操作是真实的。
 */
@SpringBootTest
@Transactional
class OrderCloseTest {

    @Autowired OrderCloseService closeService;
    @Autowired OrderMapper orderMapper;
    @Autowired StockFlowMapper stockFlowMapper;
    @Autowired LockService lockService;
    @Autowired StringRedisTemplate redis;

    private Long userId = 424242L;
    private Long goodsId = 424242L;
    private String orderNo;

    @AfterEach
    void clean() {
        if (orderNo != null) {
            orderMapper.deleteByUserId(userId);
            redis.delete("stock:" + goodsId);
            redis.delete(PurchaseService.buyflagKey(goodsId, userId));
        }
        redis.delete(LockService.ORDER_CLOSE_LOCK);
    }

    private OrderEntity insertOrder(int status) {
        orderNo = "C" + System.currentTimeMillis() + userId;
        OrderEntity o = new OrderEntity();
        o.setOrderNo(orderNo);
        o.setType("PURCHASE");
        o.setUserId(userId);
        o.setGoodsId(goodsId);
        o.setPriceDiamond(10);
        o.setStatus(status);
        o.setActiveFlag(status == 0 ? 1L : null);
        orderMapper.insert(o);
        return o;
    }

    @Test
    void close_unpaid_order_refunds_stock_and_releases_limit() {
        insertOrder(0);
        redis.opsForValue().set("stock:" + goodsId, "3"); // 模拟已被扣 1 份后的余量

        boolean closed = closeService.closeIfUnpaid(orderNo, "测试");

        assertTrue(closed, "待支付订单应被关闭");
        OrderEntity after = orderMapper.selectByOrderNo(orderNo);
        assertEquals(3, after.getStatus(), "订单状态应变3(已关闭)");
        assertNull(after.getActiveFlag(), "active_flag 应置 NULL 释放限购");
        assertEquals("4", redis.opsForValue().get("stock:" + goodsId), "Redis 库存应回补 +1");
        assertNull(redis.opsForValue().get(PurchaseService.buyflagKey(goodsId, userId)),
                "判重标记应被删除（玩家可再抢）");
    }

    @Test
    void paid_order_is_not_closed() {
        insertOrder(1); // 已支付
        redis.opsForValue().set("stock:" + goodsId, "5");

        boolean closed = closeService.closeIfUnpaid(orderNo, "测试");

        assertFalse(closed, "已支付订单不得被关");
        assertEquals(1, orderMapper.selectByOrderNo(orderNo).getStatus());
        assertEquals("5", redis.opsForValue().get("stock:" + goodsId), "库存不得被错误回补");
    }

    @Test
    void double_close_refunds_stock_only_once() {
        insertOrder(0);
        redis.opsForValue().set("stock:" + goodsId, "3");

        assertTrue(closeService.closeIfUnpaid(orderNo, "第一次"));
        assertFalse(closeService.closeIfUnpaid(orderNo, "第二次"), "第二次关单必须空转");

        assertEquals("4", redis.opsForValue().get("stock:" + goodsId),
                "无论并发多少次关单，库存只回补一次（状态机守卫在先）");
    }

    @Test
    void distributed_lock_is_mutually_exclusive() {
        assertTrue(lockService.tryLock(LockService.ORDER_CLOSE_LOCK, Duration.ofSeconds(10)),
                "第一个实例应拿到锁");
        assertFalse(lockService.tryLock(LockService.ORDER_CLOSE_LOCK, Duration.ofSeconds(10)),
                "第二个实例必须拿不到（互斥）");
        lockService.unlock(LockService.ORDER_CLOSE_LOCK);
        assertTrue(lockService.tryLock(LockService.ORDER_CLOSE_LOCK, Duration.ofSeconds(10)),
                "释放后应能重新拿到");
    }
}
