# 포트폴리오 MVP 배포 준비 보고서

검증일: 2026-11-09. 이번 범위는 **구현·설정·로컬 검증**이다. 실제 GCP 자원 생성, 공개 배포, 운영 인증·백업 저장소 활성화는 수행하지 않았다. 추가 유료 지출 승인액은 월 0원이며 운영자는 프로젝트 소유자다.

## 구현된 사용자 흐름

- 내 결정은 상태별로 기본 접힌 상태이며 펼친 그룹만 10개씩 조회한다. 상태 복수 선택, 전체 선택/해제, 그룹별 페이지 이동을 지원한다.
- 수정일·생성일·제목·제품군과 오름/내림차순을 선택한다. 제품군 정렬은 표시명 가나다순이 아닌 template code 순서다. 동일 값은 ID 순으로 안정적으로 정렬한다. URL에 필터·펼침·페이지·정렬을 보존하여 상세에서 돌아오거나 새로고침해도 유지한다.
- 삭제 확인 후 결정과 후보·평가/AI 기록·회고·예약 알림·파생 성향 기억을 DB에서 영구 삭제한다. 동시 변경은 409로 새 확인을 요구한다. 다른 소유자와 없는 결정은 404다. 공유 카탈로그·계정과 다른 결정에 이미 적용된 값은 유지한다.
- 삭제 중 예약 작업 및 늦게 도착한 탐색 결과가 데이터를 재생성하지 않도록 부모 행 잠금과 존재 확인을 적용했다. 이미 외부로 전송된 요청·메일은 취소할 수 없다.
- 모바일 레이아웃, 키보드 조작, 삭제 취소·오류·포커스 복원, 빈 목록·실패 재시도를 포함한다. 데이터·삭제 안내는 `/privacy`에서 확인한다.

계약과 정책은 [API contract](api-contract.md), [business rules](business-rules.md), [ADR 0018](decisions/0018-decision-management-and-deletion.md)에 있다. 목록 응답이 배열에서 페이지로 바뀌므로 frontend/backend를 같은 릴리스로 배포한다.

## 검증 근거와 한계

| 검증 | 범위 |
| --- | --- |
| `npm run check` | 스크립트·backend unit·PostgreSQL integration·OpenAPI 동기화·frontend lint/typecheck/production build |
| `npm --prefix frontend run test:e2e` | 기존 비교 흐름과 신규 결정 관리, Chromium 36개 통과 |
| `py -3 -m unittest discover -s deploy -p 'test_*.py'` | DB 초기화 4개 사례, Secret 인자/로그 노출 방지·참조 검증, 실제 GCP 호출 없음 |
| Compose production/local `config --quiet` | cloud credentials 없이 설정 구조 검증 |
| `node scripts/deployment-smoke.mjs` | 실제 Linux 이미지·DB·API·카탈로그·브라우저 및 별도 DB dump/restore/삭제 재적용 |

삭제 검증은 HTTP 응답뿐 아니라 실제 종속 DB 행과 파생 기억의 부재, 다른 결정·카탈로그 보존, 동시 수정/중복 삭제, 실행 중 탐색 완료와 예약 알림의 경합을 확인한다. 실제 provider 요청이나 메일을 보내는 검증은 아니다.

2026-11-09 로컬 Docker smoke에서 synthetic 결정 13개(직접 생성 12개, 카탈로그 1개), 실제 카탈로그 후보 5개를 사용했다. 목록 100회·동시 5요청의 p95는 77ms였다. 백업 복원 시 삭제 전 행이 존재하는 것을 확인하고 삭제를 다시 적용해 0행이 됨을 검증했다. 원본 DB의 synthetic 결정도 종료 시 모두 삭제했다. 재현 명령은 [deploy runbook](../deploy/README.md)에 있으며 원본 실행 결과와 화면은 로컬 `backend/build/deployment-smoke/`에 생성된다. 이번 실행의 [결과 JSON](evaluations/mvp-2026-11-09/result.json), [데스크톱](evaluations/mvp-2026-11-09/desktop.png), [모바일](evaluations/mvp-2026-11-09/mobile.png)을 함께 보관한다.

실행 후 `docker stats` 관측값은 backend 약 479MiB, frontend 47MiB, PostgreSQL 58MiB, proxy 14MiB로 합계 약 599MiB였다. 이는 **최대 메모리 측정이 아니며**, 개발 머신의 Docker 환경이다. 호스트 OS·Docker 자체·파일 캐시·백업 순간 부하·실제 OIDC와 작은 VM의 CPU 제한을 포함하지 않는다. 설정된 컨테이너 메모리 상한 합계는 1,920MiB로 OS 여유분이 별도로 필요하다. 이 결과로 1GB VM의 안정성을 주장하지 않는다.

브라우저 테스트에서 한 차례 fixture POST의 `socket hang up`이 관측되어 전체 재검증했다. 다른 실행에서는 route callback 내부 추가 HTTP 요청이 지연되어 fixture를 사전 취득하도록 고쳤다. 전자는 원인이 확정되지 않은 로컬 전송 오류이며 재시도로 제품 검증을 생략하지 않는다. 상세 진단은 [로컬 배포·브라우저 검증](troubleshooting/mvp-local-validation.md)을 참고한다.

Next.js를 16.4.5로 올리고 비파괴적인 lockfile 갱신을 적용했다. `npm audit --omit=dev` 결과 production 취약점은 0개다. dev dependency의 minimatch 경유 moderate 8건은 남아 있으며 강제 framework downgrade로 숨기지 않는다. 이 결과는 검증일의 audit snapshot이다.

