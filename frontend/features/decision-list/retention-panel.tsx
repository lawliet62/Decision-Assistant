"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

type Item = components["schemas"]["RetentionItem"];
type Action = NonNullable<components["schemas"]["RetentionChange"]["action"]>;
const button = "rounded-lg border border-zinc-300 px-3 py-2 text-sm disabled:opacity-40";
const format = (value: string) => new Date(value).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" }) + " (KST)";

export function RecoveryNotice() {
  const [restoredTo, setRestoredTo] = useState<string>();
  useEffect(() => {
    const controller = new AbortController();
    api.GET("/api/v1/decisions/recovery-notice", { signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) setRestoredTo(data?.restoredTo); }).catch(() => {});
    return () => controller.abort();
  }, []);
  return restoredTo ? <p role="status" className="rounded-xl border border-amber-300 bg-amber-50 p-4">장애 복구로 서비스 데이터가 {format(restoredTo)} 기준으로 복원되었습니다. 이 시각 이후 생성·수정·삭제한 기록이 반영되지 않았을 수 있습니다.</p> : null;
}

export function ArchiveButton({ id, version, onChanged }: { id: string; version: number; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function archive() {
    if (!window.confirm("기본 목록에서 숨기고 서버에 보관합니다. 허용된 성향 기억은 다음 결정 지원에 계속 활용됩니다. 보관함에서 다시 꺼내거나 삭제할 수 있습니다.")) return;
    setBusy(true); setError("");
    try {
      const result = await api.PATCH("/api/v1/decisions/{decisionId}/retention", { params: { path: { decisionId: id } }, body: { expectedVersion: version, action: "ARCHIVE" } });
      if (result.response.ok) onChanged(); else setError("보관하지 못했습니다. 목록을 새로고침하세요.");
    } catch { setError("연결을 확인하고 목록을 새로고침하세요."); }
    finally { setBusy(false); }
  }
  return <span><button className={button} disabled={busy} onClick={archive}>보관하기</button>{error && <span role="alert">{error}</span>}</span>;
}

export function RetentionPanel({ onChanged }: { onChanged: () => void }) {
  const [view, setView] = useState<"ACTIVE" | "ARCHIVE" | "TRASH">();
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<components["schemas"]["RetentionPage"]>();
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!view) return;
    const controller = new AbortController();
    api.GET("/api/v1/decisions/management", { params: { query: { view, page } }, signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) { setData(data); setError(data ? "" : "목록을 불러오지 못했습니다."); } })
      .catch(() => { if (!controller.signal.aborted) setError("연결을 확인하고 다시 시도하세요."); });
    return () => controller.abort();
  }, [view, page, revision]);
  async function change(item: Item, action: Action | "DELETE") {
    if (!item.id || item.version === undefined) return;
    if (action === "DELETE" && !window.confirm("휴지통으로 이동하고 개인화 활용을 중단합니다. 30일 동안 복구할 수 있으며 이후 연결 기록·성향 기억과 함께 영구 삭제됩니다.")) return;
    setBusy(true); setError("");
    try {
      const result = action === "DELETE"
        ? await api.DELETE("/api/v1/decisions/{decisionId}", { params: { path: { decisionId: item.id }, query: { expectedVersion: item.version } } })
        : await api.PATCH("/api/v1/decisions/{decisionId}/retention", { params: { path: { decisionId: item.id } }, body: { expectedVersion: item.version, action } });
      if (!result.response.ok) { setError("처리하지 못했습니다. 목록을 새로고침해 변경 여부와 복구 기한을 확인하세요."); return; }
      setNotice(action === "RESTORE" ? "삭제 전 상태로 복구했습니다. 이전에 보관한 결정은 보관함에 있습니다." : "변경했습니다.");
      setData(undefined); setRevision(v => v + 1); onChanged();
    } catch { setError("처리 결과를 확인하지 못했습니다. 목록을 새로고침하세요."); }
    finally { setBusy(false); }
  }
  return <section aria-label="결정 보관과 삭제 관리" className="space-y-3">
    <div className="flex flex-wrap gap-2">{(["ACTIVE", "ARCHIVE", "TRASH"] as const).map(v => <button key={v} className={button} disabled={busy} aria-pressed={view === v} onClick={() => { setData(undefined); setPage(0); setView(view === v ? undefined : v); }}>{v === "ACTIVE" ? "개인화 활용 관리" : v === "ARCHIVE" ? "보관함" : "휴지통"}</button>)}</div>
    {view && <div className="space-y-3 rounded-xl border p-4">
      <p className="text-sm">{view === "TRASH" ? "삭제한 결정은 개인화에 활용하지 않습니다. 삭제 후 30일이 지나면 관련 기록과 성향 기억을 영구 삭제합니다." : "기록은 서버에 보관됩니다. 사용자가 확인하고 활용을 허용한 성향 기억만 다음 결정 지원에 사용합니다. 활용을 꺼도 기록은 유지되며, 다른 결정에 이미 적용된 값은 바뀌지 않습니다."}</p>
      {error && <p role="alert">{error}</p>}<p role="status">{notice}</p>
      <button className={button} disabled={busy} onClick={() => setRevision(v => v + 1)}>관리 목록 새로고침</button>
      {!data && !error && <p role="status">목록을 불러오는 중입니다.</p>}
      {data?.items?.length === 0 && <p>결정이 없습니다.</p>}
      <ul className="divide-y">{data?.items?.map(item => <li key={item.id} className="space-y-2 py-3">
        {view === "TRASH" ? <p>{item.title}</p> : <Link className="font-medium" href={`/decisions/${item.id}`}>{item.title}</Link>}
        {view === "TRASH" ? <>
          <p className="text-sm">복구 기한: {item.recoverableUntil ? format(item.recoverableUntil) : "확인 필요"}</p>
          <button className={button} disabled={busy || !item.recoverable} onClick={() => change(item, "RESTORE")}>복구하기</button>
        </> : <div className="flex flex-wrap gap-2">
          <button className={button} disabled={busy} onClick={() => change(item, item.personalizationEnabled ? "PERSONALIZATION_OFF" : "PERSONALIZATION_ON")}>{item.personalizationEnabled ? "개인화 활용 끄기" : "개인화 활용 켜기"}</button>
          {view === "ARCHIVE" && <button className={button} disabled={busy} onClick={() => change(item, "UNARCHIVE")}>기본 목록으로 꺼내기</button>}
          <button className={button} disabled={busy} onClick={() => change(item, "DELETE")}>삭제하기</button>
        </div>}
      </li>)}</ul>
      <nav aria-label="관리 목록 페이지" className="flex gap-3"><button className={button} disabled={busy || page === 0} onClick={() => { setData(undefined); setPage(p => p - 1); }}>이전</button><span>{page + 1} 페이지</span><button className={button} disabled={busy || !data?.hasNext} onClick={() => { setData(undefined); setPage(p => p + 1); }}>다음</button></nav>
    </div>}
  </section>;
}
