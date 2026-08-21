import { useCallback, useRef, useState } from 'react';
import { AI_SUPPORT_UNAVAILABLE_MESSAGE, askAiSupport } from '@/api/aiSupport';

export type AiChatMessage = {
  id: string;
  role: 'assistant' | 'user';
  content: string;
  isError?: boolean;
};

let messageSequence = 0;

const createMessage = (
  role: AiChatMessage['role'],
  content: string,
  isError = false,
): AiChatMessage => ({
  id: `ai-message-${Date.now()}-${messageSequence++}`,
  role,
  content,
  isError,
});

export const createInitialAiMessage = (content: string) =>
  createMessage('assistant', content);

export function useAiSupportConversation(initialMessages: AiChatMessage[] = []) {
  const [messages, setMessages] = useState<AiChatMessage[]>(initialMessages);
  const [isLoading, setIsLoading] = useState(false);
  const [inputError, setInputError] = useState<string | null>(null);
  const requestInFlight = useRef(false);

  const clearInputError = useCallback(() => setInputError(null), []);

  const askQuestion = useCallback(async (question: string) => {
    const trimmedQuestion = question.trim();

    if (!trimmedQuestion) {
      setInputError('Please enter a question.');
      return false;
    }

    if (requestInFlight.current) {
      return false;
    }

    requestInFlight.current = true;
    setInputError(null);
    setIsLoading(true);
    setMessages((current) => [...current, createMessage('user', trimmedQuestion)]);

    try {
      const response = await askAiSupport(trimmedQuestion);
      setMessages((current) => [...current, createMessage('assistant', response.answer)]);
    } catch (error) {
      const message = error instanceof Error ? error.message : AI_SUPPORT_UNAVAILABLE_MESSAGE;
      setMessages((current) => [...current, createMessage('assistant', message, true)]);
    } finally {
      requestInFlight.current = false;
      setIsLoading(false);
    }

    return true;
  }, []);

  return {
    messages,
    isLoading,
    inputError,
    askQuestion,
    clearInputError,
  };
}
