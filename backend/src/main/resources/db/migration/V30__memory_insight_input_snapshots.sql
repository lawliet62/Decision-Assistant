CREATE TABLE memory_insight_inputs (
    id uuid PRIMARY KEY,
    decision_id uuid NOT NULL REFERENCES decisions(id) ON DELETE CASCADE,
    source_decision_id uuid NOT NULL REFERENCES decisions(id) ON DELETE CASCADE,
    resolution_id uuid NOT NULL REFERENCES resolutions(id) ON DELETE CASCADE,
    input_hash varchar(64) NOT NULL,
    snapshot jsonb NOT NULL,
    created_at timestamptz NOT NULL
);
ALTER TABLE memory_insights ADD COLUMN input_id uuid REFERENCES memory_insight_inputs(id) ON DELETE CASCADE;
