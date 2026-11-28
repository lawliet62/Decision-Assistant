# ADR 0025: Cloud Run과 Neon으로 운영 환경 전환

- 상태: Accepted (구조·정책), 실제 전환 미완료
- 날짜: 2026-11-28
- 승인: human이 단일 GCP VM의 수동 운영을 중단하고 Cloud Run(`asia-southeast1`)·Neon 무료 플랜 PostgreSQL(`aws-ap-southeast-1`)·GitHub Actions 배포로 옮기는 구조를 선택했다(배포 시점은 결정 3). 추가 유료 지출 0원 원칙은 유지한다.

[ADR 0019](0019-portfolio-deployment-preparation.md)의 runtime·DB·배포 자동화 결정을 대체한다. 실제 리소스 생성, 전환(cutover), 기존 VM 종료는 이 ADR에 포함하지 않으며 각각 별도 승인 절차로 수행한다. 전환이 끝날 때까지 기존 VM 구성과 [배포 자산](../../deploy/README.md)이 운영 기준이다.

## 배경

ADR 0019의 단일 VM은 image 전달·Compose 기동·백업 timer·TLS 인증서·OS 관리를 운영자가 직접 수행해야 하며, 1인 운영에서 이 부담이 지나치게 크다고 판단했다. 운영 project는 Google Cloud 무료 체험 기간에 있고 종료일은 2026-12-15다. 공식 문서 기준으로 90일 무료 체험은 일시중지·연장할 수 없고, 유료 계정으로 전환해도 남은 크레딧은 원래 90일 안에서만 쓸 수 있다. 따라서 체험 종료 뒤에도 추가 지출 0원을 유지할 수 있는 구성으로 그 전에 옮겨야 한다.

확인한 현재 상태(관찰, 2026-11-28): 운영 VM은 `e2-medium`, `us-central1-f`이고 백업 bucket은 `US-CENTRAL1`이다. Prod Secret은 `auth0-client-secret`, `db-password` 두 개다. `_Default` 로그 bucket 보존 기간은 14일이다.

## 결정

1. 실행 환경은 Cloud Run 서비스 하나(리전 `asia-southeast1`)다. Next.js가 외부 요청을 받는 ingress container이고 Spring Boot는 같은 인스턴스의 sidecar container다. 프론트엔드는 기존 `next.config.ts` rewrites로 `/api`, `/oauth2`, `/login/oauth2`, `/logout`을 `http://localhost:8080`의 backend에 넘기며 backend는 외부에 노출하지 않는다. 배포 단위는 하나로 유지한다.
2. 데이터베이스는 Neon 무료 플랜의 PostgreSQL(리전 `aws-ap-southeast-1`, 싱가포르)이다. Cloud Run과 같은 지역에 둔다. 서울·도쿄 리전은 Neon에 없고 리전은 project 생성 후 바꿀 수 없다.
3. 배포는 human이 GitHub Actions에서 수동 실행할 때 하고, 배포 후 smoke를 실행하며, 성공하면 workflow가 tag와 규칙 기반 릴리스 노트를 만든다. 클라우드에 닿는 단계는 GitHub `production` environment의 승인(required reviewer인 human)을 거친 job에서만 실행되고, WIF 조건으로 그 밖에서는 배포용 서비스 계정을 쓸 수 없게 한다. GCP 인증은 Workload Identity Federation(WIF)을 쓰며 서비스 계정 key를 만들지 않는다. PR 병합은 계속 human이 한다. 절차와 신뢰 경계는 [Cloud Run 배포 문서](../../deploy/cloudrun/README.md)에 있다.
   - 2026-11-28에 human이 '`main` 병합 시 자동 배포'에서 '수동 실행·승인 관문·릴리스 노트 생성'으로 바꿨다.
