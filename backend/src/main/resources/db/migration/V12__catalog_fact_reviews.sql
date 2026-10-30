ALTER TABLE catalog_products ADD COLUMN fact_reviews jsonb NOT NULL DEFAULT '{}'::jsonb;
