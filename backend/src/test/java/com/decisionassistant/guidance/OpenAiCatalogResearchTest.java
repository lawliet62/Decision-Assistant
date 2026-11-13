package com.decisionassistant.guidance;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class OpenAiCatalogResearchTest {
 private static final JsonMapper JSON=JsonMapper.builder().build();
 private static final String RESPONSE="""
 {"status":"completed","usage":{"input_tokens":100,"output_tokens":50},"output":[
 {"type":"web_search_call","action":{"type":"search","sources":[{"url":"https://www.samsung.com/sec/buds/"}]}},
 {"type":"message","content":[{"type":"output_text","text":"모델 및 가격은 확인 필요","annotations":[]}]}]}
 """;
 @Test void proposalsRequireOfficialSourcesSearchAndUsage() {
  var p=OpenAiCatalogResearch.parse(OpenAiCatalogResearch.Task.SAMSUNG,JSON.readTree(RESPONSE));
  assertThat(p.sources()).containsExactly("https://www.samsung.com/sec/buds/");
  assertThat(p.searches()).isEqualTo(1);
  for(var bad:new String[]{RESPONSE.replace("completed","incomplete"),RESPONSE.replace("www.samsung.com","samsung.com.evil.example"),RESPONSE.replace("input_tokens","missing_tokens"),RESPONSE.replace("\"search\"","\"open_page\""),RESPONSE.replace(":100",":-1")})
   assertThatThrownBy(()->OpenAiCatalogResearch.parse(OpenAiCatalogResearch.Task.SAMSUNG,JSON.readTree(bad))).isInstanceOf(IllegalStateException.class);
 }
 @Test void requestKeepsCostControlsAndNoRetries() throws Exception {
  var request=OpenAiCatalogResearch.request(OpenAiCatalogResearch.Task.SAMSUNG);
  assertThat(request).containsEntry("max_tool_calls",3).containsEntry("max_output_tokens",4000).containsEntry("store",false);
  var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/responses",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(429,-1);exchange.close();});server.start();
  try {var adapter=new OpenAiCatalogResearch("fixture",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/responses"));
   assertThatThrownBy(()->adapter.research(OpenAiCatalogResearch.Task.SAMSUNG)).isInstanceOf(IllegalStateException.class);
   assertThat(calls.get()).isEqualTo(1);
  }finally{server.stop(0);}
 }
 @Test void jblResearchUsesConfirmedKoreanAndSupportDomains() {
  var request=OpenAiCatalogResearch.request(OpenAiCatalogResearch.Task.JBL);
  var domains=JSON.valueToTree(request).path("tools").path(0).path("filters").path("allowed_domains");
  assertThat(domains.toString()).isEqualTo("[\"kr.jbl.com\",\"support.jbl.com\",\"samsung.com\"]");
  var jbl=RESPONSE.replace("https://www.samsung.com/sec/buds/","https://kr.jbl.com/TOUR-PRO-3.html");
  assertThat(OpenAiCatalogResearch.parse(OpenAiCatalogResearch.Task.JBL,JSON.readTree(jbl)).sources())
    .containsExactly("https://kr.jbl.com/TOUR-PRO-3.html");
  for(var host:new String[]{"jbl.co.kr","kr.jbl.com.evil.example","other.jbl.com"}) {
   var outside=jbl.replace("kr.jbl.com",host);
   assertThatThrownBy(()->OpenAiCatalogResearch.parse(OpenAiCatalogResearch.Task.JBL,JSON.readTree(outside)))
     .hasMessage("RESEARCH_SOURCE_OUTSIDE_ALLOWLIST");
  }
 }
 @Test void incompleteResponsePreservesUsageAndSafeReason() throws Exception {
  var response=RESPONSE.replace("\"status\":\"completed\"", "\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}");
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/responses",exchange->{
   byte[] body=response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
   exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
  });server.start();
  try {
   var adapter=new OpenAiCatalogResearch("fixture",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/responses"));
   var failure=catchThrowableOfType(OpenAiCatalogResearch.ResearchFailure.class,()->adapter.research(OpenAiCatalogResearch.Task.SAMSUNG));
   assertThat(failure).hasMessage("RESEARCH_INCOMPLETE_max_output_tokens");
   assertThat(failure.inputTokens).isEqualTo(100L);
   assertThat(failure.outputTokens).isEqualTo(50L);
  }finally{server.stop(0);}
 }
}
