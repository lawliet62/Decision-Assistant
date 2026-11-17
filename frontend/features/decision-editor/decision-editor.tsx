"use client";
import { ReasonPicker, PastDecisions } from "./decision-reasons";


import { useEffect, useRef, useState } from "react";
import { api } from "@/shared/api/client";
import type { AiCapabilitiesResponse, DecisionResponse, EvaluationResponse, UpdateDraftRequest } from "@/shared/api/types";
import { EvaluationResultView } from "@/features/evaluation-result/evaluation-result";

import { ProductFacts } from "./product-facts";
import { ComparisonWorkspace, ComparisonSummary, ResolutionSnapshots } from "./comparison-workspace";
import { DiscoveryCandidates } from "./discovery-candidates";
import { OfficialPolicyNotice } from "@/features/product-lookup/official-policy-notice";
import Link from "next/link";

import { ProductPicker, type SelectedSource } from "@/features/product-lookup/product-picker";

type Problem = { detail?: string; violations?: string[] };
type DraftValue = NonNullable<UpdateDraftRequest["values"]>[number];
const discoveryEmptyReasons: Record<string, string> = {
  NO_OFFICIAL_RESULTS: "이번 검색에서 공식 판매처의 제품 페이지를 찾지 못했습니다. 조건을 바꾸거나 제품을 직접 추가할 수 있습니다.",
  CATALOG_EMPTY: "등록된 제품을 준비 중입니다. 잠시 후 다시 확인하세요.",
  NO_VERIFIED_OFFERS: "찾은 공식 판매처 페이지에서 제품과 비교 가능한 가격을 확인하지 못했습니다. 제품을 직접 추가할 수 있습니다.",
  NO_NEW_PRODUCTS: "이번 검색에서는 이전 추천을 제외한 새 제품을 찾지 못했습니다. 이전 추천을 비교하거나 제품을 직접 추가할 수 있습니다.",
};
const discoveryStages: Record<string, string> = {
  QUEUED: "추천을 준비하고 있습니다…", CONFIGURATION: "추천 조건을 확인하고 있습니다…", SEARCH: "공식 판매처에서 제품을 찾고 있습니다…",
  COLLECTION: "제품의 가격과 사양을 가져오고 있습니다…", AI: "수집한 제품 정보를 정리하고 있습니다…", VALIDATION: "조건과 근거를 확인하고 있습니다…", PERSISTENCE: "추천 결과를 저장하고 있습니다…",
};

