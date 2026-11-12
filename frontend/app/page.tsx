import Link from "next/link";
import { redirect } from "next/navigation";
import { UserMenu } from "@/features/auth/user-menu";
import { ApiError, serverApi } from "@/shared/api/server";
import { Suspense } from "react";
import { DecisionList } from "@/features/decision-list/decision-list";
import { OfficialPolicyNotice } from "@/features/product-lookup/official-policy-notice";

export const dynamic = "force-dynamic";

export default async function Home() {
  let user: Awaited<ReturnType<typeof serverApi.me>>;
  let capabilities: Awaited<ReturnType<typeof serverApi.lookupCapabilities>> | undefined;
  try {
    [user, capabilities] = await Promise.all([serverApi.me(), serverApi.lookupCapabilities().catch(() => undefined)]);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/login");
    throw error;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-10 px-6 py-14">
      <header className="flex flex-col gap-4 border-b border-zinc-200 pb-8 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.22em] text-emerald-700">Decision Assistant</p>
          <h1 className="mt-2 break-keep text-3xl font-semibold tracking-tight text-zinc-950 sm:text-4xl">나에게 맞는 제품 찾기</h1>
        </div>
        <div className="flex flex-col items-end gap-3">
          <UserMenu email={user.email ?? ""} />
          <div className="flex gap-2">
            <Link href="/preferences" className="rounded-full border border-zinc-300 px-4 py-3 text-sm font-semibold text-zinc-700 hover:border-zinc-500">나의 결정 기준</Link>
            <Link href="/decisions/new" className="rounded-full bg-zinc-950 px-5 py-3 text-sm font-semibold text-white hover:bg-zinc-800">새 비교 시작</Link>
          </div>
        </div>
      </header>

      <OfficialPolicyNotice version={capabilities?.discoveryPolicy?.version} placement="home" />
      <nav aria-label="제품군 선택" className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {Object.entries(categoryLabels).filter(([key]) => !capabilities?.catalogEnabled || key === "EARBUDS").map(([key, label]) => <Link key={key} href={`/decisions/new?category=${key}`} className="rounded-2xl border border-zinc-200 bg-white p-5 font-semibold hover:border-emerald-600 hover:bg-emerald-50">{label}<span aria-hidden="true" className="mt-4 block text-emerald-700">→</span></Link>)}
      </nav>
      <Suspense fallback={<p>목록을 불러오는 중입니다.</p>}><DecisionList /></Suspense>
    </main>
  );
}

const categoryLabels: Record<string, string> = { LAPTOP: "노트북", PHONE: "휴대폰", TABLET: "태블릿", MONITOR: "모니터", EARBUDS: "무선 이어폰" };
