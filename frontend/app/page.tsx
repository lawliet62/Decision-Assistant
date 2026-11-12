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
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-8 px-5 py-8 sm:px-6 sm:py-10">
      <header className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Link href="/" className="inline-flex items-center gap-3 text-sm font-semibold tracking-wide text-zinc-950"><span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-zinc-950 text-lg text-white">↗</span>Decision Assistant</Link>
        </div>
        <div className="flex flex-col items-start gap-3 sm:items-end">
          <UserMenu email={user.email ?? ""} />
          <div className="flex gap-2">
            <Link href="/preferences" className="rounded-full border border-zinc-300 px-4 py-3 text-sm font-semibold text-zinc-700 hover:border-zinc-500">나의 결정 기준</Link>
            <Link href="/decisions/new" className="rounded-full bg-zinc-950 px-5 py-3 text-sm font-semibold text-white hover:bg-zinc-800">새 비교 시작</Link>
          </div>
        </div>
      </header>

      <section className="home-hero overflow-hidden rounded-[2rem] p-7 sm:p-10" aria-labelledby="welcome-title">
        <p className="text-xs font-semibold tracking-[0.18em] text-emerald-800">더 나은 선택을 위한 작은 시작</p>
        <h1 id="welcome-title" className="mt-4 max-w-xl break-keep text-3xl font-semibold leading-tight tracking-tight text-zinc-950 sm:text-5xl">나에게 맞는 제품 찾기</h1>
        <p className="mt-5 max-w-lg break-keep text-base leading-7 text-zinc-600">많은 선택지 앞에서 고민하고 있다면,<br className="hidden sm:block" /> 나에게 중요한 기준부터 함께 정리해 보세요.</p>
        <Link href="/decisions/new" className="mt-7 inline-flex items-center gap-6 rounded-full bg-zinc-950 px-6 py-3 text-sm font-semibold text-white hover:bg-zinc-800">나의 기준으로 비교 시작 <span aria-hidden="true">↗</span></Link>
        <ol aria-label="비교 진행 과정" className="mt-8 flex flex-wrap gap-x-6 gap-y-3 border-t border-emerald-900/10 pt-5 text-sm text-zinc-700">
          {["조건 정하기", "후보 비교하기", "나의 선택 기록하기"].map((step, index) => <li key={step} className="flex items-center gap-2"><span className="flex size-6 items-center justify-center rounded-full bg-white/80 text-xs font-semibold text-emerald-800">{index + 1}</span>{step}</li>)}
        </ol>
      </section>

      <section aria-labelledby="category-title" className="space-y-4">
      <div><h2 id="category-title" className="text-lg font-semibold text-zinc-950">어떤 제품을 찾고 있나요?</h2><p className="mt-1 text-sm text-zinc-600">제품군을 선택하고 나에게 필요한 조건을 알려주세요.</p></div>
      <nav aria-label="제품군 선택" className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {Object.entries(categoryLabels).filter(([key]) => !capabilities?.catalogEnabled || key === "EARBUDS").map(([key, label]) => <Link key={key} href={`/decisions/new?category=${key}`} className="category-card group rounded-2xl border border-zinc-200 bg-white p-5 font-semibold hover:border-emerald-600 hover:bg-emerald-50"><CategoryIcon category={key} /><span className="mt-4 flex items-center justify-between gap-2">{label}<span aria-hidden="true" className="text-emerald-700">↗</span></span></Link>)}
      </nav>
      </section>
      <OfficialPolicyNotice version={capabilities?.discoveryPolicy?.version} placement="home" />
      <Suspense fallback={<p>목록을 불러오는 중입니다.</p>}><DecisionList /></Suspense>
    </main>
  );
}

const categoryLabels: Record<string, string> = { LAPTOP: "노트북", PHONE: "휴대폰", TABLET: "태블릿", MONITOR: "모니터", EARBUDS: "무선 이어폰" };

function CategoryIcon({ category }: { category: string }) {
  const paths: Record<string, React.ReactNode> = {
    LAPTOP: <><rect x="5" y="4" width="22" height="17" rx="2" /><path d="M2 26h28l-3-5H5z" /></>,
    PHONE: <><rect x="9" y="2" width="14" height="28" rx="3" /><path d="M14 6h4M15 26h2" /></>,
    TABLET: <><rect x="5" y="3" width="22" height="26" rx="3" /><path d="M15 25h2" /></>,
    MONITOR: <><rect x="2" y="4" width="28" height="19" rx="2" /><path d="M16 23v6M10 29h12" /></>,
    EARBUDS: <><path d="M7 5a5 5 0 0 0 0 10v9a2 2 0 0 0 4 0V10a5 5 0 0 0-4-5ZM25 5a5 5 0 0 1 0 10v9a2 2 0 0 1-4 0V10a5 5 0 0 1 4-5Z" /></>,
  };
  return <span className="flex size-12 items-center justify-center rounded-2xl bg-zinc-50 text-emerald-800"><svg aria-hidden="true" width="28" height="28" viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">{paths[category]}</svg></span>;
}
