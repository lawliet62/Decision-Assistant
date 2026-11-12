package com.decisionassistant;
import com.decisionassistant.guidance.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
@Tag("integration") @Testcontainers @ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
@SpringBootTest(properties={"decision-assistant.catalog.enabled=false","decision-assistant.catalog.refresh-enabled=false"})
class QcyDirectPriceIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired CatalogProductRepository products; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
 @BeforeEach void reset(){jdbc.update("delete from catalog_observations");jdbc.update("delete from catalog_products");jdbc.update("delete from catalog_monthly_usage");}
 @Test void failureReservesOnceWithoutFirecrawlOrUnapprovedProductRequests() throws Exception {
  var direct=mock(QcyCatalogPriceSource.class); var firecrawl=mock(FirecrawlWebEvidenceProvider.class);
  var other=new CatalogProduct("other"); other.register(1,"Other","https://qcystore.co.kr/product/detail.html?product_no=990","qcy:other",List.of(),true, "Test", CatalogProduct.BrandStatus.KNOWN);products.saveAndFlush(other);
  var ct06=new CatalogProduct("qcy-ct06");ct06.register(1,"CT06",QcyCatalogPriceSource.URL,"qcy:ct06",List.of(),true, "Test", CatalogProduct.BrandStatus.KNOWN);products.saveAndFlush(ct06);
  when(direct.fetch(QcyCatalogPriceSource.URL)).thenThrow(new IllegalStateException("SOURCE_UNAVAILABLE"));
  var off=new CatalogRefreshWorker(new CatalogProperties(true,true,"",700),new WebProperties(false,"","",Duration.ofSeconds(15)),firecrawl,products,jdbc,manager,direct,false);
  off.refreshNext();verifyNoInteractions(direct,firecrawl);
  var worker=new CatalogRefreshWorker(new CatalogProperties(true,true,"",700),new WebProperties(false,"","",Duration.ofSeconds(15)),firecrawl,products,jdbc,manager,direct,true);
  worker.refreshNext();worker.refreshNext();
  verify(direct,times(1)).fetch(QcyCatalogPriceSource.URL); verifyNoInteractions(firecrawl);
  assertThat(jdbc.queryForObject("select outcome from catalog_observations",String.class)).isEqualTo("COLLECTION_FAILED");
  assertThat(jdbc.queryForObject("select calls from catalog_monthly_usage",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from catalog_products where id='qcy-ct06' and next_attempt_at >= last_attempt_at + interval '48 hours'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from catalog_products where id='other' and last_attempt_at is null",Integer.class)).isEqualTo(1);
 }
 @Test void sourceRejectsEveryNonPilotAddressBeforeNetwork() {
  var source=new QcyCatalogPriceSource();
  for(var url:List.of("http://qcystore.co.kr/product/detail.html?product_no=994",QcyCatalogPriceSource.URL+"&other=1","https://evil.example/","https://qcystore.co.kr/product/detail.html?product_no=990"))
   org.assertj.core.api.Assertions.assertThatThrownBy(()->source.fetch(url)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void invalidPageCannotRefreshPriceAndZeroBudgetMakesNoRequest() throws Exception {
  var direct=mock(QcyCatalogPriceSource.class);var firecrawl=mock(FirecrawlWebEvidenceProvider.class);
  var ct06=new CatalogProduct("qcy-ct06");ct06.register(1,"CT06",QcyCatalogPriceSource.URL,"qcy:ct06",List.of(),true, "Test", CatalogProduct.BrandStatus.KNOWN);products.saveAndFlush(ct06);
  var settings=new WebProperties(false,"","",Duration.ofSeconds(15));
  new CatalogRefreshWorker(new CatalogProperties(true,true,"",0),settings,firecrawl,products,jdbc,manager,direct,true).refreshNext();
  verifyNoInteractions(direct,firecrawl);
  when(direct.fetch(QcyCatalogPriceSource.URL)).thenReturn(new WebEvidenceProvider.Page("","","<html><h1>CT06</h1></html>",QcyCatalogPriceSource.URL));
  new CatalogRefreshWorker(new CatalogProperties(true,true,"",700),settings,firecrawl,products,jdbc,manager,direct,true).refreshNext();
  assertThat(products.findById("qcy-ct06").orElseThrow().usableOffer()).isNull();
  assertThat(jdbc.queryForObject("select outcome from catalog_observations",String.class)).isNotEqualTo("VERIFIED");
  verifyNoInteractions(firecrawl);
 }
}
