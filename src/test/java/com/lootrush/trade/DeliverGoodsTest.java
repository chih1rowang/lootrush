package com.lootrush.trade;

import com.lootrush.goods.GoodsMapper;
import com.lootrush.goods.GoodsService;
import com.lootrush.trade.callback.HmacUtil;
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

/**
 * 循环5 测试：异步发奖全链路与重投幂等。
 */
@SpringBootTest
class DeliverGoodsTest {

    private static final String PAY_SECRET = "lootrush-pay-hmac-secret-2026-0912";

    @Autowired PayService payService;
    @Autowired PurchaseService purchaseService;
    @Autowired AuthService authService;
    @Autowired GoodsService goodsService;
    @Autowired GoodsMapper goodsMapper;
    @Autowired OrderMapper orderMapper;
    @Autowired BagMapper bagMapper;
    @Autowired DeliverFlowMapper deliverFlowMapper;
    @Autowired DeliverGoodsListener listener;
    @Autowired StringRedisTemplate redis;

    private Long userId;
    private Long goodsId;

    @AfterEach
    void clean() {
        if (userId != null) {
            orderMapper.deleteByUserId(userId);
            var rows = bagMapper.selectByUser(userId);
            if (!rows.isEmpty()) {
                bagMapper.deleteByUserId(userId);
            }
        }
        if (goodsId != null) {
            redis.delete("stock:" + goodsId);
        }
    }

    /** 完整走一遍：注册→上架→抢购→等订单落库→签名回调支付。返回已支付的订单。 */
    private OrderEntity paidPurchaseOrder(int price) throws Exception {
        UserEntity u = authService.register("buyer" + System.currentTimeMillis() % 100000, "pass123");
        userId = u.getId();
        goodsId = goodsService.createGoods("发货测试品", "t", null, price, price * 2, 5,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        String orderNo = purchaseService.purchase(userId, goodsId);
        OrderEntity order = awaitOrder(orderNo);
        assertNotNull(order, "订单应先经MQ落库");
        String notifyNo = "NT" + orderNo;
        String data = orderNo + "|" + price + "|" + notifyNo;
        payService.processCallback(new PayService.PayCallbackReq(
                notifyNo, orderNo, price, HmacUtil.hmacSha256Hex(PAY_SECRET, data)));
        return order;
    }

    private OrderEntity awaitOrder(String orderNo) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            OrderEntity o = orderMapper.selectByOrderNo(orderNo);
            if (o != null) {
                return o;
            }
            Thread.sleep(200);
        }
        return null;
    }

    private DeliverFlowEntity awaitDeliver(String orderNo) throws InterruptedException {
        // 30秒窗口：容忍多测试上下文下消费者组的重平衡延迟（同 awaitOrder 注释）
        for (int i = 0; i < 150; i++) {
            DeliverFlowEntity f = deliverFlowMapper.selectByOrderNo(orderNo);
            if (f != null) {
                return f;
            }
            Thread.sleep(200);
        }
        return null;
    }

    @Test
    void paid_order_delivers_to_bag_and_marks_delivered() throws Exception {
        OrderEntity order = paidPurchaseOrder(100);
        awaitDeliver(order.getOrderNo());
        for (int i = 0; i < 50 && bagMapper.selectByUser(userId).isEmpty(); i++) {
            Thread.sleep(200);
        }
        var bag = bagMapper.selectByUser(userId);
        assertEquals(1, bag.size(), "背包应恰好有一行");
        assertEquals(1, bag.get(0).getCount(), "道具数量应为1");
        assertEquals(2, orderMapper.selectByOrderNo(order.getOrderNo()).getStatus(), "订单应标记已发货");
    }

    @Test
    void redelivered_message_does_not_deliver_twice() throws Exception {
        OrderEntity order = paidPurchaseOrder(60);
        awaitDeliver(order.getOrderNo());
        for (int i = 0; i < 50 && bagMapper.selectByUser(userId).isEmpty(); i++) {
            Thread.sleep(200);
        }
        int countAfterFirst = bagMapper.selectByUser(userId).get(0).getCount();

        // 手工重投同一条消息（模拟消息队列重复投递）
        Long itemId = goodsMapper.selectById(order.getGoodsId()).getItemId();
        String json = "{\"orderNo\":\"" + order.getOrderNo() + "\",\"userId\":" + userId
                + ",\"itemId\":" + itemId + ",\"qty\":1}";
        listener.onMessage(json);

        assertEquals(countAfterFirst, bagMapper.selectByUser(userId).get(0).getCount(),
                "重投后背包数量不得增加");
        assertEquals(1, deliverFlowMapper.selectByOrderNo(order.getOrderNo()).getQty(),
                "发货流水应仍只有一条且qty=1");
        assertEquals(2, orderMapper.selectByOrderNo(order.getOrderNo()).getStatus(),
                "订单保持已发货状态");
    }
}
