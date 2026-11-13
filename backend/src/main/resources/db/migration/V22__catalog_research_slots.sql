ALTER TABLE catalog_research_attempts ADD COLUMN slot smallint;
UPDATE catalog_research_attempts SET slot = CASE task
    WHEN 'SAMSUNG' THEN 1 WHEN 'SONY' THEN 2 WHEN 'JBL' THEN 3 WHEN 'QCY' THEN 4 WHEN 'ANKER' THEN 5 END;
ALTER TABLE catalog_research_attempts ALTER COLUMN slot SET NOT NULL;
ALTER TABLE catalog_research_attempts DROP CONSTRAINT catalog_research_attempts_pkey;
ALTER TABLE catalog_research_attempts ADD PRIMARY KEY (month,slot);
ALTER TABLE catalog_research_attempts ADD CONSTRAINT catalog_research_slot_range CHECK (slot BETWEEN 1 AND 5);
