# 로그아웃 후 로그인 화면에서 RSC 요청이 CORS 오류를 내는 경우

## 증상과 기대 결과

운영에서 로그아웃 뒤 Next.js `_rsc` 요청이 Auth0 `/authorize`로 리다이렉트되고 브라우저 콘솔에 CORS 오류가 표시됐다([#86](https://github.com/lawliet62/Decision-Assistant/issues/86)). 로그아웃 자체는 처리됐다. 로그아웃 이후의 Next.js 요청과 Auth0 로그인 화면 전환은 브라우저 CORS 오류 없이 진행돼야 한다.

## 원인

로그인 화면([`frontend/app/login/page.tsx`](../../frontend/app/login/page.tsx))의 로그인 버튼이 Next.js `<Link href="/oauth2/authorization/auth0">`였다. 이 경로는 [`next.config.ts`](../../frontend/next.config.ts)의 rewrite로 Spring backend에 전달되고, backend는 cross-origin인 Auth0 `/authorize`로 `302`를 응답한다. 운영 로그아웃은 Auth0 로그아웃 뒤 `/login`으로 돌아오도록 설정돼 있어([`SecurityConfiguration`](../../backend/src/main/java/com/decisionassistant/identity/SecurityConfiguration.java)의 `setPostLogoutRedirectUri`), "로그아웃 후"라는 증상은 이 화면에서 시작된다.

근거 수준은 다음과 같다. 관찰 조건은 Next.js 16.4.5, `next dev`, Playwright Chromium이다.

- 시험에서 관찰: 수정 전 화면에서 hydration 후 버튼을 누르면 문서 탐색보다 먼저 `RSC: 1` header와 `_rsc` query를 가진 `fetch` 요청이 같은 경로로 나갔다. 실패 실행 5회 모두 같았다. 이 실행에서는 시험이 RSC 요청에 HTML `200`을 돌려줬으므로 CORS 실패 경로 자체는 지나지 않았다.
- source에서 확인, 실행으로는 미관찰: Next.js는 RSC fetch가 실패하면 콘솔에 오류를 남기고 브라우저 문서 탐색으로 전환한다(`next/dist/client/components/router-reducer/fetch-server-response.js`의 "CORS request fails" 주석과 MPA navigation fallback). 따라서 운영에서도 로그인 탐색은 이어지고 콘솔에만 오류가 남았을 것으로 판단한다. 운영의 전환 이후 화면은 확인하지 않았다.
- 문서와 source에서 확인, 실행으로는 미관찰: Next.js 문서는 `<Link>`가 production build에서 viewport에 들어올 때 prefetch하고, hover 시에는 만료된 데이터를 다시 prefetch한다고 설명한다. `next dev`에서는 prefetch가 꺼져 있어 이 경로의 prefetch는 관찰하지 않았다.
- 미확인: 운영에서 실패한 요청이 prefetch였는지 클릭으로 시작된 RSC 탐색이었는지는 요청 header를 수집하지 않아 알 수 없다. 두 경우 모두 이번 수정으로 사라지는 경로다.

frontend에서 cross-origin으로 리다이렉트되는 backend 경로(`/oauth2`, `/login/oauth2`, `/logout`)를 client-side 탐색으로 향하는 곳은 이 버튼뿐이었다. 로그아웃은 일반 `<form method="post" action="/logout">` 제출이고, 다른 비로그인 처리는 server의 `redirect("/login")` 또는 `window.location.assign`으로 `/login`에 이동한다.

## 조치와 검증

로그인 버튼을 일반 `<a href="/oauth2/authorization/auth0">`로 바꿔 브라우저 문서 탐색만 일어나게 했다. 문구, class, 접근성 역할은 그대로다. `prefetch={false}`만으로는 부족하다. 위 실패 실행은 prefetch가 꺼진 `next dev`에서도 클릭이 RSC fetch로 처리됨을 보여 준다. 로그인 요청의 query나 전송 내용은 바꾸지 않았다.

[브라우저 회귀 시험](../../frontend/e2e/login.spec.ts)은 mock backend에서 비로그인 상태로 `/login`을 열고, hydration 후 버튼을 눌러 인가 경로로 가는 요청이 `RSC`·`Next-Router-Prefetch` header와 `_rsc` query가 없는 문서 탐색 한 건뿐인지 확인한다. 수정 전 화면에서는 5회 반복 모두 RSC fetch가 기록돼 실패했고, 수정 후 5회 모두 통과했다. hydration 전에 누르면 어떤 구현이든 문서 탐색이 되므로, 시험은 React가 요소를 hydrate했는지 확인한 뒤 누른다. 이 시험은 RSC 요청이 생기지 않음을 확인할 뿐 Auth0 cross-origin 리다이렉트와 CORS 오류 자체를 재현하지 않으며, production prefetch도 다루지 않는다.

상태는 부분 해결이다. 코드 수정과 회귀 시험은 끝났고, 수정된 운영 릴리스에서 CORS 오류가 사라졌는지는 아직 확인하지 않았다.

## 운영에서 확인하는 방법

1. 브라우저 Network에서 **Preserve log**를 켜고 로그아웃한 뒤 `/login` 화면에 머물렀다가 로그인 버튼을 누른다.
2. CORS 오류가 난 요청의 URL을 확인한다. `/oauth2/authorization/auth0?_rsc=...`처럼 `_rsc` query가 있으면 Next.js RSC 요청이다.
3. 같은 요청의 Request Headers를 확인한다. RSC 요청은 `RSC: 1`을 보낸다. Next.js 16.4.5의 prefetch 요청은 여기에 `Next-Router-Prefetch`(값 `1`·`2`·`3`)를 더하고, 대개 `Next-Router-Segment-Prefetch`도 보낸다. prefetch header가 있으면 viewport 진입 또는 hover로 시작된 prefetch다. 없으면 대개 클릭으로 시작된 RSC 탐색이지만, `prefetch={true}`인 `<Link>`의 전체 prefetch는 `Next-Router-Prefetch`를 보내지 않으므로(`next/dist/client/components/segment-cache/cache.js`) 4단계의 Initiator 시점과 함께 판단한다.
4. Initiator와 Type을 확인한다. RSC 요청은 Type이 `fetch`이고 Initiator가 Next.js client script다. 수정 후에는 인가 경로 요청이 Type `document`인 한 건이어야 하고 CORS 오류가 없어야 한다.

요청 URL의 `state`·`code` 같은 인가 parameter와 cookie 값은 공유 자료에 포함하지 않는다.
