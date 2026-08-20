# Decision Assistant frontend

Next.js App Router UI다. 전체 제품과 architecture 설명은 root [README](../README.md)와 [docs](../docs/README.md)를 기준으로 한다.

## Commands

```bash
npm run dev
npm run lint
npm run typecheck
npm run build
npm run api:generate
```

`BACKEND_URL` 기본값은 `http://localhost:8080`이다. browser의 `/api` 요청은 Next.js rewrite로 전달하고 server component는 이 주소를 직접 사용한다.

`shared/api/schema.d.ts`를 직접 수정하지 않는다. backend contract 변경 후 committed OpenAPI에서 재생성한다.
