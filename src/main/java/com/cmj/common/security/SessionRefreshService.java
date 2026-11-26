package com.cmj.common.security;

import java.util.List;

/**
 * 세션 새로고침: 관리자가 권한을 바꾸거나 사용자가 언어를 바꾸면, DB에서 다시 읽어 Redis 세션을 교체한다.
 *
 * ✅ 관리자 화면에서 권한을 바꾸면 권한이 바뀐 "대상 사용자"의 세션을 갱신 → 다음 요청부터 바로 반영 (Q7)
 * ✅ 언어를 바꿀 때도 마지막에 같은 새로고침이 호출됨 (Q20)
 * ✅ 화면에서는 Redux dispatch 한 줄로 호출 (frontend-common/sessionSlice.ts 참고)
 * ⚠️ 대상 사용자를 찾는 방식(역할→사용자 목록)과 역할 단위 일괄 처리는 예시 — 실제 방식은 기억 명확하지 않음
 */
public class SessionRefreshService {

    private final SessionStore store;
    private final SessionBuilder builder;
    private final PermissionRepository permissions;

    public SessionRefreshService(SessionStore store, SessionBuilder builder, PermissionRepository permissions) {
        this.store = store;
        this.builder = builder;
        this.permissions = permissions;
    }

    /** 한 사용자: 로그인 중이면 sid·로그인 시각은 그대로 두고 내용만 다시 만든다 */
    public boolean refreshUser(String userId) {
        return store.find(userId)
            .map(old -> store.replaceKeepingTtl(builder.build(userId, old.sid(), old.loginAtMillis())))
            .orElse(false); // 로그인 안 한 사용자는 다음 로그인 때 새 권한으로 세션이 만들어짐
    }

    /**
     * 역할의 권한이 바뀜 → 그 역할을 가진 사용자 전체
     * 📘 개선안에서는 쓰지 않음: 역할 정의 변경은 PermissionVersionStore.bump() 만 하고,
     *    각 사용자의 다음 요청 때 SessionAuthFilter가 그 사람 세션만 다시 만든다 (대상이 수백 명이어도 일괄 작업 없음)
     */
    public int refreshUsersByRole(String roleId) {
        return refreshAll(permissions.findUserIdsByRole(roleId));
    }

    /** 권한의 화면 매핑이 바뀜 → 그 권한을 가진 사용자 전체 */
    public int refreshUsersByPermission(String permId) {
        return refreshAll(permissions.findUserIdsByPermission(permId));
    }

    private int refreshAll(List<String> userIds) {
        int refreshed = 0;
        for (String userId : userIds) {
            if (refreshUser(userId)) refreshed++;
        }
        return refreshed;
    }
}
