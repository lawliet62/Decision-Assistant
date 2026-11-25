import { expect, test, type Page } from "@playwright/test";

// 결정 완료 화면의 "내 결정으로 돌아가기" 버튼과 다음 정보 사이 간격을 검증한다(#83).
const viewports = [
  { name: "desktop", size: null },
  { name: "mobile", size: { width: 390, height: 844 } },
] as const;

const scenarios = [
  { kind: "CHOSEN", next: (page: Page) => page.getByText("구매 상태: 미기록", { exact: true }) },
  { kind: "DEFERRED", next: (page: Page) => page.getByText("아직 구매하지 않아 다시 검토합니다", { exact: true }) },
  { kind: "ABANDONED", next: (page: Page) => page.getByText("아직 구매하지 않아 다시 검토합니다", { exact: true }) },
] as const;

for (const scenario of scenarios) {
  for (const viewport of viewports) {
    test(`${scenario.kind} 완료 화면(${viewport.name})은 돌아가기 버튼 아래에 section 간격을 둔다`, async ({ page, request }) => {
      if (viewport.size) await page.setViewportSize(viewport.size);
      const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
        intentText: "완료 화면 간격 검증", templateKey: "EARBUDS", selectedSources: [{ name: "Alpha", url: "https://example.com/alpha" }],
      } });
      const decision = await response.json();
      await request.post(`http://127.0.0.1:18080/api/v1/decisions/${decision.id}/resolutions`, { data: {
        kind: scenario.kind, selectedOptionId: scenario.kind === "CHOSEN" ? decision.options[0].id : undefined,
        reasonIds: ["budget"], primaryReasonId: "budget", rationale: "간격 검증용 판단 근거",
      } });
      await page.goto(`/decisions/${decision.id}`);
      await expect(page.getByRole("heading", { name: "결정을 완료했습니다" })).toBeVisible();

      const link = page.getByRole("link", { name: "내 결정으로 돌아가기", exact: true });
      const next = scenario.next(page);
      await expect(next).toBeVisible();
      // 기대 간격은 section이 제목 블록 등 다른 자식에 주는 margin-block-end에서 읽는다.
      const expectedGap = await page.getByRole("heading", { name: "결정을 완료했습니다" })
        .evaluate((heading) => parseFloat(getComputedStyle(heading.parentElement!).marginBlockEnd));
      expect(expectedGap).toBeGreaterThan(0);
      const linkBox = (await link.boundingBox())!;
      const nextBox = (await next.boundingBox())!;
      const gap = nextBox.y - (linkBox.y + linkBox.height);
      expect(gap, "버튼과 다음 정보가 겹치지 않아야 한다").toBeGreaterThanOrEqual(0);
      expect(Math.abs(gap - expectedGap), `간격 ${gap}px가 section 간격 ${expectedGap}px와 같아야 한다`).toBeLessThanOrEqual(1);
    });
  }
}
