CREATE TABLE product_discoveries (
    decision_id uuid PRIMARY KEY REFERENCES decisions(id) ON DELETE CASCADE,
    version bigint NOT NULL DEFAULT 0,
    conditions jsonb NOT NULL,
    status varchar(20) NOT NULL,
    error text,
    updated_at timestamptz NOT NULL,
    provider varchar(100), model varchar(200), input_tokens bigint, output_tokens bigint,
    latency_ms bigint, prompt_version varchar(50) NOT NULL, schema_version varchar(50) NOT NULL
);
CREATE INDEX product_discoveries_pending ON product_discoveries(updated_at) WHERE status = 'PENDING';
ALTER TABLE candidate_suggestions ADD COLUMN facts jsonb NOT NULL DEFAULT '[]';
ALTER TABLE candidate_suggestions ADD COLUMN option_id uuid REFERENCES decision_options(id) ON DELETE SET NULL;
ALTER TABLE candidate_suggestions ADD COLUMN discovered boolean NOT NULL DEFAULT false;
ALTER TABLE candidate_suggestions ADD COLUMN source_excerpt text;
ALTER TABLE candidate_suggestions ADD COLUMN fetched_at timestamptz;
-- 비교 글 한 페이지가 여러 모델의 근거가 될 수 있다. 기존 URL lookup의 중복 정책은 유지한다.
ALTER TABLE candidate_suggestions DROP CONSTRAINT uq_candidate_suggestion;
CREATE UNIQUE INDEX uq_legacy_candidate_url ON candidate_suggestions(decision_id, url) WHERE NOT discovered;
CREATE UNIQUE INDEX uq_discovered_candidate_name ON candidate_suggestions(decision_id, lower(name)) WHERE discovered;
