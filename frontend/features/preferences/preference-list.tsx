"use client";

import { useEffect, useState } from "react";
import { api } from "@/shared/api/client";
import type { PreferenceResponse } from "@/shared/api/types";

export function PreferenceList() {
  const [items, setItems] = useState<PreferenceResponse[]>([]);
  const [error, setError] = useState<string>();
  const [emailEnabled, setEmailEnabled] = useState<boolean>();
  useEffect(() => {
    refresh();
    api.GET("/api/v1/notification-preferences").then(({ data }) => setEmailEnabled(data?.emailEnabled));
  }, []);
  async function refresh() {
    const { data, error: apiError } = await api.GET("/api/v1/preferences");
    if (apiError) setError("성향 기억을 불러오지 못했습니다."); else setItems(data ?? []);
  }
  async function update(id: string, action: "CONFIRM" | "DISMISS") {
    const { error: apiError } = await api.PATCH("/api/v1/preferences/{preferenceId}", { params: { path: { preferenceId: id } }, body: { action } });
    if (apiError) setError("성향을 변경하지 못했습니다."); else refresh();
  }
  async function remove(id: string) {
    const { error: apiError } = await api.DELETE("/api/v1/preferences/{preferenceId}", { params: { path: { preferenceId: id } } });
    if (apiError) setError("성향을 삭제하지 못했습니다."); else refresh();
  }
  async function toggleEmail(enabled: boolean) {
    const { data, error: apiError } = await api.PATCH("/api/v1/notification-preferences", { body: { emailEnabled: enabled } });
    if (apiError) setError("이메일 reminder 설정을 변경하지 못했습니다."); else setEmailEnabled(data?.emailEnabled);
  }
  return <div className="mt-8 space-y-4">
    {error && <p className="rounded-xl bg-red-50 p-4 text-sm text-red-800">{error}</p>}
    <label className="flex items-center justify-between rounded-2xl border border-zinc-200 bg-white p-5"><span><strong className="block">회고 이메일</strong><span className="mt-1 block text-sm text-zinc-500">선택 14일·60일 뒤 일반적인 내용의 reminder를 받습니다.</span></span><input type="checkbox" checked={emailEnabled ?? false} onChange={(event) => toggleEmail(event.target.checked)} /></label>
    {items.length === 0 && <div className="rounded-3xl border border-dashed border-zinc-300 p-10 text-center text-zinc-500">선택 후 회고를 남기면 검토할 성향이 여기에 나타납니다.</div>}
    {items.map((item) => <article key={item.id} className={`rounded-3xl border p-5 ${item.status === "CONFIRMED" ? "border-violet-300 bg-violet-50" : "border-zinc-200 bg-white"}`}>
      <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-wide text-zinc-500">{item.templateKey} · {item.confidence} confidence</p><p className="mt-2 font-medium text-zinc-900">{item.statement}</p><p className="mt-1 text-sm text-zinc-500">적용 기준: {item.criterionName} · 강도 {item.strength}/3</p></div><span className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-zinc-600">{item.status}</span></div>
      <div className="mt-4 flex gap-2">{item.status !== "CONFIRMED" && <button onClick={() => update(item.id!, "CONFIRM")} className="rounded-full bg-zinc-950 px-4 py-2 text-sm font-semibold text-white">다음 결정에 적용</button>}{item.status !== "DISMISSED" && <button onClick={() => update(item.id!, "DISMISS")} className="rounded-full border border-zinc-300 bg-white px-4 py-2 text-sm">사용하지 않기</button>}<button onClick={() => remove(item.id!)} className="ml-auto text-sm text-red-600">삭제</button></div>
    </article>)}
  </div>;
}
