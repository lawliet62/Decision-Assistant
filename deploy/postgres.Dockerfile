FROM postgres:17-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends python3 ca-certificates && rm -rf /var/lib/apt/lists/*
COPY deploy/pg_hba.conf /etc/postgresql/pg_hba.conf
COPY deploy/init-database.py /opt/init-database.py
COPY --chmod=755 deploy/init-database.sh /docker-entrypoint-initdb.d/10-application.sh
