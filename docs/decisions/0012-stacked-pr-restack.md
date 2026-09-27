# ADR 0012: 사람의 순차 squash merge와 이벤트 기반 restack

- Status: Accepted
- Date: 2026-09-28
- Decision: human이 전용 GitHub App과 Actions를 사용하는 AI 없는 자동 restack 계획을 승인했다. PR 병합은 human만 수행한다.

## 배경

의존하는 PR을 미리 쌓으면 선행 PR의 squash merge가 commit identity를 바꾼다. 후속 PR을 그대로 main에 병합하면 이미 반영한 변경을 다시 비교하면서 충돌할 수 있다. 여러 작업의 병렬 준비를 막거나 마지막 PR 하나로 전체 변경을 합치는 대신 PR별 리뷰·검증 단위를 유지한다.

## 결정

- 명시적으로 등록된 같은 저장소의 일렬 stack만 처리한다. 기반 PR이 main에 병합되면 전체 후속 stack의 고유 commit을 순서대로 새 기반에 replay한다. 부모의 병합 전 수정 전파와 AI 충돌 해결은 제외한다.
- 전용 GitHub App의 설치 token으로 후속 브랜치·PR base·check·상태 댓글을 갱신한다. CI와 실제 squash merge는 별도 단계이며 main 갱신 ruleset으로 App의 병합을 막는다.
- 키는 main만 접근할 수 있는 GitHub Environment에 둔다. privileged 작업은 main의 스크립트만 실행하며 PR 코드·artifact·hook은 실행하지 않는다. 앱 런타임의 identity 기반 GCP secret 경계는 바꾸지 않는다.
- 전용 상태 브랜치에 기존 기반 SHA와 transaction을 기록한다. Git refs는 명시적인 lease가 있는 atomic push로 갱신하고 복구용 tag를 함께 보존한다. PR base API 변경은 journal로 재개한다. 상태 브랜치는 배포 단위가 아니다.
- 충돌은 원격 갱신 전 중단하고, push 후 CI 실패는 rollback 없이 보고한다. 현재 head/base에 대한 `validate`와 App의 `stack-ready`를 모두 통과한 PR만 human이 병합한다.
- 반복·누락 이벤트는 전체 상태 조정으로 복구한다. 기존 #17·#18은 수동 마무리하고 신규 stack부터 등록한다.

## 대안과 영향

Graphite 같은 외부 stack 서비스 대신 저장소 내 Node.js 스크립트를 선택하여 새 외부 서비스·AI 실행 비용 없이 기존 GitHub CI를 재사용한다. 그 대신 등록 snapshot, 동시 변경, 부분 실패 복구를 repository가 유지·검증한다. 한 PR로 합치는 방식과 달리 PR별 main commit이 유지된다.

등록된 stack의 자동 이력 재작성은 승인된 예외다. 일반 branch의 force push·default branch 직접 push·agent의 PR merge 금지는 유지한다. 미등록 head 변경과 복잡한 이력은 자동 추정하지 않으므로 수동 정리 비용이 남는다. 원격 restack은 사용자 로컬 작업 공간을 수정하지 않는다.

설정, 비상 정지, 복구, 로컬 동기화 절차는 [운영 문서](../stacked-prs.md)를 따른다.
