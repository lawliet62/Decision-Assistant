"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
type Kind = "CHOSEN" | "DEFERRED" | "ABANDONED";
type Reason = components["schemas"]["Reason"];
type Memory = components["schemas"]["Memory"];
const button = "rounded-xl border px-4 py-2 text-sm aria-pressed:bg-emerald-100 disabled:opacity-40";
export function ReasonPicker({ options, onComplete, initialResolution }: { options: components["schemas"]["OptionData"][]; initialResolution?: components["schemas"]["ResolutionData"]; onComplete: (kind: Kind, option: string, note: string, ids: string[], primary?: string) => void }) {
  const [kind, setKind] = useState<Kind>(initialResolution?.kind ?? "CHOSEN");
  const [selected, setSelected] = useState(initialResolution?.selectedOptionId ?? "");
  const [ids, setIds] = useState<string[]>(initialResolution?.reasonIds ?? []);
  const [primary, setPrimary] = useState(initialResolution?.primaryReasonId ?? "");
  const [note, setNote] = useState(initialResolution?.rationale ?? "");
  const [templates, setTemplates] = useState<Reason[]>([]);
  useEffect(() => { api.GET("/api/v1/decisions/reason-templates").then(r => setTemplates(r.data ?? [])).catch(() => {}); }, []);
  return <section className="space-y-4 rounded-3xl border bg-white p-6"><h2 className="text-xl font-semibold">결정 결과</h2>
    <p className="text-sm text-zinc-600">선택·보류·포기 모두 결정 완료입니다. 이유 선택과 추가 설명은 선택 사항입니다.</p>
    <div className="flex flex-wrap gap-2">{([["CHOSEN", "제품 선택"], ["DEFERRED", "보류"], ["ABANDONED", "포기"]] as const).map(([value,label]) => <button type="button" key={value} className={button} aria-pressed={kind === value} onClick={() => { setKind(value); setIds([]); setPrimary(""); }}>{label}</button>)}</div>
    {kind === "CHOSEN" && <label className="block text-sm">선택한 제품<select aria-label="실제로 선택한 후보" className="ml-3 rounded-xl border p-2" value={selected} onChange={e => setSelected(e.target.value)}><option value="">후보 선택</option>{options.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}</select></label>}
    <fieldset className="space-y-2"><legend className="mb-2 font-medium">결정 이유 (여러 개 선택)</legend><div className="flex flex-wrap gap-2">{templates.filter(r => r.kind === kind).map(r => <button type="button" key={r.id} className={button} aria-pressed={ids.includes(r.id!)} onClick={() => { setIds(ids.includes(r.id!) ? ids.filter(id => id !== r.id) : [...ids, r.id!]); if (primary === r.id) setPrimary(""); }}>{r.label}</button>)}</div></fieldset>
    {!!ids.length && <label className="block text-sm">가장 중요한 이유<select className="ml-3 rounded-xl border p-2" value={primary} onChange={e => setPrimary(e.target.value)}><option value="">지정하지 않음</option>{templates.filter(r => ids.includes(r.id!)).map(r => <option key={r.id} value={r.id}>{r.label}</option>)}</select></label>}
    <label className="block text-sm">더 남기고 싶은 이유<textarea aria-label="결정 이유" placeholder="결정 이유" className="mt-2 w-full rounded-xl border p-3" value={note} onChange={e => setNote(e.target.value)} /></label>
    <p className="text-xs text-zinc-500">이유는 나의 판단 기록이며 사양의 지원 여부를 바꾸지 않습니다.</p>
    <button type="button" className={button + " bg-zinc-950 text-white"} disabled={kind === "CHOSEN" && !selected} onClick={() => onComplete(kind, selected, note, ids, primary || undefined)}>결정 완료</button>
  </section>;
}
export function PastDecisions({ id, onApply }: { id: string; onApply: (note: string) => void }) {
  const [memories, setMemories] = useState<Memory[]>([]);
  const [templates, setTemplates] = useState<Reason[]>([]);
  const [handled, setHandled] = useState<string[]>([]);
  useEffect(() => { Promise.all([api.GET("/api/v1/decisions/{decisionId}/memories", { params: { path: { decisionId: id } } }), api.GET("/api/v1/decisions/reason-templates")]).then(([m,r]) => { setMemories(m.data ?? []); setTemplates(r.data ?? []); }).catch(() => {}); }, [id]);
  if (!memories.length) return null;
  return <section className="space-y-3 rounded-3xl border bg-emerald-50 p-5"><h2 className="font-semibold">과거 결정에서 참고하기</h2><p className="text-sm">같은 제품군의 최근 기록입니다. 이번에도 중요한지는 직접 선택하세요.</p>{memories.map(m => <article key={m.resolutionId} className="space-y-2 rounded-xl bg-white p-4 text-sm"><p>{m.decidedAt?.slice(0,10)} · {m.title} · {m.kind === "CHOSEN" ? `${m.selectedName ?? "제품"} 선택` : m.kind === "DEFERRED" ? "보류" : "포기"}</p><p>{m.reasonIds?.map(id => templates.find(r => r.id === id)?.label ?? id).join(" · ") || "선택한 이유 없음"}</p>{m.rationale && <p className="whitespace-pre-wrap">당시 메모: {m.rationale}</p>}{m.outcomes?.map(o => <p key={o.id}>이후 회고: {o.notes || "메모 없음"} · 만족도 {o.satisfaction}</p>)}<Link className="underline" href={`/decisions/${m.decisionId}`}>당시 기록 보기</Link><div className="flex flex-wrap gap-2"><button type="button" className={button} disabled={handled.includes(m.resolutionId!)} onClick={() => { onApply(`과거 기록 참고 (${m.decisionId}/${m.resolutionId}): ${m.reasonIds?.map(id => templates.find(r => r.id === id)?.label ?? id).join(" · ") ?? ""} ${m.rationale ?? ""}`); setHandled([...handled,m.resolutionId!]); }}>이번에도 참고</button><button type="button" className={button} disabled={handled.includes(m.resolutionId!)} onClick={() => setHandled([...handled,m.resolutionId!])}>이번에는 다름</button></div></article>)}</section>;
}
