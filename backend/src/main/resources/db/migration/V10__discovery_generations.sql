ALTER TABLE product_discoveries ADD COLUMN generation integer NOT NULL DEFAULT 0;
ALTER TABLE product_discoveries ADD COLUMN displayed_generation integer NOT NULL DEFAULT 0;
ALTER TABLE product_discoveries ADD COLUMN history jsonb NOT NULL DEFAULT '[]';
ALTER TABLE candidate_suggestions ADD COLUMN generation integer NOT NULL DEFAULT 0;
CREATE INDEX candidate_discovery_generation ON candidate_suggestions(decision_id, generation) WHERE discovered;
-- 기존 후보는 최초 묶음(0)으로 읽고 내용·승인 상태·공식 여부를 수정하지 않는다.
