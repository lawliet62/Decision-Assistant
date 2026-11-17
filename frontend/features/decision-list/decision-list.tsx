"use client";

import Link from "next/link";
import { ArchiveButton, RecoveryNotice, RetentionPanel } from "./retention-panel";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

type Summary = components["schemas"]["DecisionSummary"];
type Page = components["schemas"]["DecisionPage"];
type Count = components["schemas"]["StatusCount"];
type Status = NonNullable<Summary["status"]>;
type Sort = "UPDATED_AT" | "CREATED_AT" | "TITLE" | "TEMPLATE_KEY";
const statuses: Status[] = ["DRAFT", "CHOSEN", "ARCHIVED"];
const labels: Record<Status, string> = { DRAFT: "비교 중", DEFERRED: "결정 완료 · 보류", CHOSEN: "결정 완료", ABANDONED: "결정 완료 · 구매하지 않음", ARCHIVED: "보관됨" };
const groupStatus = (status?: Status) => (status === "ABANDONED" || status === "DEFERRED") ? "CHOSEN" : status;
const apiStatuses = (scope: Status[]) => scope.flatMap(s => s === "CHOSEN" ? ["CHOSEN", "ABANDONED", "DEFERRED"] as Status[] : [s]);
const categories: Record<string, string> = { LAPTOP: "노트북", PHONE: "휴대폰", TABLET: "태블릿", MONITOR: "모니터", EARBUDS: "무선 이어폰", CUSTOM: "직접 비교" };
const sorts: Record<Sort, string> = { UPDATED_AT: "수정일", CREATED_AT: "생성일", TITLE: "제목", TEMPLATE_KEY: "제품군" };
const button = "rounded-lg border border-zinc-300 px-3 py-2 text-sm hover:bg-zinc-100 disabled:opacity-40 disabled:cursor-not-allowed";
const pressedButton = button + " aria-disabled:opacity-40 aria-disabled:cursor-wait aria-pressed:bg-emerald-700 aria-pressed:text-white aria-pressed:border-emerald-700 aria-pressed:hover:bg-emerald-800";
const date = (value?: string) => value ? new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeZone: "Asia/Seoul" }).format(new Date(value)) : "확인 필요";

function changeQuery(change: (params: URLSearchParams) => void) {
  const params = new URLSearchParams(window.location.search);
  change(params);
  window.history.replaceState(null, "", `?${params.toString()}`);
  window.dispatchEvent(new Event("decision-list-query"));
}
function subscribeQuery(listener: () => void) {
  window.addEventListener("popstate", listener);
  window.addEventListener("decision-list-query", listener);
  return () => { window.removeEventListener("popstate", listener); window.removeEventListener("decision-list-query", listener); };
}
const querySnapshot = () => window.location.search;
const serverQuerySnapshot = () => "";
function resetPages(params: URLSearchParams) { statuses.forEach(s => params.delete(`page.${s}`)); }

export function DecisionList() {
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState("");
  const refreshButton = useRef<HTMLButtonElement>(null);
  return <section aria-labelledby="decision-list-title" className="decision-workspace space-y-4">
    <div className="flex items-center justify-between gap-3"><h2 id="decision-list-title" className="text-xl font-semibold">내 결정</h2>
      <button ref={refreshButton} className={button} onClick={() => setRevision(v => v + 1)}>목록 새로고침</button></div>
    <p className="text-sm text-zinc-600">상태를 선택하고 그룹을 펼쳐 저장한 결정을 확인하세요.</p>
    <RecoveryNotice />
    <RetentionPanel onChanged={() => setRevision(v => v + 1)} />
    <p role="status" className="text-sm text-emerald-800">{notice}</p>
    <LoadedList key={revision} onRefresh={() => { setRevision(v => v + 1); refreshButton.current?.focus(); }} onDeleted={() => { setNotice("휴지통으로 이동했습니다. 30일 동안 복구할 수 있습니다."); refreshButton.current?.focus(); }} />
  </section>;
}

