import type { EvaluationResponse } from "@/shared/api/types";

export function EvaluationResultView({ evaluation }: { evaluation: EvaluationResponse }) {
  const algorithm = evaluation.algorithmResult;
  const sorted = [...(algorithm?.options ?? [])].sort((left, right) => (right.score ?? 0) - (left.score ?? 0));

  return (
    <section className="space-y-5 rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-xl font-semibold">평가 결과</h2>
        <span className="font-mono text-xs text-zinc-400">snapshot {evaluation.inputHash?.slice(0, 12)}</span>
      </div>
      <div>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">Algorithmic Evaluation</h3>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          {sorted.map((option) => (
            <article key={option.optionId} className={`rounded-2xl border p-4 ${option.optionId === algorithm?.recommendedOptionId ? "border-emerald-500 bg-emerald-50" : "border-zinc-200"}`}>
              <div className="flex items-center justify-between">
                <strong>{option.optionName}</strong>
                <span className="text-xl font-semibold">{option.feasible ? option.score?.toFixed(2) : "제외"}</span>
              </div>
              {option.usesEstimatedValues && <p className="mt-1 text-xs text-amber-700">추정값 포함</p>}
              {(option.failedConstraints?.length ?? 0) > 0 && <p className="mt-1 text-xs text-red-700">Constraint: {option.failedConstraints?.join(", ")}</p>}
              <ul className="mt-3 space-y-1 text-xs text-zinc-600">
                {option.contributions?.map((item) => <li key={item.criterionId}>{item.criterionName}: +{item.weightedPoints}</li>)}
              </ul>
            </article>
          ))}
        </div>
      </div>
      <div className="border-t border-zinc-200 pt-5">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-zinc-500">LLM Assessment · {evaluation.llmStatus}</h3>
        {evaluation.llmAssessment ? (
          <div className="mt-3 rounded-2xl bg-zinc-950 p-5 text-zinc-100">
            <p>{evaluation.llmAssessment.summary}</p>
            <p className="mt-3 text-sm text-zinc-300">{evaluation.llmAssessment.abstained ? "결론 보류" : `추천 Option: ${evaluation.llmAssessment.recommendedOptionId}`}</p>
            <div className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
              <ResultList title="핵심 요인" items={evaluation.llmAssessment.decisiveFactors} />
              <ResultList title="Trade-off" items={evaluation.llmAssessment.tradeOffs} />
              <ResultList title="Assumption" items={evaluation.llmAssessment.assumptions} />
              <ResultList title="Risk" items={evaluation.llmAssessment.risks} />
            </div>
          </div>
        ) : (
          <p className="mt-2 text-sm text-zinc-600">{evaluation.llmError ?? "LLM 평가를 요청하지 않았습니다."}</p>
        )}
      </div>
    </section>
  );
}

function ResultList({ title, items }: { title: string; items?: string[] }) {
  return <div><strong>{title}</strong><ul className="mt-1 list-disc pl-5 text-zinc-300">{items?.map((item) => <li key={item}>{item}</li>)}</ul></div>;
}
