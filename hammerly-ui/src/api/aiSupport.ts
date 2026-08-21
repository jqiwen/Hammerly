const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

export const AI_SUPPORT_UNAVAILABLE_MESSAGE =
  'AI Support is currently being configured. Please try again shortly.';

export type AiSupportResponse = {
  answer: string;
};

type AiSupportApiPayload = {
  answer?: string;
  response?: string;
  data?: {
    answer?: string;
  };
};

export class AiSupportUnavailableError extends Error {
  constructor() {
    super(AI_SUPPORT_UNAVAILABLE_MESSAGE);
    this.name = 'AiSupportUnavailableError';
  }
}

const getAuthHeaders = () => {
  const token = localStorage.getItem('token');

  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

export const askAiSupport = async (question: string): Promise<AiSupportResponse> => {
  const trimmedQuestion = question.trim();

  if (!trimmedQuestion) {
    throw new Error('Please enter a question.');
  }

  try {
    const response = await fetch(`${API_BASE_URL}/ai/support/chat`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ question: trimmedQuestion }),
    });

    if (!response.ok) {
      throw new AiSupportUnavailableError();
    }

    const payload = (await response.json()) as AiSupportApiPayload;
    const answer = payload.answer ?? payload.response ?? payload.data?.answer;

    if (!answer?.trim()) {
      throw new AiSupportUnavailableError();
    }

    return { answer: answer.trim() };
  } catch (error) {
    if (error instanceof AiSupportUnavailableError) {
      throw error;
    }

    throw new AiSupportUnavailableError();
  }
};
