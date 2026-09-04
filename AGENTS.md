# Decision Assistant 작업 지침

이 repository는 사람과 coding agent가 함께 운영하는 monorepo다. 변경 전에는 이 파일과 작업 영역의 `AGENTS.md`, 그리고 [문서 색인](docs/README.md)을 읽는다.

## Repository map

- `backend/`: Spring Boot API와 database migration. 세부 규칙은 `backend/AGENTS.md`.
- `frontend/`: Next.js web app. 세부 규칙은 `frontend/AGENTS.md`.
- `docs/`: 제품·domain·architecture·ADR·API contract의 source of truth.
- `scripts/`: 로컬과 CI가 공유하는 validation entrypoint.

## Golden commands

- 빠른 검증: `npm run check:quick`
- 전체 검증(PostgreSQL Docker 필요): `npm run check`
- 전체 로컬 실행: `npm run dev`
- 로컬 실행: `docker compose -f backend/compose.yaml up -d`, backend `bootRun`, frontend `npm run dev`

## Git safety and delivery

- 변경 작업은 repository의 canonical default branch가 아닌 작업별 branch에서 수행하고, default branch에 직접 commit하거나 push하지 않는다.
- 작업 전에 current branch, upstream, working tree를 확인하고, 사용자의 미커밋·untracked·unrelated change를 임의로 discard, overwrite, stash, stage, commit하지 않는다.
- `reset --hard`, `clean -fd`, 강제 checkout, history rewrite, force push 등 복구 위험이 큰 Git 작업은 agent가 독자적으로 실행하지 않는다.
- repository를 변경하는 구현 작업은 별도 요청이 없어도 검증된 변경의 commit, 작업 branch push, Pull Request 생성, CI 확인까지를 기본 완료 조건으로 한다. 분석·진단·review처럼 변경이 요청되지 않은 작업은 제외한다.
- agent는 자신의 PR을 merge하거나 auto-merge하지 않는다. 최종 수용과 squash merge는 human이 수행한다.
- CI는 우회하거나 test·quality gate를 약화하지 않는다. 자기 변경으로 인한 실패는 원인을 분석하고 정상적인 수정으로 해결한다.
- PR을 준비할 때 승인된 경계 안에서 자율적으로 구현한 선택 중 향후 기술 리뷰 가치가 높은 비자명한 선택만 `Technical review candidates`로 선별한다. 이는 non-blocking review pointer이며 Human Decision Gate나 ADR을 대체하지 않는다. 0개면 생략하고 보통 최대 3개만 남기며, 상세 기준과 형식은 [Development workflow](docs/development.md#technical-review-candidates)를 따른다.
- branch, commit, push, Pull Request, CI 대응의 상세 절차는 [Development workflow](docs/development.md)를 따른다.

## 구현 전 Human Decision Gate

- 현재 human 요청, 승인된 spec 또는 Accepted ADR이 아직 결정하지 않은 선택이 제품·핵심 domain 정책, 데이터 의미·수명, 인증·보안·개인정보·외부 전송 경계, runtime·provider·배포 architecture 또는 Accepted ADR을 변경한다면 구현 전에 human의 명시적 결정을 받는다. 세부 기준과 요청 형식은 [Development workflow](docs/development.md#human-decision-gate)를 따른다.
- 기존 경계 안에서 검증 가능하고 쉽게 되돌릴 수 있는 구현 선택은 재확인을 요청하지 않고 agent가 자율적으로 결정하며, 가장 단순하고 가역적인 선택을 우선한다.

## 공통 규칙

- 현재 구조는 modular monolith다. 배포 단위나 module을 근거 없이 추가하지 않는다.
- API 변경 시 backend OpenAPI, `docs/api/openapi.json`, generated frontend types를 함께 갱신한다.
- schema 변경은 기존 Flyway migration을 수정하지 않고 새 migration으로 추가한다.
- LLM output과 deterministic result를 하나의 임의 점수로 합치지 않는다.
- 사용자 제공 URL은 evidence reference일 뿐 server가 자동 fetch하지 않는다.
- 도메인 동작을 변경할 때는 `docs/business-rules.md`의 영향 규칙을 확인하고, 규칙과 관련 검증을 함께 갱신한다.
- architecture/workflow 규칙을 바꿀 때는 이유와 영향 범위를 제시하고 검토 가능한 문서 또는 ADR로 남긴다.

지침은 반복되는 오류나 탐색 비용을 줄일 때만 추가한다. 코드가 이미 강제하는 세부사항을 복사하지 말고, 더 이상 유효하지 않은 규칙은 관련 변경과 함께 삭제한다.
