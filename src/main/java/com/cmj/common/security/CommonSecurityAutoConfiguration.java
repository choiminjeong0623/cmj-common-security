package com.cmj.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 서비스가 common-security jar를 의존성에 넣기만 하면 아래가 자동으로 등록된다.
 * (META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 에 이 클래스를 적어 둠)
 *
 *  - 세션 저장소(SessionStore)          : Redis가 있는 모든 서비스
 *  - 권한 조회·세션 만들기·새로고침     : DB(JDBC)가 있는 서비스
 *  - 요청마다 검사하는 필터 체인        : 업무 서비스(web-filter=true)만
 */
@AutoConfiguration(
    after = {RedisAutoConfiguration.class, JdbcTemplateAutoConfiguration.class},
    before = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@EnableConfigurationProperties(CommonSecurityProperties.class)
public class CommonSecurityAutoConfiguration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    SessionStore sessionStore(StringRedisTemplate redis, CommonSecurityProperties props) {
        return new SessionStore(redis, MAPPER, props.sessionIdleTimeout());
    }

    /** 🟩 본인 기억(납세자 쪽 Redis 부하 대응, 키 구조는 예시): 역할별 권한 버전 (perm_ver:{roleId}) */
    @Bean
    PermissionVersionStore permissionVersionStore(StringRedisTemplate redis) {
        return new PermissionVersionStore(redis);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate") // auth-server처럼 DB가 없는 서비스 보호
    @ConditionalOnBean(NamedParameterJdbcTemplate.class)
    static class SessionDbConfiguration {

        @Bean
        PermissionRepository permissionRepository(NamedParameterJdbcTemplate jdbc) {
            return new PermissionRepository(jdbc);
        }

        @Bean
        SessionBuilder sessionBuilder(NamedParameterJdbcTemplate jdbc, PermissionRepository permissions,
                                      PermissionVersionStore versions) {
            return new SessionBuilder(jdbc, permissions, versions);
        }

        @Bean
        SessionRefreshService sessionRefreshService(SessionStore store, SessionBuilder builder,
                                                    PermissionRepository permissions) {
            return new SessionRefreshService(store, builder, permissions);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "cmj.common-security", name = "web-filter", havingValue = "true", matchIfMissing = true)
    static class WebFilterConfiguration {

        @Bean
        JwtDecoder commonJwtDecoder(CommonSecurityProperties props) {
            return NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
        }

        @Bean
        SecurityFilterChain commonSecurityFilterChain(HttpSecurity http, JwtDecoder commonJwtDecoder,
                                                      SessionStore store, CommonSecurityProperties props,
                                                      PermissionVersionStore versions,
                                                      ObjectProvider<SessionRefreshService> refresh) throws Exception {
            http.csrf(c -> c.disable())                       // 쿠키 세션이 아니라 헤더의 JWT를 쓰므로
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 서버 메모리 세션은 안 씀(Redis 세션만)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll()) // 실제 판단은 SessionAuthFilter가 함
                .addFilterBefore(new SessionAuthFilter(commonJwtDecoder, store, props, MAPPER, versions, refresh),
                    AnonymousAuthenticationFilter.class);
            return http.build();
        }
    }
}
