# Sony WF-C710N 실제 수집·사양 검증

2026-11-13 개발 환경의 고정 상품 `sony-wf-c710n-glass-blue`를 확인했다. 전체 목표 중 Sony 한 모델의 검증이며 운영 활성화·장기 안정성·주류 제품 확보의 완료를 의미하지 않는다.

| 항목 | 실제 확인 결과 |
| --- | --- |
| 구매 페이지 | https://store.sony.co.kr/product-view/128838586 |
| 구성 | WF-C710N / 글래스 블루, 수량 1 |
| worker 가격 관측 | VERIFIED, 139,000 KRW |
| 가격 확인 시각 | 2026-11-13T11:12:12.504216700Z (20:12 KST) |
| 다음 가격 시도 | 2026-11-15T11:11:57.290048Z (20:11 KST) 이후 |
| 사양 | ANC, Android/iPhone 기본 연결 근거 |
| Node 조회→Java 검증→Dev DB 갱신 | 두 출처 검증, fact_reviews 2026-11-13T11:45:13.564Z (20:45 KST) |
| 다음 사양 시도 | 2026-11-20T11:45:12.868746Z 이후 |
| 실제 API 조건 | 예산 150,000원, ANC 필수, Android, 외부 동의 모두 false |
| 실제 API 결과 | RECOMMENDED, ANC MEETS, 호환성 MEETS, 가격·원래 가격 확인 시각 보존 |
| 개발 DB | 활성 23개, 8개 브랜드 |

성공 가격 관측 한 건과 등록 정보를 격리 DB에서 기본 개발 DB로 옮길 때 기존 제품·관측을 덮어쓰지 않았고 활성 30개 한도를 확인했다. quota를 중복 복사하지 않았다. 원문 HTML·자격 증명·사용자 정보는 저장하지 않았다.

## 실패와 수정

선택 전 HTML에는 구매 구성이 없어 Firecrawl의 색상 선택 후 snapshot을 사용했다. 첫 worker는 PRICE_CONTEXT_MISSING으로 실패했다. 단계 코드 진단에서 구매 링크가 상대 URL에서 같은 공식 절대 URL로 바뀌는 차이를 확인했다. 공식 HTTPS 주문 주소와 정확히 일치하는 경우만 정규화한 뒤 재검증이 성공했다. 최초 실패 격리 DB의 다음 시도(2026-11-15 17:22 KST)는 단축하지 않았고 교정 검증은 별도 DB에서 진행했다.

Java 사양 HTTP 조회는 두 페이지 모두 403이었다. Node 조회는 200이며 같은 Java 검증기가 모델·제목·canonical·정확한 인용을 확인했다. 실제 helper 갱신은 두 fact_reviews만 연장했고 가격·다음 가격 시도를 보존했다. iPhone을 iOS로 자동 해석하지 않아 iOS 조건은 UNKNOWN이다. [진단 기록](../../troubleshooting/sony-selected-option-and-source-client.md)을 참고한다.

## 비용과 남은 범위

`npm run check` 전체 검증(단위·PostgreSQL 통합·계약·lint·typecheck·build)을 통과했다. 실제 기본 개발 DB를 사용하는 `node scripts/gradle.mjs bootRun`에서 helper 기동, 가격·사양 시각 보존, API 추천, backend 종료 후 helper 종료를 확인했다. API 확인용 임시 결정은 expectedVersion을 적용한 DELETE로 삭제했다. 통합 시험의 fixture 가격과 위 실제 공급자 관측은 구분한다.

Sony 교정에서 앱 Firecrawl 시도 계수가 48에서 52로 증가했다(실패·진단 포함 4회). 공급자 청구 내역이나 남은 무료 크레딧은 확인하지 않았으므로 실제 청구 credit과 동일시하지 않는다. OpenAI 추가 호출과 새 유료 리소스 생성은 없었다.

기존 제품의 예약된 실제 재조회, Sony의 48시간 후 반복 갱신, Samsung·JBL과 가격대·형태 보완은 남아 있다. 고정 펌웨어 버전이나 옵션이 바뀌면 자동 승격하지 않는다. GitHub Actions 문구 점검과 DB 확인 시각 갱신은 별개다.
