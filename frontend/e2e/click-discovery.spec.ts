import { expect, test, type Page } from "@playwright/test";

async function consent(page: Page) {
  await page.getByLabel("선택 조건을 Serper").check();
  await page.getByLabel("검색된 공개 페이지를 후보 선택 전에 Firecrawl").check();
  await page.getByLabel("선택 조건과 수집 정보를").check();
  await page.getByLabel("개인 식별·금융·건강·비공개 업무 정보가 없음을 확인합니다.", { exact: true }).check();
}

for (const [key, name, use] of [["LAPTOP", "노트북", "학업"], ["PHONE", "휴대폰", "일상"], ["TABLET", "태블릿", "필기"], ["MONITOR", "모니터", "사무"], ["EARBUDS", "무선 이어폰", "출퇴근"]]) {
  test(`${name}: 조건 선택부터 주요 정보 비교까지 타이핑 없이 진행`, async ({ page }) => {
    if (key === "EARBUDS") await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "나에게 맞는 제품 찾기" })).toBeVisible();
    await page.getByRole("navigation", { name: "제품군 선택" }).getByRole("link", { name, exact: false }).click();
    await expect(page.getByRole("button", { name, exact: true })).toHaveAttribute("aria-pressed", "true");
    await page.getByRole("button", { name: use, exact: true }).click();
    const secondUse = key === "EARBUDS" ? "운동" : key === "TABLET" ? "그림" : "게임";
    await page.getByRole("button", { name: secondUse, exact: true }).click();
    await expect(page.getByRole("button", { name: use, exact: true })).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByRole("button", { name: "추천 제품 보기", exact: true })).toBeDisabled();
    await consent(page);
    const sent = page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/api/v1/decisions"));
    await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
    expect((await sent).postDataJSON()).toMatchObject({ templateKey: key, discovery: { uses: [use, secondUse], searchConsent: true } });
    await expect(page.getByRole("heading", { name: "추천 제품", exact: true })).toBeVisible();
    await page.reload();
    for (let index = 0; index < 2; index++) await page.getByRole("button", { name: "비교에 추가", exact: true }).first().click();
    await page.getByRole("button", { name: "후보 비교로 이동", exact: true }).click();
    await expect(page.getByRole("heading", { name: "주요 정보 비교", exact: true })).toBeVisible();
    await expect(page.getByRole("table").getByRole("columnheader")).toHaveCount(3);
    await expect(page.getByRole("table")).toContainText("100000 KRW");
    await expect(page.getByRole("table")).toContainText("미확인");
    await expect(page.getByRole("table")).toContainText("확인 필요");
    await expect(page.getByRole("button", { name: "검증된 평가 실행" })).not.toBeVisible();
    await page.getByRole("region", { name: "제품 주요 정보 표" }).focus();
    await page.keyboard.press("ArrowRight");
    if (key === "EARBUDS") await page.screenshot({ path: test.info().outputPath("mobile-comparison.png"), fullPage: true });
    await page.getByRole("button", { name: "실제 선택 기록으로 이동" }).click();
    await page.getByLabel("실제로 선택한 후보").selectOption({ label: name + " Alpha" });
    await page.getByRole("button", { name: "선택 기록", exact: true }).click();
    await expect(page.getByRole("heading", { name: "사용 후 회고", exact: true })).toBeVisible();
  });
}

test("복수 용도 해제와 미정 선택 및 제품군 변경", async ({ page }) => {
  await page.goto("/decisions/new");
  const unknown = page.getByRole("button", { name: "아직 모르겠어요", exact: true });
  const commute = page.getByRole("button", { name: "출퇴근", exact: true });
  const exercise = page.getByRole("button", { name: "운동", exact: true });
  await expect(unknown).toHaveAttribute("aria-pressed", "true");
  await commute.click(); await exercise.click(); await commute.click();
  await expect(commute).toHaveAttribute("aria-pressed", "false");
  await expect(exercise).toHaveAttribute("aria-pressed", "true");
  await expect(unknown).toHaveAttribute("aria-pressed", "false");
  await unknown.click();
  await expect(exercise).toHaveAttribute("aria-pressed", "false");
  await commute.click(); await commute.click();
  await expect(unknown).toHaveAttribute("aria-pressed", "true");
  await commute.click();
  await page.getByRole("button", { name: "노트북", exact: true }).click();
  await expect(unknown).toHaveAttribute("aria-pressed", "true");
  await consent(page);
  const sent = page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/api/v1/decisions"));
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  expect((await sent).postDataJSON()).toMatchObject({ templateKey: "LAPTOP", discovery: { uses: [] } });
});

test("추천 비활성화여도 직접 추가 가능", async ({ page }) => {
  await page.route("**/api/v1/ai/capabilities", route => route.fulfill({ json: { enabled: false, provider: "gemini" } }));
  await page.goto("/decisions/new");
  await expect(page.getByText("현재 추천 서비스가 꺼져 있습니다.", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "제품 직접 추가", exact: true }).click();
  await expect(page.getByLabel("제품명", { exact: true })).toBeVisible();
});

test("추천 부분 실패 표시와 명시적 재시도", async ({ page }) => {
  await page.goto("/decisions/new"); await consent(page);
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "추천 제품", exact: true })).toBeVisible();
  await page.route("**/api/v1/decisions/11111111-1111-1111-1111-111111111111", async route => {
    const response = await route.fetch(); const data = await response.json();
    data.discovery.status = "PARTIAL"; data.discovery.error = "일부 정보만 확인했습니다.";
    await route.fulfill({ json: data });
  });
  // Client refresh retrieves the same persisted partial state that a restored page would see.
  await page.getByRole("button", { name: "비교에 추가", exact: true }).first().click();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await page.getByRole("button", { name: "보류", exact: true }).click();
  await page.getByRole("button", { name: "1. 비교 준비", exact: true }).click();
  await expect(page.getByText("일부 정보만 확인했습니다.", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "추천 다시 시도", exact: true }).click();
  await expect(page.getByText("제품 정보를 찾고 있습니다…", { exact: true })).toBeVisible();
});
