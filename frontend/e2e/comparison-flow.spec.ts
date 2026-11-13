import { expect, test } from "@playwright/test";

test.beforeEach(async ({ request, page }) => {
  const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
    title: "조건 비교 브라우저 검증 (가상제품)", intentText: "가상 제품 비교", templateKey: "EARBUDS", webConsent: false,
    selectedSources: [{ name: "가상 Alpha", url: "https://example.com/alpha" }, { name: "가상 Beta", url: "https://example.com/beta" }],
  } });
  const decision = await response.json();
  await page.goto(`/decisions/${decision.id}`);
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();
});

test("같은 후보에서 조건 복원·직접 확인·보류·결정 기록을 보존한다", async ({ page }) => {
  const alpha = page.getByRole("article", { name: "가상 Alpha 조건 비교", exact: true });
  const beta = page.getByRole("article", { name: "가상 Beta 조건 비교", exact: true });
  await expect(alpha).toContainText("미달");
  await expect(beta).toContainText("근거 없음");
  await expect(page.getByRole("region", { name: "추가 비용과 차이" })).toContainText("40,000원 추가");
  await expect(page.getByRole("region", { name: "추가 비용과 차이" })).toContainText("ANC 지원");
  await page.getByLabel("비교 예산 상한", { exact: true }).fill("7");
  await expect(page.getByRole("button", { name: "3. 선택 기록", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "조건 적용", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("가상 Beta · 최대 예산: 충족 → 미달 (70000원)");
  await page.getByRole("button", { name: "이전 조건으로 복원", exact: true }).click();
  await expect(page.getByLabel("비교 예산 상한", { exact: true })).toHaveValue("10");
  await page.getByLabel("먼저 볼 정보", { exact: true }).selectOption("배터리");
  await page.getByRole("button", { name: "조건 적용", exact: true }).click();
  await expect(beta).toContainText("최대 8시간 (ANC 끔)");
  await expect(beta).toContainText("자동으로 우열 비교하지 않습니다");
  await beta.getByRole("button", { name: "기본 연결·사용 호환성 직접 확인·수정" }).click();
  await page.getByLabel("확인 결과", { exact: true }).selectOption("true");
  await page.getByLabel("확인 메모", { exact: true }).fill("내 iPhone에서 연결 확인");
  await page.getByRole("button", { name: "확인 정보 저장", exact: true }).click();
  await expect(beta).toContainText("확인한 사양상 충족");
  await alpha.getByRole("button", { name: "이 후보 비교 보류", exact: true }).click();
  await expect(alpha).toHaveCount(0);
  await page.reload();
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();
  await expect(alpha).toHaveCount(0);
  await page.getByRole("button", { name: "비교 복원", exact: true }).click();
  await expect(alpha).toBeVisible();
  await expect(beta).toContainText("확인한 사양상 충족");
  await page.screenshot({ path: test.info().outputPath("comparison-desktop.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator("body")).toHaveJSProperty("scrollWidth", 390);
  await page.screenshot({ path: test.info().outputPath("comparison-mobile.png"), fullPage: true });
  await page.getByRole("button", { name: "실제 선택 기록으로 이동" }).click();
  await page.getByLabel("실제로 선택한 후보", { exact: true }).selectOption({ label: "가상 Beta" });
  await page.getByPlaceholder("결정 이유").fill("4만원을 더 내고 ANC 지원, iOS는 직접 확인");
  await page.getByRole("button", { name: "선택 기록", exact: true }).click();
  await page.reload();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await page.getByText("선택 · 4만원을 더 내고 ANC 지원, iOS는 직접 확인", { exact: false }).click();
  await expect(page.getByRole("heading", { name: "결정 당시 기록" })).toBeVisible();
  await expect(page.getByText("당시 근거를 보존한 기록입니다.", { exact: false })).toBeVisible();
  await expect(beta).toContainText("확인한 사양상 충족");
});

test("저장 장애에서 입력을 지키고 모든 후보 보류를 복원할 수 있다", async ({ page }) => {
  await page.route("**/api/v1/decisions/*/comparison", route => route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ detail: "시험 장애: 저장 실패" }) }));
  await page.getByLabel("비교 예산 상한", { exact: true }).fill("6");
  await page.getByRole("button", { name: "조건 적용", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "시험 장애" })).toContainText("시험 장애");
  await expect(page.getByLabel("비교 예산 상한", { exact: true })).toHaveValue("6");
  await expect(page.getByText("저장 조건:", { exact: false })).toContainText("100,000원");
  await page.getByRole("button", { name: "조건 편집 취소" }).click();
  await page.unroute("**/api/v1/decisions/*/comparison");
  await page.getByRole("button", { name: "이 후보 비교 보류", exact: true }).first().click();
  await expect(page.getByRole("button", { name: "이 후보 비교 보류", exact: true })).toHaveCount(1);
  await page.getByRole("button", { name: "이 후보 비교 보류", exact: true }).click();
  await expect(page.getByText("비교 중인 후보가 없습니다.", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "비교 복원", exact: true }).first().click();
  await expect(page.getByRole("article", { name: "가상 Alpha 조건 비교" })).toBeVisible();
});
