"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/shared/api/client";

export function DecisionCreateForm() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [templateKey, setTemplateKey] = useState<"LAPTOP" | "CUSTOM">("LAPTOP");
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(undefined);
    const { data, error } = await api.POST("/api/v1/decisions", {
      body: { title, templateKey },
    });
    setPending(false);
    if (error || !data?.id) {
      setError("Decision을 만들지 못했습니다. backend 상태와 입력을 확인하세요.");
      return;
    }
    router.push(`/decisions/${data.id}`);
  }

  return (
    <form onSubmit={submit} className="mt-8 space-y-6 rounded-3xl border border-zinc-200 bg-white p-7 shadow-sm">
      <label className="block">
        <span className="text-sm font-medium text-zinc-700">무엇을 결정하려고 하나요?</span>
        <input required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="예: 개발용 노트북 선택" className="mt-2 w-full rounded-xl border border-zinc-300 px-4 py-3 outline-none focus:border-emerald-500" />
      </label>
      <fieldset>
        <legend className="text-sm font-medium text-zinc-700">시작 template</legend>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          {(["LAPTOP", "CUSTOM"] as const).map((key) => (
            <label key={key} className={`cursor-pointer rounded-2xl border p-4 ${templateKey === key ? "border-emerald-500 bg-emerald-50" : "border-zinc-200"}`}>
              <input type="radio" className="sr-only" checked={templateKey === key} onChange={() => setTemplateKey(key)} />
              <span className="font-semibold">{key === "LAPTOP" ? "노트북 비교" : "Custom Decision"}</span>
              <p className="mt-1 text-sm text-zinc-600">{key === "LAPTOP" ? "핵심 criterion과 constraint starter를 제공합니다." : "개인 dogfooding을 위한 빈 비교형 decision입니다."}</p>
            </label>
          ))}
        </div>
      </fieldset>
      {error && <p className="text-sm text-red-700">{error}</p>}
      <button disabled={pending} className="rounded-full bg-zinc-950 px-5 py-3 font-semibold text-white disabled:opacity-50">{pending ? "만드는 중…" : "Decision 만들기"}</button>
    </form>
  );
}
