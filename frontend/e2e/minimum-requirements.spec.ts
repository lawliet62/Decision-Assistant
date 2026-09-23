import { expect, test, type Page } from "@playwright/test";

async function consent(page: Page) {
  for (const label of ["선택 조건을 Serper", "검색된 공개 페이지를 후보 선택 전에 Firecrawl", "선택 조건과 수집 정보를", "개인 식별·금융·건강·비공개 업무 정보가 없음을 확인합니다."]) {
    await page.getByLabel(label).check();
  }
}

const scenarios = [
  { key: "LAPTOP", name: "노트북", choices: [["메모리", "16GB 이상"], ["저장 공간", "1TB 이상"]], expected: { memory: "16", storage: "1000" } },
  { key: "PHONE", name: "휴대폰", choices: [["저장 공간", "256GB 이상"], ["화면 주사율", "90Hz 이상"]], expected: { storage: "256", refreshRate: "90" } },
  { key: "TABLET", name: "태블릿", choices: [["저장 공간", "128GB 이상"], ["화면 주사율", "120Hz 이상"], ["전용 펜 지원", "지원 필수"]], expected: { storage: "128", refreshRate: "120", pen: "required" } },
  { key: "MONITOR", name: "모니터", choices: [["해상도", "QHD 이상"], ["화면 주사율", "144Hz 이상"]], expected: { resolution: "qhd", refreshRate: "144" } },
  { key: "EARBUDS", name: "무선 이어폰", choices: [["ANC 지원", "지원 필수"]], expected: { anc: "required" } },
];

for (const scenario of scenarios) {
  test(`${scenario.name}: 최소 조건·근거·미달 분리와 승인 후 비교`, async ({ page }) => {
    if (scenario.key === "TABLET") await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`/decisions/new?category=${scenario.key}`);
    for (const [group, choice] of scenario.choices) {
      const field = page.getByRole("group", { name: group, exact: true });
      await expect(field.getByRole("button", { name: "상관없음" })).toHaveAttribute("aria-pressed", "true");
      await field.getByRole("button", { name: choice, exact: true }).click();
    }
    await consent(page);
    const sent = page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/api/v1/decisions"));
    await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
    expect((await sent).postDataJSON().discovery).toMatchObject({ templateVersion: "intake-v2", minimumRequirements: scenario.expected });
    await expect(page.getByRole("heading", { name: "추천 제품", exact: true })).toBeVisible();
    await page.reload();
    const alpha = page.getByRole("article", { name: `${scenario.name} Alpha`, exact: true });
    const beta = page.getByRole("article", { name: `${scenario.name} Beta`, exact: true });
    const gamma = page.getByRole("article", { name: `${scenario.name} Gamma`, exact: true });
    await expect(alpha.getByText("수집 사양상 충족", { exact: true })).toHaveCount(scenario.choices.length);
    await expect(gamma.getByText("사양 미확인", { exact: true })).toHaveCount(scenario.choices.length);
    await expect(beta).not.toBeVisible();
    await page.getByText("최소 조건에 미달한 제품 (1)", { exact: true }).click();
    await expect(beta).toBeVisible();
    await expect(beta.getByText("미달", { exact: true })).toHaveCount(scenario.choices.length);
    await beta.getByRole("list", { name: "최소 사양 확인" }).getByText("출처·조회 시점").first().click();
    await expect(beta.getByRole("link", { name: "원문 보기 ↗" }).first()).toHaveAttribute("href", "https://example.com/Beta");
    await beta.getByRole("button", { name: "비교에 추가", exact: true }).click();
    await alpha.getByRole("button", { name: "비교에 추가", exact: true }).click();
    await page.getByRole("button", { name: "후보 비교로 이동", exact: true }).click();
    const row = page.getByRole("row").filter({ has: page.getByRole("rowheader", { name: "최소 조건", exact: true }) });
    await expect(row).toContainText("수집 사양상 충족");
    await expect(row).toContainText("미달");
    for (const [, choice] of scenario.choices) await expect(row).toContainText(choice);
    if (scenario.key === "TABLET") await page.screenshot({ path: test.info().outputPath("minimum-requirements-mobile.png"), fullPage: true });
  });
}

test("선택 해제와 제품군 변경은 최소 조건을 초기화한다", async ({ page }) => {
  await page.goto("/decisions/new?category=LAPTOP");
  const memory = page.getByRole("group", { name: "메모리", exact: true });
  const selected = memory.getByRole("button", { name: "16GB 이상", exact: true });
  await selected.click(); await selected.click();
  await expect(memory.getByRole("button", { name: "상관없음" })).toHaveAttribute("aria-pressed", "true");
  await selected.click();
  await memory.getByRole("button", { name: "상관없음" }).click();
  await expect(selected).toHaveAttribute("aria-pressed", "false");
  await selected.click();
  await page.getByRole("button", { name: "무선 이어폰", exact: true }).click();
  await expect(page.getByRole("group", { name: "ANC 지원", exact: true }).getByRole("button", { name: "상관없음" })).toHaveAttribute("aria-pressed", "true");
  await consent(page);
  const sent = page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/api/v1/decisions"));
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  expect((await sent).postDataJSON().discovery.minimumRequirements).toEqual({});
  await expect(page.getByText("최소 조건에 미달한 제품", { exact: false })).toHaveCount(0);
});

test("기본 후보가 없으면 자동 재검색 없이 직접 추가와 조건 변경을 안내한다", async ({ page }) => {
  let creations = 0;
  await page.route("**/api/v1/decisions", async route => {
    if (route.request().method() === "POST") creations++;
    await route.continue();
  });
  await page.goto("/decisions/new?category=EARBUDS");
  await page.getByRole("group", { name: "ANC 지원", exact: true }).getByRole("button", { name: "지원 필수" }).click();
  await consent(page);
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  for (const name of ["Alpha", "Gamma"]) await page.getByRole("article", { name: `무선 이어폰 ${name}`, exact: true }).getByRole("button", { name: "제외", exact: true }).click();
  await expect(page.getByText("추가할 기본 추천 후보가 없습니다.", { exact: false })).toBeVisible();
  await expect(page.getByRole("link", { name: "조건 바꿔 새로 찾기" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "후보 추가", exact: true })).toBeVisible();
  expect(creations).toBe(1);
});
