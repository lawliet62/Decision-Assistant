"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/shared/api/client";
import type { AiCapabilitiesResponse, DecisionResponse } from "@/shared/api/types";
import { ProductPicker, problemMessage, type SelectedSource, type LookupCapabilities } from "@/features/product-lookup/product-picker";

export function DecisionCreateForm() {
  const router = useRouter();
  const [intentText, setIntentText] = useState("");
  const [title, setTitle] = useState("");
  const titleHint = useId();
  const [sources, setSources] = useState<SelectedSource[]>([]);
  const [templateKey, setTemplateKey] = useState<DecisionResponse["templateKey"]>("EARBUDS");
  const [aiConsent, setAiConsent] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [ai, setAi] = useState<AiCapabilitiesResponse>();
  const [web, setWeb] = useState<LookupCapabilities>();
  const [webConsent, setWebConsent] = useState(false);
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);

  useEffect(() => {
    api.GET("/api/v1/ai/capabilities").then(({ data }) => setAi(data)).catch(() => {});
    api.GET("/api/v1/product-lookups/capabilities").then(({ data }) => setWeb(data)).catch(() => {});
  }, []);

  function select(source: SelectedSource) {
    if (sources.some((item) => item.url === source.url)) throw new Error("이미 추가한 페이지입니다.");
    if (sources.length >= 5) throw new Error("후보는 최대 5개까지 추가할 수 있습니다.");
    setSources((current) => [...current, source]);
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (inFlight.current || sources.length === 0) return;
    inFlight.current = true; setPending(true); setError(undefined);
    try {
      const { data, error } = await api.POST("/api/v1/decisions", { body: {
        intentText, title: title.trim() || undefined, selectedSources: sources, templateKey,
        aiConsent: aiConsent && Boolean(ai?.enabled), webConsent: webConsent && Boolean(web?.importEnabled),
        sensitiveDataAcknowledged: aiConsent && acknowledged,
      } });
      if (error || !data?.id) { setError(problemMessage(error)); return; }
      router.push(`/decisions/${data.id}`);
    } catch { setError("연결이 끊겼습니다. 결정 목록에서 생성 여부를 확인한 뒤 다시 시도하세요."); }
    finally { inFlight.current = false; setPending(false); }
  }

  return <form onSubmit={submit} className="mt-8 space-y-7 rounded-3xl border border-zinc-200 bg-white p-7 shadow-sm">
    <fieldset disabled={pending} className="space-y-5">
      <legend className="mb-3 text-lg font-semibold">1. 비교할 상황</legend>
      <label className="block text-sm font-medium">어떤 상황에서 무엇을 고르고 있나요?<textarea required maxLength={4000} value={intentText} onChange={(e) => setIntentText(e.target.value)} placeholder="예: 출퇴근에 쓸 무선 이어폰을 비교하고 있어요. 오래 착용해도 편했으면 해요." className="mt-2 min-h-24 w-full rounded-xl border border-zinc-300 px-4 py-3" /></label>
      <div><label className="block text-sm font-medium">비교 제목 (선택)<input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} aria-describedby={titleHint} placeholder="예: 출퇴근 이어폰 고르기" className="mt-2 w-full rounded-xl border border-zinc-300 px-4 py-3" /></label>
        <p id={titleHint} className="mt-1 text-xs text-zinc-500">비워 두면 상황 설명 앞부분으로 제목을 자동으로 만듭니다. 최대 200자입니다.</p></div>
      <label className="block text-sm font-medium">제품 범주<select value={templateKey} onChange={(e) => setTemplateKey(e.target.value as typeof templateKey)} className="mt-2 w-full rounded-xl border border-zinc-300 bg-white px-4 py-3">
        <option value="EARBUDS">완전 무선 이어폰</option><option value="LAPTOP">노트북</option><option value="PHONE">휴대폰</option><option value="TABLET">태블릿</option><option value="MONITOR">모니터</option>
      </select></label>
    </fieldset>
    <section className="space-y-4"><h2 className="text-lg font-semibold">2. 제품명으로 후보 추가</h2>
      <ProductPicker selectedUrls={sources.map((item) => item.url)} onSelect={select} disabled={pending || sources.length >= 5} />
      <p className="text-sm text-zinc-500">선택한 후보 {sources.length}/5 · 한 개로 시작할 수 있고, 비교에는 두 개 이상이 필요합니다.</p>
      <ul aria-label="선택한 후보" className="space-y-2">{sources.map((source) => <li key={source.url} className="flex items-center justify-between gap-3 rounded-xl bg-emerald-50 p-3 text-sm"><span className="min-w-0 break-all">{source.name ?? source.url}</span><button type="button" disabled={pending} onClick={() => setSources(sources.filter((item) => item.url !== source.url))} className="shrink-0 text-red-700">제거</button></li>)}</ul>
    </section>
    {sources.length > 0 && <fieldset disabled={pending} className="space-y-3 rounded-2xl bg-amber-50 p-4 text-sm text-amber-950">
      <legend className="px-1 font-semibold">3. 선택한 페이지 정보 가져오기 (선택)</legend>
      <label className="flex gap-3"><input type="checkbox" checked={webConsent} onChange={(e) => setWebConsent(e.target.checked)} disabled={!web?.importEnabled} className="mt-1" /><span>{web?.importNotice ?? "페이지 수집 설정을 확인하고 있습니다."}</span></label>
      {!web?.importEnabled && <p>현재 페이지 수집이 꺼져 있습니다. 후보를 저장하고 비교 값을 직접 입력할 수 있습니다.</p>}
      <label className="flex gap-3"><input type="checkbox" checked={aiConsent} onChange={(e) => setAiConsent(e.target.checked)} disabled={!ai?.enabled} className="mt-1" /><span>이 비교의 제품 정보와 구매 메모를 {ai?.enabled ? `${ai.provider} (${ai.model})` : "AI (현재 비활성)"}에 보내 비교 항목을 정리하는 데 동의합니다. 자동 값은 검토가 필요합니다.</span></label>
      {aiConsent && <label className="flex gap-3"><input required type="checkbox" checked={acknowledged} onChange={(e) => setAcknowledged(e.target.checked)} className="mt-1" /><span>개인 식별·금융·건강·비공개 업무 정보가 포함되지 않았음을 확인합니다.</span></label>}
      <p className="text-xs">동의하지 않아도 직접 입력으로 비교할 수 있습니다. 동의는 이 비교에만 적용됩니다.</p>
    </fieldset>}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    <button disabled={pending || sources.length === 0} className="rounded-full bg-zinc-950 px-5 py-3 font-semibold text-white disabled:opacity-50">{pending ? "비교 준비 중…" : "비교 시작하기"}</button>
  </form>;
}
