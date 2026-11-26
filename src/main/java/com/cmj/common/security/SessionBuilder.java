package com.cmj.common.security;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

/**
 * DB에서 사용자 정보·역할·화면 목록을 한 번에 불러와 세션 한 건을 만든다.
 * ✅ "처음 로그인할 때 사용자의 각 정보들을 쫙 불러와서" (Q7)
 * 로그인(security-server)과 세션 새로고침(portal-api 관리자 화면) 양쪽이 같은 코드를 쓴다.
 */
public class SessionBuilder {

    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionRepository permissions;
    private final PermissionVersionStore versions;

    public SessionBuilder(NamedParameterJdbcTemplate jdbc, PermissionRepository permissions,
                          PermissionVersionStore versions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.versions = versions;
    }

    public SessionData build(String userId, String sid, long loginAtMillis) {
        return jdbc.queryForObject("""
            SELECT USER_ID, LOGIN_ID, USER_TYPE, TIN, ORG_CD, DEPT_CD, LANG_CD
              FROM TB_USER
             WHERE USER_ID = :u""", Map.of("u", userId), (rs, i) -> {
            String userType = rs.getString("USER_TYPE");
            String portalCd = portalOf(userType);
            var roleIds = permissions.findRoleIds(userId);
            return new SessionData(
                sid,
                rs.getString("USER_ID"),
                rs.getString("LOGIN_ID"),
                userType,
                portalCd,
                rs.getString("TIN"),
                rs.getString("ORG_CD"),
                rs.getString("DEPT_CD"),
                rs.getString("LANG_CD"),
                roleIds,
                permissions.findScreens(userId, portalCd),
                loginAtMillis,
                versions.current(roleIds)); // 📘 개선안: 만들 당시의 권한 버전을 같이 저장
        });
    }

    /** ✅ 포털 3개: IPT=내부 직원, OPT=기관 사용자, EPT=납세자 (세무 대리인을 EPT에 둔 것은 ⚠️ 예시) */
    public static String portalOf(String userType) {
        return switch (userType) {
            case "STAFF" -> "IPT";
            case "AGENCY" -> "OPT";
            default -> "EPT"; // TAXPAYER, AGENT
        };
    }
}
