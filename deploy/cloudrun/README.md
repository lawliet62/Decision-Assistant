# Cloud Run 배포

[ADR 0025](../../docs/decisions/0025-cloud-run-neon-deployment.md)의 Cloud Run 단일 서비스와 GitHub Actions 배포를 구현한 자산이다. 전환(cutover)이 끝날 때까지는 [단일 VM 문서](../README.md)의 구성과 절차가 운영 기준이며, 이 문서의 Cloud Run 서비스는 전환 전 검증 서비스다. 휴지통 만료 삭제의 일회 실행 경로, 백업 job, Cloud Scheduler, 도메인 연결은 아직 없다.

배포는 human이 승인한 수동 실행에서만 일어난다. `main` 병합은 배포를 일으키지 않는다(2026-11-28 human 결정).

## 구성

| 파일 | 역할 |
|---|---|
| [`.github/workflows/deploy-cloud-run.yml`](../../.github/workflows/deploy-cloud-run.yml) | 수동 실행. 준비(검사·미리보기) → 승인 → 배포 → tag·Release |
| [`service.template.yaml`](service.template.yaml) | 서비스 정의 template. 운영자가 render해 `gcloud run services replace`로 적용 |
| [`smoke.sh`](smoke.sh) | `/login` 200과 미인증 `/api/v1/me` 401을 제한 시간 안에 재시도하며 확인 |
| [`scripts/release/notes.mjs`](../../scripts/release/notes.mjs) | version 결정과 규칙 기반 릴리스 노트 생성(Git 기록과 실행 결과만 사용) |

리소스(2026-11-28 생성·관찰):

- GCP project `decision-assistant-prod`(number `277935636498`), 리전 `asia-southeast1`
- Artifact Registry 저장소 `asia-southeast1-docker.pkg.dev/decision-assistant-prod/decision-assistant`, image `backend`·`frontend`
- Cloud Run 서비스 `decision-assistant`, 공개 주소 `https://decision-assistant-277935636498.asia-southeast1.run.app`
- runtime 서비스 계정 `decision-assistant-prod-backen@decision-assistant-prod.iam.gserviceaccount.com`
- 배포용 서비스 계정 `github-deployer@decision-assistant-prod.iam.gserviceaccount.com`
- WIF provider `projects/277935636498/locations/global/workloadIdentityPools/github/providers/decision-assistant`

서비스 구조: `frontend` container(Next.js, port 3000)가 외부 요청을 받는 ingress이고 `backend` container(Spring Boot, 8080)는 같은 인스턴스의 sidecar다. frontend image는 `BACKEND_URL=http://localhost:8080`으로 build하며 rewrites가 이 주소에 고정된다. `container-dependencies`로 backend가 먼저 뜨고, backend startup probe(TCP 8080)는 최대 240초를 기다린다. 인스턴스는 0-1개, 요청이 없을 때 CPU를 할당하지 않는다.

## 신뢰 경계

배포 권한은 human에게 종속되며 GitHub와 WIF 설정으로 강제한다.

