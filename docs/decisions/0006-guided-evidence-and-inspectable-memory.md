# ADR 0006: Guided evidence와 inspectable preference memory

## 상태

Accepted

## 배경

기존 UI는 Decision aggregate의 모든 Option×Criterion 값, weight, anchor, constraint를 사용자에게 직접 입력하게 했다. 구조화 proposal도 저장 흐름과 연결되지 않아 일반 LLM보다 추가적인 입력 비용이 컸고, satisfaction 중심 outcome은 다음 결정에 재사용할 성향을 설명하기 어려웠다.

## 결정

- 사용자의 한 문장과 공개 후보 URL을 guided intake의 시작점으로 삼는다.
- URL은 명시적 동의 후 `WebEvidenceProvider`를 통해 비동기로 수집한다. 첫 adapter는 Firecrawl이며 공개 HTTP(S), main content, no-cache 요청만 허용한다.
- 수집·AI 값은 origin, verification status, URL, 조회 시점을 가진 미확인 초안으로 저장한다. 사용자 확인값은 자동 작업이 덮어쓰지 않는다.
- 불완전 입력에는 score range와 잠정 조언을 제공하고, 완전성 규칙을 통과한 경우에만 기존 immutable Evaluation을 만든다.
- outcome에서 category별 PreferenceMemory를 제안하고 사용자가 확인한 기억만 다음 Decision의 weight 기본값으로 적용한다. vector DB나 자동 학습 모델을 도입하지 않는다.
- 선택 후 14일·60일에 in-app/email reminder를 만들며 사용자는 개별 reminder와 전체 email을 해제할 수 있다.

## 결과

입력 부담을 줄이면서 출처·불확실성·deterministic evaluation을 유지한다. 대신 외부 provider 비용과 실패 상태, 비동기 merge, consent·retention 정책을 운영해야 한다. 검색 결과는 후보 제안일 뿐 자동으로 Option이나 추천 근거가 되지 않는다.
