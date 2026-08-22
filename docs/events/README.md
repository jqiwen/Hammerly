# Hammerly Kafka contracts

Phase 4 uses a small topic set and versioned JSON envelopes. AI and worker maintain their own typed DTOs so neither service has a compile-time dependency on the other. Contract changes are coordinated through this document and compatibility tests.

## Topics

| Topic | Kind | Producer | Phase 4 consumer | Purpose |
| --- | --- | --- | --- | --- |
| `hammerly.ai.events.v1` | fact | `hammerly-ai` | `hammerly-worker-v1` | Successful AI conversation facts (`message.created`) |
| `hammerly.ai.jobs.v1` | command/job | `hammerly-ai` | `hammerly-worker-v1` | Background summary work; reserves embedding jobs |
| `hammerly.support.events.v1` | fact | future Core support flow | none | Reserved for real ticket lifecycle events |
| `hammerly.ai.events.v1.DLT` | dead letter | worker recoverer | operator | Exhausted records from AI events |
| `hammerly.ai.jobs.v1.DLT` | dead letter | worker recoverer | operator | Exhausted records from AI jobs |
| `hammerly.support.events.v1.DLT` | dead letter | future support consumer | operator | Reserved with the support topic |

The local KRaft broker auto-creates used topics with three partitions. Production environments should pre-provision the base and DLT topics with their desired replication factor and retention policy.

Conversation-related records use the exact `conversationId` as the Kafka key. Kafka therefore preserves order within a conversation/partition, while the worker's three listener containers can process different partitions concurrently. A future ticket producer must use `ticketId`. No global ordering is promised.

## Common envelope (version 1)

```json
{
  "eventId": "5e38db4c-b67a-4b76-ae64-58e3652a2dca",
  "eventType": "message.created",
  "eventVersion": 1,
  "occurredAt": "2026-08-22T12:00:00Z",
  "producer": "hammerly-ai",
  "correlationId": "ca9ca71a-b1ef-4c0b-ac84-6949e4dc7816",
  "userId": "42",
  "conversationId": "cfb5e918-b5a3-4ee5-94e5-0e183dc1cdf6",
  "payload": {}
}
```

Required fields:

- `eventId`: unique UUID used for worker idempotency.
- `eventType`: stable lower-case dotted name.
- `eventVersion`: integer payload/envelope contract version; Phase 4 accepts `1`.
- `occurredAt`: UTC ISO-8601 instant.
- `producer`: originating service, currently `hammerly-ai`.
- `correlationId`: UUID shared by the two message facts and optional summary request from one AI turn.
- `userId`: trusted Core-derived user identity; never a JWT.
- `conversationId`: conversation UUID and Kafka partition key.
- `payload`: event-specific typed object.

Envelopes and payloads must never include API keys, JWTs, cookies, broker credentials, or database credentials.

## Implemented and emitted

### `message.created` version 1

Topic: `hammerly.ai.events.v1`. AI emits one user and one assistant record only after the entire answer completed and the pair was stored in conversation state.

```json
{
  "role": "ASSISTANT",
  "content": "Open the listing and submit the bid form.",
  "createdAt": "2026-08-22T12:00:00Z"
}
```

`role` is `USER` or `ASSISTANT`. The worker treats each assistant message as one successfully completed AI turn for Micrometer analytics.

### `conversation.summary.requested` version 1

Topic: `hammerly.ai.jobs.v1`. AI emits this at or after the configured stored-message threshold only when its Redis `SET NX` threshold marker is newly claimed.

```json
{
  "messageCount": 10,
  "messages": [
    {
      "role": "USER",
      "content": "How do I bid?",
      "createdAt": "2026-08-22T11:59:50Z"
    },
    {
      "role": "ASSISTANT",
      "content": "Open an active listing and use its bid form.",
      "createdAt": "2026-08-22T12:00:00Z"
    }
  ]
}
```

`messages` is the bounded recent-history snapshot already held by AI (20 messages by default). Carrying the context in the durable job lets a restarted worker summarize its backlog even if the live recent-history key later expires. The worker writes a separate summary document at `hammerly:conversation:summary:{userId}:{conversationId}`.

## Defined but not emitted in Phase 4

These contracts prepare clean boundaries without inventing UI actions or business behavior.

### `conversation.completed` version 1

Topic: `hammerly.ai.events.v1`; key `conversationId`.

```json
{
  "messageCount": 14,
  "completedAt": "2026-08-22T12:10:00Z",
  "reason": "USER_ENDED"
}
```

Hammerly currently has no explicit end-conversation action, so nothing emits this event and the worker has no Phase 4 side effect for it.

### `ticket.created` version 1

Topic: `hammerly.support.events.v1`; key `ticketId`. A support producer would use a ticket-oriented envelope containing `ticketId` in addition to relevant user/correlation fields.

```json
{
  "ticketId": "TKT-12345",
  "title": "Bid submission problem",
  "description": "The customer could not submit a bid.",
  "createdAt": "2026-08-22T12:10:00Z"
}
```

There is no current ticket creation product flow, producer, UI, or worker consumer.

### `embedding.requested` version 1

Topic: `hammerly.ai.jobs.v1`; use a stable source/conversation key appropriate to the future ingestion flow.

```json
{
  "sourceType": "KNOWLEDGE_DOCUMENT",
  "sourceId": "auction-bidding-guide",
  "content": "Document content to be chunked in Phase 5."
}
```

The worker contains only the typed payload and `EmbeddingJobHandler` interface. Publishing this contract before a Phase 5 implementation is enabled will exhaust retries and reach the jobs DLT. Phase 4 does not chunk content, call an embedding model, create vectors, use pgvector, or perform retrieval.

## Delivery behavior

- Producer sends are best-effort asynchronous side effects. AI never waits for them to complete.
- Worker delivery is at least once with manual acknowledgment after successful processing.
- A completed-event marker lives for seven days at `hammerly:worker:processed:{eventId}`.
- A two-minute `SET NX` processing lock coordinates simultaneous copies of the same event.
- Failed processing is not marked complete and is not acknowledged by the listener.
- The error handler makes the initial attempt plus three retries at 500 ms intervals.
- After exhaustion, the recoverer sends the raw value, original key, and failure headers to the original topic plus `.DLT`, on the same partition, then commits the recovered offset.
- Broker-outage publication is not guaranteed. Worker-offline backlog recovery is guaranteed within Kafka retention because offsets remain uncommitted until processing succeeds.
