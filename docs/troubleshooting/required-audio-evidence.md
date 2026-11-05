# ANC·호환성 필수 조건의 미확인 원인 분리

진단일: 2026-11-05. 기준: `origin/main`의 `8d0e3c0`. 상태: **판정 결함과 리버티 5 멀티포인트 근거 누락을 수정했다. 추가 플랫폼 근거의 판정 혼합도 수정했다. 등록 근거가 없는 경우의 미확인은 MVP의 정상 동작이며 사용자 실행 환경의 동일 사례 대조는 미완료다.**

## 증상과 기대 결과

ANC 또는 호환성을 필수로 선택했을 때 지원 여부를 확인하지 못했다는 설명이 나온다. 같은 모델의 공식 근거가 있으면 충족 여부를 판정하고, 없는 근거는 추정으로 채우지 않아야 한다. 가격 확인, ANC 지원, 플랫폼 기본 사용, 전용 앱 지원은 각각 다른 검사다.

## 현재 경로에서 확인한 사실

기본 경로는 검색·AI 추출이 아니라 `CatalogDiscovery`가 등록 제품의 `usableFacts`를 읽어 `MinimumRequirementMatcher`와 `CompatibilityMatcher`에 전달하는 방식이다. 따라서 이 경로의 누락을 AI JSON 파싱 문제로 단정할 수 없다.

`CatalogRequirementCoverageTest`는 배포되는 `catalog/earbuds.json` 전체를 읽어 등록 시각 직후의 판정을 검사한다. 가격과 사양 만료를 분리한 이 시험에서 활성 22개 중 15개는 ANC 지원 근거가 있고 모두 MEETS다. 나머지 7개는 MISSING이다. Android·iOS 기본 호환성은 QCY T13 Pro 한 제품에만 등록돼 있고 두 플랫폼 모두 MEETS다. 나머지 21개는 해당 플랫폼 근거가 없다. 멀티포인트 근거는 수정 전 0개였고, 이번 보완으로 리버티 5 한 제품이 MEETS다. 멀티포인트를 Android·iOS 지원으로 취급하지 않는다. 이 수치는 실제 가격이나 운영 DB의 현재 상태를 검증한 결과가 아니다.

SOFTWARE 근거는 30일 후 보류되고, 성공한 공식 사양 재확인만 확인 시각을 연장한다. 가격이 없거나 72시간을 넘으면 사양이 MEETS여도 추천 그룹은 UNKNOWN일 수 있다. 이미 생성한 후보는 당시 snapshot이므로 공용 등록 변경만으로 갱신되지 않는다. 이 세 가지는 문자열 파싱과 구분해 확인한다.

실시간 검색 경로에서는 과거 공식 문서 보완이 실제 평가의 사양 채택으로 이어지지 않았다. 검색·모델 연결·수집 실패가 있었으며, 해당 경로의 미해결 문제는 [기존 분석](discovery-quality-2026-10-06.md)을 따른다. 이번 카탈로그 시험으로 과거 검색 경로가 해결됐다고 주장하지 않는다.

## 실패한 접근과 재현된 결함

| 접근 또는 기존 동작 | 확인 결과 | 판단 |
| --- | --- | --- |
| 공식 문서 검색·수집을 추가하면 조건 확인율이 오를 것이라는 과거 시도 | 과거 15조건 평가에서 보완 사양 채택 0건 | 입력 근거 확보 전의 실패를 판정기나 모델 교체만으로 해결할 수 없다. |
| 현재 등록된 ANC 문구가 전부 파싱에 실패한다는 가설 | 15개 모두 MEETS | 현재 기본 경로의 포괄적 원인으로는 기각한다. |
| 인용 전체에 `앱`/`app`이 있으면 호환성 근거를 거절 | 기본 연결 지원과 별도 앱 조건이 함께 있는 재현 시험에서 UNKNOWN | 앱 조건과 기본 사용 근거의 구분이 너무 넓었다. |
| 인용 전체의 `최대`/`up to`를 모든 최소 조건의 불확실성으로 처리 | 명시적 ANC 지원과 최대 감쇠량이 함께 있는 재현 시험에서 UNKNOWN | 지원 여부와 감쇠 성능을 혼동했다. |
| 기존 리버티 5 페이지의 멀티포인트 근거와 판정 확인 | 실제 표에는 지원이 명시돼 있지만 manifest에는 누락됐고, 해당 표 문구를 주어도 기존 판정기는 UNKNOWN | 근거 등록과 영문 항목·값 판정이 함께 빠졌다. |

앱·최대 감쇠량 시험은 수정 전 관련 60개 테스트 중 정확히 2개가 실패했다. 이는 코드 결함을 재현한 합성 fixture이며, 사용자가 본 실제 페이지에 같은 문구가 있었다는 증거는 아니다. 멀티포인트 표의 Yes/No 의미 검사도 수정 전에 별도로 실패했다.

## 수정과 검증 범위

