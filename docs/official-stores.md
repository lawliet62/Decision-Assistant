# 공식 판매처 추천의 수집 범위

`official-kr-v2`는 국내·한국어 판매 페이지를 먼저 검색한다. 공식 판매처 여부는 `OfficialStorePolicy`의 정확한 호스트·상품 경로에 한정한다. 검색 결과 제목이나 AI의 판단으로 판매자를 등록하지 않는다. 전체 브랜드·전체 시장의 카탈로그가 아니다.

| 판매처 | 허용 범위와 확인 근거 |
| --- | --- |
| 삼성닷컴 | `www.samsung.com/sec/`의 이어폰·휴대폰·태블릿·노트북·모니터 개별 상품 경로. [공식 제품 페이지](https://www.samsung.com/sec/buds/galaxy-buds-fe-r400/SM-R400NZWAKOO/) |
| Apple 한국 스토어 | `www.apple.com/kr/shop/`의 구매·상품 경로. [공식 구매 페이지](https://www.apple.com/kr/shop/buy-airpods/airpods-pro-3) |
| LG전자 | `www.lge.co.kr`의 모니터·노트북 개별 상품 경로. [공식 제품 페이지](https://www.lge.co.kr/product/monitors/27gs85q) |
| QCY 한국 공식몰 | `qcystore.co.kr/product/`의 상품 번호가 있는 경로. [QCY KOREA 공식 유통 안내](https://www.qcykorea.com/default/customer/customer17.php?com_board_basic=read_form&com_board_id=17&com_board_idx=1&com_board_page=&com_board_search_code=&com_board_search_value1=&com_board_search_value2=&sub=01)에서 연결한 판매처 |
| QCY 공식 스마트스토어 | 위 공식 유통 안내로 확인한 `smartstore.naver.com/ylintl/products/` 경로만 허용. 다른 판매자나 네이버 전체 도메인은 허용하지 않음 |

등록 근거 확인일: 2026-09-24. 판매처 추가·변경 시 공식 브랜드 페이지의 연결 근거를 이 문서에 남기고, 다른 호스트·판매자·경로·리다이렉트를 배제하는 테스트를 함께 갱신한다. 검색은 카테고리별 공식 상품 경로의 OR 질의 한 번으로 제한하며, 찾지 못한 브랜드를 임의 사이트로 대체하지 않는다.

## 가격과 이미지

동일한 Firecrawl 수집 응답의 Markdown과 raw HTML을 사용한다. 추천 상품은 `maxAge=0`, `storeInCache=false`로 요청한다. [Firecrawl의 scrape 계약](https://docs.firecrawl.dev/api-reference/endpoint/scrape)에 있는 최종 `metadata.url`을 확인하고, 수집 HTML의 JSON-LD Product/Offer를 jsoup와 Jackson으로 로컬 파싱한다. 별도 페이지·이미지 요청이나 AI 추출 호출은 추가하지 않는다. 전체 HTML은 저장하거나 AI로 보내지 않는다.

단일 제품에 연결된 양수 KRW 판매가만 사용한다. AggregateOffer, 구성별 복수 가격, 회원·조건부 가격 명시, 기간이 지난 가격, 품절·중고 등은 제외한다. ProductGroup은 색상만 다르고 모든 판매가가 같은 경우만 묶는다. 용량·구성이 다르면 묶지 않는다. 구조화 데이터가 없거나 해석할 수 없는 페이지는 가격 미확인으로 제외하므로 지원 판매처 안에서도 추천 가능한 제품이 줄어들 수 있다.

2026-10-01 실제 수집에서 확인한 다음 판매처 표현도 같은 기준으로 처리한다. 검색·수집·AI 호출 상한과 `official-kr-v2`의 가격·필수 조건 정책은 유지한다.

- LG의 `/notebook/모델`과 `/product/notebook/모델`(모니터도 동일)은 같은 호스트·제품군·모델일 때만 같은 상품 주소로 본다. 다른 모델을 가리키는 canonical은 제외한다. 최종 URL 누락·다른 최종 상품·다른 canonical은 계측 사유를 구분한다. 실제 구형 노트북 페이지에는 최종 URL의 모델과 canonical의 모델이 다른 사례도 있어 경로 별칭만으로 판매 가능한 후보가 되지는 않는다.
- [QCY HT16](https://qcystore.co.kr/product/detail.html?product_no=990)·[HT19](https://qcystore.co.kr/product/detail.html?product_no=1012)의 `variesBy: ["선택"]`은 모든 variant의 유일한 추가 속성이 `선택=블랙/화이트`이고 이름이 그룹명+해당 색상일 때만 색상으로 해석한다. 모르는 값·추가 구성·다른 모델·다른 가격은 허용하지 않는다. 이는 일반적인 `선택` 옵션을 색상으로 간주하는 규칙이 아니다.
- 검증된 QCY 색상 그룹은 말미 블랙·화이트를 제외한 공개 제품명으로 중복을 제거한다. 색상별 별도 상품 번호를 두 개의 비교 모델로 세지 않는다. 일반 상품·다른 판매처의 SKU 식별은 유지한다. 추가 탐색에서는 과거 공식 QCY 후보의 이름도 새 색상 그룹 식별자에 연결하여 제외하되, 저장된 SKU·사양·판정을 재작성하지 않는다.

v2는 JSON-LD 금액을 같은 상품 구매 영역의 라벨·금액과 대조한다. 상품 microdata 내부의 구매 행, QCY의 상품 판매가 표, LG의 구매 가격 행을 지원한다. 회원·쿠폰·카드 가격 또는 구매 영역 연결 불명확은 제외하며 할인 전 가격으로 대체하지 않는다. 사이트 전체의 회원 안내를 해당 판매가의 조건으로 오인하지 않는다. LG `pricing_info_box`와 사양 `pdp-pc-specs-more`, QCY `xans-product-detail` 가격 표의 구조는 2026-09-30 공개 상품 페이지에서 확인했다. selector 변경은 회귀 fixture와 함께 검토한다.

구조화 상품 속성·사양 표의 항목/값을 제품 식별자에 연결해 AI와 서버가 같은 근거를 사용한다. 선택한 필수 사양·호환성부터 6,000자 근거 예산에 배치한다. 단위 분리와 기본 용량 뒤의 확장 슬롯은 한정한 서버 규칙으로 정규화하고 원문을 보존한다. 이미지의 OCR이나 추가 fetch는 하지 않는다.

이미지는 해당 Product의 이미지 필드만 사용하며 판매처별 확인한 이미지 호스트로 제한한다. 공식 이미지 CDN 호스트가 새로 필요하면 해당 상품 페이지의 근거와 테스트를 추가한다. 누락·차단·로딩 실패 시 대체 영역을 표시한다. 브라우저가 직접 요청하고 `referrerPolicy="no-referrer"`를 사용한다. IP 전송은 입력 화면에 안내한다. 이미지의 안전성·저작권이나 제품의 품질·정품·재고를 인증한다는 의미가 아니다.

확인한 이미지 호스트는 삼성 상품 페이지의 `images.samsung.com`, Apple 구매 페이지의 `store.storeimages.cdn-apple.com`, QCY 상품 페이지의 `qcystore.co.kr`, [LG 상품 페이지](https://www.lge.co.kr/product/monitors/27gs50f)의 `www.lge.co.kr`이다. 같은 제품군 이름이라도 사양 인용은 판매가를 확인한 그 상품 페이지에 한정하여 다른 구성의 사양과 결합하지 않는다.

## 사용자 안내와 기존 데이터

홈과 조건 입력은 capabilities의 현재 정책 버전을 사용한다. 결과는 저장된 실행의 정책 버전을 사용하고, 카드·비교 표는 저장된 공식 offer가 있는 항목에만 판매처 정보를 표시한다. 과거 추천과 직접 입력에는 공식 여부를 소급하지 않는다. 홈·입력·결과 모두 다른 판매처·할인 경로가 더 저렴할 수 있음을 눈에 보이는 본문으로 안내한다.

기본 추천은 최소 사양을 모두 확인했고 예산 안에 있는 제품을 가격·이름 순으로 표시한다. 미확인·미달은 접힌 별도 목록에 둔다. 후보를 비교에 넣는 것은 여전히 사용자의 선택이며 수집 사양은 UNREVIEWED로 남는다. 제공되는 가격은 조회 시점의 상품 판매가이며 배송비·추가 할인은 판매처에서 확인한다.

v2의 기본 추천에는 선택한 호환성도 충족해야 한다. 이어폰의 기본 플랫폼 연결·사용과 앱·기기별 추가 기능을 구분하며 후자는 사양과 제약으로 별도 표시한다. 설명은 검증 결과에서 서버가 구성한다. v1 후보·설명은 새 기준으로 재작성하지 않고 과거 기준임을 표시한다. [ADR 0014](decisions/0014-evidence-based-discovery-validation.md)에 변경 이유와 한계를 기록한다.
