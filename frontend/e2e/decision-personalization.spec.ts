import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const backend = "http://127.0.0.1:18080/api/v1/decisions";

async function createDecision(request: APIRequestContext, complete: boolean) {
  const created = await (await request.post(backend, { data: {
    intentText: "개인화 활용 설정 검증", templateKey: "EARBUDS", webConsent: false,
    selectedSources: [{ name: "Alpha", url: "https://example.com/alpha" }],
  } })).json();
  if (complete) await request.post(`${backend}/${created.id}/resolutions`, { data: {
    expectedVersion: created.version, kind: "CHOSEN", selectedOptionId: created.options[0].id, reasonIds: [], rationale: "",
  } });
  return (await (await request.get(`${backend}/${created.id}`)).json()) as { id: string; version: number };
}

async function open(page: Page, id: string) {
  const capabilities = page.waitForResponse("**/api/v1/ai/capabilities");
  await page.goto(`/decisions/${id}`);
  await capabilities;
}

test("완료한 결정에서 개인화 활용을 끄고 같은 화면의 후속 동작을 이어 간다", async ({ page, request }) => {
  const decision = await createDecision(request, true);
  await open(page, decision.id);
  await expect(page.getByText("이 결정의 개인화 활용: 켜짐", { exact: true })).toBeVisible();

  const patch = page.waitForRequest(r => r.method() === "PATCH" && r.url().endsWith(`/api/v1/decisions/${decision.id}/retention`));
  await page.getByRole("button", { name: "개인화 활용 끄기 (현재 켜짐)", exact: true }).click();
  expect((await patch).postDataJSON()).toEqual({ expectedVersion: decision.version, action: "PERSONALIZATION_OFF" });
  await expect(page.getByText("이 결정의 개인화 활용: 꺼짐", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "개인화 활용 켜기 (현재 꺼짐)", exact: true })).toBeEnabled();

  // 변경으로 증가한 version을 다시 읽었으므로 version을 검사하는 후속 요청이 409 없이 성공한다.
  const revision = page.waitForRequest(r => r.method() === "POST" && r.url().endsWith(`/api/v1/decisions/${decision.id}/revisions`));
  await page.getByLabel("아직 구매하지 않아 다시 검토합니다").check();
  await page.getByRole("button", { name: "다시 검토", exact: true }).click();
  expect((await revision).postDataJSON().expectedVersion).toBe(decision.version + 1);
  await expect(page.getByText("다시 검토를 시작했습니다", { exact: false })).toBeVisible();
  // 비교 중 화면에는 설정을 표시하지 않는다.
  await expect(page.getByRole("heading", { name: "개인화 활용", exact: true })).toHaveCount(0);
});

test("설정 값은 결정 상세 응답에서 읽고 다시 불러와도 유지된다", async ({ page, request }) => {
  const decision = await createDecision(request, true);
  await open(page, decision.id);
  await page.getByRole("button", { name: "개인화 활용 끄기 (현재 켜짐)", exact: true }).click();
  await expect(page.getByText("이 결정의 개인화 활용: 꺼짐", { exact: true })).toBeVisible();
  await open(page, decision.id);
  await expect(page.getByText("이 결정의 개인화 활용: 꺼짐", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "개인화 활용 켜기 (현재 꺼짐)", exact: true }).click();
  await expect(page.getByText("이 결정의 개인화 활용: 켜짐", { exact: true })).toBeVisible();
});

test("다른 곳에서 결정이 바뀌어 409가 나면 오류를 알리고 서버의 값을 유지한다", async ({ page, request }) => {
  const decision = await createDecision(request, true);
  await open(page, decision.id);
  // 화면이 가진 version을 낡게 만든다. 설정 값은 그대로 켜짐이다.
  const changed = await request.patch(`${backend}/${decision.id}/retention`, { data: { expectedVersion: decision.version, action: "PERSONALIZATION_ON" } });
  expect(changed.status()).toBe(200);

  await page.getByRole("button", { name: "개인화 활용 끄기 (현재 켜짐)", exact: true }).click();
  await expect(page.getByText("결정이 변경되었습니다", { exact: false })).toBeVisible();
  await expect(page.getByText("이 결정의 개인화 활용: 켜짐", { exact: true })).toBeVisible();
  await open(page, decision.id);
  await expect(page.getByText("이 결정의 개인화 활용: 켜짐", { exact: true })).toBeVisible();
});

test("본문 없는 거절 응답도 성공으로 표시하지 않는다", async ({ page, request }) => {
  const decision = await createDecision(request, true);
  await open(page, decision.id);
  await page.route(`**/api/v1/decisions/${decision.id}/retention`, route => route.fulfill({ status: 403, body: "" }));
  await page.getByRole("button", { name: "개인화 활용 끄기 (현재 켜짐)", exact: true }).click();
  await expect(page.getByText("요청을 처리하지 못했습니다.", { exact: true })).toBeVisible();
  await expect(page.getByText("이 결정의 개인화 활용을 껐습니다.", { exact: true })).toHaveCount(0);
  await expect(page.getByText("이 결정의 개인화 활용: 켜짐", { exact: true })).toBeVisible();
});

test("보관한 완료 결정에서도 개인화 활용을 바꿀 수 있다", async ({ page, request }) => {
  await request.post("http://127.0.0.1:18080/__test/decision-list", { data: {} });
  // 목록 fixture의 12번째 항목은 CHOSEN이다.
  const id = "99999999-9999-9999-9999-000000000011";
  expect((await request.patch(`${backend}/${id}/retention`, { data: { expectedVersion: 0, action: "ARCHIVE" } })).status()).toBe(200);
  await open(page, id);
  await expect(page.getByText("이 결정의 개인화 활용: 켜짐", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "개인화 활용 끄기 (현재 켜짐)", exact: true }).click();
  await expect(page.getByText("이 결정의 개인화 활용: 꺼짐", { exact: true })).toBeVisible();
  const row = (await (await request.get(`${backend}/management?view=ARCHIVE&page=0`)).json()).items.find((item: { id: string }) => item.id === id);
  expect(row).toMatchObject({ personalizationEnabled: false, version: 2 });
});

test("비교 중인 결정에는 개인화 활용 설정을 표시하지 않는다", async ({ page, request }) => {
  const decision = await createDecision(request, false);
  await open(page, decision.id);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.getByRole("heading", { name: "개인화 활용", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /개인화 활용/ })).toHaveCount(0);
});
