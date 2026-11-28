-- 복원 시험용 조회(deploy/cloudrun/README.md의 복원 시험). 읽기만 한다.
-- Flyway 버전과 public schema의 table별 행 수를 한 줄씩 출력해 두 database의 결과를 diff로 비교한다.
select 'flyway ' || max(version::int) from flyway_schema_history where success;
select format('select %L || '' '' || count(*) from %I.%I', table_name, table_schema, table_name)
  from information_schema.tables
  where table_schema = 'public' and table_type = 'BASE TABLE'
  order by table_name \gexec
