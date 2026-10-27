package com.lootrush.rank;

import com.lootrush.config.LockService;
import com.lootrush.user.UserEntity;
import com.lootrush.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消费排行榜（技术总表场景13）：Redis 有序集合（ZSET）的唯一教科书场景。
 *
 * 为什么是 ZSET：30 万成员下"更新分数、查前100、查我的排名"全部是对数级操作，
 * MySQL 排序方案每秒几十次查询就崩——业务压力直接推导出技术选型。
 *
 * 三个要点：
 * 1. 计分时机：与钱包流水同源（PURCHASE/GACHA 扣款即消费），在【事务提交后】ZINCRBY——
 *    与发货消息同一个 afterCommit 模式，回滚的消费绝不计分；
 * 2. 造数：30 万测试玩家走 Redis pipeline 批量写入（逐条发 30 万次网络往返是灾难）；
 * 3. 同分排序：ZSET 同分按成员字典序，天然稳定（面试细节）。
 */
@Service
public class RankService {

    public static final String KEY = "rank:main";
    public static final long BOT_ID_BASE = 900_000_000L; // 造数玩家的ID段（真实自增ID远离此区间）

    private static final Logger log = LoggerFactory.getLogger(RankService.class);

    private final StringRedisTemplate redis;
    private final UserMapper userMapper;

    public RankService(StringRedisTemplate redis, UserMapper userMapper) {
        this.redis = redis;
        this.userMapper = userMapper;
    }

    /** 业务方在事务内调用：登记"提交成功后再加分"的回调 */
    public void recordAfterCommit(Long userId, long delta) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    addConsumptionNow(userId, delta);
                }
            });
        } else {
            addConsumptionNow(userId, delta);
        }
    }

    /** 立即加分（真实消费累计） */
    public void addConsumptionNow(Long userId, long delta) {
        redis.opsForZSet().incrementScore(KEY, String.valueOf(userId), delta);
    }

    /** 前 N 名（默认100）：ZREVRANGE 分数降序 + 批量补全玩家名 */
    public List<RankEntry> top(int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(KEY, 0, limit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tuples.stream().map(t -> Long.valueOf(t.getValue())).toList();
        Map<Long, UserEntity> users = userMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        List<RankEntry> result = new ArrayList<>(tuples.size());
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            Long uid = Long.valueOf(t.getValue());
            long score = t.getScore() == null ? 0 : t.getScore().longValue();
            UserEntity u = users.get(uid);
            String name = u != null ? u.getUsername() : "神秘玩家" + uid % 1000;
            result.add(new RankEntry(rank++, uid, name, score));
        }
        return result;
    }

    /** 我的排名与分数（未上榜返回 null rank） */
    public MeRank me(Long userId) {
        Long rank = redis.opsForZSet().reverseRank(KEY, String.valueOf(userId)); // 0起，展示+1
        Double score = redis.opsForZSet().score(KEY, String.valueOf(userId));
        Long total = redis.opsForZSet().zCard(KEY);
        return new MeRank(rank == null ? null : rank + 1, score == null ? 0 : score.longValue(), total);
    }

    public record MeRank(Long rank, long score, Long totalMembers) {
    }

    /** 历史消费回填：从钱包流水（事实源）重建真实玩家的榜内分数 */
    public int backfillRealPlayers() {
        var rows = userMapper.selectConsumptionByUser();
        for (var row : rows) {
            if (row.getValue() != null && row.getValue() > 0) {
                redis.opsForZSet().add(KEY, String.valueOf(row.getUserId()), row.getValue().doubleValue());
            }
        }
        log.info("真实玩家消费回填完成，人数={}", rows.size());
        return rows.size();
    }

    /**
     * 造数：灌 count 个测试玩家进榜（pipeline 批量，30万条约秒级）。
     * 定位=测试数据工厂：为验证跳表在几十万成员下的查询性能，面试明示为压测数据。
     */
    public long seedBots(int count) {
        long start = System.currentTimeMillis();
        List<ZSetOperations.TypedTuple<String>> batch = new ArrayList<>(5000);
        for (int i = 1; i <= count; i++) {
            String member = String.valueOf(BOT_ID_BASE + i);
            long score = 50 + ThreadLocalRandom.current().nextInt(50000);
            batch.add(ZSetOperations.TypedTuple.of(member, (double) score));
            if (batch.size() >= 5000) {
                final List<ZSetOperations.TypedTuple<String>> chunk = List.copyOf(batch);
                redis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) conn -> {
                    for (ZSetOperations.TypedTuple<String> t : chunk) {
                        redis.opsForZSet().add(KEY, t.getValue(), t.getScore());
                    }
                    return null;
                });
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            for (ZSetOperations.TypedTuple<String> t : batch) {
                redis.opsForZSet().add(KEY, t.getValue(), t.getScore());
            }
        }
        long cost = System.currentTimeMillis() - start;
        log.info("造数完成：{}个测试玩家入榜，耗时{}ms", count, cost);
        return cost;
    }
}
