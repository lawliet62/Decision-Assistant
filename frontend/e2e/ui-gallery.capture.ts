import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { cp, mkdir, readFile, writeFile } from "node:fs/promises";
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
      // 보관·삭제는 관리 패널과 별도로 기본 목록도 다시 로딩한다.
      if (name.startsWith("decision-list/")) {
        await expect(page.getByRole("region", { name: "비교 중", exact: true }).getByRole("listitem")).toHaveCount(10);
        await expect(page.getByRole("region", { name: "결정 완료", exact: true }).getByRole("listitem"))
          .toHaveCount(name.includes("09-list") ? 4 : 3);
      }
      await page.waitForLoadState("networkidle");
      await page.evaluate(() => document.fonts.ready);
      await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
      await page.evaluate(() => window.scrollTo(0, 0));
      const file = `${name}-${device}.png`;
      await mkdir(path.dirname(path.join(directory, file)), { recursive: true });
      const screenshot = await page.screenshot({ path: path.join(directory, file), fullPage: true, animations: "disabled",
        style: "nextjs-portal { visibility: hidden; }" });
      // screenshot 도중 페이지가 커져 하단이 잘린 이미지는 문서에 반영하지 않는다.
      const renderedHeight = await page.evaluate(() => Math.max(document.documentElement.scrollHeight, document.body.scrollHeight));
      expect(screenshot.readUInt32BE(20)).toBeGreaterThanOrEqual(renderedHeight);
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
  // 원문 모음의 AI 비활성 상태도 명시한다. 뒤의 전용 AI 장면에서만 가상 응답으로 교체한다.
  await page.route("**/api/v1/decisions/*/memories/*/insight", route => route.fulfill({ json: { available: false } }));
  // 일반 E2E mock은 결과별 이유를 1개로 축약한다. 문서는 현재 backend 템플릿 전체를 표시한다.
  const reasonSource = await readFile(path.join(root, "backend/src/main/java/com/decisionassistant/decision/DecisionReasons.java"), "utf8");
  const reasons = [...reasonSource.matchAll(/new Reason\("([^"]+)", "([^"]+)", DecisionTypes\.ResolutionKind\.(\w+)\)/g)]
    .map(([, id, label, kind]) => ({ id, label, kind }));
  expect(reasons.length).toBeGreaterThan(0);
  await page.route("**/api/v1/decisions/reason-templates", route => route.fulfill({ json: reasons }));
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
  await page.getByRole("button", { name: "예산에 적합함", exact: true }).click();
  await page.getByRole("button", { name: "필요한 기능을 충족함", exact: true }).click();
  await page.getByLabel("가장 중요한 이유").selectOption("budget");
  await page.getByLabel("결정 이유", { exact: true }).fill("추가 비용을 감수하고 ANC를 선택, iOS 연결은 직접 확인");
  await capture("completion/05-resolution", "제품 선택·보류·포기와 복수 이유·대표 이유·자유 메모");
  await page.getByRole("button", { name: "결정 완료", exact: true }).click();
  await expect(page.getByRole("heading", { name: "결정을 완료했습니다", exact: true })).toBeVisible();
  await expect(page.getByText("구매 상태: 미기록", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "다시 검토", exact: true })).toBeDisabled();
  await capture("completion/15-completed", "필수 흐름 종료 · 구매 미기록과 선택적 구매·회고");
  await page.getByRole("button", { name: "선택 사항 · 회고", exact: true }).click();
  await expect(page.getByRole("heading", { name: "사용 후 회고", exact: true })).toBeVisible();
  await page.getByPlaceholder("예상과 달랐던 점").fill("출퇴근에 유용했지만 착용감은 더 확인할 필요가 있었음 (가상 회고)");
  await page.getByRole("button", { name: "회고 저장", exact: true }).click();
  await expect(page.getByText(/만족도 4.*출퇴근에 유용/)).toBeVisible();
  await capture("reflection/06-outcome", "선택한 완료 기록의 사용 후 회고");
  await page.getByLabel("아직 구매하지 않아 다시 검토합니다").check();
  await page.getByRole("button", { name: "기록 수정", exact: true }).click();
  await expect(page.getByText("기록 수정 중", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await expect(page.getByLabel("가장 중요한 이유")).toHaveValue("budget");
  await expect(page.getByRole("button", { name: "보류", exact: true })).toBeEnabled();
  await capture("completion/07-correction", "미구매 확인 후 원본 결과·이유를 초기값으로 정정 · 보류도 허용");
  await page.getByRole("button", { name: "포기", exact: true }).click();
  await page.getByRole("button", { name: "조건에 맞는 제품이 없음", exact: true }).click();
  await page.getByLabel("결정 이유", { exact: true }).fill("이미 가진 제품으로 충분하므로 구매하지 않기로 정정");
  await page.getByRole("button", { name: "결정 완료", exact: true }).click();
  await page.getByRole("button", { name: "선택 사항 · 회고", exact: true }).click();
  await expect(page.getByRole("heading", { name: "결정 후 회고", exact: true })).toBeVisible();
  await capture("completion/08-not-purchased", "포기로 완료 · 기존 NOT_PURCHASED 결과와 선택적인 결정 후 회고");

  await page.getByLabel("아직 구매하지 않아 다시 검토합니다").check();
  await page.getByRole("button", { name: "다시 검토", exact: true }).click();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await page.getByRole("button", { name: "보류", exact: true }).click();
  await page.getByRole("button", { name: "추가 정보가 필요함", exact: true }).click();
  await page.getByRole("button", { name: "결정 완료", exact: true }).click();
  await expect(page.getByText("결정 완료 · 보류", { exact: false })).toBeVisible();
  await capture("completion/16-deferred", "보류도 완료 · 직접 편집 대신 미구매 확인 후 재검토");
  await page.getByLabel("아직 구매하지 않아 다시 검토합니다").check();
  await page.getByRole("button", { name: "다시 검토", exact: true }).click();
  await page.getByLabel("제목", { exact: true }).fill("취소로 되돌릴 임시 검토 제목");
  await page.getByRole("button", { name: "변경 저장", exact: true }).click();
  await expect(page.getByRole("heading", { name: "취소로 되돌릴 임시 검토 제목", exact: true })).toBeVisible();
  await capture("completion/17-reconsideration", "이전 결론·근거를 보존하며 새 판단 준비 · 취소 가능");
  await page.getByRole("button", { name: "재검토 취소 · 이전 결론 유지", exact: true }).click();
  await expect(page.getByRole("heading", { name: "출퇴근용 이어폰 비교 (가상제품)", exact: true })).toBeVisible();
  await expect(page.getByText("결정 완료 · 보류", { exact: false })).toBeVisible();
  await capture("completion/18-restored", "취소 후 이전 제목·보류 결론 복원 · 제품 코드 복원 범위는 backend 검증 참조");
  await page.getByLabel("아직 구매하지 않아 다시 검토합니다").check();
  await page.getByRole("button", { name: "다시 검토", exact: true }).click();
  await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
  await page.getByRole("button", { name: "제품 선택", exact: true }).click();
  await page.getByLabel("실제로 선택한 후보").selectOption({ label: "가상 Beta" });
  await page.getByRole("button", { name: "결정 완료", exact: true }).click();
  await page.getByRole("button", { name: "구매 예정", exact: true }).click();
  await expect(page.getByText("구매 상태: 구매 예정", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "구매함", exact: true }).click();
  await expect(page.getByText("구매 상태: 구매함", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "다시 검토", exact: true })).toHaveCount(0);
  await capture("completion/19-purchased", "선택과 별도인 구매함 기록 · 구매 후 재검토 차단 UI");

  // 선택적 점수 평가 경로는 표시 사양 비교와 구분해 별도 draft로 촬영한다.
  await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
    intentText: "점수 평가 예시 (가상제품)", templateKey: "EARBUDS", webConsent: false,
    selectedSources: [{ name: "가상 Alpha", url: "https://example.com/alpha" }, { name: "가상 Beta", url: "https://example.com/beta" }],
  } });
  // 원문 참고는 성향 projection과 구분한 고정 조회 fixture다. 실제 접근·필터 검증은 아니다.
  await page.route("**/api/v1/decisions/*/memories", route => route.fulfill({ json: [{
    decisionId: "77777777-7777-7777-7777-777777777777", resolutionId: "88888888-8888-8888-8888-888888888888",
    title: "과거 이어폰 선택 (가상 기록)", kind: "CHOSEN", selectedName: "가상 이전 제품",
    reasonIds: ["budget"], primaryReasonId: "budget", rationale: "예산을 우선해 선택했지만 착용감도 다시 살펴볼 필요가 있었음",
    decidedAt: "2026-11-01T00:00:00Z", outcomes: [{ id: "past-outcome", notes: "오래 착용할 때 귀가 불편했음 (가상 회고)", satisfaction: 3 }],
  }] }));
  await page.goto(`/decisions/${decision.id}`);
  await expect(page.getByRole("button", { name: "이번에도 참고", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "이번에도 참고", exact: true }).click();
  await expect(page.getByLabel("메모", { exact: true })).toHaveValue(/과거 기록 참고/);
  await capture("reflection/20-history", "과거 이유·회고 원문과 출처 링크 · 명시적으로 현재 메모에 참고");
  await page.getByRole("button", { name: "변경 저장", exact: true }).click();
  await page.unroute("**/api/v1/decisions/*/memories");
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
  await expect(page.getByRole("region", { name: "결정 완료", exact: true }).getByRole("listitem")).toHaveCount(4);
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

  // 실제 AI 호출 없이 PR #75의 전송 확인·해석·수정·실패 UI를 재현한다.
  const aiTitle = "AI 기록 활용 예시 (가상제품)";
  await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
    intentText: aiTitle, templateKey: "EARBUDS", webConsent: false,
    selectedSources: [{ name: "가상 Alpha", url: "https://example.com/alpha" }],
  } });
  const source = {
    decisionId: "77777777-7777-7777-7777-777777777777", resolutionId: "88888888-8888-8888-8888-888888888888",
    title: "과거 이어폰 선택 (가상 기록)", kind: "CHOSEN", selectedName: "가상 이전 제품",
    reasonIds: ["budget"], primaryReasonId: "budget", rationale: "예산을 우선해 선택했지만 착용감도 다시 살펴볼 필요가 있었음",
    purchaseStatus: "UNRECORDED", decidedAt: "2026-11-01T00:00:00Z",
    context: { intendedUse: "출퇴근", budget: 100000, currency: "KRW", notes: null }, conditions: null,
    outcomes: [{ id: "past-outcome", notes: "오래 착용할 때 귀가 불편했음 (가상 회고)", satisfaction: 3 }],
  };
  const interpretation = {
    sourceDecisionId: source.decisionId, resolutionId: source.resolutionId,
    provider: "fixture", model: "fixture-model", promptVersion: "contextual-memory-v1", createdAt: "2026-11-17T00:00:00Z",
    insight: { summary: "예산을 우선해 선택했고 구매 상태는 미기록입니다. 회고에는 장시간 착용 시 불편함이 남아 있습니다.",
      relevance: "이번에도 장시간 착용한다면 예산과 함께 착용감을 직접 확인할 이유로 참고할 수 있습니다.",
      conditions: ["이번 사용 시간과 예산이 당시와 비슷한지 확인하세요.", "현재 후보의 착용감은 직접 확인하세요."],
      uncertainty: "선택 사실만으로 구매나 만족을 확정할 수 없고, 현재 후보 사양도 별도로 확인해야 합니다." },
  };
  let saved: typeof interpretation | null = null;
  let insightCalls = 0;
  let failInsight = false;
  await page.route("**/api/v1/decisions/*/memories", route => route.fulfill({ json: [source] }));
  await page.route("**/api/v1/decisions/*/memories/*/insight", route => {
    if (route.request().method() === "GET") return route.fulfill({ json: {
      available: true, provider: "fixture", model: "fixture-model", dataNotice: "문서 촬영용 가상 provider 고지 · 실제 외부 전송 없음",
      input: { currentTitle: aiTitle, currentContext: { intendedUse: aiTitle, currency: "KRW", notes: null }, currentConditions: null,
        source, reasonLabels: reasons.filter(reason => source.reasonIds.includes(reason.id)) },
      inputHash: "fixture-input-hash", saved,
    } });
    expect(route.request().postDataJSON()).toEqual({ aiConsent: true, sensitiveDataAcknowledged: true, expectedInputHash: "fixture-input-hash" });
    insightCalls++;
    if (failInsight) return route.fulfill({ status: 422, json: { detail: "문서용 가상 provider 실패" } });
    saved = interpretation;
    return route.fulfill({ json: interpretation });
  });
  await page.goto(`/decisions/${decision.id}`);
  const generate = page.getByRole("button", { name: "요약과 관련 이유 생성", exact: true });
  const consent = page.getByRole("checkbox", { name: "표시된 과거 기록과 현재 조건의 AI 전송에 동의하며, 민감정보가 없음을 확인합니다." });
  await expect(generate).toBeDisabled();
  await page.getByText("AI에 보낼 내용 보기", { exact: true }).click();
  await expect(page.getByText("문서 촬영용 가상 provider 고지 · 실제 외부 전송 없음", { exact: true })).toBeVisible();
  expect(insightCalls).toBe(0);
  await capture("reflection/21-ai-preview", "전송 입력·provider 고지·명시적 동의 확인 · 생성 전 외부 호출 없음");
  await consent.check();
  await generate.click();
  await expect(page.getByText(interpretation.insight.summary, { exact: false })).toBeVisible();
  await capture("reflection/22-ai-insight", "AI 해석의 요약·조건부 관련성·확인 조건·불확실성·원본 링크");
  await page.getByRole("button", { name: "해석 수정", exact: true }).click();
  await page.getByLabel("과거 이유 해석 수정").fill("이번에는 통근 시간이 길어졌으므로 예산 외에 장시간 착용감을 직접 확인하겠습니다.");
  await capture("reflection/23-ai-edit", "AI 해석을 사용자가 수정 · 원본 판단이나 장기 성향을 변경하지 않음");
  await page.getByRole("button", { name: "수정한 내용 참고", exact: true }).click();
  await expect(page.getByLabel("메모", { exact: true })).toHaveValue(/사용자가 수정한 해석/);
  await expect(page.getByRole("button", { name: "이번에도 중요함", exact: true })).toBeDisabled();
  await capture("reflection/24-ai-applied", "출처 포함 현재 메모에 적용 · 미저장 변경은 추가 AI 생성·적용 차단");
  await page.getByRole("button", { name: "변경 저장", exact: true }).click();
  await page.reload();
  await expect(page.getByLabel("메모", { exact: true })).toHaveValue(/사용자가 수정한 해석/);
  await expect(page.getByText(interpretation.insight.summary, { exact: false })).toBeVisible();
  expect(insightCalls).toBe(1);
  // 실패는 별도 fixture 상태다. 실제 서버의 hash 무효화나 cache를 검증하는 장면이 아니다.
  saved = null; failInsight = true;
  await page.reload();
  await consent.check();
  await generate.click();
  await expect(page.getByRole("alert").filter({ hasText: "요약을 생성하지 못했습니다" })).toBeVisible();
  await expect(page.getByText(`당시 메모: ${source.rationale}`, { exact: true })).toBeVisible();
  await capture("reflection/25-ai-failure", "AI 실패 후 원문 유지·전송 재확인 안내 · 자동 재시도 없음");

  // 모든 장면이 성공한 뒤에만 문서 자산을 갱신한다. 실패 시 기존 모음을 유지한다.
  await cp(directory, path.join(root, "docs/ui/screenshots"), { recursive: true });
  await writeFile(path.join(root, "docs/ui/capture-manifest.json"), JSON.stringify({
    capturedAt: new Date().toISOString(),
    applicationCommit: execFileSync("git", ["-C", root, "rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
    data: "기존 E2E mock backend의 가상 데이터. 이유 템플릿은 backend 소스에서 읽고 성향·알림 설정·원문 조회·AI 입력/결과/실패는 촬영용 응답. 실제 AI 호출 없음.",
    command: "npm.cmd --prefix frontend run test:e2e -- --config playwright.docs.config.ts",
    browser: "chromium", locale: "ko-KR", timezone: "Asia/Seoul", fullPage: true,
    hidden: "Next.js 개발 도구 overlay만 숨김. 제품 화면 내용은 그대로 촬영.",
    images,
  }, null, 2) + "\n", "utf8");
});
