import { forwardRef, type KeyboardEvent } from 'react';
import { LoaderCircle, Send } from 'lucide-react';

type AiQuestionInputProps = {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  isLoading: boolean;
  error?: string | null;
  onClearError?: () => void;
  placeholder?: string;
  submitLabel?: string;
  compact?: boolean;
  inputId?: string;
};

const AiQuestionInput = forwardRef<HTMLTextAreaElement, AiQuestionInputProps>(
  (
    {
      value,
      onChange,
      onSubmit,
      isLoading,
      error,
      onClearError,
      placeholder = 'Ask about bidding, auctions, accounts, or selling...',
      submitLabel = 'Ask AI',
      compact = false,
      inputId = 'ai-question',
    },
    ref,
  ) => {
    const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        onSubmit();
      }
    };

    return (
      <form
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit();
        }}
        className="w-full"
      >
        <label htmlFor={inputId} className="sr-only">
          Ask Hammerly AI a question
        </label>
        <div className={`flex ${compact ? 'items-end gap-2' : 'flex-col gap-3 sm:flex-row sm:items-end'}`}>
          <textarea
            ref={ref}
            id={inputId}
            value={value}
            onChange={(event) => {
              onChange(event.target.value);
              onClearError?.();
            }}
            onKeyDown={handleKeyDown}
            rows={compact ? 2 : 2}
            disabled={isLoading}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? `${inputId}-error` : undefined}
            placeholder={placeholder}
            className={`min-h-[52px] flex-1 resize-none rounded-xl border bg-white px-4 py-3 text-sm text-gray-900 outline-none transition-all placeholder:text-gray-400 focus:border-[#8B2635] focus:ring-2 focus:ring-[#8B2635]/20 disabled:cursor-wait disabled:bg-gray-50 ${
              error ? 'border-red-300' : 'border-gray-300'
            }`}
          />
          <button
            type="submit"
            disabled={isLoading}
            aria-label={compact ? submitLabel : undefined}
            className={`inline-flex min-h-[52px] items-center justify-center gap-2 rounded-xl bg-[#8B2635] font-semibold text-white transition-colors hover:bg-[#7A1F2B] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#8B2635] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 ${
              compact ? 'w-[52px] flex-shrink-0 px-3' : 'px-6'
            }`}
          >
            {isLoading ? (
              <LoaderCircle size={19} className="animate-spin" aria-hidden="true" />
            ) : (
              <Send size={19} aria-hidden="true" />
            )}
            <span className={compact ? 'sr-only' : ''}>
              {isLoading ? 'Asking...' : submitLabel}
            </span>
          </button>
        </div>
        {error && (
          <p id={`${inputId}-error`} role="alert" className="mt-2 text-sm font-medium text-red-700">
            {error}
          </p>
        )}
        {!compact && (
          <p className="mt-2 text-xs text-gray-500">
            Press Enter to send. Use Shift+Enter for a new line.
          </p>
        )}
      </form>
    );
  },
);

AiQuestionInput.displayName = 'AiQuestionInput';

export default AiQuestionInput;
