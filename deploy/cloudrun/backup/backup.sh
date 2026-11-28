#!/bin/sh
# Cloud Run Job(decision-assistant-backup)에서 실행한다. 절차와 신뢰 경계: deploy/cloudrun/README.md
# 운영 DB를 pg_dump -Fc로 받아 비공개 bucket의 backups/neon/<UTC 시각>.dump에 새 object로 올린다.
# 접속 정보는 libpq 표준 env(PGHOST, PGDATABASE, PGUSER, PGPASSWORD, PGSSLMODE)로 받는다.
# 비밀번호·access token·접속 문자열은 출력하지 않는다.
set -eu
set -o pipefail
umask 077

fail() {
  echo "backup_failed reason=$1" >&2
  exit 1
}

for name in BACKUP_BUCKET PGHOST PGDATABASE PGUSER PGPASSWORD; do
  eval "value=\${$name:-}"
  [ -n "$value" ] || fail "missing_env:$name"
done
unset value
case "${PGSSLMODE:-}" in
  require | verify-ca | verify-full) ;;
  *) fail "pgsslmode_must_require_tls" ;;
esac
export PGCONNECT_TIMEOUT="${PGCONNECT_TIMEOUT:-30}"

# 기본값이 운영 주소다. 로컬 시험에서만 BACKUP_TEST_ENDPOINTS=1과 함께 바꾼다. job 정의에는 넣지 않는다.
metadata_base=http://metadata.google.internal
storage_base=https://storage.googleapis.com
if [ "${BACKUP_TEST_ENDPOINTS:-}" = 1 ]; then
  metadata_base="${BACKUP_METADATA_BASE_URL:-$metadata_base}"
  storage_base="${BACKUP_STORAGE_BASE_URL:-$storage_base}"
elif [ -n "${BACKUP_METADATA_BASE_URL:-}${BACKUP_STORAGE_BASE_URL:-}" ]; then
  fail "test_endpoints_not_allowed"
fi

stamp=$(date -u +%Y%m%dT%H%M%SZ)
object="backups/neon/$stamp.dump"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
trap 'exit 1' HUP INT TERM
dump="$work/backup.dump"

# 로그인 세션 table은 구조만 남기고 행은 넣지 않는다. 복원 가치가 없고 직렬화된 OIDC ID token·email claim을 담는다.
pg_dump --format=custom --no-password \
  --exclude-table-data=public.spring_session \
  --exclude-table-data=public.spring_session_attributes \
  --file="$dump" || fail "pg_dump"
[ -s "$dump" ] || fail "empty_dump"
pg_restore --list "$dump" > /dev/null || fail "unreadable_archive"
bytes=$(wc -c < "$dump" | tr -d ' ')

# 서비스 계정 access token. token은 권한 077의 header 파일로만 curl에 넘겨 명령 인자에 남기지 않는다.
curl --silent --show-error --fail --connect-timeout 5 --max-time 15 --retry 3 --retry-connrefused \
  --header 'Metadata-Flavor: Google' --output "$work/token.json" \
  "$metadata_base/computeMetadata/v1/instance/service-accounts/default/token" || fail "metadata_token"
token=$(jq -r '.access_token // empty' "$work/token.json") || fail "metadata_token_parse"
[ -n "$token" ] || fail "metadata_token_empty"
printf 'Authorization: Bearer %s\n' "$token" > "$work/auth.header"
unset token
rm -f -- "$work/token.json"

# 단일 요청 upload. ifGenerationMatch=0이라 같은 이름의 object가 있으면 412로 거부되고 덮어쓰지 않는다.
# 응답이 유실된 뒤 재시도하면 412가 되어 성공을 실패로 볼 수 있으므로 upload는 재시도하지 않는다.
# job 수준 재시도는 새 시각으로 새 object를 만든다.
encoded=$(printf '%s' "$object" | sed 's|/|%2F|g')
url="$storage_base/upload/storage/v1/b/$BACKUP_BUCKET/o?uploadType=media&ifGenerationMatch=0&name=$encoded"
status=$(curl --silent --show-error --connect-timeout 10 --max-time 900 \
  --request POST --header "@$work/auth.header" --header 'Content-Type: application/octet-stream' \
  --upload-file "$dump" --output "$work/response.json" --write-out '%{http_code}' "$url") || fail "upload_request"
rm -f -- "$work/auth.header"
if [ "$status" != 200 ]; then
  message=$(jq -r '.error.message // empty' "$work/response.json" 2>/dev/null | head -c 200 || true)
  echo "backup_failed reason=upload_status status=$status object=$object message=${message:-none}" >&2
  exit 1
fi

uploaded_name=$(jq -r '.name // empty' "$work/response.json") || fail "upload_response_parse"
uploaded_size=$(jq -r '.size // empty' "$work/response.json") || fail "upload_response_parse"
[ "$uploaded_name" = "$object" ] || fail "uploaded_name_mismatch"
if [ "$uploaded_size" != "$bytes" ]; then
  echo "backup_failed reason=size_mismatch object=$object local=$bytes uploaded=${uploaded_size:-none}" >&2
  exit 1
fi

echo "backup_completed object=gs://$BACKUP_BUCKET/$object bytes=$bytes"
