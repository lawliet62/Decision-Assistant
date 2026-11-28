package com.decisionassistant.guidance;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;

/** 명시적으로 켠 개발 조사만 수행한다. 월 5개 슬롯은 실패해도 다시 소비하지 않는다. */
@Component @Profile("local") @org.springframework.context.annotation.Conditional(com.decisionassistant.job.JobMode.Inactive.class)
@ConditionalOnProperty(name="decision-assistant.catalog.research-enabled",havingValue="true")
public class CatalogResearchRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc; private final String key; private final String task; private final int slot;
    public CatalogResearchRunner(JdbcTemplate jdbc,@Value("${spring.ai.openai.api-key}") String key,
            @Value("${decision-assistant.catalog.research-task:SAMSUNG}") String task){this(jdbc,key,task,1);}
    @org.springframework.beans.factory.annotation.Autowired
    public CatalogResearchRunner(JdbcTemplate jdbc,@Value("${spring.ai.openai.api-key}") String key,
            @Value("${decision-assistant.catalog.research-task:SAMSUNG}") String task,
            @Value("${decision-assistant.catalog.research-slot:1}") int slot){this.jdbc=jdbc;this.key=key;this.task=task;this.slot=slot;}
    @Override public void run(ApplicationArguments args) {
        var selected=OpenAiCatalogResearch.Task.valueOf(task);
        if(slot<1 || slot>5) throw new IllegalArgumentException("RESEARCH_SLOT_OUT_OF_RANGE");
        var month=YearMonth.now(ZoneOffset.UTC).toString();
        int reserved=jdbc.update("""
                insert into catalog_research_attempts
                (month,task,slot,attempted_at,outcome,provider,model,prompt_version,max_tool_calls,max_output_tokens)
                values (?,?,?,now(),'ATTEMPTED','openai',?,?,3,4000) on conflict do nothing
                """,month,selected.name(),slot,OpenAiCatalogResearch.MODEL,OpenAiCatalogResearch.PROMPT_VERSION);
        if(reserved==0) return;
        try {
            var proposal=new OpenAiCatalogResearch(key).research(selected);
            jdbc.update("update catalog_research_attempts set outcome='PROPOSAL_UNREVIEWED',input_tokens=?,output_tokens=?,searches=?,proposal=?,sources=? where month=? and slot=?",
                    proposal.inputTokens(),proposal.outputTokens(),proposal.searches(),proposal.text(),String.join("\n",proposal.sources()),month,slot);
        } catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            var failure=e instanceof OpenAiCatalogResearch.ResearchFailure f ? f : null;
            jdbc.update("update catalog_research_attempts set outcome='FAILED_OR_USAGE_UNKNOWN',failure_reason=?,input_tokens=?,output_tokens=? where month=? and slot=?",
                    failure==null?"INTERNAL_OR_TRANSPORT":failure.getMessage(),failure==null?null:failure.inputTokens,
                    failure==null?null:failure.outputTokens,month,slot);
        }
    }
}
