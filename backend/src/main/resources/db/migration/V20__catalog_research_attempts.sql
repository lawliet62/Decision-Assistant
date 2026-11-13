CREATE TABLE catalog_research_attempts (
    month varchar(7) NOT NULL,
    task varchar(30) NOT NULL,
    attempted_at timestamptz NOT NULL,
    outcome varchar(40) NOT NULL,
    input_tokens bigint,
    output_tokens bigint,
    searches integer,
    proposal text,
    sources text,
    PRIMARY KEY(month, task)
);
