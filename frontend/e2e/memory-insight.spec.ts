import { test, expect } from "@playwright/test";

const source = { decisionId: "past-decision", resolutionId: "past-resolution", title: "과거 이어폰", kind: "CHOSEN", selectedName: "가상 버즈", reasonIds: ["compatibility"], primaryReasonId: "compatibility", rationale: "휴대폰 연동이 필요했음", decidedAt: "2026-11-01T00:00:00Z", purchaseStatus: "UNRECORDED", outcomes: [] };
const result = { sourceDecisionId: source.decisionId, resolutionId: source.resolutionId, provider: "fixture", model: "fixture-model", promptVersion: "contextual-memory-v1", createdAt: "2026-11-17T00:00:00Z", insight: {
  summary: "휴대폰 연동을 이유로 선택했으며 구매는 미기록입니다.", relevance: "이번에도 같은 연동이 필요하다면 참고할 수 있습니다.", conditions: ["현재 휴대폰과 필요한 연동 기능을 확인하세요."], uncertainty: "현재 후보의 호환성은 별도 확인이 필요합니다.",
} };

for (const mode of ["generate", "edit", "failure"] as const) {
  test(`과거 이유 AI 해석: ${mode}`, async ({ page, request }) => {
    const created = await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: { intentText: "조건에 따른 기록 활용", templateKey: "EARBUDS", selectedSources: [{ name: "Alpha", url: "https://example.com/alpha" }] } });
    const decision = await created.json();
    let calls = 0;
    await page.route("**/api/v1/decisions/*/memories", route => route.fulfill({ json: [source] }));
    await page.route("**/api/v1/decisions/*/memories/*/insight", route => {
      if (route.request().method() === "GET") return route.fulfill({ json: { available: true, provider: "fixture", model: "fixture-model", dataNotice: "가상 provider 고지", input: { currentTitle: decision.title, source }, inputHash: "fixture-hash", saved: mode === "edit" ? result : null } });
      calls++;
      expect(route.request().postDataJSON()).toEqual({ aiConsent: true, sensitiveDataAcknowledged: true, expectedInputHash: "fixture-hash" });
      return mode === "failure" ? route.fulfill({ status: 422, json: { detail: "fixture failure" } }) : route.fulfill({ json: result });
    });
    await page.goto(`/decisions/${decision.id}`);
    await expect(page.getByText("당시 메모: 휴대폰 연동이 필요했음")).toBeVisible();
    expect(calls).toBe(0);
    if (mode !== "edit") {
      await expect(page.getByRole("button", { name: "요약과 관련 이유 생성", exact: true })).toBeDisabled();
      await page.getByText("AI에 보낼 내용 보기", { exact: true }).click();
      await expect(page.getByText("가상 provider 고지")).toBeVisible();
      await page.getByRole("checkbox", { name: "표시된 과거 기록과 현재 조건의 AI 전송에 동의하며, 민감정보가 없음을 확인합니다." }).check();
      await page.getByRole("button", { name: "요약과 관련 이유 생성", exact: true }).click();
    }
    if (mode === "failure") {
      await expect(page.getByRole("alert").filter({ hasText: "요약을 생성하지 못했습니다" })).toBeVisible();
      await expect(page.getByText("당시 메모: 휴대폰 연동이 필요했음")).toBeVisible();
      expect(calls).toBe(1);
      return;
    }
    await expect(page.getByText(result.insight.summary, { exact: false })).toBeVisible();
    await expect(page.getByText(result.insight.uncertainty, { exact: false })).toBeVisible();
    await expect(page.getByRole("link", { name: "당시 기록 보기" })).toHaveAttribute("href", "/decisions/past-decision");
    await page.screenshot({ path: test.info().outputPath(`memory-${mode}.png`), fullPage: true });
    if (mode === "edit") {
      await page.getByRole("button", { name: "해석 수정", exact: true }).click();
      await page.getByLabel("과거 이유 해석 수정").fill("이번에는 노트북 연결을 확인하고 싶음");
      await page.getByRole("button", { name: "수정한 내용 참고", exact: true }).click();
    } else await page.getByRole("button", { name: "이번에도 중요함", exact: true }).click();
    await expect(page.getByLabel("메모", { exact: true })).toHaveValue(/past-decision\/past-resolution/);
    await page.getByRole("button", { name: "변경 저장", exact: true }).click();
    await expect(page.getByText("검증 입력을 저장했습니다.")).toBeVisible();
    await page.reload();
    await expect(page.getByLabel("메모", { exact: true })).toHaveValue(mode === "edit" ? /이번에는 노트북 연결/ : /AI 해석을 이번 결정에서 확인함/);
    expect(calls).toBe(mode === "edit" ? 0 : 1);
  });
}
