create table decisions (
    id uuid primary key,
    version bigint not null default 0,
    title varchar(200) not null,
    template_key varchar(30) not null,
    template_version varchar(50) not null,
    status varchar(30) not null,
    context_intended_use varchar(1000),
    context_budget numeric(19, 4),
    context_currency varchar(3),
    context_notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table decision_options (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    name varchar(200) not null,
    description text,
    source_url varchar(2048),
    source_text text
);

create table criteria (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    name varchar(200) not null,
    kind varchar(30) not null,
    direction varchar(30) not null,
    weight numeric(12, 4) not null,
    worst_anchor numeric(19, 4),
    best_anchor numeric(19, 4),
    unit varchar(30),
    active boolean not null
);

create table option_values (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    option_id uuid not null,
    criterion_id uuid not null,
    numeric_value numeric(19, 4),
    rating integer,
    certainty varchar(20) not null,
    source_url varchar(2048),
    source_note text,
    constraint uq_option_criterion unique (option_id, criterion_id),
    constraint ck_rating_range check (rating is null or rating between 1 and 5)
);

create table decision_constraints (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    name varchar(200) not null,
    kind varchar(30) not null,
    criterion_id uuid,
    threshold numeric(19, 4),
    unit varchar(30),
    description text,
    active boolean not null
);

create table constraint_results (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    option_id uuid not null,
    constraint_id uuid not null,
    status varchar(20) not null,
    note text,
    constraint uq_option_constraint unique (option_id, constraint_id)
);

create table resolutions (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    kind varchar(20) not null,
    selected_option_id uuid,
    rationale text,
    created_at timestamptz not null
);

create table outcome_check_ins (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    satisfaction integer not null check (satisfaction between 1 and 5),
    expectation_status varchar(30) not null,
    notes text,
    created_at timestamptz not null
);

create table evaluations (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    decision_version bigint not null,
    input_snapshot jsonb not null,
    input_hash varchar(64) not null,
    algorithm_result jsonb not null,
    llm_status varchar(30) not null,
    llm_result jsonb,
    llm_error text,
    provider varchar(100),
    model varchar(200),
    prompt_version varchar(50),
    schema_version varchar(50),
    llm_latency_ms bigint,
    input_tokens bigint,
    output_tokens bigint,
    created_at timestamptz not null
);

create index ix_decisions_updated_at on decisions(updated_at desc);
create index ix_evaluations_decision on evaluations(decision_id, created_at desc);
create index ix_resolutions_decision on resolutions(decision_id, created_at desc);
create index ix_outcomes_decision on outcome_check_ins(decision_id, created_at desc);