이어폰 기본 호환성은 세미콜론으로 나눈 문장 구간 중 앱에 관한 구간을 기본 사용 판정에서 제외한다. 앱의 쉼표·줄바꿈 구분 플랫폼 목록도 구간 전체를 제외해 목록 뒤 플랫폼을 기본 지원으로 오인하지 않는다. 별도의 명시적인 플랫폼 연결 지원 구간은 사용할 수 있다. 앱 지원만 있거나 Bluetooth 버전만 있는 경우는 UNKNOWN이고, 기본 사용의 지원·미지원이 상충해도 UNKNOWN이다. 앱과 기본 지원을 같은 구간에서 구분할 수 없으면 여전히 보류한다.

ANC 지원 판정에서 `최대 <수치> dB`와 `up to <수치> dB`만 모호성 검사에서 분리한다. 감쇠량을 지원 여부나 성능 점수로 사용하지 않는다. 옵션별 지원, 미확인, 부정·상충 근거는 기존처럼 보류한다. 메모리·주사율 등 수치 하한의 최대치 규칙도 유지한다.

리버티 5는 동일 canonical·모델 제목의 단일 `Specs` 표에서 멀티포인트 항목과 Yes 값을 확인하고 SOFTWARE 근거로 등록했다. manifest revision을 2로 올렸다. 기존 주간 응답 한 개에서 ANC·멀티포인트를 독립 검증하므로 정기 요청 수는 늘지 않는다. 다른 모델, 다른 영역, 복수 표, 중복·상충 행은 재확인하지 않는다. 판정기는 명시적인 `Multipoint Connection Yes/No`만 지원/미지원으로 해석한다.

