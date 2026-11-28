#!/usr/bin/env bash
# Cloud Run 배포 smoke: 로그인 화면(200)과 미인증 API 거절(401)만 확인한다.
# 사용법: smoke.sh <https base URL> [제한 시간(초), 기본 300]
# 인증 정보나 cookie를 보내지 않으며 응답 본문을 출력하지 않는다.
set -euo pipefail

base="${1:?base URL이 필요합니다}"
base="${base%/}"
limit="${2:-300}"
if [[ ! "$base" =~ ^https://[A-Za-z0-9.-]+$ ]]; then
  echo "path 없는 https 주소만 허용합니다: $base" >&2
  exit 2
fi
if [[ ! "$limit" =~ ^[0-9]+$ ]]; then
  echo "제한 시간은 초 단위 정수여야 합니다: $limit" >&2
  exit 2
fi

status() {
  # 콜드 스타트(JVM 기동, Neon 재개) 동안 Cloud Run이 요청을 붙잡고 있을 수 있어 요청당 제한을 길게 둔다.
  curl --silent --output /dev/null --write-out '%{http_code}' \
    --max-time 60 --proto '=https' "$base$1" 2>/dev/null || true
}

started=$SECONDS
deadline=$((started + limit))
attempt=0
while true; do
  attempt=$((attempt + 1))
  login="$(status /login)"
  me="$(status /api/v1/me)"
  echo "시도 ${attempt}: /login=${login:-000} /api/v1/me=${me:-000}"
  if [[ "$login" == "200" && "$me" == "401" ]]; then
    # 마지막 줄은 배포 workflow가 릴리스 노트의 확인 결과로 그대로 쓴다.
    echo "smoke 통과: $base (/login=200, /api/v1/me=401, 시도 ${attempt}회, $((SECONDS - started))초)"
    exit 0
  fi
  if ((SECONDS >= deadline)); then
    echo "smoke 실패: ${limit}초 안에 /login 200, /api/v1/me 401을 확인하지 못했습니다 ($base)" >&2
    exit 1
  fi
  sleep 5
done
