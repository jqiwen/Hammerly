import http from 'node:http';

const port = Number(process.env.MOCK_OPENAI_PORT || 5100);
const firstTokenDelay = Number(process.env.MOCK_LLM_FIRST_TOKEN_DELAY_MS || 100);
const tokenInterval = Number(process.env.MOCK_LLM_TOKEN_INTERVAL_MS || 20);
const tokenCount = Number(process.env.MOCK_LLM_TOKEN_COUNT || 8);

function chunk(content, finishReason = null) {
  return JSON.stringify({
    id: 'chatcmpl-phase5-baseline',
    object: 'chat.completion.chunk',
    created: Math.floor(Date.now() / 1000),
    model: 'gpt-5-mini',
    choices: [{ index: 0, delta: content ? { content } : {}, finish_reason: finishReason }],
  });
}

const server = http.createServer((request, response) => {
  if (request.method !== 'POST' || !request.url.endsWith('/chat/completions')) {
    response.writeHead(404).end();
    return;
  }
  let body = '';
  request.on('data', (data) => { body += data; });
  request.on('end', () => {
    const payload = JSON.parse(body || '{}');
    if (!payload.stream) {
      setTimeout(() => {
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify({
          id: 'chatcmpl-phase5-baseline',
          object: 'chat.completion',
          created: Math.floor(Date.now() / 1000),
          model: 'gpt-5-mini',
          choices: [{ index: 0, message: { role: 'assistant', content: 'Hammerly baseline response' }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 8, completion_tokens: tokenCount, total_tokens: tokenCount + 8 },
        }));
      }, firstTokenDelay);
      return;
    }

    response.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    let index = 0;
    const emit = () => {
      if (index < tokenCount) {
        const content = index === 0 ? 'Hammerly ' : `token-${index} `;
        response.write(`data: ${chunk(content)}\n\n`);
        index += 1;
        if (index < tokenCount) {
          setTimeout(emit, tokenInterval);
          return;
        }
        response.write(`data: ${chunk('', 'stop')}\n\n`);
        response.end('data: [DONE]\n\n');
        return;
      }
      response.write(`data: ${chunk('', 'stop')}\n\n`);
      response.end('data: [DONE]\n\n');
    };
    setTimeout(emit, firstTokenDelay);
  });
});

server.listen(port, '0.0.0.0', () => {
  console.log(`Phase 5 baseline OpenAI mock listening on ${port}`);
});