function LoadedList({ onRefresh, onDeleted }: { onRefresh: () => void; onDeleted: () => void }) {
  const query = useSyncExternalStore(subscribeQuery, querySnapshot, serverQuerySnapshot);
  const params = new URLSearchParams(query);
  const [counts, setCounts] = useState<Count[]>();
  const [error, setError] = useState(false);
  const [deleting, setDeleting] = useState<Summary>();
  const [chosen, setChosen] = useState<Record<string, Summary>>({});
  const [snapshots, setSnapshots] = useState<Partial<Record<Status, Summary[]>>>({});
  const [selecting, setSelecting] = useState(false);
  const [selectionError, setSelectionError] = useState("");
  const [errorStatus, setErrorStatus] = useState<Status>();
  const [bulkOpen, setBulkOpen] = useState(false);
  const [revision, setRevision] = useState(0);
  const [visibleGroups, setVisibleGroups] = useState<Partial<Record<Status, string[]>>>({});
  const updateVisible = useCallback((status: Status, ids: string[]) => setVisibleGroups(previous => ({ ...previous, [status]: ids })), []);
  useEffect(() => {
    const controller = new AbortController();
    api.GET("/api/v1/decisions/counts", { signal: controller.signal }).then(result => {
      if (!controller.signal.aborted) { if (result.data) setCounts(statuses.map(status => ({ status, count: result.data!.filter(item => groupStatus(item.status) === status).reduce((sum, item) => sum + (item.count ?? 0), 0) }))); else setError(true); }
    }).catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => controller.abort();
  }, [revision]);
  const selected = params.has("statuses") ? statuses.filter(s => params.get("statuses")!.split(",").map(value => (value === "ABANDONED" || value === "DEFERRED") ? "CHOSEN" : value).includes(s)) : statuses;
  const opened = (params.get("open") ?? "").split(",").map(s => (s === "ABANDONED" || s === "DEFERRED") ? "CHOSEN" : s);
  const rawSort = params.get("sort");
  const sort: Sort = rawSort && Object.hasOwn(sorts, rawSort) ? rawSort as Sort : "UPDATED_AT";
  const direction = params.get("direction") === "ASC" ? "ASC" : "DESC";
  function select(next: Status[]) { changeQuery(p => { p.set("statuses", next.join(",")); resetPages(p); }); }
  function toggle(item: Summary, checked: boolean) {
    if (!item.id) return;
    setChosen(previous => { const next = { ...previous }; if (checked) next[item.id!] = item; else delete next[item.id!]; return next; });
  }
  function selectItems(items: Summary[]) {
    setChosen(previous => { const next = { ...previous }; items.forEach(item => { if (item.id) next[item.id] = item; }); return next; });
  }
  function unselectItems(items: Summary[]) {
    setChosen(previous => { const next = { ...previous }; items.forEach(item => { if (item.id) delete next[item.id]; }); return next; });
  }
  function scopeIsSelected(scope: Status[]) {
    const populated = scope.filter(s => (counts?.find(item => item.status === s)?.count ?? 0) > 0);
    return populated.length > 0 && populated.every(s => {
      const snapshot = snapshots[s];
      return snapshot && snapshot.length === counts?.find(item => item.status === s)?.count && snapshot.every(item => item.id && chosen[item.id]);
    });
  }
  async function selectAll(scope: Status[], source?: Status) {
    if (!scope.length || selecting) return;
    setErrorStatus(source);
    if (scopeIsSelected(scope)) {
      unselectItems(scope.flatMap(s => snapshots[s] ?? []));
      setSelectionError("");
      return;
    }
    setSelecting(true); setSelectionError("");
    try {
      const result = await api.GET("/api/v1/decisions/selection", { params: { query: { statuses: apiStatuses(scope) } } });
      if (!result.data) {
        setSelectionError(result.response.status === 403 || result.response.status === 404
          ? "선택 요청이 거부되었습니다. 로그인과 앱 서버 버전을 확인하고, 서버를 최신 버전으로 다시 시작한 뒤 시도해 주세요."
          : "결정 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        return;
      }
      selectItems(result.data);
      const items = result.data;
      setSnapshots(previous => { const next = { ...previous }; scope.forEach(s => { next[s] = items.filter(item => groupStatus(item.status) === s); }); return next; });
    } catch { setSelectionError("서버에 연결하지 못했습니다. 연결을 확인하고 다시 시도해 주세요."); }
    finally { setSelecting(false); }
  }
  if (error) return <div role="alert" className="rounded-xl border border-red-200 p-5">목록을 불러오지 못했습니다. <button className={button} onClick={onRefresh}>다시 시도</button></div>;
  if (!counts) return <p role="status">결정 목록을 불러오는 중입니다.</p>;
  return <>
    <fieldset className="rounded-xl border border-zinc-200 p-4">
      <legend className="px-1 text-sm font-semibold">표시할 상태 (복수 선택)</legend>
      <div className="flex flex-wrap gap-x-5 gap-y-3">{statuses.map(s => <label key={s} className="flex items-center gap-2 text-sm">
        <input type="checkbox" disabled={selecting} checked={selected.includes(s)} onChange={e => select(e.target.checked ? [...selected, s] : selected.filter(item => item !== s))} />{labels[s]}
      </label>)}</div>
      <div className="mt-4 flex gap-2"><button className={button} disabled={selecting} onClick={() => select(statuses)}>전체 선택</button><button className={button} disabled={selecting} onClick={() => select([])}>전체 해제</button></div>
    </fieldset>
    <div className="flex flex-wrap items-center gap-2" aria-label="결정 선택">
      <button className={pressedButton} aria-pressed={scopeIsSelected(selected)} aria-disabled={selecting} disabled={selected.length === 0} onClick={() => selectAll(selected)}>선택한 상태 결정 모두 선택</button>
      <button className={button} disabled={!Object.keys(chosen).length || selecting} onClick={() => setChosen({})}>결정 선택 해제</button>
      <button className={button + " text-red-700"} disabled={!Object.keys(chosen).length || selecting} onClick={() => setBulkOpen(true)}>선택 삭제</button>
      <span role="status" className="text-sm">{Object.keys(chosen).length}개 선택 · 현재 화면에 없는 결정 {Object.keys(chosen).filter(id => !Object.values(visibleGroups).some(ids => ids?.includes(id))).length}개</span>
    </div>
    <p className="text-xs text-zinc-500">전체 선택은 누른 시점의 결정을 기준으로 합니다. 새로 추가된 결정은 다시 선택해 주세요.</p>
    {selectionError && !errorStatus && <p role="alert">{selectionError}</p>}
    <div className="flex flex-wrap items-center gap-3">
      <label className="text-sm">정렬 기준 <select className={button} value={sort} onChange={e => changeQuery(p => { p.set("sort", e.target.value); resetPages(p); })}>
        {Object.entries(sorts).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label className="text-sm">정렬 방향 <select className={button} value={direction} onChange={e => changeQuery(p => { p.set("direction", e.target.value); resetPages(p); })}>
        <option value="DESC">{sort.endsWith("_AT") ? "최신순" : "내림차순"}</option><option value="ASC">{sort.endsWith("_AT") ? "오래된순" : "오름차순"}</option>
      </select></label><span className="text-sm text-zinc-500">그룹별 10개씩 표시</span>
    </div>
    {counts.every(item => !item.count) && <p className="rounded-xl border border-dashed p-6 text-zinc-600">아직 저장한 결정이 없습니다. 새 비교를 시작해 보세요.</p>}
    {selected.length === 0 && <p className="rounded-xl border border-dashed p-6">선택한 상태가 없습니다. 표시할 상태를 선택하세요.</p>}
    {selected.length > 0 && counts.some(item => (item.count ?? 0) > 0) && !counts.some(item => item.status && selected.includes(item.status) && (item.count ?? 0) > 0) && <p>선택한 상태의 결정이 없습니다.</p>}
    {statuses.filter(s => selected.includes(s)).map(s => {
      const open = opened.includes(s);
      const pageParam = Number(params.get(`page.${s}`) ?? 0);
      const page = Number.isInteger(pageParam) && pageParam >= 0 && pageParam <= 1000000 ? pageParam : 0;
      return <section key={s} aria-label={labels[s]} className="overflow-hidden rounded-xl border border-zinc-200 bg-white">
        <h3><button aria-expanded={open} aria-controls={`group-${s}`} className="flex w-full items-center justify-between gap-4 p-4 text-left font-semibold hover:bg-zinc-50" onClick={() => changeQuery(p => p.set("open", (open ? opened.filter(item => item !== s) : [...opened.filter(Boolean), s]).join(",")))}>
          <span>{labels[s]} <span className="ml-2 text-sm font-normal text-zinc-500">{counts.find(item => item.status === s)?.count ?? 0}건</span></span><span aria-hidden="true">{open ? "−" : "+"}</span>
        </button></h3>
        <div id={`group-${s}`} hidden={!open}>{open && <DecisionGroup key={`${s}-${page}-${sort}-${direction}-${revision}`} onChanged={() => { setChosen({}); setRevision(v => v + 1); }} status={s} page={page} sort={sort} direction={direction} onDelete={setDeleting} chosen={chosen} onToggle={toggle} updateVisible={updateVisible} selecting={selecting} onSelectItems={selectItems} onUnselectItems={unselectItems} onSelectStatus={() => selectAll([s], s)} statusSelected={!!scopeIsSelected([s])} allSelected={!!scopeIsSelected(selected)} onSelectAll={() => selectAll(selected, s)} onClear={() => setChosen({})} onBulkDelete={() => setBulkOpen(true)} selectionError={errorStatus === s ? selectionError : ""} />}</div>
      </section>;
    })}
    {deleting && <DeleteDialog decision={deleting} onCancel={() => setDeleting(undefined)} onDeleted={() => { if (deleting.id) setChosen(previous => { const next = { ...previous }; delete next[deleting.id!]; return next; }); setDeleting(undefined); setRevision(v => v + 1); onDeleted(); }} onRefresh={onRefresh} />}
    {bulkOpen && <BulkDeleteDialog decisions={Object.values(chosen)} onCancel={() => setBulkOpen(false)} onFinished={remaining => { setChosen(Object.fromEntries(remaining.filter(item => item.id).map(item => [item.id!, item]))); setRevision(v => v + 1); }} />}
  </>;
}

function DecisionGroup({ onChanged, status, page, sort, direction, onDelete, chosen, onToggle, updateVisible, selecting, onSelectItems, onUnselectItems, onSelectStatus, statusSelected, allSelected, onSelectAll, onClear, onBulkDelete, selectionError }: { onChanged: () => void; status: Status; page: number; sort: Sort; direction: "ASC" | "DESC"; onDelete: (d: Summary) => void; chosen: Record<string, Summary>; onToggle: (d: Summary, checked: boolean) => void; updateVisible: (status: Status, ids: string[]) => void; selecting: boolean; onSelectItems: (items: Summary[]) => void; onUnselectItems: (items: Summary[]) => void; onSelectStatus: () => void; statusSelected: boolean; allSelected: boolean; onSelectAll: () => void; onClear: () => void; onBulkDelete: () => void; selectionError: string }) {
  const [result, setResult] = useState<Page>();
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api.GET("/api/v1/decisions", { params: { query: { statuses: apiStatuses([status]), page, size: 10, sort, direction } }, signal: controller.signal }).then(({ data }) => {
      if (controller.signal.aborted) return;
      if (!data) { setError(true); return; }
      setError(false); setResult(data);
      if (data.page !== undefined && data.page !== page) changeQuery(p => p.set(`page.${status}`, String(data.page)));
    }).catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => controller.abort();
  }, [status, page, sort, direction, attempt]);
  useEffect(() => {
    updateVisible(status, result?.items?.flatMap(item => item.id ? [item.id] : []) ?? []);
    return () => updateVisible(status, []);
  }, [status, result, updateVisible]);
  if (error) return <div role="alert" className="p-4">결정을 불러오지 못했습니다. <button className={button} onClick={() => setAttempt(v => v + 1)}>다시 시도</button></div>;
  if (!result) return <p role="status" className="p-4">결정을 불러오는 중입니다.</p>;
  const pageSelected = !!result.items?.length && result.items.every(item => item.id && chosen[item.id]);
  return <div className="border-t border-zinc-100 p-4">
    <div className="mb-4 flex flex-wrap items-center gap-2" aria-label={`${labels[status]} 결정 선택`}>
      <button className={pressedButton} aria-pressed={pageSelected} aria-disabled={selecting} disabled={!result.items?.length} onClick={() => { if (selecting) return; if (pageSelected) onUnselectItems(result.items ?? []); else onSelectItems(result.items ?? []); }}>이 페이지 선택</button>
      <button className={pressedButton} aria-pressed={statusSelected} aria-disabled={selecting} disabled={!result.totalElements} onClick={onSelectStatus}>이 상태 전체 선택</button>
      <button className={pressedButton} aria-pressed={allSelected} aria-disabled={selecting} onClick={onSelectAll}>선택한 상태 결정 모두 선택</button>
      <button className={button} disabled={selecting || !Object.keys(chosen).length} onClick={onClear}>결정 선택 해제</button>
      <button className={button + " text-red-700"} disabled={selecting || !Object.keys(chosen).length} onClick={onBulkDelete}>선택 삭제</button>
    </div>
    {selectionError && <p role="alert" className="mb-4 text-red-700">{selectionError}</p>}
    {!result.items?.length ? <p className="text-sm text-zinc-500">이 상태의 결정이 없습니다.</p> : <ul className="divide-y divide-zinc-100">{result.items.map(d => <li key={d.id} className="flex items-center gap-3 py-3 first:pt-0">
      <input type="checkbox" aria-label={`${d.title} 선택`} disabled={selecting} checked={!!d.id && !!chosen[d.id]} onChange={e => onToggle(d, e.target.checked)} />
      <Link href={`/decisions/${d.id}`} className="min-w-0 flex-1 rounded-md py-1 hover:text-emerald-700">
        <span className="block break-words font-medium">{d.title}</span><span className="mt-1 block text-sm text-zinc-500">{categories[d.templateKey ?? ""] ?? "직접 비교"} · {labels[status]}</span>
        <span className="block text-xs text-zinc-600">{d.status === "CHOSEN" ? "후보 선택" : d.status === "ABANDONED" ? "구매하지 않음" : labels[d.status ?? "DRAFT"]}</span><span className="mt-1 block text-xs text-zinc-500">생성 {date(d.createdAt)} · 수정 {date(d.updatedAt)}</span>
      </Link>{(d.status === "CHOSEN" || d.status === "ABANDONED" || d.status === "DEFERRED") && d.id && d.version !== undefined && <ArchiveButton id={d.id} version={d.version} onChanged={onChanged} />}<button className={button + " shrink-0 text-red-700"} aria-label={`${d.title} 삭제`} onClick={() => onDelete(d)}>삭제</button>
    </li>)}</ul>}
    {(result.totalPages ?? 0) > 0 && <nav aria-label={`${labels[status]} 페이지`} className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-zinc-100 pt-4">
      <button className={button} disabled={!result.page} onClick={() => changeQuery(p => p.set(`page.${status}`, String((result.page ?? 0) - 1)))}>이전</button>
      <span className="text-sm">{(result.page ?? 0) + 1} / {result.totalPages} 페이지 · {result.totalElements}건</span>
      <button className={button} disabled={(result.page ?? 0) + 1 >= (result.totalPages ?? 0)} onClick={() => changeQuery(p => p.set(`page.${status}`, String((result.page ?? 0) + 1)))}>다음</button>
    </nav>}
  </div>;
}

