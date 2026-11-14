-- 상태와 이름을 분리한다. 과거 예약값 '무브랜드'만 브랜드 없음 상태로 이전한다.
ALTER TABLE catalog_products DROP CONSTRAINT catalog_products_brand_not_blank;
ALTER TABLE catalog_products DROP CONSTRAINT catalog_products_unbranded_verified;
ALTER TABLE catalog_products ALTER COLUMN brand_name DROP NOT NULL;
ALTER TABLE catalog_products ADD COLUMN brand_status varchar(20);
UPDATE catalog_products
SET brand_status = CASE WHEN brand_name = '무브랜드' THEN 'UNBRANDED' ELSE 'KNOWN' END,
    brand_name = CASE WHEN brand_name = '무브랜드' THEN NULL ELSE brand_name END,
    version = version + 1;
ALTER TABLE catalog_products ALTER COLUMN brand_status SET NOT NULL;
ALTER TABLE catalog_products ADD CONSTRAINT catalog_products_brand_state_valid CHECK (
    (brand_status = 'KNOWN' AND brand_name IS NOT NULL AND brand_name ~ '[^[:space:]]')
    OR (brand_status IN ('UNBRANDED', 'UNVERIFIED') AND brand_name IS NULL)
);
ALTER TABLE catalog_products ADD CONSTRAINT catalog_products_unbranded_consistent CHECK (
    brand_status <> 'UNBRANDED' OR catalog_known_brand(id, name, expected_identity) = ''
);
COMMENT ON COLUMN catalog_products.brand_name IS '관리용 대표 브랜드명. KNOWN만 값을 가지며 soundcore는 Anker로 통일한다.';
COMMENT ON COLUMN catalog_products.brand_status IS 'KNOWN: 이름 확인, UNBRANDED: 브랜드 없음 확인, UNVERIFIED: 확인 미완료. UNKNOWN을 브랜드 없음으로 추정하지 않는다.';
