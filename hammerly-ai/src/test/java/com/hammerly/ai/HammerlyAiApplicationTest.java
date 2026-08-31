package com.hammerly.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hammerly.ai.redis.InMemoryRedisStateClient;
import com.hammerly.ai.redis.RedisStateClient;
import com.hammerly.ai.rag.NoOpRagRetrievalService;
import com.hammerly.ai.rag.RagRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "OPENAI_API_KEY=",
    "spring.ai.openai-sdk.api-key=",
    "hammerly.redis.enabled=false",
    "hammerly.kafka.enabled=false",
    "spring.data.redis.host=redis-must-not-be-contacted.invalid"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
class HammerlyAiApplicationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    RedisStateClient redisStateClient;

    @Autowired
    RagRetrievalService ragRetrievalService;

    @Test
    void contextLoads() {
        org.junit.jupiter.api.Assertions.assertInstanceOf(
            InMemoryRedisStateClient.class, redisStateClient);
    }

    @Test
    void repositoryDefaultKeepsRagDisabledAndNonBlocking() {
        org.junit.jupiter.api.Assertions.assertInstanceOf(
            NoOpRagRetrievalService.class, ragRetrievalService);
        org.junit.jupiter.api.Assertions.assertTrue(
            ragRetrievalService.retrieve("How do I bid?").chunks().isEmpty());
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
            .andExpect(jsonPath("$.aiConfigured").value(false))
            .andExpect(jsonPath("$.redisEnabled").value(false))
            .andExpect(jsonPath("$.kafkaEnabled").value(false));
    }

    @Test
    void actuatorExposesHealthWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void actuatorExposesPrometheusWithoutGeneralMetricsEndpoint() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ai_requests_total")));

        mvc.perform(get("/actuator"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._links.prometheus.href").exists())
            .andExpect(jsonPath("$._links.metrics").doesNotExist());
    }
}