- **(가) 승인 관문.** workflow의 클라우드 단계(WIF 인증, Artifact Registry push, Cloud Run 배포, traffic 전환)는 모두 `environment: production`을 선언한 `deploy` job 하나에 있다. 이 environment에는 human이 GitHub 설정에서 required reviewer(저장소 소유자)와 배포 branch `main` 제한을 직접 구성한다. environment 설정은 저장소 관리자만 바꿀 수 있고 bot에는 그 권한이 없다. 에이전트(bot)는 workflow를 실행할 수 있지만 `deploy` job은 required reviewer인 human이 승인해야 시작된다.
- **(나) WIF 조건.** provider는 `repository_id=1339097882`, `repository_owner_id=102518515`, `ref=refs/heads/main`인 GitHub OIDC token만 받고, 배포용 서비스 계정의 `roles/iam.workloadIdentityUser`는 `attribute.workflow_ref`가 `lawliet62/Decision-Assistant/.github/workflows/deploy-cloud-run.yml@refs/heads/main`인 주체에만 있다(2026-11-28 관찰). provider 조건에는 `assertion.environment=='production'`도 있어 `production` environment를 선언한 job의 token만 받는다(2026-11-28 리드가 적용하고 조회로 확인. 같은 날 첫 실행에서 승인된 `deploy` job의 인증이 통과했다. 조건에 맞지 않는 token이 거부되는지는 실제 인증으로 확인하지 않았다). 다른 branch, PR, 다른 workflow 파일, 승인되지 않은 job은 이 계정을 쓸 수 없다. workflow 파일 이름이나 environment 이름을 바꾸면 GCP 조건도 바꿔야 한다.
- **(다) 관문의 범위.** 이 관문은 GitHub와 WIF 범위의 강제다. 에이전트가 PC에서 human의 개인 `gh` 로그인이나 배포 권한이 있는 `gcloud` 계정을 쓸 수 있다면 우회된다. 그 격리는 운영 에이전트의 권한 구성에서 따로 다룬다(미해결).
- **(라) 대리 승인 경로 없음.** "human이 명령하면 에이전트가 직접 승인·배포"하는 경로는 두지 않는다. 그런 경로는 에이전트의 자기 승인과 구분해 강제할 수 없기 때문이다.
- environment 이름은 정확히 `production`이어야 한다. workflow가 존재하지 않는 environment 이름을 참조하면 GitHub가 **보호 규칙 없는 environment를 자동으로 만들어** 승인 없이 job이 실행된다. 이름을 바꾸거나 새 job에 environment를 붙일 때는 GitHub 설정에 같은 이름의 보호 규칙이 먼저 있어야 한다.
- 서비스 계정 key와 GitHub secret은 없다. workflow에 적힌 project·리전·서비스·저장소·provider·계정 email은 식별자이며 비밀값이 아니다.
- 배포용 서비스 계정의 권한은 Artifact Registry 저장소 수준 `roles/artifactregistry.writer`, Cloud Run 서비스 수준 `roles/run.developer`, runtime 서비스 계정에 대한 `roles/iam.serviceAccountUser`뿐이다. project 수준 역할과 user-managed key는 없다(2026-11-28 관찰).
- **승인된 배포는 임의의 code를 runtime 서비스 계정으로 실행할 수 있다(`actAs`).** runtime 계정은 Prod Secret(`db-password`, `auth0-client-secret`)을 읽으므로, workflow·backend code를 바꾸는 PR의 병합과 배포 승인은 Prod Secret 접근과 같은 무게로 검토한다. 병합과 승인은 human만 한다.
- `roles/run.developer`는 IAM상 env·서비스 계정·scaling 같은 서비스 설정도 바꿀 수 있다. workflow가 image만 바꾸는 것은 IAM이 아니라 workflow 내용으로 지키는 약속이다. 이렇게 나눈 이유는 허용 email 목록 같은 비공개 값이 저장소와 Actions log에 들어가지 않게 하기 위해서다.
- GitHub에 쓰는 권한(`contents: write`)은 `release` job에만 있고 이 job에는 클라우드 인증이 없다. 준비 job은 `contents: read`만 갖는다.
- workflow log에는 image digest, revision 이름, 후보 주소, smoke의 HTTP status만 남는다. `gcloud run services describe` 결과는 env를 포함하므로 jq로 필요한 값만 꺼내고 원문을 출력하지 않는다. image는 GCP 인증 전에 build해 build context에 인증 파일이 생기기 전 상태로 만든다.
- 후보 revision은 traffic 0%이지만 tag 주소(`candidate---…`)로 직접 열린다. 서비스가 공개 호출을 허용하므로 배포 중에는 이 주소도 같은 조건으로 공개된다. workflow는 끝날 때 성공·실패와 무관하게 tag를 지운다.
- 후보 revision은 운영 DB(Neon)에 연결하고 기동할 때 Flyway migration을 적용한다. **smoke가 실패해 traffic이 이전 revision에 남아도 schema는 이미 새 버전이다.** migration은 이전 backend가 계속 동작하는 형태로 작성해야 한다.

## 수동 배포 절차

1. GitHub Actions에서 **Cloud Run deploy**를 고르고 branch `main`으로 **Run workflow**를 실행한다. 에이전트가 실행해도 된다.
2. `version` 입력은 선택이다. 형식은 `vMAJOR.MINOR.PATCH`(앞자리 0 금지, 예: `v0.3.0`)다. 비우면 가장 최근 semver tag의 patch를 1 올린다(예: `v0.2.0` → `v0.2.1`). 형식이 틀리거나 이미 있는 tag면 `prepare` job이 실패하고 배포는 시작되지 않는다.
3. `prepare` job이 끝나면 실행 화면의 job summary에 릴리스 노트 미리보기(배포 commit, 마이그레이션 포함 여부와 스키마 버전, 직전 tag 이후 변경 목록)가 나온다. 배포 결과 항목은 "배포 후 기록"으로 표시된다.
4. required reviewer(human)가 미리보기를 보고 `production` 배포를 승인한다. 거절하거나 승인하지 않으면 아무것도 배포되지 않는다.
5. `deploy` job: 실행을 시작한 commit(`github.sha`)을 build한다. 승인을 기다리는 동안 `main`이 앞서가도 배포 대상은 바뀌지 않는다.
   1. Java 21로 backend `bootJar`, backend·frontend image build(`linux/amd64`, frontend는 `BACKEND_URL=http://localhost:8080`), tag는 commit SHA
   2. WIF 인증, Artifact Registry push, registry digest 확보
   3. 배포 전 traffic 100% revision을 log에 남김
   4. `gcloud run services update --no-traffic --tag candidate --container frontend --image <digest> --container backend --image <digest>`로 traffic 없는 새 revision 생성. 서비스 단위 flag는 첫 `--container` 앞에 둔다([사례](../../docs/troubleshooting/gcloud-run-container-flag-order.md)). 새 revision이 준비되지 않으면 여기서 실패한다
   5. 후보 tag의 revision이 최신 생성·준비 revision인지, 서비스 template의 image가 이번 digest인지 확인
   6. 후보 tag 주소에서 smoke(최대 300초 재시도)
   7. traffic을 후보 revision 100%로 전환하고 실제 traffic이 그 revision 100%인지 확인
   8. 공개 주소에서 같은 smoke
   9. 후보 tag 제거(실패해도 job은 성공으로 두고 릴리스 노트에 결과를 남긴다)
