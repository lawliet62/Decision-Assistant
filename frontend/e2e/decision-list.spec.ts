import { expect, test } from "@playwright/test";

test("구매하지 않음의 이전 필터 링크도 완료 그룹으로 열고 접을 수 있다", async ({ page }) => {
  await page.goto("/?statuses=ABANDONED&open=ABANDONED");
  const completed = page.getByRole("region", { name: "결정 완료", exact: true });
  await expect(completed.getByRole("listitem")).toHaveCount(3);
  await expect(completed.getByText("구매하지 않음", { exact: true })).toBeVisible();
  await completed.getByRole("button", { name: "결정 완료 3건" }).click();
  await expect(completed.getByRole("button", { name: "결정 완료 3건" })).toHaveAttribute("aria-expanded", "false");
});

test("완료 결정을 보관하고 활용을 끈 뒤 삭제·복구한다", async ({ page }) => {
  await page.goto("/?open=CHOSEN");
  const chosen = page.getByRole("region", { name: "결정 완료", exact: true });
  page.on("dialog", dialog => dialog.accept());
  await chosen.getByRole("button", { name: "보관하기", exact: true }).first().click();
  await page.getByRole("button", { name: "보관함", exact: true }).click();
  const management = page.getByRole("region", { name: "결정 보관과 삭제 관리" });
  await expect(management.getByRole("link")).toHaveCount(1);
  const title = await management.getByRole("link").innerText();
  await management.getByRole("button", { name: "개인화 활용 끄기" }).click();
  await expect(management.getByRole("button", { name: "개인화 활용 켜기" })).toBeVisible();
  await management.getByRole("button", { name: "삭제하기", exact: true }).click();
  await expect(management.getByText("결정이 없습니다.")).toBeVisible();
  await page.getByRole("button", { name: "휴지통", exact: true }).click();
  await expect(management.getByText(title, { exact: true })).toBeVisible();
  await expect(management.getByText("복구 기한:", { exact: false })).toBeVisible();
  await management.getByRole("button", { name: "복구하기" }).click();
  await expect(management.getByText("결정이 없습니다.")).toBeVisible();
  await page.getByRole("button", { name: "보관함", exact: true }).click();
  await expect(management.getByRole("link", { name: title })).toBeVisible();
  await expect(management.getByRole("button", { name: "개인화 활용 켜기" })).toBeVisible();
});

test.beforeEach(async ({ request }) => {
  await request.post("http://127.0.0.1:18080/__test/decision-list", { data: {} });
});

test("백업 복원 기준 시각과 변경 유실 가능성을 안내한다", async ({ page }) => {
  await page.route("**/api/v1/decisions/recovery-notice", route => route.fulfill({
    json: { restoredTo: "2026-11-14T03:00:00Z" },
  }));
  await page.goto("/");
  await expect(page.getByText("장애 복구로 서비스 데이터가", { exact: false })).toContainText("KST");
  await expect(page.getByText("장애 복구로 서비스 데이터가", { exact: false })).toContainText("생성·수정·삭제한 기록이 반영되지 않았을 수 있습니다.");
});

