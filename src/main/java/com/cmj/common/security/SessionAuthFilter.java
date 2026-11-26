package com.cmj.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 업무 서비스에 들어오는 모든 요청 앞에서 한 번 실행되는 공통 검사.
 * "요청마다 Redis 세션을 한 번 꺼내서" 아래 네 가지를 한 자리에서 처리한다.
 *
 *   ① 세션이 없다           → 401 SESSION_EXPIRED   (미사용 자동 로그아웃, ✅ Q16)
 *   ② 세션 ID가 다르다      → 401 DUPLICATE_LOGIN   (다른 곳에서 새로 로그인, 다음 클릭에 바로 튕김 ✅ Q5)
 *   ③ 화면·API 권한이 없다  → 403 FORBIDDEN         (주소를 직접 쳐도 서버가 막음 ✅ Q7)
 *   ④ 통과                 → 세션 유효 시간 연장 + SecurityUtil로 꺼낼 수 있게 등록
 *   (🟩 본인 기억(납세자 쪽 Redis 부하 대응, 키 구조는 예시)) ②와 ③ 사이: 세션의 권한 버전이 현재와 다르면 이 사용자 세션만 다시 만든 뒤 ③을 검사
 *
 * ✅ 권한 검사는 gateway가 아니라 각 업무 서비스의 common.security에서 (Q1)
 * 🟩 비교 기준은 로그인 때 세션에 넣어 둔 화면 목록 (Q7)
 * ⚠️ JWT를 업무 서비스에서 다시 풀어 userId·sid를 꺼내는 방식, 응답 헤더로 만료 시각을 내려주는 방식은 예시
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final SessionStore store;
    private final CommonSecurityProperties props;
    private final ObjectMapper objectMapper;
    private final PermissionVersionStore versions;
    private final ObjectProvider<SessionRefreshService> refresh; // DB가 없는 서비스에는 없을 수 있음
    private final AntPathMatcher matcher = new AntPathMatcher();

    public SessionAuthFilter(JwtDecoder jwtDecoder, SessionStore store, CommonSecurityProperties props,
                             ObjectMapper objectMapper, PermissionVersionStore versions,
                             ObjectProvider<SessionRefreshService> refresh) {
        this.jwtDecoder = jwtDecoder;
        this.store = store;
        this.props = props;
        this.objectMapper = objectMapper;
        this.versions = versions;
        this.refresh = refresh;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (matchesAny(props.publicPaths(), path)) {
            chain.doFilter(req, res);
            return;
        }

        // gateway가 이미 서명·만료를 확인했지만, 업무 서비스도 같은 공개키로 풀어 sub·sid를 꺼낸다
        Optional<Jwt> jwt = bearerToken(req).flatMap(this::decode);
        if (jwt.isEmpty()) {
            reject(res, 401, "UNAUTHENTICATED", "로그인이 필요합니다.");
            return;
        }
        String userId = jwt.get().getSubject();
        String sid = jwt.get().getClaimAsString("sid");

        Optional<SessionData> session = store.find(userId);
        if (session.isEmpty()) {                                       // ①
            reject(res, 401, "SESSION_EXPIRED", "오랫동안 사용하지 않아 로그아웃되었습니다.");
            return;
        }
        if (!session.get().sid().equals(sid)) {                        // ②
            reject(res, 401, "DUPLICATE_LOGIN", "다른 곳에서 로그인되어 로그아웃되었습니다.");
            return;
        }
        // 🟩 본인 기억(납세자 쪽 Redis 부하 대응, 키 구조는 예시): 이미 로그인한 사용자의 "다음 요청" 때 비교 (✅ 비교 시점 2026-11-26 확인). 역할 정의가 바뀌어 권한 버전이 올라갔으면, 이 사용자 세션만 지금 다시 만든다
        //    → 역할 변경 때 그 역할을 가진 사용자 전체를 한꺼번에 갱신하지 않아도 됨 (게으른 갱신)
        if (!versions.isCurrent(session.get().permVersions(), session.get().roles())) {
            SessionRefreshService refresher = refresh.getIfAvailable();
            if (refresher != null && refresher.refreshUser(userId)) {
                session = store.find(userId);
                if (session.isEmpty()) {
                    reject(res, 401, "SESSION_EXPIRED", "오랫동안 사용하지 않아 로그아웃되었습니다.");
                    return;
                }
            }
        }
        if (!matchesAny(props.authenticatedOnlyPaths(), path) && !hasScreen(session.get(), path)) { // ③
            reject(res, 403, "FORBIDDEN", "접근 권한이 없습니다.");
            return;
        }

        Instant expiresAt = store.touch(userId);                       // ④ 슬라이딩 만료
        res.setHeader("X-Session-Expires-At", String.valueOf(expiresAt.toEpochMilli())); // 화면 만료 안내용 (⚠️ 예시)

        List<SimpleGrantedAuthority> authorities = session.get().roles().stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(session.get(), null, authorities));
        try {
            chain.doFilter(req, res);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 요청 주소가 세션에 들어 있는 화면들의 API 경로 중 하나에 맞으면 통과 */
    private boolean hasScreen(SessionData session, String path) {
        return session.screens().stream()
            .anyMatch(s -> s.apiPath() != null && matcher.match(s.apiPath(), path));
    }

    private boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream().anyMatch(p -> matcher.match(p, path));
    }

    private static Optional<String> bearerToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? Optional.of(header.substring(7)) : Optional.empty();
    }

    private Optional<Jwt> decode(String token) {
        try {
            return Optional.of(jwtDecoder.decode(token));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    /** 401·403을 공통 JSON 형식으로. 화면(React 공통 axios 인터셉터)은 code를 보고 로그인 화면 이동·안내를 정한다 */
    private void reject(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(res.getWriter(), Map.of("code", code, "message", message));
    }
}
