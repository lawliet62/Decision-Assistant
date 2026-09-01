import Link from "next/link";
import { redirect } from "next/navigation";
import { ApiError, serverApi } from "@/shared/api/server";
import { PreferenceList } from "@/features/preferences/preference-list";

export const dynamic = "force-dynamic";

export default async function PreferencesPage() {
  try {
    await serverApi.me();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/login");
    throw error;
  }
  return <main className="mx-auto min-h-screen w-full max-w-3xl px-6 py-14">
    <Link href="/" className="text-sm text-zinc-500 hover:text-zinc-900">← 돌아가기</Link>
    <h1 className="mt-6 text-3xl font-semibold tracking-tight">나의 결정 기준</h1>
    <p className="mt-2 text-zinc-600">회고에서 발견한 성향을 확인해야 다음 비교의 기본값으로 사용합니다.</p>
    <PreferenceList />
  </main>;
}