## 무료 운영 검토와 비용안

GCP 무료 Compute 할당은 지정 미국 리전의 e2-micro 사용 시간, standard persistent disk 30GB 등에 조건이 있다. e2-micro의 메모리는 1GB이고 CPU도 shared-core다. 이 앱 전체와 백업을 안정적으로 운영할 수 있다는 증거가 부족하다. 무료 체험/크레딧은 계정 자격·잔액·만료 확인이 필요하며 보유를 가정하지 않는다. [무료 할당 안내](https://docs.cloud.google.com/free/docs/free-cloud-features), [머신 사양](https://docs.cloud.google.com/compute/docs/general-purpose-machines)

아래는 us-central1 on-demand, 월 730시간, USD 기준의 계획용 계산이다. 실제 결제 견적·환율·세금은 별도 확인한다. [Compute 가격](https://cloud.google.com/products/compute/pricing/general-purpose?hl=en), [외부 IPv4 가격](https://cloud.google.com/vpc/network-pricing)

| 후보 | 메모리 | VM/월 | 외부 IPv4/월 약 | 합계 약 | 판단 |
| --- | --- | ---: | ---: | ---: | --- |
| e2-micro | 1GB | 무료 조건 충족 시 0 | $3.65 | 최소 $3.65 | 메모리 안정성 미검증, 채택 보류 |
| e2-small | 2GB | $12.23 | $3.65 | $15.88 | 비용 하한 후보, 현재 상한 합계 대비 OS 여유 부족; 추가 부하 검증 필요 |
| e2-medium | 4GB | $24.46 | $3.65 | $28.11 | 현 구성의 보수적인 검증 시작 후보 |

시간당 단가는 small $0.016752855, medium $0.03350571, 표준 VM의 사용 중 IPv4 $0.005이며 IPv4 최초 1시간 무료를 반영했다. 표는 도메인, 세금, 무료 범위 밖 디스크·백업·로그·네트워크·Secret 요청을 포함하지 않는다. 무료 지역의 지연시간도 한국 사용자 기준으로 별도 측정해야 한다. **$15.88은 안정성이 확인된 최소 운영비가 아니라 검증이 필요한 하한 후보**다. 현재 구성의 보수적인 예산 제안은 월 약 $28.11에 제외 비용을 더한 금액이며 승인된 지출은 아니다.

월 0원 추가 지출을 유지하는 현실적인 선택은 우선 로컬 데모를 제공하거나, 실제 크레딧이 확인된 기간에 한해 종료 계획을 갖고 제한적으로 공개하는 것이다. 장기 무과금 운영은 확약하지 않는다. 다른 배포 구조로 옮기거나 기능·보안·백업을 줄이는 변경은 자동으로 실행하지 않는다.

## 비용 통제와 운영 인수 조건

초기 활성 기능은 등록 카탈로그와 직접 비교다. AI·실시간 검색/수집·메일·가격/사양 갱신은 꺼져 있다. 갱신은 별도 호출 예산 승인 후 활성화한다. VM 1대, 외부에 DB 포트를 열지 않는 구성, 추가 autoscaling/load balancer/NAT 없음, 운영 VM 외부에서 이미지 빌드를 기본으로 한다.

alerts-only budget은 강제 지출 상한이 아니다. 크레딧 잔액·만료와 서비스별 실제 spend cap 지원 여부를 확인하고, 비용 알림·호출 할당량·수동 중단 절차를 함께 준비해야 한다. VM 중지만으로 disk/IP/storage 비용이 모두 멈추지는 않는다. [Cloud Billing budgets](https://docs.cloud.google.com/billing/docs/how-to/budgets)

실제 배포 전 운영자가 확인할 항목은 다음과 같다.

1. 크레딧과 비용 허용 범위, 머신 용량, hostname·DNS, 실제 이미지 digest.
2. Prod IAM 최소 권한, Auth0 callback/초대 거부·다른 계정 격리, Secret 숫자 버전, 공개 HTTPS·보안 cookie.
3. 별도 비공개 백업 저장소, 매일 1회·7일 lifecycle 및 soft delete/versioning 추가 보존 해제, 실패 경보. RPO 최대 24시간, RTO는 실제 복원 시험 뒤 결정.
4. 제한된 외부 로그 저장소의 14일 보존, 삭제 UUID audit의 완전성. 30MB Docker rotation은 14일 보존을 보장하지 않는다.
5. 백업 복원 후 snapshot 이후 모든 삭제를 재적용하고 DB 부재를 확인. audit이 불완전하면 공개 재개 금지.
6. 실제 VM에서 동시 사용자·백업 부하와 메모리/OOM·지연시간, 장애 복구 및 비용 알림을 확인.

정책과 실행 절차는 [ADR 0019](decisions/0019-portfolio-deployment-preparation.md)와 [배포 runbook](../deploy/README.md)에 있다. 위 운영 인수 항목은 로컬 검증 성공과 별개로 남아 있다.

## 포트폴리오 시연 순서

등록 카탈로그로 비교 생성 → 후보 근거·만료 가격 확인 → 직접 비교/보류·완료 → 홈에서 복수 상태 펼침 → 정렬·페이지 이동 → 상세 방문 후 목록 상태 유지 → 삭제 취소 및 영구 삭제 → 백업 복원 시 삭제 재적용 근거를 설명한다. 사용자가 입력한 실제 데이터 대신 synthetic 사례를 사용한다. AI를 끈 초기 운영 범위와 deterministic 비교, 현재 제약을 함께 공개한다.
