package com.hammerly.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "OPENAI_API_KEY=",
    "spring.ai.openai-sdk.api-key="
})
@AutoConfigureMockMvc
class HammerlyAiApplicationTest {
    @Autowired
    MockMvc mvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointReturnsStableServiceIdentity() throws Exception {
        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AI service is running"))
            .andExpect(jsonPath("$.service").value("hammerly-ai"));
    }

    @Test
    void internalStatusEndpointReportsFoundationState() throws Exception {
        mvc.perform(get("/internal/ai/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value("hammerly-ai"))
            .andExpect(jsonPath("$.status").value("ready"))
            .andExpect(jsonPath("$.aiConfigured").value(false));
    }

    @Test
    void actuatorExposesHealthWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void actuatorExposesMetricsEndpoint() throws Exception {
        mvc.perform(get("/actuator/metrics"))
            .andExpect(status().isOk());
    }
}
