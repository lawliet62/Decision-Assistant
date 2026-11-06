ALTER TABLE decisions ADD COLUMN comparison_state jsonb;
ALTER TABLE resolutions ADD COLUMN comparison_snapshot jsonb;
