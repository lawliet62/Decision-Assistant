create table structure_runs (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    decision_version bigint not null,
    input_snapshot jsonb not null,
    status varchar(30) not null,
    result jsonb,
    error text,
    provider varchar(100),
    model varchar(200),
    prompt_version varchar(50),
    schema_version varchar(50),
    latency_ms bigint,
    input_tokens bigint,
    output_tokens bigint,
    created_at timestamptz not null
);

create index ix_structure_runs_decision on structure_runs(decision_id, created_at desc);
