package com.decisionassistant;
import com.decisionassistant.guidance.CatalogResearchRunner;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@Tag("integration") @Testcontainers @ActiveProfiles("test")
@SpringBootTest(properties="decision-assistant.catalog.enabled=false")
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogResearchIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired JdbcTemplate jdbc;
 @Test void reservesBeforeRequestAndDoesNotRepeatUncertainFailures() {
  var runner=new CatalogResearchRunner(jdbc,"test","SAMSUNG");runner.run(null);runner.run(null);
  assertThat(jdbc.queryForObject("select count(*) from catalog_research_attempts",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select outcome from catalog_research_attempts",String.class)).isEqualTo("FAILED_OR_USAGE_UNKNOWN");
  assertThat(jdbc.queryForObject("select count(*) from catalog_products",Integer.class)).isZero();
  assertThat(jdbc.queryForObject("select model from catalog_research_attempts",String.class)).isEqualTo("gpt-5.4-mini-2026-03-17");
  assertThat(jdbc.queryForObject("select max_tool_calls from catalog_research_attempts",Integer.class)).isEqualTo(3);
  assertThatThrownBy(()->new CatalogResearchRunner(jdbc,"test","ARBITRARY").run(null)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new CatalogResearchRunner(jdbc,"test","SONY",6).run(null)).isInstanceOf(IllegalArgumentException.class);
  new CatalogResearchRunner(jdbc,"test","SONY",1).run(null);
  assertThat(jdbc.queryForObject("select count(*) from catalog_research_attempts",Integer.class)).isEqualTo(1);
  new CatalogResearchRunner(jdbc,"test","SONY",2).run(null);
  assertThat(jdbc.queryForObject("select count(*) from catalog_research_attempts",Integer.class)).isEqualTo(2);
 }
}
