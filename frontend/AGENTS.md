<!-- BEGIN:nextjs-agent-rules -->

## This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# Decision Assistant frontend 지침

root `AGENTS.md`가 우선 적용된다. 위 Next.js generated block은 유지한다.

- `app/`은 route와 server composition, `features/`는 사용자 흐름, `shared/api/`는 generated contract와 얇은 client를 담당한다.
- backend 호출은 generated `shared/api/schema.d.ts` 타입을 사용한다. API payload를 별도 수기 interface로 복제하지 않는다.
- browser 요청은 same-origin `/api` rewrite를 사용하고 server component는 `BACKEND_URL`을 사용한다.
- AI 제안은 자동 적용하지 않고 사용자가 검토하도록 표시한다. AI 실행 전 consent UI를 유지한다.
- 검증은 `npm run lint`, `npm run typecheck`, `npm run build`, API contract 갱신은 `npm run api:generate`다.
