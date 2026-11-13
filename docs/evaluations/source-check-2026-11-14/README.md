# GitHub 사양 출처 점검의 실제 실패 분리

2026-11-14 KST에 기존 수동 점검 workflow를 main에서 한 번 실행했다. [실행 43466735086](https://github.com/lawliet62/Decision-Assistant/actions/runs/43466735086)은 실패로 종료됐고, [공개 요약](github-before.json)을 보존했다. 활성 카탈로그 25개 중 등록 사양 36개를 검사해 문구 33개를 찾았다. 문구 존재가 모델·사양 정확성을 보증하지 않으며 DB 확인 시각을 갱신하지 않는다. 미병합 PR #64의 Apple iOS 근거는 포함되지 않았다.

| 현상 | 확인된 범위 |
| --- | --- |
| Anker Liberty 5 두 사양 제목 불일치 | 직접 HTTP에서 제목의 `3D` 삭제 확인. 고정 canonical·h1·설명과 ANC·멀티포인트 근거 유지 |
| Samsung 버즈4 두 사양 제목 불일치 | 로컬 제목은 예상과 일치. GitHub 실제 제목이 당시 보고에 없어 원인 미확정 |
| Sony 두 출처 HTTP 403 | GitHub에서 관측. 로컬 Node 성공으로 클라우드 접근까지 보증할 수 없음 |
| HT10 사양 출처 HTTP 404 | 이전에도 발생한 출처 부재. 제목 수정으로 복구하지 않음 |

## 수정과 검증

Liberty 5의 확인된 두 제목만 정확히 허용한다. Java는 canonical·단일 h1·meta description과 기능별 근거를 계속 대조한다. Node 출처 점검도 같은 두 제목을 허용하며 문구 누락·HTTP 실패·기한 경고는 유지한다. 가격·사양 값·manifest·DB 시각·예약은 변경하지 않는다.

다음 진단에서 제목을 잃지 않도록 최대 500자의 관측 제목을 기록한다. 누락·복수·HTML 중첩·초과 길이는 진단 값으로 채택하지 않는다. summary에는 불일치 제목만 코드로 표시하고 마크업 구분자를 이스케이프한다. 진단 값을 자동 허용 목록 갱신에 사용하지 않는다.

[Anker 실제 입력](anker.json)은 HTTP 200 HTML을 메모리/stdin으로 두 검증기에 전달한 결과다. Java ANC·멀티포인트와 Node 문구·제목 점검이 모두 통과했다. 원문은 저장하지 않았고 유료 provider 호출과 DB 갱신은 없었다. Node 회귀 테스트 12개와 Java 사양 테스트를 통과했다.

## 남는 경계

첫 수정 branch의 [GitHub 점검](https://github.com/lawliet62/Decision-Assistant/actions/runs/43469831975)에서는 Anker 두 사양이 MATCH로 바뀌었다. Samsung은 여전히 MISMATCH였고, 로컬 실제 응답에서 문서 제목 외에 JavaScript 문자열의 팝업 HTML 제목까지 정규식이 센 것을 확인했다. 제목 추출에서 script·주석을 제외하도록 수정했다. 실제 복수 title은 계속 거절하며 회귀 테스트로 구분한다. 이 추가 수정의 클라우드 결과는 후속 실행에서 확인한다.

Sony 차단은 새 provider나 우회 수집으로 자동 전환하지 않는다. 에이전트도 같은 접근 제한을 만날 수 있으므로 403만으로 도입 효과를 주장하지 않는다. 코드 유지·진단 비용이 에이전트 검토 비용보다 커지는 근거가 생기면 기존 운영 기준에 따라 전환을 권고한다. 전체 자동 운영·장기 반복 검증은 미완료다.
`npm run check` 전체(단위·PostgreSQL 통합·계약·lint·typecheck·production build)를 통과했다.
`n최종 branch의 [실제 GitHub 점검](https://github.com/lawliet62/Decision-Assistant/actions/runs/43470207628)에서는 Samsung·Anker 네 사양 모두 문구·제목이 일치했다. [후속 요약](github-after.json)에 결과를 보존했다. 36개 중 문구 33개·그 33개 제목이 일치하며, Sony HTTP 403 두 항목과 HT10 HTTP 404 한 항목은 그대로 실패다. 따라서 workflow의 최종 failure는 코드 CI 실패가 아닌 남은 접근·출처 문제를 알리는 결과다. 위의 후속 확인 계획은 이 관측으로 수행했다.
