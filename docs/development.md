# Development workflow

## Prerequisites

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

### Working tree and branch safety

- 새 branch를 만들기 전에 기존 변경이 없거나 해당 변경이 현재 task에 속함이 명확해야 한다. 소유권이 불명확하면 branch 전환, stash, commit으로 임의 이동하지 않고 human에게 확인한다.
- default branch에서는 commit·push하지 않는다. remote의 canonical default branch와 local upstream이 다르면 추측해 바꾸지 말고 불일치를 보고한다.
- 광범위한 `git add .`보다 task에 속한 path를 명시적으로 stage한다. 사용자의 변경이 같은 파일에 있다면 해당 hunk을 분리하고 전체 파일을 임의로 stage하지 않는다.
- `reset --hard`, `clean -fd`, 강제 checkout, 무단 rebase·history rewrite·force push는 실행하지 않는다. 복구를 위해 불가피하면 정확한 대상, 영향, 복구 방법을 제시하고 human의 명시적 승인을 받는다.

### Commit and Pull Request

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

GitHub Pro 활성화 전에는 default branch 보호가 문서화된 soft boundary임을 인지한다. 보호 기능이 없다는 점을 default branch push 권한으로 해석하지 않는다. 요금제 업그레이드 후 human administrator는 `main`에 다음 보호를 적용한다.

- Pull Request를 통한 변경만 허용하고 approving review 1개를 요구한다.
- 새 commit이 push되면 stale approval을 dismiss하고, 가장 최근 push는 PR 작성자가 아닌 human이 승인하게 한다.
- required status check으로 `validate`를 지정하고, branch가 최신 `main`과 동기화되어야 merge할 수 있게 한다. Qodana는 warning 실패 기준을 확정하기 전까지 필요할 때 수동으로 실행하는 진단 도구로 사용한다.
- conversation resolution을 요구하고 force push와 branch deletion을 금지하며 administrator bypass도 허용하지 않는다.
- repository merge method는 squash merge만 허용하고 merge 후 head branch를 자동 삭제한다.

Agent의 GitHub identity는 human 계정과 분리한다. Bot 계정 또는 GitHub App에는 작업 branch 작성·push, Pull Request 작성·갱신, Actions/check 조회에 필요한 최소 권한만 주고 administration·branch protection bypass 권한과 human credential은 제공하지 않는다.

## Commands

- `npm run dev`: Docker PostgreSQL과 backend `local` 인증 프로필/frontend 개발 서버를 함께 시작한다. 종료해도 PostgreSQL 컨테이너는 유지한다.
- `npm run check:quick`: backend unit + frontend lint/typecheck
- `npm run check`: quick + Testcontainers PostgreSQL integration + OpenAPI drift + frontend production build
- `npm --prefix frontend run api:generate`: committed OpenAPI에서 TypeScript type 재생성
- `npm --prefix frontend run test:e2e`: mock backend와 Chromium으로 guided intake 브라우저 흐름 검증
- CI는 stacked PR도 검증하도록 모든 PR base branch에서 실행하며, full check 후 Chromium 제품 검색→선택→회고와 실패 복구 E2E를 별도로 실행한다. 외부 API key는 CI에 필요하지 않다.

## Database

`docker compose -f backend/compose.yaml up -d`로 개발 DB를 시작한다. JPA `ddl-auto=validate`이며 schema source of truth는 Flyway다. 적용된 migration을 수정하지 않는다.

인증 도입 전 Decision이 있는 개발 DB에는 V3 ownership migration을 적용하지 않는다. 데이터가 disposable하다는 전제에서 컨테이너를 제거한 뒤 빈 DB로 migration한다. 개별 backend 실행에는 `--spring.profiles.active=local`을 명시한다. Production은 `oidc` profile, Auth0 설정, exact email allowlist가 모두 필요하다.

## 문서 유지

- 동작 설명은 가능한 test/OpenAPI/config에서 생성하거나 연결한다.
- 제품 boundary 변경은 `product.md`, 핵심 용어 변경은 `domain.md`를 같은 변경에서 갱신한다.
- 장기간 유지할 architecture 선택과 대안은 ADR로 남긴다.
- 완료된 일의 로그나 코드와 동일한 목록은 문서화하지 않는다.
