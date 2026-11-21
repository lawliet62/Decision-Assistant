# 단일 VM 배포 준비

현재 범위는 설정·로컬 검증이다. 아래 운영 명령은 **실제 배포 승인, 크레딧/비용 확인, PR 병합 이후 운영자만** 실행한다. 무료라고 가정해 VM·IP·bucket을 생성하지 않는다. 추가 유료 지출 승인액은 0원이다.

## 구성과 신뢰 경계

- 단일 GCP VM의 Compose에서 Caddy → Next.js → Spring Boot → PostgreSQL을 실행한다. 외부에는 80/443만 연다. DB·API 직접 포트, Docker socket, 관리 endpoint를 공개하지 않는다.
- 운영은 `oidc` + `gcp`, 등록 카탈로그·직접 비교만 활성화한다. AI·검색·수집·메일·가격/사양 worker는 모두 꺼져 있다. 가격은 확인 필요로 표시될 수 있다.
- VM 전용 서비스 계정은 Prod의 `db-password`, `auth0-client-secret` 읽기만 허용한다. 키 파일이나 개인 ADC를 컨테이너에 mount하지 않는다. Dev 접근은 주지 않는다. 컨테이너들은 VM Identity를 공유하므로 독립적인 IAM 보안 경계가 아니다. 백업 쓰기·로그 수집에 필요한 권한은 해당 bucket/log sink로 제한한다.
- PostgreSQL 초기화도 metadata Identity로 숫자 Secret 버전을 직접 조회한다. 비밀번호는 Python 메모리와 psql stdin에만 있으며 파일·CLI 인자·환경변수로 전달하지 않는다. `POSTGRES_HOST_AUTH_METHOD=trust`는 upstream 초기화 요구를 충족하기 위한 값이다. 실제 초기/정상 서버 모두 고정 `hba_file`을 사용하여 Unix socket은 peer, TCP는 scram 인증만 허용한다. postgres superuser는 원격 로그인용 비밀번호를 발급하지 않는다.
- 빈 volume에서 초기화가 실패하면 운영자에게 보고한다. 기존 volume을 지우거나 자동 초기화하지 않는다. 비밀번호 변경은 Secret 숫자 버전만 바꿔서는 DB에 적용되지 않는다. 운영자가 안전한 DB 관리 경로로 role 변경·Dev 검증·Secret 참조 변경을 조정한다.

## 로컬 synthetic smoke

저장소 루트에서 실행한다. `decision-mvp-smoke`라는 별도 Compose project·DB volume을 사용하며 공개 포트는 `127.0.0.1:3180`뿐이다. local override는 운영에 사용하지 않는다.

```powershell
./backend/gradlew.bat -p backend bootJar
docker compose --env-file deploy/.env.example -f deploy/compose.yaml -f deploy/compose.local.yaml -p decision-mvp-smoke build
docker compose --env-file deploy/.env.example -f deploy/compose.yaml -f deploy/compose.local.yaml -p decision-mvp-smoke up -d
node scripts/deployment-smoke.mjs
docker stats --no-stream
docker compose --env-file deploy/.env.example -f deploy/compose.yaml -f deploy/compose.local.yaml -p decision-mvp-smoke stop
```

로컬 smoke는 Auth0·GCP Identity·공개 HTTPS·실제 백업 저장소의 검증을 대체하지 않는다.

별도 로컬 frontend를 `127.0.0.1:3101`에서 실행하고 같은 synthetic Compose backend에 연결한 경우, 검증 명령에 `DECISION_SMOKE_ORIGIN=http://127.0.0.1:3101`을 지정할 수 있다. 그 외 주소는 허용하지 않는다. smoke는 선택 API와 화면의 선택·해제도 실제 backend에서 확인한다.

## 운영 이미지 릴리스

개발/CI의 Linux 빌드 환경에서 Java 21, Python 3.12 이상과 Docker를 사용한다. 배포 대상은 성공한 CI와 대조한 병합 커밋이다. 전체 SHA로 checkout한 뒤 새 출력 디렉터리를 지정한다. 빌드는 해당 커밋의 `git archive`에서 시작하며 현재 checkout의 미커밋 파일이나 기존 JAR를 읽지 않는다.

```bash
bundle=/path/to/new-release
python3 deploy/release.py build "$bundle" "$(git rev-parse HEAD)"
```

