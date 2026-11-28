# 백업 job image. Neon과 같은 PostgreSQL 17의 pg_dump·pg_restore와 upload script만 쓴다.
# build context는 이 디렉터리다: docker build -f deploy/cloudrun/backup/Dockerfile deploy/cloudrun/backup
ARG BASE_IMAGE=postgres:17-alpine
FROM ${BASE_IMAGE}
# curl은 metadata server와 Cloud Storage JSON API 호출, jq는 응답 해석에 쓴다. gcloud·gsutil은 넣지 않는다.
RUN apk add --no-cache curl jq \
  && adduser -D -H -u 10001 -s /sbin/nologin backup
COPY --chmod=0555 backup.sh /usr/local/bin/backup.sh
USER 10001:10001
WORKDIR /tmp
# 기본 이미지의 entrypoint(DB server 기동)를 쓰지 않는다.
ENTRYPOINT ["/usr/local/bin/backup.sh"]
CMD []
