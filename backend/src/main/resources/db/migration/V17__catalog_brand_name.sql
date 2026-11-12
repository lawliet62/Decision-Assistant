-- 기존 제품의 식별 정보로 관리용 대표 브랜드를 채운다. 가격·사양·revision은 변경하지 않는다.
CREATE FUNCTION catalog_known_brand(product_id text, product_name text, identity text)
RETURNS text LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT CASE
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(qcy|큐씨와이)([^a-z0-9]|$)' THEN 'QCY'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(anker|soundcore|앤커|사운드코어)([^a-z0-9]|$)' THEN 'Anker'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(apple|airpods|애플)([^a-z0-9]|$)' THEN 'Apple'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(audio-technica|오디오테크니카)([^a-z0-9]|$)' THEN 'Audio-Technica'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(bose|보스)([^a-z0-9]|$)' THEN 'BOSE'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(marshall|마샬)([^a-z0-9]|$)' THEN 'Marshall'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(sennheiser|젠하이저)([^a-z0-9]|$)' THEN 'Sennheiser'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(sony|소니)([^a-z0-9]|$)' THEN 'Sony'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(samsung|삼성)([^a-z0-9]|$)' THEN 'Samsung'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(lg|엘지)([^a-z0-9]|$)' THEN 'LG'
        ELSE ''
    END
$$;

ALTER TABLE catalog_products ADD COLUMN brand_name varchar(100) NOT NULL DEFAULT '';

UPDATE catalog_products
SET brand_name = catalog_known_brand(id, name, expected_identity), version = version + 1
WHERE catalog_known_brand(id, name, expected_identity) <> '';

ALTER TABLE catalog_products ADD CONSTRAINT catalog_products_known_brand_required
    CHECK (brand_name ~ '[^[:space:]]' OR catalog_known_brand(id, name, expected_identity) = '');
COMMENT ON COLUMN catalog_products.brand_name IS '카탈로그 관리용 대표 브랜드. soundcore는 Anker로 통일하며 노브랜드는 빈 문자열을 허용한다.';
