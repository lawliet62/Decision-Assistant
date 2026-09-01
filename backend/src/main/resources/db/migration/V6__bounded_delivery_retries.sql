alter table evidence_imports
    add column attempt_count integer not null default 0;

alter table outcome_reminders
    add column attempt_count integer not null default 0;
