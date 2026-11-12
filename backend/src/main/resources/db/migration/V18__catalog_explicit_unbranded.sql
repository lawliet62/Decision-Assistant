-- 공백은 미입력이다. 브랜드 없음이 확인된 제품만 명시적으로 '무브랜드'를 저장한다.
-- 기존 공백을 무브랜드로 추정하지 않는다. 남아 있으면 검토·수정 후 migration을 다시 실행한다.
ALTER TABLE catalog_products ALTER COLUMN brand_name DROP DEFAULT;
ALTER TABLE catalog_products DROP CONSTRAINT catalog_products_known_brand_required;
ALTER TABLE catalog_products ADD CONSTRAINT catalog_products_brand_not_blank
    CHECK (brand_name ~ '[^[:space:]]');
ALTER TABLE catalog_products ADD CONSTRAINT catalog_products_unbranded_verified
    CHECK (brand_name <> '무브랜드' OR catalog_known_brand(id, name, expected_identity) = '');
COMMENT ON COLUMN catalog_products.brand_name IS '카탈로그 관리용 대표 브랜드. soundcore는 Anker로 통일한다. 브랜드 없음이 확인된 제품은 무브랜드를 명시하며 공백은 오류다.';
