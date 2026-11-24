import { expect, test, type Page } from "@playwright/test";

async function captureLogout(page: Page) {
  const requests: { token: string | null; cookie: string | null }[] = [];
  await page.route("**/logout", async route => {
    const request = route.request();
    expect(request.method()).toBe("POST");
    const token = new URLSearchParams(request.postData() ?? "").get("_csrf");
    const cookie = (await request.headerValue("cookie"))?.split(/;\s*/)
      .find(value => value.startsWith("XSRF-TOKEN="))?.slice("XSRF-TOKEN=".length) ?? null;
    requests.push({ token, cookie });
    await route.fulfill({ status: 204 });
  });
  return requests;
}

const logoutButton = (page: Page) => page.getByRole("button", { name: "로그아웃" });
const logoutForm = (page: Page) => page.locator('form[action="/logout"]');
const user = { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", email: "user@example.test" };

test("열어 둔 화면에서도 다른 탭이 교체한 쿠키를 로그아웃 양식에 반영한다", async ({ page, context, baseURL }) => {
  await context.addCookies([{ name: "XSRF-TOKEN", value: "old-token", url: baseURL! }]);
  const requests = await captureLogout(page);
  await page.goto("/");
  await expect(logoutButton(page)).toBeEnabled();

  const other = await context.newPage();
  await other.goto("/");
  await other.evaluate(() => { document.cookie = "XSRF-TOKEN=rotated-token; Path=/; SameSite=Lax"; });

  await logoutButton(page).click();
  await expect.poll(() => requests).toEqual([{ token: "rotated-token", cookie: "rotated-token" }]);
});

test("쿠키가 없으면 한 번 발급하고 그동안 들어온 추가 제출을 무시한다", async ({ page }) => {
  const requests = await captureLogout(page);
  let reads = 0;
  let releaseResponse!: () => void;
  const delayedResponse = new Promise<void>(resolve => { releaseResponse = resolve; });
  await page.route("**/api/v1/me", async route => {
    reads++;
    await delayedResponse;
    await route.fulfill({ json: user, headers: { "Set-Cookie": "XSRF-TOKEN=issued-token; Path=/; SameSite=Lax" } });
  });
  await page.goto("/");
  await logoutButton(page).click();
  try {
    await expect.poll(() => reads).toBe(1);
    await expect(logoutButton(page)).toBeDisabled();
    await logoutForm(page).evaluate((form: HTMLFormElement) => {
      form.requestSubmit();
      form.requestSubmit();
    });
    expect(requests).toEqual([]);
  } finally {
    releaseResponse();
  }

  await expect.poll(() => requests).toEqual([{ token: "issued-token", cookie: "issued-token" }]);
  expect(reads).toBe(1);
});

test("발급 실패 시 로그아웃을 제출하지 않고 다시 시도할 수 있다", async ({ page }) => {
  const requests = await captureLogout(page);
  let reads = 0;
  await page.route("**/api/v1/me", route => {
    reads++;
    return reads === 1
      ? route.fulfill({ status: 503 })
      : route.fulfill({ json: user, headers: { "Set-Cookie": "XSRF-TOKEN=retried-token; Path=/; SameSite=Lax" } });
  });
  await page.goto("/");
  await logoutButton(page).click();
  await expect(logoutForm(page).getByRole("alert")).toContainText("다시 시도하세요");
  expect(requests).toEqual([]);

  await logoutButton(page).click();
  await expect.poll(() => requests).toEqual([{ token: "retried-token", cookie: "retried-token" }]);
  expect(reads).toBe(2);
});
