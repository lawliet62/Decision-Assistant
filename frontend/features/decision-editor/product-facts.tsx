"use client";

import { useEffect, useState } from "react";
import { api } from "@/shared/api/client";
import type { DecisionResponse } from "@/shared/api/types";
import type { components } from "@/shared/api/schema";
import { SourceLink } from "@/features/product-lookup/source-link";
import { ProductOffer } from "@/features/product-lookup/product-offer";

type Fact = components["schemas"]["ProductFact"];
type Check = components["schemas"]["RequirementCheck"];

export function RequirementChecks({ checks, label = "최소 사양 확인" }: { checks?: Check[]; label?: string }) {
  if (!checks?.length) return null;
  return <ul aria-label={label} className="space-y-3 text-sm">{checks.map(check => {
    const confirmed = check.facts?.length && check.facts.every(fact => fact.verificationStatus === "CONFIRMED");
    const unknown = check.unknownReason === "CONFLICT" ? "근거가 상충함" : check.unknownReason === "AMBIGUOUS" || check.facts?.length ? "표현이 모호함" : "근거 없음";
    const label = check.status === "BELOW" ? "미달" : check.status === "MEETS" ? confirmed ? "확인한 사양상 충족" : "수집 사양상 충족" : unknown;
    return <li key={check.id} className="rounded-xl bg-zinc-50 p-3">
      <p className="font-medium">{check.label}: {check.requiredValue}</p>
      <p className={check.status === "BELOW" ? "text-red-700" : check.status === "MEETS" ? "text-emerald-800" : "text-amber-800"}>{label}</p>
      {check.facts?.map((fact, index) => <div key={index} className="mt-2"><FactValue fact={fact} compact /></div>)}
    </li>;
  })}</ul>;
}

export function FactValue({ fact, compact = false }: { fact?: Fact; compact?: boolean }) {
  if (!fact) return <span className="text-zinc-400">미확인</span>;
  return <div className="space-y-1"><span className="font-medium">{fact.value}</span>{(!compact || fact.verificationStatus !== "UNREVIEWED") && <span className="block text-xs text-amber-800">{fact.verificationStatus === "CONFIRMED" ? "확인됨" : fact.verificationStatus === "DISPUTED" ? "이견 있음" : "사용자 확인 전"}</span>}
    {fact.officialEvidence && <details className="text-xs text-zinc-500"><summary className="cursor-pointer">공식 문서의 모델 연결 근거</summary><p>모델: {fact.officialEvidence.model}</p><p>판매 페이지: {fact.officialEvidence.purchaseModelQuote}</p><p>공식 문서: {fact.officialEvidence.documentModelQuote}</p></details>}
    {fact.sourceUrl && <details className="text-xs text-zinc-500"><summary className="cursor-pointer">출처·조회 시점</summary><SourceLink url={fact.sourceUrl} /><time className="block">{fact.fetchedAt ? new Date(fact.fetchedAt).toLocaleString("ko-KR") : "조회 시점 미확인"}</time><p className="mt-1">{fact.quote}</p></details>}</div>;
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
    {visible.length > 0 ? <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="제품 주요 정보 표"><table className="w-full text-sm"><thead><tr><th className="sticky left-0 min-w-24 bg-white p-3 text-left">주요 정보</th>{visible.map(option => {
      const source = decision.productFacts?.find(item => item.optionId === option.id);
      return <th key={option.id} className="min-w-52 p-3 text-left align-top"><h3 className="font-semibold">{option.name}</h3><ProductOffer offer={source?.officialOffer} name={option.name} />{!source?.officialOffer && <SourceLink url={option.sourceUrl} kind={source?.sourceKind} />}</th>;
    })}</tr></thead>
      <tbody>{hasRequirements && <tr className="border-t border-zinc-100"><th className="sticky left-0 bg-white p-3 text-left align-top font-medium">최소 조건</th>{visible.map(option => <td key={option.id} className="p-3 align-top"><RequirementChecks checks={decision.productFacts?.find(item => item.optionId === option.id)?.requirementChecks} /></td>)}</tr>}
      {decision.productFacts?.some(item => item.compatibilityCheck) && <tr className="border-t border-zinc-100"><th className="sticky left-0 bg-white p-3 text-left align-top font-medium">필요한 호환성</th>{visible.map(option => {
        const check = decision.productFacts?.find(item => item.optionId === option.id)?.compatibilityCheck;
        return <td key={option.id} className="p-3 align-top">{check ? <RequirementChecks checks={[check]} label="호환성 확인" /> : "미확인"}</td>;
      })}</tr>}
      {names.map(name => <tr key={name} className="border-t border-zinc-100"><th className="sticky left-0 bg-white p-3 text-left font-medium">{name}</th>{visible.map(option => {
        const facts = decision.productFacts?.find(item => item.optionId === option.id)?.facts?.filter(fact => fact.name === name) ?? [];
        return <td key={option.id} className="space-y-3 p-3 align-top">{facts.length ? facts.map((fact, index) => <FactValue key={index} fact={fact} />) : <FactValue />}</td>;
      })}</tr>)}</tbody>
    </table></div> : <p className="text-sm text-zinc-500">비교할 제품을 선택하세요.</p>}
  </section>;
}
