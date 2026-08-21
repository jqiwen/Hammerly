import { useEffect, useRef, type RefObject } from 'react';
import { Sparkles, X } from 'lucide-react';
import AiQuestionInput from './AiQuestionInput';
import { AiLoadingMessage, AiMessage } from './AiMessage';
import QuickQuestions from './QuickQuestions';
import type { AiChatMessage } from './useAiSupportConversation';

type AiSupportPanelProps = {
  messages: AiChatMessage[];
  question: string;
  onQuestionChange: (question: string) => void;
  onSubmit: () => void;
  onQuickQuestion: (question: string) => void;
  onClose: () => void;
  isLoading: boolean;
  inputError: string | null;
  onClearInputError: () => void;
  inputRef: RefObject<HTMLTextAreaElement | null>;
  quickQuestions: string[];
};

export default function AiSupportPanel({
  messages,
  question,
  onQuestionChange,
  onSubmit,
  onQuickQuestion,
  onClose,
  isLoading,
  inputError,
  onClearInputError,
  inputRef,
  quickQuestions,
}: AiSupportPanelProps) {
  const messageListRef = useRef<HTMLDivElement>(null);
  const hasUserMessage = messages.some((message) => message.role === 'user');

  useEffect(() => {
    const list = messageListRef.current;
    if (list) {
      list.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
    }
  }, [messages, isLoading]);

  return (
    <section
      id="hammerly-ai-support-panel"
      role="dialog"
      aria-label="Hammerly AI Support"
      className="fixed bottom-[78px] left-3 right-3 z-[69] flex max-h-[calc(100vh-104px)] flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl sm:bottom-[92px] sm:left-auto sm:right-6 sm:h-[560px] sm:w-[390px]"
    >
      <div className="flex items-start justify-between border-b border-gray-100 bg-[#151923] px-5 py-4 text-white">
        <div>
          <div className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#8B2635]">
              <Sparkles size={17} aria-hidden="true" />
            </span>
            <h2 className="font-bold">Hammerly AI Support</h2>
          </div>
          <p className="mt-1 pl-10 text-xs text-gray-300">Ask about auctions and bidding</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close AI Support panel"
          className="flex h-9 w-9 items-center justify-center rounded-full text-gray-300 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white"
        >
          <X size={19} aria-hidden="true" />
        </button>
      </div>

      <div
        ref={messageListRef}
        className="flex-1 space-y-3 overflow-y-auto bg-gray-50 px-4 py-4"
        aria-live="polite"
        aria-busy={isLoading}
      >
        {messages.map((message) => (
          <AiMessage key={message.id} message={message} />
        ))}
        {!hasUserMessage && (
          <div className="pt-1">
            <p className="mb-2 text-xs font-semibold uppercase tracking-[0.12em] text-gray-500">
              Popular questions
            </p>
            <QuickQuestions
              questions={quickQuestions}
              onSelect={onQuickQuestion}
              disabled={isLoading}
              compact
            />
          </div>
        )}
        {isLoading && <AiLoadingMessage />}
      </div>

      <div className="border-t border-gray-200 bg-white p-3">
        <AiQuestionInput
          ref={inputRef}
          inputId="floating-ai-question"
          value={question}
          onChange={onQuestionChange}
          onSubmit={onSubmit}
          isLoading={isLoading}
          error={inputError}
          onClearError={onClearInputError}
          placeholder="Ask a question..."
          submitLabel="Send question"
          compact
        />
        <p className="mt-2 text-center text-[11px] text-gray-400">
          Enter sends · Shift+Enter adds a line
        </p>
      </div>
    </section>
  );
}
