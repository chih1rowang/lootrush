package com.lootrush.trade;

import com.lootrush.common.ApiResponse;
import com.lootrush.goods.GoodsService;
import com.lootrush.trade.callback.HmacUtil;
import com.lootrush.user.AuthService;
import com.lootrush.user.UserEntity;
import com.lootrush.user.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环4 测试：支付回调的幂等三件套与验签。
 * 直接以服务层为对象（模拟假支付公司发来的各种通知）。
 * @Transactional 回滚测试数据——本测试曾经没有它：昨天跑完的数据留在库里，
 * 今天第二跑撞上自己昨天留下的通知号，全部走了"重复忽略"捷径，集体假绿变红。
 */
@SpringBootTest
@Transactional
class PayCallbackTest {

    @Autowired PayService payService;
    @Autowired AuthService authService;
    @Autowired GoodsService goodsService;
    @Autowired OrderMapper orderMapper;
    @Autowired UserMapper userMapper;
    @Autowired StringRedisTemplate redis;

    private Long userId;
    private Long goodsId;
    private OrderEntity order;
    private int balanceBefore;

    @BeforeEach
    void setUp() {
        UserEntity u = authService.register("payuser" + System.currentTimeMillis() % 100000, "pass123");
        userId = u.getId();
        balanceBefore = u.getDiamond();
        goodsId = goodsService.createGoods("支付测试品", "t", null, 100, 200, 3,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        // 直接插一张待支付订单（跳过抢购，聚焦支付环节）
        order = new OrderEntity();
        order.setOrderNo("PT" + System.currentTimeMillis() + userId);
        order.setType("PURCHASE");
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setPriceDiamond(100);
        order.setStatus(0);
        order.setActiveFlag(1L);
        orderMapper.insert(order);
    }

    @AfterEach
    void clean() {
        if (userId != null) {
            orderMapper.deleteByUserId(userId);
        }
        redis.delete("stock:" + goodsId);
    }

    private PayService.PayCallbackReq signed(String notifyNo, int amount) {
        String data = order.getOrderNo() + "|" + amount + "|" + notifyNo;
        return new PayService.PayCallbackReq(notifyNo, order.getOrderNo(), amount,
                HmacUtil.hmacSha256Hex("lootrush-pay-hmac-secret-2026-0912", data));
    }

    @Test
    void valid_callback_pays_once_and_deducts_once() {
        ApiResponse<Void> r = payService.processCallback(signed("N1", 100));
        assertEquals(0, r.code(), "合法回调应成功");
        assertEquals(1, orderMapper.selectByOrderNo(order.getOrderNo()).getStatus(), "订单应变已支付");
        assertEquals(balanceBefore - 100, userMapper.selectById(userId).getDiamond(), "钻石应恰好扣100");
    }

    @Test
    void same_notify_no_replayed_is_ignored() {
        payService.processCallback(signed("SAME", 100));
        ApiResponse<Void> r2 = payService.processCallback(signed("SAME", 100));
        assertEquals(0, r2.code(), "重发的同一条通知应返回成功（让支付公司停止重试）");
        assertEquals(balanceBefore - 100, userMapper.selectById(userId).getDiamond(), "钻石只扣一次");
        assertEquals(1, orderMapper.selectByOrderNo(order.getOrderNo()).getStatus());
    }

    @Test
    void different_notify_no_on_paid_order_blocked_by_state_machine() {
        payService.processCallback(signed("A1", 100));
        ApiResponse<Void> r2 = payService.processCallback(signed("A2", 100));
        assertEquals(0, r2.code(), "状态机拦下后应友好返回成功（停止重试）");
        assertEquals(balanceBefore - 100, userMapper.selectById(userId).getDiamond(), "仍只扣一次");
    }

    @Test
    void forged_signature_rejected() {
        PayService.PayCallbackReq forged = new PayService.PayCallbackReq(
                "EVIL", order.getOrderNo(), 100, "deadbeef");
        ApiResponse<Void> r = payService.processCallback(forged);
        assertEquals(401, r.code(), "伪造签名必须401");
        assertEquals(0, orderMapper.selectByOrderNo(order.getOrderNo()).getStatus(), "订单不得被支付");
        assertEquals(balanceBefore, userMapper.selectById(userId).getDiamond(), "钻石不得被动");
    }

    @Test
    void amount_mismatch_rejected() {
        ApiResponse<Void> r = payService.processCallback(signed("N9", 1));
        assertEquals(400, r.code(), "金额与订单不符必须拒绝");
        assertEquals(0, orderMapper.selectByOrderNo(order.getOrderNo()).getStatus());
    }
}
