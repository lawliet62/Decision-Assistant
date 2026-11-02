package com.decisionassistant.guidance;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/** 운영자가 검토해 배포한 manifest만 읽는다. 사용자 입력이나 외부 모델 출력은 등록 경로가 아니다. */
@Component
public class CatalogImporter implements ApplicationRunner {
    public record Entry(String id, int revision, String name, String purchaseUrl, String expectedIdentity,
                        List<CatalogProduct.Fact> facts, boolean active) {}
    private final CatalogProperties config;
    private final CatalogProductRepository products;
    private final ResourceLoader resources;
    public CatalogImporter(CatalogProperties config, CatalogProductRepository products, ResourceLoader resources) {
        this.config = config; this.products = products; this.resources = resources;
    }
    @Override @Transactional public void run(ApplicationArguments args) throws Exception {
        if (!config.enabled() || config.manifest() == null || config.manifest().isBlank()) return;
        if (!config.manifest().startsWith("classpath:") && !config.manifest().startsWith("file:"))
            throw new IllegalArgumentException("카탈로그는 검토된 로컬 manifest만 허용합니다.");
        try (var stream = resources.getResource(config.manifest()).getInputStream()) {
            var entries = new ObjectMapper().readValue(stream, Entry[].class);
            var ids = new HashSet<String>(); var urls = new HashSet<String>(); var identities = new HashSet<String>();
            for (var entry : entries) {
                validate(entry, Instant.now());
                if (!ids.add(entry.id()) || !urls.add(entry.purchaseUrl()) || !identities.add(entry.expectedIdentity())) throw new IllegalArgumentException("중복 카탈로그 항목입니다.");
                var product = products.findById(entry.id()).orElseGet(() -> new CatalogProduct(entry.id()));
                product.register(entry.revision(), entry.name(), entry.purchaseUrl(), entry.expectedIdentity(), entry.facts(), entry.active());
                products.save(product);
            }
            if (products.findByActiveTrueOrderByIdAsc().size() > 30) throw new IllegalArgumentException("활성 카탈로그는 최대 30개입니다.");
        }
    }
    static void validate(Entry e, Instant now) {
        if (e.id() == null || !e.id().matches("[a-z0-9-]{1,100}") || e.revision() < 1 || e.name() == null || e.name().isBlank()
                || e.name().length() > 300 || e.expectedIdentity() == null || e.expectedIdentity().isBlank() || e.expectedIdentity().length() > 500
                || OfficialStorePolicy.store(e.purchaseUrl()).isEmpty() || e.facts() == null || e.facts().size() > 20)
            throw new IllegalArgumentException("카탈로그 제품과 공식 판매 URL을 확인하세요.");
        for (var item : e.facts()) {
            var f = item.evidence();
            if (item.cycle() == null || f == null || f.name() == null || "가격".equals(f.name()) || f.value() == null || f.value().isBlank()
                    || f.quote() == null || f.quote().isBlank() || f.quote().length() > 600 || f.fetchedAt() == null || f.fetchedAt().isAfter(now)
                    || !"UNREVIEWED".equals(f.verificationStatus())) throw new IllegalArgumentException("사양의 인용·시각·검토 상태가 필요합니다.");
            var uri = URI.create(f.sourceUrl());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() != -1
                    || !(Set.of("www.qcy.com", "qcystore.co.kr", "www.samsung.com", "www.apple.com", "support.apple.com", "www.lge.co.kr").contains(uri.getHost())
                         || AnkerP31iSpecificationVerifier.URL.equals(f.sourceUrl()) && AnkerProductVerifier.URL.equals(e.purchaseUrl())
                         || AnkerProductVerifier.AERO_URL.equals(f.sourceUrl()) && AnkerProductVerifier.AERO_URL.equals(e.purchaseUrl())
                         || AnkerLibertySpecificationVerifier.URL.equals(f.sourceUrl()) && AnkerProductVerifier.LIBERTY_URL.equals(e.purchaseUrl())
                         || AnkerLibertyProSpecificationVerifier.URL.equals(f.sourceUrl()) && AnkerProductVerifier.PRO_URL.equals(e.purchaseUrl())
                         || AnkerP41iVerifier.SPEC_URL.equals(f.sourceUrl()) && AnkerP41iVerifier.URL.equals(e.purchaseUrl())
                         || AudioTechnicaSpecificationSource.URL.equals(f.sourceUrl()) && AudioTechnicaProductVerifier.URL.equals(e.purchaseUrl())
                         || AudioTechnicaProductVerifier.AC5_URL.equals(f.sourceUrl()) && AudioTechnicaProductVerifier.AC5_URL.equals(e.purchaseUrl())
                         || Set.of("marshall-kr", "sennheiser-kr").contains(OfficialStorePolicy.store(f.sourceUrl()).map(OfficialStorePolicy.Store::id).orElse(""))))
                throw new IllegalArgumentException("등록된 공식 출처만 사양 근거로 허용합니다.");
        }
    }
}