- `release.json`에는 Git SHA, 소스 아카이브·backend JAR·전달 아카이브의 SHA-256, 배포 파일 해시, 플랫폼(`linux/amd64`), Dockerfile·베이스 이미지 참조 또는 공식 Caddy 다이제스트, 최종 Docker 이미지 ID가 기록된다. 이미지 ID는 로드된 이미지의 검증에 사용한다. 자체 빌드 이미지에 게시된 Registry 참조를 가정하지 않는다.
- 같은 `images.tar`를 로드한 이미지로 격리된 로컬 smoke를 수행한다. 기존 synthetic smoke project가 유휴 상태인지 확인하고 아래 명령을 실행한다. CI의 Release image smoke도 빌드한 아카이브를 다시 로드해 이 경로를 검증한다. CI에서 생성한 이미지는 해당 실행의 검증 산출물이다. 별도로 빌드한 운영 후보에도 아래 검증을 적용한다.

```bash
python3 deploy/release.py load "$bundle"
python3 deploy/release.py verify "$bundle" deploy/.env.example --local-smoke
smoke=(docker compose --env-file deploy/.env.example --env-file "$bundle/images.env" \
  -f deploy/compose.yaml -f deploy/compose.release.yaml -f deploy/compose.local.yaml -p decision-mvp-smoke)
"${smoke[@]}" up -d --no-build --pull never
node scripts/deployment-smoke.mjs
python3 deploy/release.py verify "$bundle" deploy/.env.example --local-smoke --running
"${smoke[@]}" stop
```

검증에 사용한 `release.json`, `images.env`, `images.tar`와 CI 실행 링크·스모크 결과를 함께 보관한다. 운영 승인 이후에만 검증한 세 파일을 신뢰할 수 있는 경로로 VM에 전달한다. 운영 VM에는 해당 릴리스의 배포 파일과 Python 3.11 이상을 사용한다. VM에서 빌드하거나 Registry에서 pull하지 않는다.

공개 접근을 차단한 상태에서 아카이브 해시와 배포 파일·Compose 설정·이미지 ID를 확인한다. 기존 proxy가 실행 중이면 먼저 정지한다. PostgreSQL·backend·frontend를 같은 릴리스 설정으로 올리고 Flyway 적용 및 backend 기동을 확인한 뒤 proxy를 시작한다. 실제 컨테이너의 이미지 ID와 운영 인수 항목까지 확인한 후 접근을 개방한다.

```bash
bundle=/path/to/verified-release
runtime=/etc/decision-assistant/runtime.conf
python3 deploy/release.py load "$bundle"
python3 deploy/release.py verify "$bundle" "$runtime"
release=(docker compose --env-file "$runtime" --env-file "$bundle/images.env" \
  -f deploy/compose.yaml -f deploy/compose.release.yaml)
"${release[@]}" stop proxy
"${release[@]}" up -d --no-build --pull never postgres backend frontend
# Flyway 적용과 backend 기동을 확인한 뒤 실행한다.
"${release[@]}" up -d --no-build --pull never
python3 deploy/release.py verify "$bundle" "$runtime" --running
```

## 실제 배포의 사전 조건

1. [MVP 준비 보고서](../docs/mvp-readiness.md)의 비용·메모리 결과를 확인하고 유효한 크레딧/비용 승인을 받는다. 현재 추가 지출 허용액은 0원이다. 프로젝트 생성·결제 연결·유료 리소스 생성 명령은 제공된 스크립트에 포함하지 않는다.
2. 기존 보유 hostname과 DNS를 결정한다. 예시 `.env.example`은 실제 배포 설정이 아니다. `/etc/decision-assistant/runtime.conf`에 비밀 아닌 프로젝트 ID·Secret 숫자 버전·Auth0 issuer/client ID·정확한 allowlist·hostname을 작성한다. 실제 이메일 목록은 commit하지 않는다.
3. Auth0 callback/logout URL을 기존 runbook과 일치시키고 Dev/Prod Identity 격리와 실제 Secret 접근을 확인한다. VM 외부 firewall은 80/443 및 운영자 전용 관리 접근만 허용한다. 신뢰하지 않는 Host·forwarded header는 public ingress에서 차단한다. Caddy는 지정 hostname만 처리하고 proxy header를 재작성한다.
4. 백업 bucket은 비공개·uniform access·public access prevention, `backup-lifecycle.json`을 적용한다. 7일 보존 정책과 충돌하는 object versioning·기본 soft delete 추가 보존은 끈다. lifecycle 삭제는 비동기이므로 정확히 168시간 이내 물리 파기를 보장한다고 고지하지 않는다. 운영자는 만료 객체 잔존을 점검한다.
5. `backup.conf`에 `BACKUP_BUCKET`, `DEPLOY_ENV_FILE`을 설정하고 service/timer를 설치한다. 백업은 매일 UTC 03시, 실패/24시간 이상 미생성은 경보 대상이다. 일일 백업의 RPO는 최대 24시간이며 RTO는 실제 복원 시험 전에는 약속하지 않는다.
6. 앱 로그를 VM 밖의 제한된 Cloud Logging bucket에 보존하고 retention 14일을 설정한다. 요청 본문·쿠키·토큰·사용자 메모는 수집하지 않는다. 삭제 audit에는 UUID·시각만 남긴다. Docker 로컬 로그는 디스크 보호를 위한 30MB rotation이고 **14일 보존의 대체물이 아니다**. 외부 수집과 TTL이 검증되기 전에는 운영 준비 완료로 표시하지 않는다.
7. 실제 배포 시 인증/초대 거부/CSRF/다른 계정 격리, HTTPS cookie, 외부 기능 비활성화, 목록·삭제·재로그인을 확인한다. health 응답만으로 성공 처리하지 않는다.

