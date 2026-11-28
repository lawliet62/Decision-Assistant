# Troubleshooting

- [OpenAPI 객체 속성 순서에 따른 계약 검사 실패](openapi-property-order.md): 값·구조 차이와 직렬화 순서 차이의 구분

- [카탈로그 갱신의 유효 가격·사양 시각·부분 실패](catalog-refresh-invariants.md): 과거 성공과 현재 유효성, JSON 표현·Instant 비교, 원본 DB와 API 검증 분리

- [Jabra 직접 HTML과 Firecrawl 근거 차이](jabra-provider-evidence-gap.md): 첫 거절 단계와 후속 재고 근거 확인, 등록 보류

장애를 해결한 방법뿐 아니라, 기대한 개선이 나타나지 않은 이유와 아직 확인하지 못한 원인도 기록한다. 평가 보고서는 실행 결과의 원본이고, troubleshooting은 그 근거로 다음 진단에서 반복할 판단과 피할 오해를 설명한다.

## 사례와 진단 방법

- [로그아웃이 403으로 거부되는 경우](logout-csrf-token-rotation.md) — 양식과 쿠키의 CSRF 토큰 교체 시점, 기존 세션과 새 인증 구분
- [로그아웃 후 로그인 화면에서 RSC 요청이 CORS 오류를 내는 경우](login-link-rsc-cors.md): cross-origin으로 리다이렉트되는 backend 경로의 `<Link>`, RSC·prefetch header 확인
- [로그인 직후 backend 내부 주소로 이동하는 경우](login-saved-request-redirect.md): server-side 조회가 저장한 요청과 로그인 성공 redirect, 익명 API의 session 발급 확인
- [Cloud Run에서 로그인 직후 연결 거부 화면으로 이동하는 경우](login-success-forwarded-host.md): host 없는 `Forwarded` header와 상대 경로 redirect의 절대화, `X-Forwarded-*` 무시 확인

- [UI 촬영 성공과 화면 준비 완료의 구분](ui-gallery-capture.md): 목록 로딩·viewport 변경 중 전체 페이지 높이와 hydration 지연 확인
- [겹친 Gradle 테스트의 결과 파일 충돌](parallel-gradle-test-output.md): 동일 build 디렉터리의 EOF·결과 파일 유실과 순차 검증

- [GitHub 출처 점검 실패 분리](../evaluations/source-check-2026-11-14/README.md): 로컬 성공과 클라우드 접근의 구분, 제목 변경을 제한적으로 처리한 사례
- [Claude Code에서 지침 파일이 로드되지 않는 경우](claude-code-instruction-loading.md): 루트 `CLAUDE.md`와 하위 폴더 `AGENTS.md`의 로드 조건, `/context` 확인
- [Windows 임시 worktree에서 로컬 명령이 실패하는 경우](windows-worktree-permissions.md): Codex CLI의 Hook 점유, 실행기 오류 740, Git 소유권, 실제 파일 접근 실패의 구분
- [Turbopack build가 CSS 처리용 Node 자식 프로세스에 연결하지 못하는 경우](turbopack-postcss-pool-child.md): `PATH`의 Node로 실행되는 PostCSS 자식과 loopback TCP 연결, exit code 0의 해석(추정), 미해결 상태

- [개발 실행 종료 후 backend가 남는 경우](dev-server-process-shutdown.md): Node wrapper의 신호 전달·하위 서버 정리와 OS별 검증

- [Samsung의 선택 SKU·할인과 기본 연결](samsung-selected-price-and-basic-audio.md): 다중 모델·행사 기간·input property와 문서 직렬화·앱 지원 구분

- [Sony의 선택 후 가격과 HTTP client 차이](sony-selected-option-and-source-client.md): snapshot·공식 링크 정규화·Java 403과 Node 검증

- [JBL 삼성닷컴의 렌더링된 구매 근거](jbl-rendered-purchase-evidence.md): 배열형 JSON-LD·hidden input 제거·기준가와 혜택가 대조

- [웹 검색 조사 실패와 가격 검증 실패의 구분](catalog-research-incomplete.md): 미완성 응답, 실패 시 사용량 보존과 중복 호출 방지

- [결정 전체 선택에서 403이 반환되는 경우](stale-decision-selection-api.md): 구버전 backend와 최신 frontend의 실행 버전 불일치

- [로컬 배포·브라우저 검증의 실패 단계 구분](mvp-local-validation.md): Compose Secret 참조 병합과 fixture 전송 오류 구분
- [읽기 전용 VM 점검에서 `gcloud compute ssh`가 프로젝트 메타데이터를 변경하려는 경우](gcloud-ssh-project-metadata-write.md): IAP 터널과 SSH 클라이언트 분리, 호스트 키 확인, Windows 키 권한·PowerShell 인수 손상

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
