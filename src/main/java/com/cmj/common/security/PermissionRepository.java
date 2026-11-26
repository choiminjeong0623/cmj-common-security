package com.cmj.common.security;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 사용자 → 역할 → 권한 → 화면 매핑 조회 (✅ 순서 확인: 2026-11-26)
 *
 *   TB_USER ─< TB_USER_ROLE >─ TB_ROLE ─< TB_ROLE_PERM >─ TB_PERMISSION ─< TB_PERM_SCREEN >─ TB_SCREEN
 *
 * ✅ 메뉴를 거를 때 EXISTS 사용, ✅ "있는지만" 확인하는 쿼리를 JOIN+DISTINCT → EXISTS로 튜닝 (Q10)
 * ⚠️ 테이블·컬럼 이름은 예시
 */
public class PermissionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PermissionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 로그인 때 세션에 넣을 메뉴(화면) 목록.
     * 화면 하나마다 "이 사용자가 쓸 수 있나?"만 EXISTS로 확인 → 역할이 여러 개여도 중복 행이 생기지 않음.
     * 포털 조건으로 사용자 유형별 화면도 나눔 (납세자는 IPT 화면이 애초에 안 나옴, ✅ Q7 사용자 유형별 화면 분리 — 조건 방식은 ⚠️ 예시)
     */
    public List<SessionData.ScreenAuth> findScreens(String userId, String portalCd) {
        return jdbc.query("""
            SELECT S.SCREEN_ID, S.UP_SCREEN_ID, S.MENU_NM, S.API_PATH, S.SORT_ORD
              FROM TB_SCREEN S
             WHERE S.USE_YN = 'Y'
               AND S.PORTAL_CD = :portalCd
               AND EXISTS (
                     SELECT 1
                       FROM TB_USER_ROLE   UR
                       JOIN TB_ROLE_PERM   RP ON RP.ROLE_ID = UR.ROLE_ID
                       JOIN TB_PERM_SCREEN PS ON PS.PERM_ID = RP.PERM_ID
                      WHERE UR.USER_ID   = :userId
                        AND PS.SCREEN_ID = S.SCREEN_ID)
             ORDER BY S.SORT_ORD""",
            Map.of("userId", userId, "portalCd", portalCd),
            (rs, i) -> new SessionData.ScreenAuth(rs.getString("SCREEN_ID"), rs.getString("UP_SCREEN_ID"),
                rs.getString("MENU_NM"), rs.getString("API_PATH"), rs.getInt("SORT_ORD")));
    }

    /**
     * 화면 하나에 권한이 "있는지만" 확인 (✅ Q10 튜닝 사례의 바뀐 뒤 모습).
     * 바꾸기 전: SELECT DISTINCT 화면ID ... 로 목록 전체를 가져와 자바에서 contains 비교
     * 바꾼 뒤  : 조건에 맞는 행을 하나 찾으면 멈추고 Y/N 한 글자만 돌려줌
     */
    public boolean hasScreenAuth(String userId, String screenId) {
        String yn = jdbc.queryForObject("""
            SELECT CASE WHEN EXISTS (
                     SELECT 1
                       FROM TB_USER_ROLE   UR
                       JOIN TB_ROLE_PERM   RP ON RP.ROLE_ID = UR.ROLE_ID
                       JOIN TB_PERM_SCREEN PS ON PS.PERM_ID = RP.PERM_ID
                      WHERE UR.USER_ID   = :userId
                        AND PS.SCREEN_ID = :screenId
                   ) THEN 'Y' ELSE 'N' END
              FROM DUAL""",
            Map.of("userId", userId, "screenId", screenId), String.class);
        return "Y".equals(yn);
    }

    public List<String> findRoleIds(String userId) {
        return jdbc.queryForList("SELECT ROLE_ID FROM TB_USER_ROLE WHERE USER_ID = :u", Map.of("u", userId), String.class);
    }

    /** 역할의 권한이 바뀌면 그 역할을 가진 사용자 전체의 세션을 갱신해야 함 */
    public List<String> findUserIdsByRole(String roleId) {
        return jdbc.queryForList("SELECT USER_ID FROM TB_USER_ROLE WHERE ROLE_ID = :r", Map.of("r", roleId), String.class);
    }

    /** 권한-화면 매핑이 바뀌면 그 권한을 가진 역할의 사용자 전체 (EXISTS로 중복 없이) */
    public List<String> findUserIdsByPermission(String permId) {
        return jdbc.queryForList("""
            SELECT U.USER_ID
              FROM TB_USER U
             WHERE EXISTS (SELECT 1
                             FROM TB_USER_ROLE UR
                             JOIN TB_ROLE_PERM RP ON RP.ROLE_ID = UR.ROLE_ID
                            WHERE UR.USER_ID = U.USER_ID
                              AND RP.PERM_ID = :p)""", Map.of("p", permId), String.class);
    }
}
