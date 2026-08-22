package com.hammerly.ai.service;

import com.hammerly.ai.cache.AiCacheKeyFactory;
import com.hammerly.ai.cache.AiResponseCache;
import com.hammerly.ai.conversation.ConversationHistory;
import com.hammerly.ai.conversation.ConversationMessage;
import com.hammerly.ai.conversation.ConversationStore;
import com.hammerly.ai.dto.ChatMessage;
import com.hammerly.ai.dto.ChatRequest;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.exception.AiRateLimitExceededException;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.ratelimit.AiRateLimiter;
import com.hammerly.ai.ratelimit.RateLimitDecision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

@Service
public class AiChatService {
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final AiModelClient modelClient;
    private final ConversationStore conversationStore;
    private final AiResponseCache responseCache;
    private final AiCacheKeyFactory cacheKeyFactory;
    private final AiRateLimiter rateLimiter;
    private final AiMetrics metrics;
    private final Clock clock;

    public AiChatService(AiModelClient modelClient, ConversationStore conversationStore,
                         AiResponseCache responseCache, AiCacheKeyFactory cacheKeyFactory,
                         AiRateLimiter rateLimiter, AiMetrics metrics, Clock clock) {
        this.modelClient = modelClient;
        this.conversationStore = conversationStore;
        this.responseCache = responseCache;
        this.cacheKeyFactory = cacheKeyFactory;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.clock = clock;
    }

    public AiChatResult chat(String userId, ChatRequest request) {
        RateLimitDecision rateLimit = acquirePermit(userId);
        long startedAt = System.nanoTime();
        PreparedRequest prepared = prepare(userId, request);
        Optional<String> cached = findCached(prepared);
        if (cached.isPresent()) {
            String answer = cached.orElseThrow();
            prepared.retryCacheKey().ifPresent(key -> responseCache.put(prepared.cacheKey(), answer));
            appendExchange(userId, request.conversationId(), request.message(), answer);
            metrics.requestLatency("cache_hit", startedAt);
            log.info("AI chat cache hit user={} conversation={}", userId, request.conversationId());
            return new AiChatResult(answer, rateLimit);
        }

        log.info("AI chat cache miss user={} conversation={} contextMessages={}",
            userId, request.conversationId(), prepared.context().size());
        try {
            String answer = modelClient.chat(prepared.context(), request.message());
            if (!StringUtils.hasText(answer)) {
                throw new AiProviderUnavailableException("AI provider returned an empty response.");
            }
            responseCache.put(prepared.cacheKey(), answer);
            appendExchange(userId, request.conversationId(), request.message(), answer);
            metrics.requestLatency("llm_request", startedAt);
            log.info("AI chat completed durationMs={}", elapsedMillis(startedAt));
            return new AiChatResult(answer, rateLimit);
        } catch (RuntimeException exception) {
            metrics.requestLatency("llm_error", startedAt);
            log.warn("AI chat request failed durationMs={} errorType={}",
                elapsedMillis(startedAt), rootCauseName(exception));
            throw exception instanceof AiProviderUnavailableException
                ? exception
                : new AiProviderUnavailableException("AI provider request failed.", exception);
        }
    }

