# Known limitations / deferred decisions

## 구현된 배포 baseline

- Auth0 OIDC Authorization Code + PKCE, PostgreSQL-backed Spring session, CSRF
- exact-email invite allowlist와 모든 Decision 계열 데이터의 user ownership

## 배포 전 반드시 해결

- GCP Secret Manager 초기 로더는 구현했지만 실제 Dev/Prod 프로젝트·IAM·Secret 등록·Data Access 로그와 smoke는 운영자가 검증해야 한다. 운영 호스팅과 Workload Identity 연결은 아직 미정이다. SDK/오프라인 테스트 통과를 production 연결 완료로 취급하지 않는다.
- TLS, CORS/host 정책, backup/restore
- 개인정보/AI provider 고지와 retention/delete 정책
- rate limit과 AI 비용 한도

## 사용 데이터가 생긴 뒤 결정

- Laptop vertical의 실제 사용자와 acquisition channel
- template version upgrade 정책
- weight elicitation UX와 sensitivity analysis
- 동점 추천 표시와 `ARCHIVED` lifecycle 정책
- Algorithm/LLM 품질 평가 지표와 결합 여부
- category를 넘는 preference transfer와 실제 outcome 기반 confidence 승급 정책
- Google과 이메일 OTP identity의 명시적 account linking

## 실제 평가에서 확인한 결함

- v1 실제 평가에서 회원 가격 수용, 기본 사양 누락과 근거 없는 용도 설명을 확인했다. [출처 검토](evaluations/discovery-a-2026-09-30/source-review.md). v2는 본문 구매 가격 대조, 상품별 사양 블록·단위 정규화, 필수 호환성, 검증 결과의 설명을 추가한다. 이는 해당 회귀 사례의 수정이며 전체 시장의 가격·사양 정확성 보증은 아니다.
- v2의 본문 가격 대조·상품 사양 영역은 구조화 microdata와 확인한 LG/QCY 구조를 우선 지원한다. 구조가 다르거나 가격 조건을 연결할 수 없으면 후보가 제외되므로 공식 판매처 등록만으로 수집 성공을 보장하지 않는다. 이미지 속 사양과 누락된 기본 호환성은 여전히 미확인일 수 있다.
- [v2 실제 15조건 평가](evaluations/discovery-v2-2026-09-30/README.md)는 후보 3개를 반환했지만 모두 확인 필요였고 기본 추천 2개 이상은 0/15였다. 수정한 결함을 직접 파서 진단으로 확인한 것과 서비스의 추천 품질 충족은 구분한다. 수집 URL·상품 연결과 사양 확보가 후속 검토 대상이다.
- 이어폰의 기종 선택·강력한 전용 기능 선택은 후속 과제다. 현재 기본 호환성을 필수 검사하고, 근거가 있는 앱·기기별 추가 기능과 제한을 별도 표시한다. 구체 기능·기종의 지원 행렬이나 OCR·리뷰 수집을 추가하지 않았다.
- v3 공식 문서 보완은 초기 등록한 QCY 출처·식별 가능한 단일 모델·명시적 사양 영역에 한정한다. HT16/MeloBuds N50 같은 지역 별칭, 이미지/PDF 사양, 다른 브랜드 지원 문서는 자동 연결하지 않는다. 보완 검색은 가격을 검증한 후보가 있을 때만 가능하므로 상품·가격 확보 병목은 별도로 남는다. [ADR 0015](decisions/0015-official-specification-supplement.md).

## 의도적으로 단순화한 부분

- 최소 사양은 포트폴리오용 MVP로 객관적 수치·지원 여부만 비교한다. 사양의 표현이 모호하거나 구성이 섞이면 확인 필요로 남긴다. CPU 성능·음질·배터리 사용 시간과 점수 평가 연동은 보류한다. 자동 인용 검사는 의미 정확성을 보장하지 않으며 실제 제품·구성 확인은 필요하다. [ADR 0010](decisions/0010-minimum-product-requirements.md)을 따른다.

- 제품명 검색은 Serper의 웹 결과를 고르게 할 뿐 상품 catalog/동일 모델 판별 시스템이 아니다. 결과는 0~5개이며 세대·색상·유통·광고성 페이지 모호성은 사용자가 판단한다.
- 이어폰의 50,000~500,000원 anchor, 선형 만족도 환산, 가중합은 검증된 인간 의사결정 모델이 아니다. 기본 중요도는 동일하며 기존 우선순위/확인 성향 동작은 유지한다. 음질·착용감·ANC·배터리 적합도는 사용자 1~5점 입력이 필요하다. 주요 정보 비교·실제 선택에는 rating 입력이 필요 없다. 검증된 점수 평가를 선택한 경우에만 주관 만족도 입력이 필요하다.
- Serper 월 1,000회 상한은 구현했지만 AI/Firecrawl 계정 전체 비용 한도를 대체하지 않는다. [배포 runbook](deployment.md)의 운영 전제와 실제 provider smoke가 필요하다.
- 생성 시 동의하지 않은 비교에 나중에 웹/AI 동의를 추가하는 별도 UI는 이번 범위에 없다. 직접 입력은 항상 가능하다. 기존 AI 평가는 별도 명시적 동의 후 실행한다.

- 기존 평가·구조화 AI는 요청 내 동기 실행이며 30초 timeout이다. 클릭 추천은 저장된 비동기 작업에서 같은 AI timeout을 사용한다. 실제 latency/traffic이 문제일 때 job queue를 검토한다.
- single instance를 전제로 한다. cache/message broker가 없다.
- 공개 URL 수집은 Firecrawl 구성 시에만 동작하며 사이트 차단·동적 콘텐츠·추출 오류가 있을 수 있다. 실패하면 사용자가 검증 입력을 보강한다.
- 기존 대안 탐색은 최대 3개, 클릭 추천은 최대 5개다. 전체 시장·최저가·재고 완전성을 보장하지 않는다. 페이지 인용 검사는 문맥·구성·사양의 의미 정확성까지 보장하지 않으므로 자동 정보는 확인이 필요하다.
- guided decision 핵심 경로에는 mock backend 기반 end-to-end browser test가 있다. 실제 Firecrawl·Resend까지 포함한 staging smoke test와 observability dashboard는 첫 배포 흐름이 정해질 때 추가한다.
- reminder의 `SENT`는 Resend API가 요청을 접수했다는 뜻이다. 실제 배달·반송을 반영하는 webhook reconciliation은 아직 구현하지 않았다.
- 첫 로그인 방식을 바꾸는 account linking UI는 제공하지 않는다. 동일 이메일만으로 identity를 자동 병합하지 않는다.
