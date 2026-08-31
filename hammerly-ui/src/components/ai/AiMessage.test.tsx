// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { AiMessage } from './AiMessage';

describe('AiMessage sources', () => {
  afterEach(cleanup);

  it('renders a concise, human-readable source label once', () => {
    render(<AiMessage message={{
      id: 'answer-1',
      role: 'assistant',
      content: 'Open the auction and enter your bid.',
      sources: [
        { title: 'Bidding', source: 'Hammerly Support Guide' },
        { title: 'Bidding', source: 'Hammerly Support Guide' },
      ],
    }} />);

    expect(screen.getByText('Sources')).toBeInTheDocument();
    expect(screen.getAllByText(/Hammerly Support Guide · Bidding/)).toHaveLength(1);
    expect(screen.queryByText(/uuid|chunk/i)).not.toBeInTheDocument();
  });
});
