CREATE TABLE catalog_products (
    id varchar(100) PRIMARY KEY,
    revision integer NOT NULL,
    name varchar(300) NOT NULL,
    purchase_url varchar(2048) NOT NULL UNIQUE,
    expected_identity varchar(500) NOT NULL UNIQUE,
    facts jsonb NOT NULL,
    active boolean NOT NULL,
    offer jsonb,
    price_quote text,
    price_status varchar(40) NOT NULL,
    last_attempt_at timestamptz,
    next_attempt_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE catalog_observations (
    id uuid PRIMARY KEY,
    product_id varchar(100) NOT NULL REFERENCES catalog_products(id),
    attempted_at timestamptz NOT NULL,
    completed_at timestamptz,
    outcome varchar(50) NOT NULL,
    price numeric,
    currency varchar(3)
);
CREATE INDEX catalog_observations_attempted ON catalog_observations(attempted_at);
CREATE TABLE catalog_monthly_usage (
    month varchar(7) PRIMARY KEY,
    calls integer NOT NULL CHECK (calls >= 0)
);
