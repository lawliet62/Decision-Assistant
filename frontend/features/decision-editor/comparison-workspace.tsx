"use client";

import { useState } from "react";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import type { DecisionResponse } from "@/shared/api/types";
import { FactValue, RequirementChecks } from "./product-facts";
import { SourceLink } from "@/features/product-lookup/source-link";
import { BudgetInput } from "@/shared/ui/budget-input";

type Data = NonNullable<DecisionResponse["comparison"]>;
type Settings = NonNullable<Data["settings"]>;
type Snapshot = components["schemas"]["Snapshot"];
type Check = components["schemas"]["RequirementCheck"];
const button = "rounded-full border border-zinc-300 px-4 py-2 text-sm disabled:opacity-40";
const input = "mt-1 block w-full rounded-xl border border-zinc-300 bg-white p-2 text-sm";
const status = (check?: Check) => check?.status === "MEETS" ? "충족" : check?.status === "BELOW" ? "미달" : "미확인";
const money = (value: number) => `${value.toLocaleString("ko-KR")}원`;

export function ComparisonWorkspace({ decision, disabled, apply, run, onEditing }: {
  decision: DecisionResponse; disabled: boolean; apply: (data: DecisionResponse) => void;
  run: (action: () => Promise<void>) => void; onEditing: (editing: boolean) => void;
}) {
  const data = decision.comparison!;
  const [draft, setDraft] = useState<Settings>();
  const [previous, setPrevious] = useState<Data>();
  const [change, setChange] = useState<string[]>();
  const [error, setError] = useState<string>();
  const [confirmation, setConfirmation] = useState<{ optionId: string; feature: string; supported?: boolean; note: string; sourceUrl: string }>();
  const settings = draft ?? data.settings!;
  const edit = (next: Settings) => { setDraft(next); onEditing(true); setError(undefined); };
  const fail = (problem: unknown) => { const value = problem as { detail?: string; violations?: string[] }; setError(value?.violations?.join(" · ") ?? value?.detail ?? "저장하지 못했습니다. 입력을 유지했습니다."); };

  async function save(next: Settings, restore = false) {
    setError(undefined);
    try {
      const result = await api.PUT("/api/v1/decisions/{decisionId}/comparison", {
        params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.version ?? 0, settings: next },
      });
      if (!result.data) return fail((result as { error?: unknown }).error);
      const messages: string[] = [];
      for (const option of result.data.comparison?.options ?? []) {
        const before = data.options?.find(item => item.optionId === option.optionId);
        for (const check of option.checks ?? []) {
          const old = before?.checks?.find(item => item.id === check.id);
          if (!old || old.status !== check.status || old.requiredValue !== check.requiredValue)
            messages.push(`${decision.options?.find(item => item.id === option.optionId)?.name} · ${check.label}: ${old ? status(old) : "조건 없음"} → ${status(check)} (${check.requiredValue})`);
        }
        for (const check of before?.checks ?? []) if (!option.checks?.some(item => item.id === check.id))
          messages.push(`${decision.options?.find(item => item.id === option.optionId)?.name} · ${check.label}: 비교 조건에서 해제`);
      }
      if (data.settings?.focus !== next.focus) messages.push(`먼저 볼 정보: ${data.settings?.focus} → ${next.focus}. 사양 표시 순서만 바뀌며 점수와 지원 판정은 바뀌지 않습니다.`);
      if (JSON.stringify(data.settings?.deferredOptionIds) !== JSON.stringify(next.deferredOptionIds)) messages.push("후보의 비교 보류 상태를 변경했습니다. 후보와 확인 기록은 보존됩니다.");
      setChange(messages.length ? messages : ["조건을 적용했습니다. 후보별 판정에는 변화가 없습니다."]);
      setPrevious(restore ? undefined : data); setDraft(undefined); onEditing(false); apply(result.data);
    } catch { fail(undefined); }
  }

  async function confirm() {
    if (!confirmation) return;
    try {
      const result = await api.PUT("/api/v1/decisions/{decisionId}/comparison/confirmation", {
        params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.version ?? 0, ...confirmation },
      });
      if (!result.data) return fail((result as { error?: unknown }).error);
      setConfirmation(undefined); setPrevious(undefined); setChange(["직접 확인한 정보를 반영했습니다. 이 값은 자동 수집으로 덮어쓰지 않습니다."]);
      onEditing(false); apply(result.data);
    } catch { fail(undefined); }
  }

  return <section className="space-y-5 rounded-3xl border border-emerald-200 bg-white p-5 sm:p-6" aria-label="조건을 바꾸며 비교">
    <div><h2 className="text-xl font-semibold">내 조건으로 차이 보기</h2><p className="mt-1 text-sm text-zinc-600">같은 후보에서 조건을 바꿔 보세요. 근거가 없는 정보는 미확인으로 남깁니다.</p></div>
    <fieldset disabled={disabled || !!confirmation} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="max-w-md sm:col-span-2"><BudgetInput label="비교 예산 상한" value={settings.maxBudget} onChange={maxBudget => edit({ ...settings, maxBudget })} /></div>
        <label className="text-sm">기본 연결·사용 호환성<select aria-label="기본 연결·사용 호환성" value={settings.compatibility ?? ""} onChange={event => edit({ ...settings, compatibility: event.target.value || undefined })} className={input}><option value="">조건 없음</option>{["Android", "iOS", "멀티포인트"].map(value => <option key={value}>{value}</option>)}</select></label>
        <label className="text-sm">먼저 볼 정보<select aria-label="먼저 볼 정보" value={settings.focus} onChange={event => edit({ ...settings, focus: event.target.value })} className={input}>{["가격", "ANC 지원", "호환성", "배터리", "무게"].map(value => <option key={value}>{value}</option>)}</select></label>
      </div>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={settings.ancRequired} onChange={event => edit({ ...settings, ancRequired: event.target.checked })} />ANC 지원 필수</label>
      <p className="text-xs text-zinc-500">Android·iOS는 기본 연결·사용 기준입니다. 앱의 모든 기능을 보장하지 않습니다. 먼저 볼 정보는 표시 순서이며 점수 가중치와 별개입니다.</p>
      {draft && <div className="flex flex-wrap items-center gap-2"><button className={button} onClick={() => run(() => save(settings))}>조건 적용</button><button className={button} onClick={() => { setDraft(undefined); onEditing(false); setError(undefined); }}>조건 편집 취소</button><span className="text-sm text-amber-800">아래 결과는 아직 저장된 조건 기준입니다.</span></div>}
      {!draft && previous?.settings && <button className={button} onClick={() => run(() => save(previous.settings!, true))}>이전 조건으로 복원</button>}
    </fieldset>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {change && <div role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm"><p className="font-medium">변경 결과</p><ul className="mt-2 list-disc space-y-1 pl-5">{change.map((text, index) => <li key={index}>{text}</li>)}</ul><p className="mt-2">판정은 아래에 표시한 근거와 저장된 조건을 비교한 결과입니다.</p></div>}
    {confirmation && <fieldset disabled={disabled} className="space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">
      <legend className="px-2 font-medium">{decision.options?.find(item => item.id === confirmation.optionId)?.name} · {confirmation.feature === "anc" ? "ANC 지원" : confirmation.feature} 직접 확인</legend>
      <p className="text-sm">확인한 기능 제공 여부만 입력하세요. 원래 수집 근거는 남고, 비교 판정에는 이 확인값을 우선 사용합니다.</p>
      <label className="block text-sm">확인 결과<select aria-label="확인 결과" className={input} value={confirmation.supported === undefined ? "" : String(confirmation.supported)} onChange={event => setConfirmation({ ...confirmation, supported: event.target.value === "" ? undefined : event.target.value === "true" })}><option value="">직접 확인값 해제 · 수집 근거로 돌아가기</option><option value="true">지원 확인</option><option value="false">미지원 확인</option></select></label>
      <label className="block text-sm">확인 메모<input maxLength={1000} className={input} value={confirmation.note} onChange={event => setConfirmation({ ...confirmation, note: event.target.value })} placeholder="예: 제품 설명서 3쪽에서 확인" /></label>
      <label className="block text-sm">근거 URL (선택)<input type="url" maxLength={2048} className={input} value={confirmation.sourceUrl} onChange={event => setConfirmation({ ...confirmation, sourceUrl: event.target.value })} /></label>
      <p className="text-xs text-zinc-600">URL은 기록용이며 자동으로 가져오지 않습니다.</p>
      <div className="flex gap-2"><button className={button} onClick={() => run(confirm)}>확인 정보 저장</button><button className={button} onClick={() => { setConfirmation(undefined); onEditing(false); }}>입력 취소</button></div>
    </fieldset>}
    <ComparisonSummary data={data} options={decision.options} products={decision.productFacts} onConfirm={disabled || draft || confirmation ? undefined : (optionId, feature) => {
      const saved = data.confirmations?.find(item => item.optionId === optionId && item.feature === feature);
      setConfirmation({ optionId, feature, supported: saved?.supported, note: saved?.note ?? "", sourceUrl: saved?.sourceUrl ?? "" }); onEditing(true); setError(undefined);
    }} onDefer={disabled || draft || confirmation ? undefined : optionId => {
      const ids = data.settings?.deferredOptionIds ?? [];
      run(() => save({ ...data.settings!, deferredOptionIds: ids.includes(optionId) ? ids.filter(id => id !== optionId) : [...ids, optionId] }));
    }} />
  </section>;
}

