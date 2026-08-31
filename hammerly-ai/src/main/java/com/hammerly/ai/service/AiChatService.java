package com.hammerly.ai.service;

import com.hammerly.ai.cache.AiCacheKeyFactory;
import com.hammerly.ai.cache.AiResponseCache;
import com.hammerly.ai.cache.GroundedFaqCache;
import com.hammerly.ai.cache.GroundedFaqCacheEntry;
import com.hammerly.ai.cache.GroundedFaqCacheProbe;
import com.hammerly.ai.cache.StandaloneFaqPolicy;
import com.hammerly.ai.context.AiContextBuilder;
import com.hammerly.ai.context.BuiltAiContext;
import com.hammerly.ai.conversation.ConversationHistory;
import com.hammerly.ai.conversation.ConversationAppendResult;
import com.hammerly.ai.conversation.ConversationMessage;
import com.hammerly.ai.conversation.ConversationStore;
import com.hammerly.ai.dto.ChatMessage;
import com.hammerly.ai.dto.ChatRequest;
import com.hammerly.ai.dto.ChatRole;
import com.hammerly.ai.exception.AiProviderUnavailableException;
import com.hammerly.ai.exception.AiRateLimitExceededException;
import com.hammerly.ai.event.AiEventPublisher;
import com.hammerly.ai.event.SuccessfulAiTurn;
import com.hammerly.ai.event.SummaryMessage;
import com.hammerly.ai.observability.AiMetrics;
import com.hammerly.ai.observability.AiRequestLatency;
import com.hammerly.ai.ratelimit.AiRateLimiter;
import com.hammerly.ai.ratelimit.RateLimitDecision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AiEventPublisher eventPublisher;
    private final Clock clock;
    private final AiContextBuilder contextBuilder;
    private final GroundedFaqCache groundedFaqCache;
    private final StandaloneFaqPolicy standaloneFaqPolicy;

    @Autowired
    public AiChatService(AiModelClient modelClient, ConversationStore conversationStore,
                         AiResponseCache responseCache, AiCacheKeyFactory cacheKeyFactory,
                         AiRateLimiter rateLimiter, AiMetrics metrics,
                         AiEventPublisher eventPublisher, Clock clock,
                         AiContextBuilder contextBuilder, GroundedFaqCache groundedFaqCache,
                         StandaloneFaqPolicy standaloneFaqPolicy) {
        this.modelClient = modelClient;
        this.conversationStore = conversationStore;
        this.responseCache = responseCache;
        this.cacheKeyFactory = cacheKeyFactory;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.contextBuilder = contextBuilder;
        this.groundedFaqCache = groundedFaqCache;
        this.standaloneFaqPolicy = standaloneFaqPolicy;
    }

    public AiChatService(AiModelClient modelClient, ConversationStore conversationStore,
                         AiResponseCache responseCache, AiCacheKeyFactory cacheKeyFactory,
                         AiRateLimiter rateLimiter, AiMetrics metrics,
                         AiEventPublisher eventPublisher, Clock clock,
                         AiContextBuilder contextBuilder) {
        this(modelClient, conversationStore, responseCache, cacheKeyFactory, rateLimiter,
            metrics, eventPublisher, clock, contextBuilder, GroundedFaqCache.disabled(),
            new StandaloneFaqPolicy());
    }

    public AiChatService(AiModelClient modelClient, ConversationStore conversationStore,
                         AiResponseCache responseCache, AiCacheKeyFactory cacheKeyFactory,
                         AiRateLimiter rateLimiter, AiMetrics metrics,
                         AiEventPublisher eventPublisher, Clock clock) {
        this(modelClient, conversationStore, responseCache, cacheKeyFactory, rateLimiter,
            metrics, eventPublisher, clock, AiContextBuilder.basic());
    }

    public AiChatResult chat(String userId, ChatRequest request) {
        RateLimitDecision rateLimit = acquirePermit(userId);
        long startedAt = System.nanoTime();
        metrics.aiRequestStarted();
        try {
            GroundedFaqCacheProbe faqProbe = fastFaqProbe(request);
            if (faqProbe.entry().isPresent()) {
                GroundedFaqCacheEntry entry = faqProbe.entry().orElseThrow();
                appendExchange(userId, request.conversationId(), request.message(),
                    entry.answer(), List.of()).ifPresent(this::publishEventsSafely);
                metrics.requestCompleted("success", startedAt);
                log.info("AI chat grounded FAQ cache hit");
                return new AiChatResult(entry.answer(), rateLimit, entry.sources());
            }
            PreparedRequest prepared = prepare(userId, request);
            Optional<String> cached = findCached(prepared);
            if (cached.isPresent()) {
                String answer = cached.orElseThrow();
                prepared.retryCacheKey().ifPresent(key -> responseCache.put(prepared.cacheKey(), answer));
                cacheGroundedFaq(request, prepared, answer);
                appendExchange(userId, request.conversationId(), request.message(), answer, prepared)
                    .ifPresent(this::publishEventsSafely);
                metrics.requestCompleted("success", startedAt);
                log.info("AI chat cache hit");
                return new AiChatResult(answer, rateLimit, prepared.sources());
            }

            log.info("AI chat cache miss contextMessages={}", prepared.context().size());
            try {
                String answer = modelClient.chat(prepared.modelRequest());
                if (!StringUtils.hasText(answer)) {
                    throw new AiProviderUnavailableException("AI provider returned an empty response.");
                }
                responseCache.put(prepared.cacheKey(), answer);
                cacheGroundedFaq(request, prepared, answer);
                appendExchange(userId, request.conversationId(), request.message(), answer, prepared)
                    .ifPresent(this::publishEventsSafely);
                metrics.requestCompleted("success", startedAt);
                log.info("AI chat completed durationMs={}", elapsedMillis(startedAt));
                return new AiChatResult(answer, rateLimit, prepared.sources());
            } catch (RuntimeException exception) {
                log.warn("AI chat request failed durationMs={} errorType={}",
                    elapsedMillis(startedAt), rootCauseName(exception));
                throw exception instanceof AiProviderUnavailableException
                    ? exception
                    : new AiProviderUnavailableException("AI provider request failed.", exception);
            }
        } catch (RuntimeException exception) {
            metrics.requestCompleted("error", startedAt);
            throw exception;
        } finally {
            metrics.aiRequestFinished();
        }
    }

    public AiStreamResult stream(String userId, ChatRequest request, boolean permitAlreadyAcquired) {
        return stream(userId, request, permitAlreadyAcquired, null);
    }

    public AiStreamResult stream(String userId, ChatRequest request, boolean permitAlreadyAcquired,
                                 Long coreAiStartedAtEpochMs) {
        RateLimitDecision rateLimit = permitAlreadyAcquired
            ? RateLimitDecision.prechecked()
            : acquirePermit(userId);
        long startedAt = System.nanoTime();
        AiRequestLatency latency = AiRequestLatency.start(coreAiStartedAtEpochMs);
        metrics.aiRequestStarted();
        try {
            GroundedFaqCacheProbe faqProbe = fastFaqProbe(request);
            latency.faqCacheLookup(faqProbe.durationMs(), faqProbe.entry().isPresent());
            if (faqProbe.entry().isPresent()) {
                latency.cacheHit();
                GroundedFaqCacheEntry entry = faqProbe.entry().orElseThrow();
                AtomicReference<SuccessfulAiTurn> completedTurn = new AtomicReference<>();
                Flux<String> fastStream = Flux.just(entry.answer())
                    .doOnComplete(() -> appendExchange(userId, request.conversationId(),
                        request.message(), entry.answer(), List.of()).ifPresent(completedTurn::set))
                    .doFinally(signal -> {
                        metrics.requestCompleted(requestOutcome(signal), startedAt);
                        metrics.aiRequestFinished();
                        if (signal == SignalType.ON_COMPLETE) {
                            Optional.ofNullable(completedTurn.get()).ifPresent(this::publishEventsSafely);
                        }
                    });
                log.info("AI stream grounded FAQ cache hit");
                return new AiStreamResult(fastStream, rateLimit, entry.sources(), latency);
            }
            PreparedRequest prepared = prepare(userId, request);
            latency.contextBuilt(prepared.contextDurationMs(), prepared.summaryDurationMs(),
                prepared.ragDurationMs(), prepared.knowledgeVersionDurationMs(),
                prepared.ragCacheDurationMs(), prepared.embeddingDurationMs(),
                prepared.ragSearchDurationMs());
            Optional<String> cached = findCached(prepared);
            if (cached.isPresent()) {
                latency.cacheHit();
                String answer = cached.orElseThrow();
                prepared.retryCacheKey().ifPresent(key -> responseCache.put(prepared.cacheKey(), answer));
                cacheGroundedFaq(request, prepared, answer);
                log.info("AI stream cache hit");
                AtomicReference<SuccessfulAiTurn> completedTurn = new AtomicReference<>();
                Flux<String> cachedStream = Flux.just(answer)
                    .doOnComplete(() -> appendExchange(userId, request.conversationId(),
                        request.message(), answer, prepared).ifPresent(completedTurn::set))
                    .doFinally(signal -> {
                        metrics.requestCompleted(requestOutcome(signal), startedAt);
                        metrics.aiRequestFinished();
                        if (signal == SignalType.ON_COMPLETE) {
                            Optional.ofNullable(completedTurn.get()).ifPresent(this::publishEventsSafely);
                        }
                    });
                return new AiStreamResult(cachedStream, rateLimit, prepared.sources(), latency);
            }

            log.info("AI stream cache miss contextMessages={}", prepared.context().size());
            StringBuilder completedResponse = new StringBuilder();
            AtomicReference<SuccessfulAiTurn> completedTurn = new AtomicReference<>();
            final Flux<String> chunks;
            try {
                chunks = modelClient.stream(prepared.modelRequest());
            } catch (RuntimeException exception) {
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
                        cacheGroundedFaq(request, prepared, answer);
                        appendExchange(userId, request.conversationId(), request.message(), answer,
                            prepared).ifPresent(completedTurn::set);
                    }
                    log.info("AI stream completed durationMs={}", elapsedMillis(startedAt));
                })
                .doOnError(exception -> log.warn("AI stream failed durationMs={} errorType={}",
                    elapsedMillis(startedAt), rootCauseName(exception)))
                .doFinally(signal -> {
                    metrics.requestCompleted(requestOutcome(signal), startedAt);
                    metrics.aiRequestFinished();
                    if (signal == SignalType.ON_COMPLETE) {
                        Optional.ofNullable(completedTurn.get()).ifPresent(this::publishEventsSafely);
                    }
                })
                .onErrorMap(exception -> exception instanceof AiProviderUnavailableException
                    ? exception
                    : new AiProviderUnavailableException("AI provider stream was interrupted.", exception))
                .contextWrite(context -> context.put(AiRequestLatency.class, latency));
            return new AiStreamResult(observed, rateLimit, prepared.sources(), latency);
        } catch (RuntimeException exception) {
            metrics.requestCompleted("error", startedAt);
            metrics.aiRequestFinished();
            latency.completed("error");
            throw exception;
        }
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
        List<ConversationMessage> snapshot = stored.messages();
        List<ChatMessage> context = snapshot.stream()
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
                ConversationAppendResult importResult = conversationStore.append(
                    userId, request.conversationId(), imported);
                if (importResult != null && importResult.successful()) {
                    snapshot = imported;
                }
            }
        }

        BuiltAiContext built = contextBuilder.build(userId, request.conversationId(),
            context, request.message());
        String cacheKey = cacheKeyFactory.create(userId, request.conversationId(),
            cacheMaterial(built), built.messages());
        Optional<String> retryCacheKey = retryCacheKey(userId, request, built.messages(),
            built.systemContext());
        return new PreparedRequest(new ModelRequest(built.messages(), built.question(),
            built.systemContext()), snapshot, cacheKey, retryCacheKey, built.sources(),
            built.knowledgeBaseVersion(), built.contextDurationMs(), built.summaryDurationMs(),
            built.ragDurationMs(),
            built.knowledgeVersionDurationMs(), built.ragCacheDurationMs(),
            built.embeddingDurationMs(), built.ragSearchDurationMs());
    }

    private String cacheMaterial(BuiltAiContext built) {
        return built.question() + "\n" + built.systemContext();
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

    private GroundedFaqCacheProbe fastFaqProbe(ChatRequest request) {
        if (!standaloneFaqPolicy.allowsFastCache(request)) {
            return GroundedFaqCacheProbe.unavailable();
        }
        return groundedFaqCache.lookup(request.message());
    }

    private void cacheGroundedFaq(ChatRequest request, PreparedRequest prepared, String answer) {
        if (!standaloneFaqPolicy.allowsFastCache(request)) return;
        groundedFaqCache.put(request.message(), prepared.knowledgeBaseVersion(), answer,
            prepared.sources());
    }

    private Optional<String> retryCacheKey(String userId, ChatRequest request,
                                           List<ChatMessage> context, String systemContext) {
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
            request.message() + "\n" + systemContext, priorContext));
    }

    private String normalized(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private Optional<SuccessfulAiTurn> appendExchange(String userId, String conversationId,
                                                       String question, String answer,
                                                       PreparedRequest prepared) {
        return appendExchange(userId, conversationId, question, answer,
            prepared.conversationSnapshot());
    }

    private Optional<SuccessfulAiTurn> appendExchange(String userId, String conversationId,
                                                       String question, String answer,
                                                       List<ConversationMessage> conversationSnapshot) {
        Instant timestamp = clock.instant();
        ConversationAppendResult result = conversationStore.append(userId, conversationId, List.of(
            new ConversationMessage(ChatRole.USER, question, timestamp),
            new ConversationMessage(ChatRole.ASSISTANT, answer, timestamp)
        ));
        if (result == null || !result.successful()) {
            return Optional.empty();
        }

        List<SummaryMessage> snapshot = new ArrayList<>();
        for (ConversationMessage message : conversationSnapshot) {
            snapshot.add(new SummaryMessage(
                message.role(), message.content(), message.timestamp()));
        }
        snapshot.add(new SummaryMessage(ChatRole.USER, question, timestamp));
        snapshot.add(new SummaryMessage(ChatRole.ASSISTANT, answer, timestamp));
        if (snapshot.size() > result.messageCount()) {
            snapshot = new ArrayList<>(snapshot.subList(
                snapshot.size() - result.messageCount(), snapshot.size()));
        }
        return Optional.of(new SuccessfulAiTurn(userId, conversationId, question, answer,
            timestamp, result.messageCount(), snapshot));
    }

    private void publishEventsSafely(SuccessfulAiTurn turn) {
        try {
            eventPublisher.publishSuccessfulTurn(turn);
        } catch (RuntimeException exception) {
            metrics.kafkaPublishFailure("successful_turn");
            log.warn("Kafka event dispatch failed errorType={}", rootCauseName(exception));
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String requestOutcome(SignalType signal) {
        return switch (signal) {
            case ON_COMPLETE -> "success";
            case CANCEL -> "cancelled";
            default -> "error";
        };
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private record PreparedRequest(ModelRequest modelRequest,
                                   List<ConversationMessage> conversationSnapshot,
                                   String cacheKey,
                                   Optional<String> retryCacheKey,
                                   List<com.hammerly.ai.rag.RagSource> sources,
                                   long knowledgeBaseVersion,
                                   long contextDurationMs,
                                   long summaryDurationMs,
                                   long ragDurationMs,
                                   long knowledgeVersionDurationMs,
                                   long ragCacheDurationMs,
                                   long embeddingDurationMs,
                                   long ragSearchDurationMs) {
        List<ChatMessage> context() {
            return modelRequest.history();
        }
    }
}
