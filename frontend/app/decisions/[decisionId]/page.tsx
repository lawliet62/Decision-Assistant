import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { DecisionEditor } from "@/features/decision-editor/decision-editor";
import { LegacyDecisionEditor } from "@/features/decision-editor/legacy-decision-editor";
import { ApiError, serverApi } from "@/shared/api/server";

export const dynamic = "force-dynamic";

export default async function DecisionPage({ params }: { params: Promise<{ decisionId: string }> }) {
  const { decisionId } = await params;
  let decision;
  try {
    decision = await serverApi.decision(decisionId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/login");
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
  return (
    <main className="mx-auto min-h-screen w-full max-w-6xl px-5 py-10">
      <Link href="/" className="text-sm text-zinc-500 hover:text-zinc-900">← Decision 목록</Link>
      {decision.guidance
        ? <DecisionEditor initialDecision={decision} />
        : <LegacyDecisionEditor initialDecision={decision} />}
    </main>
  );
}
