alter table decisions
    add column intent_text text,
    add column processing_status varchar(30) not null default 'READY',
    add column ai_consent boolean not null default false,
    add column web_consent boolean not null default false,
    add column consent_policy_version varchar(50);

alter table option_values
    add column origin varchar(30) not null default 'USER',
    add column verification_status varchar(30) not null default 'CONFIRMED';

alter table outcome_check_ins
    add column would_choose_again boolean,
    add column worked_well text,
    add column disappointed_by text;

create table evidence_imports (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    option_id uuid not null,
    url varchar(2048) not null,
    status varchar(30) not null,
    title varchar(500),
    content_hash varchar(64),
    excerpt text,
    provider varchar(100),
    error text,
    fetched_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_evidence_imports_pending on evidence_imports(status, created_at);
create index ix_evidence_imports_decision on evidence_imports(decision_id, created_at);

create table decision_questions (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    kind varchar(30) not null,
    target_key varchar(200) not null,
    prompt varchar(1000) not null,
    impact text,
    options_json jsonb,
    answer_json jsonb,
    status varchar(20) not null,
    priority integer not null,
    created_at timestamptz not null,
    answered_at timestamptz
);

create index ix_decision_questions_open on decision_questions(decision_id, status, priority);

create table candidate_suggestions (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    name varchar(300) not null,
    url varchar(2048) not null,
    reason text not null,
    status varchar(20) not null,
    provider varchar(100),
    created_at timestamptz not null,
    constraint uq_candidate_suggestion unique(decision_id, url)
);

create table preference_memories (
    id uuid primary key,
    owner_user_id uuid not null references app_users(id) on delete cascade,
    template_key varchar(30),
    criterion_name varchar(200),
    statement text not null,
    strength integer not null,
    status varchar(20) not null,
    confidence varchar(20) not null,
    source_decision_id uuid references decisions(id) on delete set null,
    source_outcome_id uuid references outcome_check_ins(id) on delete set null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index ix_preference_memories_owner on preference_memories(owner_user_id, status, template_key);

create table outcome_reminders (
    id uuid primary key,
    decision_id uuid not null references decisions(id) on delete cascade,
    due_at timestamptz not null,
    status varchar(20) not null,
    channel varchar(20) not null,
    provider_message_id varchar(200),
    last_error text,
    sent_at timestamptz,
    created_at timestamptz not null
);

create index ix_outcome_reminders_due on outcome_reminders(status, due_at);
create index ix_outcome_reminders_decision on outcome_reminders(decision_id, due_at);
