# cmj-common-security

> ⚠️ **참고용 예시 코드입니다.** 실제 프로젝트 코드가 아니며, 면접 대비를 위해 기억하는 구조와 대화로 확정한 사항을 바탕으로 다시 짠 것입니다.
> 의존성 다운로드가 막힌 환경에서 작성해 **컴파일·실행은 검증하지 않았습니다.**

## 역할
공통 보안 라이브러리(jar). Redis 세션(session:{userId}) 저장·조회, 세션 생성(SessionBuilder), 권한 조회(EXISTS), 요청마다 세션·중복 로그인·화면 권한을 검사하는 SessionAuthFilter. 각 업무 서비스가 의존성으로 가져다 씀.

- ✅ 매크로 장애 대응 — `SessionAuthFilter` 맨 앞(⓪)에 Python User-Agent 차단 규칙 (본인 구현, 비교 방식·목록은 ⚠️ 예시, 설정 `cmj.common-security.blocked-user-agents`). 비로그인 경로 통과(publicPaths)보다 **앞**에 있어야 비로그인 API를 노린 매크로도 걸러짐. 호출 횟수 제한은 이 라이브러리가 아니라 **cmj-gateway**에서 함

역할 변경 알림 개선 (✅ 공지 전환·부여/회수 분기·영향 범위 표시·50명 이상 승인·발송 대기 테이블+배치 / 🟩 권한 버전(납세자 쪽 Redis 부하 대응) / 📘 중복 차단·발송 중지 스위치)
- 🟩 권한 버전 — 납세자 쪽 Redis 부하(ELK, 고객사 ICT 메일 전달) 대응으로 본인이 고쳤다고 기억, 키 구조는 예시 (`PermissionVersionStore`, Redis `perm_ver:{roleId}`): 버전이 바뀐 사용자만 다음 요청 때 세션을 다시 만듦. 바뀐 jar를 다시 배포하고 업무 서비스도 재배포해야 반영됨

## 빌드
```bash
./gradlew publishToMavenLocal   # ~/.m2 에 com.cmj:common-security:1.0.0 배포
```
다른 서비스보다 **먼저** 빌드·배포해야 합니다.

## 이름 변경 안내
실제 고객사·국가를 드러내는 이름은 모두 `cmj-choiminjeong` 형태의 예시 이름으로 바꿨습니다.
- 고객사 이름 → `cmj-choiminjeong` (예: `cmj-choiminjeong 직원`, `ad.cmj-choiminjeong.example`, `hr-db.cmj-choiminjeong.internal`)
- 국가 시간대 → 설정값 `cmj.batch.zone` (예시 값 `Asia/Seoul`)
- 국가·정치 관련 설명 → "고객사 요청" 같은 일반 표현

## 관련 저장소
| 저장소 | 역할 |
|---|---|
| cmj-common-security | 공통 보안 라이브러리 (서버) — Redis 세션·요청마다 세션·권한 검사 |
| cmj-gateway | 게이트웨이 — JWT 서명·만료 확인 |
| cmj-auth-server | 토큰 발급 (OAuth2) |
| cmj-security-server | 로그인 규칙 |
| cmj-portal-app | 포털·관리자 API |
| cmj-spring-batch | 인사 동기화 배치 |
| cmj-redis | Redis에 저장할 값(라벨·공통 코드 등) 정의 + 기동 시 DB→Redis 적재 |
| cmj-frontend-common | 공통 화면 코드 |
| cmj-frontend-service | 업무 화면 코드 |

✅ 실제로는 라이브러리로 묶어 배포하는 구조가 **기존에 있었고**, JAR를 **GitLab 패키지 저장소**에 올려 각 업무 서비스가 의존성으로 가져다 씀. 라이브러리를 고치면 **업무 서비스도 다시 배포**해야 반영됨 (2026-11-27 확인). 이 예시는 로컬에서 돌려 보기 쉽게 mavenLocal 을 씁니다.