## 백업·복원과 삭제 재적용

- 백업은 암호화·접근 제한된 저장소에 7일 보존한다. 앱에서 영구 삭제된 데이터가 이전 백업에는 남을 수 있음을 안내한다. 이미 전송된 provider 데이터·메일은 앱 삭제가 취소하지 않는다.
- 별도 격리 DB에 `pg_restore --exit-on-error --no-owner`로 복원하고 Flyway를 적용한다. 운영 DB를 덮어쓰거나 이전 migration을 수정하지 않는다.
- 복원 snapshot 이후의 `product_event=decision_deleted`, `decision_trashed`, `decision_retention_changed` 로그를 **현재 시점까지 모두** 확보한다. UUID·시각·action을 검증해 결정별 시간 순으로 재적용한다. `decision_trashed`는 원래 시각으로 deleted_at 설정, RESTORE는 deleted_at 해제, ARCHIVE/UNARCHIVE는 archived_at 설정/해제, PERSONALIZATION_ON/OFF는 활용 설정을 복원한다. `decision_deleted`는 해당 행을 삭제하고 cascade를 확인한다. 재적용 후 만료 휴지통도 정리한다. 모든 상태 변경은 version을 증가시키고 운영 중인 앱과 병행하지 않는다. 대상 목록은 로그 접근 권한이 있는 운영자만 취급한다. 이력이 불완전하면 서비스를 다시 공개하지 않는다.
- 삭제 audit은 DB commit 뒤 기록하는 로그이며 transactional outbox가 아니다. commit 직후 프로세스/호스트 장애나 외부 로그 수집 손실이 있었다면 누락 가능성이 있다. 해당 기간의 완전성을 입증할 수 없으면 복원 DB 공개를 중지한다. 로컬 복원 smoke는 이 장애 구간의 내구성을 검증하지 않는다.
- `DECISION_ASSISTANT_RECOVERY_RESTORED_TO`에 실제 복원 기준 시각(예: `2026-11-14T03:00:00Z`)을 넣고 backend를 재기동한다. 홈의 KST 안내와 생성·수정·삭제 영향 문구를 확인한다.
- 세션을 무효화하고 외부 기능·예약 알림을 계속 끈 채 소유권·목록·정렬·삭제 결과와 복원 전후 카탈로그/결정 건수를 확인한다. 영구 삭제된 결정·성향 기억이 없고 휴지통·활용 중단 기록이 개인화에 쓰이지 않는지 SQL로 검증한 뒤 접근을 재개한다.
- 첫 릴리스에는 직전 운영 이미지나 DB 롤백을 배포 전제 조건으로 두지 않는다. 장애 시 공개 접근을 차단하고 수정한 이미지 쌍을 검증한 뒤 전진 복구한다.
- 이후 릴리스부터 직전 frontend·backend 이미지 쌍과 DB 스키마 버전을 기록한다. 격리된 복제 DB에서 현재 적용된 Flyway 스키마로 직전 backend 기동, 주요 읽기·쓰기, frontend 계약을 확인한 경우에만 해당 쌍을 복구 후보로 사용할 수 있다. 이전 삭제 정책의 코드로 되돌리는 복구, `down -v`, 자동 DB 초기화 및 migration rollback은 운영 절차에 포함하지 않는다.

## 비용·장애 통제

리소스 수는 VM 1대·DB 1개로 제한하고 autoscaling, load balancer, NAT, 자동 API 충전은 추가하지 않는다. feature flag만으로 GCP 자체 비용이 제한되지는 않는다. 크레딧 만료일·잔액·무료 할당량을 확인하고 충분한 잔여 기간 안에 종료 일정을 잡는다. alerts-only budget은 청구를 강제로 중단하지 않는다. 계정/서비스별 spend cap 적용 가능성도 실제로 확인해야 한다.

운영자는 월 0원 초과가 예상되면 신규 리소스·외부 호출을 중지하고 공개 운영을 보류한다. VM 정지만으로 disk·보관 IP·storage 비용이 모두 멈추지는 않는다. 잔존 리소스 정리는 데이터 백업과 human 승인을 거쳐 수행한다. 장애/비용 경보에는 최근 정상 백업·삭제 audit의 보존 여부를 함께 확인한다.
