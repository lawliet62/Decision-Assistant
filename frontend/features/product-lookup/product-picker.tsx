"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type SelectedSource = components["schemas"]["AddSourceRequest"];
export type LookupCapabilities = components["schemas"]["LookupCapabilities"];
type SearchResult = components["schemas"]["SearchResult"];

export function ProductPicker({ selectedUrls, onSelect, disabled = false }: {
  selectedUrls: string[]; onSelect: (source: SelectedSource) => void | Promise<void>; disabled?: boolean;
}) {
  const [capabilities, setCapabilities] = useState<LookupCapabilities>();
  const [query, setQuery] = useState("");
  const [consent, setConsent] = useState(false);
  const [results, setResults] = useState<SearchResult[]>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [url, setUrl] = useState("");
  const [name, setName] = useState("");
  const inFlight = useRef(false);

  useEffect(() => {
    api.GET("/api/v1/product-lookups/capabilities").then(({ data }) => setCapabilities(data))
      .catch(() => setError("검색 상태를 확인할 수 없습니다. URL 직접 추가를 이용하세요."));
  }, []);

  async function search() {
    if (inFlight.current || !query.trim() || !consent || !capabilities?.searchEnabled) return;
    inFlight.current = true; setBusy(true); setError(undefined); setResults(undefined);
    try {
      const { data, error } = await api.POST("/api/v1/product-lookups", { body: { query: query.trim(), searchConsent: consent } });
      if (error || !data) { setError(problemMessage(error)); return; }
      setResults(data.results ?? []);
    } catch { setError("검색 연결이 끊겼습니다. 잠시 후 다시 시도하거나 URL을 직접 추가하세요."); }
    finally { inFlight.current = false; setBusy(false); }
  }

  async function choose(source: SelectedSource) {
    if (inFlight.current || disabled) return;
    inFlight.current = true; setBusy(true); setError(undefined);
    try { await onSelect(source); setResults(undefined); setQuery(""); setUrl(""); setName(""); }
    catch (error) { setError(error instanceof Error ? error.message : "후보를 추가하지 못했습니다."); }
    finally { inFlight.current = false; setBusy(false); }
  }

  function addUrl() {
    try {
      const parsed = new URL(url.trim());
      if (!["https:", "http:"].includes(parsed.protocol) || parsed.username || parsed.password) throw new Error();
      void choose({ url: parsed.href, name: name.trim() || undefined });
    } catch { setError("공개 HTTP(S) URL을 입력하세요."); }
  }

  return <div className="space-y-4">
    <p className="text-sm text-zinc-600">알고 있는 제품명을 검색한 뒤 정확한 모델·세대의 페이지를 선택하세요. 검색 순서는 추천 순위가 아닙니다.</p>
    <fieldset disabled={disabled || busy} className="space-y-3 disabled:opacity-60">
      <label className="block text-sm font-medium">제품명<input maxLength={200} value={query} onChange={(e) => { setQuery(e.target.value); setResults(undefined); }} onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); void search(); } }} placeholder="예: Galaxy Buds3 Pro" className={inputClass} /></label>
      <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} disabled={!capabilities?.searchEnabled} className="mt-1" /><span>{capabilities?.searchNotice ?? "입력한 제품명을 Serper로 전송하는 데 동의합니다. 개인정보는 입력하지 마세요."}</span></label>
      <button type="button" disabled={!consent || !query.trim() || !capabilities?.searchEnabled} onClick={search} className={buttonClass}>{busy ? "처리 중…" : "제품 검색"}</button>
    </fieldset>
    {capabilities && !capabilities.searchEnabled && <p className="text-sm text-amber-800">현재 제품 검색이 꺼져 있습니다. 아래 URL 직접 추가로 계속할 수 있습니다.</p>}
    <div aria-live="polite">
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      {results?.length === 0 && <p className="text-sm text-zinc-600">검색 결과가 없습니다. 제조사·모델명을 더 구체적으로 입력하거나 URL을 추가하세요.</p>}
    </div>
    {results && results.length > 0 && <ul aria-label="제품 검색 결과" className="space-y-3">{results.map((result) => <li key={result.url} className="rounded-xl border border-zinc-200 p-4">
      <p className="font-semibold">{result.title}</p><p className="mt-1 text-sm text-zinc-600">{result.description}</p>
      <a href={result.url} target="_blank" rel="noreferrer" className="my-2 block break-all text-xs text-emerald-700">{result.url}</a>
      <button type="button" disabled={disabled || busy || selectedUrls.includes(result.url!)} onClick={() => choose({ url: result.url!, name: result.title })} className={buttonClass}>{selectedUrls.includes(result.url!) ? "이미 추가됨" : "이 제품 선택"}</button>
    </li>)}</ul>}
    <details className="rounded-xl border border-zinc-200 p-4"><summary className="cursor-pointer text-sm font-medium">URL 직접 추가 (검색이 어렵다면)</summary>
      <fieldset disabled={disabled || busy} className="mt-3 space-y-3">
        <label className="block text-sm">제품명 (선택)<input value={name} maxLength={200} onChange={(e) => setName(e.target.value)} className={inputClass} /></label>
        <label className="block text-sm">제품 페이지 URL<input type="url" maxLength={2048} value={url} onChange={(e) => setUrl(e.target.value)} className={inputClass} /></label>
        <button type="button" disabled={!url.trim()} onClick={addUrl} className={buttonClass}>URL로 후보 추가</button>
      </fieldset>
    </details>
  </div>;
}

export function problemMessage(error: unknown): string {
  const problem = error as { detail?: string; violations?: string[] } | undefined;
  return problem?.violations?.join(" · ") ?? problem?.detail ?? "요청을 처리하지 못했습니다.";
}
const inputClass = "mt-2 block w-full rounded-xl border border-zinc-300 bg-white px-3 py-2";
const buttonClass = "rounded-full bg-zinc-950 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40";
