"use client";

import { useEffect, useState } from "react";
import { api } from "@/shared/api/client";
import type { DecisionResponse } from "@/shared/api/types";
import type { components } from "@/shared/api/schema";

type Fact = components["schemas"]["ProductFact"];
type Check = components["schemas"]["RequirementCheck"];

export function RequirementChecks({ checks }: { checks?: Check[] }) {
  if (!checks?.length) return null;
  return <ul aria-label="최소 사양 확인" className="space-y-3 text-sm">{checks.map(check => {
    const confirmed = check.facts?.length && check.facts.every(fact => fact.verificationStatus === "CONFIRMED");
    const label = check.status === "BELOW" ? "미달" : check.status === "MEETS" ? confirmed ? "확인한 사양상 충족" : "수집 사양상 충족" : "확인 필요";
    return <li key={check.id} className="rounded-xl bg-zinc-50 p-3">
      <p className="font-medium">{check.label} · {check.requiredValue}</p>
      <p className={check.status === "BELOW" ? "text-red-700" : check.status === "MEETS" ? "text-emerald-800" : "text-amber-800"}>{label}</p>
      {!!check.facts?.length ? check.facts.map((fact, index) => <div key={index} className="mt-2"><FactValue fact={fact} /></div>) : <p className="mt-1 text-zinc-500">사양 미확인</p>}
      {check.status === "UNKNOWN" && !!check.facts?.length && <p className="mt-1 text-xs text-zinc-500">값·단위·구성 또는 상충하는 근거를 확인해 주세요.</p>}
    </li>;
  })}</ul>;
}

export function FactValue({ fact }: { fact?: Fact }) {
  if (!fact) return <span className="text-zinc-400">미확인</span>;
  return <div className="space-y-1"><span className="font-medium">{fact.value}</span><span className="block text-xs text-amber-800">{fact.verificationStatus === "CONFIRMED" ? "확인됨" : fact.verificationStatus === "DISPUTED" ? "이견 있음" : "확인 필요"}</span>
    {fact.sourceUrl && <details className="text-xs text-zinc-500"><summary className="cursor-pointer">출처·조회 시점</summary><a href={fact.sourceUrl} target="_blank" rel="noreferrer" className="block break-all text-emerald-800">원문 보기 ↗</a><time>{fact.fetchedAt ? new Date(fact.fetchedAt).toLocaleString("ko-KR") : "조회 시점 미확인"}</time><p className="mt-1">{fact.quote}</p></details>}</div>;
}

export function ProductFacts({ decision }: { decision: DecisionResponse }) {
  const [templates, setTemplates] = useState<components["schemas"]["IntakeTemplate"][]>([]);
  const [hidden, setHidden] = useState<string[]>([]);
  useEffect(() => { api.GET("/api/v1/intake-templates").then(({ data }) => setTemplates(data ?? [])).catch(() => {}); }, []);
  const options = decision.options ?? [];
  const visible = options.filter(option => !hidden.includes(option.id!));
  const template = templates.find(item => item.key === decision.templateKey);
  const names = template?.facts ?? [...new Set(decision.productFacts?.flatMap(item => item.facts?.map(fact => fact.name!) ?? []) ?? [])];
  const hasRequirements = Object.keys(decision.discovery?.conditions?.minimumRequirements ?? {}).length > 0;
  return <section className="space-y-4 rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm">
    <h2 className="text-xl font-semibold">주요 정보 비교</h2>
    {options.length < 2 && <p className="text-sm text-zinc-500">제품을 두 개 이상 추가하면 나란히 비교할 수 있습니다.</p>}
    <div className="flex flex-wrap gap-3" aria-label="비교할 제품">{options.map(option => <label key={option.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={!hidden.includes(option.id!)} onChange={event => setHidden(event.target.checked ? hidden.filter(id => id !== option.id) : [...hidden, option.id!])} />{option.name}</label>)}</div>
    {visible.length > 0 ? <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="제품 주요 정보 표"><table className="w-full text-sm"><thead><tr><th className="sticky left-0 min-w-24 bg-white p-3 text-left">주요 정보</th>{visible.map(option => <th key={option.id} className="min-w-52 p-3 text-left align-top"><h3 className="font-semibold">{option.name}</h3><a href={option.sourceUrl} target="_blank" rel="noreferrer" className="text-xs font-normal text-emerald-800">제품 출처 ↗</a></th>)}</tr></thead>
      <tbody>{hasRequirements && <tr className="border-t border-zinc-100"><th className="sticky left-0 bg-white p-3 text-left align-top font-medium">최소 조건</th>{visible.map(option => <td key={option.id} className="p-3 align-top"><RequirementChecks checks={decision.productFacts?.find(item => item.optionId === option.id)?.requirementChecks} /></td>)}</tr>}
      {names.map(name => <tr key={name} className="border-t border-zinc-100"><th className="sticky left-0 bg-white p-3 text-left font-medium">{name}</th>{visible.map(option => {
        const facts = decision.productFacts?.find(item => item.optionId === option.id)?.facts?.filter(fact => fact.name === name) ?? [];
        return <td key={option.id} className="space-y-3 p-3 align-top">{facts.length ? facts.map((fact, index) => <FactValue key={index} fact={fact} />) : <FactValue />}</td>;
      })}</tr>)}</tbody>
    </table></div> : <p className="text-sm text-zinc-500">비교할 제품을 선택하세요.</p>}
  </section>;
}
