package com.lootrush.trade;

import com.lootrush.common.BizException;
import com.lootrush.goods.GoodsService;
import com.lootrush.user.AuthService;
import com.lootrush.user.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环3 全链路测试：抢购 → MQ → 订单落库（依赖本机 RocketMQ，属集成测试）。
 * 消费者在线程池里异步写库，这里轮询等待订单出现（最多10秒）。
 */
@SpringBootTest
class PurchaseFlowTest {

    @Autowired PurchaseService purchaseService;
    @Autowired AuthService authService;
    @Autowired GoodsService goodsService;
    @Autowired OrderMapper orderMapper;
    @Autowired StringRedisTemplate redis;

    private Long goodsId;
    private Long userId;

    @AfterEach
    void clean() {
        if (userId != null) {
            orderMapper.deleteByUserId(userId);
        }
        if (goodsId != null) {
            redis.delete("stock:" + goodsId);
            redis.delete(String.format(PurchaseService.BUYFLAG_KEY_FORMAT, goodsId, userId == null ? 0 : userId));
        }
    }

    private Long onSaleGoods(int stock) {
        return goodsService.createGoods("抢购测试品", "test", null, 10, 20, stock,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
    }

    private OrderEntity awaitOrder(String orderNo) throws InterruptedException {
        // 30秒而非10秒：每个测试类各起一个Spring上下文，同组消费者多实例时
        // RocketMQ 重平衡最长约20秒，期间消息延迟投递（曾致偶发假失败）
        for (int i = 0; i < 150; i++) {
            OrderEntity o = orderMapper.selectByOrderNo(orderNo);
            if (o != null) {
                return o;
            }
            Thread.sleep(200);
        }
        return null;
    }

    @Test
    void purchase_success_persists_order_via_mq() throws Exception {
        goodsId = onSaleGoods(5);
        UserEntity u = authService.register("buyer" + System.currentTimeMillis() % 100000, "pass123");
        userId = u.getId();

        String orderNo = purchaseService.purchase(userId, goodsId);
        assertNotNull(orderNo);

        OrderEntity order = awaitOrder(orderNo);
        assertNotNull(order, "10秒内订单应经MQ落库");
        assertEquals(0, order.getStatus(), "初始状态应为待支付");
        assertEquals(orderNo, order.getOrderNo());
        assertNotNull(order.getActiveFlag(), "活跃订单 active_flag 应有值");
        assertEquals(4L, Long.parseLong(redis.opsForValue().get("stock:" + goodsId)), "Redis库存应减1");
    }

    @Test
    void same_user_cannot_buy_twice() throws Exception {
        goodsId = onSaleGoods(5);
        UserEntity u = authService.register("twice" + System.currentTimeMillis() % 100000, "pass123");
        userId = u.getId();

        purchaseService.purchase(userId, goodsId);
        BizException e = assertThrows(BizException.class,
                () -> purchaseService.purchase(userId, goodsId));
        assertTrue(e.getMessage().contains("限购"), "第二单应被一人一单规则拒绝：实际=" + e.getMessage());
    }

    @Test
    void sold_out_rejected_fast() throws Exception {
        goodsId = onSaleGoods(1);
        UserEntity a = authService.register("sa" + System.currentTimeMillis() % 100000, "pass123");
        UserEntity b = authService.register("sb" + System.currentTimeMillis() % 100000, "pass123");

        purchaseService.purchase(a.getId(), goodsId);
        BizException e = assertThrows(BizException.class,
                () -> purchaseService.purchase(b.getId(), goodsId));
        assertTrue(e.getMessage().contains("售罄"), "第二人应被告知已售罄：实际=" + e.getMessage());

        // 清理第二位用户的测试数据
        orderMapper.deleteByUserId(b.getId());
        userId = a.getId(); // AfterEach 只清 a 的单（b 无单）
    }
}
