do $$
begin
    if exists (select 1 from decisions) then
        raise exception 'V3 requires the pre-authentication local database to be reset before migration';
    end if;
end $$;

create table app_users (
    id uuid primary key,
    email varchar(320) not null,
    email_normalized varchar(320) not null unique,
    display_name varchar(200),
    created_at timestamptz not null,
    last_login_at timestamptz not null
);

create table external_identities (
    id uuid primary key,
    user_id uuid not null references app_users(id) on delete cascade,
    issuer varchar(500) not null,
    subject varchar(500) not null,
    created_at timestamptz not null,
    last_login_at timestamptz not null,
    constraint uq_external_identity unique (issuer, subject)
);

create index ix_external_identities_user on external_identities(user_id);

alter table decisions
    add column owner_user_id uuid not null references app_users(id);

drop index ix_decisions_updated_at;
create index ix_decisions_owner_updated on decisions(owner_user_id, updated_at desc);

create table spring_session (
    primary_id char(36) not null,
    session_id char(36) not null,
    creation_time bigint not null,
    last_access_time bigint not null,
    max_inactive_interval integer not null,
    expiry_time bigint not null,
    principal_name varchar(100),
    constraint spring_session_pk primary key (primary_id)
);

create unique index spring_session_ix1 on spring_session(session_id);
create index spring_session_ix2 on spring_session(expiry_time);
create index spring_session_ix3 on spring_session(principal_name);

create table spring_session_attributes (
    session_primary_id char(36) not null,
    attribute_name varchar(200) not null,
    attribute_bytes bytea not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id)
        references spring_session(primary_id) on delete cascade
);
