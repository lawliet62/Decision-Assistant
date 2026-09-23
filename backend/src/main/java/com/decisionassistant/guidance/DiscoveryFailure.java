package com.decisionassistant.guidance;

import com.decisionassistant.lookup.LookupException;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import tools.jackson.core.JacksonException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.concurrent.TimeoutException;

/** 저장·화면에는 고정 문구, 운영 로그에는 단계·코드·예외 종류만 사용한다. */
record DiscoveryFailure(Stage stage, Code code) {
    enum Stage { CONFIGURATION, SEARCH, COLLECTION, AI, VALIDATION, PERSISTENCE }
    enum Code {
        CONFIGURATION_UNAVAILABLE("추천 서비스 설정을 확인할 수 없습니다. 제품을 직접 추가하거나 서비스 상태를 확인하세요."),
        SEARCH_MONTHLY_LIMIT("이번 달 제품 검색 한도에 도달했습니다. 제품을 직접 추가하세요."),
        SEARCH_BUSY("제품 검색 요청이 몰려 있습니다. 잠시 후 다시 시도하거나 제품을 직접 추가하세요."),
        SEARCH_TIMEOUT("제품 검색 응답이 늦어 중단했습니다. 잠시 후 다시 시도하거나 제품을 직접 추가하세요."),
        SEARCH_FAILED("제품 검색 서비스 요청에 실패했습니다. 잠시 후 다시 시도하거나 제품을 직접 추가하세요."),
        SEARCH_NO_RESULTS("검색에서 공개 제품 페이지를 찾지 못했습니다. 조건을 바꾸거나 제품을 직접 추가하세요."),
        COLLECTION_RESPONSE_INVALID("공개 페이지 수집 응답을 처리하지 못했습니다. 서비스 점검이 필요합니다. 제품을 직접 추가할 수 있습니다."),
        COLLECTION_TIMEOUT("공개 페이지 수집 응답이 늦어 중단했습니다. 잠시 후 다시 시도하거나 제품을 직접 추가하세요."),
        COLLECTION_FAILED("공개 페이지를 수집하지 못했습니다. 잠시 후 다시 시도하거나 제품을 직접 추가하세요."),
        COLLECTION_EMPTY("수집한 공개 페이지에 비교할 내용이 없습니다. 조건을 바꾸거나 제품을 직접 추가하세요."),
        AI_TIMEOUT("AI가 제품 정보를 정리하는 데 시간이 초과되었습니다. 다시 시도하거나 제품을 직접 추가하세요."),
        AI_RESPONSE_INVALID("AI 응답을 제품 정보로 변환하지 못했습니다. 다시 시도하거나 제품을 직접 추가하세요."),
        AI_FAILED("AI 제품 정보 정리에 실패했습니다. 잠시 후 다시 시도하거나 제품을 직접 추가하세요."),
        EVIDENCE_INSUFFICIENT("제품을 확인할 근거가 부족합니다. 조건을 바꾸거나 직접 추가하세요."),
        PERSISTENCE_FAILED("추천 결과를 저장하지 못했습니다. 잠시 후 상태를 확인하고 다시 시도하세요.");

        private final String message;
        Code(String message) { this.message = message; }
        String message() { return message; }
    }

    String message() { return code.message(); }

    static DiscoveryFailure classify(Stage stage, Throwable error) {
        var chain = causes(error);
        var timeout = chain.stream().anyMatch(item -> item instanceof TimeoutException || item instanceof HttpTimeoutException || item instanceof SocketTimeoutException);
        var invalid = chain.stream().anyMatch(item -> item instanceof HttpMessageConversionException || item instanceof JacksonException
                || item instanceof UnknownContentTypeException
                || item instanceof WebEvidenceException web && web.reason() == WebEvidenceException.Reason.RESPONSE_INVALID);
        var code = switch (stage) {
            case CONFIGURATION -> Code.CONFIGURATION_UNAVAILABLE;
            case SEARCH -> chain.stream().filter(LookupException.class::isInstance).map(LookupException.class::cast)
                    .findFirst().map(item -> switch (item.code()) {
                        case "SEARCH_MONTHLY_LIMIT" -> Code.SEARCH_MONTHLY_LIMIT;
                        case "SEARCH_BUSY", "SEARCH_RATE_LIMIT" -> Code.SEARCH_BUSY;
                        case "SEARCH_TIMEOUT" -> Code.SEARCH_TIMEOUT;
                        default -> Code.SEARCH_FAILED;
                    }).orElse(timeout ? Code.SEARCH_TIMEOUT : Code.SEARCH_FAILED);
            case COLLECTION -> invalid ? Code.COLLECTION_RESPONSE_INVALID : timeout ? Code.COLLECTION_TIMEOUT : Code.COLLECTION_FAILED;
            case AI -> invalid ? Code.AI_RESPONSE_INVALID : timeout ? Code.AI_TIMEOUT : Code.AI_FAILED;
            case VALIDATION -> Code.EVIDENCE_INSUFFICIENT;
            case PERSISTENCE -> Code.PERSISTENCE_FAILED;
        };
        return new DiscoveryFailure(stage, code);
    }

    static String rootCauseType(Throwable error) {
        var chain = causes(error);
        return chain.isEmpty() ? "none" : chain.getLast().getClass().getSimpleName();
    }

    static Integer httpStatus(Throwable error) {
        return causes(error).stream().filter(RestClientResponseException.class::isInstance)
                .map(RestClientResponseException.class::cast).map(item -> item.getStatusCode().value()).findFirst().orElse(null);
    }

    private static List<Throwable> causes(Throwable error) {
        var result = new ArrayList<Throwable>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var current = error; current != null && seen.add(current); current = current.getCause()) result.add(current);
        return result;
    }
}
