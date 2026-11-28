import { expect, test } from "@playwright/test";

// 실제 API는 예산 없이 만든 결정의 비교 조건을 maxBudget: null로 내려보낸다(생략하지 않는다).
test("예산 없이 만든 결정도 비교·평가 단계가 열리고 예산을 정할 수 있다", async ({ request, page }) => {
  const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
    title: "예산 없는 조건 비교 (가상제품)", intentText: "가상 제품 비교", templateKey: "EARBUDS", webConsent: false,
    selectedSources: [{ name: "가상 Alpha", url: "https://example.com/alpha" }, { name: "가상 Beta", url: "https://example.com/beta" }],
  } });
  const decision = await response.json();
  expect(decision.comparison.settings.maxBudget).toBeNull();
  await page.goto(`/decisions/${decision.id}`);
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();

  const budget = page.getByLabel("비교 예산 상한", { exact: true });
  await expect(budget).toBeEmpty();
  await expect(page.getByText("예산 제한 없음", { exact: true })).toBeVisible();
  await expect(page.getByText("저장 조건:", { exact: false })).toContainText("예산 제한 없음");
  await expect(page.getByRole("article", { name: "가상 Alpha 조건 비교", exact: true })).toBeVisible();

  await budget.fill("7");
  await page.getByRole("button", { name: "조건 적용", exact: true }).click();
  await expect(page.getByText("저장 조건:", { exact: false })).toContainText("70,000원");
});
