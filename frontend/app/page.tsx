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
          <h1 className="mt-2 text-4xl font-semibold tracking-tight text-zinc-950">나에게 맞는 제품 찾기</h1>
        </div>
        <div className="flex flex-col items-end gap-3">
          <UserMenu email={user.email ?? ""} />
          <div className="flex gap-2">
            <Link href="/preferences" className="rounded-full border border-zinc-300 px-4 py-3 text-sm font-semibold text-zinc-700 hover:border-zinc-500">나의 결정 기준</Link>
            <Link href="/decisions/new" className="rounded-full bg-zinc-950 px-5 py-3 text-sm font-semibold text-white hover:bg-zinc-800">새 비교 시작</Link>
          </div>
        </div>
      </header>

      <nav aria-label="제품군 선택" className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {Object.entries(categoryLabels).map(([key, label]) => <Link key={key} href={`/decisions/new?category=${key}`} className="rounded-2xl border border-zinc-200 bg-white p-5 font-semibold hover:border-emerald-600 hover:bg-emerald-50">{label}<span aria-hidden="true" className="mt-4 block text-emerald-700">→</span></Link>)}
      </nav>
      <section>
        <h2 className="text-lg font-semibold text-zinc-900">최근 비교</h2>
        {decisions.length === 0 ? (
          <div className="mt-4 rounded-2xl border border-dashed border-zinc-300 bg-white p-10 text-center text-zinc-500">
            아직 저장한 비교가 없습니다.
          </div>
        ) : (
          <div className="mt-4 grid gap-3">
            {decisions.map((decision) => (
              <Link key={decision.id} href={`/decisions/${decision.id}`} className="flex items-center justify-between rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm hover:border-emerald-400">
                <div>
                  <h3 className="font-semibold text-zinc-900">{decision.title}</h3>
                  <p className="mt-1 text-sm text-zinc-500">{categoryLabels[decision.templateKey ?? ""] ?? "직접 비교"} · {statusLabels[decision.status ?? ""] ?? decision.status}</p>
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

const categoryLabels: Record<string, string> = { LAPTOP: "노트북", PHONE: "휴대폰", TABLET: "태블릿", MONITOR: "모니터", EARBUDS: "무선 이어폰" };
const statusLabels: Record<string, string> = { DRAFT: "비교 중", DEFERRED: "보류", CHOSEN: "선택 완료", ABANDONED: "구매하지 않음", ARCHIVED: "보관됨" };
