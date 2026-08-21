import { useRef, useState } from 'react';
import { ChevronDown, HelpCircle, Sparkles } from 'lucide-react';
import Header from '@/components/feature/Header';
import Footer from '@/components/feature/Footer';
import AiQuestionInput from '@/components/ai/AiQuestionInput';
import { AiMessage } from '@/components/ai/AiMessage';
import { useAiSupportConversation } from '@/components/ai/useAiSupportConversation';
import { allFaqItems, faqCategories, popularFaqIds } from './faqData';

export default function FAQ() {
  const [question, setQuestion] = useState('');
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const aiInputRef = useRef<HTMLTextAreaElement>(null);
  const {
    messages,
    isLoading,
    streamingMessageId,
    inputError,
    askQuestion,
    clearInputError,
  } = useAiSupportConversation();

  const latestAssistantMessage = [...messages]
    .reverse()
    .find((message) => message.role === 'assistant');
  const popularQuestions = popularFaqIds
    .map((id) => allFaqItems.find((item) => item.id === id))
    .filter(Boolean);

  const submitQuestion = async () => {
    const accepted = await askQuestion(question);
    if (accepted) {
      setQuestion('');
    }
  };

  const toggleQuestion = (id: string) => {
    setExpandedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const openPopularQuestion = (id: string) => {
    setExpandedIds((current) => new Set(current).add(id));
    window.requestAnimationFrame(() => {
      document.getElementById(`faq-${id}`)?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      });
    });
  };

  const focusAiQuestion = () => {
    document.getElementById('ask-hammerly-ai')?.scrollIntoView({ behavior: 'smooth' });
    window.requestAnimationFrame(() => aiInputRef.current?.focus());
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      <main className="pt-20">
        <section className="border-b border-gray-200 bg-[#151923] text-white">
          <div className="mx-auto max-w-7xl px-6 py-14 sm:py-16">
            <div className="max-w-3xl">
              <div className="mb-4 flex items-center gap-2 text-sm font-bold uppercase tracking-[0.18em] text-[#d8a8af]">
                <Sparkles size={17} aria-hidden="true" />
                FAQ &amp; AI Support
              </div>
              <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
                HOW CAN WE <span className="text-[#c35a6a]">HELP?</span>
              </h1>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-gray-300">
                Find straightforward answers about bidding, auctions, accounts, and selling—or ask Hammerly AI for help.
              </p>
            </div>
          </div>
        </section>

        <section id="ask-hammerly-ai" className="mx-auto max-w-7xl scroll-mt-24 px-6 py-10 sm:py-12">
          <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
            <div className="h-1 bg-[#8B2635]" />
            <div className="p-6 sm:p-8">
              <div className="mb-6 flex items-start gap-4">
                <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-[#8B2635] text-white">
                  <Sparkles size={21} aria-hidden="true" />
                </span>
                <div>
                  <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#8B2635]">
                    ✦ Hammerly AI
                  </p>
                  <h2 className="mt-1 text-2xl font-bold text-gray-900">Ask Hammerly AI</h2>
                  <p className="mt-1 text-sm text-gray-600">
                    Ask about bidding, auctions, accounts, or selling.
                  </p>
                </div>
              </div>

              <AiQuestionInput
                ref={aiInputRef}
                inputId="faq-ai-question"
                value={question}
                onChange={setQuestion}
                onSubmit={() => void submitQuestion()}
                isLoading={isLoading}
                error={inputError}
                onClearError={clearInputError}
              />

              {latestAssistantMessage && (
                <div
                  className="mt-6 max-w-3xl rounded-2xl border border-gray-200 bg-gray-50 p-4"
                  aria-live="polite"
                  aria-busy={isLoading}
                >
                  <AiMessage
                    message={latestAssistantMessage}
                    isStreaming={latestAssistantMessage.id === streamingMessageId}
                  />
                </div>
              )}
            </div>
          </div>

          <div className="mt-10">
            <div className="mb-5 flex items-end justify-between gap-4">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#8B2635]">
                  Start here
                </p>
                <h2 className="mt-1 text-2xl font-bold text-gray-900">Popular Questions</h2>
              </div>
              <p className="hidden text-sm text-gray-500 sm:block">Select a question to reveal its answer.</p>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {popularQuestions.map((item) => item && (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => openPopularQuestion(item.id)}
                  className="group flex min-h-[72px] items-center justify-between gap-4 rounded-xl border border-gray-200 bg-white px-5 py-4 text-left font-semibold text-gray-800 shadow-sm transition-all hover:-translate-y-0.5 hover:border-[#8B2635]/40 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#8B2635] focus-visible:ring-offset-2"
                >
                  <span>{item.question}</span>
                  <ChevronDown size={18} className="flex-shrink-0 text-[#8B2635]" aria-hidden="true" />
                </button>
              ))}
            </div>
          </div>
        </section>

        <section className="border-t border-gray-200 bg-white py-12 sm:py-16">
          <div className="mx-auto max-w-7xl px-6">
            <div className="mb-10 max-w-2xl">
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#8B2635]">
                Hammerly help centre
              </p>
              <h2 className="mt-2 text-3xl font-bold text-gray-900 sm:text-4xl">
                FREQUENTLY ASKED <span className="text-[#8B2635]">QUESTIONS</span>
              </h2>
              <p className="mt-3 text-gray-600">
                Browse concise guidance for the most common Hammerly tasks.
              </p>
            </div>

            <div className="grid items-start gap-6 lg:grid-cols-2">
              {faqCategories.map((category) => (
                <section
                  key={category.id}
                  aria-labelledby={`${category.id}-heading`}
                  className="overflow-hidden rounded-2xl border border-gray-200 bg-gray-50"
                >
                  <div className="border-b border-gray-200 bg-white px-6 py-5">
                    <div className="flex items-center gap-3">
                      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#8B2635]/10 text-[#8B2635]">
                        <HelpCircle size={19} aria-hidden="true" />
                      </span>
                      <div>
                        <h3 id={`${category.id}-heading`} className="text-xl font-bold text-gray-900">
                          {category.title}
                        </h3>
                        <p className="mt-0.5 text-sm text-gray-500">{category.description}</p>
                      </div>
                    </div>
                  </div>

                  <div className="divide-y divide-gray-200 px-6">
                    {category.questions.map((item) => {
                      const isExpanded = expandedIds.has(item.id);

                      return (
                        <div key={item.id} id={`faq-${item.id}`} className="scroll-mt-28 py-1">
                          <button
                            type="button"
                            onClick={() => toggleQuestion(item.id)}
                            aria-expanded={isExpanded}
                            aria-controls={`faq-answer-${item.id}`}
                            className="flex w-full items-center justify-between gap-4 py-4 text-left font-semibold text-gray-900 transition-colors hover:text-[#8B2635] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#8B2635] focus-visible:ring-offset-2"
                          >
                            <span>{item.question}</span>
                            <ChevronDown
                              size={19}
                              className={`flex-shrink-0 text-[#8B2635] transition-transform duration-200 ${
                                isExpanded ? 'rotate-180' : ''
                              }`}
                              aria-hidden="true"
                            />
                          </button>
                          <div
                            id={`faq-answer-${item.id}`}
                            aria-hidden={!isExpanded}
                            className={`overflow-hidden transition-all duration-200 ease-out ${
                              isExpanded ? 'max-h-48 opacity-100' : 'max-h-0 opacity-0'
                            }`}
                          >
                            <p className="pb-5 pr-8 text-sm leading-7 text-gray-600">{item.answer}</p>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </section>
              ))}
            </div>

            <div className="mt-10 flex flex-col items-start justify-between gap-5 rounded-2xl bg-[#151923] px-6 py-7 text-white sm:flex-row sm:items-center sm:px-8">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#d8a8af]">AI Support</p>
                <h3 className="mt-1 text-xl font-bold">Still have a Hammerly question?</h3>
                <p className="mt-1 text-sm text-gray-300">Ask another question at the top of this page.</p>
              </div>
              <button
                type="button"
                onClick={focusAiQuestion}
                className="inline-flex items-center gap-2 rounded-lg bg-[#8B2635] px-5 py-3 font-semibold text-white transition-colors hover:bg-[#a13a4a] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-[#151923]"
              >
                <Sparkles size={17} aria-hidden="true" />
                Ask Hammerly AI
              </button>
            </div>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}
