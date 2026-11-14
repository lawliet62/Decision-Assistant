import { expect, test } from "@playwright/test";
import { confirmationTime } from "../features/product-lookup/confirmation-time";

test("확인 시각은 한국 시간으로 표시하고 잘못된 값은 미확인으로 처리한다", () => {
  expect(confirmationTime("2026-11-01T23:30:00Z")).toContain("2026. 11. 02.");
  expect(confirmationTime("2026-11-01T23:30:00Z")).toContain("08:30");
  expect(confirmationTime(undefined)).toBeNull();
  expect(confirmationTime("잘못된 날짜")).toBeNull();
});

test("카탈로그는 외부 동의 없이 클릭으로 시작하고 만료 가격을 분리한다", async ({ page }) => {
  await page.route("https://qcystore.co.kr/web/product/fixture.jpg", route => route.fulfill({ status: 404, body: "" }));
  await page.route("**/api/v1/product-lookups/capabilities", route => route.fulfill({ json: {
    catalogEnabled: true, searchEnabled: false, importEnabled: false,
    discoveryPolicy: { version: "catalog-v1", scope: "CURATED_EARBUDS", priceBasis: "OBSERVED_KRW_PRICE" },
  } }));
  await page.route("**/api/v1/ai/capabilities", route => route.fulfill({ json: { enabled: false } }));
  await page.route("**/api/v1/intake-templates", async route => {
    const response = await route.fetch();
    const templates = await response.json();
    await route.fulfill({ json: templates.filter((t: { key: string }) => t.key === "EARBUDS")
      .map((t: object) => ({ ...t, version: "intake-catalog-v1", budgets: [30000, 50000] })) });
  });
  await page.goto("/decisions/new");
  await page.getByRole("button", { name: "출퇴근", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "최대 예산", exact: true })).toHaveValue("5");
  await page.getByRole("button", { name: "지원 필수", exact: true }).click();
  await expect(page.getByRole("checkbox")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "노트북", exact: true })).toHaveCount(0);
  const sent = page.waitForRequest(r => r.method() === "POST" && r.url().endsWith("/api/v1/decisions"));
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  expect((await sent).postDataJSON()).toMatchObject({ templateKey: "EARBUDS", discovery: {
    templateVersion: "intake-catalog-v1", minimumRequirements: { anc: "required" }, maxBudget: 50000,
    searchConsent: false, collectionConsent: false, aiConsent: false,
  } });
  await expect(page.getByRole("heading", { name: "추천 제품", exact: true })).toBeVisible();
  const alpha = page.getByRole("article", { name: "무선 이어폰 Alpha", exact: true });
  await expect(alpha.getByText(/가격 확인 시각: .*\(한국 시간\)/)).toBeVisible();
  await expect(alpha.getByText(/사양 확인 시각: .*\(한국 시간\)/).first()).toBeVisible();
  await page.getByText("최소 조건·호환성의 근거가 부족한 제품 (1)", { exact: true }).click();
  await expect(page.getByRole("article", { name: "무선 이어폰 Gamma", exact: true })).toContainText("최근 판매가 확인이 필요합니다");
  await page.getByRole("article", { name: "무선 이어폰 Alpha", exact: true }).getByRole("button", { name: "비교에 추가" }).click();
  await expect(page.getByRole("button", { name: "후보 비교로 이동" })).toBeVisible();
});
