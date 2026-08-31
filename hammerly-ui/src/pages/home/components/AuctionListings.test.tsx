// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { auctionApi, TOP_AUCTIONS_CACHE_KEY } from '../../../api/auctions';
import {
  FEATURED_AUCTIONS_BOOTSTRAP,
  type FeaturedAuctionSummary
} from '../../../bootstrap/featuredAuctions';
import AuctionListings from './AuctionListings';

const makeAuction = (
  id: number,
  title: string,
  timeRemaining: string
): FeaturedAuctionSummary => ({
  id,
  title,
  category: 'Test category',
  currentBid: id * 10,
  image: `/demo-auctions/test-${id}.webp`,
  startTime: '2026-08-30T00:00:00Z',
  endTime: '2026-09-30T00:00:00Z',
  timeRemaining
});

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
};

describe('AuctionListings first paint', () => {
  beforeEach(() => {
    sessionStorage.clear();
    window.REACT_APP_NAVIGATE = vi.fn();
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('bundles exactly five structurally valid seeded auction summaries', () => {
    expect(FEATURED_AUCTIONS_BOOTSTRAP).toHaveLength(5);
    expect(FEATURED_AUCTIONS_BOOTSTRAP.map(auction => auction.id)).toEqual([45, 3, 46, 4, 5]);

    for (const auction of FEATURED_AUCTIONS_BOOTSTRAP) {
      expect(auction.title).toBeTruthy();
      expect(auction.category).toBeTruthy();
      expect(auction.currentBid).toBeGreaterThan(0);
      expect(auction.image).toMatch(/^\/demo-auctions\/.+\.webp$/);
      expect(Date.parse(auction.startTime)).not.toBeNaN();
      expect(Date.parse(auction.endTime)).not.toBeNaN();
      expect(auction.timeRemaining).toBeTruthy();
    }
  });

  it('renders five bootstrap cards immediately on a fresh visit without a skeleton', () => {
    vi.spyOn(auctionApi, 'getTopAuctions').mockImplementation(() => new Promise(() => {}));

    render(<AuctionListings />);

    expect(screen.getAllByTestId('featured-auction-card')).toHaveLength(5);
    expect(screen.getByText('Big Agnes Copper Spur Tent')).toBeInTheDocument();
    expect(screen.queryByLabelText('Loading featured auctions')).not.toBeInTheDocument();
  });

  it('uses the fresh session cache for the immediate five cards', () => {
    const cachedAuctions = Array.from({ length: 6 }, (_, index) =>
      makeAuction(100 + index, `Cached auction ${index + 1}`, `${index + 1}h`)
    );
    sessionStorage.setItem(TOP_AUCTIONS_CACHE_KEY, JSON.stringify({
      cachedAt: Date.now(),
      response: { data: cachedAuctions, stats: { activeLots: 70 } }
    }));
    vi.spyOn(auctionApi, 'getTopAuctions').mockImplementation(() => new Promise(() => {}));

    render(<AuctionListings />);

    expect(screen.getAllByTestId('featured-auction-card')).toHaveLength(5);
    expect(screen.getByText('Cached auction 1')).toBeInTheDocument();
    expect(screen.queryByText('Big Agnes Copper Spur Tent')).not.toBeInTheDocument();
    expect(screen.getByText('70 Active Lots')).toBeInTheDocument();
  });

  it('silently replaces bootstrap cards after a successful background refresh', async () => {
    const request = deferred<{ data: FeaturedAuctionSummary[]; stats: { activeLots: number } }>();
    vi.spyOn(auctionApi, 'getTopAuctions').mockReturnValue(request.promise);

    render(<AuctionListings />);
    expect(screen.getByText('Big Agnes Copper Spur Tent')).toBeInTheDocument();
    expect(screen.getAllByTestId('featured-auction-card')).toHaveLength(5);

    const freshAuctions = Array.from({ length: 6 }, (_, index) =>
      makeAuction(200 + index, `Fresh auction ${index + 1}`, `${index + 1}m`)
    );
    await act(async () => {
      request.resolve({ data: freshAuctions, stats: { activeLots: 69 } });
      await request.promise;
    });

    expect(screen.getAllByTestId('featured-auction-card')).toHaveLength(5);
    expect(screen.getByText('Fresh auction 1')).toBeInTheDocument();
    expect(screen.queryByText('Big Agnes Copper Spur Tent')).not.toBeInTheDocument();
    expect(screen.getByText('69 Active Lots')).toBeInTheDocument();
    expect(screen.queryByLabelText('Loading featured auctions')).not.toBeInTheDocument();
  });

  it('keeps all five bootstrap cards when the background refresh fails', async () => {
    const request = deferred<never>();
    vi.spyOn(auctionApi, 'getTopAuctions').mockReturnValue(request.promise);

    render(<AuctionListings />);
    await act(async () => {
      request.reject(new Error('Core is waking up'));
      await request.promise.catch(() => undefined);
    });

    await waitFor(() => {
      expect(screen.getByText(/Live updates are temporarily unavailable/)).toBeInTheDocument();
    });
    expect(screen.getAllByTestId('featured-auction-card')).toHaveLength(5);
    expect(screen.getByText('Big Agnes Copper Spur Tent')).toBeInTheDocument();
  });

  it('routes a bootstrap card to its real database auction ID', () => {
    vi.spyOn(auctionApi, 'getTopAuctions').mockImplementation(() => new Promise(() => {}));

    render(<AuctionListings />);
    fireEvent.click(screen.getByText('Big Agnes Copper Spur Tent'));

    expect(window.REACT_APP_NAVIGATE).toHaveBeenCalledWith('/auction/45');
  });
});
