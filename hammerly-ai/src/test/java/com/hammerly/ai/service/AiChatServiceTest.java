package com.hammerly.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hammerly.ai.cache.AiCacheKeyFactory;
import com.hammerly.ai.cache.AiResponseCache;
import com.hammerly.ai.cache.RedisAiResponseCache;
import com.hammerly.ai.config.AiStateProperties;
import com.hammerly.ai.config.HammerlySystemPrompt;
import com.hammerly.ai.config.OpenAiConfigurationState;
import com.hammerly.ai.conversation.ConversationHistory;
import com.hammerly.ai.conversation.ConversationMessage;
import com.hammerly.ai.conversation.ConversationStore;
import com.hammerly.ai.conversation.RedisConversationStore;
import com.hammerly.ai.dto.ChatRequest;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.diagnostic.OpenAiProviderFailureClassifier;
import com.hammerly.ai.diagnostic.OpenAiProviderFailureDiagnostics;
import com.hammerly.ai.diagnostic.OpenAiProviderRetryPolicy;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.ratelimit.AiRateLimiter;
import com.hammerly.ai.ratelimit.RateLimitDecision;
import com.hammerly.ai.ratelimit.RedisAiRateLimiter;
import com.hammerly.ai.support.AiTestFixtures;
import com.hammerly.ai.support.FakeRedisStateClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Flux;

class AiChatServiceTest {
    private static final String CONVERSATION_ID = "b29bd72b-a2d5-4938-90f0-151867ac4c7a";

