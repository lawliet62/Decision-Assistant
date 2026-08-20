import Link from "next/link";
import { DecisionCreateForm } from "@/features/decision-create/decision-create-form";

export default function NewDecisionPage() {
  return (
    <main className="mx-auto min-h-screen w-full max-w-3xl px-6 py-14">
      <Link href="/" className="text-sm text-zinc-500 hover:text-zinc-900">← 돌아가기</Link>
      <h1 className="mt-6 text-3xl font-semibold tracking-tight">새 Decision</h1>
      <p className="mt-2 text-zinc-600">공개 방향은 노트북 비교이며 Custom은 반복 dogfooding을 위한 내부 경로입니다.</p>
      <DecisionCreateForm />
    </main>
  );
}