test("선택 버튼은 범위별 상태를 표시하고 다시 누르면 해당 범위만 해제한다", async ({ page }) => {
  await page.goto("/?statuses=DRAFT,CHOSEN&open=DRAFT,CHOSEN");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  const chosen = page.getByRole("region", { name: "결정 완료", exact: true });
  const row = draft.locator('[aria-label="비교 중 결정 선택"]');
  await expect(row.getByRole("button")).toHaveCount(5);
  const topPositions = await row.getByRole("button").evaluateAll(buttons => buttons.map(button => Math.round(button.getBoundingClientRect().top)));
  expect(new Set(topPositions).size).toBe(1);
  await chosen.getByRole("checkbox", { name: "결정 12 선택" }).check();
  const pageToggle = draft.getByRole("button", { name: "이 페이지 선택" });
  const normalColor = await pageToggle.evaluate(button => getComputedStyle(button).backgroundColor);
  await pageToggle.click();
  await expect(pageToggle).toHaveAttribute("aria-pressed", "true");
  expect(await pageToggle.evaluate(button => getComputedStyle(button).backgroundColor)).not.toBe(normalColor);
  await pageToggle.click();
  await expect(pageToggle).toHaveAttribute("aria-pressed", "false");
  await expect(chosen.getByRole("checkbox", { name: "결정 12 선택" })).toBeChecked();
  const statusToggle = draft.getByRole("button", { name: "이 상태 전체 선택" });
  await statusToggle.click();
  await expect(statusToggle).toHaveAttribute("aria-pressed", "true");
  await expect(statusToggle).toBeFocused();
  await expect(pageToggle).toHaveAttribute("aria-pressed", "true");
  await draft.getByRole("checkbox", { name: "결정 11 선택" }).uncheck();
  await expect(statusToggle).toHaveAttribute("aria-pressed", "false");
  await statusToggle.click();
  await expect(statusToggle).toHaveAttribute("aria-pressed", "true");
  await statusToggle.click();
  await expect(statusToggle).toHaveAttribute("aria-pressed", "false");
  await expect(page.getByText("1개 선택", { exact: false })).toBeVisible();
  const all = draft.getByRole("button", { name: "선택한 상태 결정 모두 선택" });
  await all.click();
  await expect(all).toHaveAttribute("aria-pressed", "true");
  await expect(chosen.getByRole("button", { name: "이 상태 전체 선택" })).toHaveAttribute("aria-pressed", "true");
  await all.click();
  await expect(all).toHaveAttribute("aria-pressed", "false");
  await expect(page.getByText("0개 선택", { exact: false })).toBeVisible();
});

test("오래된 서버의 선택 거부는 서버 재시작 안내와 함께 선택을 보존한다", async ({ page }) => {
  await page.goto("/?open=DRAFT");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  await draft.getByRole("checkbox", { name: "결정 11 선택" }).check();
  await page.route("**/api/v1/decisions/selection?*", route => route.fulfill({ status: 403, body: "" }));
  await draft.getByRole("button", { name: "이 상태 전체 선택" }).click();
  await expect(draft.getByRole("alert")).toContainText("서버를 최신 버전으로 다시 시작");
  await expect(draft.getByRole("checkbox", { name: "결정 11 선택" })).toBeChecked();
});

test("단일 결정 삭제 후 다른 결정의 선택을 유지한다", async ({ page }) => {
  await page.goto("/?open=DRAFT");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  await draft.getByRole("checkbox", { name: "결정 10 선택" }).check();
  await draft.getByRole("button", { name: "결정 11 삭제" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "휴지통으로 이동" }).click();
  await expect(draft.getByRole("checkbox", { name: "결정 10 선택" })).toBeChecked();
  await expect(page.getByText("1개 선택", { exact: false })).toBeVisible();
});

test("상태별 페이지 선택과 모든 페이지 선택은 다른 상태의 선택을 유지한다", async ({ page }) => {
  await page.goto("/?open=DRAFT,CHOSEN");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  const chosen = page.getByRole("region", { name: "결정 완료", exact: true });
  await chosen.getByRole("checkbox", { name: "결정 12 선택" }).check();
  await draft.getByRole("button", { name: "이 페이지 선택" }).click();
  await expect(page.getByText("11개 선택", { exact: false })).toBeVisible();
  await draft.getByRole("button", { name: "다음" }).click();
  await expect(draft.getByRole("checkbox", { name: "결정 01 선택" })).not.toBeChecked();
  await expect(chosen.getByRole("checkbox", { name: "결정 12 선택" })).toBeChecked();
  await draft.getByRole("button", { name: "이 상태 전체 선택" }).click();
  await expect(page.getByText("12개 선택", { exact: false })).toBeVisible();
  await expect(draft.getByRole("checkbox", { name: "결정 01 선택" })).toBeChecked();
  await expect(chosen.getByRole("checkbox", { name: "결정 12 선택" })).toBeChecked();
  await expect(chosen.getByRole("checkbox", { name: "결정 13 선택" })).not.toBeChecked();
  await page.locator('[aria-label="결정 선택"]').getByRole("button", { name: "선택 삭제" }).click();
  await expect(page.getByRole("dialog")).toContainText("12개");
});

