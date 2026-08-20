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

### 1. PostgreSQL

```bash
docker compose -f backend/compose.yaml up -d
```

### 2. Backend

```bash
cd backend
./gradlew bootRun # Windows: gradlew.bat bootRun
```

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

`http://localhost:3000`에서 사용한다. AI는 기본 비활성화다. 사용하려면 `backend/.env.example`을 참고해 환경 변수를 설정한다.

## Validate

```bash
npm run check:quick
npm run check # Docker 필요
```
