package com.hammerly.ai.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.ErrorObject;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class OpenAiProviderFailureClassifierTest {
    private final OpenAiProviderFailureClassifier classifier =
        new OpenAiProviderFailureClassifier();

    @Test
    void classifiesAuthenticationFailuresThroughWrappers() {
        UnauthorizedException providerFailure = UnauthorizedException.builder()
            .headers(emptyHeaders())
            .error(error("invalid_api_key", "invalid_request_error"))
            .build();

        OpenAiProviderFailure result = classifier.classify(
            new IllegalStateException("Spring AI wrapper", providerFailure)
        );

        assertThat(result.category()).isEqualTo(OpenAiProviderFailure.Category.AUTHENTICATION);
        assertThat(result.status()).isEqualTo(401);
        assertThat(result.code()).isEqualTo("invalid_api_key");
        assertThat(result.exceptionClass()).isEqualTo("UnauthorizedException");
    }

    @Test
    void distinguishesQuotaFromOrdinaryRateLimits() {
        RateLimitException providerFailure = RateLimitException.builder()
            .headers(emptyHeaders())
            .error(error("insufficient_quota", "insufficient_quota"))
            .build();

        OpenAiProviderFailure result = classifier.classify(providerFailure);

        assertThat(result.category()).isEqualTo(OpenAiProviderFailure.Category.QUOTA);
        assertThat(result.status()).isEqualTo(429);
        assertThat(result.code()).isEqualTo("insufficient_quota");
    }

    @Test
    void classifiesModelFailures() {
        BadRequestException providerFailure = BadRequestException.builder()
            .headers(emptyHeaders())
            .error(error("model_not_found", "invalid_request_error"))
            .build();

        OpenAiProviderFailure result = classifier.classify(providerFailure);

        assertThat(result.category()).isEqualTo(OpenAiProviderFailure.Category.MODEL);
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.code()).isEqualTo("model_not_found");
    }

    @Test
    void classifiesNetworkFailuresWithoutInspectingMessages() {
        OpenAiProviderFailure result = classifier.classify(
            new OpenAIIoException("sensitive transport detail")
        );

        assertThat(result.category()).isEqualTo(OpenAiProviderFailure.Category.NETWORK);
        assertThat(result.status()).isNull();
        assertThat(result.code()).isEqualTo("unspecified");
    }

    @Test
    void distinguishesTimeoutAndConnectionReset() {
        OpenAiProviderFailure timeout = classifier.classify(
            new OpenAIIoException("transport", new SocketTimeoutException("timed out"))
        );
        OpenAiProviderFailure reset = classifier.classify(
            new OpenAIIoException("transport", new SocketException("Connection reset"))
        );

        assertThat(timeout.category()).isEqualTo(OpenAiProviderFailure.Category.TIMEOUT);
        assertThat(timeout.exceptionClass()).isEqualTo("SocketTimeoutException");
        assertThat(reset.category()).isEqualTo(OpenAiProviderFailure.Category.CONNECTION_RESET);
        assertThat(reset.exceptionClass()).isEqualTo("SocketException");
    }

    @Test
    void unwrapsAnExistingSafeProviderExceptionToItsAsyncCause() {
        OpenAiProviderFailure result = classifier.classify(
            new AiProviderUnavailableException(
                "safe wrapper",
                new SocketTimeoutException("timed out")
            )
        );

        assertThat(result.category()).isEqualTo(OpenAiProviderFailure.Category.TIMEOUT);
        assertThat(result.exceptionClass()).isEqualTo("SocketTimeoutException");
    }

    @Test
    void classifiesRetryableServerStatus() {
        UnexpectedStatusCodeException providerFailure = UnexpectedStatusCodeException.builder()
            .statusCode(503)
            .headers(emptyHeaders())
            .error(error("service_unavailable", "server_error"))
            .build();

        OpenAiProviderFailure result = classifier.classify(providerFailure);

        assertThat(result.category()).isEqualTo(OpenAiProviderFailure.Category.SERVER_ERROR);
        assertThat(result.status()).isEqualTo(503);
    }

    @Test
    void redactsUnsafeProviderCodes() {
        BadRequestException providerFailure = BadRequestException.builder()
            .headers(emptyHeaders())
            .error(error("bad\nforged-log=true", "invalid_request_error"))
            .build();

        assertThat(classifier.classify(providerFailure).code()).isEqualTo("unspecified");
    }

    private Headers emptyHeaders() {
        return Headers.builder().build();
    }

    private ErrorObject error(String code, String type) {
        return ErrorObject.builder()
            .code(code)
            .message("test provider message")
            .param("test")
            .type(type)
            .build();
    }
}
