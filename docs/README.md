# 문서 색인

## 개발자 시작점

1. [도메인 개요](domain.md): 전체 사용자 흐름, 도메인 지도와 공통 경계
2. 도메인 상세: [카탈로그](domains/catalog.md), [탐색·근거](domains/discovery-evidence.md), [결정·비교·템플릿](domains/decision-comparison.md), [평가](domains/evaluation.md), [회고·알림·개인화](domains/outcome-personalization.md), [과거 판단 기록 활용](domains/history-reuse.md), [사용자·소유권](domains/identity.md)
3. [UI 화면 모음](ui/README.md): 과정별 대표 화면과 재촬영 방법
4. [Architecture](architecture.md): 현재 package 책임, 실행 흐름과 보안·데이터 경계
5. [Business rules](business-rules.md): 동작 규칙과 검증 시작점 · [Development workflow](development.md): 개발·전달 절차

도메인 설명은 위 개요·상세 문서, 강제할 동작은 Business rules, 장기 선택의 이유는 ADR에 둔다. 화면 변경 시 관련 이미지와 촬영 metadata를 갱신하고, 과거 검증 이미지는 아래 evaluations 기록으로 보존한다.

## 설계·운영·검증 자료

- [결정별 요약과 과거 이유 설명](decisions/0023-contextual-decision-memory.md): 명시적 동의·조건부 AI 해석·원본 근거·현재 메모에 선택 적용

- [결정 이유와 재사용 기록](decisions/0022-decision-reasons-and-reusable-history.md): 이유 GUI·구매 상태·구매 전 재검토·원문 과거 기록 활용
- [11월 17일 카탈로그 반복 갱신](evaluations/catalog-refresh-2026-11-17/README.md): 25회 시도·21개 유효 가격·격리 API 대조·주간 출처 점검 결과

- [결정 완료와 기록 정정·재검토](decisions/0021-decision-completion-and-revisions.md): 완료 결과 분리, 과거 근거·회고 보존과 개인화 연결

- [결정 보관·휴지통과 개인화 활용](decisions/0020-decision-retention-and-personalization.md): 30일 복구·만료 삭제·복원 안내
- [Jabra 실제 수집 검증](evaluations/jabra-2026-11-14/README.md): 구매 영역 거절·inline 근거 부재·단일 호출과 등록 보류

- [CT06 개발 DB 실제 갱신](evaluations/catalog-refresh-2026-11-14/README.md): 기한 도래 직접 HTTP·DB/API 반영·Apple iOS manifest 적용과 예약 보존

- [GitHub 출처 점검 실패 분리](evaluations/source-check-2026-11-14/README.md): 제목 변경·환경별 403·출처 404와 관측 제목 진단

- [Apple iOS 기본 연결 보강](evaluations/apple-ios-2026-11-14/README.md): 모델별 사양 검증·기존 가격 시각 보존과 실제 추천 후보 증가
- [젠하이저 행사명 변경 검증](evaluations/sennheiser-event-2026-11-14/README.md): 고정 상품의 canonical·제품명 변경과 가격·사양 실제 입력 검증

- [주류 이어폰 3개 브랜드 통합 검증](evaluations/catalog-mainstream-2026-11-13/README.md): 25개 카탈로그 조합·실측 근거·비용과 미완료 항목
- [Samsung 버즈4 실제 검증](evaluations/samsung-buds4-2026-11-13/README.md): 선택 SKU·즉시 할인·Android/iOS 기본 연결

- [Sony WF-C710N 실제 검증](evaluations/sony-c710n-2026-11-13/README.md): 선택 후 판매가·사양 시각 갱신·개발 DB/API 검증과 남은 범위

- [JBL TUNE BEAM 2 국내 공식 판매 검증](evaluations/jbl-tune-beam2-2026-11-13/README.md): 실제 가격·Java 사양 갱신·DB/API 결과와 남은 범위

- [이어폰 데이터 확대와 혼합 수집](catalog-expansion.md): 주류 브랜드·가격대 확대 완료 기준과 제한된 OpenAI 공식 출처 조사