test("상태 전체 선택 실패 시 다른 선택을 보존하고 빈 그룹은 선택을 비활성화한다", async ({ page }) => {
  await page.goto("/?open=DRAFT,ARCHIVED");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  const archived = page.getByRole("region", { name: "보관됨", exact: true });
  await expect(archived.getByRole("button", { name: "이 페이지 선택" })).toBeDisabled();
  await expect(archived.getByRole("button", { name: "이 상태 전체 선택" })).toBeDisabled();
  await draft.getByRole("checkbox", { name: "결정 11 선택" }).check();
  await page.route("**/api/v1/decisions/selection?*", route => route.fulfill({ status: 503, json: {} }));
  await draft.getByRole("button", { name: "이 상태 전체 선택" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "결정 목록을 불러오지 못했습니다" })).toBeVisible();
  await expect(draft.getByRole("checkbox", { name: "결정 11 선택" })).toBeChecked();
  await expect(page.getByText("1개 선택", { exact: false })).toBeVisible();
});

test("표시 상태 전체 선택은 모든 페이지와 접힌 그룹을 포함하고 선택 후 필터 변경에도 유지된다", async ({ page }) => {
  await page.goto("/?statuses=DRAFT,CHOSEN&open=DRAFT");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  await expect(draft.getByRole("listitem")).toHaveCount(10);
  await page.locator('[aria-label="결정 선택"]').getByRole("button", { name: "선택한 상태 결정 모두 선택" }).click();
  await expect(page.getByText("14개 선택", { exact: false })).toBeVisible();
  await page.getByRole("checkbox", { name: "결정 완료", exact: true }).uncheck();
  await expect(page.getByText("현재 화면에 없는 결정 4개", { exact: false })).toBeVisible();
  await page.locator('[aria-label="결정 선택"]').getByRole("button", { name: "선택 삭제" }).click();
  await expect(page.getByRole("dialog")).toContainText("14개");
  await page.getByRole("dialog").getByRole("button", { name: "취소" }).click();
  await expect(page.getByText("14개 선택", { exact: false })).toBeVisible();
  await page.locator('[aria-label="결정 선택"]').getByRole("button", { name: "선택 삭제" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "휴지통으로 이동" }).click();
  await expect(page.getByRole("dialog")).toContainText("14개 삭제 또는 이미 삭제됨");
  await page.getByRole("dialog").getByRole("button", { name: "닫기" }).click();
  await expect(page.getByRole("button", { name: "비교 중 0건" })).toBeVisible();
});

test("개별 복수 선택의 일부 충돌과 통신 실패를 표시하고 성공분만 선택에서 제거한다", async ({ page }) => {
  await page.goto("/?open=DRAFT");
  await page.getByRole("checkbox", { name: "결정 11 선택" }).check();
  await page.getByRole("checkbox", { name: "결정 10 선택" }).check();
  await page.getByRole("checkbox", { name: "결정 09 선택" }).check();
  await page.route("**/api/v1/decisions/*?expectedVersion=*", async route => {
    if (route.request().url().includes("99999999-9999-9999-9999-000000000009")) return route.fulfill({ status: 409, json: {} });
    if (route.request().url().includes("99999999-9999-9999-9999-000000000008")) return route.abort();
    return route.continue();
  });
  await page.locator('[aria-label="결정 선택"]').getByRole("button", { name: "선택 삭제" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "휴지통으로 이동" }).click();
  await expect(page.getByRole("dialog")).toContainText("1개 삭제 또는 이미 삭제됨 · 2개 확인 필요");
  await expect(page.getByRole("dialog")).toContainText("다른 곳에서 변경됨");
  await expect(page.getByRole("dialog")).toContainText("결과 확인 불가");
  await page.getByRole("dialog").getByRole("button", { name: "닫기" }).click();
  await expect(page.getByText("2개 선택", { exact: false })).toBeVisible();
});

