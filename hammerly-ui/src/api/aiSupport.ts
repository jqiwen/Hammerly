const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

export const AI_SUPPORT_UNAVAILABLE_MESSAGE =
  'Hammerly AI is temporarily unavailable. Please try again.';
export const AI_SUPPORT_RATE_LIMIT_MESSAGE =
  'Too many AI requests. Please try again shortly.';
export const AI_SUPPORT_MAX_MESSAGE_LENGTH = 2_000;
export const AI_SUPPORT_MAX_HISTORY_MESSAGES = 20;

export type AiSupportHistoryMessage = {
  role: 'assistant' | 'user';
  content: string;
};

export class AiSupportUnavailableError extends Error {
  constructor() {
    super(AI_SUPPORT_UNAVAILABLE_MESSAGE);
    this.name = 'AiSupportUnavailableError';
  }
}

export class AiSupportRateLimitError extends Error {
  constructor(message = AI_SUPPORT_RATE_LIMIT_MESSAGE) {
    super(message);
    this.name = 'AiSupportRateLimitError';
  }
}

const getAuthHeaders = () => {
  const token = localStorage.getItem('token');

  return {
    Accept: 'text/event-stream, application/json',
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

type StreamAiSupportOptions = {
  question: string;
  history: AiSupportHistoryMessage[];
  conversationId: string;
  onChunk: (chunk: string) => void;
  signal?: AbortSignal;
};

const readEvent = (frame: string) => {
  let event = 'message';
  const data: string[] = [];

  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      data.push(line.slice('data:'.length).replace(/^ /, ''));
    }
  }

  return { event, data: data.join('\n') };
};

const readEventContent = (data: string) => {
  try {
    const payload = JSON.parse(data) as { content?: unknown };
    return typeof payload.content === 'string' ? payload.content : '';
  } catch {
    return data;
  }
};

export const streamAiSupport = async ({
  question,
  history,
  conversationId,
  onChunk,
  signal,
}: StreamAiSupportOptions): Promise<void> => {
  const trimmedQuestion = question.trim();
  if (!trimmedQuestion) {
    throw new Error('Please enter a question.');
  }
  if (trimmedQuestion.length > AI_SUPPORT_MAX_MESSAGE_LENGTH) {
    throw new Error(`Questions must be ${AI_SUPPORT_MAX_MESSAGE_LENGTH.toLocaleString()} characters or fewer.`);
  }

  try {
    const response = await fetch(`${API_BASE_URL}/ai/support/chat/stream`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({
        message: trimmedQuestion,
        history: history.slice(-AI_SUPPORT_MAX_HISTORY_MESSAGES),
        conversationId,
      }),
      signal,
    });

    if (response.status === 429) {
      const payload = await response.json().catch(() => null) as { message?: unknown } | null;
      throw new AiSupportRateLimitError(
        typeof payload?.message === 'string' ? payload.message : undefined,
      );
    }

    if (!response.ok || !response.body) {
      throw new AiSupportUnavailableError();
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let completed = false;

    while (!completed) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      buffer = buffer.replace(/\r\n/g, '\n');

      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        boundary = buffer.indexOf('\n\n');

        if (!frame.trim() || frame.startsWith(':')) continue;
        const parsed = readEvent(frame);
        if (parsed.event === 'chunk') {
          const content = readEventContent(parsed.data);
          if (content) onChunk(content);
        } else if (parsed.event === 'done') {
          completed = true;
          break;
        } else if (parsed.event === 'error') {
          throw new AiSupportUnavailableError();
        }
      }

      if (done && !completed) {
        throw new AiSupportUnavailableError();
      }
    }

    await reader.cancel();
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    if (error instanceof AiSupportUnavailableError || error instanceof AiSupportRateLimitError || error instanceof Error &&
        error.message.startsWith('Questions must be')) {
      throw error;
    }
    throw new AiSupportUnavailableError();
  }
};
