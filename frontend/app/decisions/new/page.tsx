import Link from "next/link";
import { redirect } from "next/navigation";
import { ClickIntake } from "@/features/decision-create/click-intake";
import { ApiError, serverApi } from "@/shared/api/server";

export const dynamic = "force-dynamic";

export default async function NewDecisionPage({ searchParams }: { searchParams: Promise<{ category?: string }> }) {
  const { category } = await searchParams;
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
      <ClickIntake initialCategory={category} />
    </main>
  );
}
