"use client";

import { useEffect, useState } from "react";
import { useFieldArray, useForm, useWatch } from "react-hook-form";
import { api } from "@/shared/api/client";
import type { AiCapabilitiesResponse, DecisionResponse, EvaluationResponse, StructureRunResponse, UpdateDraftRequest } from "@/shared/api/types";
import { EvaluationResultView } from "@/features/evaluation-result/evaluation-result";
import { DecisionEditor } from "./decision-editor";
import { ReasonPicker, PastDecisions } from "./decision-reasons";
import { ResolutionSnapshots } from "./comparison-workspace";

type Problem = { detail?: string; violations?: string[] };

export function LegacyDecisionEditor({ initialDecision }: { initialDecision: DecisionResponse }) {
  const [decision, setDecision] = useState(initialDecision);
  const [revisionBusy, setRevisionBusy] = useState(false);
  const completed = ["CHOSEN", "DEFERRED", "ABANDONED"].includes(decision.status ?? "");
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();
  const [evaluation, setEvaluation] = useState<EvaluationResponse>();
  const [proposal, setProposal] = useState<StructureRunResponse>();
  const [capabilities, setCapabilities] = useState<AiCapabilitiesResponse>();
  const [includeLlm, setIncludeLlm] = useState(false);
  const [aiConsent, setAiConsent] = useState(false);
  const [sensitiveDataAcknowledged, setSensitiveDataAcknowledged] = useState(false);
  const [satisfaction, setSatisfaction] = useState(4);
  const [expectationStatus, setExpectationStatus] = useState<"MET" | "PARTIALLY_MET" | "NOT_MET" | "TOO_EARLY">("TOO_EARLY");
  const [outcomeNotes, setOutcomeNotes] = useState("");

  const { control, register, handleSubmit, getValues, setValue, reset, formState: { isDirty, isSubmitting, dirtyFields } } = useForm<UpdateDraftRequest>({
    defaultValues: toDraft(initialDecision),
  });
  const optionsArray = useFieldArray({ control, name: "options", keyName: "formKey" });
  const criteriaArray = useFieldArray({ control, name: "criteria", keyName: "formKey" });
  const constraintsArray = useFieldArray({ control, name: "constraints", keyName: "formKey" });
  const options = useWatch({ control, name: "options" }) ?? [];
  const criteria = useWatch({ control, name: "criteria" }) ?? [];
  const values = useWatch({ control, name: "values" }) ?? [];
  const constraints = useWatch({ control, name: "constraints" }) ?? [];
  const constraintResults = useWatch({ control, name: "constraintResults" }) ?? [];

  useEffect(() => {
    api.GET("/api/v1/ai/capabilities").then(({ data }) => setCapabilities(data));
  }, []);

  async function saveDraft(body: UpdateDraftRequest) {
    clearFeedback();
    const { data, error: apiError } = await api.PUT("/api/v1/decisions/{decisionId}/draft", {
      params: { path: { decisionId: decision.id! } },
      body: { ...body, expectedVersion: decision.version },
    });
    if (apiError || !data) {
      setError(problemMessage(apiError));
      return;
    }
    applyDecision(data);
    setMessage("Draft를 저장했습니다.");
  }

  async function refreshDecision() {
    const { data } = await api.GET("/api/v1/decisions/{decisionId}", {
      params: { path: { decisionId: decision.id! } },
    });
    if (data) applyDecision(data);
  }

  function applyDecision(next: DecisionResponse) {
    setDecision(next);
    setEvaluation(undefined); setProposal(undefined);
    reset(toDraft(next));
  }

  function addOption() {
    const id = crypto.randomUUID();
    optionsArray.append({ id, name: `Option ${options.length + 1}`, description: "", sourceUrl: "", sourceText: "" });
    const nextValues = [...getValues("values")];
    for (const criterion of getValues("criteria")) {
      nextValues.push(emptyValue(id, criterion.id!));
    }
    setValue("values", nextValues, { shouldDirty: true });
    const nextResults = [...getValues("constraintResults")];
    for (const constraint of getValues("constraints").filter((item) => item.active && !item.kind.startsWith("NUMERIC"))) {
      nextResults.push(emptyConstraintResult(id, constraint.id!));
    }
    setValue("constraintResults", nextResults, { shouldDirty: true });
  }

  function removeOption(index: number) {
    const optionId = getValues(`options.${index}.id`);
    optionsArray.remove(index);
    setValue("values", getValues("values").filter((value) => value.optionId !== optionId), { shouldDirty: true });
    setValue("constraintResults", getValues("constraintResults").filter((result) => result.optionId !== optionId), { shouldDirty: true });
  }

  function addCriterion() {
    const id = crypto.randomUUID();
    criteriaArray.append({ id, name: "새 Criterion", kind: "RATING_1_TO_5", direction: "NOT_APPLICABLE", weight: 1, active: true });
    setValue("values", [
      ...getValues("values"),
      ...getValues("options").map((option) => emptyValue(option.id!, id)),
    ], { shouldDirty: true });
  }

  function removeCriterion(index: number) {
    const criterionId = getValues(`criteria.${index}.id`);
    criteriaArray.remove(index);
    setValue("values", getValues("values").filter((value) => value.criterionId !== criterionId), { shouldDirty: true });
    setValue("constraints", getValues("constraints").map((constraint) => constraint.criterionId === criterionId ? { ...constraint, criterionId: undefined, active: false } : constraint), { shouldDirty: true });
  }

  function addConstraint() {
    const id = crypto.randomUUID();
    constraintsArray.append({ id, name: "새 Constraint", kind: "MANUAL", description: "", active: false });
  }

  function removeConstraint(index: number) {
    const constraintId = getValues(`constraints.${index}.id`);
    constraintsArray.remove(index);
    setValue("constraintResults", getValues("constraintResults").filter((result) => result.constraintId !== constraintId), { shouldDirty: true });
  }

  function setConstraintActive(index: number, active: boolean) {
    setValue(`constraints.${index}.active`, active, { shouldDirty: true });
    const constraint = getValues(`constraints.${index}`);
    if (active && !constraint.kind.startsWith("NUMERIC")) {
      const existing = getValues("constraintResults");
      const additions = getValues("options")
        .filter((option) => !existing.some((result) => result.optionId === option.id && result.constraintId === constraint.id))
        .map((option) => emptyConstraintResult(option.id!, constraint.id!));
      setValue("constraintResults", [...existing, ...additions], { shouldDirty: true });
    }
  }

  function ensureValue(optionId: string, criterionId: string) {
    if (getValues("values").some((value) => value.optionId === optionId && value.criterionId === criterionId)) return;
    setValue("values", [...getValues("values"), emptyValue(optionId, criterionId)], { shouldDirty: true });
  }

  async function requestStructure() {
    clearFeedback();
    if (isDirty) return setError("AI proposal 전에 draft를 저장하세요.");
    if (!hasAiDataConsent()) return;
    const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/structure-proposals", {
      params: { path: { decisionId: decision.id! } },
      body: { expectedVersion: decision.version, aiConsent: true, sensitiveDataAcknowledged },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    setProposal(data);
  }

  async function evaluate() {
    clearFeedback();
    if (isDirty) return setError("평가 전에 draft를 저장하세요.");
    if (includeLlm && !hasAiDataConsent()) return;
    const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/evaluations", {
      params: { path: { decisionId: decision.id! } },
      body: { expectedVersion: decision.version, includeLlm, aiConsent: includeLlm && aiConsent, sensitiveDataAcknowledged: includeLlm && sensitiveDataAcknowledged },
    });
    if (apiError || !data) return setError(problemMessage(apiError));
    setEvaluation(data);
  }

  async function resolveDecision(kind: "CHOSEN" | "DEFERRED" | "ABANDONED", selectedOptionId: string, rationale: string, reasonIds: string[], primaryReasonId?: string) {
    clearFeedback();
    if (Object.keys(dirtyFields).length > 0) return setError("편집한 내용을 먼저 저장하세요.");
    const { error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/resolutions", {
      params: { path: { decisionId: decision.id! } },
      body: { reasonIds, primaryReasonId, kind, selectedOptionId: kind === "CHOSEN" ? selectedOptionId : undefined, rationale, expectedVersion: decision.version },
    });
    if (apiError) return setError(problemMessage(apiError));
    await refreshDecision();
    setMessage("실제 선택 상태를 기록했습니다.");
  }

  async function addOutcome() {
    clearFeedback();
    const { error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/outcome-check-ins", {
      params: { path: { decisionId: decision.id! } },
      body: { satisfaction, expectationStatus, notes: outcomeNotes, resolutionId: decision.currentResolutionId, expectedVersion: decision.version },
    });
    if (apiError) return setError(problemMessage(apiError));
    setOutcomeNotes("");
    await refreshDecision();
    setMessage("Outcome check-in을 기록했습니다.");
  }

  function clearFeedback() { setMessage(undefined); setError(undefined); }

  async function beginRevision(mode: "CORRECTION" | "RECONSIDERATION") {
    if (revisionBusy) return;
    clearFeedback(); setRevisionBusy(true);
    try {
      const { data, error: apiError } = await api.POST("/api/v1/decisions/{decisionId}/revisions", {
        params: { path: { decisionId: decision.id! } }, body: { expectedVersion: decision.version!, mode },
      });
      if (apiError || !data) return setError(problemMessage(apiError));
      applyDecision(data);
      setMessage(mode === "CORRECTION" ? "기록 수정을 시작했습니다." : "다시 검토를 시작했습니다.");
    } catch { setError("연결을 확인하고 최신 기록을 다시 불러오세요."); }
    finally { setRevisionBusy(false); }
  }

  function hasAiDataConsent() {
    if (!aiConsent) {
      setError("외부 AI 전송 범위를 확인하고 동의하세요.");
      return false;
    }
    if (capabilities?.requiresSensitiveDataAcknowledgement && !sensitiveDataAcknowledged) {
      setError("민감한 개인정보·금융 정보·비공개 업무 정보가 없음을 확인하세요.");
      return false;
    }
    return true;
  }

  if (completed) return <DecisionEditor initialDecision={decision} />;
  return (
    <div className="mt-6 space-y-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-emerald-700">{decision.templateKey} · {completed ? `결정 완료 · ${decision.status === "CHOSEN" ? "후보 선택" : "구매하지 않음"}` : decision.status === "DEFERRED" ? "보류" : "비교 중"}</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight">{decision.title}</h1>
        </div>
        <button form="decision-form" disabled={completed || revisionBusy || isSubmitting || !isDirty} className="rounded-full bg-zinc-950 px-5 py-3 text-sm font-semibold text-white disabled:opacity-40">Draft 저장</button>
      </header>

      {(message || error) && <div className={`rounded-xl p-4 text-sm ${error ? "bg-red-50 text-red-800" : "bg-emerald-50 text-emerald-800"}`}>{error ?? message}</div>}
      <ResolutionSnapshots decision={decision} />
      {completed && <Section title="완료한 결정 관리" description="후보 선택은 실제 구매 완료를 뜻하지 않습니다. 기록 수정은 이전 기록을 정정 이력으로 남기고, 다시 검토는 당시 판단을 유효한 기록으로 보존합니다."><button disabled={revisionBusy} className={secondaryButton} onClick={() => beginRevision("CORRECTION")}>기록 수정</button><button disabled={revisionBusy} className={secondaryButton} onClick={() => beginRevision("RECONSIDERATION")}>다시 검토</button></Section>}
      {decision.editMode && <p role="status">{decision.editMode === "CORRECTION" ? "기록 수정 중 · 수정한 입력을 저장하고 결정 결과를 다시 기록하세요. 이전 기록의 개인화 적용을 중단했습니다." : "다시 검토 중 · 이전 판단과 회고를 보존합니다."}</p>}

      <fieldset disabled={completed || revisionBusy} className="space-y-8">
      <form id="decision-form" onSubmit={handleSubmit(saveDraft)} className="space-y-8">
        <Section title="Context" description="목적과 예산은 추천보다 먼저 확인되는 입력입니다.">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="제목"><input {...register("title")} className={inputClass} /></Field>
            <Field label="예산"><input type="number" {...register("context.budget", numberInput)} className={inputClass} /></Field>
            <Field label="통화"><input maxLength={3} {...register("context.currency")} className={inputClass} placeholder="KRW" /></Field>
            <Field label="주요 사용 목적"><input {...register("context.intendedUse")} className={inputClass} /></Field>
          </div>
          <Field label="상황과 추가 메모"><textarea {...register("context.notes")} className={`${inputClass} min-h-24`} /></Field>
        </Section>

        <Section title="Options" description="URL은 provenance로만 저장하며 서버가 페이지를 읽지 않습니다." action={<button type="button" onClick={addOption} className={secondaryButton}>Option 추가</button>}>
          <div className="grid gap-4 md:grid-cols-2">
            {optionsArray.fields.map((field, index) => (
              <article key={field.formKey} className="space-y-3 rounded-2xl border border-zinc-200 p-4">
                <div className="flex gap-2"><input {...register(`options.${index}.name`)} className={`${inputClass} font-semibold`} /><button type="button" onClick={() => removeOption(index)} className="text-sm text-red-600">삭제</button></div>
                <textarea {...register(`options.${index}.description`)} className={inputClass} placeholder="설명" />
                <input {...register(`options.${index}.sourceUrl`)} className={inputClass} placeholder="출처 URL" />
                <textarea {...register(`options.${index}.sourceText`)} className={`${inputClass} min-h-28`} placeholder="제품 페이지에서 직접 확인해 붙여 넣은 설명·스펙" />
              </article>
            ))}
          </div>
        </Section>

        <Section title="Criteria" description="Numeric 값은 후보 min-max가 아니라 확인한 worst/best anchor로 utility를 계산합니다." action={<button type="button" onClick={addCriterion} className={secondaryButton}>Criterion 추가</button>}>
          <div className="space-y-3">
            {criteriaArray.fields.map((field, index) => {
              const criterion = criteria[index];
              return <article key={field.formKey} className="grid gap-3 rounded-2xl border border-zinc-200 p-4 md:grid-cols-8">
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" {...register(`criteria.${index}.active`)} /> 사용</label>
                <input {...register(`criteria.${index}.name`)} className={`${inputClass} md:col-span-2`} />
                <select {...register(`criteria.${index}.kind`)} className={inputClass}><option value="NUMERIC_UTILITY">Numeric</option><option value="RATING_1_TO_5">1~5 rating</option></select>
                <select {...register(`criteria.${index}.direction`)} className={inputClass}><option value="HIGHER_BETTER">높을수록</option><option value="LOWER_BETTER">낮을수록</option><option value="NOT_APPLICABLE">해당 없음</option></select>
                <input type="number" step="0.1" {...register(`criteria.${index}.weight`, numberInput)} className={inputClass} placeholder="weight" />
                {criterion?.kind === "NUMERIC_UTILITY" ? <><input type="number" step="any" {...register(`criteria.${index}.worstAnchor`, numberInput)} className={inputClass} placeholder="worst" /><input type="number" step="any" {...register(`criteria.${index}.bestAnchor`, numberInput)} className={inputClass} placeholder="best" /></> : <div className="md:col-span-2" />}
                <button type="button" onClick={() => removeCriterion(index)} className="text-left text-sm text-red-600 md:col-start-8">삭제</button>
              </article>;
            })}
          </div>
        </Section>

        <Section title="Option values" description="활성 Criterion은 모든 Option에 값이 있어야 평가할 수 있습니다.">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-separate border-spacing-2 text-sm">
              <thead><tr><th className="text-left">Option</th>{criteria.filter((criterion) => criterion.active).map((criterion) => <th key={criterion.id} className="text-left">{criterion.name}</th>)}</tr></thead>
              <tbody>{options.map((option) => <tr key={option.id}><th className="text-left font-medium">{option.name}</th>{criteria.filter((criterion) => criterion.active).map((criterion) => {
                const valueIndex = values.findIndex((value) => value.optionId === option.id && value.criterionId === criterion.id);
                if (valueIndex < 0) return <td key={criterion.id}><button type="button" className={secondaryButton} onClick={() => ensureValue(option.id!, criterion.id!)}>값 추가</button></td>;
                return <td key={criterion.id} className="align-top"><div className="space-y-1">{criterion.kind === "NUMERIC_UTILITY" ? <input type="number" step="any" {...register(`values.${valueIndex}.numericValue`, numberInput)} className={inputClass} /> : <select {...register(`values.${valueIndex}.rating`, numberInput)} className={inputClass}><option value="">선택</option>{[1,2,3,4,5].map((rating) => <option key={rating} value={rating}>{rating}</option>)}</select>}<select {...register(`values.${valueIndex}.certainty`)} className={inputClass}><option value="CONFIRMED">확정</option><option value="ESTIMATED">추정</option></select></div></td>;
              })}</tr>)}</tbody>
            </table>
          </div>
        </Section>

        <Section title="Constraints" description="FAIL은 추천 대상에서 제외되고 UNKNOWN은 평가를 차단합니다." action={<button type="button" onClick={addConstraint} className={secondaryButton}>Constraint 추가</button>}>
          <div className="space-y-3">{constraintsArray.fields.map((field, index) => {
            const constraint = constraints[index];
            const activeRegistration = register(`constraints.${index}.active`);
            return <article key={field.formKey} className="space-y-3 rounded-2xl border border-zinc-200 p-4">
              <div className="grid gap-3 sm:grid-cols-5">
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" {...activeRegistration} onChange={(event) => { activeRegistration.onChange(event); setConstraintActive(index, event.target.checked); }} /> 사용</label>
                <input {...register(`constraints.${index}.name`)} className={`${inputClass} sm:col-span-2`} />
                <select {...register(`constraints.${index}.kind`)} className={inputClass}><option value="NUMERIC_MIN">Numeric min</option><option value="NUMERIC_MAX">Numeric max</option><option value="BOOLEAN_REQUIRED">Boolean</option><option value="MANUAL">Manual</option></select>
                <button type="button" onClick={() => removeConstraint(index)} className="text-sm text-red-600">삭제</button>
              </div>
              {constraint?.kind.startsWith("NUMERIC") ? <div className="grid gap-3 sm:grid-cols-2"><select {...register(`constraints.${index}.criterionId`)} className={inputClass}><option value="">Criterion 선택</option>{criteria.filter((criterion) => criterion.kind === "NUMERIC_UTILITY").map((criterion) => <option key={criterion.id} value={criterion.id}>{criterion.name}</option>)}</select><input type="number" step="any" {...register(`constraints.${index}.threshold`, numberInput)} className={inputClass} placeholder="threshold" /></div> : constraint?.active && <div className="flex flex-wrap gap-3">{options.map((option) => { const resultIndex = constraintResults.findIndex((result) => result.optionId === option.id && result.constraintId === constraint.id); return resultIndex < 0 ? null : <label key={option.id} className="text-sm"><span className="mr-2">{option.name}</span><select {...register(`constraintResults.${resultIndex}.status`)} className={inputClass}><option value="UNKNOWN">Unknown</option><option value="PASS">Pass</option><option value="FAIL">Fail</option></select></label>; })}</div>}
            </article>;
          })}</div>
        </Section>
      </form>

      <Section title="AI proposal" description={capabilities?.dataNotice ?? "AI 상태를 확인 중입니다."}>
        <AiConsent enabled={Boolean(capabilities?.enabled)} checked={aiConsent} onChange={setAiConsent} provider={capabilities?.provider} model={capabilities?.model} requiresSensitiveDataAcknowledgement={Boolean(capabilities?.requiresSensitiveDataAcknowledgement)} sensitiveDataAcknowledged={sensitiveDataAcknowledged} onSensitiveDataAcknowledgedChange={setSensitiveDataAcknowledged} />
        <button type="button" onClick={requestStructure} disabled={!capabilities?.enabled} className={secondaryButton}>구조화 proposal 요청</button>
        {proposal && <pre className="max-h-96 overflow-auto rounded-2xl bg-zinc-950 p-4 text-xs text-emerald-100">{JSON.stringify(proposal, null, 2)}</pre>}
      </Section>

      <Section title="Evaluate" description="Algorithm과 LLM은 같은 확정 snapshot을 서로의 결과 없이 평가합니다.">
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={includeLlm} onChange={(event) => setIncludeLlm(event.target.checked)} disabled={!capabilities?.enabled} /> LLM Assessment 포함</label>
        {includeLlm && <AiConsent enabled checked={aiConsent} onChange={setAiConsent} provider={capabilities?.provider} model={capabilities?.model} requiresSensitiveDataAcknowledgement={Boolean(capabilities?.requiresSensitiveDataAcknowledgement)} sensitiveDataAcknowledged={sensitiveDataAcknowledged} onSensitiveDataAcknowledgedChange={setSensitiveDataAcknowledged} />}
        <button type="button" onClick={evaluate} className="rounded-full bg-emerald-700 px-5 py-3 font-semibold text-white hover:bg-emerald-600">평가 실행</button>
      </Section>

      {evaluation && <EvaluationResultView evaluation={evaluation} />}

      <PastDecisions key={decision.version} id={decision.id!} onApply={note => setValue("context.notes", [getValues("context.notes"), note].filter(Boolean).join("\n"), { shouldDirty: true })} /><ReasonPicker key={decision.editMode === "CORRECTION" ? decision.revisionBaseId : "new"} initialResolution={decision.editMode === "CORRECTION" ? decision.resolutions?.find(r => r.id === decision.revisionBaseId) : undefined} options={options} onComplete={(kind, option, note, ids, primary) => resolveDecision(kind, option, note, ids, primary)} />
      </fieldset>

      {completed && <Section title="Outcome check-in" description={decision.status === "ABANDONED" ? "구매하지 않기로 한 결정에 대한 만족도와 결과를 기록합니다." : "시간이 지난 뒤 만족도와 기대 충족 여부를 반복 기록합니다."}>
        <div className="grid gap-3 sm:grid-cols-3"><select value={satisfaction} onChange={(event) => setSatisfaction(Number(event.target.value))} className={inputClass}>{[1,2,3,4,5].map((value) => <option key={value} value={value}>만족도 {value}</option>)}</select><select value={expectationStatus} onChange={(event) => setExpectationStatus(event.target.value as typeof expectationStatus)} className={inputClass}><option value="MET">기대 충족</option><option value="PARTIALLY_MET">일부 충족</option><option value="NOT_MET">미충족</option><option value="TOO_EARLY">판단 이른 시점</option></select><input value={outcomeNotes} onChange={(event) => setOutcomeNotes(event.target.value)} className={inputClass} placeholder="사후 메모" /></div>
        <button type="button" onClick={addOutcome} className={secondaryButton}>Check-in 저장</button>
        <ul className="space-y-2 text-sm text-zinc-600">{decision.outcomes?.filter(outcome => outcome.resolutionId === decision.currentResolutionId).map((outcome) => <li key={outcome.id}>{outcome.createdAt?.slice(0,10)} · 만족도 {outcome.satisfaction} · {outcome.expectationStatus} · {outcome.notes}</li>)}</ul>
      </Section>}
    </div>
  );
}

