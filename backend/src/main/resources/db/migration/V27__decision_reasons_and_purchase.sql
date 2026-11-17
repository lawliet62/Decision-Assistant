ALTER TABLE resolutions ADD COLUMN reason_ids jsonb NOT NULL DEFAULT '[]';
ALTER TABLE resolutions ADD COLUMN primary_reason_id varchar(40);
ALTER TABLE resolutions ADD COLUMN purchase_status varchar(30) NOT NULL DEFAULT 'UNRECORDED';
ALTER TABLE resolutions ADD COLUMN purchase_recorded_at timestamptz;
ALTER TABLE decisions ADD COLUMN revision_draft jsonb;
