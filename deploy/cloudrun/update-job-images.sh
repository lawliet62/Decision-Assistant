#!/usr/bin/env bash
# 배포 workflow가 공개 주소 smoke 통과 뒤 실행한다. Cloud Run Job의 image만 이번 배포의 digest로 바꾼다.
# 사용법: update-job-images.sh <출력 key> <job 이름> <image digest> [<출력 key> <job 이름> <image digest> ...]
# 필요 env: PROJECT_ID, REGION. GITHUB_OUTPUT이 있으면 key=결과를 기록한다.
# 배포는 이미 끝났으므로 job이 없거나 갱신이 실패해도 0으로 끝나고, 결과를 릴리스 노트에 남긴다. 되돌리지 않는다.
# job 설정(env·서비스 계정·secret)은 바꾸지 않는다. describe 결과에는 env가 들어 있어 원문을 출력하지 않는다.
set -uo pipefail
: "${PROJECT_ID:?}" "${REGION:?}"
if (( $# == 0 || $# % 3 != 0 )); then
  echo "사용법: $0 <출력 key> <job 이름> <image digest> ..." >&2
  exit 2
fi

err="$(mktemp)"
trap 'rm -f -- "$err"' EXIT

record() {
  local key="$1" result="$2"
  echo "$key: $result"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then echo "$key=$result" >> "$GITHUB_OUTPUT"; fi
}

first_error_line() {
  grep -m 1 -E 'ERROR|error' "$err" | cut -c 1-300 || true
}

while (( $# )); do
  key="$1" job="$2" image="$3"
  shift 3
  if ! gcloud run jobs describe "$job" --project "$PROJECT_ID" --region "$REGION" --format='value(metadata.name)' \
      > /dev/null 2> "$err"; then
    # job 수준 권한만 있는 계정은 없는 job을 조회하면 NOT_FOUND 대신 권한 거부를 받을 수 있다.
    if grep -qiE 'NOT_FOUND|cannot find job|could not be found' "$err"; then
      result="건너뜀(job 없음)"
    else
      result="건너뜀(job 조회 실패: job이 없거나 조회 권한이 없음)"
    fi
    echo "::warning::$job: $result. $(first_error_line)"
    record "$key" "$result"
    continue
  fi

  if ! gcloud run jobs update "$job" --project "$PROJECT_ID" --region "$REGION" --quiet --image "$image" 2> "$err"; then
    result="실패(image 갱신 명령 거부. 운영자가 job image 확인 필요)"
    echo "::warning::$job: $result. $(first_error_line)"
    record "$key" "$result"
    continue
  fi

  actual="$(gcloud run jobs describe "$job" --project "$PROJECT_ID" --region "$REGION" --format=json 2> "$err" \
    | jq -r '[.spec.template.spec.template.spec.containers[].image] | if length == 1 then .[0] else "container \(length)개" end' \
    2>> "$err")" || actual=""
  if [[ "$actual" == "$image" ]]; then
    record "$key" "갱신함($job)"
  else
    result="실패(갱신 뒤 job image가 이번 digest와 다름: ${actual:-확인 불가})"
    echo "::warning::$job: $result"
    record "$key" "$result"
  fi
done