export function ComparisonSummary({ data, options = [], products = [], onConfirm, onDefer }: {
  data: Data; options?: DecisionResponse["options"]; products?: DecisionResponse["productFacts"];
  onConfirm?: (optionId: string, feature: string) => void; onDefer?: (optionId: string) => void;
}) {
  const visible = options.filter(option => !data.settings?.deferredOptionIds?.includes(option.id!));
  const common = (data.options?.find(item => item.optionId === visible[0]?.id)?.checks ?? []).filter(check => visible.length > 1 && visible.every(option => data.options?.find(item => item.optionId === option.id)?.checks?.some(other => other.id === check.id && other.status === "MEETS")));
  const commonIds = new Set(common.map(check => check.id));
  const hasConditions = !!data.settings?.ancRequired || !!data.settings?.compatibility || data.settings?.maxBudget != null;
  const allUnmet = visible.length > 0 && hasConditions && visible.every(option => data.options?.find(item => item.optionId === option.id)?.checks?.some(check => check.status !== "MEETS"));
  const priced = (data.options ?? []).filter(item => visible.some(option => option.id === item.optionId) && item.price != null).sort((a, b) => a.price! - b.price!);
  const cheapest = priced[0];
  const focused = data.settings?.focus ?? "가격";
  return <div className="space-y-4">
    <p className="text-sm text-zinc-600">저장 조건: {data.settings?.maxBudget == null ? "예산 제한 없음" : money(data.settings.maxBudget) + " 이하"} · {data.settings?.ancRequired ? "ANC 필수" : "ANC 조건 없음"} · {data.settings?.compatibility ?? "호환성 조건 없음"}</p>
    {allUnmet && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">현재 모든 필수 조건을 충족한다고 확인된 후보가 없습니다. 미달과 미확인을 구분해 근거를 확인하거나, 후보를 보류하거나, 직접 조건을 바꿀 수 있습니다.</p>}
    {visible.length === 0 && <p className="rounded-xl bg-amber-50 p-3 text-sm">비교 중인 후보가 없습니다. 아래에서 보류한 후보를 복원하거나 비교 준비에서 후보를 추가하세요.</p>}
    <div className={`grid gap-4 md:grid-cols-2 ${visible.length > 2 ? "xl:grid-cols-3" : ""}`}>{visible.map(option => {
      const result = data.options?.find(item => item.optionId === option.id);
      const product = products.find(item => item.optionId === option.id);
      const checks = result?.checks?.filter(check => !commonIds.has(check.id)) ?? [];
      return <article key={option.id} aria-label={`${option.name} 조건 비교`} className="space-y-3 rounded-2xl border border-zinc-200 p-4">
        <h3 className="font-semibold">{option.name}</h3><p className="text-lg font-semibold">{result?.price == null ? "가격 미확인" : money(result.price)} <span className="text-xs font-normal text-zinc-500">{result?.priceOrigin}</span></p>
        <SourceLink url={product?.officialOffer?.purchaseUrl ?? option.sourceUrl} kind={product?.sourceKind} />
        {focused !== "가격" && <div className="rounded-xl bg-zinc-50 p-3 text-sm"><p className="mb-2 font-medium">먼저 볼 정보 · {focused}</p>{(product?.facts?.filter(fact => fact.name === focused) ?? []).length ? product?.facts?.filter(fact => fact.name === focused).map((fact, index) => <FactValue key={index} fact={fact} />) : <p>등록된 사양 미확인</p>}<p className="mt-2 text-xs text-zinc-500">{focused === "배터리" ? "사용 조건이 다를 수 있어 재생 시간을 자동으로 우열 비교하지 않습니다." : "아래 필수 조건 판정에는 직접 확인한 값을 우선 반영합니다."}</p></div>}
        {checks.length === 0 && <p className="text-sm text-zinc-600">{hasConditions ? "공통 충족 조건은 아래에서 확인할 수 있습니다." : "필수 조건을 선택하면 후보별 차이와 미확인 사항을 보여줍니다."}</p>}
        {checks.map(check => <div key={check.id} className="space-y-2"><RequirementChecks compact checks={[check]} label={`${option.name} ${check.label} 판정`} />
          {check.status === "UNKNOWN" && <p className="text-sm text-amber-900">이 조건을 충족하는지 아직 판단할 수 없습니다. 출처에서 확인하거나 이 후보를 보류하세요.</p>}
          {onConfirm && check.id !== "budget" && <button className={button} onClick={() => onConfirm(option.id!, check.id === "anc" ? "anc" : data.settings!.compatibility!)}>{check.label} 직접 확인·수정</button>}
          {check.id === "budget" && check.status === "UNKNOWN" && <p className="text-xs text-zinc-600">아래 ‘비교 값 확인과 평가’에서 직접 확인한 가격을 입력할 수 있습니다.</p>}
        </div>)}
        {onDefer && <button className={button} onClick={() => onDefer(option.id!)}>이 후보 비교 보류</button>}
      </article>;
    })}</div>
    {common.length > 0 && <details className="rounded-2xl border p-4"><summary className="cursor-pointer font-medium">공통으로 충족하는 조건 ({common.length})</summary><div className="mt-3 grid gap-4 md:grid-cols-2">{visible.map(option => <div key={option.id}><h3 className="mb-2 font-medium">{option.name}</h3><RequirementChecks compact checks={data.options?.find(item => item.optionId === option.id)?.checks?.filter(check => commonIds.has(check.id))} />{onConfirm && common.filter(check => check.id !== "budget").map(check => <button key={check.id} className={`${button} mt-2`} onClick={() => onConfirm(option.id!, check.id === "anc" ? "anc" : data.settings!.compatibility!)}>{check.label} 직접 확인·수정</button>)}</div>)}</div></details>}
    {priced.length > 1 && <section className="space-y-2 rounded-2xl bg-zinc-50 p-4" aria-label="추가 비용과 차이"><h3 className="font-semibold">추가 비용과 확인된 차이</h3>{priced.slice(1).map(item => {
      const differences = item.checks?.filter(check => check.id !== "budget" && check.status === "MEETS" && cheapest.checks?.some(other => other.id === check.id && other.status === "BELOW")) ?? [];
      const uncertain = [...(item.checks ?? []), ...(cheapest.checks ?? [])].some(check => check.status === "UNKNOWN");
      return <div key={item.optionId} className="text-sm"><p>{options.find(option => option.id === item.optionId)?.name}: {options.find(option => option.id === cheapest.optionId)?.name}보다 {money(item.price! - cheapest.price!)} {item.price === cheapest.price ? "차이 · 같은 가격" : "추가"}</p><p className="text-zinc-600">{differences.length ? `선택한 조건 중 ${differences.map(check => check.label).join(" · ")}은 이 후보가 충족하고 기준 후보는 미달입니다. 근거와 확인 상태는 위에 표시합니다.` : "선택한 필수 조건에서는 추가 비용에 대응하는 지원 차이가 확인되지 않았습니다."}{uncertain && " 미확인 조건이 있어 차이가 없다는 뜻은 아닙니다."}</p></div>;
    })}<p className="text-xs text-zinc-500">가격이 확인된 비교 중 후보끼리만 계산합니다. 추가 비용의 가치와 음질·착용감의 우열은 단정하지 않습니다.</p></section>}
    {(data.settings?.deferredOptionIds?.length ?? 0) > 0 && <div className="space-y-2 border-t pt-3"><h3 className="font-medium">보류한 후보</h3>{options.filter(option => data.settings?.deferredOptionIds?.includes(option.id!)).map(option => <div key={option.id} className="flex items-center justify-between gap-3 text-sm"><span>{option.name} · 후보와 확인 기록 보존</span>{onDefer && <button className={button} onClick={() => onDefer(option.id!)}>비교 복원</button>}</div>)}</div>}
  </div>;
}

export function ResolutionSnapshots({ decision }: { decision: DecisionResponse }) {
  if (!decision.resolutions?.length) return null;
  return <section className="space-y-3 rounded-3xl border bg-white p-5"><h2 className="text-xl font-semibold">결정 당시 기록</h2>{decision.resolutions.map(resolution => {
    const snapshot: Snapshot | undefined = resolution.comparisonSnapshot;
    return <details key={resolution.id} className="rounded-xl border p-4"><summary className="cursor-pointer font-medium">{resolution.createdAt?.slice(0, 10)} · {resolution.kind === "CHOSEN" ? "선택" : resolution.kind === "DEFERRED" ? "보류" : "구매하지 않음"} · {resolution.supersededAt ? "정정된 이전 기록" : resolution.editMode === "CORRECTION" ? "기록 수정" : resolution.editMode === "RECONSIDERATION" ? "재검토 결과" : "결정 기록"} · {resolution.rationale || "이유 미입력"}</summary><div className="mt-4 space-y-3">{decision.outcomes?.filter(outcome => outcome.resolutionId === resolution.id).map(outcome => <p key={outcome.id} className="text-sm">이 기록의 회고 · 만족도 {outcome.satisfaction} · {outcome.notes}</p>)}{snapshot ? <>
      <p className="text-sm">{snapshot.options?.find(option => option.id === resolution.selectedOptionId)?.name ?? "선택한 후보 없음"} · 당시 근거를 보존한 기록입니다.</p>
      {snapshot.title && <p className="text-sm">당시 제목: {snapshot.title}</p>}
      {snapshot.context && <p className="whitespace-pre-wrap text-sm">당시 목적: {snapshot.context.intendedUse || "미입력"} · 예산 메모: {snapshot.context.budget ?? "미입력"} {snapshot.context.currency}<br />{snapshot.context.notes}</p>}
      {snapshot.comparison && <ComparisonSummary data={snapshot.comparison} options={snapshot.options} products={snapshot.productFacts} />}
      <details><summary className="cursor-pointer text-sm">당시 입력한 평가 기준과 값</summary><ul className="mt-2 space-y-2 text-sm">{snapshot.options?.map(option => <li key={option.id}>{option.name}: {snapshot.criteria?.filter(criterion => criterion.active).map(criterion => { const value = snapshot.values?.find(value => value.optionId === option.id && value.criterionId === criterion.id); return `${criterion.name} ${value?.numericValue ?? value?.rating ?? "미확인"} (중요도 ${criterion.weight})`; }).join(" · ")}</li>)}</ul></details>
    </> : <p className="text-sm text-zinc-500">이전 버전의 기록으로 당시 조건과 근거의 고정본이 없습니다.</p>}</div></details>;
  })}{decision.outcomes?.filter(outcome => !outcome.resolutionId).map(outcome => <p key={outcome.id} className="text-sm text-zinc-500">이전 버전의 회고 · 연결된 결정 기록 확인 불가 · 만족도 {outcome.satisfaction} · {outcome.notes}</p>)}</section>;
}
