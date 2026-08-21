type QuickQuestionsProps = {
  questions: string[];
  onSelect: (question: string) => void;
  disabled?: boolean;
  compact?: boolean;
};

export default function QuickQuestions({
  questions,
  onSelect,
  disabled = false,
  compact = false,
}: QuickQuestionsProps) {
  return (
    <div className="flex flex-wrap gap-2" aria-label="Suggested questions">
      {questions.map((question) => (
        <button
          key={question}
          type="button"
          onClick={() => onSelect(question)}
          disabled={disabled}
          className={`rounded-full border border-[#8B2635]/25 bg-white font-medium text-[#8B2635] transition-colors hover:border-[#8B2635] hover:bg-[#8B2635]/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#8B2635] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${
            compact ? 'px-3 py-1.5 text-xs' : 'px-4 py-2 text-sm'
          }`}
        >
          {question}
        </button>
      ))}
    </div>
  );
}
