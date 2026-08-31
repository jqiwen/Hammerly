package com.hammerly.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hammerly.backend.exception.AuthRateLimitExceededException;
import com.hammerly.backend.exception.GlobalExceptionHandler;
import com.hammerly.backend.security.AuthRateLimiter;
import com.hammerly.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerTest {
    private AuthService authService;
    private AuthRateLimiter rateLimiter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        rateLimiter = mock(AuthRateLimiter.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, rateLimiter))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .build();
    }

    @Test
    void invalidRegistrationReturnsSafeFieldErrorsBeforeServiceCall() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"invalid","password":"short","firstName":" ","lastName":"","phone":"abc"}
            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Invalid request"))
            .andExpect(jsonPath("$.fields.email").exists())
            .andExpect(jsonPath("$.fields.password").exists())
            .andExpect(jsonPath("$.fields.firstName").exists())
            .andExpect(jsonPath("$.fields.lastName").exists())
            .andExpect(jsonPath("$.fields.phone").exists());
        verifyNoInteractions(authService);
    }

    @Test
    void malformedJsonReturnsStructuredValidationErrorWithoutStackDetails() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.com\","))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fields").isMap())
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist());
        verifyNoInteractions(authService);
    }

    @Test
    void rateLimitResponseIsGenericAndIncludesRetryHint() throws Exception {
        doThrow(new AuthRateLimitExceededException(10, 60)).when(rateLimiter).checkLogin(any());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"person@example.com","password":"password123"}
            """))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.error").value("AUTH_RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value(AuthRateLimitExceededException.MESSAGE));
        verifyNoInteractions(authService);
    }
}
