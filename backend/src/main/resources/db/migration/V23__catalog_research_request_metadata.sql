-- 기존 세 조사에도 동일한 provider/model/prompt가 사용됐다. 당시 호출 상한은 평가 기록을 따른다.
ALTER TABLE catalog_research_attempts
    ADD COLUMN provider varchar(30) NOT NULL DEFAULT 'openai',
    ADD COLUMN model varchar(100) NOT NULL DEFAULT 'gpt-5.4-mini-2026-03-17',
    ADD COLUMN prompt_version varchar(100) NOT NULL DEFAULT 'catalog-official-research-v1',
    ADD COLUMN max_tool_calls integer,
    ADD COLUMN max_output_tokens integer;
