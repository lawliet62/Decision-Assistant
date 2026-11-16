-- 기존 상태값과 고정본은 재해석하지 않는다. 과거 회고·알림의 불명확한 연결은 NULL로 보존한다.
alter table decisions add column edit_mode varchar(30);
alter table decisions add column current_resolution_id uuid;
alter table decisions add column revision_base_id uuid;
alter table resolutions add column edit_mode varchar(30);
alter table resolutions add column previous_resolution_id uuid;
alter table resolutions add column superseded_at timestamptz;
alter table preference_memories add column superseded_at timestamptz;
alter table outcome_check_ins add column resolution_id uuid references resolutions(id) on delete cascade;
alter table outcome_reminders add column resolution_id uuid references resolutions(id) on delete cascade;
create index ix_outcomes_resolution on outcome_check_ins(resolution_id);
create index ix_reminders_resolution on outcome_reminders(resolution_id);
