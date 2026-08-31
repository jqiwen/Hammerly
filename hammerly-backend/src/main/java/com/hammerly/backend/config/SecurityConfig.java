package com.hammerly.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hammerly.backend.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter,
                                            InternalKnowledgeTokenFilter knowledgeTokenFilter,
                                            ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                String message = (String) request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE);
                if (message == null) {
                    message = "No token provided";
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("message", message);
                objectMapper.writeValue(response.getOutputStream(), body);
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                .requestMatchers("/api/users/profile/**", "/api/users/my-bids", "/api/users/my-auctions").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/auctions/bid/*", "/api/auctions/*/bid").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auctions/watch/*", "/api/auctions/create").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/auctions/unwatch/*", "/api/auctions/delete/*").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/auctions/get-watchlist", "/api/auctions/is-watched/*").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/auctions/end/*").authenticated()
                .requestMatchers("/health", "/actuator/health", "/actuator/prometheus", "/error",
                    "/v3/api-docs/**", "/api-docs/**", "/internal/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/ai/support/**")
                    .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth", "/api/auth/", "/api/users/*",
                    "/api/auctions/get-top", "/api/auctions/get/*", "/api/auctions/get-related/*",
                    "/api/auctions/search").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(knowledgeTokenFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