test("페이지 간 개별 선택과 전체 선택 오류, 이미 삭제된 결정의 결과를 처리한다", async ({ page }) => {
  await page.goto("/?open=DRAFT");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  await draft.getByRole("checkbox", { name: "결정 11 선택" }).check();
  await draft.getByRole("button", { name: "다음" }).click();
  await draft.getByRole("checkbox", { name: "결정 01 선택" }).check();
  await page.route("**/api/v1/decisions/selection?*", route => route.fulfill({ status: 503, json: {} }));
  await page.locator('[aria-label="결정 선택"]').getByRole("button", { name: "선택한 상태 결정 모두 선택" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "결정 목록을 불러오지 못했습니다" })).toBeVisible();
  await expect(page.getByText("2개 선택", { exact: false })).toBeVisible();
  await page.route("**/api/v1/decisions/*?expectedVersion=*", route => route.fulfill({ status: 404, json: {} }));
  await page.locator('[aria-label="결정 선택"]').getByRole("button", { name: "선택 삭제" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "휴지통으로 이동" }).click();
  await expect(page.getByRole("dialog")).toContainText("2개 삭제 또는 이미 삭제됨 · 0개 확인 필요");
  await page.getByRole("dialog").getByRole("button", { name: "닫기" }).click();
  await expect(page.getByText("0개 선택", { exact: false })).toBeVisible();
});

test("상태 그룹은 기본 접힘이며 복수 선택과 그룹별 페이지·탐색 복원을 지원한다", async ({ page }) => {
  const lists: string[] = [];
  page.on("request", r => { if (new URL(r.url()).pathname === "/api/v1/decisions") lists.push(r.url()); });
  await page.goto("/");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  await expect(draft.getByRole("button", { name: "비교 중 11건" })).toHaveAttribute("aria-expanded", "false");
  expect(lists).toHaveLength(0);
  await page.getByRole("button", { name: "전체 해제" }).click();
  await expect(page.getByText("선택한 상태가 없습니다.", { exact: false })).toBeVisible();
  await page.getByRole("checkbox", { name: "비교 중", exact: true }).check();
  await page.getByRole("checkbox", { name: "결정 완료", exact: true }).check();
  await expect(page.getByRole("region", { name: "보류", exact: true })).toHaveCount(0);
  await draft.getByRole("button", { name: "비교 중 11건" }).click();
  await expect(draft.getByRole("listitem")).toHaveCount(10);
  await draft.getByRole("button", { name: "다음", exact: true }).click();
  await expect(draft.getByRole("listitem")).toHaveCount(1);
  await expect(draft.getByText("2 / 2 페이지 · 11건")).toBeVisible();
  const chosen = page.getByRole("region", { name: "결정 완료", exact: true });
  await chosen.getByRole("button", { name: "결정 완료 3건" }).click();
  await expect(chosen.getByRole("listitem")).toHaveCount(3);
  await draft.getByRole("link").click();
  await expect(page).toHaveURL(/\/decisions\/999/);
  await page.goBack();
  await expect(draft.getByText("2 / 2 페이지 · 11건")).toBeVisible();
  await expect(chosen.getByRole("listitem")).toHaveCount(3);
  await page.reload();
  await expect(draft.getByRole("listitem")).toHaveCount(1);
  expect(lists.every(url => new URL(url).searchParams.get("size") === "10")).toBe(true);
});

