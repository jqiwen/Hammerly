import { Sparkles } from 'lucide-react';
import type { AiChatMessage } from './useAiSupportConversation';

type AiMessageProps = {
  message: AiChatMessage;
  isStreaming?: boolean;
};

export function AiMessage({ message, isStreaming = false }: AiMessageProps) {
  const isAssistant = message.role === 'assistant';
  const sources = [...new Map((message.sources ?? []).map((source) =>
    [`${source.title}\u0000${source.source}`, source])).values()];

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
        {message.content ? (
          <p className="whitespace-pre-wrap">
            {message.content}
            {isStreaming && (
              <span className="ml-0.5 inline-block h-4 w-0.5 animate-pulse bg-[#8B2635] align-middle" />
            )}
          </p>
        ) : (
          <div className="flex h-6 items-center gap-1" aria-label="Hammerly AI is responding">
            {[0, 1, 2].map((dot) => (
              <span
                key={dot}
                className="h-1.5 w-1.5 animate-pulse rounded-full bg-[#8B2635]"
                style={{ animationDelay: `${dot * 140}ms` }}
              />
            ))}
          </div>
        )}
        {isAssistant && sources.length > 0 && (
          <div className="mt-3 border-t border-[#8B2635]/10 pt-2">
            <p className="text-[11px] font-bold uppercase tracking-[0.1em] text-[#8B2635]">
              Sources
            </p>
            <ul className="mt-1 space-y-0.5 text-xs text-gray-600">
              {sources.map((source) => (
                <li key={`${source.title}-${source.source}`}>
                  • {source.source}{source.title ? ` · ${source.title}` : ''}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}
