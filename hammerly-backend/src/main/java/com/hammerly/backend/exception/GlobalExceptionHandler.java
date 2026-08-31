package com.hammerly.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(error(exception.getMessage()));
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    ResponseEntity<Map<String, Object>> handleAiUnavailable(AiServiceUnavailableException exception) {
        return ResponseEntity.status(503).body(error("Hammerly AI is temporarily unavailable. Please try again."));
    }

    @ExceptionHandler(AiRateLimitExceededException.class)
    ResponseEntity<Map<String, Object>> handleAiRateLimit(AiRateLimitExceededException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", "AI_RATE_LIMIT_EXCEEDED");
        body.put("message", AiRateLimitExceededException.MESSAGE);
        return ResponseEntity.status(429)
            .header("X-RateLimit-Limit", Integer.toString(exception.rateLimit().limit()))
            .header("X-RateLimit-Remaining", Integer.toString(exception.rateLimit().remaining()))
            .header("X-RateLimit-Reset", Long.toString(exception.rateLimit().resetEpochSeconds()))
            .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().stream()
            .sorted(Comparator.comparing(error -> error.getField()))
            .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(validationError(fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(validationError(Map.of()));
    }

    @ExceptionHandler(AuthRateLimitExceededException.class)
    ResponseEntity<Map<String, Object>> handleAuthRateLimit(AuthRateLimitExceededException exception) {
        Map<String, Object> body = error(AuthRateLimitExceededException.MESSAGE);
        body.put("error", "AUTH_RATE_LIMIT_EXCEEDED");
        return ResponseEntity.status(429)
            .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
            .header("X-RateLimit-Limit", Integer.toString(exception.limit()))
            .body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled API error", exception);
        return ResponseEntity.internalServerError().body(error("Internal server error"));
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    private Map<String, Object> validationError(Map<String, String> fields) {
        Map<String, Object> response = error("Invalid request");
        response.put("error", "VALIDATION_ERROR");
        response.put("fields", fields);
        return response;
    }
}
