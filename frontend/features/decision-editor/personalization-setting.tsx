"use client";

// 완료한 결정 화면의 개인화 활용 설정. 값은 서버가 돌려준 결정 상세에서만 읽고, 변경 후에는 부모가 상세를 다시 불러온다.
// 홈의 RetentionPanel과 같은 PATCH와 버튼 문구를 쓰지만 패널과 독립된 작은 컴포넌트로 둔다.
// 설명은 backend가 이 값을 읽는 두 경로(성향 기억 적용, 과거 결정 참고 목록)를 모두 적는다.
export function PersonalizationSetting({ enabled, onToggle }: { enabled: boolean; onToggle: (next: boolean) => void }) {
  const state = enabled ? "켜짐" : "꺼짐";
  const action = enabled ? "개인화 활용 끄기" : "개인화 활용 켜기";
  return <section aria-labelledby="personalization-setting-title" className="space-y-3 rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm">
    <h2 id="personalization-setting-title" className="text-xl font-semibold">개인화 활용</h2>
    <p className="text-sm font-medium">이 결정의 개인화 활용: {state}</p>
    <p className="text-sm text-zinc-600">켜져 있으면 이 결정에서 나온 성향 기억 중 사용자가 확인한 것을 같은 제품군의 다음 결정에 적용하고, 이 결정의 선택 이유·메모·회고 등 완료 기록을 같은 제품군의 다른 결정에서 ‘과거 결정에서 참고하기’로 볼 수 있습니다. 끄면 기록은 유지되고, 성향 기억 적용과 ‘과거 결정에서 참고하기’(동의 후 실행하는 AI 해석 포함) 대상에서 이 결정을 제외합니다. 다른 결정에 이미 적용된 값은 바뀌지 않습니다.</p>
    <button type="button" aria-label={`${action} (현재 ${state})`} onClick={() => onToggle(!enabled)}
      className="rounded-full border border-zinc-300 bg-white px-4 py-2 text-sm font-medium text-zinc-800 hover:border-zinc-500 disabled:opacity-40">{action}</button>
  </section>;
}
