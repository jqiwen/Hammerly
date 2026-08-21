import { Sparkles } from 'lucide-react';
import type { AiChatMessage } from './useAiSupportConversation';

type AiMessageProps = {
  message: AiChatMessage;
};

export function AiMessage({ message }: AiMessageProps) {
  const isAssistant = message.role === 'assistant';

  return (
    <div className={`flex ${isAssistant ? 'justify-start' : 'justify-end'}`}>
      <div
        className={`max-w-[88%] rounded-2xl px-4 py-3 text-sm leading-6 shadow-sm ${
          isAssistant
            ? message.isError
              ? 'border border-amber-200 bg-amber-50 text-amber-900'
              : 'border border-[#8B2635]/10 bg-[#8B2635]/5 text-gray-800'
            : 'bg-[#8B2635] text-white'
        }`}
        aria-label={isAssistant ? 'Hammerly AI message' : 'Your message'}
      >
        {isAssistant && (
          <div className="mb-1.5 flex items-center gap-1.5 text-xs font-bold uppercase tracking-[0.12em] text-[#8B2635]">
            <Sparkles size={13} aria-hidden="true" />
            Hammerly AI
          </div>
        )}
        <p className="whitespace-pre-wrap">{message.content}</p>
      </div>
    </div>
  );
}

export function AiLoadingMessage() {
  return (
    <div className="flex justify-start" aria-label="Hammerly AI is responding">
      <div className="rounded-2xl border border-[#8B2635]/10 bg-[#8B2635]/5 px-4 py-3 shadow-sm">
        <div className="mb-2 flex items-center gap-1.5 text-xs font-bold uppercase tracking-[0.12em] text-[#8B2635]">
          <Sparkles size={13} aria-hidden="true" />
          Hammerly AI
        </div>
        <div className="flex items-center gap-1" aria-hidden="true">
          {[0, 1, 2].map((dot) => (
            <span
              key={dot}
              className="h-1.5 w-1.5 animate-pulse rounded-full bg-[#8B2635]"
              style={{ animationDelay: `${dot * 140}ms` }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
