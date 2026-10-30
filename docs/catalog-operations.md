# 카탈로그 등록·갱신·관측 운영

## 갱신 방식 선택

반복 갱신에 애플리케이션 코드와 간단한 자동화를 우선하는 이유는 세 가지다. 등록된 공식 출처와 모델에 같은 검증 규칙을 반복 적용할 수 있고, 호출 상한·실패 처리·판정 근거를 코드와 테스트로 확인할 수 있으며, 매번 에이전트를 실행하지 않아 반복적인 토큰 비용을 피할 수 있다. 가격 수집에는 기존 Firecrawl 비용이 들고 판매처별 파서 개발과 사이트 변경 대응에도 운영 시간이 필요하다. 반면 에이전트는 비정형 페이지 변경을 조사하는 데 유연하지만 실행 비용과 결과 검토가 필요하다. 따라서 코드 방식의 총비용이 에이전트보다 낮다고 검증된 것은 아니다.

판매처별 개발·수정 및 장애 조사 시간, 수집 API 비용을 에이전트 사용량과 결과 검토 비용에 비교한다. 코드 방식의 총비용이 더 크다는 근거가 생기면 에이전트 갱신을 권고하고 판단 근거와 미측정 비용을 기록한다. 현재 두 방식의 총비용과 장기 안정성은 비교되지 않았다. 결정과 대안은 [ADR 0016](decisions/0016-curated-catalog.md#갱신-방식과-비용-판단)에 기록한다. 개발 환경에서 실제 동작을 확인한 뒤 운영 호스팅과 PostgreSQL을 설계한다.

QCY T13 Pro의 ANC·호환성·방수, QCY HT16과 HT10 Hi-Res의 ANC, T17 APP·T29 APP·HT20의 형태, HT19의 ANC·방수, Apple AirPods Pro 3와 AirPods 5 무선 충전 케이스 모델의 ANC·방수, Marshall MOTIF II A.N.C.의 ANC 및 젠하이저 MOMENTUM True Wireless 5의 ANC·이어버드 IP54는 모델·항목·값이 일치하면 확인 시각을 자동 갱신할 수 있다. HT16, HT10 Hi-Res, T17 APP, T29 APP과 HT20은 국내 공식 판매 페이지의 제품명과 상품 번호를 함께 확인한다. HT19는 제조사 페이지에서 MeloBuds N60과 HT19의 연결 및 ANC 지원을 확인하고, 방수는 사양표의 IPX5도 대조한다. 소니 Float Run의 단일 상품 DOM 가격 검증은 구현했지만 실제 Firecrawl 응답에서 검증에 실패하여 상품을 비활성으로 유지한다.

사양 확인 시각은 manifest와 별도의 DB 필드에 저장한다. 동일 revision의 manifest 재등록은 확인 시각을 보존하고, revision이 바뀌면 이전 확인 시각을 제거한다. `DECISION_ASSISTANT_CATALOG_SPEC_REFRESH_ENABLED=true`로 활성화하면 QCY T13 Pro, HT16, HT10 Hi-Res, HT20, T17 APP, T29 APP, HT19, Apple AirPods Pro 3 및 AirPods 5, Marshall MOTIF II A.N.C.와 젠하이저 MOMENTUM True Wireless 5의 공식 페이지를 각각 7일 간격으로 확인한다. 기본값은 false다. 모델명과 사양 영역의 항목·값·기존 출처 및 인용이 일치한 항목만 갱신한다. QCY ANC는 같은 모델의 제품명과 공식 페이지 설명을 대조한다. 통신 실패·변경·불일치는 시각을 갱신하지 않는다. 확인 가능한 사양이 있을 때만 요청 전에 시도를 DB에 기록해 재시작이나 동시 실행에도 간격을 유지한다. 자동 확인 범위 밖의 사양은 기존 수동 검토 대상이다.

GitHub Actions의 출처 점검은 운영 DB에 접근하지 않으므로 자동 갱신된 DB 시각을 알지 못하고 manifest의 원래 시각 기준으로 기한 경고를 표시할 수 있다. 해당 경고를 DB 확인 결과와 대조한다. 개발 환경에서는 실제 QCY와 Apple 페이지 조회 후 각각 두 항목의 확인 시각과 다음 시도 시각이 PostgreSQL에 저장되는 것을 확인했다. 별도 개발 시험 DB에서는 실제 페이지 조회로 QCY T13 Pro의 ANC·호환성·방수, HT19의 ANC·방수와 Apple의 ANC·방수 확인 시각 및 다음 시도 시각이 저장됐다.

Marshall MINOR IV 블랙의 이어버드 IPX4도 상품 번호와 사양 행이 일치하면 확인 시각을 자동 갱신한다. 사양 출처 점검의 주간 대상에도 포함한다.

QCY HT08 멜로버즈 프로 플러스의 ANC 지원은 국내 공식 상품명과 같은 모델의 공식 게시물에서 확인했다. 게시물 번호·제목·모델과 ANC 지원 문장이 일치하면 확인 시각을 자동 갱신하며 주간 출처 점검에도 포함한다.

기본 추천은 활성 이어폰 14개(QCY 8개, Apple 한국 스토어 2개, Marshall 2개, 젠하이저 1개, BOSE 1개)를 사용한다. BOSE QC 이어버드 아이스 블루는 가격만 등록하고 ANC 등 사양은 아직 미확인이므로 해당 필수 조건에서 UNKNOWN이다. 사양 근거가 없어 사양 출처 점검·갱신 대상에도 포함되지 않는다. 판매 종료된 QCY T13 ANC는 revision 2에서 비활성화했고 등록 이력은 유지한다. 별도 캐시·관리 UI는 없으며 `backend/src/main/resources/catalog/earbuds.json`이 검토 가능한 등록 원본이다. 정책은 [ADR 0016](decisions/0016-curated-catalog.md), 초기 근거와 성능은 [평가](evaluations/catalog-2026-10-06/README.md)를 따른다.

## 첫 실행과 가격 갱신

기본 `CATALOG_ENABLED=true`, `CATALOG_REFRESH_ENABLED=false`다. offline에서도 등록 사양을 볼 수 있지만 초기 가격은 없으므로 예산 충족 추천은 없다. 과거 가격을 seed에 넣어 최신 판매가처럼 제공하지 않는다.

실제 가격 갱신은 기존 [Secret 설정](deployment.md#secret-management)을 완료한 환경에서 다음 **비밀 아닌 설정**으로 활성화한다. Firecrawl 키는 기존 Secret Manager에서만 읽는다. 검색·AI 활성화는 필요 없다.

```dotenv
DECISION_ASSISTANT_CATALOG_ENABLED=true
DECISION_ASSISTANT_CATALOG_REFRESH_ENABLED=true
DECISION_ASSISTANT_CATALOG_MONTHLY_LIMIT=700
DECISION_ASSISTANT_WEB_ENABLED=true
FIRECRAWL_SECRET_VERSION=1
```

활성 제품 20개를 목표로 기본 월 시도 상한을 700회로 설정한다. 20개 제품의 31일 일간 시도에는 약 620회가 필요하다. 현재 활성 제품은 14개이며 확보하지 못한 제품은 미검증 상태로 추가하지 않는다. 이 상한은 Firecrawl 계정 전체의 크레딧이나 금액 상한이 아니므로 사용자 수집·다른 프로그램의 사용량과 계정 잔여량도 확인한다.

2026-10-30 기준 [Firecrawl 공개 요금표](https://www.firecrawl.dev/pricing)는 Free 플랜의 월 1,000크레딧과 기본 Scrape 페이지당 1크레딧을 안내한다. 현재 가격 요청은 Markdown과 rawHtml을 사용하며 JSON 추출이나 Firecrawl Agent를 호출하지 않는다. 기본 단가가 적용된다고 가정하면 24시간 간격의 제품 6개는 월 약 180~186크레딧, 7개는 약 210~217크레딧, 8개는 약 240~248크레딧이다. 이는 시도 횟수를 기준으로 한 추산이다. 결과가 없는 요청은 과금되지 않을 수 있지만 403·404 응답 페이지는 과금될 수 있고, 다른 기능도 계정 크레딧을 공유한다. 실제 계정의 플랜·잔여량·다른 사용량과 추가 과금 여부는 확인되지 않았다.

worker는 기본 60초마다 최대 한 제품을 claim하고 제품별 시도 간격은 24시간이다. 14개 제품은 약 14분에 걸쳐 시도한다. `catalog_monthly_usage`는 UTC 달을 사용한다. 실패·중단도 예약 호출을 소비하고 같은 제품을 즉시 재시도하지 않는다. 단순 재시작으로 호출 한도가 초기화되지 않는다. 스케줄 지연이나 실패로 가격이 24시간을 넘으면 잠시 UNKNOWN으로 떨어질 수 있다.

기동 후 `catalog_observations`에서 성공/실패를 확인하고 실제 화면의 모델·구매 링크·확인 가격을 대조한다. HTTP 200만으로 판매가 검증 성공으로 취급하지 않는다. strict parser가 구조를 지원하지 못하면 가격을 수동 조작해서 통과시키지 않고 원인을 수정하거나 등록 제품을 비활성화한다.

## 제품·사양 관리

매주 월요일 03:17 UTC(12:17 KST)에 GitHub Actions의 `카탈로그 사양 출처 점검`이 활성 등록 제품의 고정 사양 출처를 제품당 최대 한 번 읽는다. 현재 QCY T13 Pro, HT16, HT10 Hi-Res, HT08, HT20, T17 APP, T29 APP, HT19, AirPods Pro 3, AirPods 5, Marshall MOTIF II A.N.C.와 MINOR IV, 젠하이저 MOMENTUM True Wireless 5의 공식 페이지 열세 곳이 대상이다. GitHub Actions 화면에서 `workflow_dispatch`로 수동 실행할 수도 있다. 비밀이나 운영 DB 권한은 필요하지 않으며 외부 AI를 호출하지 않는다. 예약 실행은 workflow가 기본 branch에 반영된 뒤 동작한다.

작업 요약에서 `QUOTE_PRESENT`는 단순 문구 존재, `QUOTE_MISSING`은 미발견, `UNAVAILABLE`은 접근 실패, `UNSUPPORTED_SOURCE`는 허용된 고정 주소가 아닌 사양이다. 제목은 등록된 예상 제목과 비교하며 `MATCH`, `MISMATCH`, `UNAVAILABLE`로 표시한다. 문구가 다른 상품·추천 영역에 나타날 수도 있고 제목만으로 모델을 확정할 수 없으므로 **제목과 문구 일치는 동일 모델이나 사양의 정확성을 검증하지 않는다**. 점검 기한이 7일 이내이거나 제목 불일치·문구 미발견·접근 실패가 있으면 작업을 실패로 표시한다. 광고 문구 등 제목의 무해한 변경도 경고가 될 수 있으므로 실제 모델을 확인한 뒤 예상 제목을 수정한다. 수집 실패의 HTTP 상태와 크기 초과는 간단한 코드만 남긴다. GitHub 알림 수신 여부는 계정 설정에 달려 있으며 예약 자체가 누락된 경우 이 workflow가 알림을 보장하지 않는다.

GitHub Actions 점검은 `fetchedAt`이나 manifest·DB를 수정하지 않는다. 자동 갱신 범위 밖의 항목은 요약에서 점검 기한과 실패 항목을 확인하고 실제 모델·구성·원문을 점검한 뒤 아래 절차로 revision을 올린다. 이미 생성한 개인 비교는 당시 snapshot으로 유지한다.

1. 공식 제품명·세대·구성·판매 URL과 `OfficialProductParser`의 identity를 대조한다. 유사한 제품명이나 지역별 별칭만으로 사양을 복사하지 않는다.
2. 공식 사양 출처의 짧은 원문 인용과 확인 시각을 `facts[].evidence`에 넣고 HARDWARE/SOFTWARE를 선택한다. ANC 지원은 음질·ANC 성능 점수가 아니다. 앱·OS 관련 호환성은 SOFTWARE다.
3. 수정 시 해당 제품 `revision`을 증가시킨다. 같은 revision의 다른 내용은 기동을 거절하며 낮은 revision은 최신 DB 등록을 되돌리지 않는다. 비활성화는 항목 삭제 대신 `active:false`와 revision 증가를 사용한다.
4. 코드 리뷰·테스트 후 배포/재시작한다. 제품 URL 또는 identity가 바뀌면 기존 가격을 초기화한다. 모델·구성·출처의 재검토 없이는 `fetchedAt`만 갱신하지 않는다. 이 작업을 위임받은 agent도 실제 출처를 재검토하고 근거를 PR에 남길 수 있지만 예약 문구 점검의 일치만으로 시각을 연장하지 않는다. 이미 생성한 개인 비교의 근거는 그대로 남는다.

등록은 classpath 또는 운영자가 지정한 로컬 `file:` manifest만 읽는다. 사용자 입력 URL·외부 응답으로 manifest를 생성하지 않는다. 활성 제품 30개를 넘기지 않는다. 자동 확인 대상 외의 사양은 아래 보고서의 `reviewsDue`를 보고 수동 점검한다.

## 2주 관측과 주기 비교

운영자 DB 접속으로 아래 결과를 각각 UTF-8 JSON 파일로 저장한다. 공개 제품 관측만 내보내고 Decision·사용자·비밀 설정은 포함하지 않는다. PowerShell에서는 UTF-8 출력으로 저장한다.

```sql
SELECT coalesce(json_agg(t ORDER BY attempted_at), '[]'::json)
FROM (SELECT product_id, attempted_at, completed_at, outcome, price, currency
      FROM catalog_observations) t;

SELECT coalesce(json_agg(t), '[]'::json)
FROM (SELECT id, facts, fact_reviews FROM catalog_products WHERE active) t;
```

```powershell
node scripts/catalog/report.mjs observations.json products.json
```

보고서는 시도 수·결과별 수·관측 기간·점검 기한과 1/3/7일 가상 갱신 결과를 출력한다. `references`는 VERIFIED 관측 수, `available`은 24시간 내 가격이 남은 표본 수다. `priceMismatches`와 3/5/10/20만원 예산의 `falseBudgetPasses`를 함께 본다. `unavailableReferences`/`falseAvailable`은 관측된 품절과 그때 남아 있던 가격을 센다. 실패·미관측 구간은 정상 가격으로 간주하지 않는다. ATTEMPTED만 남은 관측은 프로세스 중단 가능성을 확인한다.

`reviewsDue`는 `fact_reviews`의 유효한 자동 확인 시각이 원래 확인 시각보다 나중이면 이를 사용한다. 미래 시각이나 잘못된 자동 확인 값은 무시하고 원래 시각을 사용한다. 원래 시각이 잘못된 입력은 보고서 실행을 실패시킨다. 이전 형식으로 `fact_reviews` 없이 내보내면 원래 시각만 사용하므로 자동 확인 결과가 보고서에 반영되지 않는다. GitHub Actions의 출처 점검은 DB를 읽지 않으므로 이 보고서와 별개로 manifest 기준 경고가 남을 수 있다.

주 1회 다음을 별도 기록한다: 수동 출처 점검 시간, 수정한 사양 수, 품절·잘못된 모델 발견, 사용자 신고. 근거가 충분하지 않으면 24시간을 유지한다. 3/7일은 가격 정답률이 높더라도 UNKNOWN 노출을 크게 늘릴 수 있다. 기간·품목·판매 채널이 좁으므로 불일치 0건만으로 주기를 늘리지 않는다. 2주 실측은 갱신 주기의 적절성을 판단하는 자료이며 기능 구현·배포를 위해 그 기간을 기다리는 필수 절차가 아니다. 실측과 관리 시간 기록은 배포 후 수행하며 구현 검증 결과와 구분한다.

## 성능과 장애 대응

- 가격 worker는 관측의 성공 또는 실패를 `catalog_refresh` 로그에 제품 ID와 결과 코드로 남긴다. 마지막으로 사용할 수 있는 가격 대비 2배 이상 또는 절반 이하인 새 가격은 `catalog_price_change` 경고를 추가하지만 자동으로 거절하지 않는다. 할인 등 정상 변동도 포함될 수 있어 경고는 원인 확인 신호다. DB 관측 기록이 최종 결과의 기준이며 로그는 별도 알림 시스템이 아니다.
- `DECISION_ASSISTANT_CATALOG_MONTHLY_LIMIT=700`은 승인된 월 시도 상한이다. 20개 제품의 31일 일간 시도에는 약 620회가 필요하다. 실패도 한도를 소비하며 Firecrawl 계정의 다른 사용량은 별도로 확인한다.

- `CatalogApiIntegrationTest.measureDatabaseBaselineAtFiveAndThirtyProductsWithoutCache`는 제품 5/30개, 동시 요청 1/5개에서 비교 생성 API를 측정하고 `backend/build/reports/catalog-benchmark.json`을 만든다. 운영 트래픽·브라우저 지연을 포함하지 않는다.
- `catalog.db.read`, `catalog.discovery` Micrometer timer로 DB 조회와 전체 후보 생성을 구분한다. 외부에 metrics endpoint를 새로 노출하지 않는다. 운영 지연이 관찰되면 DB 시간과 요청 전체 시간부터 확인하고 캐시 효과를 별도 비교한다.
- 수집 실패는 마지막 가격의 시각을 연장하지 않는다. 품절·모델 불일치·가격 검증 실패는 다음 추천에서 판매 정보를 보류한다. 기존 개인 비교는 당시 snapshot이며 최신 조회를 약속하지 않는다.
- 비용/품질 문제가 생기면 `CATALOG_REFRESH_ENABLED=false`로 중단한다. 비교 열람은 유지하고 가격은 자연 만료한다. 이전 실시간 추천으로 복귀하려면 `CATALOG_ENABLED=false`로 설정하고 기존 검색·AI·수집 설정과 동의를 사용한다. DB 이력은 삭제하지 않는다.

## 판매처 확장 검증

Apple 한국 스토어의 AirPods Pro 3는 JSON-LD Offer의 단일 SKU와 현재 상품 구매 선택 링크의 `product` 값을 대조하고, 해당 링크 안의 단일 원화 표시 가격이 같은 경우만 수용한다. 비교 추천 영역의 가격, 서로 다른 세대 리다이렉트, 복수 구매 링크·SKU, 회원 등 조건부 문구는 확인 가격으로 사용하지 않는다. 사양은 모델이 명시된 공식 사양 페이지의 ANC·IP57만 등록했다. 기본 연결 호환성은 현재 추출 규칙이 판정할 명시 근거를 등록하지 않아 선택한 플랫폼에 대해 UNKNOWN일 수 있다.

[실제 Dev 갱신 검증](evaluations/catalog-2026-10-29/README.md)은 Firecrawl과 PostgreSQL 저장을 포함한다. 개발 PC의 smoke는 상시 클라우드 운영 검증을 대신하지 않는다. 신규 상품을 늘릴 때 활성 수 × 월 일수를 기존 호출 상한과 대조하고, 실패도 호출을 소비한다는 점을 유지한다.
