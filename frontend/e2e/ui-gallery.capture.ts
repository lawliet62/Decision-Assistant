import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { cp, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

// 제품 코드를 변경하지 않고 기존 mock backend와 화면 조작으로 대표 상태를 재현한다.
// playwright.docs.config.ts에서만 실행하며 일반 *.spec.ts와 분리한다.
test("개발자 문서용 화면 모음 촬영", async ({ page, request }) => {
  const root = path.resolve(__dirname, "../..");
  const directory = test.info().outputPath("ui-gallery");
  const images: { file: string; route: string; state: string; viewport: { width: number; height: number } }[] = [];
  const viewports = {
    desktop: { width: 1440, height: 1000 },
    mobile: { width: 390, height: 844 },
  };
  async function capture(name: string, state: string) {
    for (const [device, viewport] of Object.entries(viewports)) {
      await page.setViewportSize(viewport);
      await page.waitForLoadState("networkidle");
      await page.evaluate(() => document.fonts.ready);
      await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
      await page.evaluate(() => window.scrollTo(0, 0));
      const file = `${name}-${device}.png`;
      await mkdir(path.dirname(path.join(directory, file)), { recursive: true });
      await page.screenshot({ path: path.join(directory, file), fullPage: true, animations: "disabled",
        style: "nextjs-portal { visibility: hidden; }" });
      images.push({ file, route: new URL(page.url()).pathname + new URL(page.url()).search, state, viewport });
    }
    await page.setViewportSize(viewports.desktop);
  }
  await page.route("**/*", route => {
    const host = new URL(route.request().url()).hostname;
    return host === "127.0.0.1" || host === "localhost" ? route.continue() : route.abort();
  });
  // 기본 카탈로그 UI를 기존 catalog.spec.ts와 같은 capabilities fixture로 연다.
  await page.route("**/api/v1/product-lookups/capabilities", route => route.fulfill({ json: {
    catalogEnabled: true, searchEnabled: false, importEnabled: false,
    discoveryPolicy: { version: "catalog-v1", scope: "CURATED_EARBUDS", priceBasis: "OBSERVED_KRW_PRICE" },
  } }));
  await page.route("**/api/v1/ai/capabilities", route => route.fulfill({ json: { enabled: false } }));
  await page.route("**/api/v1/intake-templates", async route => {
    const response = await route.fetch();
    const templates = await response.json();
    await route.fulfill({ json: templates.filter((item: { key: string }) => item.key === "EARBUDS")
      .map((item: object) => ({ ...item, version: "intake-catalog-v1", budgets: [50000, 200000] })) });
  });
  await page.goto("/decisions/new");
  await expect(page.getByRole("button", { name: "출퇴근", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "출퇴근", exact: true }).click();
  await page.getByLabel("최대 예산", { exact: true }).fill("20");
  await page.getByRole("button", { name: "지원 필수", exact: true }).click();
  await capture("discovery/01-intake", "카탈로그 추천 조건 선택 · 출퇴근·20만원·ANC");
  await page.getByRole("button", { name: "추천 제품 보기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "추천 제품", exact: true })).toBeVisible();
  await expect(page.getByRole("article", { name: "무선 이어폰 Alpha", exact: true })).toBeVisible();
  await capture("discovery/02-candidates", "가상 추천 후보 · 충족·미달·미확인 구분");

  // 사양 조건 비교는 기존 comparison-flow.spec.ts의 가상 Alpha/Beta fixture다.
  const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
    title: "조건 비교 브라우저 검증 (가상제품)", intentText: "출퇴근용 이어폰 비교 (가상제품)",
    templateKey: "EARBUDS", webConsent: false,
    selectedSources: [{ name: "가상 Alpha", url: "https://example.com/alpha" }, { name: "가상 Beta", url: "https://example.com/beta" }],
  } });
  expect(response.ok()).toBeTruthy();
  const decision = await response.json();
  await page.goto(`/decisions/${decision.id}`);
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();
  await expect(page.getByRole("article", { name: "가상 Beta 조건 비교", exact: true })).toBeVisible();
  await capture("comparison/03-conditions", "현재 비교 조건과 후보별 충족·미달·미확인");
  const beta = page.getByRole("article", { name: "가상 Beta 조건 비교", exact: true });
  await beta.getByRole("button", { name: "기본 연결·사용 호환성 직접 확인·수정" }).click();
  await page.getByLabel("확인 결과", { exact: true }).selectOption("true");
  await page.getByLabel("확인 메모", { exact: true }).fill("내 iPhone에서 연결 확인 (문서용 가상 기록)");
  await capture("comparison/04-confirmation", "수집 근거와 구분한 사용자 직접 확인 입력");
  await page.getByRole("button", { name: "확인 정보 저장", exact: true }).click();
  await expect(beta).toContainText("확인한 사양상 충족");
  await page.getByRole("button", { name: "실제 선택 기록으로 이동" }).click();
  await page.getByLabel("실제로 선택한 후보").selectOption({ label: "가상 Beta" });
  await page.getByLabel("결정 이유", { exact: true }).fill("추가 비용을 감수하고 ANC를 선택, iOS 연결은 직접 확인");
  await capture("completion/05-resolution", "후보 선택·보류·구매하지 않음 기록 전");
  await page.getByRole("button", { name: "선택 기록", exact: true }).click();
  await expect(page.getByRole("heading", { name: "사용 후 회고", exact: true })).toBeVisible();
  await page.getByPlaceholder("예상과 달랐던 점").fill("출퇴근에 유용했지만 착용감은 더 확인할 필요가 있었음 (가상 회고)");
  await page.getByRole("button", { name: "회고 저장", exact: true }).click();
  await expect(page.getByText(/만족도 4.*출퇴근에 유용/)).toBeVisible();
  await capture("reflection/06-outcome", "선택한 완료 기록의 사용 후 회고");
  await page.getByRole("button", { name: "기록 수정", exact: true }).click();
  await expect(page.getByText("기록 수정 중", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await capture("completion/07-correction", "원본 완료 기록을 보존하는 정정 · 보류 비활성");
  await page.getByLabel("결정 이유", { exact: true }).fill("이미 가진 제품으로 충분하므로 구매하지 않기로 정정");
  await page.getByRole("button", { name: "구매하지 않음으로 완료", exact: true }).click();
  await expect(page.getByRole("heading", { name: "결정 후 회고", exact: true })).toBeVisible();
  await capture("completion/08-not-purchased", "구매하지 않음도 결정 완료 · 결정에 대한 회고");

  // 선택적 점수 평가 경로는 표시 사양 비교와 구분해 별도 draft로 촬영한다.
  await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
    intentText: "점수 평가 예시 (가상제품)", templateKey: "EARBUDS", webConsent: false,
    selectedSources: [{ name: "가상 Alpha", url: "https://example.com/alpha" }, { name: "가상 Beta", url: "https://example.com/beta" }],
  } });
  await page.goto(`/decisions/${decision.id}`);
  await page.getByRole("button", { name: "2. 비교·평가", exact: true }).click();
  await page.getByText("비교 값 확인과 평가", { exact: true }).click();
  for (const name of ["가상 Alpha", "가상 Beta"]) {
    await page.getByLabel(`${name} 가격`, { exact: true }).fill(name.endsWith("Alpha") ? "100000" : "140000");
    for (const criterion of ["음질", "착용감", "노이즈 캔슬링", "배터리 적합도"]) {
      await page.getByLabel(`${name} ${criterion}`, { exact: true }).selectOption("4");
    }
  }
  await page.getByRole("button", { name: "검증 입력 저장", exact: true }).click();
  await expect(page.getByRole("button", { name: "검증된 평가 실행", exact: true })).toBeEnabled();
  await capture("comparison/13-evaluation-input", "사양과 별개인 예상 만족도·가격 평가 입력");
  await page.getByRole("button", { name: "검증된 평가 실행", exact: true }).click();
  await expect(page.getByRole("heading", { name: "평가 결과", exact: true })).toBeVisible();
  await capture("comparison/14-evaluation-result", "고정 입력에 대한 평가 결과 · mock 점수는 알고리즘 검증 자료가 아님");

  // 관리 화면의 목록은 기존 전용 reset fixture를 사용한다.
  await request.post("http://127.0.0.1:18080/__test/decision-list", { data: {} });
  await page.goto("/?open=DRAFT,CHOSEN");
  await expect(page.getByRole("region", { name: "결정 완료", exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole("region", { name: "비교 중", exact: true }).getByRole("listitem")).toHaveCount(10);
  await expect(page.getByRole("region", { name: "결정 완료", exact: true }).getByRole("listitem")).toHaveCount(3);
  await capture("decision-list/09-list", "진행·완료 그룹과 소유자 목록 관리");
  const completed = page.getByRole("region", { name: "결정 완료", exact: true });
  page.on("dialog", dialog => dialog.accept());
  await completed.getByRole("button", { name: "보관하기", exact: true }).first().click();
  await page.getByRole("button", { name: "보관함", exact: true }).click();
  const management = page.getByRole("region", { name: "결정 보관과 삭제 관리" });
  await expect(management.getByRole("link")).toHaveCount(1);
  await capture("decision-list/10-archive", "완료 상태·개인화 활용을 유지하는 보관함");
  await management.getByRole("button", { name: "삭제하기", exact: true }).click();
  await page.getByRole("button", { name: "휴지통", exact: true }).click();
  await expect(management.getByText("복구 기한:", { exact: false })).toBeVisible();
  await capture("decision-list/11-trash", "30일 복구 유예와 개인화 활용 제외 안내");

  // 기존 mock에 없는 성향·알림 설정 API는 고정 가상 응답이다. 실제 성향 생성 검증은 아니다.
  await page.route("**/api/v1/preferences", route => route.fulfill({ json: [{
    id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", templateKey: "EARBUDS", criterionName: "착용감",
    statement: "다음 이어폰 비교에서는 착용감을 더 엄격히 확인합니다. (문서용 가상 성향)",
    strength: 2, status: "PROPOSED", confidence: "LOW", sourceDecisionId: decision.id,
  }] }));
  await page.route("**/api/v1/notification-preferences", route => route.fulfill({ json: { emailEnabled: true } }));
  await page.goto("/preferences");
  await expect(page.getByRole("button", { name: "다음 결정에 적용", exact: true })).toBeVisible();
  await capture("reflection/12-preferences", "성향 제안의 명시적 확인과 회고 email 설정");

  // 모든 장면이 성공한 뒤에만 문서 자산을 갱신한다. 실패 시 기존 모음을 유지한다.
  await cp(directory, path.join(root, "docs/ui/screenshots"), { recursive: true });
  await writeFile(path.join(root, "docs/ui/capture-manifest.json"), JSON.stringify({
    capturedAt: new Date().toISOString(),
    applicationCommit: execFileSync("git", ["-C", root, "rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
    data: "기존 E2E mock backend의 가상 데이터. 성향 API는 capture script의 고정 응답.",
    command: "npm.cmd --prefix frontend run test:e2e -- --config playwright.docs.config.ts",
    browser: "chromium", locale: "ko-KR", timezone: "Asia/Seoul", fullPage: true,
    hidden: "Next.js 개발 도구 overlay만 숨김. 제품 화면 내용은 그대로 촬영.",
    images,
  }, null, 2) + "\n", "utf8");
});
