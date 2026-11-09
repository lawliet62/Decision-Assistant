-- 사용자가 승인한 영구 삭제 정책: 원본 결정의 성향 기억도 함께 삭제한다.
alter table preference_memories drop constraint preference_memories_source_decision_id_fkey;
alter table preference_memories add constraint preference_memories_source_decision_id_fkey
    foreign key (source_decision_id) references decisions(id) on delete cascade;
create index ix_decisions_owner_status_updated on decisions(owner_user_id, status, updated_at desc, id);
create index ix_preferences_source_decision on preference_memories(source_decision_id);
