import type { components } from "@/shared/api/schema";
import { FactValue, RequirementChecks } from "./product-facts";

type Candidate = components["schemas"]["CandidateSuggestionData"];

export function DiscoveryCandidates({ candidates, editable, optionCount, pending, hasRequirements, onAction }: {
  candidates: Candidate[]; editable: boolean; optionCount: number; pending: boolean; hasRequirements: boolean;
  onAction: (id: string, action: "ACCEPT" | "DISMISS") => void;
}) {
  const isBelow = (item: Candidate) => item.requirementChecks?.some(check => check.status === "BELOW");
  const remaining = candidates.filter(item => !isBelow(item));
  const below = candidates.filter(isBelow);
  function card(item: Candidate) {
    return <article key={item.id} aria-label={item.name} className="space-y-3 rounded-2xl border border-zinc-200 p-4">
      <h3 className="font-semibold">{item.name}</h3><p className="text-sm text-zinc-600">{item.reason}</p>
      <a href={item.url} target="_blank" rel="noreferrer" className="text-xs text-emerald-800">제품 출처 ↗</a>
      {item.fetchedAt && <p className="text-xs text-zinc-500">조회 {new Date(item.fetchedAt).toLocaleString("ko-KR")} · 수집 정보, 사용자 확인 전</p>}
      <RequirementChecks checks={item.requirementChecks} />
      <dl className="space-y-2 text-sm">{item.facts?.filter(fact => !item.requirementChecks?.some(check => check.facts?.some(checked => checked.name === fact.name))).map((fact, index) => <div key={index}><dt className="text-zinc-500">{fact.name}</dt><dd><FactValue fact={fact} compact /></dd></div>)}</dl>
      {!item.facts?.length && !item.requirementChecks?.length && <p className="text-sm text-zinc-500">수집한 사양이 없습니다.</p>}
      {editable && <div className="flex gap-2"><button disabled={optionCount >= 5} onClick={() => onAction(item.id!, "ACCEPT")} className="rounded-full bg-emerald-800 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">비교에 추가</button><button onClick={() => onAction(item.id!, "DISMISS")} className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-medium">제외</button></div>}
    </article>;
  }
  return <div className="space-y-4">
    {!pending && hasRequirements && remaining.length === 0 && <p role="status" className="text-sm text-amber-800">추가할 기본 추천 후보가 없습니다. 조건을 바꿔 새로 찾거나 제품을 직접 추가하세요.</p>}
    <div aria-label="추천 후보 목록" className="grid gap-4 sm:grid-cols-2">{remaining.map(card)}</div>
    {below.length > 0 && <details className="rounded-2xl border border-amber-200 p-4">
      <summary className="cursor-pointer font-semibold">최소 조건에 미달한 제품 ({below.length})</summary>
      <p className="my-3 text-sm text-zinc-600">수집 사양상 최소 조건에 미달합니다. 근거를 확인한 뒤 비교에 추가할 수 있습니다.</p>
      <div className="grid gap-4 sm:grid-cols-2">{below.map(card)}</div>
    </details>}
  </div>;
}