function Section({ title, description, action, children }: { title: string; description: string; action?: React.ReactNode; children: React.ReactNode }) {
  return <section className="space-y-5 rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm"><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-xl font-semibold">{title}</h2><p className="mt-1 text-sm text-zinc-500">{description}</p></div>{action}</div>{children}</section>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block text-sm font-medium text-zinc-700"><span>{label}</span><div className="mt-2">{children}</div></label>;
}

function AiConsent({ enabled, checked, onChange, provider, model, requiresSensitiveDataAcknowledgement, sensitiveDataAcknowledged, onSensitiveDataAcknowledgedChange }: { enabled: boolean; checked: boolean; onChange: (checked: boolean) => void; provider?: string; model?: string; requiresSensitiveDataAcknowledgement: boolean; sensitiveDataAcknowledged: boolean; onSensitiveDataAcknowledgedChange: (checked: boolean) => void }) {
  return <div className="space-y-3 rounded-xl bg-amber-50 p-4 text-sm text-amber-950">
    <label className="flex items-start gap-3"><input type="checkbox" className="mt-1" checked={checked} onChange={(event) => onChange(event.target.checked)} disabled={!enabled} /><span>현재 Decision 내용이 외부 provider({provider ?? "-"}, {model ?? "-"})로 전송됨을 확인했습니다.</span></label>
    {requiresSensitiveDataAcknowledgement && <label className="flex items-start gap-3"><input type="checkbox" className="mt-1" checked={sensitiveDataAcknowledged} onChange={(event) => onSensitiveDataAcknowledgedChange(event.target.checked)} disabled={!enabled} /><span>제품 정보와 일반 구매 메모만 포함했으며, 개인 식별·금융·건강·비공개 업무 정보는 포함하지 않았음을 확인했습니다.</span></label>}
  </div>;
}

