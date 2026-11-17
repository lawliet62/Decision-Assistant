"use client";

import { useEffect, useState } from "react";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

type View = components["schemas"]["InsightView"];
type Response = components["schemas"]["InsightResponse"];
const button = "rounded-xl border px-3 py-2 text-sm disabled:opacity-40";

export function MemoryInsight({ decisionId, resolutionId, hasUnsavedChanges, onApply }: {
  decisionId: string; resolutionId: string; hasUnsavedChanges: boolean; onApply: (note: string) => void;
}) {
  const [view, setView] = useState<View>();
  const [result, setResult] = useState<Response>();
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [action, setAction] = useState("");
  const [editing, setEditing] = useState(false);
  const [note, setNote] = useState("");
  const path = { decisionId, resolutionId };
  useEffect(() => {
    let active = true;
    api.GET("/api/v1/decisions/{decisionId}/memories/{resolutionId}/insight", { params: { path: { decisionId, resolutionId } } })
      .then(r => { if (active && r.data) { setView(r.data); setResult(r.data.saved); } }).catch(() => {});
    return () => { active = false; };
  }, [decisionId, resolutionId]);

  async function generate() {
    if (hasUnsavedChanges || !consent || busy) return;
    setBusy(true); setError("");
    try {
      const response = await api.POST("/api/v1/decisions/{decisionId}/memories/{resolutionId}/insight", {
        params: { path }, body: { aiConsent: consent, sensitiveDataAcknowledged: consent, expectedInputHash: view!.inputHash! },
      });
      if (!response.data) {
        setError("요약을 생성하지 못했습니다. 전송 내용을 다시 확인하거나 원문을 참고하세요.");
        setConsent(false);
        const refreshed = await api.GET("/api/v1/decisions/{decisionId}/memories/{resolutionId}/insight", { params: { path } });
        setView(refreshed.data);
        return;
      }
      setResult(response.data);
    } catch { setError("연결하지 못했습니다. 원문 기록은 계속 참고할 수 있습니다."); }
    finally { setBusy(false); }
  }
  const insight = result?.insight;
  return <div className="space-y-3 rounded-xl border border-emerald-200 p-3">
    <p className="font-medium">결정 요약과 이번 선택에 참고할 이유</p>
    {hasUnsavedChanges && <p role="status" className="text-amber-800">변경한 조건을 먼저 저장하세요. 저장 전에는 AI 해석을 생성하거나 적용할 수 없습니다.</p>}
    {!insight && <>
      <p>당시 이유를 요약하고 이번 조건에서 확인할 점을 AI가 설명합니다.</p>
      {view?.available ? <>
        <details><summary className="cursor-pointer underline">AI에 보낼 내용 보기</summary>
          <p className="mt-2">{view.provider} · {view.model}</p><p>{view.dataNotice}</p>
          <p>현재 결정의 제목·용도·예산·비교 조건과 이 과거 기록의 이유·구매 상태·회고를 전송합니다.</p>
          <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap break-all text-xs">{JSON.stringify(view.input, null, 2)}</pre>
        </details>
        <label className="flex items-start gap-2"><input type="checkbox" checked={consent} disabled={hasUnsavedChanges || busy} onChange={e => setConsent(e.target.checked)} />
          <span>표시된 과거 기록과 현재 조건의 AI 전송에 동의하며, 민감정보가 없음을 확인합니다.</span></label>
        <button type="button" className={button} disabled={hasUnsavedChanges || !consent || busy} onClick={generate}>{busy ? "요약하는 중…" : "요약과 관련 이유 생성"}</button>
      </> : <p className="text-zinc-500">AI 요약은 현재 사용할 수 없습니다. 위의 원문을 참고하세요.</p>}
    </>}
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {insight && <>
      <p className="text-xs text-zinc-500">AI 해석 · {result.provider} · {result.model} · {result.createdAt?.slice(0, 10)} · 원문을 확인하세요.</p>
      <p><strong>당시 결정 요약:</strong> {insight.summary}</p>
      <p><strong>이번 결정과의 관련성:</strong> {insight.relevance}</p>
      <ul className="list-disc pl-5">{insight.conditions?.map((c, i) => <li key={i}>{c}</li>)}</ul>
      <p><strong>확인할 점:</strong> {insight.uncertainty}</p>
      <p className="text-xs">위의 ‘당시 기록 보기’에서 근거를 확인할 수 있습니다. 적용하면 이번 결정의 메모에 추가되며 변경 저장으로 보존합니다.</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" className={button} disabled={hasUnsavedChanges || !!action} onClick={() => {
          onApply(`과거 기록 참고 (${result.sourceDecisionId}/${resolutionId}) · AI 해석을 이번 결정에서 확인함: ${insight.relevance} 확인 조건: ${insight.conditions?.join(" · ")}`);
          setAction("이번 결정에서 중요하게 참고합니다.");
        }}>이번에도 중요함</button>
        <button type="button" className={button} disabled={!!action} onClick={() => setAction("이번에는 해당하지 않는 것으로 표시했습니다.")}>이번에는 해당 없음</button>
        <button type="button" className={button} disabled={hasUnsavedChanges || !!action} onClick={() => { setNote(insight.relevance ?? ""); setEditing(true); }}>해석 수정</button>
      </div>
      {editing && !action && <label className="block">이번 결정에서 참고할 내용<textarea aria-label="과거 이유 해석 수정" className="mt-2 w-full rounded-xl border p-2" maxLength={2000} value={note} onChange={e => setNote(e.target.value)} />
        <button type="button" className={button} disabled={hasUnsavedChanges || !note.trim()} onClick={() => { onApply(`과거 기록 참고 (${result.sourceDecisionId}/${resolutionId}) · 사용자가 수정한 해석: ${note.trim()}`); setAction("수정한 내용을 이번 메모에 추가했습니다."); }}>수정한 내용 참고</button>
      </label>}
      {action && <p role="status">{action}</p>}
    </>}
  </div>;
}
