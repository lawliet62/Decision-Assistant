# Development workflow

## Prerequisites

`scripts/gradle.mjs`와 `npm run dev`는 소유한 하위 프로세스의 종료를 기다린다. Gradle wrapper 실행은 `--no-daemon`으로 격리하며 SIGINT·SIGTERM을 하위 트리에 전달한다. POSIX에서는 5초 후 강제 종료하고 Windows에서는 `taskkill /T /F`를 사용한다. 자세한 재현과 검증 범위는 [종료 신호 troubleshooting](troubleshooting/dev-server-process-shutdown.md)을 따른다.

- Java 21
- Node.js 22 이상과 npm
- Docker (full validation과 local PostgreSQL)

## Golden path

1. 변경 전 root와 scoped `AGENTS.md`, 관련 docs/ADR을 읽고 current branch, upstream, working tree를 확인한다.
2. canonical default branch의 최신 기준에서 `<type>/<short-kebab-description>` 형식의 작업별 branch를 준비한다. 기존 branch의 목적과 변경이 현재 task와 명확히 같으면 session이 달라도 그 branch를 이어서 사용한다.
3. 구현이 [Human Decision Gate](#human-decision-gate)에 해당하는지 확인하고, 해당하면 선택을 구현하기 전에 human의 결정을 받는다.
4. 작은 단위로 구현하고 가까운 unit/type test를 실행한다.
5. `npm run check:quick`으로 빠르게 검증하고, API/DB 변경 또는 완료 전에는 `npm run check`을 실행한다.
6. 자기 변경만 stage하고 staged diff를 검토한 뒤 `<type>: <summary>` 형식으로 commit한다.
7. repository를 변경한 구현 작업은 별도의 원격 전달 요청을 기다리지 않고 작업 branch를 remote에 push한 뒤 Pull Request를 생성한다. PR에는 목적, 주요 변경, 실행한 검증, 남은 제약을 기록한다.
8. CI 결과를 확인하고 자기 변경이 유발한 실패는 로컬 재현과 원인 분석 후 수정·검증·추가 commit으로 해결한다.
9. CI가 통과하면 human review를 기다린다. agent는 자신의 PR을 merge하지 않는다.

## Human Decision Gate

이 gate는 이미 정해진 요구사항을 재승인하는 절차가 아니다. 현재 human 요청, human이 승인한 spec·issue 또는 Accepted ADR이 선택을 명확히 결정했다면 충족된 것으로 본다. 그렇지 않다면 agent는 다음 선택을 확정하는 구현 전에 human의 명시적 결정을 받는다.

- 제품 boundary, 핵심 domain invariant 또는 사용자에게 적용되는 정책 변경
- 데이터 삭제·재해석·대규모 backfill이나 ownership·retention 변경처럼 비가역적이거나 데이터 의미를 바꾸는 migration
- 인증·인가·보안·개인정보 또는 외부 AI·service로의 데이터 전송 경계 변경
- 새로운 외부 provider, 배포 단위, 운영 infrastructure 또는 핵심 runtime capability 도입
- Accepted ADR을 뒤집거나 현재 architecture boundary 변경
- 기존 관례로 결정할 수 없고, 선택이 지속적인 contract·운영 비용·vendor lock-in을 만들거나 전환 비용이 큰 경우

기존 architecture와 승인된 제품 동작 안에서의 국소적 구현, 쉽게 되돌릴 수 있는 refactoring, source of truth에 맞추는 bug fix, 승인된 기능을 위한 additive schema 변경, routine dependency 갱신, boilerplate·formatting·generated code 갱신은 agent가 자율적으로 수행한다. 기존 validation과 test로 충분히 검증할 수 있는 세부 구현은 가장 단순하고 가역적인 선택을 우선한다.

결정을 요청할 때는 한 화면에서 판단할 수 있도록 다음만 간결하게 제시한다. 현실적인 대안은 최대 두 개로 제한하고 상세 배경은 요청받을 때 확장한다.

- 지금 해결하려는 문제
- 추천안
- 현실적인 대안
- 핵심 trade-off와 영향 범위·되돌리기 비용
- 검증 방법
- human이 실제로 선택할 항목

ADR은 human이 내린 장기 결정을 기록하지만 승인 자체를 대신하지 않는다.

## Technical Review Candidates

Technical Review Candidate는 agent가 승인된 경계 안에서 자율적으로 구현한 선택 중, 현실적인 대안과 의미 있는 trade-off 때문에 향후 기술 리뷰에서 실제 코드와 검증을 다시 볼 가치가 있는 소수의 PR-local pointer다.

이 mechanism은 non-blocking이다. Human 승인 필요 여부를 판단하거나 기존 자율 구현 범위를 줄이지 않으며, candidate에 포함되었다는 이유만으로 별도 답변이나 merge 전 처리를 요구하지 않는다. 구현 중 Human Decision Gate 대상이 발견되면 candidate로 대신 기록하지 않고 해당 선택을 구현하기 전에 결정을 요청한다.

PR을 준비할 때 최종 diff와 검증 결과를 기준으로 다음 조건을 모두 충족하는 선택만 선별한다.

- 이번 PR에서 실제로 선택하고 구현했다.
- 현재 제약 안에서 현실적인 대안이 둘 이상 있었다.
- correctness, consistency·concurrency, security·privacy, failure behavior, performance·cost·scalability, system boundary 또는 변경 비용에 의미 있는 영향을 준다.
- 선택 이유가 diff, test 또는 기존 source of truth만으로 쉽게 복원되지 않는다.
- 기술 리뷰가 구현이나 위험 판단을 실질적으로 바꿀 수 있다.

boilerplate, formatting, generated code, 명백하거나 기계적인 선택, repository convention을 그대로 따른 구현, 완료된 작업의 요약, 미해결 TODO·제약, 구현하지 않은 아이디어는 제외한다. 승인된 spec·business rule·Accepted ADR이 이미 결정한 내용을 반복하지 않으며, 장기간 유지할 architecture 선택은 기존 기준에 따라 ADR로 남긴다.

기본값은 0개이며 개수를 채우지 않는다. 보통 최대 3개로 제한하고, 더 많으면 밀접한 항목을 합친 뒤 review 결과에 가장 큰 영향을 줄 수 있는 선택만 남긴다. 0개이면 PR section을 생략한다.

기록 형식은 다음과 같다.

```md
## Technical review candidates

- **Decision:** <이번 PR에서 실제로 구현한 선택>
  - **Why review-worthy:** <주요 현실적 대안과 핵심 trade-off 또는 위험>
  - **Where:** `<관련 path/symbol>`, `<관련 test>`
```

review로 구현이 바뀌면 merge 전에 candidate도 최종 선택에 맞게 갱신하거나 제거한다. Merge 후에는 PR history에 보존하며 별도 문서로 자동 집계하지 않는다. 이후 장기 규칙이나 architecture decision이 되면 그 시점에 관련 source of truth 또는 ADR로 승격한다.

## Git workflow details

### Codex worktree 점유

독립 Codex 세션은 worktree를 독점한다. 하위 agent는 부모의 세션 ID와 worktree를 공유하므로 부모가 작업 충돌을 조정한다. Node.js와 Git을 준비한 뒤 저장소 루트에서 `node scripts/install-worktree-guard.mjs`를 실행하고, 새 Codex CLI 세션의 `/hooks`에서 Hook 정의를 검토·신뢰한다. 이미 실행 중인 세션은 종료하고 다시 시작해야 보호된다. 정의가 바뀌면 다시 검토한다.

설치기는 사용자 홈의 `.codex/hooks.json`, `.codex/worktree-guard-runtime/`, `.codex/worktree-guard-repos.json`을 사용한다. 다른 사용자 Hook 설정은 덮어쓰지 않는다. 연결 worktree에서 프로젝트 Hook을 발견하지 못하는 Codex 환경을 고려해 사용자 Hook을 사용하고, 등록된 Git 공통 디렉터리에 속한 저장소만 점유를 확인한다. 다른 저장소는 통과시킨다. 공통 설정 파일이 손상되면 적용 대상을 판단할 수 없어 다른 저장소의 작업도 차단될 수 있다. 다른 저장소에 적용할 때도 해당 저장소에서 설치기를 실행한다. 시작·재개, 사용자 입력, 지원되는 도구 호출에서 점유를 확인하며 종료 시 소유 기록을 해제한다. 설정이 적용되지 않은 세션은 보호되지 않는다.

기록은 `~/.codex/worktree-guard`에 있으며 같은 사용자와 파일 시스템을 공유하는 worktree만 조정한다. sandbox에서 이 디렉터리에 접근할 수 있어야 한다. 같은 세션 ID를 동시에 재개한 실행은 서로 구별할 수 없다. Hook을 건너뛰는 도구 경로, 다른 agent 제품, 다른 컴퓨터 및 Hook 미실행을 차단하는 보안 경계는 아니다. Hook 실행 실패나 시간 초과 자체는 도구 호출을 차단하지 않을 수 있다. 보호가 활성화되지 않으면 별도 worktree를 사용한다. [Codex Hook 문서](https://learn.chatgpt.com/docs/hooks)를 참조한다.

비정상 종료 후 기록은 자동 만료되지 않는다. 해당 worktree에서 `node scripts/worktree-guard.mjs inspect`로 소유 세션 ID를 확인하고, 그 세션과 하위 작업이 모두 끝났음을 확인한 후 `node scripts/worktree-guard.mjs release <세션ID>`를 실행한다. 손상된 기록은 확인 없이 삭제하지 않는다. 이 복구는 현재 소유자의 작업과 동시에 실행하면 안 된다. 충돌한 세션은 다른 worktree를 사용하거나 기존 세션 종료 후 다시 시도한다.

### Working tree and branch safety

- 새 branch를 만들기 전에 기존 변경이 없거나 해당 변경이 현재 task에 속함이 명확해야 한다. 소유권이 불명확하면 branch 전환, stash, commit으로 임의 이동하지 않고 human에게 확인한다.
- default branch에서는 commit·push하지 않는다. remote의 canonical default branch와 local upstream이 다르면 추측해 바꾸지 말고 불일치를 보고한다.
- 광범위한 `git add .`보다 task에 속한 path를 명시적으로 stage한다. 사용자의 변경이 같은 파일에 있다면 해당 hunk을 분리하고 전체 파일을 임의로 stage하지 않는다.
- `reset --hard`, `clean -fd`, 강제 checkout, 무단 rebase·history rewrite·force push는 실행하지 않는다. 복구를 위해 불가피하면 정확한 대상, 영향, 복구 방법을 제시하고 human의 명시적 승인을 받는다.

### Commit and Pull Request

독립 작업은 최신 `origin/main`에서 분기한다. 의존 작업을 별도 PR로 검토할 때는 일렬 stacked PR로 구성할 수 있다. 첫 PR의 base는 `main`, 후속 PR의 base는 직전 head로 명시하고 완료 보고에 head/base와 병합 순서를 포함한다. 자동화의 설치·활성 상태를 확인한 후 [stack 등록 절차](stacked-prs.md)를 수행한다. 활성화 전에는 사람이 선행 PR을 병합한 뒤 후속 기반 정리·재검증을 별도 수행한다.

등록 후 head는 동결하며 review 수정 시 전체 후속 이력을 수동 정리하고 다시 등록한다. 승인된 restack workflow만 등록 branch를 atomic push와 명시적 lease로 재작성할 수 있다. 일반 agent의 무단 history rewrite 금지는 유지한다. human은 `stack-ready`와 `validate`를 확인한 뒤 기반부터 squash merge한다. 원격 restack 이후 로컬 branch에는 미반영 작업이 있을 수 있으므로 다음 작업 전에 fetch·diff로 확인한다.

- commit type은 기본적으로 `feat`, `fix`, `docs`, `test`, `refactor`, `chore`를 사용하고 summary는 변경 결과를 간결하게 설명한다.
- commit은 review 가능한 논리 단위로 만들되 파일별로 기계적으로 분할하지 않는다. secret, credential, local-only 설정은 commit하지 않는다.
- 긴 작업은 논리 단위가 완성되고 관련 검증이 통과한 시점에만 checkpoint commit·push한다. 컴파일되지 않거나 중간 상태만 담은 WIP commit을 자동 생성하지 않는다.
- agent는 자신의 branch와 PR만 push·갱신하며, default branch push, PR merge, auto-merge 설정을 수행하지 않는다.
- 변경 작업은 remote tracking branch, review 가능한 commit, open PR, CI 결과, clean working tree가 모두 확인되기 전에 완료로 보고하지 않는다.

### CI response

- 실패한 job과 log를 확인하고 자기 변경과의 연관성을 먼저 판단한다. 테스트 삭제·skip, assertion 약화, quality threshold 하향, 무관한 코드 수정으로 통과시키지 않는다.
- 자기 변경으로 인한 실패는 원인을 수정하고 관련 local validation을 다시 실행한다. 반복되는 실패가 검증 부재나 문맥 탐색 문제라면 harness 개선을 함께 검토한다.
- 기존 실패, flaky test, CI infrastructure·외부 service 장애라면 unrelated change로 해결하지 않고 재현 결과와 근거를 PR에 남긴다.

### Exceptions

- 문서 분석, code review, 진단처럼 repository 변경이 요청되지 않은 작업은 branch·commit·push·PR을 생성하지 않는다.
- remote, credential, network, hosting service API 권한이 없으면 우회하지 말고 완료한 범위와 blocker를 정확히 보고한다.
- human이 지정한 기존 task branch는 계속 사용할 수 있지만, agent가 default branch 직접 push나 self-merge 예외를 독자적으로 선언하지 않는다.

## Repository protection target

실제 적용 상태는 GitHub ruleset으로 확인한다. 현재 저장소에는 main의 PR 경유·선형 이력·삭제/force push 금지·`validate` 요구 규칙이 있다. 아래는 추가적인 보호 목표이며 이미 모두 적용됐다는 뜻은 아니다. 보호가 부족해도 default branch push 권한으로 해석하지 않는다. stacked PR 자동화 활성화 시 [별도 main 갱신 제한과 App 검사 설정](stacked-prs.md)을 적용한다.

- Pull Request를 통한 변경만 허용하고 approving review 1개를 요구한다.
- 새 commit이 push되면 stale approval을 dismiss하고, 가장 최근 push는 PR 작성자가 아닌 human이 승인하게 한다.
- required status check으로 `validate`를 지정하고, branch가 최신 `main`과 동기화되어야 merge할 수 있게 한다. Qodana는 warning 실패 기준을 확정하기 전까지 필요할 때 수동으로 실행하는 진단 도구로 사용한다.
- conversation resolution을 요구하고 force push와 branch deletion을 금지하며 administrator bypass도 허용하지 않는다.
- repository merge method는 squash merge만 허용하고 merge 후 head branch를 자동 삭제한다.

Agent의 GitHub identity는 human 계정과 분리한다. Bot 계정 또는 GitHub App에는 작업 branch 작성·push, Pull Request 작성·갱신, Actions/check 조회에 필요한 최소 권한만 주고 administration·branch protection bypass 권한과 human credential은 제공하지 않는다.

## Commands

- `npm run dev`: Docker PostgreSQL과 backend `local` 인증 프로필/frontend 개발 서버를 함께 시작한다. 종료해도 PostgreSQL 컨테이너는 유지한다.
- `npm run check:quick`: 개발 설정 로더 테스트 + backend unit + frontend lint/typecheck
- `npm run check`: quick + Testcontainers PostgreSQL integration + OpenAPI drift + frontend production build
- `npm --prefix frontend run api:generate`: committed OpenAPI에서 TypeScript type 재생성
- `npm --prefix frontend run test:e2e`: mock backend와 Chromium으로 guided intake 브라우저 흐름 검증
- CI는 stacked PR도 검증하도록 모든 PR base branch에서 실행하며, full check 후 Chromium 제품 검색→선택→회고와 실패 복구 E2E를 별도로 실행한다. 외부 API key는 CI에 필요하지 않다.

## 로컬 설정과 Developer Identity

실제 API key는 파일/환경 변수에 넣지 않는다. [ADR 0008](decisions/0008-identity-based-secret-management.md)에 따라 backend가 GCP Secret Manager에서 기동 시 직접 조회한다. `backend/.env.example`은 **비밀 아닌 설정** 예시다. `npm run dev`와 `npm run backend:dev`는 `backend/.env`를 자동으로 읽어 backend 자식 프로세스에만 전달한다. IntelliJ의 npm 실행도 동일하다. 파일에 작성한 활성화 설정이 실제 개발 실행에 반영되도록 공통 로더를 사용하며, frontend나 테스트·빌드 실행에는 파일 설정을 주입하지 않는다. 우선순위는 실행 환경변수 → 파일 → 기본값이다. 파일이 없으면 기본값을 사용하며 읽기 오류는 실패로 표시한다.

### Cloud-free 기본 경로

파일·실행 환경에 별도 설정이 없으면 `npm run dev`는 `local` + `offline`으로 실행한다. 로컬 Compose DB의 disposable 비밀번호만 사용하고 AI/검색/수집/메일은 기본 비활성이다. Google Cloud 계정과 ADC 없이 직접 입력·비교를 개발할 수 있다. 외부 기능을 켜면서 offline을 유지하면 조용히 무시하지 않고 기동을 거부한다.

`npm run check`와 일반 CI는 Production/Dev Secret을 읽지 않는다. `test` profile은 Secret 로딩과 AI/메일을 강제로 끄고 provider key에 fixture만 공급한다. 검색/수집 활성화는 `@SpringBootTest(properties=...)`의 명시적 fixture에 한정하며 이 경우 외부 provider/worker를 mock한다. 개발자 shell의 managed mode, API key, feature flag는 이 경로를 활성화하지 못한다. 실제 cloud smoke를 일반 test task에 추가하지 않는다.

### 실제 Dev provider 사용

1. 운영자가 [Secret management runbook](deployment.md#secret-management)에 따라 Dev 프로젝트의 필요한 Secret과 읽기 권한을 준비한다. Prod 읽기 권한은 주지 않는다. 두 프로젝트 ID는 비밀이 아니며 Dev/Prod에서 공유하는 환경별 설정 목록으로 관리한다.
2. Google Cloud CLI를 설치하고 개발자 계정으로 ADC를 설정한다. `gcloud auth login`만으로 애플리케이션 ADC가 설정되는 것은 아니다.

```powershell
gcloud auth application-default login
gcloud auth application-default set-quota-project YOUR_DEV_PROJECT_ID
```

quota project 사용 권한이 없으면 운영자에게 해당 Dev 프로젝트의 `serviceusage.services.use`에 필요한 최소 권한을 요청한다. 이를 해결하려고 프로젝트 Editor/Owner를 부여하지 않는다. ADC는 로컬 인증 파일을 만들 수 있으므로 OS 계정과 파일 접근을 보호하고 저장소에 복사하지 않는다. 앱 API key나 서비스 계정 JSON private key를 내려받는 방식은 사용하지 않는다. 직접 사용자 ADC가 기본이며 개발자 impersonation은 필요할 때만 사용한다.

3. `backend/.env`에는 다음과 같이 비밀 아닌 값만 기록한다. 실제 프로젝트 ID와 등록된 숫자 버전을 사용한다.

```dotenv
SPRING_PROFILES_ACTIVE=local
DECISION_ASSISTANT_SECRETS_MODE=gcp
DECISION_ASSISTANT_SECRETS_DEV_PROJECT_ID=your-decision-dev
DECISION_ASSISTANT_SECRETS_PROD_PROJECT_ID=your-decision-prod
DECISION_ASSISTANT_SEARCH_ENABLED=true
SERPER_SECRET_VERSION=1
DECISION_ASSISTANT_WEB_ENABLED=true
FIRECRAWL_SECRET_VERSION=1
DECISION_ASSISTANT_AI_ENABLED=false
```

Prod 프로젝트가 아직 준비되지 않았다면 계획된 별도 ID를 기록할 수 있지만 Prod 설정 완료로 취급하지 않는다. 앱은 local에서 Dev 프로젝트만 조회한다. 프로젝트 ID 설정 자체는 IAM 격리의 대체물이 아니다.

4. 저장소 루트의 첫 터미널에서 실행한다.

```powershell
npm run dev
```

backend만 실행하려면 DB를 준비한 뒤 `npm run backend:dev`를 사용한다. Gradle 또는 IntelliJ JVM 직접 실행에는 같은 **비밀 아닌 설정**을 명시적으로 전달한다. 조회된 키는 JVM 안에만 들어가며 Node/Frontend의 환경으로 export하지 않는다. 기존 shell에 남은 `*_API_KEY`, `AUTH0_CLIENT_SECRET`, `DB_PASSWORD`는 제거해야 한다. 평문 입력이 남으면 기동이 거부된다. 설정 변경 후 backend를 재시작한다.

Serper는 제품명 검색, Firecrawl은 페이지 수집과 기존 대안 검색이다. AI 구조화는 `DECISION_ASSISTANT_AI_ENABLED=true`, `GEMINI_SECRET_VERSION`과 Dev의 `gemini-api-key`가 추가로 필요하다. OpenAI를 선택하면 `DECISION_ASSISTANT_AI_PROVIDER=openai`, `DECISION_ASSISTANT_AI_MODEL`/`OPENAI_MODEL`, `OPENAI_SECRET_VERSION`을 설정한다. `AI_CHAT_PROVIDER`는 더 이상 사용하지 않고 선택한 provider에 맞춰 Spring AI client를 설정한다. 기존 사용자 전송 동의와 provider별 비용 정책은 변하지 않는다.

공유 Dev DB는 `DB_URL`, `DB_USERNAME`, `DB_SECRET_VERSION`과 `SPRING_DOCKER_COMPOSE_ENABLED=false`를 사용한다. 비밀번호는 Dev의 `db-password`에서 읽는다. Compose의 connection details가 공유 DB 설정을 덮어쓰지 않도록 통합 `npm run dev` 대신 backend를 개별 실행한다.

문제 해결: `IDENTITY_TYPE_NOT_ALLOWED`는 잘못된 ADC 종류, `GCP_PERMISSION_DENIED`는 IAM/API/quota project, `VERSION_REQUIRED_*`는 버전 누락, `PLAINTEXT_INPUT_*`는 예전 실행 설정을 먼저 확인한다. 오류 해결을 위해 Secret 값·ADC 파일·전체 환경을 로그나 PR에 붙이지 않는다. [ADC 공식 문서](https://docs.cloud.google.com/docs/authentication/application-default-credentials)를 참고한다.

## Database

`docker compose -f backend/compose.yaml up -d`로 개발 DB를 시작한다. JPA `ddl-auto=validate`이며 schema source of truth는 Flyway다. 적용된 migration을 수정하지 않는다.

인증 도입 전 Decision이 있는 개발 DB에는 V3 ownership migration을 적용하지 않는다. 데이터가 disposable하다는 전제에서 컨테이너를 제거한 뒤 빈 DB로 migration한다. 개별 backend 실행에는 `--spring.profiles.active=local`을 명시한다. Production은 `oidc` profile, Auth0 설정, exact email allowlist가 모두 필요하다.

## 문서 유지

- 문서는 작성 주체의 관점이 드러나는 대화체 대신 프로젝트의 결정·근거·현재 동작을 직접 서술한다. 예를 들어 요청자를 제3자로 지칭해 결정 사항을 설명하지 않는다.

- 반복 진단의 탐색 비용과 근거 없는 원인 추정을 줄이기 위해, agent는 승인된 작업에서 얻은 재사용 가치가 있는 진단을 별도 요청 없이 `docs/troubleshooting/`에 작성하거나 갱신한다. [작성 기준](troubleshooting/README.md)에 따라 선정하고 최종 보고에 추가·갱신한 문서를 알린다. 이 규칙은 문서 유지에 적용되며 제품 범위 변경, 새 유료 실험 또는 Human Decision Gate 대상의 구현을 승인하지 않는다.
- 동작 설명은 가능한 test/OpenAPI/config에서 생성하거나 연결한다.
- 제품 boundary 변경은 `product.md`, 핵심 용어 변경은 `domain.md`를 같은 변경에서 갱신한다.
- 장기간 유지할 architecture 선택과 대안은 ADR로 남긴다.
- 완료된 일의 로그나 코드와 동일한 목록은 문서화하지 않는다.

### 개발 Sony 사양 점검 실행

scripts/gradle.mjs의 bootRun에서 명시적으로 활성화한 기본 local DB만 Sony helper를 시작한다. prepareCatalogInspection은 test class를 컴파일하며 JUnit은 실행하지 않는다. Java 오프라인 CLI에는 Node가 고정 공개 URL에서 읽은 원문만 stdin으로 전달한다. CLI는 네트워크·DB·Secret을 사용하지 않는다. 준비 실패는 오류를 남기고 backend 기동을 막지 않으며 확인 시각도 연장하지 않는다.

helper는 사전 7일 예약과 revision·identity·근거·optimistic version을 대조한 뒤 fact_reviews와 version만 갱신한다. backend 종료 시 helper도 종료한다. 외부 DB·운영/복수 프로필·Secret 기반 DB에는 연결하지 않는다. 운영 runtime이나 인증 구조 변경은 포함하지 않는다. [운영 방법](catalog-operations.md#sony-wf-c710n-개발-환경-자동-점검)을 따른다.

Samsung 버즈4의 오프라인 가격 진단은 실제 렌더링 크기를 반영해 고정 URL에 한해 최대 300만 자(UTF-8 입력 최대 900만 바이트)를 허용한다. 다른 상품의 진단 입력 제한은 유지한다. CLI 검증은 원문 출처의 실제 조회나 DB 저장 성공을 대신하지 않는다.
