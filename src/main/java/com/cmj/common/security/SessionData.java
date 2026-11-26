package com.cmj.common.security;

import java.util.List;
import java.util.Map;

/**
 * Redis 세션 한 건 = 로그인한 사용자 한 명의 "지금 상태".
 *
 * ✅ 확인: 로그인 때 사용자 유형·TIN·소속(기관·부서)·사용 언어를 세션에 넣고, 업무팀은 공통 유틸로 꺼내 씀 (Q2)
 * ✅ 확인: 접근 가능한 화면 목록도 로그인 때 불러와 세션에 둠 → 요청마다 이 목록과 비교 (Q7)
 * ⚠️ 필드 이름·구성은 예시
 */
public record SessionData(
    String sid,               // 이번 로그인의 세션 ID. 새로 로그인하면 바뀜 → 중복 로그인 판정에 사용 (Q5)
    String userId,
    String loginId,
    String userType,          // STAFF(cmj-choiminjeong 직원) / TAXPAYER / AGENT(세무 대리인) / AGENCY(기관 사용자)
    String portalCd,          // IPT(내부 직원) / OPT(기관) / EPT(납세자) — Q20
    String tin,               // 납세자 고유번호
    String orgCd,             // 소속 기관
    String deptCd,            // 소속 부서
    String langCd,            // 사용 언어 (EN / SW / FR / KO) — 언어를 바꾸면 이 값도 갱신 (Q20)
    List<String> roles,
    List<ScreenAuth> screens, // 이 사용자가 쓸 수 있는 화면(메뉴)과 API 경로
    long loginAtMillis,
    Map<String, Long> permVersions // 🟩 본인 기억(납세자 쪽 Redis 부하 대응, 키 구조는 예시): 세션을 만들 당시 역할별 권한 버전 (PermissionVersionStore)
) {
    /** 화면 하나 = 메뉴 하나 + 그 화면이 부르는 API 경로 패턴 */
    public record ScreenAuth(String screenId, String upScreenId, String menuNm, String apiPath, int sortOrd) {}
}
