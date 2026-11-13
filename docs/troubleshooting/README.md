# Troubleshooting

장애를 해결한 방법뿐 아니라, 기대한 개선이 나타나지 않은 이유와 아직 확인하지 못한 원인도 기록한다. 평가 보고서는 실행 결과의 원본이고, troubleshooting은 그 근거로 다음 진단에서 반복할 판단과 피할 오해를 설명한다.

## 사례와 진단 방법

- [Samsung의 선택 SKU·할인과 기본 연결](samsung-selected-price-and-basic-audio.md): 다중 모델·행사 기간·input property와 문서 직렬화·앱 지원 구분

- [Sony의 선택 후 가격과 HTTP client 차이](sony-selected-option-and-source-client.md): snapshot·공식 링크 정규화·Java 403과 Node 검증

- [JBL 삼성닷컴의 렌더링된 구매 근거](jbl-rendered-purchase-evidence.md): 배열형 JSON-LD·hidden input 제거·기준가와 혜택가 대조

- [웹 검색 조사 실패와 가격 검증 실패의 구분](catalog-research-incomplete.md): 미완성 응답, 실패 시 사용량 보존과 중복 호출 방지

- [결정 전체 선택에서 403이 반환되는 경우](stale-decision-selection-api.md): 구버전 backend와 최신 frontend의 실행 버전 불일치

- [로컬 배포·브라우저 검증의 실패 단계 구분](mvp-local-validation.md): Compose Secret 참조 병합과 fixture 전송 오류 구분

- [ANC·호환성 필수 조건의 미확인 원인 분리](required-audio-evidence.md): 실제 등록 근거 범위, 앱·기본 연결 구분, ANC 최대 감쇠량과 멀티포인트 근거 누락

- [연결 worktree에서 Codex Hook이 보이지 않는 경우](codex-worktree-hooks.md): 프로젝트 Hook 발견 실패와 사용자 Hook 검증
- [공식 문서 보완 후에도 추천 품질이 개선되지 않은 사례](discovery-quality-2026-10-06.md): 관찰 결과, 병목, 검증 한계와 미해결 상태
- [AI 품질 문제의 원인을 분리하는 방법](ai-quality-diagnosis.md): 수집·입력·추출·검증·표현을 구분하는 진단 절차

- [공식 판매처의 페이지 변경과 실제 갱신 실패](catalog-source-drift.md): 판매 종료·상품명 identity 변경·구매 가격 표현을 구분한 진단

## 자율 작성 기준

agent는 현재 승인된 작업에서 다음 중 하나를 발견하고, 증거와 재사용할 판단이 있으면 별도 작성 요청 없이 문서화한다.

- 예상과 다른 개선 효과를 측정했고 다음 투자 판단에 영향을 준다.
- 반복되거나 여러 단계에 걸친 실패를 진단해 다시 조사할 비용이 크다.
- 직관적인 원인과 실제 실패 단계가 달라 같은 오진을 예방할 수 있다.
- 해결 방법, 재현 조건 또는 미해결 원인의 구분이 후속 작업에 유용하다.

단순 작업 완료 로그, 평범한 일회성 수정, 코드가 이미 명확히 설명하는 목록은 만들지 않는다. 같은 문제가 있으면 기존 문서를 갱신하며 평가 원본·runbook·ADR의 내용을 복제하지 않고 연결한다. 문서 수를 채우기 위해 사례를 나누지 않는다.

문서에는 증상과 기대 결과, 실행 조건과 근거 링크, 확인된 사실과 가설, 시도한 조치와 검증 결과, 해결·부분 해결·미해결 상태, 다음 진단의 범위를 포함한다. 미실행 실험은 계획으로 표시하고 원인을 확정하지 못했다면 그대로 적는다. agent 평가와 사용자 평가도 구분한다. 비밀값, 개인 정보, 전체 환경 변수나 불필요한 원문 HTML은 저장하지 않는다.

문서화는 새 외부 호출·유료 실험·제품 정책 변경의 허가가 아니다. 필요한 진단이 기존 승인 범위를 벗어나면 기존 비용·동의·Human Decision Gate를 따른다. 문서 변경에도 기존 branch·검증·commit·PR·CI 절차가 적용된다.