2026-11-05(KST)에 [기존 제조사 URL](https://www.soundcore.com/products/a3957-liberty-5-tws-earbuds)의 직접 HTTP 응답을 확인했다. 모델과 표 구조를 읽고, 실제 응답을 수정된 Java 검증기에 넣어 ANC와 멀티포인트 모두 `true`를 얻었다. 전체 HTML은 저장하지 않았다. 이 결과는 일회성 공식 출처 검증이며 실제 운영 DB 갱신이나 장기 안정성 검증은 아니다.

수정 후 세 회귀 시험·등록 데이터·출처 검증을 포함한 관련 64개 단위 테스트가 통과했다. `CatalogApiIntegrationTest.realRegisteredEvidenceSupportsRequiredAncAndEachBasicPlatformWithoutProviders`는 실제 QCY T13 Pro 등록 사양에 시험용 최신 판매가·재확인 시각을 조합해 ANC 필수 및 Android/iOS 각각의 추천 API 판정을 확인한다. `libertyRefreshStoresDiscountAncAndMultipointWithOneSpecificationFetch`는 만료된 멀티포인트 근거를 fixture 응답 한 번으로 갱신하고 ANC·멀티포인트 필수 추천까지 검사한다. 시험용 판매가·재확인 시각을 실제 조회 결과로 취급하지 않는다.

## 해결 상태와 다음 대조

과도한 보류 규칙과 명시적 멀티포인트 표의 판정을 수정하고 실제 누락된 근거를 보완했다. 현재 기본 경로의 광범위한 Android·iOS 미확인에는 여전히 등록 근거 부족이 직접적인 원인이다. 공식 페이지의 앱 지원·Bluetooth 버전을 기본 음악·통화 지원으로 승격하는 방식은 사용하지 않는다.

공개 출처를 소량 확인했지만 [QCY HT19의 기존 제조사 페이지](https://www.qcy.com/products/qcy-melobuds-n60)에서는 기본 플랫폼 호환성 문구를 확보하지 못했다. [BOSE 페이지](https://www.bose.com/p/earbuds/bose-quietcomfort-headphones/QCEARB24-HEADPHONEIN.html)의 Android 언급은 다른 Ultra 모델의 추가 기능 설명이므로 QC 이어버드의 기본 호환성 근거로 채택하지 않았다. [Apple의 비 Apple 기기 연결 안내](https://support.apple.com/en-gb/guide/airpods/dev499c9718b/web)는 Android의 듣기·통화와 해당 모델의 페어링 절차를 설명한다. 이를 등록하려면 기존 제품 사양 URL 외의 추가 문서와 모델별 근거 연결·갱신 범위를 함께 검토해야 한다. 이번 보완은 기존 출처를 재사용한 리버티 5 멀티포인트에 한정한다.

추가 공식 문서를 대조한 결과, 검색 요약에 플랫폼명이 나타나는 것만으로 기본 호환성 근거를 확보했다고 판단할 수 없었다. 2026-11-05에 다음 두 사례의 본문과 상위 제목·각주 연결을 확인했다. 정기 수집이나 manifest 등록은 하지 않았다.

| 공식 출처 | 검색 요약과 본문 대조 | 채택하지 않은 이유 |
| --- | --- | --- |
| [Liberty 5 Pro 시리즈 소개](https://www.soundcore.com/liberty-5-pro-series) | iOS·Android 호환 문구는 각주 9·10에 있고, 본문 연결 대상은 Pro Max 충전 케이스의 녹음·동기화 기능이다. | 등록된 Pro 모델의 기본 음악·통화 호환성으로 옮기면 모델과 기능 범위를 모두 바꾸게 된다. |
| [Liberty 5 영국 공식 FAQ](https://www.anker.com/uk/products/a3957-liberty-5-tws-earbuds?position=Liberty+5) | iOS 13 미만의 연결 제한은 soundcore 앱에서 제품을 찾거나 연결하지 못하는 문제의 답변에 포함된다. | 해당 문장만 떼어 기본 Bluetooth 연결의 지원 또는 미지원으로 판정할 수 없다. |

[Apple 기기 연결 문서](https://support.apple.com/en-gb/guide/airpods/dev7c85810f2/web)는 웹 조회 도구에서 429였지만 직접 HTTP 응답의 본문을 읽을 수 있었다. iPhone·iPad 페어링 절차와 수동 페어링의 AirPods 5·Pro 3 분기를 확인했다. 최신 iOS 설치 안내와 문서 전체의 iOS 27 대상 표시는 보존해야 할 문맥이며, 이를 모든 iOS 버전에 대한 지원 선언으로 바꾸지 않는다. 이는 추가 근거의 후보를 확보한 결과이고, 자동 검증기나 갱신 방식이 검증됐다는 뜻은 아니다. 원문 전체는 저장하지 않았다.

사용자 사례는 제품명·선택한 호환성·실행 환경과 당시 후보의 `facts`, `requirementChecks`, `compatibilityCheck`, `priceStatus`를 대조해야 한다. 운영 DB나 개인 비교 데이터를 조회하지 않았으므로 실제 장애의 단일 원인 확정과 운영 복구는 아직 주장하지 않는다. 새로운 유료 수집 실험이나 호출 상한 변경도 하지 않았다.

## 별도 플랫폼 근거를 연결할 때 발견한 추가 병목

공식 지원 문서 보완을 준비하며 현재 Java 코드에 두 개의 독립 근거를 넣어 재현했다. `Android 기본 연결 지원` 하나는 MEETS지만 별도 URL의 `iOS 기본 연결 지원`을 함께 넣으면 Android가 UNKNOWN으로 바뀌었다. 판정기가 이름이 같은 호환성 근거를 모두 결합하여, 다른 플랫폼의 근거를 선택한 플랫폼의 모호함으로 처리했기 때문이다. 이 재현은 외부 호출이나 DB 변경 없이 수행했다.

선택한 플랫폼을 언급하지 않고 다른 Android·iOS·멀티포인트 대상만 명시한 근거는 해당 검사에서 제외하도록 수정했다. 선택한 플랫폼의 부정·상충과 일반적인 미확인 근거는 보존한다. `DiscoveryVerificationTest.independentPlatformEvidenceDoesNotInvalidateRequestedCompatibility`와 `CatalogApiIntegrationTest.separatePlatformSourcesRemainIndependentInCatalogResponses`가 판정과 원래 근거 목록의 보존을 검사한다.

수정된 코드에 같은 재현 입력을 다시 넣으면 단독·복수 출처 모두 Android가 MEETS다. 재확인 시각 저장은 아직 `IllegalArgumentException`이며 아래 결정 대상이다. Apple의 [비 Apple 기기 연결 문서](https://support.apple.com/en-gb/guide/airpods/dev499c9718b/web)와 [Apple 기기 연결 문서](https://support.apple.com/en-gb/guide/airpods/dev7c85810f2/web)는 직접 HTTP 진단에서 모두 200이었다. 한국어 URL과 웹 조회 도구에서 일부 429를 관찰했다. HTTP 200이나 본문의 플랫폼 문자열 존재를 모델·사양 검증 성공으로 취급하지 않으며, 아직 근거 등록이나 자동 수집은 하지 않았다.

자동 재확인은 별개의 문제다. `CatalogProduct.reviewedFact`는 같은 이름의 근거가 하나여야 하므로 별도 Android·iOS 근거 두 개에 확인 시각을 기록하려고 하면 `IllegalArgumentException`을 던진다. 저장된 확인 시각도 항목명 하나에 연결되어 있다. 기존 제품별 주간 예약과 출처 점검은 한 사양 URL을 전제로 한다. 다른 공식 문서에 있는 근거를 추가하는 일과 그 문서를 안전하게 계속 확인하는 일은 같은 작업이 아니다.

MVP에서는 등록된 확인 가능 근거가 없는 경우를 정상적인 UNKNOWN으로 처리한다. 제조사의 정보 제공 여부를 판단하기 위한 별도 조사는 보류하며, 확인할 수 없는 지원 여부를 추론하지 않는다. 사용자가 직접 확인해야 하는 불편은 MVP에서 감수한다. 따라서 추가 공식 문서의 자동 재확인 확장이나 수동 등록을 이번 해결의 선결 조건으로 삼지 않는다. 위의 확인 시각 제약은 향후 별도 근거를 확장할 때 검토할 제한으로 남는다.
