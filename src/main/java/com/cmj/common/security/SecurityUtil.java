package com.cmj.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 업무 코드에서 "지금 로그인한 사람"을 꺼내는 공통 유틸.
 * ✅ 업무팀은 SecurityUtil 같은 유틸로 세션 값을 꺼내 썼고(session.get), 본인이 꺼낼 항목을 추가함 (Q2, Q4)
 *
 * 사용 예) String tin = SecurityUtil.getTin();
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    public static SessionData current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionData session) {
            return session;
        }
        throw new IllegalStateException("로그인 세션이 없습니다.");
    }

    public static String getUserId()   { return current().userId(); }
    public static String getUserType() { return current().userType(); }
    public static String getPortalCd() { return current().portalCd(); }
    public static String getTin()      { return current().tin(); }
    public static String getOrgCd()    { return current().orgCd(); }
    public static String getDeptCd()   { return current().deptCd(); }
    public static String getLangCd()   { return current().langCd(); }
}
