alter table decisions add column archived_at timestamptz;
alter table decisions add column deleted_at timestamptz;
alter table decisions add column personalization_enabled boolean not null default true;
create index ix_decisions_trash_expiry on decisions(deleted_at) where deleted_at is not null;
