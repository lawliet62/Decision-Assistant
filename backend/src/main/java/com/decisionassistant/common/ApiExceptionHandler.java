package com.decisionassistant.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(com.decisionassistant.lookup.LookupException.class)
    ProblemDetail lookup(com.decisionassistant.lookup.LookupException exception) {
        var detail = problem(HttpStatus.valueOf(exception.status()), "Product search unavailable", exception.getMessage());
        detail.setProperty("code", exception.code());
        return detail;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ProblemDetail staleDraft(org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", "비교 내용이 변경되었습니다. 최신 내용을 다시 불러오세요.");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler(InvalidDecisionException.class)
    ProblemDetail invalidDecision(InvalidDecisionException exception) {
        var detail = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Decision is not ready", exception.getMessage());
        detail.setProperty("violations", exception.violations());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException exception) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        var detail = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
        detail.setProperty("violations", violations);
        return detail;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://decision-assistant.local/problems/" + status.value()));
        return problem;
    }
}
