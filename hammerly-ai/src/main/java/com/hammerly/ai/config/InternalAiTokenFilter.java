package com.hammerly.ai.config;

import com.hammerly.ai.controller.InternalAiHeaders;
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
public class InternalAiTokenFilter extends OncePerRequestFilter {
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final byte[] expectedToken;

    public InternalAiTokenFilter(@Value("${hammerly.ai.internal-token:}") String expectedToken) {
        this.expectedToken = StringUtils.hasText(expectedToken)
            ? expectedToken.getBytes(StandardCharsets.UTF_8)
            : new byte[0];
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expectedToken.length == 0 || !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String suppliedToken = request.getHeader(InternalAiHeaders.INTERNAL_TOKEN);
        byte[] suppliedBytes = suppliedToken == null
            ? new byte[0]
            : suppliedToken.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedToken, suppliedBytes)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Unauthorized internal request\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
