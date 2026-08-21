import { useState, useEffect } from 'react';
import Header from '../../components/feature/Header';
import Footer from '../../components/feature/Footer';
import { auctionApi } from '@/api/auctions';
import { useNavigate } from 'react-router-dom';

interface WatchedItem {
  id: number;
  title: string;
  image: string;
  currentBid: number;
  timeLeft?: string;
  endTime?: string;
  watching?: number;
}

export default function Cart() {
  const navigate = useNavigate();
  const [watchedItems, setWatchedItems] = useState<WatchedItem[]>([]);

  useEffect(() => {
    loadWatchedItems();
    
    // Listen for updates to watchlist
    const handleUpdate = () => {
      loadWatchedItems();
    };
    
    window.addEventListener('watchedItemsUpdated', handleUpdate);
    
    return () => {
      window.removeEventListener('watchedItemsUpdated', handleUpdate);
    };
  }, []);

  const loadWatchedItems = async () => {
    if (!localStorage.getItem('token')) {
      setWatchedItems([]);
      return;
    }

    try {
      const response = await auctionApi.getWatchlist();
      setWatchedItems(Array.isArray(response?.data) ? response.data : []);
    } catch (e) {
      console.error('Error fetching watched items:', e);
      setWatchedItems([]);
    }
  };

  const removeItem = async (id: number) => {
    try {
      await auctionApi.unwatchAuction(id);
      setWatchedItems(prev => prev.filter(item => item.id !== id));
      window.dispatchEvent(new Event('watchedItemsUpdated'));
    } catch (e) {
      console.error('Error removing watched item:', e);
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <Header />
      
      <main className="flex-grow pt-24 pb-16">
        <div className="max-w-7xl mx-auto px-6">
          <div className="mb-8 mt-4">
            <h1 className="text-4xl font-bold text-gray-900 mb-2">Your Watchlist</h1>
            <p className="text-gray-600">Track items you're interested in</p>
          </div>

          {watchedItems.length === 0 ? (
            <div className="bg-white rounded-xl shadow-sm p-12 text-center">
              <div className="w-24 h-24 flex items-center justify-center mx-auto mb-6 bg-gray-100 rounded-full">
                <i className="ri-heart-line text-5xl text-gray-400"></i>
              </div>
              <h2 className="text-2xl font-bold text-gray-900 mb-3">Your watchlist is empty</h2>
              <p className="text-gray-600 mb-8 max-w-md mx-auto">
                Start watching items you're interested in. You can add items to your watchlist from any auction detail page.
              </p>
              <button
                onClick={() => navigate('/auctions')}
                className="bg-[#8B2635] text-white px-8 py-3 rounded-lg hover:bg-[#7A1F2B] transition-colors whitespace-nowrap cursor-pointer font-semibold"
              >
                Browse Auctions
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
              {/* Watched Items List */}
              <div className="lg:col-span-2 space-y-4">
                {watchedItems.map((item) => (
                  <div key={item.id} className="bg-white rounded-xl shadow-sm p-6 hover:shadow-md transition-shadow">
                    <div className="flex gap-6">
                      {/* Item Image */}
                      <div className="w-32 h-32 flex-shrink-0">
                        <img
                          src={item.image}
                          alt={item.title}
                          className="w-full h-full object-cover object-top rounded-lg cursor-pointer"
                          onClick={() => navigate(`/auction/${item.id}`)}
                        />
                      </div>

                      {/* Item Details */}
                      <div className="flex-grow">
                        <div className="flex justify-between items-start mb-2">
                          <div>
                            <h3 
                              className="text-lg font-semibold text-gray-900 hover:text-[#8B2635] cursor-pointer mb-2"
                              onClick={() => navigate(`/auction/${item.id}`)}
                            >
                              {item.title}
                            </h3>
                          </div>
                          <button
                            onClick={() => removeItem(item.id)}
                            className="text-gray-400 hover:text-red-600 transition-colors cursor-pointer"
                            aria-label="Remove from watchlist"
                          >
                            <i className="ri-close-line text-2xl"></i>
                          </button>
                        </div>

                        <div className="flex items-center gap-4 mb-3">
                          <div className="flex items-center gap-2 text-sm text-gray-600">
                            <i className="ri-eye-line"></i>
                            <span>{item.watching || 0} watching</span>
                          </div>
                        </div>

                        <div className="flex justify-between items-end">
                          <div>
                            <p className="text-xs text-gray-500 uppercase tracking-wide mb-1">Current Bid</p>
                            <p className="text-2xl font-bold text-[#8B2635]">
                              ${item.currentBid.toLocaleString()}
                            </p>
                          </div>
                          <div className="text-right">
                            <p className="text-xs text-gray-500 mb-1">Time Left</p>
                            <p className="text-sm font-semibold text-gray-900">{item.timeLeft || (item.endTime ? new Date(item.endTime).toLocaleString() : '-')}</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {/* Summary Sidebar */}
              <div className="lg:col-span-1">
                <div className="bg-white rounded-xl shadow-sm p-6 sticky top-24">
                  <h2 className="text-xl font-bold text-gray-900 mb-6">Watchlist Summary</h2>
                  
                  <div className="space-y-4 mb-6 pb-6 border-b">
                    <div className="flex justify-between items-center">
                      <span className="text-gray-600">Total Items</span>
                      <span className="text-xl font-bold text-gray-900">{watchedItems.length}</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-gray-600">Total Value</span>
                      <span className="text-xl font-bold text-[#8B2635]">
                        ${watchedItems.reduce((sum, item) => sum + item.currentBid, 0).toLocaleString()}
                      </span>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <button
                      onClick={() => navigate('/auctions')}
                      className="w-full bg-[#8B2635] text-white py-3 rounded-lg hover:bg-[#7A1F2B] transition-colors whitespace-nowrap cursor-pointer font-semibold"
                    >
                      Browse More Auctions
                    </button>
                  </div>

                  <div className="mt-6 pt-6 border-t">
                    <div className="flex items-start gap-3 text-sm text-gray-600">
                      <i className="ri-information-line text-lg text-[#8B2635] flex-shrink-0"></i>
                      <p>
                        Items in your watchlist are tracked for your convenience. Prices and availability may change.
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>

      <Footer />
    </div>
  );
}