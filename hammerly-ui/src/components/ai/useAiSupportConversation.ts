import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AI_SUPPORT_MAX_HISTORY_MESSAGES,
  AI_SUPPORT_MAX_MESSAGE_LENGTH,
  AI_SUPPORT_UNAVAILABLE_MESSAGE,
  streamAiSupport,
  type AiSupportHistoryMessage,
  type AiSupportSource,
} from '@/api/aiSupport';

export type AiChatMessage = {
  id: string;
  role: 'assistant' | 'user';
  content: string;
  isError?: boolean;
  isLocal?: boolean;
  sources?: AiSupportSource[];
};

let messageSequence = 0;

const createConversationId = () => globalThis.crypto?.randomUUID?.() ??
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    const value = character === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });

const createMessage = (
  role: AiChatMessage['role'],
  content: string,
  options: Pick<AiChatMessage, 'isError' | 'isLocal'> = {},
): AiChatMessage => ({
  id: `ai-message-${Date.now()}-${messageSequence++}`,
  role,
  content,
  ...options,
});

export const createInitialAiMessage = (content: string) =>
  createMessage('assistant', content, { isLocal: true });

export function useAiSupportConversation(initialMessages: AiChatMessage[] = []) {
  const [messages, setMessages] = useState<AiChatMessage[]>(initialMessages);
  const [isLoading, setIsLoading] = useState(false);
  const [streamingMessageId, setStreamingMessageId] = useState<string | null>(null);
  const [inputError, setInputError] = useState<string | null>(null);
  const requestInFlight = useRef(false);
  const activeRequest = useRef<AbortController | null>(null);
  const mounted = useRef(true);
  const conversationId = useRef(createConversationId());

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      activeRequest.current?.abort();
    };
  }, []);

  const clearInputError = useCallback(() => setInputError(null), []);

  const askQuestion = useCallback((question: string) => {
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion) {
      setInputError('Please enter a question.');
      return false;
    }
    if (trimmedQuestion.length > AI_SUPPORT_MAX_MESSAGE_LENGTH) {
      setInputError(
        `Questions must be ${AI_SUPPORT_MAX_MESSAGE_LENGTH.toLocaleString()} characters or fewer.`,
      );
      return false;
    }
    if (requestInFlight.current) {
      return false;
    }

    const history: AiSupportHistoryMessage[] = messages
      .filter((message) => !message.isLocal && !message.isError && message.content.trim())
      .slice(-AI_SUPPORT_MAX_HISTORY_MESSAGES)
      .map(({ role, content }) => ({ role, content }));
    const assistantMessage = createMessage('assistant', '');
    const controller = new AbortController();

    requestInFlight.current = true;
    activeRequest.current = controller;
    setInputError(null);
    setIsLoading(true);
    setStreamingMessageId(assistantMessage.id);
    setMessages((current) => [
      ...current,
      createMessage('user', trimmedQuestion),
      assistantMessage,
    ]);

    void streamAiSupport({
      question: trimmedQuestion,
      history,
      conversationId: conversationId.current,
      signal: controller.signal,
      onChunk: (chunk) => {
        if (!mounted.current) return;
        setMessages((current) => current.map((message) =>
          message.id === assistantMessage.id
            ? { ...message, content: message.content + chunk }
            : message));
      },
      onSources: (sources) => {
        if (!mounted.current) return;
        setMessages((current) => current.map((message) =>
          message.id === assistantMessage.id ? { ...message, sources } : message));
      },
    }).catch((error: unknown) => {
      if (!mounted.current || error instanceof DOMException && error.name === 'AbortError') return;
      const errorMessage = error instanceof Error ? error.message : AI_SUPPORT_UNAVAILABLE_MESSAGE;
      setMessages((current) => current.map((message) =>
        message.id === assistantMessage.id
          ? { ...message, content: errorMessage, isError: true }
          : message));
    }).finally(() => {
      if (!mounted.current) return;
      requestInFlight.current = false;
      activeRequest.current = null;
      setIsLoading(false);
      setStreamingMessageId(null);
    });

    return true;
  }, [messages]);

  return {
    messages,
    isLoading,
    streamingMessageId,
    inputError,
    askQuestion,
    clearInputError,
  };
}
