# ADR 0008: Identity 기반 Secret Manager를 애플리케이션 Secret의 기준 저장소로 사용한다

- Status: Accepted
- Date: 2026-09-16
- Decision: human이 audit의 축소 권고(GCP 초기 조회·cloud-free 테스트·문서화)를 승인했다. 실제 cloud provisioning과 운영 호스팅/Identity 연결은 별도 작업이다.

## Context

Decision Assistant는 단일 Spring Boot backend에서 PostgreSQL, Auth0, Gemini 또는 OpenAI, 선택적 Serper/Firecrawl/Resend를 사용한다. 이전 설정은 환경 변수로 key를 전달하고 로컬에서는 평문 `.env`의 명시적 로딩을 안내했다. API key, DB password, OIDC client secret의 소비 지점은 Spring 설정과 소수의 adapter에 집중되어 있다. 일반 CI와 대부분의 테스트에는 실제 외부 Secret이 필요하지 않다.

현재 저장소에는 특정 클라우드 배포나 Secret Manager 연동이 없었다. Gemini Developer API 사용은 GCP 호스팅/Vertex AI 사용의 근거가 아니며 Auth0 사용자 로그인은 개발자/운영 workload의 클라우드 Identity와 별개다. human의 선택에 따라 Secret 저장소는 GCP를 기준으로 한다. 실제 운영 호스팅은 이 결정에서 선택하지 않는다.

## Problem

외부 기능을 켜고 운영 환경을 추가하면 key가 개발자 파일, IDE 실행 설정, 부모 프로세스 환경, CI/배포 설정에 복제되기 쉽다. Git 제외는 파일 보호·접근 회수·누가 읽었는지의 감사를 제공하지 않는다. 같은 key를 Dev/Prod에서 재사용하면 오류와 유출의 영향 범위도 분리되지 않는다.

이 결정은 확인된 침해 사고의 대응이 아니다. 운영 시작 전에 Secret의 기준 저장소, 접근 주체, 환경 격리와 실패 동작을 일관되게 정의하려는 것이다. Secret payload를 다른 위치에 복사하면서 중앙 관리라고 부르는 구조는 피한다.

## Alternatives

### 로컬 `.env` 및 일반 환경 변수

구현이 가장 작고 기동에 추가 네트워크 의존이 없다. 하지만 실제 key의 수동 배포/복제/폐기가 계속되고 shell 환경은 불필요한 자식 프로세스로 상속될 수 있다. 실제 애플리케이션 Secret의 기본 경로로는 채택하지 않는다. 비밀 아닌 Configuration과 disposable 개발 DB/test fixture에는 유지한다.

### CI/CD Secret을 통한 주입

CI의 기존 저장소에서 배포 시 주입하기 쉽다. 반면 빌드/배포 주체가 runtime key를 보유하고 pipeline/log/artifact 경계에 Secret이 추가된다. 로컬 개발의 별도 공급 문제도 남는다. 앱 runtime Secret의 기준 저장소로 채택하지 않는다. Qodana 같은 CI 도구 자체의 token은 계속 CI Secret으로 관리한다.

### Identity 기반 Secret Manager 런타임 직접 조회 — 채택

개발자와 workload의 Identity에 권한을 부여하여 앱이 실제 key를 직접 조회한다. Secret의 버전·접근 권한·감사 지점을 중앙화하고 앱 key를 로컬 파일이나 CI에 배포하는 경로를 줄인다. 대신 인증 설정, 초기화 코드, IAM 운영과 기동 시 서비스 의존을 받아들인다.

## Decision

- 실제 애플리케이션 Secret은 Google Secret Manager에 개별 저장한다. 필요한 provider만 활성화하며 선택하지 않은 provider key는 요구하지 않는다.
- local은 Developer ADC로 Dev 프로젝트, 운영은 attached service Identity 또는 지원되는 federation으로 Prod 프로젝트를 사용한다. 서비스 계정 private key 파일을 배포하지 않는다.
- backend는 기동 시 필요한 숫자 버전을 직접 조회하고 기존 Spring property로 JVM 메모리에 바인딩한다. DB/OIDC/AI client 생성 전에 완료한다. 정상 조회 전 일부 Secret만 적용하지 않는다.
- 실패 시 기동을 중단하고 평문 `.env`/환경 변수로 자동 fallback하지 않는다. managed Secret과 중복된 평문 입력은 설정 오류다. 비활성 기능의 Secret은 조회하지 않는다.
- Dev/Prod는 서로 다른 프로젝트·실제 key·IAM으로 분리한다. 일상 개발 Identity에는 Prod 접근 권한을 부여하지 않는다.
- Unit/일반 Integration/E2E/CI는 가짜 값과 mock으로 동작하며 Cloud Identity가 필요 없다. local 기본도 cloud-free이고 실제 연동은 명시적으로 켠다.
- 일반 Configuration(프로젝트 ID, Secret 숫자 버전, feature flag, 모델, endpoint, DB username, Auth0 client ID)은 YAML/환경 변수로 유지한다. 로컬 disposable DB password는 개발 fixture다.
- API/domain/DB lifecycle은 변경하지 않는다. 기존 Auth0 구조(ADR 0005), AI 역할과 consent(ADR 0004), 제품명 lookup 정책(ADR 0007)을 유지한다.

