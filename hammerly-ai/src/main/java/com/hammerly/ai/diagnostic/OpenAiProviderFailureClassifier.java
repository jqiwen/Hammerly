package com.hammerly.ai.diagnostic;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiProviderFailureClassifier {
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final String UNSPECIFIED = "unspecified";

    public OpenAiProviderFailure classify(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        Throwable deepest = failure;
        OpenAiProviderFailure networkFallback = null;

        while (current != null && visited.add(current)) {
            deepest = current;
            if (current instanceof OpenAIServiceException serviceException) {
                return classifyServiceException(serviceException);
            }
            if (isTimeout(current)) {
                return new OpenAiProviderFailure(
                    OpenAiProviderFailure.Category.TIMEOUT,
                    null,
                    UNSPECIFIED,
                    safeClassName(current)
                );
            }
            if (isConnectionReset(current)) {
                return new OpenAiProviderFailure(
                    OpenAiProviderFailure.Category.CONNECTION_RESET,
                    null,
                    UNSPECIFIED,
                    safeClassName(current)
                );
            }
            if (current instanceof OpenAIIoException || current instanceof IOException) {
                networkFallback = new OpenAiProviderFailure(
                    OpenAiProviderFailure.Category.NETWORK,
                    null,
                    UNSPECIFIED,
                    safeClassName(current)
                );
            }
            current = current.getCause();
        }

        if (networkFallback != null) {
            return networkFallback;
        }
        return new OpenAiProviderFailure(
            OpenAiProviderFailure.Category.UNKNOWN,
            null,
            UNSPECIFIED,
            safeClassName(deepest)
        );
    }

    private OpenAiProviderFailure classifyServiceException(OpenAIServiceException exception) {
        int status = exception.statusCode();
        String rawCode = exception.code().orElse(null);
        String rawType = exception.type().orElse(null);
        String code = safeCode(rawCode == null ? rawType : rawCode);
        String classificationCode = ((rawCode == null ? "" : rawCode) + " "
            + (rawType == null ? "" : rawType)).toLowerCase(Locale.ROOT);

        OpenAiProviderFailure.Category category;
        if (status == 401 || status == 403 || classificationCode.contains("api_key")) {
            category = OpenAiProviderFailure.Category.AUTHENTICATION;
        } else if (classificationCode.contains("quota")) {
            category = OpenAiProviderFailure.Category.QUOTA;
        } else if (classificationCode.contains("model") || status == 404) {
            category = OpenAiProviderFailure.Category.MODEL;
        } else if (status == 429) {
            category = OpenAiProviderFailure.Category.RATE_LIMIT;
        } else if (status >= 500) {
            category = OpenAiProviderFailure.Category.SERVER_ERROR;
        } else if (status == 400 || status == 422) {
            category = OpenAiProviderFailure.Category.REQUEST;
        } else {
            category = OpenAiProviderFailure.Category.UNKNOWN;
        }

        return new OpenAiProviderFailure(category, status, code, safeClassName(exception));
    }

    private boolean isTimeout(Throwable failure) {
        return failure instanceof SocketTimeoutException
            || failure instanceof HttpTimeoutException
            || failure instanceof TimeoutException
            || failure.getClass().getSimpleName().contains("TimeoutException");
    }

    private boolean isConnectionReset(Throwable failure) {
        if (!(failure instanceof SocketException) && !(failure instanceof EOFException)) {
            return false;
        }
        String message = failure.getMessage();
        if (message == null) {
            return failure instanceof EOFException;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("reset")
            || normalized.contains("broken pipe")
            || normalized.contains("unexpected end")
            || normalized.contains("forcibly closed");
    }

    private String safeCode(String code) {
        if (!StringUtils.hasText(code) || !SAFE_CODE.matcher(code).matches()) {
            return UNSPECIFIED;
        }
        return code;
    }

    private String safeClassName(Throwable failure) {
        if (failure == null || !StringUtils.hasText(failure.getClass().getSimpleName())) {
            return "UnknownException";
        }
        return failure.getClass().getSimpleName();
    }
}
