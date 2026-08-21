import { forwardRef } from 'react';
import { Sparkles, X } from 'lucide-react';

type AiSupportButtonProps = {
  isOpen: boolean;
  onClick: () => void;
};

const AiSupportButton = forwardRef<HTMLButtonElement, AiSupportButtonProps>(
  ({ isOpen, onClick }, ref) => (
    <button
      ref={ref}
      type="button"
      onClick={onClick}
      aria-label={isOpen ? 'Close AI Support' : 'Open AI Support'}
      aria-expanded={isOpen}
      aria-controls="hammerly-ai-support-panel"
      className="fixed bottom-4 right-4 z-[70] inline-flex min-h-[52px] items-center gap-2 rounded-full bg-[#8B2635] px-5 py-3 font-semibold text-white shadow-lg shadow-[#8B2635]/20 transition-all hover:-translate-y-0.5 hover:bg-[#7A1F2B] hover:shadow-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#8B2635] focus-visible:ring-offset-2 sm:bottom-6 sm:right-6"
    >
      {isOpen ? <X size={19} aria-hidden="true" /> : <Sparkles size={19} aria-hidden="true" />}
      <span className="hidden sm:inline">{isOpen ? 'Close' : 'AI Support'}</span>
    </button>
  ),
);

AiSupportButton.displayName = 'AiSupportButton';

export default AiSupportButton;
