import { useEffect, useRef, useState } from 'react';
import AiSupportButton from './AiSupportButton';
import AiSupportPanel from './AiSupportPanel';
import {
  createInitialAiMessage,
  useAiSupportConversation,
} from './useAiSupportConversation';

const INITIAL_MESSAGES = [
  createInitialAiMessage(
    "Hi! I'm Hammerly AI Support.\n\nI can help with auctions, bidding, accounts, selling, and common Hammerly questions.",
  ),
];

const QUICK_QUESTIONS = [
  'How do I place a bid?',
  'How do auctions work?',
  'How do I create an auction?',
  'What happens if I win?',
];

export default function AiSupportWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [question, setQuestion] = useState('');
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const hasOpened = useRef(false);
  const {
    messages,
    isLoading,
    streamingMessageId,
    inputError,
    askQuestion,
    clearInputError,
  } = useAiSupportConversation(INITIAL_MESSAGES);

  useEffect(() => {
    if (isOpen) {
      hasOpened.current = true;
      window.requestAnimationFrame(() => inputRef.current?.focus());
    } else if (hasOpened.current) {
      buttonRef.current?.focus();
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;

    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [isOpen]);

  const submitQuestion = async (questionToSubmit = question, standaloneFaq = false) => {
    const accepted = await askQuestion(questionToSubmit, { standaloneFaq });
    if (accepted) {
      setQuestion('');
    }
  };

  return (
    <>
      <AiSupportButton
        ref={buttonRef}
        isOpen={isOpen}
        onClick={() => setIsOpen((current) => !current)}
      />
      {isOpen && (
        <AiSupportPanel
          messages={messages}
          question={question}
          onQuestionChange={setQuestion}
          onSubmit={() => void submitQuestion()}
          onQuickQuestion={(quickQuestion) => void submitQuestion(quickQuestion, true)}
          onClose={() => setIsOpen(false)}
          isLoading={isLoading}
          streamingMessageId={streamingMessageId}
          inputError={inputError}
          onClearInputError={clearInputError}
          inputRef={inputRef}
          quickQuestions={QUICK_QUESTIONS}
        />
      )}
    </>
  );
}
