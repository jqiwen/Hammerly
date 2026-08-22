package com.hammerly.backend.config;

import com.hammerly.backend.client.InternalAiHeaders;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

final class InternalAiTokenInterceptor implements ClientHttpRequestInterceptor {
    private final String token;

    InternalAiTokenInterceptor(String token) {
        this.token = token;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(InternalAiHeaders.INTERNAL_TOKEN, token);
        return execution.execute(request, body);
    }
}
