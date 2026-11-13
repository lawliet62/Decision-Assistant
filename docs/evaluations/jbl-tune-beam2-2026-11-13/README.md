# JBL TUNE BEAM 2 국내 공식 판매 검증

2026-11-13 JBL 한국 공식 제품 페이지의 판매처 링크를 따라 삼성닷컴의 블랙 SKU `JBLTBEAM2BLKAS`를 확인했다. [공식 제품·판매처 연결](https://kr.jbl.com/TUNE-BEAM-2.html?dwvar_TUNE-BEAM-2_color=Black-APAC-Current)과 [국내 구매 페이지](https://www.samsung.com/sec/harman-life-style-audio/jbl-earphone-tunebeam2-d2c/JBLTBEAM2BLKAS/)를 연결한 검증이다. 출처가 공식이라는 사실과 실제 판매 가능 여부는 구분했다.

## 실제 결과

| 항목 | 결과 |
| --- | --- |
| 가격 | 실제 Firecrawl→CatalogRefreshWorker→PostgreSQL에서 VERIFIED 99,000 KRW |
| 가격 확인 시각 | 2026-11-13T12:34:44.670325900Z (21:34 KST) |
| 사양 | 고정 JBL 제조사 페이지의 ANC 및 Android 페어링 근거 |
| 실제 사양 worker | 두 fact_reviews 갱신, 2026-11-13T12:29:55.332349Z |
| 실제 API | 10만원·ANC 필수·Android·외부 동의 false에서 RECOMMENDED, 두 조건 MEETS |
| 다음 가격 시도 | 2026-11-15T12:34:29.142215Z 이후 |
| 다음 사양 시도 | 2026-11-20T12:29:55.332349Z 이후 |
| 개발 DB 반영 후 | 활성 24개·9개 브랜드. 아직 병합되지 않은 Sony PR #59 데이터 포함 |

[관측 메타데이터](observations.json)에 첫 실패와 교정 성공, 실제 시각·API 결과·비용을 남겼다. 가격과 사양은 서로 다른 격리 DB 검증 결과를 동일 revision·identity·근거 대조 후 결합했다. 성공 제품·관측 한 건과 사양 예약을 개발 DB에 추가하면서 기존 제품·관측을 덮어쓰지 않았고 활성 30개 상한을 확인했다. 월 quota는 중복 복사하지 않았다. 가격 JSON의 확인 시각은 원래 정밀도를 보존했다. API 확인용 임시 결정은 DELETE 204로 삭제했다.

이 PR의 main 기반 manifest는 활성 23개다. 개발 DB의 24개와 차이는 미병합 Sony 등록이며, 두 PR의 코드가 이미 main에 반영됐다는 의미가 아니다.

## 가격 검증과 실패 진단

렌더링 전 본문의 정보 확인 전용 안내나 JBL 사이트의 `orderable:false`만으로 판매 종료를 판정하지 않았다. 격리 브라우저에서 삼성닷컴의 선택 블랙 SKU와 활성 구매 버튼을 확인했다. 구조화 Offer, 기준가, 선택 수량 1, 총액 및 화면의 최대 혜택가가 모두 같은 경우만 수용한다. 최대 혜택가 단독 값이나 조건부 할인 금액을 일반 판매가로 사용하지 않는다. 값이 달라지면 보류한다.

첫 실제 worker는 PRICE_CONTEXT_MISSING이었다. 별도 진단에서 배열형 WebPage `@type`을 문자열로 읽던 오류와 Firecrawl의 hidden input 제거를 확인했다. 알려진 단일 Product를 구분하고, 공개 구매 영역의 SKU·기준가·총액·수량·재고·구매 버튼을 검증하도록 수정했다. 기존 실패 DB의 48시간 예약은 유지했고 교정 검증은 별도 DB에서 수행했다. [재사용할 진단](../../troubleshooting/jbl-rendered-purchase-evidence.md)을 참고한다.

사양은 기존 Java HTTP 경로에서 실제 검증됐다. 제조사 제목·canonical·단일 모델 제목·기능 문단의 정확한 근거를 확인한다. 사양 값은 자동 변경하지 않으며 iOS는 UNKNOWN이다. GitHub Actions 문구 점검은 DB 확인 시각을 연장하지 않는다.

## 비용과 남은 범위

Firecrawl 요청은 최초 worker·진단·교정 worker 각 1회, 총 3회다. 개발 DB 월 계수는 73→76, 공급자 잔여 credit은 직전 999에서 996으로 감소했다. Credit Usage API의 planCredits는 1,000이었으며 요금제 이름·청구 금액까지 확인한 것은 아니다. [API 정의](https://docs.firecrawl.dev/api-reference/endpoint/credit-usage)를 따른다. 추가 구매·충전·유료 리소스 생성과 OpenAI 호출은 없었다.

단일 성공은 장기 안정성이나 목표 전체 완료를 증명하지 않는다. Samsung 자체 브랜드 공백, 기존 실패 제품, 48시간 후 실제 반복 검증은 남아 있다. 운영 배포·상시 활성화는 하지 않았다.

## 업그레이드 검증

기존 브랜드 추론이 JBL 판매처 식별자를 Samsung으로 오인하는 문제를 전체 통합 검사에서 발견했다. V24는 JBL을 먼저 인식하고 해당 행의 브랜드 메타데이터만 보정한다. 기존 migration과 가격·사양·revision은 변경하지 않는다. 실제 가격·사양 관측은 V23 격리 DB에서 수행했으며 V24 업그레이드는 별도 PostgreSQL 통합 검사로 검증한다. 기본 개발 DB의 V24 적용이나 운영 반영을 이번 실측의 완료 조건으로 간주하지 않는다.

## 로컬 검증

단위·PostgreSQL 통합(96개)·OpenAPI 계약·lint·typecheck를 통과했다. npm run check의 마지막 build는 Google 폰트 다운로드 연결 오류로 실패했고, 같은 build 단계 재실행은 통과했다. 이 외부 연결 실패를 JBL parser 실패로 해석하지 않는다. 가격·사양의 실제 worker/API 검증은 fixture와 구분해 위에 기록했다.
