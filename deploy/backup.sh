#!/bin/sh
# 운영 VM에서 timer로 실행한다. 로컬 검증 중에는 실행하지 않는다.
set -eu
umask 077
: "${BACKUP_BUCKET:?승인된 비공개 backup bucket 필요}"
: "${DEPLOY_ENV_FILE:?비밀 아닌 운영 설정 파일 필요}"
cd /opt/decision-assistant
stamp=$(date -u +%Y%m%dT%H%M%SZ)
archive=$(mktemp /var/tmp/decision-backup.XXXXXX)
trap 'rm -f -- "$archive"' EXIT HUP INT TERM
docker compose --env-file "$DEPLOY_ENV_FILE" -f deploy/compose.yaml exec -T --user postgres postgres \
  pg_dump -U postgres -Fc decision_assistant > "$archive"
test -s "$archive"
gcloud storage cp "$archive" "gs://$BACKUP_BUCKET/backups/$stamp.dump" --quiet
echo "backup_completed timestamp=$stamp"
