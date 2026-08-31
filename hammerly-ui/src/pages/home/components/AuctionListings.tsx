import { useEffect, useState } from 'react';
import { auctionApi } from '../../../api/auctions';
import AuctionCardSkeleton from '../../../components/feature/AuctionCardSkeleton';

// Helper function to convert timeRemaining string to minutes for sorting
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

export default function AuctionListings() {
  const cachedResponse = auctionApi.getCachedTopAuctions();
  const [auctions, setAuctions] = useState<any[]>(cachedResponse?.data || []);
  const [loading, setLoading] = useState(!cachedResponse);
  const [error, setError] = useState<string | null>(null);
  const [auctionStats, setAuctionStats] = useState<any>(cachedResponse?.stats || null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;
    const fetchTopAuctions = async () => {
      try {
        setLoading(true);
        const response = await auctionApi.getTopAuctions();
        if (!active) return;
        setAuctions(response.data || []);
        setAuctionStats(response.stats || null);
        setError(null);
      } catch  {
        if (!active) return;
        setError('Unable to load auctions.');
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
          {Array.from({ length: 6 }, (_, index) => (
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

  const endingSoonAuctions = [...auctions]
    .sort((a, b) => parseTimeToMinutes(a.timeRemaining) - parseTimeToMinutes(b.timeRemaining))
    .slice(0, 10);

  const handleAuctionClick = (auctionId: number) => {
    window.REACT_APP_NAVIGATE(`/auction/${auctionId}`);
  };

  return (
    <section id="auctions" className="py-20 bg-gray-50">
      <div className="max-w-7xl mx-auto px-6">
        {/* Section Header */}
        <div className="flex flex-col gap-6 sm:flex-row sm:justify-between sm:items-center mb-16">
          <div>
            <h2 className="text-4xl sm:text-5xl font-bold mb-4">
              <span className="text-black">FEATURED </span>
              <span className="text-[#8B2635]">AUCTIONS</span>
            </h2>
            <p className="text-xl text-gray-600">Discover unique items and place your bids</p>
          </div>
          <div className="self-start sm:self-auto bg-gray-800 text-white px-6 py-3 rounded-full">
            <span className="font-semibold">{auctionStats?.activeLots ?? auctions.length} Active Lots</span>
          </div>
        </div>

        {error && (
          <div className="mt-6 flex items-center justify-center gap-3 text-sm text-amber-800">
            <span>{error} Showing the latest session copy.</span>
            <button type="button" onClick={() => setReloadToken(value => value + 1)} className="underline">
              Try again
            </button>
          </div>
        )}

        {/* Auction Cards Grid - Top 10 ending soonest */}
        <div className="flex gap-6 overflow-x-auto pb-4">
          {endingSoonAuctions.map((auction) => (
            <div 
              key={auction.id}
              className="bg-white rounded-xl shadow-lg hover:shadow-xl transition-shadow duration-300 min-w-[320px] cursor-pointer"
              onClick={() => handleAuctionClick(auction.id)}
            >
              {/* Product Image */}
              <div className="p-3">
                <img 
                  src={auction.image}
                  alt={auction.title}
                  className="w-full h-64 object-cover object-top rounded-lg"
                />
              </div>

              {/* Card Content */}
              <div className="p-6">
                {/* Category and Title */}
                <div className="mb-4">
                  <p className="text-xs uppercase tracking-wider text-gray-500 mb-1">
                    {auction.category}
                  </p>
                  <h3 className="text-lg font-semibold text-gray-900 line-clamp-2 min-h-[3.5rem]">
                    {auction.title}
                  </h3>
                </div>

                {/* Current Bid */}
                <div className="flex justify-between items-end mb-4">
                  <div>
                    <p className="text-xs text-gray-500 uppercase tracking-wide">Current Bid</p>
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

        {/* View All Button */}
        <div className="text-center mt-12">
          <button 
            onClick={() => window.REACT_APP_NAVIGATE('/auctions')}
            className="bg-[#8B2635] text-white px-8 py-4 rounded-lg hover:bg-[#7A1F2B] transition-colors whitespace-nowrap cursor-pointer text-lg font-semibold"
          >
            View All Auctions
          </button>
        </div>
      </div>
    </section>
  );
}
