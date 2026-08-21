package com.hammerly.ai.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AiExceptionHandler {
    public static final String UNAVAILABLE_MESSAGE =
        "Hammerly AI is temporarily unavailable. Please try again.";

    private static final Logger log = LoggerFactory.getLogger(AiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(error("Invalid chat request."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(error("Invalid chat request."));
    }

    @ExceptionHandler(AiProviderUnavailableException.class)
    ResponseEntity<Map<String, Object>> handleProviderUnavailable(AiProviderUnavailableException exception) {
        log.warn("AI provider request failed ({})", rootCauseName(exception));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(UNAVAILABLE_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        log.error("Unexpected AI API error ({})", rootCauseName(exception));
        return ResponseEntity.internalServerError().body(error(UNAVAILABLE_MESSAGE));
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "ai_request_failed");
        body.put("message", message);
        return body;
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
