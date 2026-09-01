import { expect, test } from "@playwright/test";

test("creates a provisional decision document from one sentence and one link", async ({ page }) => {
  await page.goto("/decisions/new");
  await page.getByLabel("어떤 상황에서 무엇을 고르고 있나요?").fill("휴대할 개발용 노트북을 찾고 있어요");
  await page.getByLabel("비교 중인 후보 링크").fill("https://example.com/laptop");
  await page.getByText("공개 링크와 제품 검색어").click();
  await page.getByRole("button", { name: "비교 시작하기" }).click();

  await expect(page).toHaveURL(new RegExp(`/decisions/${decisionIdPattern()}`));
  await expect(page.getByRole("heading", { name: "휴대할 개발용 노트북을 찾고 있어요" })).toBeVisible();
  await expect(page.getByText("잠정 비교").first()).toBeVisible();
  await expect(page.getByText("이번 구매의 최대 예산은 얼마인가요?")).toBeVisible();
  await expect(page.getByText("이번 결정에서 가장 중요한 기준은 무엇인가요?")).toBeVisible();
  await expect(page.getByText("반드시 피하고 싶은 단점이나 조건이 있나요?")).toBeVisible();
});

function decisionIdPattern() { return "11111111-1111-1111-1111-111111111111"; }
