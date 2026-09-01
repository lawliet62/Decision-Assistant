"use client";

import { useEffect, useState } from "react";
import { api } from "@/shared/api/client";
import type { AiCapabilitiesResponse, DecisionResponse, EvaluationResponse, UpdateDraftRequest } from "@/shared/api/types";
import { EvaluationResultView } from "@/features/evaluation-result/evaluation-result";

type Problem = { detail?: string; violations?: string[] };
type DraftValue = NonNullable<UpdateDraftRequest["values"]>[number];

export function DecisionEditor({ initialDecision }: { initialDecision: DecisionResponse }) {
  const [decision, setDecision] = useState(initialDecision);
  const [values, setValues] = useState<DraftValue[]>(() => initialDecision.values ?? []);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [newUrl, setNewUrl] = useState("");
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();
  const [evaluation, setEvaluation] = useState<EvaluationResponse>();
  const [capabilities, setCapabilities] = useState<AiCapabilitiesResponse>();
  const [includeLlm, setIncludeLlm] = useState(false);
  const [selectedOptionId, setSelectedOptionId] = useState(initialDecision.options?.[0]?.id ?? "");
  const [rationale, setRationale] = useState("");
  const [satisfaction, setSatisfaction] = useState(4);
  const [expectationStatus, setExpectationStatus] = useState<"MET" | "PARTIALLY_MET" | "NOT_MET" | "TOO_EARLY">("TOO_EARLY");
  const [wouldChooseAgain, setWouldChooseAgain] = useState<boolean>();
  const [workedWell, setWorkedWell] = useState("");
  const [disappointedBy, setDisappointedBy] = useState("");
  const [outcomeNotes, setOutcomeNotes] = useState("");

  const guidance = decision.guidance;
  const options = decision.options ?? [];
  const criteria = (decision.criteria ?? []).filter((item) => item.active);
  const pending = guidance?.processingStatus === "IMPORTING";
  const llmAvailable = Boolean(capabilities?.enabled) && (!guidance || guidance.aiConsent);

  useEffect(() => { api.GET("/api/v1/ai/capabilities").then(({ data }) => setCapabilities(data)); }, []);
  useEffect(() => {
    if (!pending) return;
    const timer = window.setInterval(async () => {
      const { data } = await api.GET("/api/v1/decisions/{decisionId}", { params: { path: { decisionId: decision.id! } } });
      if (data) { setDecision(data); setValues(data.values ?? []); }
    }, 2500);
    return () => window.clearInterval(timer);
  }, [pending, decision.id]);

  async function refreshDecision() {
    const { data } = await api.GET("/api/v1/decisions/{decisionId}", { params: { path: { decisionId: decision.id! } } });
    if (data) { setDecision(data); setValues(data.values ?? []); }
  }
  function clearFeedback() { setMessage(undefined); setError(undefined); }
  function apply(next?: DecisionResponse) { if (next) { setDecision(next); setValues(next.values ?? []); } }

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

  async function addSource() {
    clearFeedback();
    if (!newUrl.trim()) return;
    const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/sources", {
      params: { path: { decisionId: decision.id! } }, body: { url: newUrl.trim() },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    setNewUrl(""); apply(data); setMessage("후보 링크를 추가했습니다.");
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
    const body: UpdateDraftRequest = { expectedVersion: decision.version ?? 0, title: decision.title ?? "Decision",
      context: decision.context ?? {}, options: decision.options ?? [], criteria: decision.criteria ?? [], values,
      constraints: decision.constraints ?? [], constraintResults: decision.constraintResults ?? [] };
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
      body: { expectedVersion: decision.version!, includeLlm: include, aiConsent: include, sensitiveDataAcknowledged: include },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    setEvaluation(data);
  }

  async function resolveDecision(kind: "CHOSEN" | "DEFERRED" | "ABANDONED") {
    clearFeedback();
    const { error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/resolutions", {
      params: { path: { decisionId: decision.id! } }, body: { kind, selectedOptionId: kind === "CHOSEN" ? selectedOptionId : undefined, rationale },
    });
    if (apiError) return setError(problemMessage(apiError));
    await refreshDecision(); setMessage("실제 선택을 기록했습니다. 14일과 60일 뒤 회고를 알려드릴게요.");
  }

  async function addOutcome() {
    clearFeedback();
    const { error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/outcome-check-ins", {
      params: { path: { decisionId: decision.id! } },
      body: { satisfaction, expectationStatus, notes: outcomeNotes, wouldChooseAgain, workedWell, disappointedBy },
    });
    if (apiError) return setError(problemMessage(apiError));
    await refreshDecision(); setOutcomeNotes(""); setMessage("회고를 기록하고 검토할 성향 기억을 만들었습니다.");
  }

  return <div className="mt-6 space-y-6">
    <header className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-medium text-emerald-700">{decision.templateKey} · {decision.status}</p><h1 className="mt-1 text-3xl font-semibold tracking-tight">{decision.title}</h1></div><span className={`rounded-full px-3 py-1 text-xs font-semibold ${guidance?.readiness === "VERIFIED_READY" ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-800"}`}>{guidance?.readiness === "VERIFIED_READY" ? "검증 준비됨" : "잠정 비교"}</span></header>
    {(message || error) && <div className={`rounded-xl p-4 text-sm ${error ? "bg-red-50 text-red-800" : "bg-emerald-50 text-emerald-800"}`}>{error ?? message}</div>}

    <Section title="현재 조언" description="확인된 근거와 적용된 성향만으로 만든 현재 상태입니다.">{pending && <div className="flex items-center gap-3 text-sm text-emerald-800"><span className="h-2 w-2 animate-pulse rounded-full bg-emerald-500" />후보 링크를 읽고 있습니다. 이 화면은 자동으로 갱신됩니다.</div>}<p className="text-lg leading-8 text-zinc-800">{guidance?.summary ?? "기존 Decision입니다. 아래 검증 입력에서 비교를 계속할 수 있습니다."}</p>{(guidance?.appliedPreferences?.length ?? 0) > 0 && <div><p className="text-xs font-semibold uppercase tracking-wide text-zinc-500">과거 결정에서 적용</p><div className="mt-2 flex flex-wrap gap-2">{guidance?.appliedPreferences?.map((item) => <span key={item.id} className="rounded-full bg-violet-50 px-3 py-1 text-xs text-violet-800">{item.statement}</span>)}</div></div>}</Section>

    {(guidance?.questions?.length ?? 0) > 0 && <Section title="지금 확인할 것" description="결론에 영향이 큰 질문만 최대 3개씩 보여드립니다."><div className="grid gap-4">{guidance?.questions?.map((question) => <article key={question.id} className="rounded-2xl border border-zinc-200 p-4"><p className="font-semibold">{question.prompt}</p><p className="mt-1 text-xs text-zinc-500">{question.impact}</p><div className="mt-3 flex flex-wrap gap-2">{(question.options?.length ?? 0) > 0 ? <select value={answers[question.id!] ?? ""} onChange={(event) => setAnswers({ ...answers, [question.id!]: event.target.value })} className={inputClass}><option value="">선택</option>{question.options?.map((option) => <option key={option} value={option}>{option}</option>)}</select> : <input type={question.kind === "NUMBER" ? "number" : "text"} value={answers[question.id!] ?? ""} onChange={(event) => setAnswers({ ...answers, [question.id!]: event.target.value })} className={inputClass} />}<button onClick={() => answerQuestion(question.id!)} className={primarySmall}>반영</button></div></article>)}</div></Section>}

    <Section title="후보 비교" description="자동 추출값은 출처와 확인 상태를 함께 표시합니다."><div className="grid gap-4 md:grid-cols-2">{options.map((option) => { const evidence = guidance?.evidence?.find((item) => item.optionId === option.id); return <article key={option.id} className="rounded-2xl border border-zinc-200 p-5"><div className="flex items-start justify-between gap-3"><h3 className="font-semibold text-zinc-950">{option.name}</h3><span className={`text-xs ${evidence?.status === "FAILED" ? "text-red-600" : "text-zinc-500"}`}>{evidence?.status ?? "직접 입력"}</span></div>{option.description && <p className="mt-2 text-sm text-zinc-600">{option.description}</p>}<a href={option.sourceUrl} target="_blank" rel="noreferrer" className="mt-3 block truncate text-xs text-emerald-700">{option.sourceUrl}</a>{evidence?.fetchedAt && <p className="mt-1 text-xs text-zinc-400">조회 {new Date(evidence.fetchedAt).toLocaleString("ko-KR")}{evidence.stale && <button onClick={() => retryEvidence(evidence.id!)} className="ml-2 font-semibold text-amber-700 underline">24시간 경과 · 새로고침</button>}</p>}{evidence?.error && <p className="mt-2 text-xs text-red-700">{evidence.error} <button onClick={() => retryEvidence(evidence.id!)} className="font-semibold underline">다시 시도</button></p>}<ul className="mt-4 space-y-2 text-sm">{criteria.map((criterion) => { const value = values.find((item) => item.optionId === option.id && item.criterionId === criterion.id); return <li key={criterion.id} className="flex items-center justify-between gap-3"><span className="text-zinc-500">{criterion.name}</span><span className="text-right font-medium">{displayValue(value, criterion.unit)}{value?.verificationStatus === "UNREVIEWED" && <button onClick={() => verifyValue(value.id!)} className="ml-2 text-xs text-emerald-700">확인</button>}</span></li>; })}</ul></article>; })}</div><div className="flex gap-2"><input value={newUrl} onChange={(event) => setNewUrl(event.target.value)} placeholder="비교할 후보 URL 추가" className={inputClass} /><button onClick={addSource} className={secondaryButton}>링크 추가</button></div></Section>

    {(guidance?.candidateSuggestions?.length ?? 0) > 0 && <Section title="놓쳤을 수 있는 대안" description="현재 후보와 다른 선택지를 제안합니다. 승인하기 전에는 비교에 들어가지 않습니다."><div className="grid gap-3">{guidance?.candidateSuggestions?.map((item) => <article key={item.id} className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-zinc-200 p-4"><div className="min-w-0"><p className="font-semibold">{item.name}</p><p className="mt-1 text-sm text-zinc-600">{item.reason}</p><a href={item.url} target="_blank" rel="noreferrer" className="mt-1 block truncate text-xs text-emerald-700">{item.url}</a></div><div className="flex gap-2"><button onClick={() => updateSuggestion(item.id!, "ACCEPT")} className={primarySmall}>후보에 추가</button><button onClick={() => updateSuggestion(item.id!, "DISMISS")} className={secondaryButton}>제외</button></div></article>)}</div></Section>}

    <details className="rounded-3xl border border-zinc-200 bg-white shadow-sm"><summary className="cursor-pointer p-6 text-xl font-semibold">검증 입력과 점수 보기</summary><div className="space-y-6 border-t border-zinc-100 p-6"><p className="text-sm text-zinc-500">미확인 칸만 보강하면 재현 가능한 점수 평가를 실행할 수 있습니다. weight와 anchor는 category template와 확인된 성향에서 관리됩니다.</p><div className="overflow-x-auto"><table className="w-full min-w-[700px] text-sm"><thead><tr><th className="p-2 text-left">후보</th>{criteria.map((item) => <th key={item.id} className="p-2 text-left">{item.name}</th>)}</tr></thead><tbody>{options.map((option) => <tr key={option.id} className="border-t border-zinc-100"><th className="p-2 text-left">{option.name}</th>{criteria.map((criterion) => { const value = values.find((item) => item.optionId === option.id && item.criterionId === criterion.id); return <td key={criterion.id} className="p-2">{criterion.kind === "NUMERIC_UTILITY" ? <input type="number" step="any" value={value?.numericValue ?? ""} onChange={(event) => setCell(option.id!, criterion.id!, "numericValue", event.target.value)} className={inputClass} /> : <select value={value?.rating ?? ""} onChange={(event) => setCell(option.id!, criterion.id!, "rating", event.target.value)} className={inputClass}><option value="">미확인</option>{[1,2,3,4,5].map((rating) => <option key={rating}>{rating}</option>)}</select>}</td>; })}</tr>)}</tbody></table></div><div className="flex flex-wrap items-center gap-3"><button onClick={saveComparison} className={secondaryButton}>검증 입력 저장</button><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={includeLlm} onChange={(event) => setIncludeLlm(event.target.checked)} disabled={!llmAvailable} /> 독립 LLM 관점 포함</label>{guidance && !guidance.aiConsent && <span className="text-xs text-zinc-500">생성 시 AI 처리에 동의하지 않아 Algorithm만 실행합니다.</span>}<button onClick={evaluate} disabled={guidance ? guidance.readiness !== "VERIFIED_READY" : false} className="rounded-full bg-emerald-700 px-5 py-2 text-sm font-semibold text-white disabled:opacity-40">검증된 평가 실행</button></div></div></details>
    {evaluation && <EvaluationResultView evaluation={evaluation} />}

    <Section title="실제 선택" description="추천과 별개로 내가 무엇을 선택했고 왜 그랬는지 남깁니다."><div className="grid gap-3 sm:grid-cols-2"><select value={selectedOptionId} onChange={(event) => setSelectedOptionId(event.target.value)} className={inputClass}><option value="">후보 선택</option>{options.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}</select><input value={rationale} onChange={(event) => setRationale(event.target.value)} className={inputClass} placeholder="결정 이유" /></div><div className="flex flex-wrap gap-2"><button onClick={() => resolveDecision("CHOSEN")} className={primarySmall}>선택 기록</button><button onClick={() => resolveDecision("DEFERRED")} className={secondaryButton}>보류</button><button onClick={() => resolveDecision("ABANDONED")} className={secondaryButton}>포기</button></div></Section>

    {decision.status === "CHOSEN" && <Section title="사용 후 회고" description="짧은 회고가 다음 결정에서 검토할 개인 성향이 됩니다."><div id="outcome" className="grid gap-3 sm:grid-cols-2"><select value={satisfaction} onChange={(event) => setSatisfaction(Number(event.target.value))} className={inputClass}>{[1,2,3,4,5].map((value) => <option key={value} value={value}>만족도 {value}</option>)}</select><select value={expectationStatus} onChange={(event) => setExpectationStatus(event.target.value as typeof expectationStatus)} className={inputClass}><option value="MET">기대 충족</option><option value="PARTIALLY_MET">일부 충족</option><option value="NOT_MET">미충족</option><option value="TOO_EARLY">아직 판단하기 이른 시점</option></select><select value={wouldChooseAgain === undefined ? "" : String(wouldChooseAgain)} onChange={(event) => setWouldChooseAgain(event.target.value === "" ? undefined : event.target.value === "true")} className={inputClass}><option value="">다시 선택할지</option><option value="true">다시 선택함</option><option value="false">다른 선택을 함</option></select><select value={workedWell} onChange={(event) => setWorkedWell(event.target.value)} className={inputClass}><option value="">실제로 잘 맞은 기준</option>{criteria.map((item) => <option key={item.id}>{item.name}</option>)}</select><select value={disappointedBy} onChange={(event) => setDisappointedBy(event.target.value)} className={inputClass}><option value="">더 엄격히 볼 기준</option>{criteria.map((item) => <option key={item.id}>{item.name}</option>)}</select><input value={outcomeNotes} onChange={(event) => setOutcomeNotes(event.target.value)} className={inputClass} placeholder="예상과 달랐던 점" /></div><button onClick={addOutcome} className={primarySmall}>회고 저장</button>{(guidance?.reminders?.length ?? 0) > 0 && <ul className="space-y-1 text-xs text-zinc-500">{guidance?.reminders?.map((item) => <li key={item.id} className="flex items-center gap-2">{new Date(item.dueAt!).toLocaleDateString("ko-KR")} · {item.channel} · {item.status}{item.status === "PENDING" && <button onClick={() => skipReminder(item.id!)} className="text-red-600 underline">건너뛰기</button>}</li>)}</ul>}{(decision.outcomes?.length ?? 0) > 0 && <ul className="space-y-2 border-t border-zinc-100 pt-4 text-sm text-zinc-600">{decision.outcomes?.map((item) => <li key={item.id}>{item.createdAt?.slice(0,10)} · 만족도 {item.satisfaction} · {item.expectationStatus} {item.notes && `· ${item.notes}`}</li>)}</ul>}</Section>}
  </div>;
}

function Section({ title, description, children }: { title: string; description: string; children: React.ReactNode }) { return <section className="space-y-5 rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm"><div><h2 className="text-xl font-semibold">{title}</h2><p className="mt-1 text-sm text-zinc-500">{description}</p></div>{children}</section>; }
function displayValue(value: DraftValue | undefined, unit?: string) { if (!value || (value.numericValue == null && value.rating == null)) return <span className="text-zinc-400">미확인</span>; return `${value.numericValue ?? `${value.rating}/5`}${value.numericValue != null && unit ? ` ${unit}` : ""}`; }
function problemMessage(error: unknown): string { const problem = error as Problem | undefined; return problem?.violations?.join(" · ") ?? problem?.detail ?? "요청을 처리하지 못했습니다."; }
const inputClass = "min-w-0 flex-1 rounded-xl border border-zinc-300 bg-white px-3 py-2 outline-none focus:border-emerald-500";
const secondaryButton = "rounded-full border border-zinc-300 bg-white px-4 py-2 text-sm font-medium text-zinc-800 hover:border-zinc-500 disabled:opacity-40";
const primarySmall = "rounded-full bg-zinc-950 px-4 py-2 text-sm font-semibold text-white hover:bg-zinc-800 disabled:opacity-40";
