package com.lootrush.goods;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环2 自动化测试：上架、四态推导、缓存命中、延迟双删。
 * 注意：@Transactional 只回滚数据库；Redis 不参与事务，用 @AfterEach 手动清键防污染。
 */
@SpringBootTest
@Transactional
class GoodsServiceTest {

    @Autowired GoodsService goodsService;
    @Autowired GoodsMapper goodsMapper;
    @Autowired StringRedisTemplate redis;

    @AfterEach
    void cleanRedis() {
        redis.delete(GoodsService.LIST_KEY);
        var keys = redis.keys(GoodsService.STOCK_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private Long createGoodsInMin(int startInMin, int endInMin, int stock) {
        return goodsService.createGoods("测试道具", "desc", null,
                50, 100, stock,
                LocalDateTime.now().plusMinutes(startInMin),
                LocalDateTime.now().plusMinutes(endInMin));
    }

    @Test
    void upcoming_status_with_countdown() {
        Long id = createGoodsInMin(60, 120, 10);
        var vo = goodsService.listGoods().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
        assertEquals("UPCOMING", vo.status(), "开售前应为即将开售");
        assertTrue(vo.countdownSec() > 3500, "倒计时应接近1小时");
        assertEquals(10, vo.stockLeft());
    }

    @Test
    void four_states_derived_from_time_and_stock() {
        createGoodsInMin(60, 120, 10);   // 未来 → UPCOMING
        createGoodsInMin(-30, 60, 10);   // 进行中 → ON_SALE
        createGoodsInMin(-30, 60, 0);    // 进行中无货 → SOLD_OUT
        createGoodsInMin(-120, -60, 10); // 已过 → ENDED
        List<String> statuses = goodsService.listGoods().stream()
                .map(GoodsService.GoodsVO::status).toList();
        assertTrue(statuses.contains("UPCOMING"));
        assertTrue(statuses.contains("ON_SALE"));
        assertTrue(statuses.contains("SOLD_OUT"));
        assertTrue(statuses.contains("ENDED"));
    }

    @Test
    void cache_hit_returns_stale_price_until_invalidated() {
        Long id = createGoodsInMin(-30, 60, 10);
        goodsService.listGoods(); // 第一次：查询并写入缓存

        // 绕过服务层直接改库（模拟"缓存和库不一致"的窗口）
        goodsMapper.updatePrice(id, 999);

        var cached = goodsService.listGoods().stream().filter(v -> v.id() == id).findFirst().orElseThrow(); // 第二次：应命中缓存，仍旧价
        assertEquals(50, cached.priceDiamond(), "缓存命中期间应返回旧价");

        redis.delete(GoodsService.LIST_KEY); // 缓存失效
        var fresh = goodsService.listGoods().stream().filter(v -> v.id() == id).findFirst().orElseThrow(); // 第三次：回源数据库
        assertEquals(999, fresh.priceDiamond(), "缓存失效后应返回新价");
    }

    @Test
    void price_update_triggers_delayed_double_delete() throws Exception {
        Long id = createGoodsInMin(-30, 60, 10);
        goodsService.listGoods(); // 预热缓存

        goodsService.updatePrice(id, 888);
        assertNull(redis.opsForValue().get(GoodsService.LIST_KEY), "改价后缓存应立即被删（第一删）");

        Thread.sleep(700); // 等 500ms 的延迟第二删发生
        assertNull(redis.opsForValue().get(GoodsService.LIST_KEY), "延迟双删后缓存应仍为空");

        var vo = goodsService.listGoods().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
        assertEquals(888, vo.priceDiamond(), "双删完成后查询应拿到新价");
    }
}
