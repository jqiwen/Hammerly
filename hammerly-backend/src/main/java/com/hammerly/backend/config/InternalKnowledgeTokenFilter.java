package com.hammerly.backend.config;

import com.hammerly.backend.client.InternalAiHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalKnowledgeTokenFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/internal/";
    private final byte[] expected;
    private final boolean required;

    public InternalKnowledgeTokenFilter(
            @Value("${hammerly.ai.internal-token:}") String token,
            @Value("${hammerly.internal-token-required:false}") boolean required) {
        this.expected = StringUtils.hasText(token)
            ? token.getBytes(StandardCharsets.UTF_8) : new byte[0];
        this.required = required;
        if (required && expected.length == 0) {
            throw new IllegalStateException("HAMMERLY_AI_INTERNAL_TOKEN is required for internal APIs");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX) || (!required && expected.length == 0);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(InternalAiHeaders.INTERNAL_TOKEN);
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Unauthorized internal request\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
