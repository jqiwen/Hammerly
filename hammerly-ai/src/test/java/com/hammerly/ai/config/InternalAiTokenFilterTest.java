package com.hammerly.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hammerly.ai.controller.InternalAiHeaders;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalAiTokenFilterTest {
    @Test
    void rejectsInternalRequestWithoutConfiguredTokenHeader() throws Exception {
        InternalAiTokenFilter filter = new InternalAiTokenFilter("production-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/ai/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsInternalRequestWithMatchingToken() throws Exception {
        InternalAiTokenFilter filter = new InternalAiTokenFilter("production-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/ai/chat");
        request.addHeader(InternalAiHeaders.INTERNAL_TOKEN, "production-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void keepsHealthEndpointPublicWhenTokenIsConfigured() throws Exception {
        InternalAiTokenFilter filter = new InternalAiTokenFilter("production-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
