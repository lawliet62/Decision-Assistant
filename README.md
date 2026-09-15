# Decision Assistant

인증된 개인의 비교형 의사결정을 구조화하고, deterministic evaluation과 독립적인 LLM assessment를 비교하며, 실제 선택과 outcome을 기록하는 MVP다.

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

이 명령은 `backend/compose.yaml`의 PostgreSQL을 시작하고 backend를 안전한 `local` 인증 프로필로, frontend를 개발 모드로 실행한다. 고정 local user는 loopback에서만 사용되며 종료해도 PostgreSQL 컨테이너는 유지된다.

인증 기능 도입 전 DB에 Decision이 있다면 V3 migration 전에 개발 DB를 한 번 초기화해야 한다. 보존할 데이터가 없음을 확인한 뒤 `docker compose -f backend/compose.yaml down`을 실행한다.

### 개별 실행

PostgreSQL만 시작하려면 다음 명령을 사용한다.

```bash
docker compose -f backend/compose.yaml up -d
```

Backend:

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
# Windows: gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Frontend:

```bash
cd frontend
npm ci
npm run dev
```

`http://localhost:3000`에서 사용한다. AI/외부 provider는 기본 비활성화이며 로컬 실행과 테스트에 클라우드 계정은 필요 없다. 실제 API 호출은 [Developer Identity 온보딩](docs/development.md#로컬-설정과-developer-identity)을 따라 Dev Secret Manager를 연결한다. `backend/.env.example`에는 비밀 아닌 설정만 넣고 실제 API key는 넣지 않는다. Gemini 무료 티어에는 제품 정보와 일반 구매 메모만 보내고 민감한 개인정보·금융·비공개 업무 정보는 입력하지 않는다.

Production은 `oidc` profile과 Auth0 Regular Web Application을 사용한다. Auth0 callback은 `{PUBLIC_BASE_URL}/login/oauth2/code/auth0`, logout URL은 `{PUBLIC_BASE_URL}/login`으로 등록한다. issuer/client ID/allowlist는 운영 설정, client secret/DB 비밀번호/API key는 Prod Secret Manager에서 읽는다. Google connection과 passwordless email OTP를 Auth0 Universal Login에 활성화한다. 운영 호스팅과 서비스 Identity 연결은 [배포 runbook](docs/deployment.md)에 따라 별도 검증해야 한다.

## Validate

```bash
npm run check:quick
npm run check # Docker 필요
```
