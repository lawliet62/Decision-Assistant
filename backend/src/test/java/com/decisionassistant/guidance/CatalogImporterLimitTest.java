package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogImporterLimitTest {
    @TempDir Path directory;

    private CatalogImporter importer(int activeEntries, int inactiveEntries, int resultingActiveCount) throws Exception {
        var entries = new ArrayList<CatalogImporter.Entry>();
        for (int i = 0; i < activeEntries + inactiveEntries; i++) {
            entries.add(new CatalogImporter.Entry("product-" + i, 1, "Product " + i,
                    "https://qcystore.co.kr/product/buds/" + (1000 + i) + "/", "qcy:test-" + i,
                    List.of(), i < activeEntries, "QCY", CatalogProduct.BrandStatus.KNOWN));
        }
        var manifest = directory.resolve("catalog.json");
        new ObjectMapper().writeValue(manifest.toFile(), entries);
        var products = mock(CatalogProductRepository.class);
        when(products.findByActiveTrueOrderByIdAsc()).thenReturn(
                Collections.nCopies(resultingActiveCount, mock(CatalogProduct.class)));
        return new CatalogImporter(new CatalogProperties(true, false, manifest.toUri().toString(), 0),
                products, new DefaultResourceLoader());
    }

    @Test void permitsThirtyActiveProductsAlongsideInactiveHistory() throws Exception {
        importer(30, 2, 30).run(null);
    }

    @Test void rejectsMoreThanThirtyActiveProductsIncludingExistingDatabaseEntries() throws Exception {
        var importer = importer(30, 2, 31);
        assertThatThrownBy(() -> importer.run(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("활성 카탈로그는 최대 30개입니다.");
    }
}
