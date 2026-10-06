import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

export function summarize(rows) {
  const events = rows.flatMap(row => row.metrics.events);
  const countBy = (items, key) => items.reduce((counts, item) => {
    const value = item[key] ?? "UNKNOWN";
    counts[value] = (counts[value] ?? 0) + 1;
    return counts;
  }, {});
  const sum = field => rows.some(row => row.metrics[field] == null) ? null : rows.reduce((total, row) => total + row.metrics[field], 0);
  const timings = field => {
    const known = rows.map(row => row.metrics[field]).filter(Number.isFinite).sort((a, b) => a - b);
    const middle = Math.floor(known.length / 2);
    return { measured: known.length,
      median: known.length ? known.length % 2 ? known[middle] : (known[middle - 1] + known[middle]) / 2 : null,
      max: known.length ? known.at(-1) : null, over120Seconds: known.filter(value => value > 120000).length };
  };
  const funnel = rows.map(row => {
    const event = name => row.metrics.events.find(item => item.product_event === name);
    const search = event("product_lookup_results");
    const filter = event("discovery_search_filter");
    const collected = event("discovery_collection_results");
    const offers = event("discovery_offer_validation");
    return { caseId: row.caseId, decisionId: row.decisionId,
      status: row.discovery.status, emptyReason: row.discovery.emptyReason,
      returned: search ? Number(search.returned) : null,
      public: search ? Number(search.unique) : null,
      official: filter ? Number(filter.official) : search?.unique === "0" ? 0 : null,
      collected: collected ? Number(collected.collected) : row.metrics.collectionCalls === 0 ? 0 : null,
      verifiedOffers: offers ? Number(offers.eligible) : row.metrics.collectionCalls === 0 ? 0 : null,
      candidates: row.candidates.length,
      appRecommended: row.candidates.filter(candidate => candidate.recommendationGroup === "RECOMMENDED").length,
      executionMs: row.metrics.executionMs, queueMs: row.metrics.queueMs,
      searchCalls: row.metrics.searchCalls, collectionCalls: row.metrics.collectionCalls, aiCalls: row.metrics.aiCalls,
      inputTokens: row.metrics.inputTokens, outputTokens: row.metrics.outputTokens };
  });
  return {
    completed: rows.length, withCandidates: rows.filter(row => row.candidates.length > 0).length,
    // 앱 목록은 사람의 제품군·호환성·색상 중복 검토를 대신하지 않는다.
    withTwoAppRecommended: funnel.filter(row => row.appRecommended >= 2).length,
    statuses: countBy(rows.map(row => row.discovery), "status"),
    emptyReasons: countBy(rows.filter(row => row.discovery.emptyReason).map(row => row.discovery), "emptyReason"),
    searchCalls: sum("searchCalls"), collectionCalls: sum("collectionCalls"), aiCalls: sum("aiCalls"),
    knownInputTokens: rows.reduce((total, row) => total + (row.metrics.inputTokens ?? 0), 0),
    knownOutputTokens: rows.reduce((total, row) => total + (row.metrics.outputTokens ?? 0), 0),
    unknownUsageRuns: rows.filter(row => !row.metrics.usageKnown).length,
    incompleteInstrumentationRuns: rows.filter(row => row.metrics.coverageComplete === false).length,
    queueMs: timings("queueMs"), executionMs: timings("executionMs"),
    offerInspections: countBy(events.filter(event => event.product_event === "discovery_offer_inspection"), "outcome"),
    supplementaryDocuments: countBy(events.filter(event => event.product_event === "discovery_supplement_result"), "outcome"),
    failures: countBy(events.filter(event => ["discovery_failed", "discovery_page_failed", "discovery_supplement_failed"].includes(event.product_event)), "code"),
    fieldEvidence: countBy(events.filter(event => event.product_event === "discovery_field_evidence").map(event => ({key: `${event.field}:${event.outcome}`})), "key"),
    fieldValidation: countBy(events.filter(event => event.product_event === "discovery_field_validation").map(event => ({key: `${event.field}:${event.outcome}`})), "key"),
    requirementResults: countBy(events.filter(event => event.product_event === "discovery_requirement_check").map(event => ({key: `${event.field}:${event.status}:${event.reason}`})), "key"),
    funnel,
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv.length !== 3) throw new Error("사용법: node scripts/discovery-evaluation/summarize.mjs <results.jsonl>");
  const rows = readFileSync(process.argv[2], "utf8").split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
  console.log(JSON.stringify(summarize(rows), null, 2));
}