6. `release` job: 배포 job이 성공했을 때만 실행된다. 같은 이름의 tag가 없음을 다시 확인하고 배포 commit에 tag를 만들며 GitHub Release를 게시한다. 기본 `GITHUB_TOKEN`을 쓴다.

실행은 `concurrency`로 직렬화하고 진행 중인 실행을 취소하지 않는다. 승인을 기다리는 실행도 순서를 차지한다. 대기 중인 실행은 GitHub 동작상 하나만 남고 더 새 실행이 들어오면 대체된다.

### 실패했을 때의 상태

| 실패 지점 | traffic | tag·Release |
|---|---|---|
| `prepare`(ref, version 형식·중복) | 변화 없음. 클라우드 인증 전이다 | 만들지 않음 |
| 승인 거절·미승인 | 변화 없음 | 만들지 않음 |
| build, push, revision 생성, 후보 확인, 후보 smoke | 이전 revision에 그대로. 후보 revision은 남지만 traffic 0%이고 tag는 지운다. DB migration은 이미 적용됐을 수 있다 | 만들지 않음 |
| traffic 전환 확인 | 전환 명령이 일부 적용됐을 수 있다. log의 traffic 값을 보고 필요하면 rollback | 만들지 않음 |
| 공개 주소 smoke | **이미 새 revision**. log에 이전 revision으로 되돌리는 명령이 남는다. 자동 rollback은 하지 않는다 | 만들지 않음 |
| `release`(tag가 그 사이 생김 등) | 새 revision. 배포는 끝났다 | 운영자가 확인해 수동으로 정리 |

## 릴리스 노트

`release` job이 [`scripts/release/notes.mjs`](../../scripts/release/notes.mjs)로 만든다. LLM·외부 API·추가 secret을 쓰지 않는다. 기존 `v0.2.0` Release의 항목(배포 커밋 해시, 마이그레이션, 배포 시각, 확인한 항목과 미검증 항목)을 따른다.

- 배포 커밋 해시: 전체 SHA.
- 마이그레이션: 직전 릴리스 tag와 배포 commit 사이에 `backend/src/main/resources/db/migration/`에 **추가된** 파일이 있으면 "포함"과 파일 목록, 없으면 "미포함". 스키마 버전은 그 시점 파일 중 가장 높은 `V<번호>__` 번호이며 "스키마 30 유지" 또는 "스키마 30 → 31"로 적는다.
- 직전 릴리스 tag: 배포 commit에 포함된 `vMAJOR.MINOR.PATCH` tag 중 가장 높은 것(배포 commit 자체를 가리키는 tag 제외).
- 배포 시각: Cloud Run revision 생성 시작부터 공개 주소 smoke 통과까지, UTC, 범위는 하이픈.
- 배포 대상: 서비스·리전·새 revision·배포 전 traffic revision·backend와 frontend image digest.
- 포함된 변경: 직전 tag 이후 `main`의 first-parent commit. `제목 (#번호)`는 `#번호 제목`으로, `풀 리퀘스트 병합 #번호`는 본문의 `* ` 항목 중 첫째를 제목으로 쓰고 나머지는 "외 N건"으로 적는다. 그 밖의 commit은 짧은 SHA와 제목이다.
- 확인한 항목: workflow 실행 결과에서 온 값(smoke 마지막 줄, traffic 확인 값, tag 제거 결과)만 쓴다. 값이 없으면 "기록 없음"으로 남기며 성공으로 채우지 않는다.
- 미검증 항목: 자동 배포가 확인하지 않는 것(실제 로그인, 계정 간 격리, 쓰기 흐름, rollback 가능 여부, 백업, 만료 삭제, 로그 보존·비용)을 고정 목록으로 적는다.

