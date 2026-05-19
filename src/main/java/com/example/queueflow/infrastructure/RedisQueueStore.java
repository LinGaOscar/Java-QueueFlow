package com.example.queueflow.infrastructure;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class RedisQueueStore {

    private static final String QUEUE_KEY    = "queue:%d";
    private static final String USER_KEY     = "queue:user:%d:%s";
    private static final String RATELIMIT_KEY = "ratelimit:join:%s";
    private static final String LOCK_KEY     = "admit:lock:%d";
    // 每個活動獨立的原子序列，確保同毫秒並發加入時 FIFO 順序不被 userId 字典序打亂
    private static final String SEQ_KEY      = "queue:seq:%d";

    // 原子性：先確認 userKey 不存在，再同時寫入 userKey 與 sorted set
    private static final String JOIN_SCRIPT = """
            local userKey  = KEYS[1]
            local queueKey = KEYS[2]
            if redis.call('EXISTS', userKey) == 1 then return 0 end
            redis.call('SETEX', userKey, ARGV[3], '1')
            redis.call('ZADD', queueKey, ARGV[2], ARGV[1])
            return 1
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> joinScript;

    public RedisQueueStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.joinScript = RedisScript.of(JOIN_SCRIPT, Long.class);
    }

    public boolean tryJoin(Long eventId, String userId, long score, long ttlSeconds) {
        String userKey  = String.format(USER_KEY, eventId, userId);
        String queueKey = String.format(QUEUE_KEY, eventId);
        Long result = redis.execute(joinScript,
                List.of(userKey, queueKey),
                userId, String.valueOf(score), String.valueOf(ttlSeconds));
        return Long.valueOf(1).equals(result);
    }

    // 若 SET NX 失敗表示 key 已存在 → 限流觸發
    public boolean isRateLimited(String userId) {
        String key = String.format(RATELIMIT_KEY, userId);
        Boolean set = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(5));
        return Boolean.FALSE.equals(set);
    }

    public Long getPosition(Long eventId, String userId) {
        Long rank = redis.opsForZSet().rank(String.format(QUEUE_KEY, eventId), userId);
        return rank == null ? null : rank + 1;
    }

    public Long getQueueSize(Long eventId) {
        Long size = redis.opsForZSet().size(String.format(QUEUE_KEY, eventId));
        return size == null ? 0L : size;
    }

    public List<String> getTopN(Long eventId, int n) {
        Set<String> members = redis.opsForZSet().range(String.format(QUEUE_KEY, eventId), 0, n - 1L);
        return members == null ? List.of() : List.copyOf(members);
    }

    public void removeFromQueue(Long eventId, String userId) {
        redis.opsForZSet().remove(String.format(QUEUE_KEY, eventId), userId);
        redis.delete(String.format(USER_KEY, eventId, userId));
    }

    public boolean isInQueue(Long eventId, String userId) {
        return Boolean.TRUE.equals(redis.hasKey(String.format(USER_KEY, eventId, userId)));
    }

    public boolean tryAcquireLock(Long eventId, long ttlSeconds) {
        String key = String.format(LOCK_KEY, eventId);
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds)));
    }

    public void releaseLock(Long eventId) {
        redis.delete(String.format(LOCK_KEY, eventId));
    }

    public long nextSequence(Long eventId) {
        Long seq = redis.opsForValue().increment(String.format(SEQ_KEY, eventId));
        return seq == null ? System.currentTimeMillis() : seq;
    }
}
