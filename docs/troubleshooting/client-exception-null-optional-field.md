# 서버 요청은 모두 성공인데 화면이 "This page couldn't load"로 깨지는 경우

## 증상과 기대 결과

2026-11-28 Cloud Run에 `v0.2.1`을 배포한 직후 운영자가 예산 없이 만든 결정에서 "후보 비교로 이동"을 누르자 화면 전체가 다음 문구로 바뀌었다.

```text
This page couldn't load
Reload to try again, or go back.
```

2단계(비교·평가) 화면이 열리는 것이 기대 결과였다. 배포 직후에 나온 증상이라 배포 방식의 문제로 보이기 쉽다.

## 원인과 확인 범위

확인된 사실:

- 그 시간대 Cloud Run 요청 log에서 해당 결정의 페이지·API 요청은 200·201뿐이었고 error 수준 container log가 없었다. 단계 전환 버튼은 서버를 거치지 않고 화면 단계만 바꾼다.
- 이 앱에는 `error.tsx`·`global-error.tsx`가 없다. 위 문구는 화면을 그리다 예외가 났을 때 Next.js가 보여 주는 기본 화면이다.
- 해당 결정은 DB에 예산과 저장된 비교 조건이 없었다. 이때 backend는 추천 조건의 최대 예산(추천으로 만든 결정) 또는 결정의 예산을 비교 조건의 `maxBudget`으로 쓰고(`Comparison.java`, `DecisionService.java`), 응답에서 `null`을 생략하는 Jackson 설정은 없다. 따라서 응답의 `comparison.settings.maxBudget`은 `null`이다(코드로 확인했고, 운영 응답 본문을 직접 보지는 않았다).
- `BudgetInput`은 값이 없는 경우를 `value === undefined`로만 검사했다. `null`이 들어오면 `value.toLocaleString(...)`에서 예외가 난다. mock backend가 `maxBudget: null`을 내려보내게 한 e2e에서 같은 화면 전환을 실행해 재현했다.

  ```text
  Uncaught TypeError: Cannot read properties of null (reading 'toLocaleString')
      at BudgetInput (shared/ui/budget-input.tsx:29:58)
  ```

- frontend 소스와 `deploy/frontend.Dockerfile`은 직전 revision을 만든 commit과 이번 배포 commit 사이에 차이가 없다.

추정:

- 직전 revision에서 비교 화면을 확인했을 때는 예산을 입력한 결정이었을 것이다. 같은 결정으로 직전 revision을 다시 열어 보지는 않았다.
- 운영 브라우저의 console 오류는 보지 못했다. 위 오류 문구는 로컬 e2e의 것이다.

## 조치

상태: **해결(해당 component).** `BudgetInput`이 `null`도 "제한 없음"으로 다루게 했고, `maxBudget: null`인 결정으로 2단계를 열어 예산을 정하는 e2e(`frontend/e2e/comparison-no-budget.spec.ts`)를 추가했다.

미해결: OpenAPI에서 만든 타입은 선택 속성을 `number | undefined`로만 표현하지만 실제 응답은 `null`을 보낸다. 같은 불일치가 다른 선택 속성에도 있을 수 있고, 타입 검사는 이를 잡지 못한다. 전수 조사는 하지 않았다.

## 다음 진단에서 반복할 판단

- 화면이 통째로 깨졌는데 서버 요청이 모두 성공이고 container log에 오류가 없으면, 배포나 backend보다 브라우저의 렌더링 예외를 먼저 본다. 서버에는 흔적이 남지 않으므로 브라우저 console의 첫 오류가 가장 빠른 단서다.
- 배포 직후의 증상이라도 직전 revision과 소스 차이가 없으면 데이터 조합을 의심한다. 선택 입력(예산, 호환성 등)을 비운 결정으로 재현해 본다.
- e2e fixture에서 값이 없는 선택 속성은 생략하지 말고 실제 API처럼 `null`로 둔다. 생략하면 이 종류의 버그가 통과한다.
