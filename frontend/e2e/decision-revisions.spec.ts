import { expect, test } from "@playwright/test";

test("완료 기록을 정정하고 구매하지 않음으로 완료한 뒤 다시 검토한다", async ({ page, request }) => {
  const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
    intentText: "정정과 재검토 검증", templateKey: "EARBUDS", webConsent: false,
    selectedSources: [{ name: "Alpha", url: "https://example.com/alpha" }],
  } });
  const decision = await response.json();
  const capabilities = page.waitForResponse("**/api/v1/ai/capabilities");
  await page.goto(`/decisions/${decision.id}`);
  await capabilities;
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await page.getByLabel("실제로 선택한 후보").selectOption({ label: "Alpha" });
  await page.getByLabel("결정 이유", { exact: true }).fill("잘못 입력한 이유");
  await page.getByRole("button", { name: "선택 기록", exact: true }).click();
  await expect(page.getByText("결정 완료 · 후보 선택", { exact: false })).toBeVisible();
  await page.getByPlaceholder("예상과 달랐던 점").fill("이전 선택의 회고");
  await page.getByRole("button", { name: "회고 저장", exact: true }).click();
  await page.getByRole("button", { name: "기록 수정", exact: true }).click();
  await expect(page.getByText("기록 수정 중", { exact: false })).toBeVisible();
  await page.getByLabel("제목", { exact: true }).fill("정정한 비교 제목");
  await page.getByRole("button", { name: "변경 저장", exact: true }).click();
  await page.reload();
  await expect(page.getByRole("heading", { name: "정정한 비교 제목", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await expect(page.getByRole("button", { name: "보류", exact: true })).toBeDisabled();
  await page.getByLabel("결정 이유", { exact: true }).fill("이미 가진 제품으로 충분함");
  await page.getByRole("button", { name: "구매하지 않음으로 완료", exact: true }).click();
  await expect(page.getByRole("heading", { name: "결정 후 회고", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await expect(page.getByText("정정된 이전 기록", { exact: false })).toBeVisible();
  const old = page.locator("details").filter({ has: page.locator("summary", { hasText: "정정된 이전 기록" }) });
  await old.locator("summary").first().click();
  await expect(old.getByText("이 기록의 회고", { exact: false })).toContainText("이전 선택의 회고");
  await page.getByRole("button", { name: "다시 검토", exact: true }).click();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await expect(page.getByRole("button", { name: "보류", exact: true })).toBeEnabled();
  await page.getByLabel("실제로 선택한 후보").selectOption({ label: "Alpha" });
  await page.getByRole("button", { name: "선택 기록", exact: true }).click();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await expect(page.getByText("재검토 결과", { exact: false })).toBeVisible();
  await expect(page.locator("summary").filter({ hasText: "구매하지 않음 · 기록 수정" })).toHaveCount(1);
  await page.screenshot({ path: test.info().outputPath("decision-revisions.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator("body")).toHaveJSProperty("scrollWidth", 390);
  await page.screenshot({ path: test.info().outputPath("decision-revisions-mobile.png"), fullPage: true });
});

test("이전 형식의 결정도 정정과 재검토를 제공한다", async ({ page, request }) => {
  const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
    legacyFixture: true, title: "이전 형식 결정", intentText: "이전 형식 검증", templateKey: "EARBUDS",
    selectedSources: [{ name: "Alpha", url: "https://example.com/alpha" }],
  } });
  const decision = await response.json();
  const capabilities = page.waitForResponse("**/api/v1/ai/capabilities");
  await page.goto(`/decisions/${decision.id}`);
  await capabilities;
  await page.getByLabel("실제로 선택한 후보").selectOption({ label: "Alpha" });
  await page.getByRole("button", { name: "선택 기록", exact: true }).click();
  await expect(page.getByRole("heading", { name: "완료한 결정 관리" })).toBeVisible();
  await expect(page.getByLabel("제목", { exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "기록 수정", exact: true }).click();
  await page.getByLabel("제목", { exact: true }).fill("이전 형식 정정 완료");
  await page.getByRole("button", { name: "Draft 저장", exact: true }).click();
  await expect(page.getByRole("heading", { name: "이전 형식 정정 완료" })).toBeVisible();
  await expect(page.getByRole("button", { name: "보류 기록", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "구매하지 않음으로 완료", exact: true }).click();
  await expect(page.getByText("결정 완료 · 구매하지 않음", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "다시 검토", exact: true }).click();
  await expect(page.getByLabel("제목", { exact: true })).toBeEnabled();
  await expect(page.getByRole("button", { name: "보류 기록", exact: true })).toBeEnabled();
});
