package com.lootrush.trade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 循环3 核心测试：并发扣减不超卖。
 * 30 个线程同时抢库存 10 件 → 恰好 10 个成功、终态库存为 0。
 * 若"查+减"不是原子的（先 GET 再 DECR 两步），成功数会大于 10 —— 这就是超卖。
 */
@SpringBootTest
class DeductStockConcurrencyTest {

    @Autowired StringRedisTemplate redis;
    @Autowired RedisScript<Long> deductScript;

    private final String stockKey = "stock:OVERSELL_TEST";
    private final String flagPrefix = "buyflag:OVERSELL_TEST:";

    @AfterEach
    void clean() {
        redis.delete(stockKey);
        var keys = redis.keys(flagPrefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void no_oversell_under_concurrency() throws Exception {
        redis.opsForValue().set(stockKey, "10");

        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final long userId = i;
            futures.add(pool.submit(() -> {
                try {
                    startGate.await(); // 所有线程就位后同时放枪
                } catch (InterruptedException ignored) {
                }
                Long r = redis.execute(deductScript,
                        List.of(stockKey, flagPrefix + userId),
                        "ORDER" + userId, "60");
                if (r != null && r == 0) {
                    success.incrementAndGet();
                }
            }));
        }
        startGate.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(10, success.get(), "成功数必须恰好等于库存数");
        assertEquals(0L, Long.parseLong(redis.opsForValue().get(stockKey)), "终态库存必须为0");
    }

    @Test
    void same_user_second_attempt_rejected_by_flag() {
        redis.opsForValue().set(stockKey, "5");
        Long first = redis.execute(deductScript,
                List.of(stockKey, flagPrefix + 999), "ORDER999", "60");
        Long second = redis.execute(deductScript,
                List.of(stockKey, flagPrefix + 999), "ORDER999b", "60");
        assertEquals(0L, first);
        assertEquals(-3L, second, "同一人第二次必须被判重标记拦下");
        assertEquals(4L, Long.parseLong(redis.opsForValue().get(stockKey)), "库存只应扣一次");
    }
}
