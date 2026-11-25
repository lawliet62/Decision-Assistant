import { expect, test, type Page, type Request } from "@playwright/test";

// 생성 시 제목 지정(선택). 비워 두면 각 생성 경로의 자동 제목을 쓴다.
const created = (page: Page) => page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/api/v1/decisions"));
const body = async (request: Promise<Request>) => (await request).postDataJSON();

test.beforeEach(async ({ page }) => { await page.route("https://qcystore.co.kr/web/product/fixture.jpg", route => route.fulfill({ status: 404, body: "" })); });

async function consent(page: Page) {
  await page.getByLabel("선택 조건을 Serper").check();
  await page.getByLabel("검색된 공개 페이지를 후보 선택 전에 Firecrawl").check();
  await page.getByLabel("선택 조건과 수집 정보를").check();
  await page.getByLabel("개인 식별·금융·건강·비공개 업무 정보가 없음을 확인합니다.", { exact: true }).check();
}

async function startDirect(page: Page, title?: string) {
  await page.goto("/decisions/new");
  await page.getByRole("button", { name: "제품 직접 추가", exact: true }).click();
  await page.getByLabel("어떤 상황에서 무엇을 고르고 있나요?").fill("출퇴근용 무선 이어폰 비교");
  const field = page.getByLabel("비교 제목 (선택)");
  await expect(field).not.toBeFocused();
  await expect(field).toHaveAttribute("maxlength", "200");
  await expect(page.getByText("비워 두면 상황 설명 앞부분으로 제목을 자동으로 만듭니다.", { exact: false })).toBeVisible();
  if (title !== undefined) await field.fill(title);
  await page.getByLabel("제품명", { exact: true }).fill("Buds");
  await page.getByLabel("입력한 제품명을 Serper").check();
  await page.getByRole("button", { name: "제품 검색", exact: true }).click();
  await page.getByRole("list", { name: "제품 검색 결과" }).getByRole("button", { name: "이 제품 선택" }).first().click();
  const sent = created(page);
  await page.getByRole("button", { name: "비교 시작하기" }).click();
  return body(sent);
}

async function expectTitleEverywhere(page: Page, title: string) {
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
  await page.goto("/?open=DRAFT");
  await expect(page.getByRole("region", { name: "비교 중", exact: true }).getByRole("link", { name: title })).toBeVisible();
}

test("추천 시작: 입력한 제목을 보내고 상세·재접속·목록에 같은 제목을 표시한다", async ({ page }) => {
  await page.goto("/decisions/new");
  await page.getByRole("button", { name: "출퇴근", exact: true }).click();
  await consent(page);
  await page.getByText("상세 조건 (선택)", { exact: true }).click();
  const field = page.getByLabel("비교 제목 (선택)");
  await expect(field).toHaveAttribute("maxlength", "200");
  await expect(page.getByText("비워 두면 \"무선 이어폰 비교\"로 저장합니다.", { exact: false })).toBeVisible();
  await field.fill("  출근길 이어폰 고르기  ");
  const sent = created(page);
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  expect(await body(sent)).toMatchObject({ templateKey: "EARBUDS", title: "출근길 이어폰 고르기" });
  await expectTitleEverywhere(page, "출근길 이어폰 고르기");
});

test("추천 시작: 제목 입력란을 건드리지 않은 기본 흐름은 제목 없이 자동 제목을 쓴다", async ({ page }) => {
  await page.goto("/decisions/new");
  await page.getByRole("button", { name: "출퇴근", exact: true }).click();
  await consent(page);
  const sent = created(page);
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  expect(await body(sent)).not.toHaveProperty("title");
  await expect(page.getByRole("heading", { name: "추천 제품", exact: true })).toBeVisible();
  await expectTitleEverywhere(page, "무선 이어폰 비교");
});

test("추천 시작: 공백만 입력한 제목은 보내지 않는다", async ({ page }) => {
  await page.goto("/decisions/new");
  await consent(page);
  await page.getByText("상세 조건 (선택)", { exact: true }).click();
  await page.getByLabel("비교 제목 (선택)").fill("   ");
  const sent = created(page);
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  expect(await body(sent)).not.toHaveProperty("title");
  await expect(page.getByRole("heading", { name: "무선 이어폰 비교", exact: true })).toBeVisible();
});

test("직접 추가: 입력한 제목을 보내고 상세·재접속·목록에 같은 제목을 표시한다", async ({ page }) => {
  expect(await startDirect(page, "  내 출퇴근 이어폰  ")).toMatchObject({ title: "내 출퇴근 이어폰", intentText: "출퇴근용 무선 이어폰 비교" });
  await expectTitleEverywhere(page, "내 출퇴근 이어폰");
});

test("직접 추가: 제목을 비우면 보내지 않고 상황 설명으로 만든 자동 제목을 쓴다", async ({ page }) => {
  expect(await startDirect(page)).not.toHaveProperty("title");
  await expectTitleEverywhere(page, "출퇴근용 무선 이어폰 비교");
});
