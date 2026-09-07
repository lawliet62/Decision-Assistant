# ADR 0007: 마지막 MVP 확장 — 이어폰과 제품명 lookup

- Status: Accepted
- Date: 2026-09-08
- Decision: human이 최종 권장안과 Serper/월 1,000회/이어폰 기준의 기본안을 승인했다.

## Context

사용자가 아는 제품명을 후보 URL로 바꾸기 위해 별도 검색·복사해야 하는 흐름이 MVP의 큰 마찰이다. 제품 차별화 확장을 끝내고 기존 구조를 재사용해 배포 가능한 범위로 마무리한다.

## Decision

- `EARBUDS/earbuds-v1`만 추가한다. 가격 50,000~500,000 KRW anchor, 네 만족 예상 rating(음질·착용감·ANC·배터리 적합도), 기본 중요도 1과 비활성 최대 예산/호환성 조건을 사용한다. 주관 rating은 자동 추출하지 않는다.
- 기존 검색/수집 port 분리를 이용해 Serper 검색 adapter를 붙인다. 인증된 동기 POST lookup은 제품명과 명시적 Serper 동의만 받으며 최대 5개 결과를 반환한다. 단기·작은 요청에 새로운 async job/lifecycle을 만들지 않는다.
- 사용자가 선택한 URL과 이름을 기존 create/addSource API에 전달한다. 과거 `sourceUrls` 계약은 유지한다. 검색 snippet은 evidence가 아니며 `CandidateSuggestion`을 임시 검색 저장소로 재해석하지 않는다.
- 기존 Firecrawl 수집/자동 대안 검색의 provider와 동의는 그대로 유지한다. Serper 동의는 별도이며 기존 Decision 동의가 Serper 전송을 허용하지 않는다.
- 익명 월 카운터 한 테이블로 UTC 월 1,000회 상한을 관리한다. 별도 transaction에서 예약한 호출은 실패 시에도 환불하지 않는다. 자동 재시도와 유료 구매/충전은 하지 않는다.
- 화면 단계화는 frontend 상태다. aggregate, evaluator, historical snapshot, lifecycle은 재설계하지 않는다.

## Alternatives and consequences

- `WebEvidenceProvider`에 Serper를 합성하면 기존 자동 대안 검색까지 새 provider로 전송할 위험이 있다. 작은 port 분리와 qualifier 고정으로 동의 경계를 유지한다.
- 기존 `CandidateSuggestion` job을 재사용하면 검색 결과마다 Decision 생성·저장·상태 처리가 필요하다. 동기 lookup은 timeout 동안 HTTP 연결을 점유하지만 최대 2건, 8초로 제한되고 별도 workflow migration이 없다.
- 월 카운터를 메모리에만 두면 재시작·동시 요청으로 비용 상한을 넘을 수 있다. additive V7의 작은 운영 테이블로 해결하며 기존 aggregate 데이터를 backfill하지 않는다. 사용자별 burst 제한은 단일 instance 메모리에 둔다.
- 이 상한은 Serper 호출에만 적용된다. 외부 계정에서 직접 쓴 key, Firecrawl와 AI 비용은 별도 운영 책임이다. 실제 provider smoke와 개인정보/배포 설정은 [runbook](../deployment.md)의 배포 전 점검 대상이다.

## Boundary

ADR 0006의 guided workspace를 폐기하지 않고 입력 진입점만 확장한다. catalog/entity resolution, 전체 시장 추천, 후기 수집, 가격·재고·공식 유통 보장, evaluator 교체와 preference learning은 범위 밖이다. 추가 기능 개발보다 배포·안정화·문서화에 집중한다.