    private AiModelClient modelClient;
    private ConversationStore conversationStore;
    private AiResponseCache responseCache;
    private AiCacheKeyFactory cacheKeyFactory;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        modelClient = mock(AiModelClient.class);
        conversationStore = mock(ConversationStore.class);
        responseCache = mock(AiResponseCache.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        when(rateLimiter.acquire(anyString()))
            .thenReturn(new RateLimitDecision(true, 20, 19, 1_700_000_060L, true));
        when(conversationStore.getRecent(anyString(), anyString()))
            .thenReturn(ConversationHistory.available(List.of()));
        cacheKeyFactory = new AiCacheKeyFactory(new HammerlySystemPrompt("system prompt"));
        service = new AiChatService(modelClient, conversationStore, responseCache,
            cacheKeyFactory, rateLimiter,
            new AiMetrics(new SimpleMeterRegistry()),
            Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void cacheMissCallsModelCachesAnswerAndAppendsConversation() {
        when(responseCache.get(anyString())).thenReturn(Optional.empty());
        when(modelClient.chat(any(), anyString())).thenReturn("Use the bid form.");

        AiChatResult result = service.chat("42", request());

        assertEquals("Use the bid form.", result.answer());
        verify(modelClient).chat(List.of(), "How do I bid?");
        verify(responseCache).put(anyString(), org.mockito.ArgumentMatchers.eq("Use the bid form."));
        verify(conversationStore).append(anyString(), anyString(), any());
    }

    @Test
    void cacheHitAvoidsModelCall() {
        when(responseCache.get(anyString())).thenReturn(Optional.of("Cached answer"));

        AiChatResult result = service.chat("42", request());

        assertEquals("Cached answer", result.answer());
        verify(modelClient, never()).chat(any(), anyString());
        verify(modelClient, never()).stream(any(), anyString());
    }

    @Test
    void exactRetryOfPreviousTurnUsesPriorContextCacheKey() {
        Instant timestamp = Instant.parse("2026-08-22T12:00:00Z");
        when(conversationStore.getRecent("42", CONVERSATION_ID)).thenReturn(
            ConversationHistory.available(List.of(
                new ConversationMessage(ChatRole.USER, "How do I bid?", timestamp),
                new ConversationMessage(ChatRole.ASSISTANT, "Cached answer", timestamp)
            )));
        String priorContextKey = cacheKeyFactory.create("42", CONVERSATION_ID,
            "How do I bid?", List.of());
        when(responseCache.get(priorContextKey)).thenReturn(Optional.of("Cached answer"));

        AiChatResult result = service.chat("42", request());

        assertEquals("Cached answer", result.answer());
        verify(modelClient, never()).chat(any(), anyString());
    }

    @Test
    void completedStreamingResponseIsCachedAndConversationIsAppended() {
        when(responseCache.get(anyString())).thenReturn(Optional.empty());
        when(modelClient.stream(any(), anyString())).thenReturn(Flux.just("Bid ", "now."));

        List<String> chunks = service.stream("42", request(), false).chunks().collectList().block();

        assertEquals(List.of("Bid ", "now."), chunks);
        verify(responseCache).put(anyString(), org.mockito.ArgumentMatchers.eq("Bid now."));
        verify(conversationStore).append(anyString(), anyString(), any());
    }

    @Test
    void cachedStreamingResponseUsesOneChunkAndAvoidsModel() {
        when(responseCache.get(anyString())).thenReturn(Optional.of("Cached stream"));

        List<String> chunks = service.stream("42", request(), false).chunks().collectList().block();

        assertEquals(List.of("Cached stream"), chunks);
        verify(modelClient, never()).stream(any(), anyString());
    }

    @Test
    void failedPartialStreamIsNotCachedOrAppended() {
        when(responseCache.get(anyString())).thenReturn(Optional.empty());
        when(modelClient.stream(any(), anyString())).thenReturn(Flux.concat(
            Flux.just("partial"),
            Flux.error(new SocketTimeoutException("timed out"))
        ));

        Flux<String> chunks = service.stream("42", request(), false).chunks();

        assertThrows(AiProviderUnavailableException.class, () -> chunks.collectList().block());
        verify(responseCache, never()).put(anyString(), anyString());
        verify(conversationStore, never()).append(anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulProviderRetryCachesAndAppendsOnlyFinalResponseOnce() {
        when(responseCache.get(anyString())).thenReturn(Optional.empty());
        AtomicInteger attempts = new AtomicInteger();
        OpenAiProviderExecutor executor = providerExecutor(new SimpleMeterRegistry());
        when(modelClient.stream(any(), anyString())).thenReturn(
            executor.stream("stream", () -> attempts.incrementAndGet() == 1
                ? Flux.error(new SocketTimeoutException("timed out"))
                : Flux.just("Final ", "answer"))
        );

        List<String> chunks = service.stream("42", request(), false).chunks().collectList().block();

        assertEquals(List.of("Final ", "answer"), chunks);
        assertEquals(2, attempts.get());
        verify(responseCache, times(1)).put(anyString(), eq("Final answer"));
        ArgumentCaptor<List<ConversationMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(conversationStore, times(1)).append(eq("42"), eq(CONVERSATION_ID), messages.capture());
        assertEquals(List.of("How do I bid?", "Final answer"),
            messages.getValue().stream().map(ConversationMessage::content).toList());
        assertEquals(List.of(ChatRole.USER, ChatRole.ASSISTANT),
            messages.getValue().stream().map(ConversationMessage::role).toList());
    }

    @Test
    void completeRedisOutageDoesNotCrashChatAndRateLimitFailsOpen() {
        FakeRedisStateClient failedRedis = new FakeRedisStateClient();
        failedRedis.failAllOperations();
        AiStateProperties properties = AiTestFixtures.properties(20, 20, Duration.ofMinutes(1));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics outageMetrics = new AiMetrics(registry);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC);
        AiChatService outageService = new AiChatService(modelClient,
            new RedisConversationStore(failedRedis, new ObjectMapper().findAndRegisterModules(),
                properties, outageMetrics),
            new RedisAiResponseCache(failedRedis, properties, outageMetrics),
            new AiCacheKeyFactory(new HammerlySystemPrompt("system prompt")),
            new RedisAiRateLimiter(failedRedis, properties, outageMetrics, fixedClock),
            outageMetrics, fixedClock);
        when(modelClient.chat(any(), anyString())).thenReturn("Answer despite Redis outage");

        AiChatResult result = outageService.chat("42", request());

        assertEquals("Answer despite Redis outage", result.answer());
        assertEquals(1.0, registry.get("hammerly.ai.rate_limit.redis_failure").counter().count());
        verify(modelClient).chat(List.of(), "How do I bid?");
    }

    private ChatRequest request() {
        return new ChatRequest("How do I bid?", List.of(), CONVERSATION_ID);
    }

    private OpenAiProviderExecutor providerExecutor(SimpleMeterRegistry registry) {
        OpenAiProviderFailureClassifier classifier = new OpenAiProviderFailureClassifier();
        OpenAiConfigurationState configurationState = new OpenAiConfigurationState(
            new MockEnvironment()
                .withProperty("spring.ai.openai-sdk.chat.options.model", "gpt-5-mini")
        );
        return new OpenAiProviderExecutor(
            new OpenAiProviderFailureDiagnostics(classifier, configurationState),
            new OpenAiProviderRetryPolicy(),
            new AiMetrics(registry)
        );
    }
}
