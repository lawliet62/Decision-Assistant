"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/shared/api/client";
import type { AiCapabilitiesResponse } from "@/shared/api/types";

export function DecisionCreateForm() {
  const router = useRouter();
  const [intentText, setIntentText] = useState("");
  const [urls, setUrls] = useState("");
  const [templateKey, setTemplateKey] = useState<"LAPTOP" | "PHONE" | "TABLET" | "MONITOR" | "">("");
  const [aiConsent, setAiConsent] = useState(false);
  const [sensitiveDataAcknowledged, setSensitiveDataAcknowledged] = useState(false);
  const [capabilities, setCapabilities] = useState<AiCapabilitiesResponse>();
  const [webConsent, setWebConsent] = useState(false);
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState(false);

  useEffect(() => { api.GET("/api/v1/ai/capabilities").then(({ data }) => setCapabilities(data)); }, []);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(undefined);
    const { data, error } = await api.POST("/api/v1/decisions", {
      body: {
        intentText,
        sourceUrls: urls.split(/\r?\n/).map((url) => url.trim()).filter(Boolean),
        templateKey: templateKey || undefined,
        aiConsent,
        webConsent,
        sensitiveDataAcknowledged: aiConsent && sensitiveDataAcknowledged,
      },
    });
    setPending(false);
    if (error || !data?.id) {
      setError("Decision을 만들지 못했습니다. backend 상태와 입력을 확인하세요.");
      return;
    }
    router.push(`/decisions/${data.id}`);
  }

  return (
    <form onSubmit={submit} className="mt-8 space-y-6 rounded-3xl border border-zinc-200 bg-white p-7 shadow-sm">
      <label className="block">
        <span className="text-sm font-medium text-zinc-700">어떤 상황에서 무엇을 고르고 있나요?</span>
        <textarea required maxLength={4000} value={intentText} onChange={(event) => setIntentText(event.target.value)} placeholder="예: 매일 들고 다니며 개발할 노트북을 찾고 있어요. 배터리와 무게가 중요하고 예산은 200만원 정도예요." className="mt-2 min-h-32 w-full rounded-xl border border-zinc-300 px-4 py-3 outline-none focus:border-emerald-500" />
      </label>
      <label className="block">
        <span className="text-sm font-medium text-zinc-700">비교 중인 후보 링크</span>
        <textarea required value={urls} onChange={(event) => setUrls(event.target.value)} placeholder={"https://...\nhttps://..."} className="mt-2 min-h-28 w-full rounded-xl border border-zinc-300 px-4 py-3 font-mono text-sm outline-none focus:border-emerald-500" />
        <span className="mt-2 block text-xs text-zinc-500">한 줄에 하나씩, 최대 5개. 링크 하나로 시작해 대안을 제안받을 수도 있습니다.</span>
      </label>
      <label className="block text-sm text-zinc-700">
        <span className="font-medium">제품 범주 <span className="font-normal text-zinc-400">(비워두면 자동 판단)</span></span>
        <select value={templateKey} onChange={(event) => setTemplateKey(event.target.value as typeof templateKey)} className="mt-2 w-full rounded-xl border border-zinc-300 bg-white px-4 py-3">
          <option value="">자동 판단</option><option value="LAPTOP">노트북</option><option value="PHONE">휴대폰</option><option value="TABLET">태블릿</option><option value="MONITOR">모니터</option>
        </select>
      </label>
      <div className="space-y-3 rounded-2xl bg-amber-50 p-4 text-sm text-amber-950">
        <label className="flex gap-3"><input required type="checkbox" checked={webConsent} onChange={(event) => setWebConsent(event.target.checked)} className="mt-1" /><span>공개 링크와 제품 검색어를 Firecrawl에 보내 후보 정보를 가져오는 데 동의합니다.</span></label>
        <label className="flex gap-3"><input type="checkbox" checked={aiConsent} onChange={(event) => setAiConsent(event.target.checked)} disabled={!capabilities?.enabled} className="mt-1" /><span>이 Decision의 제품 정보와 일반 구매 메모를 {capabilities?.enabled ? `${capabilities.provider} (${capabilities.model})` : "AI provider(현재 비활성)"}에 보내 구조화하는 데 동의합니다.</span></label>
        {aiConsent && <label className="flex gap-3"><input required type="checkbox" checked={sensitiveDataAcknowledged} onChange={(event) => setSensitiveDataAcknowledged(event.target.checked)} className="mt-1" /><span>개인 식별·금융·건강·비공개 업무 정보가 포함되지 않았음을 확인합니다.</span></label>}
      </div>
      {error && <p className="text-sm text-red-700">{error}</p>}
      <button disabled={pending} className="rounded-full bg-zinc-950 px-5 py-3 font-semibold text-white disabled:opacity-50">{pending ? "결정 문서 만드는 중…" : "비교 시작하기"}</button>
    </form>
  );
}
