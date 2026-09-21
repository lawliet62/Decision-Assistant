"use client";

import { useEffect, useState } from "react";
import { api } from "@/shared/api/client";
import type { DecisionResponse } from "@/shared/api/types";
import type { components } from "@/shared/api/schema";

type Fact = components["schemas"]["ProductFact"];

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
  function factFor(optionId: string, name: string): Fact | undefined {
    const criterion = decision.criteria?.find(item => item.name === name && item.kind === "NUMERIC_UTILITY");
    const value = criterion && decision.values?.find(item => item.optionId === optionId && item.criterionId === criterion.id && item.numericValue != null);
    const extracted = decision.productFacts?.find(item => item.optionId === optionId)?.facts?.find(fact => fact.name === name);
    // 사용자가 확인한 수치가 있으면 표시용 자동 추출값보다 우선한다. 주관 rating은 사양으로 표시하지 않는다.
    if (value && (value.verificationStatus === "CONFIRMED" || !extracted)) return {
      name, value: `${value.numericValue} ${criterion?.unit ?? ""}`.trim(), sourceUrl: value.sourceUrl,
      quote: value.sourceNote, verificationStatus: value.verificationStatus,
      fetchedAt: decision.guidance?.evidence?.find(item => item.optionId === optionId)?.fetchedAt,
    };
    return extracted;
  }
  return <section className="space-y-4 rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm">
    <h2 className="text-xl font-semibold">주요 정보 비교</h2>
    {options.length < 2 && <p className="text-sm text-zinc-500">제품을 두 개 이상 추가하면 나란히 비교할 수 있습니다.</p>}
    <div className="flex flex-wrap gap-3" aria-label="비교할 제품">{options.map(option => <label key={option.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={!hidden.includes(option.id!)} onChange={event => setHidden(event.target.checked ? hidden.filter(id => id !== option.id) : [...hidden, option.id!])} />{option.name}</label>)}</div>
    {visible.length > 0 ? <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="제품 주요 정보 표"><table className="w-full text-sm"><thead><tr><th className="sticky left-0 min-w-24 bg-white p-3 text-left">주요 정보</th>{visible.map(option => <th key={option.id} className="min-w-52 p-3 text-left align-top"><h3 className="font-semibold">{option.name}</h3><a href={option.sourceUrl} target="_blank" rel="noreferrer" className="text-xs font-normal text-emerald-800">제품 출처 ↗</a></th>)}</tr></thead>
      <tbody>{names.map(name => <tr key={name} className="border-t border-zinc-100"><th className="sticky left-0 bg-white p-3 text-left font-medium">{name}</th>{visible.map(option => <td key={option.id} className="p-3 align-top"><FactValue fact={factFor(option.id!, name)} /></td>)}</tr>)}</tbody>
    </table></div> : <p className="text-sm text-zinc-500">비교할 제품을 선택하세요.</p>}
  </section>;
}