## Rationale

현재 Secret 소비 지점이 설정 바인딩에 집중되어 초기 로딩 계층만 추가하면 domain과 provider를 보존할 수 있다. 이 국소적 비용으로 실제 key의 복제 범위를 줄이고 접근 주체와 환경별 권한을 관리할 수 있으므로 복잡도를 감수할 가치가 있다. Secret을 안전하게 전달하는 책임을 기능별 adapter마다 반복하지 않는다.

공식 GCP Java SDK와 작은 Spring 초기 로더를 사용한다. 범용 Cloud Provider interface나 Spring Cloud 전체 연동을 도입하지 않아 현재 Boot/AI 의존성 범위를 불필요하게 넓히지 않는다. 기동 시 조회는 기존 생성자 기반 클라이언트와 맞고 매 요청의 네트워크 의존/비용을 피한다. 숫자 버전 고정은 배포 결과를 재현하고 잘못된 교체를 되돌리는 데 유리하다.

GCP 선택은 기존 AWS 배포 의존성이 없는 상태에서 human이 선택한 단일 provider이며, SDK/ADC로 현재 JVM 구조에 제한된 변경으로 연결할 수 있다는 판단이다. 무료 구간은 비용 고려사항이지 보안 전략의 근거가 아니다. 결제 계정 전체 무료 한도 때문에 환경을 합치거나 모든 key를 하나의 JSON으로 묶지 않는다.

## Trade-offs / Consequences

- 신규 기동과 재시작은 Secret Manager/인증/네트워크에 의존한다. 시간 상한과 제한된 transient retry 후 안전한 오류로 실패한다. 정상 실행 중인 프로세스는 보유한 key로 계속 동작할 수 있다.
- Dev 연동에는 Cloud CLI/ADC 설정과 IAM 온보딩이 추가된다. ADC도 로컬 인증 파일을 만들 수 있으므로 모든 로컬 credential을 없애는 결정은 아니다. OS/개발자 계정 보호가 필요하다.
- key는 실행 중 JVM과 외부 API 인증 헤더에 존재한다. 프로세스 침해·heap dump·무분별한 로깅을 Secret Manager만으로 해결하지 못한다. env/configprops/heapdump를 공개하지 않는다.
- IAM과 Secret 버전, Data Access 로그, 조회/저장 비용을 운영해야 한다. 읽기 감사 로그는 명시적으로 준비하고 값이나 credential 자체를 앱 로그에 남기지 않는다.
- key 교체는 새 버전 등록, 참조 변경, 재시작, provider smoke가 필요하다. 접근 권한 회수만으로 이미 읽힌 외부 key가 무효화되지 않는다. 유출 시 provider에서 폐기한다.
- GCP SDK 의존과 vendor lock-in을 초기화 경계에서 수용한다. 실제 다른 provider 요구가 생기기 전 추상화하지 않는다.
- 실제 호스팅 Identity·Secret 등록·권한 거부·외부 provider smoke는 mock 테스트로 증명할 수 없으며 배포 gate로 남는다.

## Development / Production isolation

Dev와 Prod는 별도 GCP 프로젝트로 두고 결제 계정은 공유할 수 있다. runtime은 필요한 개별 Secret의 reader 권한만 가지며 Secret 생성/갱신/삭제/IAM 관리 권한과 분리한다. 상위 프로젝트에서 상속된 권한도 확인한다. 일상 개발 ADC와 운영 관리 Identity를 구분한다.

같은 API key를 두 저장소에 복사하는 것은 격리로 인정하지 않는다. 외부 provider의 실제 key/권한, DB credential과 필요한 OIDC application도 환경별로 분리한다. 제품 사용자 인증은 기존 Auth0에서 유지하고 Cloud Identity로 교체하지 않는다.

## Non-goals

Vault, multi-cloud abstraction, 복잡한 KMS/CMEK 정책, 조직 IAM governance, VPC perimeter, 자동 Secret rotation, 동적 DB credential, runtime refresh/cache/sidecar, Gemini의 Vertex AI 전환, 전체 배포 IaC와 운영 호스팅 선택은 포함하지 않는다. 향후 실제 요구가 생기면 별도 결정한다.

실제 리소스 생성과 IAM 변경은 이 ADR의 commit으로 수행되지 않는다. 구현/운영 설정과 Migration·failure·acceptance 절차는 [배포 runbook](../deployment.md#secret-management), 개발자 절차는 [Development workflow](../development.md#로컬-설정과-developer-identity)에 둔다.
