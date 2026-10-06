export function selectRemaining(cases, previous, rows, created, current) {
  for (const key of ["variant", "commit", "jarSha256", "casesSha256"]) {
    if (previous[key] !== current[key]) throw new Error(`이전 실험과 ${key}가 다릅니다.`);
  }
  if ((previous.officialEvidenceConsent === true) !== (current.officialEvidenceConsent === true)) throw new Error("이전 실험과 공식 문서 보완 동의가 다릅니다.");
  if (rows.length !== created.length || rows.length !== previous.completed) {
    throw new Error("생성됐지만 결과가 확정되지 않은 실행이 있어 이어갈 수 없습니다.");
  }
  const previousIds = previous.caseIds ?? cases.map(item => item.id);
  if (rows.some((row, index) => row.caseId !== previousIds[index] || row.caseId !== created[index].caseId
      || row.decisionId !== created[index].decisionId || row.metrics.coverageComplete !== true)) {
    throw new Error("이전 실행 순서 또는 완료 계측을 확인할 수 없습니다.");
  }
  const remaining = previousIds.slice(rows.length);
  if (JSON.stringify(remaining) !== JSON.stringify(previous.unexecuted)) throw new Error("미실행 목록이 일치하지 않습니다.");
  if (new Set(previousIds).size !== previousIds.length || previousIds.some(id => !cases.some(item => item.id === id))) {
    throw new Error("알 수 없거나 중복된 조건이 있습니다.");
  }
  if (!remaining.length) throw new Error("남은 미실행 조건이 없습니다.");
  return cases.filter(item => remaining.includes(item.id));
}
