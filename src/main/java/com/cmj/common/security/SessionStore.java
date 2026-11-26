package com.cmj.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 세션을 Redis에 넣고(setSession) 꺼내는 곳.
 *
 * 키: session:{userId} → SessionData(JSON), TTL = 미사용 제한 시간
 *  - 사용자당 키가 하나라서, 새로 로그인하면 덮어써짐 → 먼저 로그인한 쪽의 sid가 사라짐 (나중 로그인이 이김, ✅ Q5)
 *  - 요청이 올 때마다 TTL을 다시 늘림(슬라이딩 만료). 한동안 요청이 없으면 Redis가 키를 지움 → 자동 로그아웃 (✅ Q16)
 *
 * 🟩 저장소가 Redis였던 것은 거의 확실(setSession 뒤에 DB DAO가 없었음). 키 이름·TTL 값은 예시.
 */
public class SessionStore {

    private static final String PREFIX = "session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration idleTimeout;

    public SessionStore(StringRedisTemplate redis, ObjectMapper objectMapper, Duration idleTimeout) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.idleTimeout = idleTimeout;
    }

    /** 로그인 성공 때: 새 세션 저장 (기존 세션은 덮어씀) */
    public void save(SessionData data) {
        redis.opsForValue().set(key(data.userId()), toJson(data), idleTimeout);
    }

    /** 권한·언어가 바뀌었을 때: 남은 시간은 그대로 두고 내용만 교체 (Q7 권한 회수, Q20 언어 변경) */
    public boolean replaceKeepingTtl(SessionData data) {
        Long seconds = redis.getExpire(key(data.userId()), TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0) {
            return false; // 이미 로그아웃됐거나 만료된 사용자는 갱신할 세션이 없음
        }
        redis.opsForValue().set(key(data.userId()), toJson(data), Duration.ofSeconds(seconds));
        return true;
    }

    public Optional<SessionData> find(String userId) {
        return Optional.ofNullable(redis.opsForValue().get(key(userId))).map(this::fromJson);
    }

    /** 요청 한 번 = 사용 중 → 만료 시간을 처음부터 다시 셈. 새 만료 시각을 돌려줌 */
    public Instant touch(String userId) {
        redis.expire(key(userId), idleTimeout);
        return Instant.now().plus(idleTimeout);
    }

    /** 로그아웃: 내 세션일 때만 지움 (이미 다른 곳에서 새로 로그인했다면 그 세션은 건드리지 않음) */
    public void deleteIfCurrent(String userId, String sid) {
        find(userId).filter(s -> s.sid().equals(sid)).ifPresent(s -> redis.delete(key(userId)));
    }

    private static String key(String userId) {
        return PREFIX + userId;
    }

    private String toJson(SessionData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private SessionData fromJson(String json) {
        try {
            return objectMapper.readValue(json, SessionData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