export function DecisionEditor({ initialDecision }: { initialDecision: DecisionResponse }) {
  const [decision, setDecision] = useState(initialDecision);
  const [draftTitle, setDraftTitle] = useState(initialDecision.title ?? "");
  const [draftContext, setDraftContext] = useState(initialDecision.context ?? {});
  const [optionDraft, setOptionDraft] = useState(initialDecision.options ?? []);
  const [values, setValues] = useState<DraftValue[]>(() => initialDecision.values ?? []);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [stage, setStage] = useState(["CHOSEN", "ABANDONED", "DEFERRED"].includes(initialDecision.status ?? "") ? 2 : 0);
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const dirtyRef = useRef(false);
  const [comparisonDirty, setComparisonDirty] = useState(false);
  const comparisonDirtyRef = useRef(false);
  function comparisonEditing(editing: boolean) { comparisonDirtyRef.current = editing; setComparisonDirty(editing); }
  const busyRef = useRef(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [criterionDraft, setCriterionDraft] = useState(initialDecision.criteria ?? []);
  const [constraints, setConstraints] = useState(initialDecision.constraints ?? []);
  const [constraintResults, setConstraintResults] = useState(initialDecision.constraintResults ?? []);
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();
  const [evaluation, setEvaluation] = useState<EvaluationResponse>();
  const [capabilities, setCapabilities] = useState<AiCapabilitiesResponse>();
  const [includeLlm, setIncludeLlm] = useState(false);
  const [satisfaction, setSatisfaction] = useState(4);
  const [expectationStatus, setExpectationStatus] = useState<"MET" | "PARTIALLY_MET" | "NOT_MET" | "TOO_EARLY">("TOO_EARLY");
  const [wouldChooseAgain, setWouldChooseAgain] = useState<boolean>();
  const [workedWell, setWorkedWell] = useState("");
  const [disappointedBy, setDisappointedBy] = useState("");
  const [outcomeNotes, setOutcomeNotes] = useState("");
  const [notPurchased, setNotPurchased] = useState(false);
  const currentRecord = decision.resolutions?.find(r => r.id === decision.currentResolutionId) ?? decision.resolutions?.[0];
  async function cancelRevision() {
    const { data, error } = await api.POST("/api/v1/decisions/{decisionId}/revisions/cancel", { params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.version! } });
    if (!data || error) { setError(problemMessage(error)); return; } apply(data); setStage(2);
  }
  async function recordPurchase(status: "UNRECORDED" | "PLANNED" | "PURCHASED" | "NOT_PURCHASED") {
    const { data, error } = await api.PUT("/api/v1/decisions/{decisionId}/purchase", { params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.version!, resolutionId: currentRecord!.id!, status } });
    if (!data || error) { setError(problemMessage(error)); return; } apply(data);
  }

  const guidance = decision.guidance;
  const options = optionDraft;
  const completed = ["CHOSEN", "DEFERRED", "ABANDONED"].includes(decision.status ?? "");
  const criteria = criterionDraft.filter((item) => item.active);
  const pending = (guidance?.processingStatus === "IMPORTING" || ["PENDING", "RUNNING"].includes(decision.discovery?.status ?? "")) && (decision.status === "DRAFT");
  const editable = decision.status === "DRAFT";
  const llmAvailable = Boolean(capabilities?.enabled) && (!guidance || guidance.aiConsent);

  useEffect(() => { api.GET("/api/v1/ai/capabilities").then(({ data }) => setCapabilities(data)).catch(() => {}); }, []);
  useEffect(() => {
    if (!pending) return;
    const timer = window.setInterval(async () => {
      if (dirtyRef.current || comparisonDirtyRef.current || busyRef.current) return;
      try {
        const { data } = await api.GET("/api/v1/decisions/{decisionId}", { params: { path: { decisionId: decision.id! } } });
        if (data && !dirtyRef.current && !comparisonDirtyRef.current && !busyRef.current) {
          setDecision(data); setDraftTitle(data.title ?? ""); setDraftContext(data.context ?? {}); setOptionDraft(data.options ?? []); setCriterionDraft(data.criteria ?? []); setValues(data.values ?? []); setConstraints(data.constraints ?? []);
          setConstraintResults(data.constraintResults ?? []);
        }
      } catch { /* Keep the current draft on a transient polling failure. */ }
    }, 2500);
    return () => window.clearInterval(timer);
  }, [pending, decision.id]);

  async function refreshDecision() {
    const { data } = await api.GET("/api/v1/decisions/{decisionId}", { params: { path: { decisionId: decision.id! } } });
    if (data) apply(data);
  }
  function clearFeedback() { setMessage(undefined); setError(undefined); }
  function apply(next?: DecisionResponse) {
    if (next) {
      setDecision(next); setDraftTitle(next.title ?? ""); setDraftContext(next.context ?? {}); setOptionDraft(next.options ?? []); setCriterionDraft(next.criteria ?? []); setValues(next.values ?? []); setConstraints(next.constraints ?? []);
      setConstraintResults(next.constraintResults ?? []); dirtyRef.current = false; setDirty(false); setEvaluation(undefined);
    }
  }
  function markDirty() { dirtyRef.current = true; setDirty(true); setEvaluation(undefined); }
  async function run(action: () => Promise<void>, allowDirty = false) {
    if (busyRef.current) return;
    if (dirtyRef.current && !allowDirty) { setError("편집한 값을 먼저 저장하거나 변경을 취소하세요."); return; }
    busyRef.current = true; setBusy(true);
    try { await action(); }
    catch { setError("연결이 끊겼습니다. 현재 입력은 유지됩니다. 다시 불러와 저장 여부를 확인하세요."); }
    finally { busyRef.current = false; setBusy(false); }
  }

  async function answerQuestion(questionId: string) {
    clearFeedback();
    const answer = answers[questionId]?.trim();
    if (!answer) return setError("질문에 답을 입력하세요.");
    const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/questions/{questionId}/answer", {
      params: { path: { decisionId: decision.id!, questionId } }, body: { answer },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    apply(data); setMessage("답을 결정 문서에 반영했습니다.");
  }

  async function addSource(source: SelectedSource) {
    if (busyRef.current || dirtyRef.current) throw new Error("현재 편집을 먼저 저장하세요.");
    busyRef.current = true; setBusy(true); clearFeedback();
    try {
      const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/sources", {
        params: { path: { decisionId: decision.id! } }, body: source,
      });
      if (apiError || !data) throw new Error(problemMessage(apiError));
      apply(data); setMessage("후보를 추가했습니다.");
    } finally { busyRef.current = false; setBusy(false); }
  }

  async function retryEvidence(evidenceId: string) {
    clearFeedback();
    const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/evidence/{evidenceId}/retry", {
      params: { path: { decisionId: decision.id!, evidenceId } },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    apply(data); setMessage("후보 정보 수집을 다시 시작했습니다.");
  }

  async function skipReminder(reminderId: string) {
    clearFeedback();
    const { error: apiError } = await api.PATCH("/api/v1/decisions/{decisionId}/reminders/{reminderId}", {
      params: { path: { decisionId: decision.id!, reminderId } }, body: { action: "SKIP" },
    });
    if (apiError) return setError(problemMessage(apiError));
    await refreshDecision(); setMessage("이번 회고 reminder를 건너뜁니다.");
  }

  async function updateSuggestion(suggestionId: string, action: "ACCEPT" | "DISMISS") {
    clearFeedback();
    const { data, error: apiError } = await api.PATCH("/api/v1/decisions/{decisionId}/candidate-suggestions/{suggestionId}", {
      params: { path: { decisionId: decision.id!, suggestionId } }, body: { action },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    apply(data); setMessage(action === "ACCEPT" ? "대안을 후보로 추가했습니다." : "대안을 제외했습니다.");
  }

  function setCell(optionId: string, criterionId: string, field: "numericValue" | "rating", raw: string) {
    markDirty();
    const parsed = raw === "" ? undefined : Number(raw);
    setValues((current) => {
      const index = current.findIndex((item) => item.optionId === optionId && item.criterionId === criterionId);
      const next = [...current];
      const base: DraftValue = index >= 0 ? next[index] : { id: crypto.randomUUID(), optionId, criterionId, certainty: "CONFIRMED", origin: "USER", verificationStatus: "CONFIRMED" };
      const updated = { ...base, [field]: parsed, certainty: "CONFIRMED" as const, origin: "USER" as const, verificationStatus: "CONFIRMED" as const };
      if (field === "numericValue") delete updated.rating; else delete updated.numericValue;
      if (index >= 0) next[index] = updated; else next.push(updated);
      return next;
    });
  }

  async function saveComparison() {
    clearFeedback();
    const body: UpdateDraftRequest = { expectedVersion: decision.version ?? 0, title: draftTitle,
      context: draftContext, options: optionDraft, criteria: criterionDraft, values,
      constraints, constraintResults };
    const { data, error: apiError } = await api.PUT("/api/v1/decisions/{decisionId}/draft", {
      params: { path: { decisionId: decision.id! } }, body,
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    apply(data); setMessage("검증 입력을 저장했습니다.");
  }

  async function verifyValue(valueId: string) {
    clearFeedback();
    const { data, error: apiError } = await api.PATCH("/api/v1/decisions/{decisionId}/values/{valueId}/verification", {
      params: { path: { decisionId: decision.id!, valueId } }, body: { disputed: false },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    apply(data); setMessage("근거 값을 확인했습니다.");
  }

  async function evaluate() {
    clearFeedback();
    const include = includeLlm && llmAvailable;
    const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/evaluations", {
      params: { path: { decisionId: decision.id! } },
      body: { expectedVersion: decision.version!, includeLlm: include, aiConsent: include, sensitiveDataAcknowledged: include && acknowledged },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    setEvaluation(data);
  }

  async function resolveDecision(kind: "CHOSEN" | "DEFERRED" | "ABANDONED", selectedOptionId: string, rationale: string, reasonIds: string[], primaryReasonId?: string) {
    if (comparisonDirtyRef.current) { setError("비교 조건을 먼저 저장하세요."); return; }
    clearFeedback();
    const { error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/resolutions", {
      params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.version, kind, reasonIds, primaryReasonId, selectedOptionId: kind === "CHOSEN" ? selectedOptionId : undefined, rationale },
    });
    if (apiError) return setError(problemMessage(apiError));
    await refreshDecision(); setStage(2); setMessage("선택 상태를 기록했습니다.");
  }

  async function beginRevision(mode: "CORRECTION" | "RECONSIDERATION") {
    clearFeedback();
    const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/revisions", {
      params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.version!, mode, notPurchased: true },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    apply(data); setNotPurchased(false); setStage(0);
    setMessage(mode === "CORRECTION" ? "기록 수정을 시작했습니다. 수정 후 결정 결과를 다시 저장하세요." : "다시 검토를 시작했습니다. 이전 결정 기록은 그대로 남습니다.");
  }

  async function addOutcome() {
    clearFeedback();
    const { error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/outcome-check-ins", {
      params: { path: { decisionId: decision.id! } },
      body: { satisfaction, expectationStatus, notes: outcomeNotes, wouldChooseAgain, workedWell: decision.status === "CHOSEN" ? workedWell : undefined, disappointedBy: decision.status === "CHOSEN" ? disappointedBy : undefined, resolutionId: decision.currentResolutionId, expectedVersion: decision.version },
    });
    if (apiError) return setError(problemMessage(apiError));
    await refreshDecision(); setOutcomeNotes(""); setMessage("사용 후 회고를 저장했습니다.");
  }

  return <fieldset disabled={busy} className="mt-6 min-w-0 space-y-6">
    <header className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-medium text-emerald-700">{templateLabels[decision.templateKey ?? "CUSTOM"]} · {statusLabels[decision.status ?? "DRAFT"]}</p><h1 className="mt-1 text-3xl font-semibold tracking-tight">{decision.title}</h1></div><span className={`rounded-full px-3 py-1 text-xs font-semibold ${guidance?.readiness === "VERIFIED_READY" ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-800"}`}>{guidance?.readiness === "VERIFIED_READY" ? "검증 준비됨" : "잠정 비교"}</span></header>
    {(message || error) && <div className={`rounded-xl p-4 text-sm ${error ? "bg-red-50 text-red-800" : "bg-emerald-50 text-emerald-800"}`}>{error ?? message}</div>}
    {completed && <Section title="결정을 완료했습니다" description="필수 절차는 여기서 끝납니다. 구매 기록과 회고는 원할 때 남길 수 있습니다.">
<Link href="/" className={primarySmall}>내 결정으로 돌아가기</Link>
{decision.status === "CHOSEN" && <div className="space-y-2"><p>구매 상태: {({ UNRECORDED: "미기록", PLANNED: "구매 예정", PURCHASED: "구매함", NOT_PURCHASED: "구매하지 않기로 함" })[currentRecord?.purchaseStatus ?? "UNRECORDED"]}</p>{currentRecord?.purchaseStatus !== "PURCHASED" && <div className="flex flex-wrap gap-2"><button className={secondaryButton} onClick={() => run(() => recordPurchase("PLANNED"))}>구매 예정</button><button className={secondaryButton} onClick={() => run(() => recordPurchase("PURCHASED"))}>구매함</button><button className={secondaryButton} onClick={() => run(() => recordPurchase("NOT_PURCHASED"))}>구매하지 않기로 함</button></div>}</div>}
{currentRecord?.purchaseStatus !== "PURCHASED" && <><label className="flex gap-2"><input type="checkbox" checked={notPurchased} onChange={e => setNotPurchased(e.target.checked)} />아직 구매하지 않아 다시 검토합니다</label><div className="flex flex-wrap gap-3"><button disabled={!notPurchased} className={secondaryButton} onClick={() => run(() => beginRevision("CORRECTION"))}>기록 수정</button><button disabled={!notPurchased} className={secondaryButton} onClick={() => run(() => beginRevision("RECONSIDERATION"))}>다시 검토</button></div></>}
</Section>}
{decision.editMode && <button className={secondaryButton} disabled={comparisonDirty} onClick={() => run(cancelRevision, true)}>재검토 취소 · 이전 결론 유지</button>}
{editable && <PastDecisions hasUnsavedChanges={dirty || comparisonDirty} key={decision.version} id={decision.id!} onApply={note => { markDirty(); setDraftContext({ ...draftContext, notes: [draftContext.notes, note].filter(Boolean).join("\n") }); setMessage("과거 기록을 이번 메모에 추가했습니다. 변경 저장으로 보존하세요."); }} />}
    {decision.editMode && <p role="status" className="rounded-xl bg-amber-50 p-4">{decision.editMode === "CORRECTION" ? "기록 수정 중 · 변경 내용을 저장한 뒤 후보 선택 또는 구매하지 않음으로 완료하세요. 이전 기록에 기반한 개인화는 잠시 중단됩니다." : "다시 검토 중 · 이전 판단과 회고를 보존하면서 새 결정을 준비합니다."}</p>}

    <nav aria-label="비교 단계" className="flex flex-wrap gap-2">{["1. 비교 준비", "2. 비교·평가", "3. 선택 기록", "선택 사항 · 회고"].map((label, index) => <button key={label} disabled={dirty || comparisonDirty || (index === 3 && !completed)} aria-current={stage === index ? "step" : undefined} onClick={() => setStage(index)} className={stage === index ? primarySmall : secondaryButton}>{label}</button>)}</nav>
    {dirty && <div role="status" className="rounded-xl bg-amber-50 p-3 text-sm">저장하지 않은 변경이 있습니다. 자동 갱신은 잠시 멈췄습니다. <button onClick={() => run(saveComparison, true)} className={primarySmall}>변경 저장</button> <button onClick={() => run(refreshDecision, true)} className={secondaryButton}>변경 취소·최신 내용 불러오기</button></div>}
    {stage === 0 && <>
    {editable && <Section title="기본 기록 수정" description="제목, 조건과 후보 이름의 입력 실수를 정정할 수 있습니다.">
      <label className="block text-sm">제목<input className={inputClass + " w-full"} value={draftTitle} onChange={e => { markDirty(); setDraftTitle(e.target.value); }} /></label>
      <label className="block text-sm">사용 목적<input className={inputClass + " w-full"} value={draftContext.intendedUse ?? ""} onChange={e => { markDirty(); setDraftContext({ ...draftContext, intendedUse: e.target.value }); }} /></label>
      <label className="block text-sm">예산 메모<input type="number" min="0" className={inputClass} value={draftContext.budget ?? ""} onChange={e => { markDirty(); setDraftContext({ ...draftContext, budget: e.target.value === "" ? undefined : Number(e.target.value) }); }} /></label>
      <label className="block text-sm">메모<textarea aria-label="메모" className={inputClass + " w-full"} value={draftContext.notes ?? ""} onChange={e => { markDirty(); setDraftContext({ ...draftContext, notes: e.target.value }); }} /></label>
      {options.map(option => <label key={option.id} className="block text-sm">후보 이름<input className={inputClass + " w-full"} value={option.name ?? ""} onChange={e => { markDirty(); setOptionDraft(options.map(item => item.id === option.id ? { ...item, name: e.target.value } : item)); }} /></label>)}
    </Section>}
    {decision.discovery && <Section title="추천 제품" description="조건에 맞춰 찾은 후보입니다. 주요 정보와 출처를 확인하고 비교할 제품을 선택하세요.">
      <OfficialPolicyNotice version={decision.discovery.policyVersion} placement="result" />
      <p className="text-sm text-zinc-600">{guidance?.intentText}</p>
      {pending && <p role="status" className="text-sm text-emerald-800">{discoveryStages[decision.discovery.stage ?? ""] ?? "제품 정보를 찾고 있습니다…"}</p>}
      {decision.discovery.emptyReason && <p role="status" className="text-sm text-zinc-600">{discoveryEmptyReasons[decision.discovery.emptyReason] ?? "이번 검색에서 추천할 제품을 확인하지 못했습니다. 제품을 직접 추가할 수 있습니다."}</p>}
      {(decision.discovery.generation ?? 0) > (decision.discovery.displayedGeneration ?? 0) && <p className="text-sm text-zinc-600">새 추천을 확보할 때까지 이전 추천 목록과 비교 내용을 유지합니다.</p>}
      {decision.discovery.error && <p role="status" className="text-sm text-amber-800">{decision.discovery.error}</p>}
      {editable && ["FAILED", "PARTIAL"].includes(decision.discovery.status ?? "") && <button className={secondaryButton} onClick={() => run(async () => {
        const { data, error } = await api.POST("/api/v1/decisions/{decisionId}/discovery/retry", { params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.discovery!.version! } });
        if (error) { setError(problemMessage(error)); return; } apply(data);
      })}>추천 다시 시도</button>}
      {editable && <button disabled={pending || busy || dirty} className={secondaryButton} onClick={() => run(async () => {
        clearFeedback();
        const { data, error } = await api.POST("/api/v1/decisions/{decisionId}/discovery/refresh", { params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.discovery!.version! } });
        if (error) { setError(problemMessage(error)); return; } apply(data);
      })}>같은 조건으로 다른 제품 찾기</button>}
      <Link href={"/decisions/new?category=" + decision.templateKey} className="ml-3 text-sm text-emerald-800 underline">조건 바꿔 새로 찾기</Link>
      <DiscoveryCandidates candidates={guidance?.candidateSuggestions ?? []} editable={editable} optionCount={options.length}
        officialPolicy={decision.discovery.policyVersion?.startsWith("official-kr-")}
        pending={pending} hasRequirements={Object.keys(decision.discovery.conditions?.minimumRequirements ?? {}).length > 0}
        onAction={(id, action) => run(() => updateSuggestion(id, action))} />
      <p className="text-sm text-zinc-500">비교에 추가한 제품 {options.length}/5</p>
      {!!decision.discovery.history?.length && <details className="rounded-2xl border border-zinc-200 p-4">
        <summary className="cursor-pointer font-semibold">추천 이력 ({decision.discovery.history.length})</summary>
        <div className="mt-4 space-y-6">{[...decision.discovery.history].reverse().map(round => <section key={round.generation} aria-label={`추천 묶음 ${(round.generation ?? 0) + 1}`} className="space-y-3">
          <h3 className="font-semibold">추천 묶음 {(round.generation ?? 0) + 1}</h3>
          {round.error && <p className="text-sm text-amber-800">{round.error}</p>}
          {round.emptyReason && <p className="text-sm text-zinc-600">새롭게 확인한 제품이 없었습니다.</p>}
          <DiscoveryCandidates candidates={round.candidates ?? []} editable={false} optionCount={options.length} pending={false} hasRequirements={false}
            officialPolicy={round.policyVersion?.startsWith("official-kr-")} onAction={() => {}} />
        </section>)}</div>
      </details>}
    </Section>}
    {!decision.discovery && <Section title="비교 준비 상태" description="수집된 정보와 아직 확인할 내용을 구분합니다.">{pending && <div className="flex items-center gap-3 text-sm text-emerald-800"><span className="h-2 w-2 animate-pulse rounded-full bg-emerald-500" />후보 링크를 읽고 있습니다. 이 화면은 자동으로 갱신됩니다.</div>}<p className="text-lg leading-8 text-zinc-800">{guidance?.summary ?? "기존 Decision입니다. 아래 검증 입력에서 비교를 계속할 수 있습니다."}</p>{(guidance?.appliedPreferences?.length ?? 0) > 0 && <div><p className="text-xs font-semibold uppercase tracking-wide text-zinc-500">과거 결정에서 적용</p><div className="mt-2 flex flex-wrap gap-2">{guidance?.appliedPreferences?.map((item) => <span key={item.id} className="rounded-full bg-violet-50 px-3 py-1 text-xs text-violet-800">{item.statement}</span>)}</div></div>}</Section>}

    {!decision.discovery && editable && (guidance?.questions?.length ?? 0) > 0 && <Section title="지금 확인할 것" description="결론에 영향이 큰 질문만 최대 3개씩 보여드립니다."><div className="grid gap-4">{guidance?.questions?.map((question) => <article key={question.id} className="rounded-2xl border border-zinc-200 p-4"><p className="font-semibold">{question.prompt}</p><p className="mt-1 text-xs text-zinc-500">{question.impact}</p><div className="mt-3 flex flex-wrap gap-2">{(question.options?.length ?? 0) > 0 ? <select value={answers[question.id!] ?? ""} onChange={(event) => setAnswers({ ...answers, [question.id!]: event.target.value })} className={inputClass}><option value="">선택</option>{question.options?.map((option) => <option key={option} value={option}>{option}</option>)}</select> : <input type={question.kind === "NUMBER" ? "number" : "text"} value={answers[question.id!] ?? ""} onChange={(event) => setAnswers({ ...answers, [question.id!]: event.target.value })} className={inputClass} />}<button onClick={() => run(() => answerQuestion(question.id!))} className={primarySmall}>반영</button></div></article>)}</div></Section>}

    {editable && <Section title="후보 추가" description={guidance?.webConsent ? "선택한 URL은 생성 시 동의한 Firecrawl 수집 흐름으로 연결됩니다." : "후보를 저장하고 비교 값을 직접 입력합니다. 이 비교에는 페이지 수집 동의가 없습니다."}>
      <ProductPicker selectedUrls={options.map((option) => option.sourceUrl ?? "")} onSelect={addSource} disabled={busy || dirty || options.length >= 5} />
    </Section>}
    <button onClick={() => setStage(1)} className={primarySmall}>후보 비교로 이동</button>
    </>}
    {stage === 1 && <>
    {decision.comparison && <ComparisonWorkspace decision={decision} disabled={!editable || dirty} apply={apply} run={action => { void run(action); }} onEditing={comparisonEditing} />}
    <details open={!decision.comparison}><summary className="cursor-pointer text-sm font-medium">모든 수집 사양과 최초 추천 조건 보기</summary><div className="mt-3"><ProductFacts decision={decision} /></div></details>


    {!decision.discovery && editable && (guidance?.candidateSuggestions?.length ?? 0) > 0 && <Section title="놓쳤을 수 있는 대안" description="현재 후보와 다른 선택지를 제안합니다. 승인하기 전에는 비교에 들어가지 않습니다."><div className="grid gap-3">{guidance?.candidateSuggestions?.map((item) => <article key={item.id} className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-zinc-200 p-4"><div className="min-w-0"><p className="font-semibold">{item.name}</p><p className="mt-1 text-sm text-zinc-600">{item.reason}</p><a href={item.url} target="_blank" rel="noreferrer" className="mt-1 block truncate text-xs text-emerald-700">{item.url}</a></div><div className="flex gap-2"><button onClick={() => run(() => updateSuggestion(item.id!, "ACCEPT"))} className={primarySmall}>후보에 추가</button><button onClick={() => run(() => updateSuggestion(item.id!, "DISMISS"))} className={secondaryButton}>제외</button></div></article>)}</div></Section>}

    <fieldset disabled={!editable || comparisonDirty} className="min-w-0"><details className="rounded-3xl border border-zinc-200 bg-white shadow-sm"><summary className="cursor-pointer p-6 text-xl font-semibold">비교 값 확인과 평가</summary><div className="space-y-6 border-t border-zinc-100 p-6">    <fieldset disabled={!editable || dirty} className="min-w-0">
    <Section title="후보 비교" description="자동 추출값은 출처와 확인 상태를 함께 표시합니다."><div className="grid gap-4 md:grid-cols-2">{options.map((option) => { const evidence = guidance?.evidence?.find((item) => item.optionId === option.id); return <article key={option.id} className="rounded-2xl border border-zinc-200 p-5"><div className="flex items-start justify-between gap-3"><h3 className="font-semibold text-zinc-950">{option.name}</h3><span className={`text-xs ${evidence?.status === "FAILED" ? "text-red-600" : "text-zinc-500"}`}>{evidence?.status ? evidenceLabels[evidence.status] ?? "확인 중" : "직접 입력"}</span></div>{option.description && <p className="mt-2 text-sm text-zinc-600">{option.description}</p>}<a href={option.sourceUrl} target="_blank" rel="noreferrer" className="mt-3 block truncate text-xs text-emerald-700">{option.sourceUrl}</a>{evidence?.fetchedAt && <p className="mt-1 text-xs text-zinc-400">조회 {new Date(evidence.fetchedAt).toLocaleString("ko-KR")}{evidence.stale && <button onClick={() => run(() => retryEvidence(evidence.id!))} className="ml-2 font-semibold text-amber-700 underline">24시간 경과 · 새로고침</button>}</p>}{evidence?.error && <p className="mt-2 text-xs text-red-700">{evidence.error} <button onClick={() => run(() => retryEvidence(evidence.id!))} className="font-semibold underline">다시 시도</button></p>}<ul className="mt-4 space-y-2 text-sm">{criteria.map((criterion) => { const value = values.find((item) => item.optionId === option.id && item.criterionId === criterion.id); return <li key={criterion.id} className="flex items-center justify-between gap-3"><span className="text-zinc-500">{criterion.name}</span><span className="text-right font-medium">{displayValue(value, criterion.unit)}{value?.verificationStatus === "UNREVIEWED" && <button onClick={() => run(() => verifyValue(value.id!))} className="ml-2 text-xs text-emerald-700">확인</button>}</span></li>; })}</ul></article>; })}</div></Section></fieldset><p className="text-sm text-zinc-500">빈칸은 미확인으로 남깁니다. 1~5점 항목은 본인의 만족 예상이며 사양에서 자동 확정하지 않습니다. 점수는 선형 환산과 가중합에 따른 참고값이지 최적 구매의 보장이 아닙니다.</p><div className="overflow-x-auto"><table className="w-full min-w-[700px] text-sm"><thead><tr><th className="p-2 text-left">후보</th>{criteria.map((item) => <th key={item.id} className="p-2 text-left">{item.name}</th>)}</tr></thead><tbody>{options.map((option) => <tr key={option.id} className="border-t border-zinc-100"><th className="p-2 text-left">{option.name}</th>{criteria.map((criterion) => { const value = values.find((item) => item.optionId === option.id && item.criterionId === criterion.id); return <td key={criterion.id} className="p-2">{criterion.kind === "NUMERIC_UTILITY" ? <input aria-label={`${option.name} ${criterion.name}`} type="number" step="any" value={value?.numericValue ?? ""} onChange={(event) => setCell(option.id!, criterion.id!, "numericValue", event.target.value)} className={inputClass} /> : <select aria-label={`${option.name} ${criterion.name}`} value={value?.rating ?? ""} onChange={(event) => setCell(option.id!, criterion.id!, "rating", event.target.value)} className={inputClass}><option value="">미확인</option>{[1,2,3,4,5].map((rating) => <option key={rating}>{rating}</option>)}</select>}</td>; })}</tr>)}</tbody></table></div><div className="flex flex-wrap items-center gap-3"><button onClick={() => run(saveComparison, true)} className={secondaryButton}>검증 입력 저장</button><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={includeLlm} onChange={(event) => setIncludeLlm(event.target.checked)} disabled={!llmAvailable} /> AI 관점도 별도로 보기</label>{guidance && !guidance.aiConsent && <span className="text-xs text-zinc-500">생성 시 AI 처리에 동의하지 않아 Algorithm만 실행합니다.</span>}<button onClick={() => run(evaluate)} disabled={dirty || (includeLlm && !acknowledged) || (guidance ? guidance.readiness !== "VERIFIED_READY" : false)} className="rounded-full bg-emerald-700 px-5 py-2 text-sm font-semibold text-white disabled:opacity-40">검증된 평가 실행</button></div>
      {includeLlm && <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={acknowledged} onChange={(e) => setAcknowledged(e.target.checked)} /><span>현재 저장된 비교 내용을 {capabilities?.provider} ({capabilities?.model})에 보내는 데 동의하며, 개인정보·민감정보가 없음을 확인합니다.</span></label>}
      <details className="space-y-3"><summary className="cursor-pointer text-sm font-medium">점수 기준과 필수 조건</summary>
        <p className="text-sm text-zinc-600">중요도는 기존 선호 점수의 가중치입니다. 수정 후 저장하고 평가를 다시 실행하면 반영됩니다. 위의 사양 조건 판정을 대체하지 않으므로 미달·미확인을 함께 확인하세요.</p>
        <div className="flex flex-wrap gap-3">{criteria.map(criterion => <label key={criterion.id} className="text-sm">{criterion.name} 중요도<select aria-label={`${criterion.name} 중요도`} className={inputClass} value={criterion.weight} onChange={event => { markDirty(); setCriterionDraft(criterionDraft.map(item => item.id === criterion.id ? { ...item, weight: Number(event.target.value) } : item)); }}>{[...new Set([1, 2, 3, criterion.weight ?? 1])].sort((a,b) => a-b).map(weight => <option key={weight} value={weight}>{weight}</option>)}</select></label>)}</div>
        <ul className="space-y-1 text-xs text-zinc-500">{criteria.map((criterion) => <li key={criterion.id}>{criterion.name}: 중요도 {criterion.weight}{criterion.kind === "NUMERIC_UTILITY" ? ` · 최선 ${criterion.bestAnchor}, 최악 ${criterion.worstAnchor} ${criterion.unit ?? ""}` : " · 1~5점"}</li>)}</ul>
        <p className="text-xs text-zinc-500">예산 메모와 별개입니다. 자동 제외할 최대 예산은 여기서 켜고 금액을 입력하세요. 호환성은 후보별로 직접 확인합니다.</p>
        {constraints.map((constraint, index) => <div key={constraint.id} className="space-y-2 rounded-xl border p-3">
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={Boolean(constraint.active)} onChange={(e) => { markDirty(); setConstraints(constraints.map((item, i) => i === index ? { ...item, active: e.target.checked } : item)); }} />{constraint.name}</label>
          {constraint.active && (constraint.kind === "NUMERIC_MAX" || constraint.kind === "NUMERIC_MIN") && <label className="block text-sm">{constraint.kind === "NUMERIC_MAX" ? "상한" : "하한"} ({constraint.unit ?? "수치"})<input aria-label={`${constraint.name} 기준값`} type="number" min="0" value={constraint.threshold ?? ""} onChange={(e) => { markDirty(); setConstraints(constraints.map((item, i) => i === index ? { ...item, threshold: e.target.value === "" ? undefined : Number(e.target.value) } : item)); }} className={inputClass} /></label>}
          {constraint.active && (constraint.kind === "MANUAL" || constraint.kind === "BOOLEAN_REQUIRED") && options.map((option) => <label key={option.id} className="block text-sm">{option.name}<select aria-label={`${option.name} ${constraint.name}`} value={constraintResults.find((item) => item.optionId === option.id && item.constraintId === constraint.id)?.status ?? "UNKNOWN"} onChange={(e) => {
            markDirty(); const existing = constraintResults.find((item) => item.optionId === option.id && item.constraintId === constraint.id);
            setConstraintResults([...constraintResults.filter((item) => item !== existing), { id: existing?.id ?? crypto.randomUUID(), optionId: option.id!, constraintId: constraint.id!, status: e.target.value as "PASS" | "FAIL" | "UNKNOWN" }]);
          }} className={inputClass}><option value="UNKNOWN">미확인</option><option value="PASS">충족</option><option value="FAIL">미충족</option></select></label>)}
        </div>)}
      </details>
    </div></details></fieldset>
    {evaluation && <EvaluationResultView evaluation={evaluation} />}

    <button disabled={dirty || comparisonDirty} onClick={() => setStage(2)} className={primarySmall}>실제 선택 기록으로 이동</button>
    </>}
    {stage === 2 && <>
    <ResolutionSnapshots decision={decision} />
    {editable && decision.comparison && <Section title="기록할 비교 내용" description="현재 조건과 미확인 사항도 결정 이유와 함께 보존됩니다."><ComparisonSummary data={decision.comparison} options={decision.options} products={decision.productFacts} /></Section>}
    {(decision.resolutions?.length ?? 0) > 0 && <p className="text-sm text-zinc-600">최근 기록: {decision.resolutions?.[0]?.rationale ?? statusLabels[decision.status ?? "DRAFT"]}</p>}
    <fieldset disabled={!editable}>


    </fieldset></>}

    {editable && <fieldset hidden={stage !== 2} disabled={busy || dirty || comparisonDirty}><ReasonPicker key={decision.editMode === "CORRECTION" ? decision.revisionBaseId : "new"} initialResolution={decision.editMode === "CORRECTION" ? decision.resolutions?.find(r => r.id === decision.revisionBaseId) : undefined} options={options} onComplete={(kind, option, note, ids, primary) => run(() => resolveDecision(kind, option, note, ids, primary))} /></fieldset>}
    {stage === 3 && completed && <Section title={decision.status === "ABANDONED" ? "결정 후 회고" : "사용 후 회고"} description={decision.status === "ABANDONED" ? "구매하지 않기로 한 결정에 만족하는지, 예상과 달랐던 점을 남깁니다." : "실제로 써본 만족도와 예상과 달랐던 점을 남깁니다."}><div id="outcome" className="grid gap-3 sm:grid-cols-2"><select value={satisfaction} onChange={(event) => setSatisfaction(Number(event.target.value))} className={inputClass}>{[1,2,3,4,5].map((value) => <option key={value} value={value}>만족도 {value}</option>)}</select><select value={expectationStatus} onChange={(event) => setExpectationStatus(event.target.value as typeof expectationStatus)} className={inputClass}><option value="MET">기대 충족</option><option value="PARTIALLY_MET">일부 충족</option><option value="NOT_MET">미충족</option><option value="TOO_EARLY">아직 판단하기 이른 시점</option></select><select value={wouldChooseAgain === undefined ? "" : String(wouldChooseAgain)} onChange={(event) => setWouldChooseAgain(event.target.value === "" ? undefined : event.target.value === "true")} className={inputClass}><option value="">다시 선택할지</option><option value="true">다시 선택함</option><option value="false">다른 선택을 함</option></select><select hidden={decision.status !== "CHOSEN"} value={workedWell} onChange={(event) => setWorkedWell(event.target.value)} className={inputClass}><option value="">실제로 잘 맞은 기준</option>{decision.status === "CHOSEN" && criteria.map((item) => <option key={item.id}>{item.name}</option>)}</select><select hidden={decision.status !== "CHOSEN"} value={disappointedBy} onChange={(event) => setDisappointedBy(event.target.value)} className={inputClass}><option value="">더 엄격히 볼 기준</option>{decision.status === "CHOSEN" && criteria.map((item) => <option key={item.id}>{item.name}</option>)}</select><input value={outcomeNotes} onChange={(event) => setOutcomeNotes(event.target.value)} className={inputClass} placeholder="예상과 달랐던 점" /></div><button onClick={() => run(addOutcome)} className={primarySmall}>회고 저장</button>{(guidance?.reminders?.length ?? 0) > 0 && <ul className="space-y-1 text-xs text-zinc-500">{guidance?.reminders?.map((item) => <li key={item.id} className="flex items-center gap-2">{new Date(item.dueAt!).toLocaleDateString("ko-KR")} · {item.channel} · {item.status}{item.status === "PENDING" && <button onClick={() => run(() => skipReminder(item.id!))} className="text-red-600 underline">건너뛰기</button>}</li>)}</ul>}{(decision.outcomes?.length ?? 0) > 0 && <ul className="space-y-2 border-t border-zinc-100 pt-4 text-sm text-zinc-600">{decision.outcomes?.filter(item => item.resolutionId === decision.currentResolutionId).map((item) => <li key={item.id}>{item.createdAt?.slice(0,10)} · 만족도 {item.satisfaction} · {item.expectationStatus} {item.notes && `· ${item.notes}`}</li>)}</ul>}</Section>}
  </fieldset>;
}

function Section({ title, description, children }: { title: string; description: string; children: React.ReactNode }) { return <section className="space-y-5 rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm"><div><h2 className="text-xl font-semibold">{title}</h2><p className="mt-1 text-sm text-zinc-500">{description}</p></div>{children}</section>; }
function displayValue(value: DraftValue | undefined, unit?: string) { if (!value || (value.numericValue == null && value.rating == null)) return <span className="text-zinc-400">미확인</span>; return `${value.numericValue ?? `${value.rating}/5`}${value.numericValue != null && unit ? ` ${unit}` : ""}`; }
function problemMessage(error: unknown): string { const problem = error as Problem | undefined; return problem?.violations?.join(" · ") ?? problem?.detail ?? "요청을 처리하지 못했습니다."; }
const inputClass = "min-w-0 flex-1 rounded-xl border border-zinc-300 bg-white px-3 py-2 outline-none focus:border-emerald-500";
const secondaryButton = "rounded-full border border-zinc-300 bg-white px-4 py-2 text-sm font-medium text-zinc-800 hover:border-zinc-500 disabled:opacity-40";
const primarySmall = "rounded-full bg-zinc-950 px-4 py-2 text-sm font-semibold text-white hover:bg-zinc-800 disabled:opacity-40";
const templateLabels = { LAPTOP: "노트북", PHONE: "휴대폰", TABLET: "태블릿", MONITOR: "모니터", EARBUDS: "무선 이어폰", CUSTOM: "직접 비교" };
const statusLabels = { DRAFT: "비교 중", DEFERRED: "결정 완료 · 보류", CHOSEN: "결정 완료 · 후보 선택", ABANDONED: "결정 완료 · 구매하지 않음", ARCHIVED: "보관됨" };
const evidenceLabels: Record<string, string> = { PENDING: "수집 대기", RUNNING: "수집 중", SUCCEEDED: "수집 완료", FAILED: "수집 실패" };
