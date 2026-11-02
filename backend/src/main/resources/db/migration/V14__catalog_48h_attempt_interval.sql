update catalog_products
set next_attempt_at = greatest(next_attempt_at, last_attempt_at + interval '48 hours')
where last_attempt_at is not null;

-- 기존 관측에는 판매가 종료일이 보존되지 않아 72시간 유효성을 소급 검증할 수 없다.
update catalog_products set price_status = 'UNVERIFIED' where price_status = 'VERIFIED';
