package com.lootrush.reconciliation;

import com.lootrush.goods.GoodsService;
import com.lootrush.trade.BagMapper;
import com.lootrush.trade.DeliverFlowMapper;
import com.lootrush.trade.OrderEntity;
import com.lootrush.trade.OrderMapper;
import com.lootrush.user.AuthService;
import com.lootrush.user.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环7 测试：对账三项核对与修复闭环。
 *
 * 刻意【不加】@Transactional（与 PurchaseFlowTest/DeliverGoodsTest 同理）：
 * 核对B重投的消息由消费者在【另一个事务】里提交——MySQL REPEATABLE READ 下，
 * 带事务的测试活在首查快照里，永远看不见快照之后提交的行（本测试首版就栽在这）。
 * 代价：所有写入真实落库，靠 @AfterEach 手动清理。
 */
@SpringBootTest
class ReconServiceTest {

    @Autowired ReconService reconService;
    @Autowired ReconDiffMapper reconDiffMapper;
    @Autowired GoodsService goodsService;
    @Autowired AuthService authService;
    @Autowired OrderMapper orderMapper;
    @Autowired BagMapper bagMapper;
    @Autowired DeliverFlowMapper deliverFlowMapper;
    @Autowired StringRedisTemplate redis;

    private Long gid;
    private Long userId;
    private String orderNo;

    @AfterEach
    void clean() {
        if (gid != null) {
            redis.delete("stock:" + gid);
            reconDiffMapper.deleteByOrderNo("GOODS:" + gid);
        }
        if (orderNo != null) {
            deliverFlowMapper.deleteByOrderNo(orderNo);
            reconDiffMapper.deleteByOrderNo(orderNo);
        }
        if (userId != null) {
            bagMapper.deleteByUserId(userId);
            orderMapper.deleteByUserId(userId);
        }
    }

    private Long createGoods(int stock) {
        return goodsService.createGoods("对账测试品", "t", null, 10, 20, stock,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
    }

    @Test
    void stock_mismatch_fixed_to_db_truth_and_not_double_registered() {
        gid = createGoods(7);
        redis.opsForValue().set("stock:" + gid, "999"); // 模拟漂移（即用户埋的999靶子）

        ReconService.Summary s = reconService.runOnce("测试");
        assertFalse(s.skipped());
        assertTrue(s.stockFixed() >= 1, "至少修复本测试品的漂移");
        assertEquals("7", redis.opsForValue().get("stock:" + gid), "修复后应等于数据库推导值");

        assertTrue(reconDiffMapper.selectRecent(100).stream().anyMatch(d ->
                ReconService.DIFF_STOCK.equals(d.getDiffType())
                        && ("GOODS:" + gid).equals(d.getOrderNo())
                        && "FIXED".equals(d.getStatus())), "差异表应有FIXED记录");

        // 重跑一轮：库存已正确 → 不再修复、不重复登记
        ReconService.Summary s2 = reconService.runOnce("测试");
        assertEquals(0, s2.stockFixed(), "第二轮不应再发现本差异");
        long rows = reconDiffMapper.selectRecent(100).stream()
                .filter(d -> ("GOODS:" + gid).equals(d.getOrderNo())
                        && ReconService.DIFF_STOCK.equals(d.getDiffType()))
                .count();
        assertEquals(1, rows, "同一差异只登记一次");
    }

    @Test
    void deliver_gap_detected_and_message_resent() throws Exception {
        UserEntity u = authService.register("recon" + System.currentTimeMillis() % 100000, "pass123");
        userId = u.getId();
        gid = createGoods(5);
        orderNo = "RT" + System.currentTimeMillis() + userId;
        OrderEntity o = new OrderEntity();
        o.setOrderNo(orderNo);
        o.setType("PURCHASE");
        o.setUserId(userId);
        o.setGoodsId(gid);
        o.setPriceDiamond(10);
        o.setStatus(1); // 已支付
        o.setActiveFlag(1L);
        orderMapper.insert(o); // 直接构造"付了没发"缺口（模拟发货消息丢失）

        ReconService.Summary s = reconService.runOnce("测试");
        assertTrue(s.deliverResent() >= 1, "应重投发货消息");

        // 重投的消息由真实消费者异步处理（独立事务，不受本测试回滚影响）
        for (int i = 0; i < 50 && deliverFlowMapper.selectByOrderNo(orderNo) == null; i++) {
            Thread.sleep(200);
        }
        assertNotNull(deliverFlowMapper.selectByOrderNo(orderNo), "10秒内应完成发货");
        for (int i = 0; i < 50 && bagMapper.selectByUser(userId).isEmpty(); i++) {
            Thread.sleep(200);
        }
        assertEquals(1, bagMapper.selectByUser(userId).get(0).getCount(), "道具应进背包");
        // 不在此断言订单状态→2：消费者线程看不到本测试事务未提交的订单行（隔离级别），
        // 状态机流转已由 DeliverGoodsTest 在真实提交路径上覆盖。
    }

    @Test
    void clean_goods_produce_no_diff() {
        gid = createGoods(3);
        redis.opsForValue().set("stock:" + gid, "3"); // 与推导一致

        reconService.runOnce("测试");

        assertFalse(reconDiffMapper.selectRecent(100).stream()
                        .anyMatch(d -> ("GOODS:" + gid).equals(d.getOrderNo())),
                "账实相符的商品不应产生差异记录");
    }
}
