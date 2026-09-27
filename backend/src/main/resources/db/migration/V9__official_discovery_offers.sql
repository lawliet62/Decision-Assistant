ALTER TABLE product_discoveries ADD COLUMN execution_id uuid;
ALTER TABLE product_discoveries ADD COLUMN stage varchar(30);
ALTER TABLE product_discoveries ADD COLUMN policy_version varchar(50);
ALTER TABLE product_discoveries ADD COLUMN empty_reason varchar(50);
ALTER TABLE candidate_suggestions ADD COLUMN official_offer jsonb;
ALTER TABLE candidate_suggestions ADD COLUMN product_identity varchar(500);
-- 기존 추천·직접 추가 제품에는 공식 판매처 표시를 소급 부여하지 않는다.
