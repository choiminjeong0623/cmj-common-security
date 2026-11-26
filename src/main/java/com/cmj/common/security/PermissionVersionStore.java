package com.cmj.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 📘 개선안 (실제로 한 일이 아님) — 역할별 "권한 버전"
 *
 *   Redis 키: perm_ver:{roleId} → 숫자 (없으면 0)
 *
 * 역할 정의(역할의 권한·화면)가 바뀌면 bump()로 그 역할의 버전만 1 올린다.
 * 로그인 때 세션에 "만들 당시의 버전"을 같이 저장해 두고(SessionBuilder),
 * 요청마다 SessionAuthFilter가 현재 버전과 비교해 다르면 그 사용자 세션만 그때 다시 만든다.
 * → 역할 하나가 바뀌었다고 그 역할을 가진 수백 명의 세션을 한꺼번에 다시 만들 필요가 없다.
 */
public class PermissionVersionStore {

    private static final String PREFIX = "perm_ver:";
    private final StringRedisTemplate redis;

    public PermissionVersionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 여러 역할의 현재 버전을 한 번에 읽음 (MGET 한 번) */
    public Map<String, Long> current(Collection<String> roleIds) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (roleIds == null || roleIds.isEmpty()) return result;
        List<String> ids = List.copyOf(roleIds);
        List<String> values = redis.opsForValue().multiGet(ids.stream().map(r -> PREFIX + r).toList());
        for (int i = 0; i < ids.size(); i++) {
            String v = values == null ? null : values.get(i);
            result.put(ids.get(i), v == null ? 0L : Long.parseLong(v));
        }
        return result;
    }

    /** 역할 정의가 바뀌었을 때 호출 — 그 역할의 버전만 1 올림 */
    public long bump(String roleId) {
        Long v = redis.opsForValue().increment(PREFIX + roleId);
        return v == null ? 0L : v;
    }

    /** 세션에 저장된 버전과 현재 버전이 같은지 (없는 값은 0으로 봄) */
    public boolean isCurrent(Map<String, Long> sessionVersions, Collection<String> roleIds) {
        Map<String, Long> now = current(roleIds);
        Map<String, Long> saved = sessionVersions == null ? Map.of() : sessionVersions;
        return now.entrySet().stream()
            .allMatch(e -> Objects.equals(e.getValue(), saved.getOrDefault(e.getKey(), 0L)));
    }
}
