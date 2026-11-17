import { expect, test, type Page } from "@playwright/test";

async function search(page: Page, query = "Buds") {
  await page.getByLabel("제품명", { exact: true }).fill(query);
  await page.getByLabel("입력한 제품명을 Serper").check();
  await page.getByRole("button", { name: "제품 검색", exact: true }).click();
}
async function start(page: Page, intent = "출퇴근용 무선 이어폰 비교", collect = false) {
  await page.goto("/decisions/new");
  await page.getByRole("button", { name: "제품 직접 추가", exact: true }).click();
  await page.getByLabel("어떤 상황에서 무엇을 고르고 있나요?").fill(intent);
  await search(page);
  await page.getByRole("list", { name: "제품 검색 결과" }).getByRole("button", { name: "이 제품 선택" }).first().click();
  if (collect) await page.getByLabel("선택한 공개 URL을 Firecrawl").check();
  await page.getByRole("button", { name: "비교 시작하기" }).click();
  await expect(page.getByRole("heading", { name: intent, exact: true })).toBeVisible();
}

test("product search, explicit selection, editing, evaluation, choice and outcome", async ({ page }) => {
  await page.goto("/decisions/new");
  await page.getByRole("button", { name: "제품 직접 추가", exact: true }).click();
  await page.getByLabel("어떤 상황에서 무엇을 고르고 있나요?").fill("출퇴근용 무선 이어폰 비교");
  await page.getByLabel("제품명", { exact: true }).fill("Buds");
  await expect(page.getByRole("button", { name: "제품 검색", exact: true })).toBeDisabled();
  await search(page);
  await expect(page.getByRole("list", { name: "제품 검색 결과" }).getByRole("listitem")).toHaveCount(3);
  await page.getByRole("list", { name: "제품 검색 결과" }).getByRole("button", { name: "이 제품 선택" }).first().click();
  await expect(page.getByRole("list", { name: "선택한 후보" })).toContainText("Buds Pro");
  await page.getByRole("button", { name: "비교 시작하기" }).click();
  await expect(page.getByText("이번 구매의 최대 예산은 얼마인가요?")).toBeVisible();
  await expect(page.getByText("이번 결정에서 가장 중요한 기준은 무엇인가요?")).toBeVisible();
  await expect(page.getByText("반드시 피하고 싶은 단점이나 조건이 있나요?")).toBeVisible();

  await search(page);
  await expect(page.getByRole("button", { name: "이미 추가됨" })).toBeDisabled();
  await page.getByRole("list", { name: "제품 검색 결과" }).getByRole("listitem").filter({ hasText: "Buds 2" }).getByRole("button").click();
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();
  await page.getByText("비교 값 확인과 평가", { exact: true }).click();
  await page.screenshot({ path: test.info().outputPath("comparison.png"), fullPage: true });
  for (const product of ["Buds Pro", "Buds 2"]) {
    await page.getByLabel(`${product} 가격`, { exact: true }).fill("150000");
    for (const criterion of ["음질", "착용감", "노이즈 캔슬링", "배터리 적합도"]) {
      await page.getByLabel(`${product} ${criterion}`, { exact: true }).selectOption("4");
    }
  }
  await expect(page.getByRole("button", { name: "검증된 평가 실행" })).toBeDisabled();
  await page.getByRole("button", { name: "검증 입력 저장" }).click();
  await page.getByRole("button", { name: "검증된 평가 실행" }).click();
  await expect(page.getByRole("heading", { name: "평가 결과", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await page.getByLabel("실제로 선택한 후보").selectOption({ label: "Buds Pro" });
  await page.getByRole("button", { name: "결정 완료", exact: true }).click();
  await page.getByRole("button", { name: "선택 사항 · 회고", exact: true }).click();
  await expect(page.getByRole("heading", { name: "사용 후 회고", exact: true })).toBeVisible();
  await page.getByPlaceholder("예상과 달랐던 점").fill("오래 착용해도 편안했어요");
  await page.getByRole("button", { name: "회고 저장", exact: true }).click();
  await expect(page.getByText("사용 후 회고를 저장했습니다.")).toBeVisible();
  await expect(page.getByText(/만족도 4.*오래 착용해도 편안했어요/)).toBeVisible();
});

test("empty results and quota failure leave URL fallback usable without external consent", async ({ page }) => {
  await page.goto("/decisions/new");
  await page.getByRole("button", { name: "제품 직접 추가", exact: true }).click();
  await page.getByLabel("어떤 상황에서 무엇을 고르고 있나요?").fill("직접 입력으로 이어폰 비교");
  await search(page, "none");
  await expect(page.getByText("검색 결과가 없습니다.", { exact: false })).toBeVisible();
  await search(page, "limit");
  await expect(page.getByRole("alert").filter({ hasText: "이번 달 검색 한도" })).toBeVisible();
  await page.getByText("URL 직접 추가 (검색이 어렵다면)", { exact: true }).click();
  await page.getByLabel("제품명 (선택)", { exact: true }).fill("직접 고른 이어폰");
  await page.getByLabel("제품 페이지 URL").fill("https://example.com/manual");
  await page.getByRole("button", { name: "URL로 후보 추가" }).click();
  await page.getByRole("button", { name: "비교 시작하기" }).click();
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();
  await page.getByText("비교 값 확인과 평가", { exact: true }).click();
  await expect(page.getByRole("heading", { name: "직접 고른 이어폰", exact: true }).last()).toBeVisible();
});

test("polling preserves unsaved values and saving conflict does not discard input", async ({ page }) => {
  await start(page, "slow 이어폰 비교");
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();
  await page.getByText("비교 값 확인과 평가", { exact: true }).click();
  const price = page.getByLabel("Buds Pro 가격", { exact: true });
  await price.fill("123456");
  await page.waitForTimeout(2800);
  await expect(price).toHaveValue("123456");
  await page.route("**/api/v1/decisions/*/draft", route => route.fulfill({ status: 409, json: { detail: "최신 내용을 다시 불러오세요." } }));
  await page.getByRole("button", { name: "검증 입력 저장" }).click();
  await expect(page.getByText("최신 내용을 다시 불러오세요.", { exact: true })).toBeVisible();
  await expect(price).toHaveValue("123456");
});

test("failed page collection can be retried without losing the candidate", async ({ page }) => {
  await start(page, "수집 재시도 이어폰 비교", true);
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();
  await page.getByText("비교 값 확인과 평가", { exact: true }).click();
  await expect(page.getByText("수집 실패", { exact: false }).first()).toBeVisible();
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(page.getByText("후보 정보 수집을 다시 시작했습니다.")).toBeVisible();
  await expect(page.getByRole("region", { name: "제품 주요 정보 표" }).getByRole("heading", { name: "Buds Pro", exact: true })).toBeVisible();
  await expect(page.getByText("수집 실패", { exact: false })).toHaveCount(0);
});

test("disabled search keeps URL fallback and enforces the five candidate limit", async ({ page }) => {
  await page.route("**/api/v1/product-lookups/capabilities", route => route.fulfill({ json: {
    searchEnabled: false, searchProvider: "Serper", searchNotice: "검색 비활성",
    importEnabled: false, importProvider: "Firecrawl", importNotice: "수집 비활성",
  } }));
  await page.goto("/decisions/new");
  await page.getByRole("button", { name: "제품 직접 추가", exact: true }).click();
  await expect(page.getByRole("button", { name: "제품 검색", exact: true })).toBeDisabled();
  await expect(page.getByText("현재 제품 검색이 꺼져 있습니다.", { exact: false })).toBeVisible();
  await page.getByText("URL 직접 추가 (검색이 어렵다면)", { exact: true }).click();
  for (let i = 1; i <= 5; i++) {
    await page.getByLabel("제품 페이지 URL").fill(`https://example.com/product-${i}`);
    await page.getByRole("button", { name: "URL로 후보 추가" }).click();
    await expect(page.getByRole("list", { name: "선택한 후보" }).getByRole("listitem")).toHaveCount(i);
  }
  await expect(page.getByLabel("제품 페이지 URL")).toBeDisabled();
  await page.getByRole("button", { name: "제거", exact: true }).last().click();
  await expect(page.getByLabel("제품 페이지 URL")).toBeEnabled();
});
