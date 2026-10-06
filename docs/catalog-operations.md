# 카탈로그 등록·갱신·관측 운영

기본 추천은 이어폰 5개로 시작한다. 별도 캐시·관리 UI는 없으며 `backend/src/main/resources/catalog/earbuds.json`이 검토 가능한 등록 원본이다. 정책은 [ADR 0016](decisions/0016-curated-catalog.md), 초기 근거와 성능은 [평가](evaluations/catalog-2026-10-06/README.md)를 따른다.

## 첫 실행과 가격 갱신

기본 `CATALOG_ENABLED=true`, `CATALOG_REFRESH_ENABLED=false`다. offline에서도 등록 사양을 볼 수 있지만 초기 가격은 없으므로 예산 충족 추천은 없다. 과거 가격을 seed에 넣어 최신 판매가처럼 제공하지 않는다.

실제 가격 갱신은 기존 [Secret 설정](deployment.md#secret-management)을 완료한 환경에서 다음 **비밀 아닌 설정**으로 활성화한다. Firecrawl 키는 기존 Secret Manager에서만 읽는다. 검색·AI 활성화는 필요 없다.

```dotenv
DECISION_ASSISTANT_CATALOG_ENABLED=true
DECISION_ASSISTANT_CATALOG_REFRESH_ENABLED=true
DECISION_ASSISTANT_CATALOG_MONTHLY_LIMIT=100
DECISION_ASSISTANT_WEB_ENABLED=true
FIRECRAWL_SECRET_VERSION=1
```

초기 2주 관측은 월 상한 100회를 사용한다. 5개 × 14일은 약 70회다. 기본 설정값 200은 5개의 월 운영 여유를 위한 상한이며 가격·provider 과금 단위를 보장하는 금액 상한은 아니다. 기존 사용자 수집·다른 프로그램의 Firecrawl 사용과는 별도이므로 계정 비용도 확인한다.

worker는 기본 60초마다 최대 한 제품을 claim하고 제품별 시도 간격은 24시간이다. 초기 5개는 약 5분에 걸쳐 시도한다. `catalog_monthly_usage`는 UTC 달을 사용한다. 실패·중단도 예약 호출을 소비하고 같은 제품을 즉시 재시도하지 않는다. 단순 재시작으로 호출 한도가 초기화되지 않는다. 스케줄 지연이나 실패로 가격이 24시간을 넘으면 잠시 UNKNOWN으로 떨어질 수 있다.

기동 후 `catalog_observations`에서 성공/실패를 확인하고 실제 화면의 모델·구매 링크·확인 가격을 대조한다. HTTP 200만으로 판매가 검증 성공으로 취급하지 않는다. strict parser가 구조를 지원하지 못하면 가격을 수동 조작해서 통과시키지 않고 원인을 수정하거나 등록 제품을 비활성화한다.

## 제품·사양 관리

1. 공식 제품명·세대·구성·판매 URL과 `OfficialProductParser`의 identity를 대조한다. 유사한 제품명이나 지역별 별칭만으로 사양을 복사하지 않는다.
2. 공식 사양 출처의 짧은 원문 인용과 확인 시각을 `facts[].evidence`에 넣고 HARDWARE/SOFTWARE를 선택한다. ANC 지원은 음질·ANC 성능 점수가 아니다. 앱·OS 관련 호환성은 SOFTWARE다.
3. 수정 시 해당 제품 `revision`을 증가시킨다. 같은 revision의 다른 내용은 기동을 거절하며 낮은 revision은 최신 DB 등록을 되돌리지 않는다. 비활성화는 항목 삭제 대신 `active:false`와 revision 증가를 사용한다.
4. 코드 리뷰·테스트 후 배포/재시작한다. 제품 URL 또는 identity가 바뀌면 기존 가격을 초기화한다. 운영자 재점검 없이는 `fetchedAt`만 갱신하지 않는다. 이미 생성한 개인 비교의 근거는 그대로 남는다.

등록은 classpath 또는 운영자가 지정한 로컬 `file:` manifest만 읽는다. 사용자 입력 URL·외부 응답으로 manifest를 생성하지 않는다. 활성 제품 30개를 넘기지 않는다. 사양 90/30일 점검은 자동 수집 작업이 아니라 아래 보고서의 `reviewsDue`를 보고 수행하는 수동 작업이다.

## 2주 관측과 주기 비교

운영자 DB 접속으로 아래 결과를 각각 UTF-8 JSON 파일로 저장한다. 공개 제품 관측만 내보내고 Decision·사용자·비밀 설정은 포함하지 않는다. PowerShell에서는 UTF-8 출력으로 저장한다.

```sql
SELECT coalesce(json_agg(t ORDER BY attempted_at), '[]'::json)
FROM (SELECT product_id, attempted_at, completed_at, outcome, price, currency
      FROM catalog_observations) t;

SELECT coalesce(json_agg(t), '[]'::json)
FROM (SELECT id, facts FROM catalog_products WHERE active) t;
```

```powershell
node scripts/catalog/report.mjs observations.json products.json
```

보고서는 시도 수·결과별 수·관측 기간·점검 기한과 1/3/7일 가상 갱신 결과를 출력한다. `references`는 VERIFIED 관측 수, `available`은 24시간 내 가격이 남은 표본 수다. `priceMismatches`와 3/5/10/20만원 예산의 `falseBudgetPasses`를 함께 본다. `unavailableReferences`/`falseAvailable`은 관측된 품절과 그때 남아 있던 가격을 센다. 실패·미관측 구간은 정상 가격으로 간주하지 않는다. ATTEMPTED만 남은 관측은 프로세스 중단 가능성을 확인한다.

주 1회 다음을 별도 기록한다: 수동 출처 점검 시간, 수정한 사양 수, 품절·잘못된 모델 발견, 사용자 신고. 근거가 충분하지 않으면 24시간을 유지한다. 3/7일은 가격 정답률이 높더라도 UNKNOWN 노출을 크게 늘릴 수 있다. 기간·품목·판매 채널이 좁으므로 불일치 0건만으로 주기를 늘리지 않는다. 2주 실측과 관리 시간 기록은 배포 후 수행해야 하며 이번 구현 검증 결과와 구분한다.

## 성능과 장애 대응

- `CatalogApiIntegrationTest.measureDatabaseBaselineAtFiveAndThirtyProductsWithoutCache`는 제품 5/30개, 동시 요청 1/5개에서 비교 생성 API를 측정하고 `backend/build/reports/catalog-benchmark.json`을 만든다. 운영 트래픽·브라우저 지연을 포함하지 않는다.
- `catalog.db.read`, `catalog.discovery` Micrometer timer로 DB 조회와 전체 후보 생성을 구분한다. 외부에 metrics endpoint를 새로 노출하지 않는다. 운영 지연이 관찰되면 DB 시간과 요청 전체 시간부터 확인하고 캐시 효과를 별도 비교한다.
- 수집 실패는 마지막 가격의 시각을 연장하지 않는다. 품절·모델 불일치·가격 검증 실패는 다음 추천에서 판매 정보를 보류한다. 기존 개인 비교는 당시 snapshot이며 최신 조회를 약속하지 않는다.
- 비용/품질 문제가 생기면 `CATALOG_REFRESH_ENABLED=false`로 중단한다. 비교 열람은 유지하고 가격은 자연 만료한다. 이전 실시간 추천으로 복귀하려면 `CATALOG_ENABLED=false`로 설정하고 기존 검색·AI·수집 설정과 동의를 사용한다. DB 이력은 삭제하지 않는다.
