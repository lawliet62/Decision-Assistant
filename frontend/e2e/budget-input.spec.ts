import { expect, test } from "@playwright/test";

test("예산을 만원 단위로 조절·입력하고 원 단위로 전송한다", async ({ page }) => {
  await page.goto("/decisions/new");
  const budget = page.getByRole("textbox", { name: "최대 예산", exact: true });
  await expect(budget).toHaveValue("5");
  for (const [name, value] of [["5만원 늘리기", "10"], ["1만원 늘리기", "11"], ["5만원 줄이기", "6"], ["1만원 줄이기", "5"]]) {
    await page.getByRole("button", { name, exact: true }).click();
    await expect(budget).toHaveValue(value);
  }
  await budget.fill("12");
  await expect(page.getByText("최대 120,000원", { exact: true })).toBeVisible();
  await budget.fill("12.5");
  await expect(budget).toHaveValue("12");
  await budget.fill("-1");
  await expect(budget).toHaveValue("12");
  await budget.fill("10000");
  await expect(page.getByRole("button", { name: "1만원 늘리기", exact: true })).toBeDisabled();
  await budget.fill("3");
  await page.getByRole("button", { name: "5만원 줄이기", exact: true }).click();
  await expect(budget).toHaveValue("1");
  await expect(page.getByRole("button", { name: "1만원 줄이기", exact: true })).toBeDisabled();
  await budget.clear();
  await expect(page.getByText("예산 제한 없음", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "5만원부터 설정", exact: true }).click();
  await expect(budget).toHaveValue("5");
  await page.getByRole("button", { name: "제한 없음으로 변경", exact: true }).click();
  await expect(budget).toBeEmpty();
  await budget.fill("12");
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator("body")).toHaveJSProperty("scrollWidth", 390);
  await page.screenshot({ path: test.info().outputPath("budget-mobile.png"), fullPage: true });
  const sent = page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/api/v1/decisions"));
  for (const checkbox of await page.getByRole("checkbox").all()) await checkbox.check();
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  expect((await sent).postDataJSON().discovery.maxBudget).toBe(120000);
});
