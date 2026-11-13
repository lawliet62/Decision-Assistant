"use client";

import { useId } from "react";

const maximum = 10000;
const button = "min-h-11 min-w-11 rounded-xl border border-zinc-300 bg-white px-2 text-sm hover:bg-zinc-50 focus-visible:outline-2 focus-visible:outline-emerald-700 disabled:opacity-40";

export function BudgetInput({ label, value, onChange }: {
  label: string; value?: number; onChange: (value?: number) => void;
}) {
  const id = useId();
  const amount = value === undefined ? undefined : value / 10000;
  const adjust = (delta: number) => onChange(Math.max(1, Math.min(maximum, Math.round(amount ?? 5) + delta)) * 10000);

  return <fieldset className="min-w-0 space-y-2">
    <legend className="mb-2 text-sm font-semibold">{label} · 만원</legend>
    <div className="flex items-center gap-1">
      {[-5, -1].map(delta => <button key={delta} type="button" className={button} aria-label={`${-delta}만원 줄이기`} disabled={amount !== undefined && amount <= 1} onClick={() => adjust(delta)}>−{-delta}</button>)}
      <input id={id} aria-label={label} aria-describedby={`${id}-hint`} type="text" inputMode="numeric" pattern="[0-9]*" placeholder="제한 없음" value={amount ?? ""}
        onChange={event => {
          const text = event.target.value;
          if (text === "") onChange(undefined);
          else if (/^\d+$/.test(text) && Number(text) >= 1 && Number(text) <= maximum) onChange(Number(text) * 10000);
        }}
        className="min-h-11 min-w-0 w-full rounded-xl border border-zinc-300 bg-white px-2 text-center text-base focus-visible:outline-2 focus-visible:outline-emerald-700" />
      {[1, 5].map(delta => <button key={delta} type="button" className={button} aria-label={`${delta}만원 늘리기`} disabled={amount !== undefined && amount >= maximum} onClick={() => adjust(delta)}>+{delta}</button>)}
    </div>
    <div id={`${id}-hint`} className="text-xs text-zinc-600">
      <p>{value === undefined ? "예산 제한 없음" : `최대 ${value.toLocaleString("ko-KR")}원`}</p>
      <p>1~10,000만원 · 정수 입력 · 빈칸은 제한 없음</p>
    </div>
    <button type="button" className="text-xs text-zinc-600 underline underline-offset-2" onClick={() => onChange(value === undefined ? 50000 : undefined)}>{value === undefined ? "5만원부터 설정" : "제한 없음으로 변경"}</button>
  </fieldset>;
}
