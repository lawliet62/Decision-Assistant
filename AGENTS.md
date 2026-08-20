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
- 로컬 실행: `docker compose -f backend/compose.yaml up -d`, backend `bootRun`, frontend `npm run dev`

## 공통 규칙

- 현재 구조는 modular monolith다. 배포 단위나 module을 근거 없이 추가하지 않는다.
- API 변경 시 backend OpenAPI, `docs/api/openapi.json`, generated frontend types를 함께 갱신한다.
- schema 변경은 기존 Flyway migration을 수정하지 않고 새 migration으로 추가한다.
- LLM output과 deterministic result를 하나의 임의 점수로 합치지 않는다.
- 사용자 제공 URL은 evidence reference일 뿐 server가 자동 fetch하지 않는다.
- architecture/workflow 규칙을 바꿀 때는 이유와 영향 범위를 제시하고 검토 가능한 문서 또는 ADR로 남긴다.

지침은 반복되는 오류나 탐색 비용을 줄일 때만 추가한다. 코드가 이미 강제하는 세부사항을 복사하지 말고, 더 이상 유효하지 않은 규칙은 관련 변경과 함께 삭제한다.
