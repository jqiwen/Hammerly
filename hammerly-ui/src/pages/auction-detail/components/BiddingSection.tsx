import { useState, useEffect } from 'react';
import Button from '../../../components/base/Button';
import { auctionApi } from '@/api/auctions';

interface Auction {
  id: number;
  title: string;
  category: string;
  currentBid: number;
  timeRemaining: string;
  image: string;
  progress: number;
}

interface BiddingSectionProps {
  auction: Auction;
}

export default function BiddingSection({ auction }: BiddingSectionProps) {
  const [bidAmount, setBidAmount] = useState(auction.currentBid + 50);
  const [isWatching, setIsWatching] = useState(false);
  const [showBidModal, setShowBidModal] = useState(false);
  const [bidStatus, setBidStatus] = useState<'processing' | 'success' | 'error' | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    const checkWatchStatus = async () => {
      if (!localStorage.getItem('token')) {
        setIsWatching(false);
        return;
      }

      try {
        const response = await auctionApi.isAuctionWatched(auction.id);
        setIsWatching(Boolean(response?.isWatched));
      } catch {
        setIsWatching(false);
      }
    };

    void checkWatchStatus();
  }, [auction.id]);

  const handleToggleWatch = async () => {
    if (!localStorage.getItem('token')) {
      alert('Please log in to manage your watchlist.');
      return;
    }

    try {
      if (isWatching) {
        await auctionApi.unwatchAuction(auction.id);
        setIsWatching(false);
      } else {
        await auctionApi.watchAuction(auction.id);
        setIsWatching(true);
      }
      window.dispatchEvent(new Event('watchedItemsUpdated'));
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to update watchlist.');
    }
  };

  const handleBidIncrement = (amount: number) => {
    setBidAmount(prev => prev + amount);
  };

  const handlePlaceBid = () => {
    setShowBidModal(true);
    setBidStatus(null);
    setErrorMessage('');
  };

  const handleBid = async () => {
    setBidStatus('processing');
    setErrorMessage('');

    try {
      await auctionApi.placeBid(auction.id, bidAmount);
      setBidStatus('success');
    } catch (error) {
      setBidStatus('error');
      setErrorMessage(error instanceof Error ? error.message : 'Something went wrong while placing your bid. Please try again.');
    }

  };


  const handleCloseModal = () => {
    setShowBidModal(false);
    setBidStatus(null);
    setErrorMessage('');
  };

  const serviceFee = bidAmount * 0.01; 
  const shipping = 15;
  const totalAmount = bidAmount + serviceFee + shipping;

  return (
    <>
      <div className="bg-white rounded-xl shadow-lg p-6 sticky top-8">
        <div className="mb-6">
          <div className="flex items-center gap-2 mb-2">
            <span className="bg-green-100 text-green-800 text-xs px-2 py-1 rounded-full font-medium">
              {auction.category}
            </span>
            <span className="text-xs text-gray-500">{auction.timeRemaining} remaining</span>
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-4">{auction.title}</h1>
        </div>

        <div className="mb-6">
          <p className="text-sm text-gray-500 mb-1">Current Bid</p>
          <p className="text-4xl font-bold text-[#8B2635]">${auction.currentBid.toLocaleString()}</p>
          <p className="text-sm text-gray-600 mt-1">Minimum bid: ${(auction.currentBid + 25).toLocaleString()}</p>
        </div>


        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-2">Your Bid Amount</label>
          <div className="flex items-center gap-2 mb-3">
            <div className="flex-1 relative">
              <span className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-500">$</span>
              <input 
                type="number"
                value={bidAmount}
                onChange={(e) => setBidAmount(Number(e.target.value))}
                className="w-full pl-8 pr-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#8B2635] focus:border-transparent text-lg font-semibold"
                min={auction.currentBid + 25}
              />
            </div>
          </div>
          
          <div className="flex gap-2 mb-4">
            <button 
              onClick={() => handleBidIncrement(25)}
              className="px-3 py-1 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer"
            >
              +$25
            </button>
            <button 
              onClick={() => handleBidIncrement(50)}
              className="px-3 py-1 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer"
            >
              +$50
            </button>
            <button 
              onClick={() => handleBidIncrement(100)}
              className="px-3 py-1 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer"
            >
              +$100
            </button>
          </div>

          <Button 
            onClick={handlePlaceBid}
            className="w-full mb-3"
            size="lg"
          >
            Place Bid - ${bidAmount.toLocaleString()}
          </Button>

          <button 
            onClick={handleToggleWatch}
            className={`w-full py-3 px-4 rounded-lg border transition-colors cursor-pointer whitespace-nowrap ${
              isWatching 
                ? 'bg-[#8B2635]/10 border-[#8B2635] text-[#8B2635]' 
                : 'border-gray-300 text-gray-700 hover:bg-gray-50'
            }`}
          >
            <div className="flex items-center justify-center gap-2">
              <i className={`${isWatching ? 'ri-heart-fill text-[#8B2635]' : 'ri-heart-line'} text-lg`}></i>
              {isWatching ? 'Watching' : 'Watch This Item'}
            </div>
          </button>
        </div>

        <div className="border-t pt-4 text-sm text-gray-600 space-y-2">
          <div className="flex justify-between">
            <span>Estimated Shipping:</span>
            <span className="font-medium">$15</span>
          </div>
          <div className="flex justify-between">
            <span>Service Fee: </span>
            <span className="font-medium">1%</span>
          </div>
          <div className="flex justify-between">
            <span>Payment:</span>
            <span className="font-medium">Credit Card, PayPal</span>
          </div>
        </div>
      </div>

      {showBidModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div 
            className="absolute inset-0 bg-black/60 backdrop-blur-sm"
            onClick={bidStatus === 'processing' ? undefined : handleCloseModal}
          ></div>
          
          <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden">
            {bidStatus === 'success' ? (
              <div className="p-8 text-center">
                <div className="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6">
                  <i className="ri-check-line text-4xl text-green-600"></i>
                </div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Bid Placed Successfully!</h3>
                <p className="text-gray-600 mb-6">
                  Your bid of <span className="font-semibold text-[#8B2635]">${bidAmount.toLocaleString()}</span> has been placed on this item.
                </p>
                <div className="bg-gray-50 rounded-xl p-4 mb-6 text-left">
                  <div className="flex items-center gap-3 mb-3">
                    <img 
                      src={auction.image} 
                      alt={auction.title}
                      className="w-12 h-12 rounded-lg object-cover"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="font-medium text-gray-900 truncate text-sm">{auction.title}</p>
                      <p className="text-xs text-gray-500">{auction.timeRemaining} remaining</p>
                    </div>
                  </div>
                  <div className="text-sm text-gray-600">
                    <p>You&apos;ll be notified if you&apos;re outbid or when the auction ends.</p>
                  </div>
                </div>
                <button
                  onClick={handleCloseModal}
                  className="w-full bg-[#8B2635] text-white py-3 rounded-xl font-medium hover:bg-[#7A1F2B] transition-colors cursor-pointer whitespace-nowrap"
                >
                  Continue Browsing
                </button>
              </div>
            ) 
              : bidStatus === 'error' ? (
              <div className="p-8 text-center">
                <div className="w-20 h-20 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-6">
                  <i className="ri-close-line text-4xl text-red-600"></i>
                </div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Bid Failed</h3>
                <p className="text-gray-600 mb-6">
                  {errorMessage || 'Something went wrong while placing your bid. Please try again.'}
                </p>
                <div className="bg-gray-50 rounded-xl p-4 mb-6 text-left">
                  <div className="flex items-center gap-3">
                    <img 
                      src={auction.image} 
                      alt={auction.title}
                      className="w-12 h-12 rounded-lg object-cover"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="font-medium text-gray-900 truncate text-sm">{auction.title}</p>
                      <p className="text-xs text-gray-500">Current bid: ${auction.currentBid.toLocaleString()}</p>
                    </div>
                  </div>
                </div>
                <div className="flex gap-3">
                  <button
                    onClick={handleCloseModal}
                    className="flex-1 py-3 px-4 rounded-xl border border-gray-300 font-medium text-gray-700 hover:bg-gray-50 transition-colors cursor-pointer whitespace-nowrap"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={() => {
                      setBidStatus(null);
                      setErrorMessage('');
                    }}
                    className="flex-1 bg-[#8B2635] text-white py-3 px-4 rounded-xl font-medium hover:bg-[#7A1F2B] transition-colors cursor-pointer whitespace-nowrap"
                  >
                    Try Again
                  </button>
                </div>
              </div>
            )
            
            : (
              <>
                <div className="bg-gradient-to-r from-[#8B2635] to-[#A63446] p-6 text-white">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-xl font-bold">Confirm Your Bid</h3>
                    <button 
                      onClick={handleCloseModal}
                      className="w-8 h-8 flex items-center justify-center rounded-full bg-white/20 hover:bg-white/30 transition-colors cursor-pointer"
                      disabled={bidStatus === 'processing'}
                    >
                      <i className="ri-close-line text-lg"></i>
                    </button>
                  </div>
                  <div className="flex items-center gap-3">
                    <img 
                      src={auction.image} 
                      alt={auction.title}
                      className="w-16 h-16 rounded-lg object-cover border-2 border-white/30"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="font-medium truncate">{auction.title}</p>
                      <p className="text-white/80 text-sm">{auction.category}</p>
                    </div>
                  </div>
                </div>

                <div className="p-6">
                  <div className="text-center mb-6">
                    <p className="text-sm text-gray-500 mb-1">Your Bid Amount</p>
                    <p className="text-4xl font-bold text-[#8B2635]">${bidAmount.toLocaleString()}</p>
                  </div>

                  <div className="bg-gray-50 rounded-xl p-4 mb-6">
                    <h4 className="font-medium text-gray-900 mb-3 text-sm">Cost Breakdown</h4>
                    <div className="space-y-2 text-sm">
                      <div className="flex justify-between">
                        <span className="text-gray-600">Bid Amount</span>
                        <span className="font-medium">${bidAmount.toLocaleString()}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-gray-600">Service Fee (1%)</span>
                        <span className="font-medium">${serviceFee.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-gray-600">Shipping (Est.)</span>
                        <span className="font-medium">${shipping.toLocaleString()}</span>
                      </div>
                      <div className="border-t pt-2 mt-2 flex justify-between">
                        <span className="font-semibold text-gray-900">Total if You Win</span>
                        <span className="font-bold text-[#8B2635]">${totalAmount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-start gap-2 mb-6 text-xs text-gray-500">
                    <i className="ri-information-line mt-0.5"></i>
                    <p>By placing this bid, you agree to our <a href="#" className="text-[#8B2635] hover:underline cursor-pointer">Terms of Service</a> and commit to purchase if you win.</p>
                  </div>

                  <div className="flex gap-3">
                    <button
                      onClick={handleCloseModal}
                      disabled={bidStatus === 'processing'}
                      className="flex-1 py-3 px-4 rounded-xl border border-gray-300 font-medium text-gray-700 hover:bg-gray-50 transition-colors cursor-pointer whitespace-nowrap disabled:opacity-50"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={handleBid}
                      disabled={bidStatus === 'processing'}
                      className="flex-1 bg-[#8B2635] text-white py-3 px-4 rounded-xl font-medium hover:bg-[#7A1F2B] transition-colors cursor-pointer whitespace-nowrap disabled:opacity-70 flex items-center justify-center gap-2"
                    >
                      {bidStatus === 'processing' ? (
                        <>
                          <i className="ri-loader-4-line animate-spin"></i>
                          Processing...
                        </>
                      ) : (
                        <>
                          <i className="ri-gavel-line"></i>
                          Confirm Bid
                        </>
                      )}
                    </button>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </>
  );
}
