import Link from "next/link";
import { redirect } from "next/navigation";
import { DecisionCreateForm } from "@/features/decision-create/decision-create-form";
import { ApiError, serverApi } from "@/shared/api/server";

export const dynamic = "force-dynamic";

export default async function NewDecisionPage() {
  try {
    await serverApi.me();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/login");
    throw error;
  }
  return (
    <main className="mx-auto min-h-screen w-full max-w-3xl px-6 py-14">
      <Link href="/" className="text-sm text-zinc-500 hover:text-zinc-900">← 돌아가기</Link>
      <h1 className="mt-6 text-3xl font-semibold tracking-tight">무엇을 고르고 있나요?</h1>
      <p className="mt-2 text-zinc-600">상황을 적고 제품명으로 후보를 찾아보세요. 정확한 제품을 선택하면 비교를 준비합니다.</p>
      <DecisionCreateForm />
    </main>
  );
}
