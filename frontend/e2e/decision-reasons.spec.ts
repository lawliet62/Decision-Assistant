import { test, expect } from "@playwright/test";

for (const scenario of [
  { kind: "CHOSEN", label: "제품 선택", reason: "budget", reasonLabel: "예산에 적합함" },
  { kind: "DEFERRED", label: "보류", reason: "information", reasonLabel: "추가 정보가 필요함" },
  { kind: "ABANDONED", label: "포기", reason: "no_match", reasonLabel: "조건에 맞는 제품이 없음" },
]) {
  test(`${scenario.label}: 제목만 정정해도 기존 판단과 모든 이유를 유지한다`, async ({page,request}) => {
    const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", { data: {
      intentText: "이유를 보존할 결정", templateKey: "EARBUDS", selectedSources: [{ name: "Alpha", url: "https://example.com/alpha" }],
    } });
    const decision = await response.json();
    const selectedOptionId = scenario.kind === "CHOSEN" ? decision.options[0].id : undefined;
    await request.post(`http://127.0.0.1:18080/api/v1/decisions/${decision.id}/resolutions`, { data: {
      kind: scenario.kind, selectedOptionId, reasonIds: [scenario.reason], primaryReasonId: scenario.reason, rationale: "정정 전의 구체적인 판단 근거",
    } });
    await page.goto(`/decisions/${decision.id}`);
    await page.getByLabel("아직 구매하지 않아 다시 검토합니다").check();
    await page.getByRole("button", { name: "기록 수정", exact: true }).click();
    await page.getByLabel("제목", { exact: true }).fill("제목만 정정했습니다");
    await page.getByRole("button", { name: "변경 저장", exact: true }).click();
    await expect(page.getByRole("heading", { name: "제목만 정정했습니다", exact: true })).toBeVisible();
    await page.reload();
    await page.getByRole("button", { name: "3. 선택 기록", exact: true }).click();
    await expect(page.getByRole("button", { name: scenario.label, exact: true })).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByRole("button", { name: scenario.reasonLabel, exact: true })).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByLabel("가장 중요한 이유")).toHaveValue(scenario.reason);
    await expect(page.getByLabel("결정 이유", { exact: true })).toHaveValue("정정 전의 구체적인 판단 근거");
    if (selectedOptionId) await expect(page.getByLabel("실제로 선택한 후보")).toHaveValue(selectedOptionId);
    const sent = page.waitForRequest(r => r.method() === "POST" && r.url().endsWith("/resolutions"));
    await page.getByRole("button", { name: "결정 완료", exact: true }).click();
    expect((await sent).postDataJSON()).toMatchObject({kind: scenario.kind, reasonIds: [scenario.reason], primaryReasonId: scenario.reason, rationale: "정정 전의 구체적인 판단 근거"});
    await expect(page.getByRole("heading", { name: "결정을 완료했습니다" })).toBeVisible();
  });
}

test("이유 GUI·메모·구매 전 재검토 취소·구매 기록", async ({page,request}) => {
  const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", {data:{intentText:"새 결정 기록 검증",templateKey:"EARBUDS",selectedSources:[{name:"Alpha",url:"https://example.com/alpha"}]}});
  const decision = await response.json(); await page.goto(`/decisions/${decision.id}`);
  await page.getByRole("button",{name:"3. 선택 기록",exact:true}).click();
  await page.getByLabel("실제로 선택한 후보").selectOption({label:"Alpha"});
  await page.getByRole("button",{name:"예산에 적합함",exact:true}).click();
  await page.getByLabel("가장 중요한 이유").selectOption("budget");
  await page.getByLabel("결정 이유",{exact:true}).fill("지금은 선택만 하고 구매는 다음 달");
  await page.getByRole("button",{name:"2. 비교·평가",exact:true}).click();
  await page.getByRole("button",{name:"3. 선택 기록",exact:true}).click();
  await expect(page.getByLabel("결정 이유",{exact:true})).toHaveValue("지금은 선택만 하고 구매는 다음 달");
  await page.screenshot({path:test.info().outputPath("reason-picker.png"),fullPage:true});
  await page.getByRole("button",{name:"결정 완료",exact:true}).click();
  await expect(page.getByRole("heading",{name:"결정을 완료했습니다"})).toBeVisible();
  await expect(page.getByText("구매 상태: 미기록")).toBeVisible();
  await expect(page.getByRole("button",{name:"다시 검토",exact:true})).toBeDisabled();
  await page.getByLabel("아직 구매하지 않아 다시 검토합니다").check();
  await page.getByRole("button",{name:"다시 검토",exact:true}).click();
  await page.getByRole("button",{name:"재검토 취소 · 이전 결론 유지"}).click();
  await expect(page.getByRole("heading",{name:"결정을 완료했습니다"})).toBeVisible();
  await page.getByRole("button",{name:"구매 예정",exact:true}).click();
  await expect(page.getByText("구매 상태: 구매 예정")).toBeVisible();
  await page.getByRole("button",{name:"구매함",exact:true}).click();
  await expect(page.getByText("구매 상태: 구매함")).toBeVisible();
  await expect(page.getByRole("button",{name:"다시 검토",exact:true})).toHaveCount(0);
  await page.reload(); await expect(page.getByText("구매 상태: 구매함")).toBeVisible();
  await page.getByText("지금은 선택만 하고 구매는 다음 달",{exact:false}).first().click();
  await expect(page.getByText("예산에 적합함 · 가장 중요한 이유")).toBeVisible();
  await page.screenshot({path:test.info().outputPath("decision-completed.png"),fullPage:true});
});

test("과거 이유와 회고를 원문으로 보고 이번 메모에 선택 적용한다", async ({page,request}) => {
  const response = await request.post("http://127.0.0.1:18080/api/v1/decisions", {data:{intentText:"과거 기록 참고",templateKey:"EARBUDS",selectedSources:[{name:"Alpha",url:"https://example.com/alpha"}]}});
  const decision = await response.json();
  await page.route("**/api/v1/decisions/*/memories", r => r.fulfill({json:[{decisionId:"past-decision",resolutionId:"past-resolution",title:"과거 이어폰",kind:"CHOSEN",selectedName:"가상 제품",reasonIds:["budget"],primaryReasonId:"budget",rationale:"가벼워서 선택",decidedAt:"2026-11-01T00:00:00Z",outcomes:[{id:"outcome",notes:"배터리는 아쉬웠음",satisfaction:3}]}]}));
  await page.goto(`/decisions/${decision.id}`);
  await expect(page.getByText("당시 메모: 가벼워서 선택")).toBeVisible();
  await expect(page.getByText("이후 회고:",{exact:false})).toContainText("배터리는 아쉬웠음");
  await expect(page.getByRole("link",{name:"당시 기록 보기"})).toHaveAttribute("href","/decisions/past-decision");
  await page.getByRole("button",{name:"이번에도 참고"}).click();
  await expect(page.getByLabel("메모",{exact:true})).toHaveValue(/past-decision\/past-resolution/);
  await page.getByRole("button",{name:"변경 저장",exact:true}).click();
  await expect(page.getByText("검증 입력을 저장했습니다.")).toBeVisible();
  await page.reload(); await expect(page.getByLabel("메모",{exact:true})).toHaveValue(/가벼워서 선택/);
});
