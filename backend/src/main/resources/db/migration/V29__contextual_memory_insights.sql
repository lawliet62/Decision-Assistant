CREATE TABLE memory_insights (
    id uuid PRIMARY KEY,
    decision_id uuid NOT NULL REFERENCES decisions(id) ON DELETE CASCADE,
    source_decision_id uuid NOT NULL REFERENCES decisions(id) ON DELETE CASCADE,
    resolution_id uuid NOT NULL REFERENCES resolutions(id) ON DELETE CASCADE,
    input_hash varchar(64) NOT NULL,
    result jsonb NOT NULL,
    provider varchar(100) NOT NULL,
    model varchar(200) NOT NULL,
    prompt_version varchar(100) NOT NULL,
    schema_version varchar(100) NOT NULL,
    input_tokens bigint NOT NULL,
    output_tokens bigint NOT NULL,
    latency_ms bigint NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE(decision_id, resolution_id, input_hash)
);
