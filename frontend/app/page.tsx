import Link from "next/link";
import { serverApi } from "@/shared/api/server";
import type { DecisionSummary } from "@/shared/api/types";

export const dynamic = "force-dynamic";

export default async function Home() {
  let decisions: DecisionSummary[];
  try {
    decisions = await serverApi.decisions();
  } catch {
    decisions = [];
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-10 px-6 py-14">
      <header className="flex flex-col gap-4 border-b border-zinc-200 pb-8 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.22em] text-emerald-700">Decision Assistant</p>
          <h1 className="mt-2 text-4xl font-semibold tracking-tight text-zinc-950">판단을 구조화하고, 결과까지 돌아보세요.</h1>
          <p className="mt-3 max-w-2xl text-zinc-600">후보와 기준을 직접 확인한 뒤 Algorithm과 LLM의 독립적인 관점을 비교합니다.</p>
        </div>
        <Link href="/decisions/new" className="rounded-full bg-zinc-950 px-5 py-3 text-sm font-semibold text-white hover:bg-zinc-800">
          새 Decision
        </Link>
      </header>

      <section>
        <h2 className="text-lg font-semibold text-zinc-900">최근 Decision</h2>
        {decisions.length === 0 ? (
          <div className="mt-4 rounded-2xl border border-dashed border-zinc-300 bg-white p-10 text-center text-zinc-500">
            아직 저장된 Decision이 없거나 backend가 실행 중이 아닙니다.
          </div>
        ) : (
          <div className="mt-4 grid gap-3">
            {decisions.map((decision) => (
              <Link key={decision.id} href={`/decisions/${decision.id}`} className="flex items-center justify-between rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm hover:border-emerald-400">
                <div>
                  <h3 className="font-semibold text-zinc-900">{decision.title}</h3>
                  <p className="mt-1 text-sm text-zinc-500">{decision.templateKey} · {decision.status}</p>
                </div>
                <span className="text-zinc-400">→</span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
