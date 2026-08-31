// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest';
import { streamAiSupport } from './aiSupport';

const sseResponse = (body: string) => new Response(body, {
  status: 200,
  headers: { 'Content-Type': 'text/event-stream' },
});

describe('streamAiSupport source events', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('delivers public source metadata and strips internal identifiers', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse(
      'event:sources\ndata:{"sources":[{"title":"Bidding","source":"Hammerly Support Guide","chunkId":"internal-uuid"}]}\n\n' +
      'event:chunk\ndata:{"content":"Open the auction."}\n\n' +
      'event:done\ndata:{"content":""}\n\n',
    ));
    const chunks: string[] = [];
    const sourceEvents: Array<Array<{ title: string; source: string }>> = [];

    await streamAiSupport({
      question: 'How do I place a bid?',
      history: [],
      conversationId: 'b29bd72b-a2d5-4938-90f0-151867ac4c7a',
      onChunk: (chunk) => chunks.push(chunk),
      onSources: (sources) => sourceEvents.push(sources),
    });

    expect(chunks).toEqual(['Open the auction.']);
    expect(sourceEvents).toEqual([[
      { title: 'Bidding', source: 'Hammerly Support Guide' },
    ]]);
  });

  it('temporarily accepts legacy metadata events during rollout', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse(
      'event:metadata\ndata:{"sources":[{"title":"Watchlists","source":"Hammerly Support Guide"}]}\n\n' +
      'event:done\ndata:{}\n\n',
    ));
    const sources: Array<{ title: string; source: string }> = [];

    await streamAiSupport({
      question: 'How do watchlists work?',
      history: [],
      conversationId: 'b29bd72b-a2d5-4938-90f0-151867ac4c7a',
      onChunk: vi.fn(),
      onSources: (received) => sources.push(...received),
    });

    expect(sources).toEqual([
      { title: 'Watchlists', source: 'Hammerly Support Guide' },
    ]);
  });
});