4. 휴지통 30일 만료 삭제([ADR 0020](0020-decision-retention-and-personalization.md))는 Cloud Scheduler가 하루 한 번 실행하는 전용 일회 실행 경로로 보장한다. 요청이 없으면 인스턴스가 0개가 되어 `@Scheduled` 작업인 `DecisionPurgeWorker`가 실행되지 않기 때문이다. 새 공개 HTTP endpoint는 추가하지 않는 방향이며, 구체 구현(예: 같은 backend image를 Cloud Run Job으로 일회 실행)은 후속 PR에서 정한다.
5. 백업은 Cloud Run Job이 `pg_dump`를 실행해 기존 비공개 GCS(Google Cloud Storage) bucket(7일 lifecycle)에 올리고 Cloud Scheduler가 매일 실행한다. DB 비밀번호를 GitHub에 두지 않는다.
   - 2026-11-28 human 결정: (1) 백업 image는 `postgres:17-alpine`(Neon과 같은 PostgreSQL 17) 기반의 작은 전용 image로 `pg_dump`와 upload script만 담는다. (2) 기존 runtime 서비스 계정, 기존 애플리케이션 DB role, 기존 `db-password` secret을 쓰며 백업용 서비스 계정·DB role·secret을 새로 만들지 않는다. 비밀번호는 job 정의의 숫자 version secret 참조로 env에 주입한다. (3) 기존 bucket(US-CENTRAL1, bucket 전체 7일 삭제 lifecycle, uniform access, public access prevention, soft delete 끔)을 유지하고 새 object는 `backups/neon/` 아래에 둔다. VM은 전환까지 `backups/<시각>.dump`를 계속 쓴다. 두 job은 Cloud Scheduler(무료 3개 중 2개)가 매일 실행하며 추가 유료 지출은 0원을 유지한다. 구현과 절차는 [Cloud Run 배포 문서](../../deploy/cloudrun/README.md#cloud-run-job휴지통-만료-삭제백업)에 있다.
6. 공개 주소는 기존 hostname을 Cloud Run 도메인 매핑으로 연결한다. 현재 DNS 서비스가 필요한 레코드를 지원하지 않으면 Cloud Run 기본 `run.app` 주소를 쓴다. 로드 밸런서는 추가하지 않는다.
7. 유료 결제 계정 전환은 새 구성을 검증한 뒤 무료 체험 종료일(2026-12-15) 전에 human이 직접 수행한다. Cloud Run 지출 상한 예산(spend cap budget)과 결제 계정 전체의 알림 전용 예산을 설정한다.
8. 기존 VM 구성은 새 구성이 검증되어 전환할 때까지 그대로 운영한다.

## ADR 0019와의 관계

대체하는 부분:

- 단일 GCP VM에서 Next.js·Spring Boot·PostgreSQL·HTTPS proxy를 함께 운영하는 runtime과 VM attached Identity bootstrap
- 관리형 DB를 추가하지 않는다는 결정. DB는 관리형 서비스인 Neon으로 바뀐다. 운영자가 image 아카이브를 직접 전달하던 배포는 human이 승인해 실행하는 GitHub Actions 배포로 바뀐다.
- 기존 단일 인스턴스의 예약 작업을 유지한다는 결정 중 휴지통 만료 삭제. 결정 4의 일회 실행 경로로 바뀐다.
- 소형 VM에서 build하지 않고 외부에서 만든 image 아카이브를 전달하는 방식. Cloud Run은 registry의 image를 받아 실행하므로, [배포 자산](../../deploy/README.md)의 VM build·registry pull 금지 규칙은 전환 뒤 적용 대상이 없어진다.
- 상시 Cloud Run + Cloud SQL을 선택하지 않은 결정 중 runtime 부분. ADR 0019는 이 대안이 관리 부담을 줄이지만 비용과 연결 설정이 늘어난다고 보았다. 이번에는 관리 부담 감소를 우선해 관리형 runtime을 채택한다. Cloud SQL의 비용 판단은 유지하며 DB는 Neon을 쓴다(아래 대안 참고).

유지하는 부분:

- 최초 공개 범위(초대 사용자, 등록 이어폰 카탈로그와 직접 비교)와 비활성 기능(AI·실시간 검색/수집·메일·가격 및 사양 갱신)
- 추가 유료 지출 월 0원
- 백업 7일·로그 14일 보존
- Auth0와 기존 Prod Secret Manager 사용, backend의 runtime Secret 직접 조회
- 삭제 audit에 UUID·시각만 남기는 경계
- 구성 검증·CI 통과와 실제 운영 배포 성공의 구분

## 신뢰·보존 경계의 변화

- 사용자 데이터와 세션(`spring-session-jdbc`)이 GCP 밖의 제3자인 Neon(AWS 싱가포르 리전)에 저장된다. 지금까지는 GCP project 안의 VM disk와 GCS 백업에만 있었다.
- backend는 sidecar가 되어도 외부에 노출되지 않는다. 공식 문서 기준으로 Cloud Run은 ingress container의 port만 외부 요청에 연결한다.
- 같은 인스턴스의 container는 서비스 계정을 공유하므로 독립적인 IAM 경계가 아니다. ADR 0019의 "같은 VM의 컨테이너는 Identity 격리 경계가 아님"과 같은 성격이다.
- Neon의 시점 복원(point-in-time restore) 이력에 삭제된 데이터가 일정 기간 남는다. 2026-11-28에 만든 project의 이력 보존 설정은 6시간(`history_retention_seconds` 21600)이다(관찰). 30일 만료 삭제와 7일 백업 보존 외에 이 기간이 추가로 생기며, 이 기간의 허용 여부와 고지는 전환 전에 human이 결정한다.
- 관찰(2026-11-28, 검증 서비스): Cloud Run 서비스 계정의 ADC(Application Default Credentials)로 기존 Secret bootstrap이 동작해 backend가 기동했다. `GcpSecretLoader`가 허용하는 Identity 종류(`ComputeEngineCredentials`·`ExternalAccountCredentials`)는 바꾸지 않았다. ADC가 그중 `ComputeEngineCredentials`로 인식됐다고 보지만 종류를 직접 기록하지는 않았다(추론).

## 결과와 제약

- 요청이 없으면 인스턴스가 0개로 줄어 첫 요청에 콜드 스타트가 생긴다. sidecar 구성에서는 JVM 기동 동안 첫 요청이 더 지연된다. 검증 서비스에서 backend 기동은 약 25초였다(관찰, 2026-11-28). 콜드 스타트에서 사용자가 체감하는 첫 응답 시간은 따로 재지 않았다. Neon compute도 5분 유휴 후 0으로 줄어 재개 지연이 더해진다.
- 요청이 없을 때 background 작업이 실행되지 않는다. 지금 꺼져 있는 `@Scheduled` 기반 기능(검색·수집 worker, 알림, 카탈로그 가격·사양 갱신)을 켜려면 실행 방식을 다시 결정해야 한다.
- 제품명 검색의 burst 제한(사용자별 5회/분, 동시 2건)은 single instance를 전제로 인스턴스 메모리에 둔다(`ProductLookupService`). 월 호출 상한은 PostgreSQL에 있어 인스턴스 수와 무관하다. burst 제한을 유지하기 위해 최대 인스턴스 수를 1로 둔다.
- Cloud Run 요청 기반 과금의 무료 범위는 월 요청 200만 건·180,000 vCPU-초·360,000 GiB-초이며 결제 계정 단위로 합산된다. `asia-southeast1`은 Tier 2 가격 리전이고 무료 범위는 Tier 1 가격 기준 할인으로 적용되므로 같은 사용량이라도 Tier 1 리전보다 무료 범위를 빨리 소진한다. Cloud Scheduler는 결제 계정당 월 3개 job이 무료이며 이번 구성은 purge·백업 두 개를 쓴다. Neon 무료 플랜은 project당 월 100 CU-시간·저장 1GB다.
- 지출 상한 예산은 Cloud Run을 지원하지만 즉시 차단이 아니므로 초과분이 청구될 수 있다. Artifact Registry·Cloud Storage·Cloud Logging은 지원 대상이 아니어서 알림 예산으로만 관측한다. 0원 원칙은 통제 수단이 아니라 구성·용량 관리로 지킨다.
- 서울-싱가포르 간 왕복 지연이 응답 시간에 더해진다. 규모는 미검증이다.
- 운영자는 VM의 OS·Compose·Caddy·백업 timer 대신 Cloud Run 서비스·Job·Scheduler·WIF·Neon project를 관리한다.

## 검토한 대안

- VM 유지 + GitHub Actions 배포: 배포 단계는 줄지만 OS·TLS·백업 timer 운영 부담이 남는다. 체험 종료 뒤에는 `e2-medium` VM 비용이 청구되므로 0원 원칙과 맞지 않는다.
- Cloud Run + Cloud SQL: GCP 안에 데이터를 둘 수 있지만 Cloud SQL 최소 공유 코어 인스턴스 예시가 월 $9.37(us-central1)이라 0원 원칙과 맞지 않아 제외했다.
- 다른 PaaS(Render·Railway): runtime·DB를 한 곳에서 제공하지만 기존 Secret Manager·Identity 기반 bootstrap([ADR 0008](0008-identity-based-secret-management.md))과 GCS 백업을 그대로 쓰기 어렵다고 판단했다. 각 서비스의 현재 무료 범위와 유휴 동작은 이번에 상세 비교하지 않았다(미검증).
- Supabase: 무료 플랜은 일주일 동안 활동이 적으면 project가 일시중지되어, 초대 사용자 위주의 낮은 traffic에서 서비스가 멈출 수 있다.
