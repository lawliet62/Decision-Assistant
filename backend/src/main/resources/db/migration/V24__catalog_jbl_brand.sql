-- JBL 제품의 삼성닷컴 판매처 식별자를 제품 브랜드 Samsung으로 오인하지 않는다.
CREATE OR REPLACE FUNCTION catalog_known_brand(product_id text, product_name text, identity text)
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
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(jbl)([^a-z0-9]|$)' THEN 'JBL'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(samsung|삼성)([^a-z0-9]|$)' THEN 'Samsung'
        WHEN lower(product_id || ' ' || product_name || ' ' || identity) ~ '(^|[^a-z0-9])(lg|엘지)([^a-z0-9]|$)' THEN 'LG'
        ELSE ''
    END
$$;

UPDATE catalog_products
SET brand_name = 'JBL', brand_status = 'KNOWN', version = version + 1
WHERE catalog_known_brand(id, name, expected_identity) = 'JBL'
  AND (brand_name IS DISTINCT FROM 'JBL' OR brand_status <> 'KNOWN');