- [MVP 배포 준비 보고서](mvp-readiness.md): 승인된 범위·검증 결과·무료 VM 가능성·배포 전 남은 작업
- [단일 VM 배포 자산](../deploy/README.md): 컨테이너·로컬 smoke·백업/복원·비용 통제
- [목록 관리·영구 삭제 결정](decisions/0018-decision-management-and-deletion.md)
- [초대형 MVP 배포 준비 결정](decisions/0019-portfolio-deployment-preparation.md)

- [조건 비교와 결정 당시 근거](decisions/0017-comparison-and-resolution-context.md): 같은 후보에서 조건 변경, 직접 확인, 보류·복원, 결정 snapshot
- [비교 흐름 검증과 사용자 평가 준비](evaluations/comparison-flow-2026-11-05/README.md): 자동 검증·가상 자료·범용 AI와 비교할 과제·미검증 한계

- [등록 제품 카탈로그 운영](catalog-operations.md): 가격 48시간 갱신·72시간 유효, 사양 30/90일, 호출 한도, 출처 검토와 주기 평가
- [카탈로그 MVP 결정](decisions/0016-curated-catalog.md): 후보 없는 진입 유지, 이어폰 범위 축소, 캐시 도입 유보
- [카탈로그 실제 갱신·국내 판매처 확장 평가](evaluations/catalog-2026-10-29/README.md): 실제 Firecrawl→DB→추천 API와 판매 종료·상품명 변경 진단
- [카탈로그 초기 평가](evaluations/catalog-2026-10-06/README.md): 공식 근거 범위, DB 성능과 미완료 운영 관측

- [Product scope](product.md): 문제, MVP boundary, 성공 신호
- [Domain model](domain.md): 용어, aggregate, invariant
- [Business rules](business-rules.md): 사용자에게 관찰 가능한 도메인 규칙과 검증 상태
- [System architecture](architecture.md): runtime/data/security 경계
- [API contract](api-contract.md): OpenAPI 생성·소비 workflow
- [Development workflow](development.md): golden commands와 변경 절차
- [Troubleshooting](troubleshooting/README.md): 재사용 가능한 문제 진단·개선 효과 분석과 작성 기준
- [Stacked PR 운영](stacked-prs.md): App 초기 설정·등록·순차 squash merge·restack 복구
- [Stacked PR 자동화 결정](decisions/0012-stacked-pr-restack.md): 사람의 병합과 AI 없는 후속 기반 정리
- [Deployment runbook](deployment.md): MVP 설정·검색 비용 한도·장애 대응·staging smoke
- [Known limitations](known-limitations.md): 배포 blocker와 deferred decision
- [클릭 중심 추천](decisions/0009-click-first-product-discovery.md): 템플릿 진입·외부 동의·표시용 사양 경계
- [최소 사양 조건](decisions/0010-minimum-product-requirements.md): 포트폴리오 MVP의 객관적 조건·미달 분리·불확실성
- [공식 판매처 중심 추천](decisions/0011-official-store-discovery.md): 승인된 추천 범위·가격 정렬·고지·추가 탐색과 수집 개선
- [공식 판매처 등록과 검증](official-stores.md): 판매처 근거·가격/이미지 수집 범위·화면 안내
- [제품 탐색 A안 평가](discovery-evaluation.md): 실제 provider 비교 절차·호출 상한·평가 기준
- [상품 근거·호환성·설명](decisions/0014-evidence-based-discovery-validation.md): v2 가격 대조·사양 정규화·필수 호환성·검증 결과의 설명
- [ADR](decisions/): 장기간 유지할 architecture decision과 trade-off
- [Identity-based secrets](decisions/0008-identity-based-secret-management.md): Dev/Prod 격리, runtime 조회와 cloud-free 테스트 전략

AGENTS.md는 작업 시 반드시 필요한 짧은 지침과 링크만 담는다. 설계의 이유와 상세 설명은 이곳에서 관리한다.
