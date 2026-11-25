import { expect, test } from "@playwright/test";

const authorizationPath = "/oauth2/authorization/auth0";

// 운영에서는 이 경로가 cross-origin Auth0 /authorize로 302 응답한다. RSC fetch가 그 리다이렉트를 따라가면
// CORS 오류가 발생하므로(#86), 로그인 버튼은 RSC 요청 없이 문서 탐색만 해야 한다.
test("로그인 버튼은 RSC 요청 없이 인가 경로로 문서 탐색한다", async ({ page, context, baseURL }) => {
  await context.addCookies([{ name: "mock-auth", value: "anonymous", url: baseURL! }]);
  const requests: { rsc: string | null; prefetch: string | null; rscQuery: boolean; navigation: boolean; type: string }[] = [];
  await page.route(url => url.pathname === authorizationPath, async route => {
    const request = route.request();
    requests.push({
      rsc: await request.headerValue("rsc"),
      prefetch: await request.headerValue("next-router-prefetch"),
      rscQuery: new URL(request.url()).searchParams.has("_rsc"),
      navigation: request.isNavigationRequest(),
      type: request.resourceType(),
    });
    await route.fulfill({ contentType: "text/html", body: "<!doctype html><title>authorize</title>" });
  });

  await page.goto("/login");
  const login = page.getByRole("link", { name: "Google 또는 이메일로 계속" });
  await expect(login).toHaveAttribute("href", authorizationPath);
  // hydration 전의 클릭은 어떤 구현이든 문서 탐색이 되므로, React가 요소를 hydrate한 뒤 클릭한다.
  await expect.poll(() => login.evaluate(element => Object.keys(element).some(key => key.startsWith("__reactFiber$")))).toBe(true);
  await login.click();

  await page.waitForURL(url => url.pathname === authorizationPath);
  expect(requests).toEqual([{ rsc: null, prefetch: null, rscQuery: false, navigation: true, type: "document" }]);
});
