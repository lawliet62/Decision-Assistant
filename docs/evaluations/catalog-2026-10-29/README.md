# 국내 판매처 확장과 실제 가격 갱신 검증

## 실행 범위

2026-10-29 KST, 기존 Dev Secret Manager 인증으로 backend의 실제 Firecrawl adapter와 가격 worker를 실행했다. 로컬 PostgreSQL 17, 제품별 24시간 간격, 이번 smoke 월 예약 상한 6회를 사용했고 AI·검색·메일은 비활성화했다. [관측 원본](price-smoke.json)은 공개 상품 관측만 포함한다. 원문 HTML과 자격 증명은 보관하지 않았다.

| 제품 | 결과 | 확인 가격(KRW) |
| --- | --- | --- |
| QCY HT16 | VERIFIED | 31,800 |
| AirPods Pro 3, MFHP4KH/A | VERIFIED | 369,000 |
| QCY HT19 | MODEL_MISMATCH | 미반영 |
| QCY T13 ANC | PRODUCT_MISSING | 미반영 |
| QCY T13 Pro | VERIFIED | 24,300 |
| QCY T17 APP | VERIFIED | 29,900 |

HTTP 응답이 아니라 parser 결과와 DB 저장을 기준으로 4/6건 성공을 확인했다. backend가 월 예약 한도 6회를 사용한 뒤 추가 외부 호출을 하지 않는 설정으로 실행했다. 지속적인 가격 정확도나 일중 가격 변동을 입증하는 표본은 아니다.

## 등록 정정과 사양 검토

T13 ANC의 판매 URL은 직접 요청에서도 404였다. 같은 모델의 대체 판매 페이지가 확인되지 않아 revision 2에서 비활성화한다. 기존 사양과 개인 비교는 삭제하지 않는다. HT19는 같은 공식 상품 번호 1012의 구조화 이름이 바뀌었으며 색상 variant 이름도 현재 그룹명에 연결됨을 확인했다. 새 이름과 identity를 revision 2로 등록한다. 수정 이후의 실제 Firecrawl 재시도는 제품별 24시간 제한을 우회하지 않았으므로 아직 검증하지 않았다.

QCY T13 Pro의 공식 페이지에서 제품명·Bluetooth 6.0·기존 ANC·호환성·IPX4 인용을 다시 대조했다. T13 ANC의 글로벌 사양 페이지도 기존 제품명·Bluetooth 5.3·인용을 대조했으나 국내 판매 종료 상태와 분리했다. 이번 agent 검토를 revision과 확인 시각에 반영했다. 미래의 예약 문구 일치가 이 검토를 자동 대체하지 않는다.

Apple은 [현재 구매 페이지](https://www.apple.com/kr/shop/buy-airpods/airpods-pro-3)의 Offer SKU와 단일 구매 선택 링크의 product 값, 해당 링크 내부 가격을 함께 검증한다. [모델별 사양](https://www.apple.com/kr/airpods-pro/specs/)에서 ANC·IP57을 등록했으며 기본 플랫폼 호환성은 명시적인 판정 근거를 등록하지 않아 UNKNOWN으로 유지한다. 이 표의 가격은 실행 당시 관측이며 seed 가격으로 복사하지 않는다.

## 미검증 범위

상시 운영 호스팅·운영 DB·운영 인증·재배포는 아직 확인하지 않았다. 로컬 실행 성공이 PC를 끈 상태의 주기적 실행을 의미하지 않는다. GitHub 예약 실행과 실패 알림 수신도 이번 로컬 점검으로 증명하지 않는다. 삼성 버즈3 FE의 직접 응답은 제품 JSON-LD가 없는 공통 페이지였으므로 삼성 상품 등록은 보류했다. 다른 URL·렌더링된 Firecrawl 응답은 별도 확인이 필요하다.

## 추천 API smoke

정정된 manifest를 재기동으로 반영한 뒤 외부 수집을 끄고 실제 로컬 API를 호출했다. EARBUDS·ANC 필수·예산 500,000원·플랫폼 미선택·모든 외부 동의 false 요청이 HTTP 201을 반환했고, AirPods Pro 3는 RECOMMENDED / VERIFIED / 369,000 KRW로 노출됐다. QCY T13 Pro도 RECOMMENDED / VERIFIED / 24,300 KRW였다. HT19는 가격 미확인, T13 ANC는 비활성으로 새 후보에서 제외됐다. 이는 실제 DB→추천 응답 검증이며 실제 브라우저 렌더링·운영 배포는 포함하지 않는다.

## 추가 실제 수집

같은 날 정정된 HT19는 Firecrawl→DB 갱신 결과 VERIFIED, 48,500 KRW였다. 소니 Float Run은 상품 상세의 DOM 구조를 브라우저에서 확인하고 단일 상품 전용 검증을 추가했지만 실제 Firecrawl 응답의 결과는 PRODUCT_AMBIGUOUS였다. 당시 검사에서는 구매 영역이 없을 때와 여러 개일 때를 같은 코드로 기록했으므로 어느 경우인지는 확인하지 못했다. 이후 사유를 구분하도록 수정하고 상품을 비활성으로 보류했다.

후속 진단에서 기존 월 호출 기록에 예약한 Firecrawl 요청 4회를 추가했다. 기본 대기에서는 구매 영역이 0개였고, 소니 상품에 한해 5초 대기한 두 요청에서는 구매 영역이 1개였으나 상품 선택 링크 2개가 있어 `VARIANT_UNSUPPORTED`로 보류됐다. 링크의 한글은 진단 콘솔 인코딩 문제로 정확히 읽지 못했다. 별도 로컬 브라우저에서 단일 옵션을 선택했을 때 표시 판매가와 총 상품금액은 모두 159,000원이었다. 이 관찰만으로 Firecrawl 응답에서 선택 후 금액을 검증했다고 간주하지 않는다. 5초 대기는 진단에만 사용했으며 상품은 비활성 상태다. 개발 DB의 이번 달 예약 호출은 기존 6회, 가격 worker 2회, 진단 4회로 총 12회다.
