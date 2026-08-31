import Link from "next/link";
import { redirect } from "next/navigation";
import { ApiError, serverApi } from "@/shared/api/server";

export const dynamic = "force-dynamic";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  let authenticated = false;
  try {
    await serverApi.me();
    authenticated = true;
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
  }
  if (authenticated) redirect("/");

  const { error } = await searchParams;
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-lg items-center px-6 py-16">
      <section className="w-full rounded-3xl border border-zinc-200 bg-white p-8 shadow-sm">
        <p className="text-sm font-medium uppercase tracking-[0.22em] text-emerald-700">Decision Assistant</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight">내 Decision에 로그인</h1>
        <p className="mt-3 text-zinc-600">초대받은 Google 계정 또는 이메일 일회용 코드로 로그인하세요.</p>
        {error && (
          <p className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
            허용되지 않은 계정이거나 처음 사용한 로그인 방식과 다릅니다.
          </p>
        )}
        <Link
          href="/oauth2/authorization/auth0"
          className="mt-7 block rounded-full bg-zinc-950 px-5 py-3 text-center font-semibold text-white hover:bg-zinc-800"
        >
          Google 또는 이메일로 계속
        </Link>
      </section>
    </main>
  );
}
