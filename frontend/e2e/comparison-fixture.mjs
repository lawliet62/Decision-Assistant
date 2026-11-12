// 브라우저 동작 전용 가상 제품. 판정 규칙은 Java 단위·API 통합 테스트에서 검증한다.
export function comparisonFixture(current) {
  current.comparison = { settings: { maxBudget: 100000, ancRequired: true, compatibility: "iOS", focus: "가격", deferredOptionIds: [] }, confirmations: [] };
  current.productFacts = current.options.map((option, index) => ({ optionId: option.id, facts: [{ name: "배터리", value: index ? "최대 8시간 (ANC 끔)" : "최대 6시간 (ANC 켬)", quote: "가상 테스트 근거", verificationStatus: "UNREVIEWED" }], sourceKind: "WEB" }));
  return recalculate(current);
}
export function recalculate(current) {
  const settings = current.comparison.settings;
  current.comparison.options = current.options.map((option, index) => {
    const checks = [];
    if (settings.ancRequired) checks.push({ id: "anc", label: "ANC 지원", requiredValue: "지원 필수", status: index === 0 ? "BELOW" : "MEETS", facts: [{ name: "ANC 지원", value: index === 0 ? "미지원" : "지원", sourceUrl: option.sourceUrl, quote: "가상 ANC 근거", verificationStatus: "UNREVIEWED" }] });
    if (settings.compatibility) checks.push({ id: "compatibility", label: "기본 연결·사용 호환성", requiredValue: settings.compatibility, status: "UNKNOWN", facts: [], unknownReason: "MISSING" });
    for (const check of checks) {
      const feature = check.id === "anc" ? "anc" : settings.compatibility;
      const confirmation = current.comparison.confirmations.find(item => item.optionId === option.id && item.feature === feature);
      if (confirmation) { check.status = confirmation.supported ? "MEETS" : "BELOW"; check.facts = [{ name: check.label, value: confirmation.supported ? "지원" : "미지원", quote: `사용자 직접 확인: ${confirmation.note}`, verificationStatus: "CONFIRMED", sourceUrl: confirmation.sourceUrl, fetchedAt: confirmation.confirmedAt }]; delete check.unknownReason; }
    }
    const price = index === 0 ? 50000 : 90000;
    if (settings.maxBudget != null) checks.push({ id: "budget", label: "최대 예산", requiredValue: `${settings.maxBudget}원`, status: price <= settings.maxBudget ? "MEETS" : "BELOW", facts: [{ name: "가격", value: `${price} KRW`, quote: "가상 가격", verificationStatus: "UNREVIEWED" }] });
    return { optionId: option.id, price, priceOrigin: "수집 가격", checks };
  });
  return current;
}