const inputClass = "w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 outline-none focus:border-emerald-500";
const secondaryButton = "rounded-full border border-zinc-300 bg-white px-4 py-2 text-sm font-medium text-zinc-800 hover:border-zinc-500 disabled:opacity-40";
const numberInput = { setValueAs: (value: string) => value === "" ? undefined : Number(value) };

function toDraft(decision: DecisionResponse): UpdateDraftRequest {
  return {
    expectedVersion: decision.version ?? 0,
    title: decision.title ?? "",
    context: decision.context ?? {},
    options: decision.options ?? [],
    criteria: decision.criteria ?? [],
    values: decision.values ?? [],
    constraints: decision.constraints ?? [],
    constraintResults: decision.constraintResults ?? [],
  };
}

function emptyValue(optionId: string, criterionId: string) {
  return { id: crypto.randomUUID(), optionId, criterionId, certainty: "CONFIRMED" as const };
}

function emptyConstraintResult(optionId: string, constraintId: string) {
  return { id: crypto.randomUUID(), optionId, constraintId, status: "UNKNOWN" as const, note: "" };
}

function problemMessage(error: unknown): string {
  const problem = error as Problem | undefined;
  return problem?.violations?.join(" · ") ?? problem?.detail ?? "요청을 처리하지 못했습니다.";
}
