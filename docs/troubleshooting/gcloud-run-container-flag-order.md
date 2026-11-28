# `gcloud run services update`가 존재하는 flag를 "unrecognized arguments"로 거부하는 경우

## 증상과 기대 결과

2026-11-28 `Cloud Run deploy` workflow(`.github/workflows/deploy-cloud-run.yml`)의 [첫 실행](https://github.com/lawliet62/Decision-Assistant/actions/runs/46130545418)(`main` `805986c`)에서 image push까지 통과한 뒤 후보 revision을 만드는 단계가 exit code 2로 실패했다.

```text
ERROR: (gcloud.run.services.update) unrecognized arguments:
  --no-traffic (did you mean '--no-traffic'?)
  --tag (did you mean '--tag'?)
  candidate
  --quiet (did you mean '--quiet'?)
```

실행한 명령은 다음 형태였고, traffic 없는 새 revision이 `candidate` tag와 함께 만들어지는 것이 기대 결과였다.

```bash
# 실패하는 순서다. 고친 순서는 "조치" 절에 있다.
gcloud run services update "$SERVICE" --project "$PROJECT_ID" --region "$REGION" \
  --container frontend --image "$FRONTEND_IMAGE" \
  --container backend --image "$BACKEND_IMAGE" \
  --no-traffic --tag "$CANDIDATE_TAG" --quiet
```

오류가 되묻는 이름이 입력한 flag와 같아서 오타나 gcloud version 문제로 읽기 쉽다. 실제 원인은 flag의 위치다.

## 원인과 확인 범위

확인된 사실:

- multi-container 서비스에서 `--container <이름>` 뒤에 오는 flag는 그 container의 flag로 해석된다. `--no-traffic`, `--tag`, `--quiet`는 서비스 단위 flag라서 container flag 자리에서는 인식되지 않는다.
- gcloud 570.0.0에서 같은 인자를 두 순서로 실행해 비교했다. 위 순서는 같은 오류로 거부된다. 서비스 단위 flag를 첫 `--container` 앞으로 옮기면 인자 해석을 통과해 API 호출 단계까지 간다.
- 인자 해석은 인증과 API 호출보다 먼저 일어난다. 그래서 이 오류는 권한이 없는 환경에서도 임의의 서비스 이름으로 재현되고, 거부된 명령은 서비스에 아무것도 바꾸지 않는다. 첫 실행에서도 새 revision은 생기지 않았고, 뒤이어 실행된 정리 단계(`update-traffic --remove-tags`)의 출력은 traffic `100% LATEST (currently decision-assistant-00002-dq6)`로 배포 전과 같은 revision이었다.
- `--project`, `--region`처럼 `--container` 앞에 둔 flag는 영향이 없었다.

확인하지 않은 것:

- runner의 gcloud version. `setup-gcloud`가 `latest`를 설치했고 오류 문구는 570.0.0과 같았다.
- 다른 `gcloud run` 하위 명령(`deploy`, `jobs update`)이 같은 규칙을 따르는지.

## 조치

상태: **해결.** 순서를 고친 뒤의 [두 번째 실행](https://github.com/lawliet62/Decision-Assistant/actions/runs/46134019406)(`main` `6ebd2fd`)에서 같은 단계가 traffic 0%의 revision을 만들었고 후보 smoke, traffic 전환, Release 생성까지 통과했다.

서비스 단위 flag를 첫 `--container` 앞에 둔다.

```bash
gcloud run services update "$SERVICE" --project "$PROJECT_ID" --region "$REGION" \
  --no-traffic --tag "$CANDIDATE_TAG" --quiet \
  --container frontend --image "$FRONTEND_IMAGE" \
  --container backend --image "$BACKEND_IMAGE"
```

## 다음 진단에서 반복할 판단

- "unrecognized arguments"가 존재하는 flag 이름을 그대로 되물으면 이름이 아니라 위치를 먼저 본다.
- 실제 리소스를 바꾸는 gcloud 명령도 인자 해석은 권한 없이 확인할 수 있다. 병합 전에 가짜 project·서비스 이름으로 같은 인자를 실행해, 인자 오류가 아니라 인증 또는 API 오류까지 가는지 본다. 이번 workflow는 이 명령을 병합 전에 실행하지 않았고(PR #104의 [미검증 항목](https://github.com/lawliet62/Decision-Assistant/blob/805986c/deploy/cloudrun/README.md#확인한-항목과-미검증-항목)에 "승인된 실행으로만 확인할 수 있다"고 적었다), 인자 해석만 따로 확인하는 절차도 없어 첫 배포에서 실패했다.
- workflow의 gcloud 명령을 바꾸면 [배포 운영 문서](../../deploy/cloudrun/README.md)의 같은 명령 표기도 함께 바꾼다.
