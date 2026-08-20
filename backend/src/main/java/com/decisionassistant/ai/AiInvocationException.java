package com.decisionassistant.ai;

public class AiInvocationException extends RuntimeException {
    public AiInvocationException(String message) {
        super(message);
    }

    public AiInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
