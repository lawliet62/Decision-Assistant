CREATE TABLE catalog_spec_attempts (
    product_id varchar(100) PRIMARY KEY REFERENCES catalog_products(id),
    next_attempt_at timestamptz NOT NULL
);
