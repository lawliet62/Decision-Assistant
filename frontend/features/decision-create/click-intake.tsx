"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import { DecisionCreateForm } from "./decision-create-form";
import { problemMessage } from "@/features/product-lookup/product-picker";

type Template = components["schemas"]["IntakeTemplate"];
type Capabilities = components["schemas"]["LookupCapabilities"];
type AiCapabilities = components["schemas"]["AiCapabilitiesResponse"];
const chip = "rounded-xl border px-4 py-3 text-sm transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 ";

export function ClickIntake({ initialCategory }: { initialCategory?: string }) {
  const router = useRouter();
  const [templates, setTemplates] = useState<Template[]>([]);
  const [category, setCategory] = useState(initialCategory ?? "EARBUDS");
  const [uses, setUses] = useState<string[]>([]);
  const [budget, setBudget] = useState<number>();
  const [compatibility, setCompatibility] = useState<string>();
  const [minimumRequirements, setMinimumRequirements] = useState<NonNullable<components["schemas"]["DiscoveryRequest"]["minimumRequirements"]>>({});
  const [note, setNote] = useState("");
  const [manual, setManual] = useState(false);
  const [web, setWeb] = useState<Capabilities>();
  const [ai, setAi] = useState<AiCapabilities>();
  const [consents, setConsents] = useState({ search: false, collection: false, ai: false, sensitive: false });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();
  const inFlight = useRef(false);
  const template = templates.find(item => item.key === category) ?? templates[0];

  useEffect(() => {
    let active = true;
    Promise.all([api.GET("/api/v1/intake-templates"), api.GET("/api/v1/product-lookups/capabilities"), api.GET("/api/v1/ai/capabilities")])
      .then(([items, webResult, aiResult]) => {
        if (!active) return;
        if (!items.data) throw new Error("템플릿을 불러오지 못했습니다.");
        setTemplates(items.data); setWeb(webResult.data); setAi(aiResult.data);
      }).catch(() => { if (active) setError("설정을 불러오지 못했습니다. 새로고침하거나 제품을 직접 추가하세요."); });
    return () => { active = false; };
  }, []);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (inFlight.current || !template) return;
    inFlight.current = true; setPending(true); setError(undefined);
    try {
      const { data, error } = await api.POST("/api/v1/decisions", { body: {
        templateKey: template.key, intentText: note || undefined,
        discovery: { templateVersion: template.version!, uses: (template.uses ?? []).filter(value => uses.includes(value)), maxBudget: budget, compatibility, minimumRequirements,
          searchConsent: consents.search, collectionConsent: consents.collection,
          aiConsent: consents.ai, sensitiveDataAcknowledged: consents.sensitive },
      } });
      if (error || !data?.id) { setError(problemMessage(error)); return; }
      router.push(`/decisions/${data.id}`);
    } catch { setError("연결이 끊겼습니다. 최근 비교에서 생성 여부를 확인하세요."); }
    finally { inFlight.current = false; setPending(false); }
  }

  return <div className="mt-8 space-y-6">
    <div className="flex flex-wrap gap-2" aria-label="시작 방법">
      <button className={chip + (!manual ? "border-emerald-700 bg-emerald-50 text-emerald-900" : "border-zinc-200")} aria-pressed={!manual} onClick={() => setManual(false)}>조건으로 추천받기</button>
      <button className={chip + (manual ? "border-emerald-700 bg-emerald-50 text-emerald-900" : "border-zinc-200")} aria-pressed={manual} onClick={() => setManual(true)}>제품 직접 추가</button>
    </div>
    {manual ? <DecisionCreateForm /> : <form onSubmit={submit} className="space-y-7 rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm">
      <fieldset disabled={pending} className="space-y-7">
        <ChoiceGroup title="제품군" options={templates.map(item => ({ value: item.key!, label: item.label! }))} selected={template?.key} onSelect={value => { setCategory(value!); setUses([]); setBudget(undefined); setCompatibility(undefined); setMinimumRequirements({}); }} />
        {template && <>
          <ChoiceGroup title="주로 어떻게 쓰나요? (복수 선택)" options={[{ label: "아직 모르겠어요" }, ...(template.uses ?? []).map(value => ({ value, label: value }))]} selected={uses} onSelect={value => setUses(current => value === undefined ? [] : current.includes(value) ? current.filter(item => item !== value) : [...current, value])} />
          <ChoiceGroup title="최대 예산" options={[{ label: "제한 없음" }, ...(template.budgets ?? []).map(value => ({ value: String(value), label: `${value / 10000}만원` }))]} selected={budget === undefined ? undefined : String(budget)} onSelect={value => setBudget(value ? Number(value) : undefined)} />
          <ChoiceGroup title="필요한 호환성" options={[{ label: "상관없음" }, ...(template.compatibility ?? []).map(value => ({ value, label: value }))]} selected={compatibility} onSelect={setCompatibility} />
          {!!template.minimumRequirements?.length && <fieldset className="space-y-4 rounded-2xl border border-zinc-200 p-4">
            <legend className="px-1 font-semibold">필요한 최소 사양 (선택)</legend>
            <p className="text-sm text-zinc-600">필요한 항목만 선택하세요. 미달 제품은 따로 보여드리며, 사양을 찾지 못한 제품은 확인 필요로 표시합니다.</p>
            {template.minimumRequirements.map(item => <ChoiceGroup key={item.id} title={item.label!}
              options={[{ label: "상관없음" }, ...(item.choices ?? []).map(choice => ({ value: choice.id, label: choice.label! }))]}
              selected={minimumRequirements[item.id!]} onSelect={value => setMinimumRequirements(current => {
                const next = { ...current };
                if (value === undefined || current[item.id!] === value) delete next[item.id!]; else next[item.id!] = value;
                return next;
              })} />)}
            {template.key === "TABLET" && <p className="text-xs text-zinc-500">전용 펜 지원은 능동형 펜과의 호환을 뜻하며, 펜 기본 포함 여부와는 다릅니다.</p>}
          </fieldset>}
          <details className="text-sm"><summary className="cursor-pointer text-zinc-600">상세 조건 (선택)</summary><div className="mt-3 space-y-3">
            <label className="block">정확한 최대 예산 (원)<input type="number" min={1} max={100000000} value={budget ?? ""} onChange={event => setBudget(event.target.value ? Number(event.target.value) : undefined)} className="mt-1 w-full rounded-xl border p-3" /></label>
            <label className="block">구매 메모<textarea maxLength={4000} value={note} onChange={event => setNote(event.target.value)} className="mt-1 w-full rounded-xl border p-3" /></label>
            <p className="text-xs text-zinc-500">메모는 비교에 저장하며 최초 추천 검색·AI에는 보내지 않습니다.</p>
          </div></details>
        </>}
        <fieldset className="space-y-3 rounded-2xl bg-zinc-50 p-4 text-sm">
          <legend className="font-semibold">추천 정보 가져오기</legend>
          <label className="flex gap-2"><input type="checkbox" checked={consents.search} onChange={e => setConsents({ ...consents, search: e.target.checked })} />선택 조건을 Serper로 보내 제품을 검색하는 데 동의합니다.</label>
          <label className="flex gap-2"><input type="checkbox" checked={consents.collection} onChange={e => setConsents({ ...consents, collection: e.target.checked })} />검색된 공개 페이지를 후보 선택 전에 Firecrawl로 수집하는 데 동의합니다. 이후 직접 추가한 페이지에도 적용됩니다.</label>
          <label className="flex gap-2"><input type="checkbox" checked={consents.ai} onChange={e => setConsents({ ...consents, ai: e.target.checked })} />선택 조건과 수집 정보를 {ai?.provider ?? "AI"} ({ai?.model ?? "설정 확인 중"})에 보내 제품·사양을 정리하는 데 동의합니다.</label>
          <label className="flex gap-2"><input type="checkbox" checked={consents.sensitive} onChange={e => setConsents({ ...consents, sensitive: e.target.checked })} />개인 식별·금융·건강·비공개 업무 정보가 없음을 확인합니다.</label>
          {ai?.provider === "gemini" && <p className="text-xs text-zinc-600">Gemini 무료 티어에서는 전송 콘텐츠가 Google 제품 개선에 사용될 수 있습니다.</p>}
          <p className="text-xs text-zinc-500">이 비교에만 적용됩니다. 출처·짧은 발췌·조회 시점을 저장하며 자동 정보는 확인이 필요합니다. 실제 판매·재고·최저가는 보장하지 않습니다.</p>
        </fieldset>
        {web && ai && !(web.searchEnabled && web.importEnabled && ai.enabled) && <p role="status" className="text-sm text-amber-800">현재 추천 서비스가 꺼져 있습니다. 제품 직접 추가를 이용하세요.</p>}
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        <button disabled={!template || !web?.searchEnabled || !web.importEnabled || !ai?.enabled || !Object.values(consents).every(Boolean)} className="rounded-full bg-emerald-800 px-6 py-3 font-semibold text-white disabled:opacity-40">{pending ? "비교 준비 중…" : "추천 제품 보기"}</button>
      </fieldset>
    </form>}
  </div>;
}

function ChoiceGroup({ title, options, selected, onSelect }: { title: string; options: { value?: string; label: string }[]; selected?: string | string[]; onSelect: (value?: string) => void }) {
  const isSelected = (value?: string) => Array.isArray(selected) ? value === undefined ? selected.length === 0 : selected.includes(value) : selected === value;
  return <fieldset><legend className="mb-3 font-semibold">{title}</legend><div className="flex flex-wrap gap-2">{options.map(option => <button key={option.label} type="button" aria-pressed={isSelected(option.value)} onClick={() => onSelect(option.value)} className={chip + (isSelected(option.value) ? "border-emerald-700 bg-emerald-50 text-emerald-950" : "border-zinc-200 hover:border-zinc-400")}>{option.label}</button>)}</div></fieldset>;
}
