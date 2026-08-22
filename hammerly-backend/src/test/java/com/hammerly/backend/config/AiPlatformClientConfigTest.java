package com.hammerly.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hammerly.backend.client.InternalAiHeaders;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

class AiPlatformClientConfigTest {
    @Test
    void addsConfiguredInternalTokenToEveryAiRequest() throws Exception {
        InternalAiTokenInterceptor interceptor = new InternalAiTokenInterceptor("internal-token");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
            URI.create("http://hammerly-ai/internal/ai/status"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        byte[] body = new byte[0];
        when(execution.execute(request, body)).thenReturn(response);

        ClientHttpResponse actual = interceptor.intercept(request, body, execution);

        assertEquals("internal-token", request.getHeaders().getFirst(InternalAiHeaders.INTERNAL_TOKEN));
        assertEquals(response, actual);
        verify(execution).execute(request, body);
    }
}
