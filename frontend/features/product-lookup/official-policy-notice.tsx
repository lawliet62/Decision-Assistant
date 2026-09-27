export function OfficialPolicyNotice({ version, placement }: { version?: string; placement: "home" | "input" | "result" }) {
  if (version !== "official-kr-v1") return null;
  return <aside aria-label="공식 판매처 추천 기준" className="space-y-2 rounded-2xl bg-emerald-50 p-4 text-sm text-emerald-950">
    {placement === "home" && <p className="font-semibold">공식 판매처 기준으로 추천합니다.</p>}
    {placement === "result" && <p className="font-semibold">공식 판매처 기준</p>}
    <p>{placement === "home" ? "국내·한국어 공식 판매처에서 제품과 가격을 확인해 추천합니다. 다른 판매처나 할인 경로에서는 더 저렴하게 구매할 수 있습니다."
      : placement === "input" ? "공식 판매처에서 확인한 제품·가격을 기준으로 추천합니다. 다른 판매 경로가 더 저렴할 수 있습니다."
      : "조건 충족 제품을 확인한 판매가 낮은 순으로 정렬했습니다. 다른 판매처나 할인 경로에서는 더 저렴하게 구매할 수 있습니다."}</p>
  </aside>;
}
