# Decision Assistant

개인의 비교형 의사결정을 구조화하고, deterministic evaluation과 독립적인 LLM assessment를 비교하며, 실제 선택과 outcome을 기록하는 local-first MVP다.

## Repository structure

```text
.
├── backend/     # Spring Boot API (Java 21, PostgreSQL)
├── frontend/    # Next.js web application
├── docs/        # 제품·domain·architecture·ADR·API contract
└── scripts/     # 공통 validation harness
```

설계의 현재 기준은 [docs/README.md](docs/README.md)에서 시작한다.

## Run locally

### 전체 실행

Docker Desktop이 실행 중인 상태에서 root에서 다음 명령을 실행한다.

```bash
npm run dev
```

이 명령은 `backend/compose.yaml`의 PostgreSQL을 시작한 뒤 backend와 frontend 개발 서버를 함께 실행한다. 종료해도 PostgreSQL 컨테이너는 데이터 보존을 위해 유지된다.

### 개별 실행

PostgreSQL만 시작하려면 다음 명령을 사용한다.

```bash
docker compose -f backend/compose.yaml up -d
```

Backend:

```bash
cd backend
./gradlew bootRun # Windows: gradlew.bat bootRun
```

Frontend:

```bash
cd frontend
npm ci
npm run dev
```

`http://localhost:3000`에서 사용한다. AI는 기본 비활성화다. Gemini Developer API는 dogfooding 기본 provider이며, 사용하려면 `backend/.env.example`을 참고해 환경 변수를 설정한다. Gemini 무료 티어에는 제품 정보와 일반 구매 메모만 보내고 민감한 개인정보·금융·비공개 업무 정보는 입력하지 않는다.

## Validate

```bash
npm run check:quick
npm run check # Docker 필요
```
