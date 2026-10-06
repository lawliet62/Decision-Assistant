export function OfficialPolicyNotice({ version, placement }: { version?: string; placement: "home" | "input" | "result" }) {
  if (version !== "official-kr-v1" && version !== "official-kr-v2" && version !== "official-kr-v3") return null;
  return <aside aria-label="공식 판매처 추천 기준" className="space-y-2 rounded-2xl bg-emerald-50 p-4 text-sm text-emerald-950">
    {placement === "home" && <p className="font-semibold">공식 판매처 기준으로 추천합니다.</p>}
    {placement === "result" && <p className="font-semibold">공식 판매처 기준</p>}
    {placement === "result" && version === "official-kr-v1" && <p>이전 기준으로 수집한 결과입니다. 기본 호환성과 본문 가격 조건을 새 기준으로 재검증하지 않았습니다.</p>}
    {version === "official-kr-v3" && <p>{placement === "result" ? "공식 문서로 사양 보완을 요청한 결과입니다. 출처가 부족하거나 상충하면 확인 필요로 표시합니다." : "동의하면 동일 모델의 공식 제품·지원 문서에서 부족한 사양을 보완합니다. 해외 문서가 포함될 수 있으며 가격은 국내 판매 페이지에서 확인합니다."}</p>}
    <p>{placement === "home" ? "국내·한국어 공식 판매처에서 제품과 가격을 확인해 추천합니다. 다른 판매처나 할인 경로에서는 더 저렴하게 구매할 수 있습니다."
      : placement === "input" ? "공식 판매처에서 확인한 제품·가격을 기준으로 추천합니다. 다른 판매 경로가 더 저렴할 수 있습니다."
      : "조건 충족 제품을 확인한 판매가 낮은 순으로 정렬했습니다. 다른 판매처나 할인 경로에서는 더 저렴하게 구매할 수 있습니다."}</p>
  </aside>;
}
