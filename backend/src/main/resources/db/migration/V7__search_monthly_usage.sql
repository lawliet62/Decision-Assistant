-- Anonymous operational counter only; no search queries, results or user identifiers.
CREATE TABLE search_monthly_usage (
    month date PRIMARY KEY,
    calls integer NOT NULL CHECK (calls >= 0 AND calls <= 1000)
);
