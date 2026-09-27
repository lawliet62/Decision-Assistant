# 공식 판매처 추천의 수집 범위

`official-kr-v1`은 국내·한국어 판매 페이지를 먼저 검색한다. 공식 판매처 여부는 `OfficialStorePolicy`의 정확한 호스트·상품 경로에 한정한다. 검색 결과 제목이나 AI의 판단으로 판매자를 등록하지 않는다. 전체 브랜드·전체 시장의 카탈로그가 아니다.

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

이미지는 해당 Product의 이미지 필드만 사용하며 판매처별 확인한 이미지 호스트로 제한한다. 공식 이미지 CDN 호스트가 새로 필요하면 해당 상품 페이지의 근거와 테스트를 추가한다. 누락·차단·로딩 실패 시 대체 영역을 표시한다. 브라우저가 직접 요청하고 `referrerPolicy="no-referrer"`를 사용한다. IP 전송은 입력 화면에 안내한다. 이미지의 안전성·저작권이나 제품의 품질·정품·재고를 인증한다는 의미가 아니다.

## 사용자 안내와 기존 데이터

홈과 조건 입력은 capabilities의 현재 정책 버전을 사용한다. 결과는 저장된 실행의 정책 버전을 사용하고, 카드·비교 표는 저장된 공식 offer가 있는 항목에만 판매처 정보를 표시한다. 과거 추천과 직접 입력에는 공식 여부를 소급하지 않는다. 홈·입력·결과 모두 다른 판매처·할인 경로가 더 저렴할 수 있음을 눈에 보이는 본문으로 안내한다.

기본 추천은 최소 사양을 모두 확인했고 예산 안에 있는 제품을 가격·이름 순으로 표시한다. 미확인·미달은 접힌 별도 목록에 둔다. 후보를 비교에 넣는 것은 여전히 사용자의 선택이며 수집 사양은 UNREVIEWED로 남는다. 제공되는 가격은 조회 시점의 상품 판매가이며 배송비·추가 할인은 판매처에서 확인한다.
