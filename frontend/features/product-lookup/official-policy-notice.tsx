export function OfficialPolicyNotice({ version, placement }: { version?: string; placement: "home" | "input" | "result" }) {
  if (version === "catalog-v1") return <aside aria-label="등록 제품 추천 기준" className="space-y-2 rounded-2xl bg-emerald-50 p-4 text-sm text-emerald-950">
    <p className="font-semibold">공식 근거를 확인한 소규모 이어폰 목록에서 추천합니다.</p>
    <p>필수 조건과 72시간 안에 확인한 판매가를 기준으로 비교합니다. 전체 시장·최저가를 보장하지 않으며 구매 전 판매처에서 가격과 재고를 확인하세요.</p>
  </aside>;
  if (version !== "official-kr-v1" && version !== "official-kr-v2") return null;
  return <aside aria-label="공식 판매처 추천 기준" className="space-y-2 rounded-2xl bg-emerald-50 p-4 text-sm text-emerald-950">
    {placement === "home" && <p className="font-semibold">공식 판매처 기준으로 추천합니다.</p>}
    {placement === "result" && <p className="font-semibold">공식 판매처 기준</p>}
    {placement === "result" && version === "official-kr-v1" && <p>이전 기준으로 수집한 결과입니다. 기본 호환성과 본문 가격 조건을 새 기준으로 재검증하지 않았습니다.</p>}
    <p>{placement === "home" ? "국내·한국어 공식 판매처에서 제품과 가격을 확인해 추천합니다. 다른 판매처나 할인 경로에서는 더 저렴하게 구매할 수 있습니다."
      : placement === "input" ? "공식 판매처에서 확인한 제품·가격을 기준으로 추천합니다. 다른 판매 경로가 더 저렴할 수 있습니다."
      : "조건 충족 제품을 확인한 판매가 낮은 순으로 정렬했습니다. 다른 판매처나 할인 경로에서는 더 저렴하게 구매할 수 있습니다."}</p>
  </aside>;
}
