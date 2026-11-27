package com.cmj.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 각 서비스의 application.yml에서 정하는 값.
 *
 * cmj:
 *   common-security:
 *     web-filter: true            # 업무 서비스는 true, auth-server·security-server는 false (자기 보안 설정이 따로 있음)
 *     jwk-set-uri: http://auth-server:9000/oauth2/jwks
 *     session-idle-timeout: 30m   # ⚠️ 실제 값 미확인 — 숫자를 지어내지 말 것
 *     public-paths: [/api/public/**]
 *     authenticated-only-paths: [/api/portal/menus, /api/portal/mypage/**]
 *     blocked-user-agents: [python-requests]   # 매크로 장애 대응 (⚠️ 실제 목록·비교 방식은 예시)
 */
@ConfigurationProperties(prefix = "cmj.common-security")
public record CommonSecurityProperties(
    Boolean webFilter,
    String jwkSetUri,
    Duration sessionIdleTimeout,
    List<String> publicPaths,                // 로그인 없이 허용 (라벨, 테마 등)
    List<String> authenticatedOnlyPaths,     // 로그인만 되어 있으면 허용 (메뉴 목록, 마이페이지)
    List<String> blockedUserAgents           // ✅ 매크로 장애 때 본인이 추가: 이 문자열이 들어간 User-Agent는 403
) {
    public CommonSecurityProperties {
        webFilter = webFilter == null || webFilter;
        sessionIdleTimeout = sessionIdleTimeout == null ? Duration.ofMinutes(30) : sessionIdleTimeout;
        publicPaths = publicPaths == null ? List.of() : publicPaths;
        authenticatedOnlyPaths = authenticatedOnlyPaths == null ? List.of() : authenticatedOnlyPaths;
        blockedUserAgents = blockedUserAgents == null ? List.of("python-requests") : blockedUserAgents;
    }
}