    public AiStreamResult stream(String userId, ChatRequest request, boolean permitAlreadyAcquired) {
        RateLimitDecision rateLimit = permitAlreadyAcquired
            ? RateLimitDecision.prechecked()
            : acquirePermit(userId);
        long startedAt = System.nanoTime();
        PreparedRequest prepared = prepare(userId, request);
        Optional<String> cached = findCached(prepared);
        if (cached.isPresent()) {
            String answer = cached.orElseThrow();
            prepared.retryCacheKey().ifPresent(key -> responseCache.put(prepared.cacheKey(), answer));
            appendExchange(userId, request.conversationId(), request.message(), answer);
            metrics.requestLatency("cache_hit", startedAt);
            log.info("AI stream cache hit user={} conversation={}", userId, request.conversationId());
            return new AiStreamResult(Flux.just(answer), rateLimit);
        }

        log.info("AI stream cache miss user={} conversation={} contextMessages={}",
            userId, request.conversationId(), prepared.context().size());
        StringBuilder completedResponse = new StringBuilder();
        final Flux<String> chunks;
        try {
            chunks = modelClient.stream(prepared.context(), request.message());
        } catch (RuntimeException exception) {
            metrics.requestLatency("llm_error", startedAt);
            throw exception instanceof AiProviderUnavailableException
                ? exception
                : new AiProviderUnavailableException("AI provider stream could not start.", exception);
        }

        Flux<String> observed = chunks
            .filter(StringUtils::hasLength)
            .doOnNext(completedResponse::append)
            .doOnComplete(() -> {
                String answer = completedResponse.toString();
                if (StringUtils.hasText(answer)) {
                    responseCache.put(prepared.cacheKey(), answer);
                    appendExchange(userId, request.conversationId(), request.message(), answer);
                }
                log.info("AI stream completed durationMs={}", elapsedMillis(startedAt));
            })
            .doOnError(exception -> log.warn("AI stream failed durationMs={} errorType={}",
                elapsedMillis(startedAt), rootCauseName(exception)))
            .doFinally(signal -> metrics.requestLatency(
                signal == SignalType.ON_COMPLETE ? "llm_request" : "llm_error", startedAt))
            .onErrorMap(exception -> exception instanceof AiProviderUnavailableException
                ? exception
                : new AiProviderUnavailableException("AI provider stream was interrupted.", exception));
        return new AiStreamResult(observed, rateLimit);
    }

    public RateLimitDecision acquirePermit(String userId) {
        RateLimitDecision decision = rateLimiter.acquire(userId);
        if (!decision.allowed()) {
            throw new AiRateLimitExceededException(decision);
        }
        return decision;
    }

    private PreparedRequest prepare(String userId, ChatRequest request) {
        ConversationHistory stored = conversationStore.getRecent(userId, request.conversationId());
        List<ChatMessage> context = stored.messages().stream()
            .map(message -> new ChatMessage(message.role(), message.content()))
            .toList();

        if (context.isEmpty() && !request.history().isEmpty()) {
            context = request.history();
            if (stored.redisAvailable()) {
                Instant timestamp = clock.instant();
                List<ConversationMessage> imported = request.history().stream()
                    .map(message -> new ConversationMessage(
                        message.role(), message.content(), timestamp))
                    .toList();
                conversationStore.append(userId, request.conversationId(), imported);
            }
        }

        String cacheKey = cacheKeyFactory.create(userId, request.conversationId(),
            request.message(), context);
        Optional<String> retryCacheKey = retryCacheKey(userId, request, context);
        return new PreparedRequest(context, cacheKey, retryCacheKey);
    }

    private Optional<String> findCached(PreparedRequest prepared) {
        if (prepared.retryCacheKey().isPresent()) {
            Optional<String> retriedResponse = responseCache.get(prepared.retryCacheKey().orElseThrow());
            if (retriedResponse.isPresent()) {
                return retriedResponse;
            }
        }
        return responseCache.get(prepared.cacheKey());
    }

    private Optional<String> retryCacheKey(String userId, ChatRequest request,
                                           List<ChatMessage> context) {
        if (context.size() < 2) {
            return Optional.empty();
        }
        ChatMessage previousUser = context.get(context.size() - 2);
        ChatMessage previousAssistant = context.getLast();
        if (previousUser.role() != ChatRole.USER || previousAssistant.role() != ChatRole.ASSISTANT
                || !normalized(previousUser.content()).equals(normalized(request.message()))) {
            return Optional.empty();
        }
        List<ChatMessage> priorContext = context.subList(0, context.size() - 2);
        return Optional.of(cacheKeyFactory.create(userId, request.conversationId(),
            request.message(), priorContext));
    }

    private String normalized(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private void appendExchange(String userId, String conversationId, String question, String answer) {
        Instant timestamp = clock.instant();
        conversationStore.append(userId, conversationId, List.of(
            new ConversationMessage(ChatRole.USER, question, timestamp),
            new ConversationMessage(ChatRole.ASSISTANT, answer, timestamp)
        ));
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private record PreparedRequest(List<ChatMessage> context, String cacheKey,
                                   Optional<String> retryCacheKey) {
    }
}
