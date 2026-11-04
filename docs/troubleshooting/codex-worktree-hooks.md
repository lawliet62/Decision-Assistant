# 연결 worktree에서 Codex Hook이 보이지 않는 경우

## 증상과 근거

Windows의 Codex CLI 0.165.0에서 연결 worktree의 `.codex/hooks.json`에 Hook을 두고 저장소 폴더를 신뢰해도 `/hooks`에는 설치된 Hook이 0개로 표시됐다. 신뢰 대화상자는 연결 worktree 대신 원본 저장소를 신뢰 대상으로 표시했다. 빈 프로젝트 `config.toml` 추가와 연결 worktree의 별도 신뢰 설정도 이 재현에서는 Hook 발견을 해결하지 못했다. Codex 내부의 정확한 경로 선택 원인은 확인하지 못했다.

독립 임시 저장소에서는 동일한 프로젝트 Hook이 발견됐으므로, Hook 파일의 존재만으로 연결 worktree에서 활성화됐다고 판단하지 않는다. 설치된 Codex 버전과 `/hooks`의 설치·활성 개수를 먼저 확인한다.

## 해결과 검증

[개발 workflow](../development.md#codex-worktree-점유)의 사용자 Hook 설치기는 Git 공통 디렉터리를 기준으로 적용 대상을 구분한다. 임시 연결 worktree에서 첫 Codex 세션의 점유, 두 번째 세션의 모델 요청 전 중단, 첫 세션 종료 후 재진입을 확인했다. 등록하지 않은 다른 임시 Git 저장소의 세션도 정상 응답했다.

이 검증은 동일 사용자·동일 머신의 Codex CLI에 한정된다. Hook 미실행, 다른 agent 제품, 다른 머신, 동일 세션 ID의 동시 재개는 별도 보호 대상이 아니다. 새 버전이나 다른 실행 환경에서는 `/hooks`와 실제 차단을 다시 확인한다.
