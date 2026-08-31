'use client';

import { useEffect, useState } from 'react';
import Header from '../../components/feature/Header';
import Footer from '../../components/feature/Footer';
import AuctionCard from './components/AuctionCard';
import { auctionApi } from '../../api/auctions';
import AuctionCardSkeleton from '../../components/feature/AuctionCardSkeleton';

interface Auction {
  id: number;
  title: string;
  category: string;
  currentBid: number;
  timeRemaining: string;
  image: string;
  progress: number;
  condition: string;
  totalBids: number;
  seller: string;
}

export default function Auctions() {
  const [auctions, setAuctions] = useState<Auction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [reloadToken, setReloadToken] = useState(0);
  const limit = 12;

  useEffect(() => {
    let active = true;
    const fetchAuctions = async () => {
      const cachedResponse = auctionApi.getCachedSearchAuctions(searchQuery, currentPage, limit);
      setError(null);
      if (cachedResponse) {
        setAuctions(cachedResponse.data || []);
        setTotal(cachedResponse.total || 0);
        setLoading(false);
      } else {
        setAuctions([]);
        setLoading(true);
      }
      try {
        const response = await auctionApi.searchAuctions(searchQuery, currentPage, limit);

        if (!active) return;
        setAuctions(response.data || []);
        setTotal(response.total || 0);
        setError(null);
      } catch (err) {
        if (!active) return;
        console.error('Failed to fetch auctions:', err);
        setError('Unable to load auctions.');
        if (!cachedResponse) {
          setAuctions([]);
          setTotal(0);
        }
      } finally {
        if (active) setLoading(false);
      }
    };

    fetchAuctions();
    return () => {
      active = false;
    };
  }, [currentPage, searchQuery, reloadToken]);


  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();

    setCurrentPage(1);
    setSearchQuery(inputValue.trim());
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Header />

      <main className="pt-24 py-8">
        <div className="max-w-7xl mx-auto px-6">

                    {/* Page Header */}
          <div className="mb-8">
            <nav className="mb-4">
              <ol className="flex items-center space-x-2 text-sm">
                <li><a href="/" className="text-gray-500 hover:text-gray-700 cursor-pointer">Home</a></li>
                <li className="text-gray-300">/</li>
                <li className="text-gray-900 font-medium">All Auctions</li>
              </ol>
            </nav>
            
            <div className="flex justify-between items-start mb-6">
              <div>
                <h1 className="text-4xl font-bold mb-2">
                  <span className="text-black">ALL </span>
                  <span className="text-[#8B2635]">AUCTIONS</span>
                </h1>
                {loading ? 'Loading...' : `${total} items available`}
              </div>
              
              {/* Search and View Toggle */}
              <div className="flex items-center gap-4">
                {/* Search Input */}
                <form onSubmit={handleSearch} className="relative flex items-center gap-2">
                  <div className="relative w-64">
                    <input
                      type="text"
                      placeholder="Search auctions..."
                      value={inputValue}
                      onChange={(e) => setInputValue(e.target.value)}
                      className="w-64 pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-[#8B2635] focus:border-[#8B2635] outline-none transition-all"
                    />
                    <div className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 flex items-center justify-center text-gray-400">
                      <i className="ri-search-line"></i>
                    </div>
                    {inputValue && (
                      <button
                        type="button"
                        onClick={() => setInputValue('')}
                        className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 flex items-center justify-center text-gray-400 hover:text-gray-600 cursor-pointer"
                      >
                        <i className="ri-close-line"></i>
                      </button>
                    )}
                  </div>
                  <button
                    type="submit"
                    className="ml-2 px-4 py-2 bg-[#8B2635] text-white rounded-lg hover:bg-[#a13a4a] transition-colors"
                  >
                    Search
                  </button>
                </form>
              </div>
            </div>
          </div>

          {/* Loading State */}
          {loading && (
            <div aria-label="Loading auctions" className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: 6 }, (_, index) => (
                <AuctionCardSkeleton key={index} />
              ))}
            </div>
          )}

          {/* Error State */}
          {error && (
            <div className="mb-8 flex items-center justify-between rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-red-700">
              <p>{error}{auctions.length > 0 ? ' Showing the latest session copy.' : ''}</p>
              <button
                type="button"
                onClick={() => setReloadToken(value => value + 1)}
                className="rounded border border-red-300 px-3 py-1 font-medium hover:bg-red-100"
              >
                Try again
              </button>
            </div>
          )}

          {/* Auction Cards */}
          {!loading && auctions.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {auctions.map((auction) => (
                <AuctionCard key={auction.id} auction={auction} viewType="grid" />
              ))}
            </div>
          ) : (
            !loading && !error && (
              <div className="text-center py-12">
                <p className="text-gray-600 text-lg">No auctions found</p>
              </div>
            )
          )}


          {/* Pagination */}
          {total > limit && (
            <div className="flex justify-center items-center gap-2 my-8">
              <button
                onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
                disabled={currentPage === 1}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              >
                <i className="ri-arrow-left-line"></i>
              </button>
              {Array.from({ length: Math.ceil(total / limit) }, (_, i) => i + 1).map(page => (
                <button
                  key={page}
                  onClick={() => setCurrentPage(page)}
                  className={`px-4 py-2 border rounded-lg transition-colors cursor-pointer ${
                    currentPage === page
                      ? 'bg-[#8B2635] text-white border-[#8B2635]'
                      : 'border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  {page}
                </button>
              ))}
              <button
                onClick={() => setCurrentPage(prev => Math.min(prev + 1, Math.ceil(total / limit)))}
                disabled={currentPage === Math.ceil(total / limit)}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              >
                <i className="ri-arrow-right-line"></i>
              </button>
            </div>
          )}


        </div>
      </main>

      <Footer />
    </div>
  );
}
