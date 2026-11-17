# 전용 worktree의 재확인과 다음 예약 진단 준비

2026-11-17 22:14 KST, 최신 main `fbaf1d7` 기반의 별도 `decision-assistant-grand-crane` worktree에서 기존 개발 DB와 공급자 잔여량을 다시 확인했다. 이전 `.worktrees/catalog-refresh-20261117`은 재사용하지 않았다.

## 현재 상태

기존 `backend-postgres-1`은 정지 상태였다. 같은 컨테이너를 시작하고 `BEGIN READ ONLY`로 조회했다. Flyway version 24, 활성 제품 25개, 대표 브랜드 10개, UTC 11월 가격 예약 104회를 확인했다. 활성 제품의 도래한 가격 예약은 0개였고, 25개 개별 가격·사양 예약을 모두 조회했다. 조회 연결 외 다른 DB 연결은 없었다. 전체 서버·migration·결정 정리 worker는 실행하지 않았으며, 조회 후 다른 연결이 0개인 것을 다시 확인해 컨테이너를 정지 상태로 복원했다. 볼륨과 기존 데이터는 보존했다.

| 예약 | 최초 시각(KST) | 25개 모두 도래하는 시각(KST) |
|---|---|---|
| 가격 | 2026-11-19 18:43:08.514713 | 2026-11-19 18:46:14.499935 |
| 사양 | 2026-11-20 20:45:12.868746 | 2026-11-20 22:13:20.454746 |

이는 이번 조회의 예약 단면이다. 이후 실행 전 개별 예약과 기존 실행 여부를 다시 확인한다. worker를 실행하거나 예약을 앞당기지 않았다. 네 실패 제품의 DB 상태도 Sony `PRICE_CONTEXT_MISSING`, Marshall MINOR IV `UNAVAILABLE`, MOTIF II `PRICE_CONTEXT_MISSING`, HT10 `PRODUCT_MISSING`으로 유지됐다. 이번에 가격·사양을 재수집한 결과는 아니다.

기존 Secret Manager의 Firecrawl 키를 프로세스 메모리에서만 사용해 크레딧 조회를 수행했다. 2026-11-17T13:14:58.3555273Z 응답은 잔여 970, 플랜 1,000, 기간 2026-11-09T12:27:48.457Z부터 2026-12-09T12:27:48.457Z까지였다. [공식 API 정의](https://github.com/firecrawl/firecrawl-docs/blob/main/api-reference/v2-openapi.json)의 `/team/credit-usage` 조회이며 수집 요청이 아니다. 앱 월 예약과 공급자 잔여량은 별도 계수다. Firecrawl 수집·CT06 직접 수집·OpenAI 호출·추가 구매는 수행하지 않았다.

## 다음 정상 수집을 위한 변경

Marshall 구매 영역 대조에서 기존 수용 조건을 바꾸지 않고, 누락·중복 영역 수와 금액·버튼 불일치 단계를 남기는 진단을 추가했다. 원문·임의 페이지 문구·가격은 로그에 넣지 않는다. 정상 가격과 구매 영역 누락·중복, 구매 금액과 버튼 문구 불일치, 두 종류의 버튼 비활성 및 정상 통과를 fixture로 확인했다. 기존 `OfficialProductParserTest`와 backend 전체 unit test 246개도 통과했다. 실제 Firecrawl 응답에서 새 로그가 동작하는지는 다음 정상 예약에서 확인해야 한다. [진단 해석](../../troubleshooting/catalog-refresh-invariants.md)을 따른다.

최신 main의 `scripts/check-contract.mjs`는 여전히 객체 속성 순서를 그대로 비교한다. 이전 OpenAPI 순서 문제를 이번 작업에서 재현하거나 수정하지 않았으며 Issue도 만들지 않았다.

## 목표별 미완료 범위

- 가격 자동 갱신: 현재 도래 항목이 없어 실행하지 않았다. 과거 21/25 성공을 현재의 신규 검증 성공으로 재집계하지 않는다. 네 제품의 실패 원인과 다음 실제 결과 확인이 남아 있다.
- 사양 출처 점검: [11월 16일 실행](https://github.com/lawliet62/Decision-Assistant/actions/runs/43859149400)을 GitHub helper로 다시 조회해 `completed / failure`와 출처 점검 단계 실패를 확인했다. 새 출처 점검은 실행하지 않았다. 기존 GitHub 점검과 DB 사양 갱신은 별도 경로이며 문구 존재를 사양 검증으로 간주하지 않는다.
- 사양 확인 시각: 이번에 사양을 재검증하거나 확인 시각을 갱신하지 않았다. 기한 도래 후 기존 검증기와 실제 DB·API 결과를 대조해야 한다.
- 국내 공식 판매처 확대: 활성 25개·10개 브랜드 범위를 유지한다. Jabra는 보류하며 새 판매처의 안정성을 입증한 변경은 없다.

이번 진단 보강만으로 전체 목표나 장기 자동 운영이 완료된 것은 아니다. 코드 유지 시간과 에이전트 총비용을 비교할 새 근거도 없어 기존 코드 중심 갱신을 유지한다.
