package com.decisionassistant.lookup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class SearchQuota {
    private final JdbcTemplate jdbc;
    private final SearchProperties properties;

    public SearchQuota(JdbcTemplate jdbc, SearchProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    // Commit before sending: an upstream timeout may still consume a billable call.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve() {
        reserveMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    }

    void reserveMonth(LocalDate month) {
        var updated = jdbc.query("""
                INSERT INTO search_monthly_usage (month, calls) VALUES (?, 1)
                ON CONFLICT (month) DO UPDATE SET calls = search_monthly_usage.calls + 1
                WHERE search_monthly_usage.calls < ? RETURNING calls
                """, (rs, row) -> rs.getInt(1), month, properties.monthlyLimit());
        if (updated.isEmpty()) throw new LookupException(429, "SEARCH_MONTHLY_LIMIT",
                "이번 달 검색 한도에 도달했습니다. URL 직접 추가는 계속 사용할 수 있습니다.");
    }
}
