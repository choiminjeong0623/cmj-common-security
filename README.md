# cmj-common-security

> ⚠️ **참고용 예시 코드입니다.** 실제 IDRAS 프로젝트 코드가 아니며, 면접 대비를 위해 기억하는 구조와 대화로 확정한 사항을 바탕으로 다시 짠 것입니다.
> 의존성 다운로드가 막힌 환경에서 작성해 **컴파일·실행은 검증하지 않았습니다.**

## 역할
공통 보안 라이브러리(jar). Redis 세션(session:{userId}) 저장·조회, 세션 생성(SessionBuilder), 권한 조회(EXISTS), 요청마다 세션·중복 로그인·화면 권한을 검사하는 SessionAuthFilter. 각 업무 서비스가 의존성으로 가져다 씀.

## 빌드
```bash
./gradlew publishToMavenLocal   # ~/.m2 에 com.cmj:common-security:1.0.0 배포
```
다른 서비스보다 **먼저** 빌드·배포해야 합니다.

## 관련 저장소
| 저장소 | 역할 |
|---|---|
| cmj-auth-app | 전체 구조 설명 · DB 스크립트 · 화면 공통 코드 (문서용) |
| cmj-common-security | 공통 보안 라이브러리 |
| cmj-gateway | 게이트웨이 |
| cmj-auth-server | 토큰 발급 (OAuth2) |
| cmj-security-server | 로그인 규칙 |
| cmj-portal-app | 포털·관리자 API |
| cmj-spring-batch | 인사 동기화 배치 |

⚠️ 공통 라이브러리의 실제 배포 방식(사내 패키지 저장소 등)은 확인되지 않아, 예시는 mavenLocal 을 씁니다.