test("정렬 변경은 페이지를 초기화하고 생성일·수정일·제목·제품군 정렬을 전달한다", async ({ page }) => {
  await page.goto("/?open=DRAFT&page.DRAFT=1");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  await expect(draft.getByRole("listitem")).toHaveCount(1);
  await page.getByLabel("정렬 방향").selectOption("ASC");
  await expect(draft.getByRole("listitem")).toHaveCount(10);
  await expect(draft.getByRole("link").first()).toContainText("결정 01");
  for (const sort of ["TITLE", "TEMPLATE_KEY", "CREATED_AT", "UPDATED_AT"]) {
    const response = page.waitForResponse(r => new URL(r.url()).pathname === "/api/v1/decisions" && new URL(r.url()).searchParams.get("sort") === sort);
    await page.getByLabel("정렬 기준").selectOption(sort);
    expect((await response).ok()).toBe(true);
  }
  await page.getByLabel("정렬 방향").selectOption("DESC");
  await expect(draft.getByRole("link").first()).toContainText("결정 11");
});

test("삭제 취소·오류·성공과 마지막 페이지 보정을 제공한다", async ({ page }) => {
  await page.goto("/?open=DRAFT&page.DRAFT=1");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  await draft.getByRole("button", { name: "결정 01 삭제" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("button", { name: "취소", exact: true })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(draft.getByRole("button", { name: "결정 01 삭제" })).toBeFocused();
  await page.route("**/api/v1/decisions/*?expectedVersion=*", route => route.fulfill({ status: 503, json: { detail: "fixture" } }));
  await draft.getByRole("button", { name: "결정 01 삭제" }).click();
  await page.getByRole("button", { name: "휴지통으로 이동", exact: true }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("삭제하지 못했습니다");
  await expect(draft.getByRole("listitem")).toHaveCount(1);
  await page.unroute("**/api/v1/decisions/*?expectedVersion=*");
  await page.getByRole("button", { name: "휴지통으로 이동", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(draft.getByRole("listitem")).toHaveCount(10);
  await expect(draft.getByText("1 / 1 페이지 · 10건")).toBeVisible();
  await expect(draft.getByRole("button", { name: "비교 중 10건" })).toBeVisible();
  await page.reload();
  await expect(draft.getByRole("link", { name: /결정 01/ })).toHaveCount(0);
});

test("빈 목록과 필터 결과 없음·조회 실패 복구를 구분한다", async ({ page, request }) => {
  await page.route("**/api/v1/decisions/counts", route => route.fulfill({ status: 503, json: {} }));
  await page.goto("/");
  await expect(page.getByRole("alert").filter({ hasText: "목록을 불러오지 못했습니다" })).toBeVisible();
  await page.unroute("**/api/v1/decisions/counts");
  await page.getByRole("button", { name: "다시 시도" }).click();
  await expect(page.getByRole("button", { name: "비교 중 11건" })).toBeVisible();
  await page.goto("/?statuses=ARCHIVED&open=ARCHIVED");
  await expect(page.getByText("선택한 상태의 결정이 없습니다.")).toBeVisible();
  await request.post("http://127.0.0.1:18080/__test/decision-list", { data: { empty: true } });
  await page.goto("/");
  await expect(page.getByText("아직 저장한 결정이 없습니다.", { exact: false })).toBeVisible();
});

test("모바일에서 가로 넘침 없이 키보드로 그룹과 삭제 확인을 조작한다", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/");
  const draft = page.getByRole("region", { name: "비교 중", exact: true });
  const toggle = draft.getByRole("button", { name: "비교 중 11건" });
  await toggle.focus();
  await page.keyboard.press("Enter");
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  await expect(draft.getByRole("listitem")).toHaveCount(10);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await draft.getByRole("button", { name: "결정 11 삭제" }).click();
  expect(await page.getByRole("dialog").evaluate(el => el.getBoundingClientRect().width <= window.innerWidth)).toBe(true);
  await page.keyboard.press("Escape");
});
