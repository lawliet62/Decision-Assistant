package com.decisionassistant.guidance;

/** 원격 응답 본문을 오류 메시지로 노출하지 않는다. */
public class WebEvidenceException extends RuntimeException {
    public enum Reason { RESPONSE_INVALID, REJECTED }
    private final Reason reason;

    public WebEvidenceException(Reason reason) {
        super(reason == Reason.RESPONSE_INVALID ? "웹 수집 응답 형식이 올바르지 않습니다." : "웹 수집 요청이 거절되었습니다.");
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
