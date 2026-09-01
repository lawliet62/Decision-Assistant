import Link from "next/link";
import { redirect } from "next/navigation";
import { UserMenu } from "@/features/auth/user-menu";
import { ApiError, serverApi } from "@/shared/api/server";
import type { DecisionSummary } from "@/shared/api/types";

export const dynamic = "force-dynamic";

export default async function Home() {
  let user: Awaited<ReturnType<typeof serverApi.me>>;
  let decisions: DecisionSummary[];
  try {
    [user, decisions] = await Promise.all([serverApi.me(), serverApi.decisions()]);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/login");
    throw error;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-10 px-6 py-14">
      <header className="flex flex-col gap-4 border-b border-zinc-200 pb-8 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.22em] text-emerald-700">Decision Assistant</p>
          <h1 className="mt-2 text-4xl font-semibold tracking-tight text-zinc-950">링크만 모아도, 결정은 더 선명하게.</h1>
          <p className="mt-3 max-w-2xl text-zinc-600">후보를 조사하고 꼭 필요한 질문만 드립니다. 선택과 실제 결과는 다음 결정의 기준으로 남습니다.</p>
        </div>
        <div className="flex flex-col items-end gap-3">
          <UserMenu email={user.email ?? ""} />
          <div className="flex gap-2">
            <Link href="/preferences" className="rounded-full border border-zinc-300 px-4 py-3 text-sm font-semibold text-zinc-700 hover:border-zinc-500">나의 결정 기준</Link>
            <Link href="/decisions/new" className="rounded-full bg-zinc-950 px-5 py-3 text-sm font-semibold text-white hover:bg-zinc-800">새 비교 시작</Link>
          </div>
        </div>
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
