# Decision Assistant 작업 지침

이 repository는 사람과 coding agent가 함께 운영하는 monorepo다. 변경 전에 이 파일과 작업 영역의 `AGENTS.md`(`backend/`, `frontend/`), [문서 색인](docs/README.md)을 읽는다. `docs/`는 제품·domain·architecture·ADR·API contract의 source of truth다.

## Golden commands

- 빠른 검증: `npm run check:quick`
- 전체 검증(PostgreSQL Docker 필요): `npm run check`
- 전체 로컬 실행: `npm run dev`

## Git safety and delivery

branch, commit, push, Pull Request, CI 대응의 상세 절차는 [Development workflow](docs/development.md)를 따른다.

- 독립 agent 세션은 각자 별도 Git worktree에서 작업하고, 탐색·검증·개발 명령도 그 worktree에서 실행한다. 다른 실행 중인 세션이 사용하는 worktree에서는 작업하지 않고 별도 worktree를 사용한다. 하위 agent는 부모 세션의 worktree를 공유할 수 있다.
- 원본 checkout이나 다른 worktree의 파일이 꼭 필요하면 작업 디렉터리를 옮기지 않고 필요한 파일만 읽는다. 그곳에서 수정·빌드는 하지 않는다.
- 변경 작업은 repository의 canonical default branch가 아닌 작업별 branch에서 수행하고, default branch에 직접 commit하거나 push하지 않는다. 독립 작업은 최신 `origin/main`에서 분기한다.
- 작업 전에 current branch, upstream, working tree를 확인하고, 사용자의 미커밋·untracked·unrelated change를 임의로 discard, overwrite, stash, stage, commit하지 않는다.
- `reset --hard`, `clean -fd`, 강제 checkout, history rewrite, force push 등 복구 위험이 큰 Git 작업은 agent가 독자적으로 실행하지 않는다. 유일한 예외는 [stacked PR 자동화](docs/stacked-prs.md)에 명시 등록된 stack의 후속 branch를 승인된 restack workflow가 명시적 lease와 atomic push로 재작성하는 경우다. 일반 agent의 임의 rebase·force push나 PR merge를 허용하는 예외는 아니다.
- 의존 작업을 stacked PR로 구성하면 head/base·병합 순서를 보고하고 [등록 절차](docs/stacked-prs.md)를 따른다. 자동화가 비활성이면 자동 restack이 된다고 안내하지 않는다.
- repository를 변경하는 구현 작업은 별도 요청이 없어도 검증된 변경의 commit, 작업 branch push, Pull Request 생성, CI 확인까지를 기본 완료 조건으로 한다. 분석·진단·review처럼 변경이 요청되지 않은 작업은 제외한다.
- agent는 자신의 PR을 merge하거나 auto-merge하지 않는다. 최종 수용과 squash merge는 human이 수행한다.
- CI는 우회하거나 test·quality gate를 약화하지 않는다. 자기 변경으로 인한 실패는 원인을 분석하고 정상적인 수정으로 해결한다.
- PR을 준비할 때 승인된 경계 안에서 자율적으로 구현한 비자명한 선택 중 기술 리뷰 가치가 높은 것만 [Technical review candidates](docs/development.md#technical-review-candidates) 기준으로 PR에 기록한다. 보통 최대 3개이며 없으면 생략한다. 이 기록은 Human Decision Gate나 ADR을 대체하지 않는다.

## 개발 에이전트 GitHub 인증

이 규칙은 `lawliet62/Decision-Assistant`에 대한 에이전트 작업에 적용한다.

- 세션 시작 시 인증을 확인한다: `& C:\Users\dlwld\Tools\github-agent\agent.ps1 verify`
- GitHub 조회·변경과 인증이 필요한 Git 작업(fetch·pull·push 등)은 `& C:\Users\dlwld\Tools\github-agent\agent.ps1 exec`를 통해 실행한다. 인증이 필요 없는 로컬 Git 조회·stage·branch 작업은 직접 실행할 수 있다.
- 에이전트의 commit은 `& C:\Users\dlwld\Tools\github-agent\agent.ps1 exec --bot-author git commit ...` 형식으로 실행해 bot 작성자와 committer를 적용한다. 개인 또는 전역 Git 작성자 설정은 변경하지 않는다.
- helper 부재 또는 인증 실패 시 개인 gh 로그인, 기존 Git 자격 증명, 다른 인증 경로로 전환하거나 우회하지 않는다. 승인된 인증 경로의 문제를 진단하고, 해결되지 않으면 실패한 단계, 비밀값을 제외한 오류, 필요한 사용자 조치를 보고한다. 인증과 무관한 로컬 작업은 계속할 수 있다.
- 개인 GitHub 로그인과 credential store는 변경하지 않으며, 토큰·JWT·클라우드 자격 증명을 출력하거나 파일·Git에 저장하지 않는다.
- 인증 문제가 있어도 commit·push·PR 생성·CI 확인이라는 완료 조건은 유지한다. 인증 실패를 근거 없이 사용자에게 PR 게시를 넘기는 이유로 사용하지 않는다.

## 구현 전 Human Decision Gate

- 현재 human 요청, 승인된 spec 또는 Accepted ADR이 아직 결정하지 않은 선택이 제품·핵심 domain 정책, 데이터 의미·수명, 인증·보안·개인정보·외부 전송 경계, runtime·provider·배포 architecture 또는 Accepted ADR을 변경한다면 구현 전에 human의 명시적 결정을 받는다. 세부 기준과 요청 형식은 [Development workflow](docs/development.md#human-decision-gate)를 따른다.
- 기존 경계 안에서 검증 가능하고 쉽게 되돌릴 수 있는 구현 선택은 재확인을 요청하지 않고 agent가 자율적으로 결정하며, 가장 단순하고 가역적인 선택을 우선한다.

## 범위 밖 개선 기회

- 현재 작업 중 자연스럽게 발견한 범위 밖 개선은 임의로 구현하지 않고, [범위 밖 개선 기회 발견 및 제안](docs/development.md#범위-밖-개선-기회-발견-및-제안)에 따라 최종 보고에 Issue 후보로 제안한다. 즉각적인 대응이 필요한 위험·차단 문제는 즉시 보고하며, Issue 생성과 구현 착수는 각각 사용자의 명시적 승인 또는 요청을 따른다.

## Code Review Rules

- 리뷰 제목, 개별 리뷰 코멘트, 전체 리뷰 요약은 한국어 존댓말의 공적이고 전문적인 문체로 간결하게 작성하며, 아첨이나 불필요한 서론을 생략합니다.
- 문제의 발생 조건과 실제 영향을 구체적으로 설명합니다. 확인된 사실과 추정을 구분하고, 추정에는 근거와 불확실성을 명시합니다.
- 약어는 문서 내 첫 사용 시 전체 명칭을 함께 제시합니다(API, HTTP 등 널리 통용되는 약어와 코드 식별자는 예외). 숫자·날짜·버전의 범위는 물결표 대신 하이픈(-)으로 표기합니다.
- 기술 용어, 고유명사, 코드 식별자, 파일 경로, 명령어는 원문을 유지할 수 있습니다.

## 공통 규칙

- PR 제목·본문을 포함한 모든 문서화 작업은 한국어로 작성한다. 기술 용어, 고유명사, 코드 식별자, 명령어와 경로는 원문을 사용할 수 있다.
- 재사용 가치가 있는 장애 진단·반복 실패·기대에 못 미친 개선 효과는 별도 요청 없이 troubleshooting으로 기록하거나 기존 문서를 갱신한다. 기준은 [문서 유지](docs/development.md#문서-유지)와 [troubleshooting 작성 기준](docs/troubleshooting/README.md)을 따른다.
- 현재 구조는 modular monolith다. 배포 단위나 module을 근거 없이 추가하지 않는다.
- API 변경 시 backend OpenAPI, `docs/api/openapi.json`, generated frontend types를 함께 갱신한다.
- schema 변경은 기존 Flyway migration을 수정하지 않고 새 migration으로 추가한다.
- LLM output과 deterministic result를 하나의 임의 점수로 합치지 않는다.
- 사용자 제공 URL은 evidence reference일 뿐 server가 자동 fetch하지 않는다.
- 도메인 동작을 변경할 때는 `docs/business-rules.md`의 영향 규칙을 확인하고, 규칙과 관련 검증을 함께 갱신한다.
- architecture/workflow 규칙을 바꿀 때는 이유와 영향 범위를 제시하고 검토 가능한 문서 또는 ADR로 남긴다.

지침은 반복되는 오류나 탐색 비용을 줄일 때만 추가한다. 코드가 이미 강제하는 세부사항을 복사하지 말고, 더 이상 유효하지 않은 규칙은 관련 변경과 함께 삭제한다.
