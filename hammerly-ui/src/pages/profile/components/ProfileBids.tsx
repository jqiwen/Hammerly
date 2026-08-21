import { useEffect, useState } from 'react';
import { getBiddingList } from '@/api/profile';

export default function ProfileBids() {
  const [filter, setFilter] = useState('all');
  const [bids, setBids] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchBids = async () => {
      try {
        setLoading(true);
        const data = await getBiddingList();
        setBids(data.bids);
        setError(null);
      } catch (err: any) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchBids();
  }, []);

  const filteredBids = filter === 'all' ? bids : bids.filter((bid: any) => bid.status === filter);

  const [showWonDetailModal, setShowWonDetailModal] = useState(false);
  const [wonDetailOrder, setWonDetailOrder] = useState<any | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const ITEMS_PER_PAGE = 4;

  const totalPages = Math.ceil(filteredBids.length / ITEMS_PER_PAGE);
  const paginatedBids = filteredBids.slice((currentPage - 1) * ITEMS_PER_PAGE, currentPage * ITEMS_PER_PAGE);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'winning':
        return <span className="px-3 py-1 bg-emerald-100 text-emerald-700 rounded-full text-xs font-medium">Winning</span>;
      case 'outbid':
        return <span className="px-3 py-1 bg-amber-100 text-amber-700 rounded-full text-xs font-medium">Outbid</span>;
      case 'won':
        return <span className="px-3 py-1 bg-[#8B2635]/10 text-[#8B2635] rounded-full text-xs font-medium">Won</span>;
      case 'lost':
        return <span className="px-3 py-1 bg-gray-100 text-gray-600 rounded-full text-xs font-medium">Lost</span>;
      default:
        return null;
    }
  };

  const getWonOrderStepStatus = (orderStatus: string, step: number) => {
    const map: Record<string, number> = { confirmed: 1, transit: 2, delivered: 3 };
    const cur = map[orderStatus] ?? 0;
    if (step < cur) return 'completed';
    if (step === cur) return 'active';
    return 'pending';
  };

  // Helper function to get configuration for won order status
  const getWonOrderStatusConfig = (status: string) => {
    const statusConfig: Record<string, { icon: string; label: string; color: string }> = {
      confirmed: {
        icon: 'ri-check-line',
        label: 'Payment Confirmed',
        color: 'bg-green-100 text-green-700',
      },
      transit: {
        icon: 'ri-truck-line',
        label: 'In Transit',
        color: 'bg-blue-100 text-blue-700',
      },
      delivered: {
        icon: 'ri-home-smile-line',
        label: 'Delivered',
        color: 'bg-emerald-100 text-emerald-700',
      },
    };

    return statusConfig[status] || {
      icon: 'ri-question-line',
      label: 'Unknown Status',
      color: 'bg-gray-100 text-gray-600',
    };
  };

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <div className="space-y-6">

      {/* Filter Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-2 pt-4">
        {[
          { id: 'all', label: 'All Bids' },
          { id: 'winning', label: 'Winning' },
          { id: 'outbid', label: 'Outbid' },
          { id: 'won', label: 'Won' },
          { id: 'lost', label: 'Lost' },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setFilter(tab.id)}
            className={`px-5 py-2 rounded-full font-medium transition-all cursor-pointer whitespace-nowrap ${
              filter === tab.id
                ? 'bg-[#8B2635] text-white'
                : 'bg-white text-gray-600 hover:bg-gray-50'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* All Bids */}
      <>

        <div className="space-y-4">
          {paginatedBids.map(bid => (
            <div key={bid.id} className="bg-white rounded-xl shadow-sm p-5 hover:shadow-md transition-all">
              <div className="flex flex-col md:flex-row gap-5">
                <div className="w-full md:w-48 h-36 rounded-lg overflow-hidden flex-shrink-0">
                  <img src={bid.image} alt={bid.title} className="w-full h-full object-cover object-top" />
                </div>
                <div className="flex-1">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-lg font-medium text-gray-900 mb-2">{bid.title}</h3>
                      {getStatusBadge(bid.status)}
                    </div>
                    <div className="text-right">
                      <p className="text-sm text-gray-500">Time Left</p>
                      <p className={`font-bold ${bid.timeLeft === 'Ended' ? 'text-gray-400' : 'text-[#8B2635]'}`}>
                        {bid.timeLeft}
                      </p>
                    </div>
                  </div>
                  <div className={`grid gap-4 mt-4 ${bid.status === 'won' ? 'grid-cols-2' : 'grid-cols-3'}`}>
                    <div>
                      <p className="text-sm text-gray-500">Your Bid</p>
                      <p className="text-lg font-bold text-gray-900">${bid.yourBid.toLocaleString()}</p>
                    </div>
                    {bid.status !== 'won' && (
                      <div>
                        <p className="text-sm text-gray-500">Current Bid</p>
                        <p className="text-lg font-bold text-[#8B2635]">${bid.currentBid.toLocaleString()}</p>
                      </div>
                    )}
                    <div>
                      <p className="text-sm text-gray-500">Total Bids</p>
                      <p className="text-lg font-bold text-gray-900">{bid.totalBids}</p>
                    </div>
                  </div>
                  <div className="flex gap-3 mt-4">
                    {bid.status === 'outbid' && (
                      <button className="bg-[#8B2635] text-white px-5 py-2 rounded-lg text-sm font-medium hover:bg-[#6d1d28] transition-all cursor-pointer whitespace-nowrap">
                        Increase Bid
                      </button>
                    )}
                    {bid.status === 'won' && (
                      <button
                        onClick={() => {
                          setWonDetailOrder(bid);
                          setShowWonDetailModal(true);
                        }}
                        className="border border-gray-300 text-gray-700 px-5 py-2 rounded-lg text-sm font-medium hover:bg-gray-50 transition-all cursor-pointer whitespace-nowrap"
                      >
                        View Details
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {filteredBids.length === 0 && (
          <div className="bg-white rounded-2xl shadow-sm p-12 text-center">
            <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <i className="ri-hammer-line text-3xl text-gray-400 w-8 h-8 flex items-center justify-center"></i>
            </div>
            <h3 className="text-lg font-medium text-gray-900 mb-2">No bids found</h3>
            <p className="text-gray-500 mb-6">You don&apos;t have any bids in this category yet.</p>
            <a
              href="/auctions"
              className="inline-flex items-center gap-2 bg-[#8B2635] text-white px-6 py-3 rounded-lg font-medium hover:bg-[#7A1F2B] transition-all cursor-pointer whitespace-nowrap"
            >
              Browse Auctions
            </a>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between bg-white rounded-xl shadow-sm px-5 py-3">
            <p className="text-sm text-gray-500">
              Total <span className="font-medium text-gray-900">{filteredBids.length}</span> bids
            </p>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                disabled={currentPage === 1}
                className={`w-9 h-9 flex items-center justify-center rounded-lg transition-all ${
                  currentPage === 1
                    ? 'text-gray-300 cursor-not-allowed'
                    : 'text-gray-600 hover:bg-gray-100 cursor-pointer'
                }`}
              >
                <i className="ri-arrow-left-s-line text-lg w-5 h-5 flex items-center justify-center"></i>
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                <button
                  key={page}
                  onClick={() => setCurrentPage(page)}
                  className={`w-9 h-9 flex items-center justify-center rounded-lg text-sm font-medium transition-all cursor-pointer ${
                    currentPage === page
                      ? 'bg-[#8B2635] text-white'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  {page}
                </button>
              ))}
              <button
                onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                disabled={currentPage === totalPages}
                className={`w-9 h-9 flex items-center justify-center rounded-lg transition-all ${
                  currentPage === totalPages
                    ? 'text-gray-300 cursor-not-allowed'
                    : 'text-gray-600 hover:bg-gray-100 cursor-pointer'
                }`}
              >
                <i className="ri-arrow-right-s-line text-lg w-5 h-5 flex items-center justify-center"></i>
              </button>
            </div>
          </div>
        )}
      </>

      {/* Won Order Detail Modal */}
      {showWonDetailModal && wonDetailOrder && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            {/* Modal Header */}
            <div className="sticky top-0 bg-white border-b border-gray-100 px-6 py-4 flex items-center justify-between z-10">
              <h3 className="text-lg font-bold text-gray-900">Order Details</h3>
              <button
                onClick={() => setShowWonDetailModal(false)}
                className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-gray-100 cursor-pointer transition-colors"
              >
                <i className="ri-close-line text-xl text-gray-500 w-5 h-5 flex items-center justify-center"></i>
              </button>
            </div>

            <div className="p-6 space-y-6">
              {/* Order ID + Date Row */}
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-xs text-gray-400 mb-0.5">Order ID</p>
                  <p className="text-base font-bold text-gray-900">#{wonDetailOrder.id}</p>
                </div>
                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <p className="text-xs text-gray-400 mb-0.5">Order Date</p>
                    <p className="text-base font-bold text-gray-900">{wonDetailOrder.orderDate}
                     
                    </p>
                  </div>
                </div>
              </div>

              {/* Delivery Progress Stepper */}
              <div className="relative flex items-start justify-between">
                <div className="absolute top-6 left-[calc(16.67%)] right-[calc(16.67%)] h-0.5 bg-gray-200 z-0">
                  <div
                    className="h-full bg-[#8B2635] transition-all duration-500"
                    style={{
                      width:
                        wonDetailOrder.deliverStatus === 'confirmed'
                          ? '0%'
                          : wonDetailOrder.deliverStatus === 'transit'
                          ? '50%'
                          : '100%',
                    }}
                  />
                </div>

                {[
                  {
                    step: 1,
                    icon: 'ri-check-line',
                    label: 'Payment Confirmed',
                  },
                  {
                    step: 2,
                    icon: 'ri-truck-line',
                    label: 'In Transit',
                  },
                  {
                    step: 3,
                    icon: 'ri-home-smile-line',
                    label: 'Delivered',
                  },
                ].map(({ step, icon, label}) => {
                  const s = getWonOrderStepStatus(wonDetailOrder.deliverStatus, step);
                  return (
                    <div key={step} className="flex flex-col items-center flex-1 relative z-10">
                      <div
                        className={`w-12 h-12 rounded-full flex items-center justify-center transition-all ${
                          s === 'pending' ? 'bg-gray-200 text-gray-400' : 'bg-[#8B2635] text-white'
                        }`}
                      >
                        <i className={`${icon} text-xl w-5 h-5 flex items-center justify-center`}></i>
                      </div>
                      <p
                        className={`text-sm font-medium mt-2 text-center ${
                          s === 'active'
                            ? 'text-[#8B2635]'
                            : s === 'completed'
                            ? 'text-gray-700'
                            : 'text-gray-400'
                        }`}
                      >
                        {label}
                      </p>
                    </div>
                  );
                })}
              </div>

              {/* Product Card */}
              <div className="border border-gray-100 rounded-xl p-4">
                <div className="flex gap-5">
                  <div className="w-36 h-28 flex-shrink-0 rounded-lg overflow-hidden bg-gray-100">
                    <img src={wonDetailOrder.image} alt={wonDetailOrder.title} className="w-full h-full object-cover object-top" />
                  </div>
                  <div className="flex-1 flex flex-col justify-between">
                    <div>
                      <div className="flex items-start justify-between gap-3">
                        <h4 className="text-base font-bold text-gray-900 leading-snug">{wonDetailOrder.title}</h4>
                        <div className="text-right flex-shrink-0">
                          <p className="text-xs text-gray-400">Purchase Price</p>
                          <p className="text-xl font-bold text-[#8B2635]">
                            ${wonDetailOrder.purchasePrice.toLocaleString()}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2 mt-2">
                        <div className="w-7 h-7 rounded-full overflow-hidden bg-gray-100 flex-shrink-0">
                          <img src={wonDetailOrder.sellerAvatar} alt={wonDetailOrder.sellerName} className="w-full h-full object-cover object-top" />
                        </div>
                        <span className="text-sm text-gray-600">{wonDetailOrder.sellerName}</span>
                      </div>
                      <div className="mt-3">
                        <span
                          className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold ${getWonOrderStatusConfig(wonDetailOrder.deliverStatus).color}`}
                        >
                          <i className={`${getWonOrderStatusConfig(wonDetailOrder.deliverStatus).icon} w-3.5 h-3.5 flex items-center justify-center`}></i>
                          {getWonOrderStatusConfig(wonDetailOrder.deliverStatus).label}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                {wonDetailOrder.deliverStatus === 'transit' && wonDetailOrder.trackingNumber && (
                  <div className="mt-4 bg-amber-50 border border-amber-200 rounded-lg p-3">
                    <div className="flex items-start gap-2">
                      <i className="ri-map-pin-line text-amber-600 text-base w-4 h-4 flex items-center justify-center mt-0.5"></i>
                      <div>
                        <p className="text-sm font-semibold text-amber-900">Tracking Information</p>
                        <p className="text-xs text-amber-800 mt-0.5">
                          {wonDetailOrder.carrier} · {wonDetailOrder.trackingNumber}
                        </p>
                      </div>
                    </div>
                  </div>
                )}

                {wonDetailOrder.deliverStatus === 'delivered' && (
                  <div className="mt-4 bg-green-50 border border-green-200 rounded-lg p-3">
                    <div className="flex items-center gap-2">
                      <i className="ri-checkbox-circle-line text-green-600 text-base w-4 h-4 flex items-center justify-center"></i>
                      <div>
                        <p className="text-sm font-semibold text-green-900">Successfully Delivered</p>
                      </div>
                    </div>
                  </div>
                )}

                <div className="flex gap-3 mt-4">
                  {wonDetailOrder.deliverStatus === 'transit' && (
                    <button className="flex items-center gap-2 px-5 py-2.5 bg-amber-600 text-white rounded-lg text-sm font-medium hover:bg-amber-700 transition-colors cursor-pointer whitespace-nowrap">
                      <i className="ri-map-pin-line w-4 h-4 flex items-center justify-center"></i>
                      Track Package
                    </button>
                  )}
                  {wonDetailOrder.deliverStatus === 'confirmed' && (
                    <button className="flex items-center gap-2 px-5 py-2.5 bg-[#8B2635] text-white rounded-lg text-sm font-medium hover:bg-[#6d1d28] transition-colors cursor-pointer whitespace-nowrap">
                      <i className="ri-message-3-line w-4 h-4 flex items-center justify-center"></i>
                      Contact Seller
                    </button>
                  )}
                  {wonDetailOrder.deliverStatus === 'delivered' && (
                    <button className="flex items-center gap-2 px-5 py-2.5 bg-[#8B2635] text-white rounded-lg text-sm font-medium hover:bg-[#6d1d28] transition-colors cursor-pointer whitespace-nowrap">
                      <i className="ri-star-line w-4 h-4 flex items-center justify-center"></i>
                      Write a Review
                    </button>
                  )}
                  <button
                    onClick={() => setShowWonDetailModal(false)}
                    className="px-5 py-2.5 border border-gray-300 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-50 transition-colors cursor-pointer whitespace-nowrap"
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