function BulkDeleteDialog({ decisions, onCancel, onFinished }: { decisions: Summary[]; onCancel: () => void; onFinished: (remaining: Summary[]) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [snapshot] = useState(decisions);
  const [busy, setBusy] = useState(false);
  const [report, setReport] = useState<{ success: number; failures: { item: Summary; reason: string }[] }>();
  useEffect(() => {
    const element = dialog.current;
    const trigger = document.activeElement;
    element?.showModal();
    return () => { element?.close(); if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus(); };
  }, []);
  async function remove() {
    setBusy(true);
    let success = 0;
    const failures: { item: Summary; reason: string }[] = [];
    for (const item of snapshot) {
      try {
        const result = await api.DELETE("/api/v1/decisions/{decisionId}", { params: { path: { decisionId: item.id! }, query: { expectedVersion: item.version! } } });
        if (result.response.ok || result.response.status === 404) success++;
        else failures.push({ item, reason: result.response.status === 409 ? "다른 곳에서 변경됨" : "삭제 실패" });
      } catch { failures.push({ item, reason: "결과 확인 불가: 목록을 새로고침해 확인하세요" }); }
    }
    setReport({ success, failures });
    onFinished(failures.map(failure => failure.item));
    setBusy(false);
  }
  return <dialog ref={dialog} aria-labelledby="bulk-delete-title" onCancel={e => { e.preventDefault(); if (!busy) onCancel(); }} className="fixed inset-0 m-auto w-[calc(100%-2rem)] max-w-lg rounded-2xl p-6 shadow-xl backdrop:bg-black/40">
    <h2 id="bulk-delete-title" className="text-xl font-semibold">선택한 결정 {snapshot.length}개를 휴지통으로 이동할까요?</h2>
    <p className="mt-3 text-sm">개인화 활용과 후속 작업을 중단합니다. 30일 동안 복구할 수 있으며 이후 후보·평가·회고·알림·성향 기억을 영구 삭제합니다. 이미 전송된 요청과 다른 결정에 적용된 값은 유지됩니다.</p>
    {report && <div role="status" className="mt-4"><p>{report.success}개 삭제 또는 이미 삭제됨 · {report.failures.length}개 확인 필요</p>{report.failures.length > 0 && <><ul className="mt-2 max-h-48 overflow-auto">{report.failures.map(({ item, reason }) => <li key={item.id}>{item.title}: {reason}</li>)}</ul><p>변경되었거나 결과를 확인할 수 없는 결정은 목록을 새로고침해 확인하세요.</p></>}</div>}
    <div className="mt-5 flex justify-end gap-2"><button className={button} disabled={busy} onClick={onCancel}>{report ? "닫기" : "취소"}</button>{!report && <button className="rounded-lg bg-red-700 px-4 py-2 text-sm text-white disabled:opacity-50" disabled={busy} onClick={remove}>{busy ? "삭제 중…" : "휴지통으로 이동"}</button>}</div>
  </dialog>;
}

function DeleteDialog({ decision, onCancel, onDeleted, onRefresh }: { decision: Summary; onCancel: () => void; onDeleted: () => void; onRefresh: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const element = dialog.current;
    const trigger = document.activeElement;
    element?.showModal();
    return () => { element?.close(); if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus(); };
  }, []);
  async function remove() {
    setBusy(true); setError("");
    try {
      const result = await api.DELETE("/api/v1/decisions/{decisionId}", { params: { path: { decisionId: decision.id! }, query: { expectedVersion: decision.version! } } });
      if (result.response.ok || result.response.status === 404) { onDeleted(); return; }
      setError(result.response.status === 409 ? "결정이 변경되었습니다. 목록을 새로고침하고 다시 확인하세요." : "삭제하지 못했습니다. 다시 시도해 주세요.");
    } catch { setError("서버에 연결하지 못했습니다. 목록을 새로고침하여 삭제 여부를 확인하세요."); }
    finally { setBusy(false); }
  }
  return <dialog ref={dialog} aria-labelledby="delete-title" aria-describedby="delete-description" onCancel={e => { e.preventDefault(); if (!busy) onCancel(); }} className="fixed inset-0 m-auto w-[calc(100%-2rem)] max-w-lg rounded-2xl p-6 shadow-xl backdrop:bg-black/40">
    <h2 id="delete-title" className="text-xl font-semibold">결정을 휴지통으로 이동할까요?</h2>
    <p className="mt-3 break-words font-medium">{decision.title}</p>
    <p id="delete-description" className="mt-3 text-sm leading-6 text-zinc-600">개인화 활용과 후속 작업을 중단합니다. 30일 동안 복구할 수 있으며 이후 후보·평가·회고·알림·성향 기억을 영구 삭제합니다. 다른 결정에 이미 적용된 값과 전송된 메일은 취소되지 않습니다.</p>
    {error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}
    <div className="mt-5 flex flex-wrap justify-end gap-2"><button className={button} disabled={busy} onClick={onCancel}>취소</button>{error && <button className={button} disabled={busy} onClick={onRefresh}>목록 새로고침</button>}<button className="rounded-lg bg-red-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50" disabled={busy} onClick={remove}>{busy ? "삭제 중…" : "휴지통으로 이동"}</button></div>
  </dialog>;
}
