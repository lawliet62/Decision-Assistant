import type { components } from "@/shared/api/schema";
import { FactValue, RequirementChecks } from "./product-facts";
import { ProductOffer } from "@/features/product-lookup/product-offer";
import { SourceLink } from "@/features/product-lookup/source-link";

type Candidate = components["schemas"]["CandidateSuggestionData"];

export function DiscoveryCandidates({ candidates, editable, optionCount, pending, hasRequirements, officialPolicy, onAction }: {
  candidates: Candidate[]; editable: boolean; optionCount: number; pending: boolean; hasRequirements: boolean;
  officialPolicy?: boolean;
  onAction: (id: string, action: "ACCEPT" | "DISMISS") => void;
}) {
  const isBelow = (item: Candidate) => item.recommendationGroup === "BELOW" || item.requirementChecks?.some(check => check.status === "BELOW");
  const isUnknown = (item: Candidate) => !isBelow(item) && item.recommendationGroup === "UNKNOWN";
  const legacy = officialPolicy ? candidates.filter(item => !item.officialOffer && !item.priceStatus) : [];
  const active = candidates.filter(item => !legacy.includes(item));
  const remaining = active.filter(item => !isBelow(item) && !isUnknown(item));
  const below = active.filter(isBelow);
  const unknown = candidates.filter(isUnknown);
  function card(item: Candidate) {
    return <article key={item.id} aria-label={item.name} className="space-y-3 rounded-2xl border border-zinc-200 p-4">
      <h3 className="font-semibold">{item.name}</h3><p className="text-sm text-zinc-600">{item.reason}</p>
      {item.status === "ACCEPTED" && <p className="text-xs text-emerald-800">비교에 추가됨</p>}
      {item.status === "DISMISSED" && <p className="text-xs text-zinc-500">제외한 제품</p>}
      <ProductOffer offer={item.officialOffer} name={item.name} />
      {item.priceStatus && item.priceStatus !== "VERIFIED" && <p className="text-sm text-amber-800">최근 판매가 확인이 필요합니다. 예산 충족 추천에 포함하지 않았습니다.</p>}
      {item.officialOffer?.policyVersion === "official-kr-v1" && <p className="text-xs text-amber-800">이전 검증 기준의 후보입니다. 기본 호환성은 재검증하지 않았습니다.</p>}
      {!item.officialOffer && <SourceLink url={item.url} kind={item.sourceKind} />}
      <p className="text-xs text-zinc-500">수집 정보, 사용자 확인 전</p>
      <RequirementChecks checks={item.requirementChecks} />
      {item.compatibilityCheck && <RequirementChecks checks={[item.compatibilityCheck]} label="호환성 확인" />}
      <dl className="space-y-2 text-sm">{item.facts?.filter(fact => !(item.officialOffer && fact.name === "가격") && !item.requirementChecks?.some(check => check.facts?.some(checked => checked.name === fact.name))).map((fact, index) => <div key={index}><dt className="text-zinc-500">{fact.name}</dt><dd><FactValue fact={fact} compact /></dd></div>)}</dl>
      {!item.facts?.length && !item.requirementChecks?.length && <p className="text-sm text-zinc-500">수집한 사양이 없습니다.</p>}
      {editable && <div className="flex gap-2"><button disabled={optionCount >= 5} onClick={() => onAction(item.id!, "ACCEPT")} className="rounded-full bg-emerald-800 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">비교에 추가</button><button onClick={() => onAction(item.id!, "DISMISS")} className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-medium">제외</button></div>}
    </article>;
  }
  return <div className="space-y-4">
    {!pending && (hasRequirements || active.some(item => item.officialOffer)) && remaining.length === 0 && <p role="status" className="text-sm text-amber-800">추가할 기본 추천 후보가 없습니다. 조건을 바꿔 새로 찾거나 제품을 직접 추가하세요.</p>}
    <div aria-label="추천 후보 목록" className="grid gap-4 sm:grid-cols-2">{remaining.map(card)}</div>
    {unknown.length > 0 && <details className="rounded-2xl border border-amber-200 p-4">
      <summary className="cursor-pointer font-semibold">최소 조건·호환성의 근거가 부족한 제품 ({unknown.length})</summary>
      <p className="my-3 text-sm text-zinc-600">선택 조건의 근거 또는 최근 판매가를 확인하지 못했습니다. 근거를 확인한 뒤 비교에 추가할 수 있습니다.</p>
      <div className="grid gap-4 sm:grid-cols-2">{unknown.map(card)}</div>
    </details>}
    {below.length > 0 && <details className="rounded-2xl border border-amber-200 p-4">
      <summary className="cursor-pointer font-semibold">최소 조건·호환성·예산에 미달한 제품 ({below.length})</summary>
      <p className="my-3 text-sm text-zinc-600">수집 사양·호환성이 선택 조건에 맞지 않거나 확인 판매가가 예산을 초과합니다. 근거를 확인한 뒤 비교에 추가할 수 있습니다.</p>
      <div className="grid gap-4 sm:grid-cols-2">{below.map(card)}</div>
    </details>}
    {legacy.length > 0 && <details className="rounded-2xl border border-zinc-200 p-4"><summary className="cursor-pointer font-semibold">이전 기준으로 수집한 후보 ({legacy.length})</summary><p className="my-3 text-sm text-zinc-600">공식 판매처와 가격을 확인하기 전에 수집한 제품입니다.</p><div className="grid gap-4 sm:grid-cols-2">{legacy.map(card)}</div></details>}
  </div>;
}
