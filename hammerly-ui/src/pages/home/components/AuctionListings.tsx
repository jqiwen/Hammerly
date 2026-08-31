import { useEffect, useState } from 'react';
import { auctionApi } from '../../../api/auctions';
import AuctionCardSkeleton from '../../../components/feature/AuctionCardSkeleton';
import {
  FEATURED_AUCTIONS_BOOTSTRAP,
  type FeaturedAuctionSummary
} from '../../../bootstrap/featuredAuctions';

interface AuctionStats {
  activeLots?: number;
}

interface TopAuctionsResponse {
  data?: FeaturedAuctionSummary[];
  stats?: AuctionStats;
}

const FEATURED_AUCTION_COUNT = 5;

// Preserve the homepage's existing "ending soon" ranking rule.
const parseTimeToMinutes = (timeStr: string): number => {
  let totalMinutes = 0;
  const dayMatch = timeStr.match(/(\d+)d/);
  const hourMatch = timeStr.match(/(\d+)h/);
  const minMatch = timeStr.match(/(\d+)m/);

  if (dayMatch) totalMinutes += parseInt(dayMatch[1]) * 24 * 60;
  if (hourMatch) totalMinutes += parseInt(hourMatch[1]) * 60;
  if (minMatch) totalMinutes += parseInt(minMatch[1]);
  return totalMinutes;
};

const rankFeaturedAuctions = (auctions: FeaturedAuctionSummary[]) =>
  [...auctions]
    .sort((first, second) =>
      parseTimeToMinutes(first.timeRemaining) - parseTimeToMinutes(second.timeRemaining)
    )
    .slice(0, FEATURED_AUCTION_COUNT);

const getInitialFeaturedAuctions = () => {
  const cachedResponse = auctionApi.getCachedTopAuctions() as TopAuctionsResponse | null;
  const cachedAuctions = cachedResponse?.data;
  const rankedCachedAuctions = cachedAuctions?.length
    ? rankFeaturedAuctions(cachedAuctions)
    : [];
  const cachedIds = new Set(rankedCachedAuctions.map(auction => auction.id));
  const bootstrapFallbacks = rankFeaturedAuctions(FEATURED_AUCTIONS_BOOTSTRAP)
    .filter(auction => !cachedIds.has(auction.id));

  return {
    auctions: [...rankedCachedAuctions, ...bootstrapFallbacks].slice(0, FEATURED_AUCTION_COUNT),
    stats: cachedResponse?.stats ?? null
  };
};

export default function AuctionListings() {
  const [initialState] = useState(getInitialFeaturedAuctions);
  const [auctions, setAuctions] = useState<FeaturedAuctionSummary[]>(initialState.auctions);
  const [loading, setLoading] = useState(initialState.auctions.length === 0);
  const [error, setError] = useState<string | null>(null);
  const [auctionStats, setAuctionStats] = useState<AuctionStats | null>(initialState.stats);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;

    const fetchTopAuctions = async () => {
      try {
        const response = await auctionApi.getTopAuctions() as TopAuctionsResponse;
        if (!active) return;

        const refreshedAuctions = rankFeaturedAuctions(response.data ?? []);
        if (refreshedAuctions.length === FEATURED_AUCTION_COUNT) setAuctions(refreshedAuctions);
        setAuctionStats(response.stats ?? null);
        setError(null);
      } catch {
        if (!active) return;
        setError('Live updates are temporarily unavailable.');
      } finally {
        if (active) setLoading(false);
      }
    };

    fetchTopAuctions();
    return () => {
      active = false;
    };
  }, [reloadToken]);

  if (loading && auctions.length === 0) {
    return (
      <section aria-label="Loading featured auctions" className="bg-gray-50 py-20">
        <div className="mx-auto flex max-w-7xl gap-6 overflow-hidden px-6">
          {Array.from({ length: FEATURED_AUCTION_COUNT }, (_, index) => (
            <AuctionCardSkeleton key={index} compact />
          ))}
        </div>
      </section>
    );
  }

  if (error && auctions.length === 0) {
    return (
      <section className="bg-gray-50 py-20 text-center">
        <p className="mb-4 text-gray-700">{error}</p>
        <button
          type="button"
          onClick={() => setReloadToken(value => value + 1)}
          className="rounded-lg bg-[#8B2635] px-5 py-2 text-white hover:bg-[#7A1F2B]"
        >
          Try again
        </button>
      </section>
    );
  }

  const handleAuctionClick = (auctionId: number) => {
    window.REACT_APP_NAVIGATE(`/auction/${auctionId}`);
  };

  return (
    <section id="auctions" className="bg-gray-50 py-20">
      <div className="mx-auto max-w-7xl px-6">
        <div className="mb-16 flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="mb-4 text-4xl font-bold sm:text-5xl">
              <span className="text-black">FEATURED </span>
              <span className="text-[#8B2635]">AUCTIONS</span>
            </h2>
            <p className="text-xl text-gray-600">Discover unique items and place your bids</p>
          </div>
          <div className="self-start rounded-full bg-gray-800 px-6 py-3 text-white sm:self-auto">
            <span className="font-semibold">{auctionStats?.activeLots ?? auctions.length} Active Lots</span>
          </div>
        </div>

        {error && (
          <div className="mb-6 flex items-center justify-center gap-3 text-sm text-amber-800">
            <span>{error} Showing the latest available auctions.</span>
            <button type="button" onClick={() => setReloadToken(value => value + 1)} className="underline">
              Try again
            </button>
          </div>
        )}

        <div className="flex gap-6 overflow-x-auto pb-4">
          {auctions.map((auction, index) => (
            <div
              key={auction.id}
              data-testid="featured-auction-card"
              data-auction-id={auction.id}
              className="min-w-[320px] cursor-pointer rounded-xl bg-white shadow-lg transition-shadow duration-300 hover:shadow-xl"
              onClick={() => handleAuctionClick(auction.id)}
            >
              <div className="p-3">
                <img
                  src={auction.image}
                  alt={auction.title}
                  width={960}
                  height={720}
                  loading="eager"
                  fetchPriority={index < 2 ? 'high' : 'auto'}
                  decoding="async"
                  className="h-64 w-full rounded-lg object-cover object-top"
                />
              </div>

              <div className="p-6">
                <div className="mb-4">
                  <p className="mb-1 text-xs uppercase tracking-wider text-gray-500">
                    {auction.category}
                  </p>
                  <h3 className="line-clamp-2 min-h-[3.5rem] text-lg font-semibold text-gray-900">
                    {auction.title}
                  </h3>
                </div>

                <div className="mb-4 flex items-end justify-between">
                  <div>
                    <p className="text-xs uppercase tracking-wide text-gray-500">Current Bid</p>
                    <p className="text-2xl font-bold text-black">
                      ${auction.currentBid.toLocaleString()}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-gray-500">{auction.timeRemaining}</p>
                    <p className="text-sm text-gray-700">remaining</p>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-12 text-center">
          <button
            onClick={() => window.REACT_APP_NAVIGATE('/auctions')}
            className="cursor-pointer whitespace-nowrap rounded-lg bg-[#8B2635] px-8 py-4 text-lg font-semibold text-white transition-colors hover:bg-[#7A1F2B]"
          >
            View All Auctions
          </button>
        </div>
      </div>
    </section>
  );
}
