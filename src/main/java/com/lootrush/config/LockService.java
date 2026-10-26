package com.lootrush.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis 分布式锁（技术总表场景11）：
 * 加锁 = SET key 随机令牌 NX EX 过期秒数（NX=没人持有才成功；EX=持有者崩溃锁也会自动过期，
 *        避免死锁）；令牌=本实例启动时生成的随机UUID，用于解锁时验明正身。
 * 解锁 = Lua 脚本"令牌相等才删"（Redis 端原子完成）——防止 A 的锁过期后 B 拿到锁，
 *        A 回来误删 B 的锁。
 */
@Service
public class LockService {

    public static final String ORDER_CLOSE_LOCK = "lock:order-close";

    private final StringRedisTemplate redis;
    private final String instanceId = UUID.randomUUID().toString();
    private final DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>();

    public LockService(StringRedisTemplate redis) {
        this.redis = redis;
        unlockScript.setScriptText(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end");
        unlockScript.setResultType(Long.class);
    }

    /** @return true=拿到锁；false=别的实例正持有 */
    public boolean tryLock(String key, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, instanceId, ttl);
        return Boolean.TRUE.equals(ok);
    }

    /** 只释放自己持有的锁（令牌校验在 Redis 端原子完成） */
    public void unlock(String key) {
        redis.execute(unlockScript, List.of(key), instanceId);
    }
}