로컬 확인: `node scripts/release/notes.mjs version [--input=vX.Y.Z]`, `node scripts/release/notes.mjs notes --preview --commit=HEAD --version=vX.Y.Z`. 규칙의 자동 시험은 `scripts/release/notes.test.mjs`이며 `npm run check:quick`에 포함된다.

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

   `replace`는 새 revision을 만들고 smoke 없이 바로 traffic을 줄 수 있다. template에는 traffic 항목이 없어 적용 뒤 traffic이 최신 revision 100%가 될 것으로 보지만 확인하지 않았다(추론). 적용 뒤 traffic을 확인하고, 필요하면 아래 rollback 명령으로 원하는 revision을 지정한다. 이 경로는 운영자의 개인 `gcloud` 권한으로 실행되며 workflow의 승인 관문을 거치지 않는다.

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

## 확인한 항목과 미검증 항목

확인함:

- 리드가 2026-11-28 위 template과 같은 구조로 배포: `/login` 200, 미인증 `/api/v1/me` 401, Flyway 30 적용, backend 기동 약 25초, Cloud Run 서비스 계정 ADC로 기존 Secret bootstrap 동작. 운영자가 로그인·결정 생성·목록·비교·로그아웃·재로그인을 확인했다(PR #103 수정 포함 revision `decision-assistant-00002-dq6`).
- 로컬(Windows, Docker 29): 두 image build, frontend image의 rewrite 대상이 `http://localhost:8080`, 로컬 registry push 뒤 `RepoDigests`에서 registry digest 추출(registry의 `Docker-Content-Digest`와 일치), template YAML parse와 render, backend env 이름·고정값의 `deploy/compose.yaml` 대조, `smoke.sh`의 공개 주소 통과와 실패·입력 거부 동작, `notes.mjs`의 version 결정과 `v0.2.0` 이후 실제 Git 기록으로 만든 릴리스 노트.
- `production` environment 구성(2026-11-28 첫 실행 전 리드가 GitHub API로 조회): required reviewer는 저장소 소유자 1명, 배포 branch 정책은 `main`. branch 제한이 다른 branch의 실행을 실제로 막는지는 확인하지 않았다(첫 실행은 `main`에서 시작됐다).
- [첫 실행](https://github.com/lawliet62/Decision-Assistant/actions/runs/46130545418)(2026-11-28, `main` `805986c`, human이 `workflow_dispatch`로 시작): 실행의 승인 기록에 저장소 소유자의 `production` 승인이 있고 `deploy` job은 그 뒤에 시작됐다. WIF 인증(environment 조건 포함), Artifact Registry push, registry digest 확보까지 통과했다. 서비스 수준 `roles/run.developer`로 `services describe`(배포 전 revision 조회)와 `update-traffic --remove-tags`(정리 단계)가 허용됐다. `gcloud run services update`는 인자 해석에서 거부돼 revision을 만들지 못했고 `release` job은 건너뛰어 tag·Release가 생기지 않았다([사례](../../docs/troubleshooting/gcloud-run-container-flag-order.md)). 정리 단계의 출력은 traffic `100% LATEST (currently decision-assistant-00002-dq6)`였다. 서비스의 traffic 대상은 특정 revision이 아니라 LATEST다.
- 수정한 flag 순서의 `services update`와 `update-traffic --to-revisions`·`--remove-tags`, `services describe`가 gcloud 570.0.0에서 인자 해석을 통과하는 것(API 호출 전 단계까지만 확인).

미검증:

- `gcloud run services update`의 multi-container image 교체와 `--no-traffic --tag`의 실제 동작, `update-traffic`, `gh release create`. `main`에서 승인된 실행으로만 확인할 수 있다.
- `--container ... --image`만 지정한 update가 env·`container-dependencies`·probe 등 나머지 설정을 그대로 두는지. gcloud 동작상 그럴 것으로 보지만 실제 revision으로 확인하지 않았다.
- 서비스 수준 `roles/run.developer`로 `services update`(revision 생성)와 `update-traffic --to-revisions`가 허용되는지.
- traffic 대상이 LATEST인 상태에서 `--no-traffic`이 기존 traffic을 현재 revision에 고정한 뒤 새 revision을 만드는지. gcloud 문서상 그렇게 동작하지만 실제 실행으로 확인하지 않았다.
- 후보 tag 주소의 실제 형식.
- runner의 Docker가 push하는 manifest 형식(로컬은 containerd image store라 OCI index였다).
- `actionlint` 검사(PC에 없어 YAML parse와 기존 workflow 대조로 대신했다).
- Artifact Registry 사용량. 저장소에는 cleanup policy(7일 지난 version 삭제, 최근 6개 version 유지)가 있다(2026-11-28 리드 관찰). 이 정책으로 저장량이 무료 범위 안에 머무는지는 확인하지 않았다.
