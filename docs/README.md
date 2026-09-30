# 문서 색인

- [Product scope](product.md): 문제, MVP boundary, 성공 신호
- [Domain model](domain.md): 용어, aggregate, invariant
- [Business rules](business-rules.md): 사용자에게 관찰 가능한 도메인 규칙과 검증 상태
- [System architecture](architecture.md): runtime/data/security 경계
- [API contract](api-contract.md): OpenAPI 생성·소비 workflow
- [Development workflow](development.md): golden commands와 변경 절차
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
