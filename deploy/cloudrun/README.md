# Cloud Run 배포

[ADR 0025](../../docs/decisions/0025-cloud-run-neon-deployment.md)의 Cloud Run 단일 서비스와 GitHub Actions 배포를 구현한 자산이다. 전환(cutover)이 끝날 때까지는 [단일 VM 문서](../README.md)의 구성과 절차가 운영 기준이며, 이 문서의 Cloud Run 서비스는 전환 전 검증 서비스다. 휴지통 만료 삭제 job과 백업 job은 이 디렉터리에 정의(template)가 있고, 실제 job과 Cloud Scheduler는 운영자가 human 승인 아래 [아래 절차](#cloud-run-job휴지통-만료-삭제백업)로 만든다. 도메인 연결은 아직 없다.

배포는 human이 승인한 수동 실행에서만 일어난다. `main` 병합은 배포를 일으키지 않는다(2026-11-28 human 결정).

## 구성

| 파일 | 역할 |
|---|---|
| [`.github/workflows/deploy-cloud-run.yml`](../../.github/workflows/deploy-cloud-run.yml) | 수동 실행. 준비(검사·미리보기) → 승인 → 배포 → tag·Release |
| [`service.template.yaml`](service.template.yaml) | 서비스 정의 template. 운영자가 render해 `gcloud run services replace`로 적용 |
| [`smoke.sh`](smoke.sh) | `/login` 200과 미인증 `/api/v1/me` 401을 제한 시간 안에 재시도하며 확인 |
| [`purge-job.template.yaml`](purge-job.template.yaml) | 휴지통 만료 삭제 job(`decision-assistant-purge`) 정의 template. 운영자가 render해 `gcloud run jobs replace`로 적용 |
| [`backup-job.template.yaml`](backup-job.template.yaml) | 백업 job(`decision-assistant-backup`) 정의 template. 적용 방식은 위와 같음 |
| [`backup/`](backup/) | 백업 job image(`postgres:17-alpine` 기반)와 `pg_dump`·upload script |
| [`restore-counts.sql`](restore-counts.sql) | 복원 시험에서 두 database의 Flyway 버전과 table별 행 수를 비교하는 읽기 전용 조회 |
| [`update-job-images.sh`](update-job-images.sh) | workflow가 배포 뒤 두 job의 image만 이번 digest로 바꾸고 결과를 기록 |
| [`scripts/release/notes.mjs`](../../scripts/release/notes.mjs) | version 결정과 규칙 기반 릴리스 노트 생성(Git 기록과 실행 결과만 사용) |

리소스(2026-11-28 생성·관찰):

- GCP project `decision-assistant-prod`(number `277935636498`), 리전 `asia-southeast1`
- Artifact Registry 저장소 `asia-southeast1-docker.pkg.dev/decision-assistant-prod/decision-assistant`, image `backend`·`frontend`
- Cloud Run 서비스 `decision-assistant`, 공개 주소 `https://decision-assistant-277935636498.asia-southeast1.run.app`
- runtime 서비스 계정 `decision-assistant-prod-backen@decision-assistant-prod.iam.gserviceaccount.com`
- 배포용 서비스 계정 `github-deployer@decision-assistant-prod.iam.gserviceaccount.com`
- WIF provider `projects/277935636498/locations/global/workloadIdentityPools/github/providers/decision-assistant`

아직 만들지 않은 리소스(이 저장소에 정의·절차만 있음): Cloud Run Job `decision-assistant-purge`·`decision-assistant-backup`, Artifact Registry image `backup`(workflow가 처음 실행될 때 push), Cloud Scheduler job 두 개, Scheduler 호출용 서비스 계정 `decision-assistant-scheduler@decision-assistant-prod.iam.gserviceaccount.com`.

서비스 구조: `frontend` container(Next.js, port 3000)가 외부 요청을 받는 ingress이고 `backend` container(Spring Boot, 8080)는 같은 인스턴스의 sidecar다. frontend image는 `BACKEND_URL=http://localhost:8080`으로 build하며 rewrites가 이 주소에 고정된다. `container-dependencies`로 backend가 먼저 뜨고, backend startup probe(TCP 8080)는 최대 240초를 기다린다. 인스턴스는 0-1개, 요청이 없을 때 CPU를 할당하지 않는다.

## 신뢰 경계

배포 권한은 human에게 종속되며 GitHub와 WIF 설정으로 강제한다.

- **(가) 승인 관문.** workflow의 클라우드 단계(WIF 인증, Artifact Registry push, Cloud Run 배포, traffic 전환)는 모두 `environment: production`을 선언한 `deploy` job 하나에 있다. 이 environment에는 human이 GitHub 설정에서 required reviewer(저장소 소유자)와 배포 branch `main` 제한을 직접 구성한다. environment 설정은 저장소 관리자만 바꿀 수 있고 bot에는 그 권한이 없다. 에이전트(bot)는 workflow를 실행할 수 있지만 `deploy` job은 required reviewer인 human이 승인해야 시작된다.
- **(나) WIF 조건.** provider는 `repository_id=1339097882`, `repository_owner_id=102518515`, `ref=refs/heads/main`인 GitHub OIDC token만 받고, 배포용 서비스 계정의 `roles/iam.workloadIdentityUser`는 `attribute.workflow_ref`가 `lawliet62/Decision-Assistant/.github/workflows/deploy-cloud-run.yml@refs/heads/main`인 주체에만 있다(2026-11-28 관찰). provider 조건에는 `assertion.environment=='production'`도 있어 `production` environment를 선언한 job의 token만 받는다(2026-11-28 리드가 적용하고 조회로 확인. 같은 날 첫 실행에서 승인된 `deploy` job의 인증이 통과했다. 조건에 맞지 않는 token이 거부되는지는 실제 인증으로 확인하지 않았다). 다른 branch, PR, 다른 workflow 파일, 승인되지 않은 job은 이 계정을 쓸 수 없다. workflow 파일 이름이나 environment 이름을 바꾸면 GCP 조건도 바꿔야 한다.
- **(다) 관문의 범위.** 이 관문은 GitHub와 WIF 범위의 강제다. 에이전트가 PC에서 human의 개인 `gh` 로그인이나 배포 권한이 있는 `gcloud` 계정을 쓸 수 있다면 우회된다. 그 격리는 운영 에이전트의 권한 구성에서 따로 다룬다(미해결).
- **(라) 대리 승인 경로 없음.** "human이 명령하면 에이전트가 직접 승인·배포"하는 경로는 두지 않는다. 그런 경로는 에이전트의 자기 승인과 구분해 강제할 수 없기 때문이다.
- environment 이름은 정확히 `production`이어야 한다. workflow가 존재하지 않는 environment 이름을 참조하면 GitHub가 **보호 규칙 없는 environment를 자동으로 만들어** 승인 없이 job이 실행된다. 이름을 바꾸거나 새 job에 environment를 붙일 때는 GitHub 설정에 같은 이름의 보호 규칙이 먼저 있어야 한다.
- 서비스 계정 key와 GitHub secret은 없다. workflow에 적힌 project·리전·서비스·저장소·provider·계정 email은 식별자이며 비밀값이 아니다.
- 배포용 서비스 계정의 권한은 Artifact Registry 저장소 수준 `roles/artifactregistry.writer`, Cloud Run 서비스 수준 `roles/run.developer`, runtime 서비스 계정에 대한 `roles/iam.serviceAccountUser`뿐이다. project 수준 역할과 user-managed key는 없다(2026-11-28 관찰). 두 job의 image를 바꾸려면 job 수준 `roles/run.developer`가 추가로 필요하며, 리드가 job을 만든 뒤 human 승인 아래 따로 부여한다([Cloud Run Job 절](#cloud-run-job휴지통-만료-삭제백업)). 부여 전에는 workflow가 job 갱신을 건너뛴다.
- **승인된 배포는 임의의 code를 runtime 서비스 계정으로 실행할 수 있다(`actAs`).** runtime 계정은 Prod Secret(`db-password`, `auth0-client-secret`)을 읽으므로, workflow·backend code를 바꾸는 PR의 병합과 배포 승인은 Prod Secret 접근과 같은 무게로 검토한다. 병합과 승인은 human만 한다.
- `roles/run.developer`는 IAM상 env·서비스 계정·scaling 같은 서비스·job 설정도 바꿀 수 있다. workflow가 image만 바꾸는 것은 IAM이 아니라 workflow 내용으로 지키는 약속이다. 이렇게 나눈 이유는 허용 email 목록 같은 비공개 값이 저장소와 Actions log에 들어가지 않게 하기 위해서다.
- GitHub에 쓰는 권한(`contents: write`)은 `release` job에만 있고 이 job에는 클라우드 인증이 없다. 준비 job은 `contents: read`만 갖는다.
- workflow log에는 image digest, revision 이름, 후보 주소, smoke의 HTTP status, job 갱신 결과만 남는다. `gcloud run services describe`·`gcloud run jobs describe` 결과는 env를 포함하므로 jq로 필요한 값만 꺼내고 원문을 출력하지 않는다. image는 GCP 인증 전에 build해 build context에 인증 파일이 생기기 전 상태로 만든다.
- 후보 revision은 traffic 0%이지만 tag 주소(`candidate---…`)로 직접 열린다. 서비스가 공개 호출을 허용하므로 배포 중에는 이 주소도 같은 조건으로 공개된다. workflow는 끝날 때 성공·실패와 무관하게 tag를 지운다.
- 후보 revision은 운영 DB(Neon)에 연결하고 기동할 때 Flyway migration을 적용한다. **smoke가 실패해 traffic이 이전 revision에 남아도 schema는 이미 새 버전이다.** migration은 이전 backend가 계속 동작하는 형태로 작성해야 한다.

## 수동 배포 절차

1. GitHub Actions에서 **Cloud Run deploy**를 고르고 branch `main`으로 **Run workflow**를 실행한다. 에이전트가 실행해도 된다.
2. `version` 입력은 선택이다. 형식은 `vMAJOR.MINOR.PATCH`(앞자리 0 금지, 예: `v0.3.0`)다. 비우면 가장 최근 semver tag의 patch를 1 올린다(예: `v0.2.0` → `v0.2.1`). 형식이 틀리거나 이미 있는 tag면 `prepare` job이 실패하고 배포는 시작되지 않는다.
3. `prepare` job이 끝나면 실행 화면의 job summary에 릴리스 노트 미리보기(배포 commit, 마이그레이션 포함 여부와 스키마 버전, 직전 tag 이후 변경 목록)가 나온다. 배포 결과 항목은 "배포 후 기록"으로 표시된다.
4. required reviewer(human)가 미리보기를 보고 `production` 배포를 승인한다. 거절하거나 승인하지 않으면 아무것도 배포되지 않는다.
5. `deploy` job: 실행을 시작한 commit(`github.sha`)을 build한다. 승인을 기다리는 동안 `main`이 앞서가도 배포 대상은 바뀌지 않는다.
   1. Java 21로 backend `bootJar`, backend·frontend·backup image build(`linux/amd64`, frontend는 `BACKEND_URL=http://localhost:8080`, backup은 `deploy/cloudrun/backup`만 build context), tag는 commit SHA
   2. WIF 인증, Artifact Registry push, registry digest 확보
   3. 배포 전 traffic 100% revision을 log에 남김
   4. `gcloud run services update --no-traffic --tag candidate --container frontend --image <digest> --container backend --image <digest>`로 traffic 없는 새 revision 생성. 서비스 단위 flag는 첫 `--container` 앞에 둔다([사례](../../docs/troubleshooting/gcloud-run-container-flag-order.md)). 새 revision이 준비되지 않으면 여기서 실패한다
   5. 후보 tag의 revision이 최신 생성·준비 revision인지, 서비스 template의 image가 이번 digest인지 확인
   6. 후보 tag 주소에서 smoke(최대 300초 재시도)
   7. traffic을 후보 revision 100%로 전환하고 실제 traffic이 그 revision 100%인지 확인
   8. 공개 주소에서 같은 smoke
   9. 후보 tag 제거(실패해도 job은 성공으로 두고 릴리스 노트에 결과를 남긴다)
   10. 공개 주소 smoke가 통과했을 때만 `update-job-images.sh`로 `decision-assistant-purge` ← backend digest, `decision-assistant-backup` ← backup digest를 `gcloud run jobs update --image`로 바꾸고, 갱신 뒤 job의 container image가 그 digest인지 확인한다. job을 실행하지는 않는다. job 조회가 실패하면(없음, 또는 job 수준 권한이 없어 없는 job과 구분되지 않음) 경고를 남기고 "건너뜀"으로, 갱신 명령이 거부되거나 확인 값이 다르면 "실패"로 기록한다. 어느 경우에도 서비스는 되돌리지 않고 `deploy` job은 성공으로 둔다
6. `release` job: 배포 job이 성공했을 때만 실행된다. 같은 이름의 tag가 없음을 다시 확인하고 배포 commit에 tag를 만들며 GitHub Release를 게시한다. 기본 `GITHUB_TOKEN`을 쓴다.

실행은 `concurrency`로 직렬화하고 진행 중인 실행을 취소하지 않는다. 승인을 기다리는 실행도 순서를 차지한다. 대기 중인 실행은 GitHub 동작상 하나만 남고 더 새 실행이 들어오면 대체된다.

### 실패했을 때의 상태

| 실패 지점 | traffic | tag·Release |
|---|---|---|
| `prepare`(ref, version 형식·중복) | 변화 없음. 클라우드 인증 전이다 | 만들지 않음 |
| 승인 거절·미승인 | 변화 없음 | 만들지 않음 |
| build, push, revision 생성 명령의 거부 | 이전 revision에 그대로. 새 revision이 생기지 않는다 | 만들지 않음 |
| revision 생성 뒤의 준비 실패, 후보 확인, 후보 smoke | 이전 revision에 그대로. 후보 revision은 남지만 traffic 0%이고 tag는 지운다. DB migration은 이미 적용됐을 수 있다 | 만들지 않음 |
| traffic 전환 확인 | 전환 명령이 일부 적용됐을 수 있다. log의 traffic 값을 보고 필요하면 rollback | 만들지 않음 |
| 공개 주소 smoke | **이미 새 revision**. log에 이전 revision으로 되돌리는 명령이 남는다. 자동 rollback은 하지 않는다 | 만들지 않음 |
| job image 갱신(건너뜀·실패) | 새 revision. 서비스 배포는 끝났다. 해당 job은 이전 image를 계속 쓴다 | 만든다. 릴리스 노트에 job별 결과가 남는다. 운영자가 job image를 확인한다 |
| `release`(tag가 그 사이 생김 등) | 새 revision. 배포는 끝났다 | 운영자가 확인해 수동으로 정리 |

## 릴리스 노트

`release` job이 [`scripts/release/notes.mjs`](../../scripts/release/notes.mjs)로 만든다. LLM·외부 API·추가 secret을 쓰지 않는다. 기존 `v0.2.0` Release의 항목(배포 커밋 해시, 마이그레이션, 배포 시각, 확인한 항목과 미검증 항목)을 따른다.

- 배포 커밋 해시: 전체 SHA.
- 마이그레이션: 직전 릴리스 tag와 배포 commit 사이에 `backend/src/main/resources/db/migration/`에 **추가된** 파일이 있으면 "포함"과 파일 목록, 없으면 "미포함". 스키마 버전은 그 시점 파일 중 가장 높은 `V<번호>__` 번호이며 "스키마 30 유지" 또는 "스키마 30 → 31"로 적는다.
- 직전 릴리스 tag: 배포 commit에 포함된 `vMAJOR.MINOR.PATCH` tag 중 가장 높은 것(배포 commit 자체를 가리키는 tag 제외).
- 배포 시각: Cloud Run revision 생성 시작부터 공개 주소 smoke 통과까지, UTC, 범위는 하이픈.
- 배포 대상: 서비스·리전·새 revision·배포 전 traffic revision·backend·frontend·backup image digest.
- 포함된 변경: 직전 tag 이후 `main`의 first-parent commit. `제목 (#번호)`는 `#번호 제목`으로, `풀 리퀘스트 병합 #번호`는 본문의 `* ` 항목 중 첫째를 제목으로 쓰고 나머지는 "외 N건"으로 적는다. 그 밖의 commit은 짧은 SHA와 제목이다.
- 확인한 항목: workflow 실행 결과에서 온 값(smoke 마지막 줄, traffic 확인 값, tag 제거 결과, job별 image 갱신 결과 "갱신함(job 이름)"·"건너뜀(...)"·"실패(...)")만 쓴다. 값이 없으면 "기록 없음"으로 남기며 성공으로 채우지 않는다.
- 미검증 항목: 자동 배포가 확인하지 않는 것(실제 로그인, 계정 간 격리, 쓰기 흐름, rollback 가능 여부, 두 job의 실제 실행 결과와 백업 복원, 로그 보존·비용)을 고정 목록으로 적는다. workflow는 job image만 바꾸고 job을 실행하지 않는다.

로컬 확인: `node scripts/release/notes.mjs version [--input=vX.Y.Z]`, `node scripts/release/notes.mjs notes --preview --commit=HEAD --version=vX.Y.Z`. 규칙의 자동 시험은 `scripts/release/notes.test.mjs`, job template의 정적 검사(placeholder, digest placeholder image, purge env와 서비스 backend env의 차이, secret 참조, backup script의 세션 table 데이터 제외)는 `scripts/cloudrun-templates.test.mjs`이며 둘 다 `npm run check:quick`에 포함된다.

### 보완 규칙

workflow가 만든 본문은 `<!-- generated:start -->`와 `<!-- generated:end -->` marker 사이에 있다.

- marker 안은 실행 결과에서 온 사실이므로 이후 수정하지 않는다. 틀린 내용을 발견하면 marker 안을 고치지 않고 보완 절에 정정으로 적는다.
- 에이전트나 human이 덧붙이는 내용은 marker 뒤의 `## 보완` 절에 쓴다. 항목마다 작성 주체(human 또는 에이전트 이름)와 작성일을 적고, 내용을 관찰(직접 확인한 결과와 출처)·추론(근거와 불확실성)·미검증으로 나눈다.
- 예: 실제 로그인 확인, 운영 중 발견한 문제, rollback 여부.

에이전트가 보완을 실행하는 자동화(에이전트 측 절차)는 아직 없다.

## 최초 생성과 설정 변경(운영자)

env·서비스 계정·scaling·resource 같은 서비스 설정은 workflow가 아니라 template을 render해 적용한다. 필요한 도구는 운영자 권한으로 인증된 `gcloud`, bash, `envsubst`(GNU gettext. Linux, Cloud Shell, Git Bash에 있음), `jq`다.

1. 저장소 밖의 비공개 파일(예: `~/decision-assistant/cloudrun.env`)에 값을 적는다. 이 파일과 render 결과는 commit하지 않는다.

   ```bash
   RUNTIME_SERVICE_ACCOUNT=decision-assistant-prod-backen@decision-assistant-prod.iam.gserviceaccount.com
   FRONTEND_IMAGE=<현재 운영 frontend image digest>
   BACKEND_IMAGE=<현재 운영 backend image digest>
   DEV_PROJECT_ID=<Dev project ID>
   PROD_PROJECT_ID=decision-assistant-prod
   DB_SECRET_VERSION=<숫자>
   AUTH0_SECRET_VERSION=<숫자>
   AUTH0_CLIENT_ID=<Auth0 client ID>
   AUTH0_ISSUER_URI=<Auth0 issuer>
   ALLOWED_EMAILS=<쉼표로 구분한 허용 email>
   PUBLIC_HOST=decision-assistant-277935636498.asia-southeast1.run.app
   NEON_HOST=<Neon host>
   DB_USERNAME=<Neon DB role>
   ```

2. image는 반드시 현재 운영 중인 digest를 넣는다. template에는 image도 들어 있어 다른 값을 넣으면 code가 그 버전으로 바뀐다. 현재 값은 다음으로 확인한다(env는 출력하지 않는다).

   ```bash
   gcloud run services describe decision-assistant --project decision-assistant-prod --region asia-southeast1 --format=json \
     | jq -r '.spec.template.spec.containers[] | "\(.name) \(.image)"'
   ```

3. 저장소 루트에서 render한다. 빈 값이 있거나 placeholder가 남으면 메시지가 나온다. 메시지가 나오면 적용하지 않는다.

   ```bash
   set -a; . ~/decision-assistant/cloudrun.env; set +a
   vars='RUNTIME_SERVICE_ACCOUNT FRONTEND_IMAGE BACKEND_IMAGE DEV_PROJECT_ID PROD_PROJECT_ID DB_SECRET_VERSION AUTH0_SECRET_VERSION AUTH0_CLIENT_ID AUTH0_ISSUER_URI ALLOWED_EMAILS PUBLIC_HOST NEON_HOST DB_USERNAME'
   for v in $vars; do [ -n "${!v:-}" ] || echo "$v 값이 비어 있습니다"; done
   out="$(mktemp -d)/service.yaml"
   envsubst "$(printf '${%s} ' $vars)" < deploy/cloudrun/service.template.yaml > "$out"
   grep -n '\${' "$out" && echo "남은 placeholder가 있습니다"
   ```

4. 적용하고 확인한 뒤 render 결과를 지운다.

   ```bash
   gcloud run services replace "$out" --project decision-assistant-prod --region asia-southeast1
   rm -r "$(dirname "$out")"
   bash deploy/cloudrun/smoke.sh https://decision-assistant-277935636498.asia-southeast1.run.app
   gcloud run services describe decision-assistant --project decision-assistant-prod --region asia-southeast1 --format=json \
     | jq '.status.traffic'
   ```

   `replace`는 새 revision을 만들고 smoke 없이 바로 traffic을 줄 수 있다. template에는 traffic 항목이 없다. `replace`로만 적용해 온 서비스의 traffic 대상은 2026-11-28 첫 workflow 실행 시점에 LATEST 100%였다(그 실행의 log로 관찰). workflow 배포가 한 번 성공한 뒤에는 traffic이 특정 revision에 고정되는데, 그 상태에서 `replace`가 traffic을 어떻게 두는지는 확인하지 않았다. 적용 뒤 traffic을 확인하고, 필요하면 아래 rollback 명령으로 원하는 revision을 지정한다. 이 경로는 운영자의 개인 `gcloud` 권한으로 실행되며 workflow의 승인 관문을 거치지 않는다.

최초 생성에도 같은 절차를 쓴다. 공개 호출 허용(`roles/run.invoker`), 위 신뢰 경계의 IAM binding, WIF provider, GitHub `production` environment의 보호 규칙은 template에 포함되지 않는 별도 설정이다. 앞의 셋은 2026-11-28에 리드가 human 승인 아래 만들었고, environment 보호 규칙은 human이 GitHub 설정에서 직접 구성한다. 다시 만들 때도 별도 승인을 받는다.

`DECISION_ASSISTANT_RECOVERY_RESTORED_TO`는 VM 구성처럼 복원 직후에만 쓰는 값이라 template에 넣지 않았다. 복원 절차를 Cloud Run에 맞게 정할 때 함께 다룬다.

## 수동 rollback

운영자가 자신의 `gcloud` 권한으로 실행한다.

```bash
gcloud run revisions list --service decision-assistant --project decision-assistant-prod --region asia-southeast1
gcloud run services update-traffic decision-assistant --project decision-assistant-prod --region asia-southeast1 \
  --to-revisions <이전 revision>=100
bash deploy/cloudrun/smoke.sh https://decision-assistant-277935636498.asia-southeast1.run.app
```

- 릴리스 노트와 배포 log의 "배포 전 traffic revision"이 직전 운영 revision이다.
- DB schema는 되돌리지 않는다. 이전 revision의 backend가 현재 schema에서 동작할 때만 rollback 대상이 된다([VM 문서의 복구 원칙](../README.md#백업복원과-삭제-재적용)과 같다).
- rollback은 tag·Release를 지우지 않는다. 해당 Release의 `## 보완` 절에 rollback 사실과 이유를 적는다.
- rollback 뒤 원인을 고치거나 revert하는 PR을 병합하고, 새 version으로 다시 배포한다.

## Cloud Run Job(휴지통 만료 삭제·백업)

[ADR 0025](../../docs/decisions/0025-cloud-run-neon-deployment.md) 결정 4·5의 구현이다. 서비스 인스턴스가 0개여도 실행되도록 Cloud Scheduler가 매일 두 job을 실행한다. 공개 HTTP endpoint는 추가하지 않는다.

| job | image | 하는 일 | 실행 시각(UTC) |
|---|---|---|---|
| `decision-assistant-purge` | 서비스와 같은 backend image | `DECISION_ASSISTANT_JOB=purge-expired-decisions`로 web server·예약 작업·Flyway·카탈로그 기동 작업 없이 기동해 만료된 휴지통 결정을 남은 대상이 없을 때까지 삭제하고 끝난다. `product_event=decision_purge_run outcome=success\|incomplete\|failed purged=<n> remaining=<n\|unknown>`을 남기고 성공이면 0, 아니면 1로 끝난다 | 매일 03:05 |
| `decision-assistant-backup` | [`backup/`](backup/)의 image(`postgres:17-alpine` + `curl`·`jq`) | `pg_dump -Fc`(TLS `sslmode=require`)로 dump를 임시 파일에 받고(로그인 세션 table `public.spring_session`·`public.spring_session_attributes`는 구조만 넣고 행은 넣지 않는다. 아래 신뢰 경계), 비어 있지 않은지와 `pg_restore --list`로 읽히는지 확인한 뒤 `gs://<bucket>/backups/neon/<UTC 시각>.dump`로 올린다. 마지막 줄은 `backup_completed object=... bytes=<n>`, 실패하면 `backup_failed reason=...`과 1 | 매일 03:00 |

일회 실행 mode는 backend의 별도 변경으로 들어간다. purge job은 그 변경이 포함된 backend image에서만 의도대로 동작한다.

### job의 신뢰 경계

- **실행 주체.** 두 job 모두 기존 runtime 서비스 계정으로 실행한다. 백업만을 위한 새 서비스 계정·DB role·secret은 만들지 않는다(2026-11-28 human 결정). purge job은 서비스와 같은 Secret bootstrap으로 `db-password`·`auth0-client-secret`을 읽고 운영 DB에서 삭제를 수행한다. backup job은 기존 애플리케이션 DB role로 읽기만 한다.
- **DB 비밀번호 전달.** backup job은 job 정의의 secret 참조(`db-password`, 숫자 version 고정, `latest` 금지)로 Cloud Run이 Secret Manager에서 읽어 `PGPASSWORD` env로 넣는다. job 정의와 `describe` 결과에는 secret 이름과 version만 보이고 값은 보이지 않는다. 값은 container 안의 process env에 있으므로 그 image가 실행하는 code는 값을 읽을 수 있다. script는 비밀번호·access token·접속 문자열을 출력하지 않는다.
- **Cloud Storage 권한.** upload에는 bucket의 `storage.objects.create`만 쓴다. script는 object를 읽거나 나열하지 않으며 `ifGenerationMatch=0`으로 기존 object를 덮어쓰지 않는다. access token은 metadata server에서 받아 권한 077의 임시 header 파일로만 `curl`에 넘긴다.
- **bucket.** 기존 비공개 bucket(US-CENTRAL1, uniform access, public access prevention, soft delete 끔, `backups/` 7일 삭제 lifecycle)을 그대로 쓴다. 새 object는 `backups/neon/` 아래에 쌓이고 같은 lifecycle로 지워진다. 전환 전까지 VM은 계속 `backups/<시각>.dump`를 쓴다(2026-11-28 human 결정).
- **세션 데이터 제외.** `pg_dump`는 `--exclude-table-data=public.spring_session --exclude-table-data=public.spring_session_attributes`로 두 세션 table의 행을 넣지 않고 table·index·제약 정의만 넣는다(2026-11-28 human 결정, [ADR 0025](../../docs/decisions/0025-cloud-run-neon-deployment.md) 결정 5). 세션 행에는 직렬화된 Spring Security context(OIDC ID token, email claim 포함)가 들어 있다. 이유는 세 가지다. 세션은 복원할 가치가 없고(복구 절차가 세션 무효화를 요구한다), 백업에 담기는 개인정보를 줄이며, 복원 뒤 이미 로그아웃한 세션이 되살아나지 않게 한다. 따라서 이 백업에서 복원한 database에는 로그인 세션이 없고 모든 사용자가 다시 로그인한다. 다른 table을 빼려면 같은 방식으로 이름을 명시해 추가하며 wildcard는 쓰지 않는다. VM 백업(`deploy/backup.sh`)은 바꾸지 않는다. 2026-11-28 기준 VM은 #116 이전 image를 실행해 세션을 DB에 저장하지 않고, 전환 뒤 종료할 대상이다.
- **배포용 계정.** workflow는 두 job의 image만 바꾸며 job을 실행하지 않는다. 그러려면 배포용 서비스 계정에 두 job 수준의 `roles/run.developer`가 있어야 하고, 리드가 job을 만든 뒤 human 승인 아래 따로 부여한다. 이 역할은 IAM상 job의 env·서비스 계정·secret 참조도 바꿀 수 있으며, image만 바꾸는 것은 서비스와 같이 workflow 내용으로 지키는 약속이다. 승인된 배포는 두 job에서도 임의의 code를 runtime 계정으로 실행할 수 있다(`actAs`).
- **Scheduler 호출 계정.** 전용 서비스 계정 `decision-assistant-scheduler`에 두 job 수준의 `roles/run.invoker`만 준다. 이 계정은 job 실행만 요청할 수 있고 job 정의를 바꾸지 못한다.
- **시험용 endpoint 변경.** `backup.sh`는 로컬 시험에서만 `BACKUP_TEST_ENDPOINTS=1`과 `BACKUP_METADATA_BASE_URL`·`BACKUP_STORAGE_BASE_URL`로 metadata·Storage 주소를 바꿀 수 있다. job 정의에는 넣지 않는다. 이 값 없이 주소 변경 env만 있으면 script가 실패한다.

### job 정의 render와 적용(운영자)

필요한 도구와 비공개 env 파일은 [최초 생성과 설정 변경](#최초-생성과-설정-변경운영자)과 같다. 같은 파일에 다음 두 값을 더한다.

```bash
BACKUP_IMAGE=<backup image digest>
BACKUP_BUCKET=<기존 비공개 backup bucket 이름>
```

- `BACKEND_IMAGE`는 일회 실행 mode가 포함된 backend image digest여야 한다. 서비스의 현재 backend digest를 쓰면 서비스와 같은 code가 된다.
- `BACKUP_IMAGE`는 workflow가 push한 digest를 쓴다. 이 저장소 변경 이후 첫 배포 실행이 `backup` image를 처음 push하며, 그 실행의 릴리스 노트 "backup image" 항목에 digest가 남는다. job이 아직 없으므로 그 실행의 job 갱신은 "건너뜀"으로 기록된다.
- 이미 있는 job을 다시 render할 때는 현재 image를 넣는다. template에는 image도 들어 있어 다른 값을 넣으면 job code가 바뀐다.

```bash
gcloud run jobs describe decision-assistant-purge --project decision-assistant-prod --region asia-southeast1 --format=json \
  | jq -r '.spec.template.spec.template.spec.containers[] | "\(.name) \(.image)"'
gcloud run jobs describe decision-assistant-backup --project decision-assistant-prod --region asia-southeast1 --format=json \
  | jq -r '.spec.template.spec.template.spec.containers[] | "\(.name) \(.image)"'
```

저장소 루트에서 render한다. 빈 값이나 남은 placeholder가 있으면 메시지가 나오고, 그때는 적용하지 않는다.

```bash
set -a; . ~/decision-assistant/cloudrun.env; set +a
out="$(mktemp -d)"
render() {  # render <template> <결과 파일> <변수 이름...>
  local template="$1" target="$2" v; shift 2
  for v in "$@"; do [ -n "${!v:-}" ] || echo "$v 값이 비어 있습니다"; done
  envsubst "$(printf '${%s} ' "$@")" < "$template" > "$target"
  if grep -n '\${' "$target"; then echo "$target 에 남은 placeholder가 있습니다"; fi
}
render deploy/cloudrun/purge-job.template.yaml "$out/purge-job.yaml" RUNTIME_SERVICE_ACCOUNT BACKEND_IMAGE \
  DEV_PROJECT_ID PROD_PROJECT_ID DB_SECRET_VERSION AUTH0_SECRET_VERSION AUTH0_CLIENT_ID AUTH0_ISSUER_URI ALLOWED_EMAILS \
  NEON_HOST DB_USERNAME
render deploy/cloudrun/backup-job.template.yaml "$out/backup-job.yaml" RUNTIME_SERVICE_ACCOUNT BACKUP_IMAGE \
  NEON_HOST DB_USERNAME DB_SECRET_VERSION BACKUP_BUCKET
```

적용하고 render 결과를 지운다. `replace`는 job을 만들거나 정의를 바꾸기만 하고 실행하지 않는다.

```bash
gcloud run jobs replace "$out/purge-job.yaml" --project decision-assistant-prod --region asia-southeast1
gcloud run jobs replace "$out/backup-job.yaml" --project decision-assistant-prod --region asia-southeast1
rm -r "$out"
```

### IAM(리드가 human 승인 아래 부여)

job을 만든 뒤 실행한다. 아래 변수는 이후 절에서도 쓴다.

```bash
PROJECT=decision-assistant-prod
REGION=asia-southeast1
RUNTIME=decision-assistant-prod-backen@decision-assistant-prod.iam.gserviceaccount.com
DEPLOYER=github-deployer@decision-assistant-prod.iam.gserviceaccount.com
INVOKER=decision-assistant-scheduler@decision-assistant-prod.iam.gserviceaccount.com
JOBS='decision-assistant-purge decision-assistant-backup'

# 배포용 계정: 두 job의 image 갱신(job 수준)
for job in $JOBS; do
  gcloud run jobs add-iam-policy-binding "$job" --project "$PROJECT" --region "$REGION" \
    --member "serviceAccount:$DEPLOYER" --role roles/run.developer
done

# runtime 계정: bucket에 object 생성만. 이미 있는지 먼저 본다(email만 출력).
gcloud storage buckets get-iam-policy "gs://$BACKUP_BUCKET" --format=json \
  | jq -r --arg m "serviceAccount:$RUNTIME" '.bindings[] | select(.members[]? == $m) | .role'
gcloud storage buckets add-iam-policy-binding "gs://$BACKUP_BUCKET" \
  --member "serviceAccount:$RUNTIME" --role roles/storage.objectCreator
```

runtime 계정이 `db-password`·`auth0-client-secret`을 읽는 권한은 서비스 구성에서 이미 있다(위 신뢰 경계).

### 수동 실행과 결과 확인(운영자)

Scheduler를 만들기 전에 각 job을 한 번 수동으로 실행해 확인한다. `--wait`는 실행이 끝날 때까지 기다린다.

```bash
gcloud run jobs execute decision-assistant-backup --project "$PROJECT" --region "$REGION" --wait
gcloud run jobs execute decision-assistant-purge --project "$PROJECT" --region "$REGION" --wait
gcloud run jobs executions list --job decision-assistant-backup --project "$PROJECT" --region "$REGION" --limit 5
gcloud run jobs executions list --job decision-assistant-purge --project "$PROJECT" --region "$REGION" --limit 5
gcloud logging read 'resource.type="cloud_run_job" AND resource.labels.job_name="decision-assistant-backup" AND ("backup_completed" OR "backup_failed")' \
  --project "$PROJECT" --freshness 2d --limit 5 --format 'value(timestamp,textPayload)'
gcloud logging read 'resource.type="cloud_run_job" AND resource.labels.job_name="decision-assistant-purge" AND "decision_purge_run"' \
  --project "$PROJECT" --freshness 2d --limit 5 --format json | jq -r '.[] | "\(.timestamp) \(.textPayload // .jsonPayload.message // "")"'
gcloud storage ls -l "gs://$BACKUP_BUCKET/backups/neon/"
```

- backup 성공은 `backup_completed` 줄과 bucket의 같은 이름 object 크기(`bytes`와 같음)로 판단한다. 실패하면 `backup_failed reason=` 값으로 단계(`pg_dump`, `empty_dump`, `unreadable_archive`, `metadata_token`, `upload_status status=<HTTP>`, `size_mismatch` 등)를 본다.
- purge 결과는 `decision_purge_run`의 `outcome`·`purged`·`remaining`으로 판단한다. backend가 log를 text와 JSON 중 어느 형식으로 남기는지는 실행해 보고 위 `jq` 식을 맞춘다(미검증).
- 실패한 task는 job 정의의 `maxRetries: 1`에 따라 한 번 더 실행된다. backup 재시도는 새 시각의 새 object를 만든다.

### Cloud Scheduler(운영자, human 승인 뒤)

결제 계정당 무료 3개 중 2개를 쓴다. 두 Scheduler job은 Cloud Run Jobs API의 `:run`을 OAuth token으로 호출하는 HTTP target이며, Cloud Run job과 같은 리전에 둔다. 명령을 실행하는 운영자는 `$INVOKER`에 대한 `iam.serviceAccounts.actAs`(예: `roles/iam.serviceAccountUser`)가 필요하다.

```bash
gcloud services enable cloudscheduler.googleapis.com --project "$PROJECT"
gcloud iam service-accounts create decision-assistant-scheduler --project "$PROJECT" \
  --display-name "Cloud Scheduler invoker for decision-assistant jobs"
for job in $JOBS; do
  gcloud run jobs add-iam-policy-binding "$job" --project "$PROJECT" --region "$REGION" \
    --member "serviceAccount:$INVOKER" --role roles/run.invoker
done

schedule() {  # schedule <job 이름> <cron(UTC)>
  gcloud scheduler jobs create http "$1" --project "$PROJECT" --location "$REGION" \
    --schedule "$2" --time-zone Etc/UTC \
    --uri "https://run.googleapis.com/v2/projects/$PROJECT/locations/$REGION/jobs/$1:run" \
    --http-method POST \
    --oauth-service-account-email "$INVOKER" \
    --oauth-token-scope https://www.googleapis.com/auth/cloud-platform
}
schedule decision-assistant-backup '0 3 * * *'
schedule decision-assistant-purge '5 3 * * *'

# 확인: 즉시 한 번 실행하고 상태를 본다.
gcloud scheduler jobs run decision-assistant-backup --project "$PROJECT" --location "$REGION"
gcloud scheduler jobs describe decision-assistant-backup --project "$PROJECT" --location "$REGION" \
  --format 'value(state,lastAttemptTime,status)'
```

- URI 형식(`https://run.googleapis.com/v2/projects/<project>/locations/<리전>/jobs/<job>:run`)과 `--http-method POST`·`--oauth-service-account-email`은 [Cloud Run 공식 문서 Execute jobs on a schedule](https://docs.cloud.google.com/run/docs/execute/jobs-on-schedule)의 예시와 같다. 그 문서에 따르면 Scheduler 리전은 job 리전과 같지 않아도 되지만, 여기서는 관리 위치를 줄이기 위해 같은 리전을 쓴다.
- Scheduler의 재시도 설정은 기본값으로 둔다. 실패 재시도는 Cloud Run job의 `maxRetries`가 맡는다.

### 복원 시험(운영자, Neon)

백업이 실제로 복원되는지 주기적으로 확인한다. 운영 DB를 덮어쓰지 않고, 버려도 되는 별도 Neon branch의 새 database에만 복원한다. 실제 장애 복구(삭제 audit 재적용, 복원 시각 안내, 세션 무효화)는 [VM 문서의 백업·복원과 삭제 재적용](../README.md#백업복원과-삭제-재적용) 원칙을 따르며, Cloud Run 구성에 맞춘 복구 절차는 아직 정하지 않았다.

1. Neon console에서 운영 branch의 child branch(예: `restore-test-<날짜>`)를 만들고 그 branch의 host와 접속할 role을 확인한다. 이 branch에도 운영 데이터 사본이 생기므로 시험이 끝나면 바로 지운다.
2. Cloud Shell(운영 project)에서 dump를 임시 디렉터리로 받고 `postgres:17-alpine`의 `pg_restore`로 새 database에 복원한다. dump를 개인 PC 등 GCP 밖으로 내려받는 것은 이 절차에 포함하지 않는다.

   ```bash
   object=backups/neon/<시각>.dump
   work="$(mktemp -d)"; chmod 700 "$work"
   gcloud storage cp "gs://$BACKUP_BUCKET/$object" "$work/backup.dump"
   export PGHOST=<restore branch host> PGUSER=<role> PGSSLMODE=require
   read -rs PGPASSWORD; export PGPASSWORD
   docker run --rm -e PGHOST -e PGUSER -e PGPASSWORD -e PGSSLMODE -v "$work:/work:ro" postgres:17-alpine sh -c '
     createdb restore_check &&
     pg_restore --exit-on-error --no-owner --no-acl --dbname=restore_check /work/backup.dump'
   ```

3. branch에 원래 있던 `decision_assistant`와 복원한 `restore_check`의 table별 행 수와 Flyway 버전을 비교한다. branch는 백업 뒤에 만들었으므로 그 사이의 쓰기만큼 차이가 날 수 있다.

   `spring_session`·`spring_session_attributes`는 백업에 행이 없으므로([세션 데이터 제외](#job의-신뢰-경계)) 복원 쪽이 0행인 것이 정상이고, 원본에 세션이 있으면 이 두 줄만 다르게 나온다. 그래서 두 table은 비교에서 빼고 복원 쪽이 0행인지 따로 확인한다.

   저장소 루트에서 실행한다. [`restore-counts.sql`](restore-counts.sql)은 Flyway 버전과 `public` schema의 table별 행 수를 출력한다.

   ```bash
   for db in decision_assistant restore_check; do
     docker run --rm -e PGHOST -e PGUSER -e PGPASSWORD -e PGSSLMODE -v "$PWD/deploy/cloudrun:/sql:ro" postgres:17-alpine \
       psql -d "$db" -At -v ON_ERROR_STOP=1 -f /sql/restore-counts.sql > "$work/$db.txt"
   done
   sessions='^spring_session(_attributes)? '
   grep -Ev "$sessions" "$work/decision_assistant.txt" > "$work/source.cmp"
   grep -Ev "$sessions" "$work/restore_check.txt" > "$work/restored.cmp"
   diff "$work/source.cmp" "$work/restored.cmp" && echo "세션 table 외 차이 없음"
   grep -E "$sessions" "$work/restore_check.txt"  # 기대값: spring_session 0, spring_session_attributes 0
   ```

4. 정리한다: `unset PGPASSWORD; rm -rf "$work"`, Neon console에서 restore branch를 삭제한다. 결과(대상 object, 복원 성공 여부, 차이)를 운영 기록에 남긴다.

## 확인한 항목과 미검증 항목

확인함:

- 리드가 2026-11-28 위 template과 같은 구조로 배포: `/login` 200, 미인증 `/api/v1/me` 401, Flyway 30 적용, backend 기동 약 25초, Cloud Run 서비스 계정 ADC로 기존 Secret bootstrap 동작. 운영자가 로그인·결정 생성·목록·비교·로그아웃·재로그인을 확인했다(PR #103 수정 포함 revision `decision-assistant-00002-dq6`).
- 로컬(Windows, Docker 29): 두 image build, frontend image의 rewrite 대상이 `http://localhost:8080`, 로컬 registry push 뒤 `RepoDigests`에서 registry digest 추출(registry의 `Docker-Content-Digest`와 일치), template YAML parse와 render, backend env 이름·고정값의 `deploy/compose.yaml` 대조, `smoke.sh`의 공개 주소 통과와 실패·입력 거부 동작, `notes.mjs`의 version 결정과 `v0.2.0` 이후 실제 Git 기록으로 만든 릴리스 노트.
- `production` environment 구성(2026-11-28 첫 실행 전 리드가 GitHub API로 조회): required reviewer는 저장소 소유자 1명, 배포 branch 정책은 `main`. branch 제한이 다른 branch의 실행을 실제로 막는지는 확인하지 않았다(첫 실행은 `main`에서 시작됐다).
- [첫 실행](https://github.com/lawliet62/Decision-Assistant/actions/runs/46130545418)(2026-11-28, `main` `805986c`, human이 `workflow_dispatch`로 시작): 실행의 승인 기록에 저장소 소유자의 `production` 승인이 있고 `deploy` job은 그 뒤에 시작됐다. WIF 인증(environment 조건 포함), Artifact Registry push, registry digest 확보까지 통과했다. 서비스 수준 `roles/run.developer`로 `services describe`(배포 전 revision 조회)와 `update-traffic --remove-tags`(정리 단계)가 허용됐다. `gcloud run services update`는 인자 해석에서 거부돼 revision을 만들지 못했고 `release` job은 건너뛰어 tag·Release가 생기지 않았다([사례](../../docs/troubleshooting/gcloud-run-container-flag-order.md)). 정리 단계의 출력은 traffic `100% LATEST (currently decision-assistant-00002-dq6)`였다. 그 시점 서비스의 traffic 대상은 특정 revision이 아니라 LATEST였다.
- [두 번째 실행](https://github.com/lawliet62/Decision-Assistant/actions/runs/46134019406)(2026-11-28, `main` `6ebd2fd`, human이 시작·승인)이 모든 단계를 통과해 `v0.2.1`을 배포했다.
  - `services update`가 traffic 0%의 revision `decision-assistant-00003-yan`을 만들었다. 첫 실행 시점에 traffic 대상은 LATEST였고, 이 실행에서 `--no-traffic`은 기존 traffic을 `decision-assistant-00002-dq6`에 둔 채 새 revision을 만들었다.
  - 후보 tag 주소는 `https://candidate---decision-assistant-<hash>-as.a.run.app` 형식이었고 smoke가 1회 만에 통과했다.
  - `update-traffic --to-revisions` 뒤 traffic은 새 revision 100%였고 공개 주소 smoke가 통과했다. 후보 tag는 제거됐다. 서비스 수준 `roles/run.developer`로 revision 생성과 traffic 전환이 모두 허용됐다.
  - `release` job이 tag `v0.2.1`(배포 commit)과 GitHub Release를 만들었고 릴리스 노트가 게시됐다.
  - 배포 뒤 리드가 두 revision을 조회해 비교했다. spec에서 다른 값은 두 container의 image뿐이고 env 개수(backend 21, frontend 0), startup probe, `container-dependencies`, 최대 인스턴스 수, 서비스 계정이 같았다. `--container ... --image`만 지정한 update는 나머지 설정을 바꾸지 않았다.
  - 배포 뒤 서비스의 traffic 대상은 LATEST가 아니라 `decision-assistant-00003-yan`으로 고정돼 있다.
  - 배포 뒤 운영자가 새 revision을 로그인한 상태로 사용했다. 요청 log에는 결정 생성(201), 초안 저장과 추천 후보 변경(200), 페이지·API 조회(200)가 남았고 error 수준 container log는 없었다. 운영자는 이어서 예산 없이 만든 결정에서 비교 단계로 이동할 때 Next.js 기본 오류 화면이 나왔다고 보고했다. 원인 분석과 수정은 별도 PR에서 다룬다.
- Cloud Run Job 자산(2026-11-28, 에이전트가 로컬과 인자 해석으로 확인. 실제 GCP 리소스는 만들지 않았다):
  - 로컬(Windows, Docker 29): backup image build, 실행 사용자 uid 10001. TLS를 켠 `postgres:17-alpine` container와 가짜 metadata·Storage server(Node)를 외부 연결이 없는 Docker network에 두고 실행했을 때 exit 0과 `backup_completed object=gs://test-bucket/backups/neon/<시각>.dump bytes=7309`. 가짜 server가 받은 요청은 `POST /upload/storage/v1/b/test-bucket/o?uploadType=media&ifGenerationMatch=0&name=backups%2Fneon%2F<시각>.dump`, `Authorization: Bearer <token>`, 본문 7309 byte였다. 받은 본문을 `pg_restore --exit-on-error --no-owner`로 새 database에 복원해 두 table의 행 수(120, 345)와 내용 hash가 원본과 같았다.
  - 실패 경로(모두 exit 1, upload 전 단계는 upload 요청 없음): 틀린 비밀번호(`reason=pg_dump`), Storage 412·403·500(`reason=upload_status status=<code>`), 응답 크기 불일치(`reason=size_mismatch`), 빈 dump(`reason=empty_dump`), archive가 아닌 dump(`reason=unreadable_archive`), `PGSSLMODE=disable`, 시험 flag 없는 주소 변경, 비밀번호 env 없음, metadata server 접속 불가.
  - gcloud 570.0.0(클라우드 작업 공간, API 접속 차단 환경)에서 가짜 값으로 render한 두 job 정의가 `gcloud run jobs replace`의 YAML·schema 해석을 통과해 API 호출 단계까지 갔다. 모르는 field를 넣으면 `Failed to parse value(s) in protobuf [Job]`로 거부되는 것도 확인했다. workflow의 `jobs describe --format=value(metadata.name)`, `jobs describe --format=json`, `jobs update --quiet --image <digest>`와 이 절의 `scheduler jobs create http`, `run jobs add-iam-policy-binding`, `run jobs execute --wait`, `run jobs executions list`, `scheduler jobs run`, `storage buckets add-iam-policy-binding`도 같은 방식으로 인자 해석을 통과했다.
  - `update-job-images.sh`를 가짜 `gcloud`로 실행: 정상 갱신, job 없음(`Cannot find job`), 권한 거부, 갱신 거부, 갱신 뒤 image 불일치를 각각 "갱신함"·"건너뜀(job 없음)"·"건너뜀(job 조회 실패...)"·"실패(...)"로 기록하고 항상 exit 0. describe의 env 값은 출력하지 않았다.
  - Git Bash에서 이 절의 `render` 함수(빈 값·남은 placeholder 메시지 포함)와 `restore-counts.sql`의 비교(같으면 "차이 없음", 행을 지우면 차이 감지)를 가짜 값과 로컬 DB로 실행했다.
- 세션 데이터 제외(2026-11-28, 에이전트가 로컬에서 확인. Windows, Docker 29): 외부 연결이 없는 Docker network에 TLS를 켠 `postgres:17-alpine`과 가짜 metadata·Storage server를 두고, V3 migration과 같은 모양의 세션 두 table과 일반 table에 행을 넣은 뒤 새로 build한 backup image를 그대로 실행했다. exit 0, `backup_completed`. 받은 dump의 `pg_restore --list`에는 네 table 정의가 모두 있고 `TABLE DATA`는 세션이 아닌 두 table에만 있었다. `pg_restore --exit-on-error --no-owner --no-acl`로 새 database에 복원한 결과 `spring_session` 3 → 0, `spring_session_attributes` 6 → 0, `decisions` 5 → 5(내용 hash 같음), `flyway_schema_history` 1 → 1이었고 세션 table의 index 5개(PK index 2개 포함)와 제약 3개(PK 2개, FK 1개)가 남았다. 위 3단계 비교 명령의 `grep`·`diff` 부분은 클라우드 작업 공간의 bash에서 이 행 수 출력을 넣어 "세션 table 외 차이 없음"과 두 세션 table 0행을 내고, 일반 table 행 수를 바꾸면 차이를 내는 것을 확인했다.

미검증:

- Cloud Run Job 전반: 실제 job 생성(`jobs replace`), 실제 실행, Scheduler 호출. 특히 다음은 실제 실행으로만 확인할 수 있다.
  - `roles/storage.objectCreator`만으로 `ifGenerationMatch=0` upload가 허용되고 응답에 `size`가 오는지. 오지 않으면 script가 `size_mismatch`로 실패한다.
  - 없는 job을 job 수준 권한만 가진 배포용 계정이 조회할 때의 오류가 NOT_FOUND인지 권한 거부인지(둘 다 "건너뜀"으로 기록하지만 문구가 다르다). 실제 NOT_FOUND 오류 문구(`Cannot find job`)도 확인하지 않았다.
  - `gcloud run jobs update --image`가 image 외의 job 설정을 바꾸지 않는지(서비스는 두 번째 실행에서 확인했다).
  - `roles/run.invoker`가 overrides 없는 `:run`만 허용하는지, Cloud Scheduler가 `asia-southeast1`에서 쓸 수 있는지, `gcloud run jobs execute --wait`가 실패한 실행에서 0이 아닌 값으로 끝나는지.
  - Neon(PostgreSQL 17)에 `postgres:17-alpine`의 `pg_dump`가 TLS로 접속하는지, Neon restore branch의 role이 `createdb`를 할 수 있는지.
  - 실제 운영 DB의 dump에서 세션 두 table의 행이 빠지는지. 승인된 다음 배포로 새 backup image가 job에 들어간 뒤 첫 실행과 복원 시험에서 확인한다.
  - purge job의 env 구성이 일회 실행 mode에서 충분한지. 특히 `DECISION_ASSISTANT_ALLOWED_EMAILS`는 비어 있으면 기동이 실패하는 bean이 있어 남겼고, 그 mode에서 해당 bean이 만들어지지 않으면 뺄 수 있다.
- 비용: job 실행의 vCPU·메모리 사용은 Cloud Run 무료 범위 안으로 예상하지만 측정하지 않았다. backup job(asia-southeast1)에서 bucket(US-CENTRAL1)으로의 지역 간 전송과 Neon의 외부 전송량이 0원으로 유지되는지는 확인하지 않았다.
- workflow의 job 관련 변경: runner에서의 backup image build·push, `Update job images` 단계, 그 결과가 `deploy` job output을 거쳐 릴리스 노트에 들어가는지. 승인된 실제 실행 전에는 확인할 수 없다.
- 실패 경로의 실제 동작: 후보 smoke 실패 시 traffic 유지, 공개 주소 smoke 실패 시의 안내, rollback 명령. 두 실행 모두 이 경로를 지나지 않았다.
- 새 revision에서의 로그아웃·재로그인, 계정 간 격리. workflow의 smoke는 `/login`과 미인증 `/api/v1/me`만 본다.
- traffic이 특정 revision에 고정된 상태에서 `services replace`가 traffic을 어떻게 두는지.
- runner의 Docker가 push하는 manifest 형식(로컬은 containerd image store라 OCI index였다).
- `actionlint` 검사(PC에 없어 YAML parse와 기존 workflow 대조로 대신했다).
- Artifact Registry 사용량. 저장소에는 cleanup policy(7일 지난 version 삭제, 최근 6개 version 유지)가 있다(2026-11-28 리드 관찰). 이 정책으로 저장량이 무료 범위 안에 머무는지는 확인하지 않았다.
