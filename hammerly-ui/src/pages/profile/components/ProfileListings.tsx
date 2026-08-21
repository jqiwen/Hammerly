import { useEffect, useState } from 'react';
import { getSellingList } from '@/api/profile';
import { auctionApi } from '@/api/auctions';
import { Listing } from '../../../mocks/myListing';
import CreateListingModal from './CreateListingModal';

export default function ProfileListings() {
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingListing, setEditingListing] = useState<Listing | null>(null);
  const [activeFilter, setActiveFilter] = useState<'all' | 'active' | 'ended'>('all');
  const [showEndConfirm, setShowEndConfirm] = useState(false);
  const [endingListingId, setEndingListingId] = useState<number | null>(null);
  const [isEndingAuction, setIsEndingAuction] = useState(false);
  const [endAuctionError, setEndAuctionError] = useState<string | null>(null);

  // Deliver modal state
  const [showDeliverModal, setShowDeliverModal] = useState(false);
  const [deliveringListing, setDeliveringListing] = useState<Listing | null>(null);
  const [deliverForm, setDeliverForm] = useState({ carrier: '', trackingNumber: '' });
  const [isDispatching, setIsDispatching] = useState(false);
  const [dispatchSuccess, setDispatchSuccess] = useState(false);
  const [dispatchedIds, setDispatchedIds] = useState<number[]>([]);

  const [listings, setListings] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const ITEMS_PER_PAGE = 4;

  const fetchListings = async () => {
    try {
      setLoading(true);
      const data = await getSellingList();
      setListings(data.auctions);
      setError(null);
    } catch (err: any) {
      setError(err?.message || 'Failed to load your listings.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchListings();
  }, []);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeFilter]);

  const filteredListings = listings.filter(listing => {
    if (activeFilter === 'all') return true;
    return listing.status === activeFilter;
  });
  const totalPages = Math.ceil(filteredListings.length / ITEMS_PER_PAGE);
  const paginatedListings = filteredListings.slice((currentPage - 1) * ITEMS_PER_PAGE, currentPage * ITEMS_PER_PAGE);

  const handleEditListing = (listing: Listing) => {
    setEditingListing(listing);
    setShowCreateModal(true);
  };

  const handleCloseModal = () => {
    setShowCreateModal(false);
    setEditingListing(null);
  };

  const handleListingSaved = async () => {
    await fetchListings();
  };

  const handleEndAuction = (listingId: number) => {
    setEndAuctionError(null);
    setEndingListingId(listingId);
    setShowEndConfirm(true);
  };

  const confirmEndAuction = async () => {
    if (!endingListingId) return;

    try {
      setIsEndingAuction(true);
      setEndAuctionError(null);
      await auctionApi.endAuction(endingListingId);

      setListings((prev) =>
        prev.map((listing) =>
          listing.id === endingListingId
            ? { ...listing, status: 'ended', timeLeft: 'Ended' }
            : listing
        )
      );

      setShowEndConfirm(false);
      setEndingListingId(null);
    } catch (err: any) {
      setEndAuctionError(err?.message || 'Failed to end auction.');
    } finally {
      setIsEndingAuction(false);
    }
  };

  // const handleOpenDeliver = (listing: Listing) => {
  //   setDeliveringListing(listing);
  //   setDeliverForm({ carrier: '', trackingNumber: '' });
  //   setDispatchSuccess(false);
  //   setShowDeliverModal(true);
  // };

  const handleCloseDeliverModal = () => {
    setShowDeliverModal(false);
    setDeliveringListing(null);
    setIsDispatching(false);
    setDispatchSuccess(false);
  };

  const handleConfirmDeliver = async () => {
    if (!deliverForm.carrier.trim() || !deliverForm.trackingNumber.trim()) return;
    setIsDispatching(true);
    await new Promise(resolve => setTimeout(resolve, 1200));
    if (deliveringListing) {
      setDispatchedIds(prev => [...prev, deliveringListing.id]);
    }
    setIsDispatching(false);
    setDispatchSuccess(true);
    setTimeout(() => {
      handleCloseDeliverModal();
    }, 1800);
  };

  const isFormValid = deliverForm.carrier.trim() !== '' && deliverForm.trackingNumber.trim() !== '';

  if (loading) {
    return (
      <div className="bg-white rounded-xl shadow-sm p-12 text-center">
        <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <i className="ri-loader-4-line animate-spin text-3xl text-gray-400 w-8 h-8 flex items-center justify-center"></i>
        </div>
        <h3 className="text-lg font-medium text-gray-900 mb-2">Loading listings</h3>
        <p className="text-gray-500">Fetching your current selling items from the backend.</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-xl shadow-sm p-12 text-center">
        <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <i className="ri-error-warning-line text-3xl text-red-500 w-8 h-8 flex items-center justify-center"></i>
        </div>
        <h3 className="text-lg font-medium text-gray-900 mb-2">Couldn&apos;t load listings</h3>
        <p className="text-gray-500 mb-6">{error}</p>
        <button
          onClick={() => void fetchListings()}
          className="inline-flex items-center gap-2 bg-[#8B2635] text-white px-6 py-3 rounded-lg font-medium hover:bg-[#7A1F2B] transition-all cursor-pointer whitespace-nowrap"
        >
          <i className="ri-refresh-line w-5 h-5 flex items-center justify-center"></i>
          Try Again
        </button>
      </div>
    );
  }


  return (
    <div className="space-y-6">

      {/* Filter Tabs */}
      <div className="flex items-center justify-between pt-4">
        <div className="bg-white rounded-xl shadow-sm p-2 inline-flex gap-1">
          {(['all', 'active', 'ended'] as const).map((filter) => (
            <button
              key={filter}
              onClick={() => setActiveFilter(filter)}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-all cursor-pointer whitespace-nowrap ${
                activeFilter === filter
                  ? 'bg-[#8B2635] text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              {filter.charAt(0).toUpperCase() + filter.slice(1)}
              {filter === 'all' && ` (${listings.length})`}
              {filter === 'active' && ` (${listings.filter(l => l.status === 'active').length})`}
              {filter === 'ended' && ` (${listings.filter(l => l.status === 'ended').length})`}
            </button>
          ))}
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="flex items-center gap-2 bg-[#8B2635] text-white px-6 py-3 rounded-lg font-medium hover:bg-[#7A1F2B] transition-all cursor-pointer whitespace-nowrap"
        >
          <i className="ri-add-line text-xl w-5 h-5 flex items-center justify-center"></i>
          Create New
        </button>
      </div>

      {/* Listings Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {paginatedListings.map((listing) => (
          <div key={listing.id} className="bg-white rounded-xl shadow-sm overflow-hidden hover:shadow-md transition-all">
            <div className="relative h-48">
              <img
                src={listing.image}
                alt={listing.title}
                className="w-full h-full object-cover object-top"
              />
              <div className={`absolute top-3 left-3 px-3 py-1 rounded-full text-xs font-medium ${
                listing.status === 'active' ? 'bg-emerald-500 text-white' :
                listing.status === 'ended' ? 'bg-gray-500 text-white' :
                'bg-amber-500 text-white'
              }`}>
                {listing.status.charAt(0).toUpperCase() + listing.status.slice(1)}
              </div>
              {listing.status === 'active' && (
                <div className="absolute top-3 right-3 bg-white/90 backdrop-blur-sm px-3 py-1 rounded-full">
                  <span className="text-xs font-medium text-gray-700">{listing.timeLeft} left</span>
                </div>
              )}
              {dispatchedIds.includes(listing.id) && (
                <div className="absolute top-3 right-3 bg-emerald-500 px-3 py-1 rounded-full flex items-center gap-1">
                  <i className="ri-truck-line text-white text-xs w-3 h-3 flex items-center justify-center"></i>
                  <span className="text-xs font-medium text-white">Dispatched</span>
                </div>
              )}
            </div>
            <div className="p-5">
              <h3 className="font-semibold text-gray-900 mb-3 line-clamp-1">{listing.title}</h3>
              
              <div className="grid grid-cols-2 gap-4 mb-4">
                <div>
                  <p className="text-xs text-gray-500">Current Bid</p>
                  <p className="text-lg font-bold text-[#8B2635]">
                    {listing.currentBid > 0 ? `$${listing.currentBid.toLocaleString()}` : '-'}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-gray-500">Starting Price</p>
                  <p className="text-lg font-bold text-gray-700">${listing.startingPrice.toLocaleString()}</p>
                </div>
              </div>

              

              <div className="flex items-center gap-2">
                {listing.status === 'draft' ? (
                  <>
                    <button className="flex-1 bg-[#8B2635] text-white py-2 rounded-lg text-sm font-medium hover:bg-[#7A1F2B] transition-all cursor-pointer whitespace-nowrap">
                      Publish
                    </button>
                    <button 
                      onClick={() => handleEditListing(listing)}
                      className="flex-1 border border-gray-200 text-gray-700 py-2 rounded-lg text-sm font-medium hover:bg-gray-50 transition-all cursor-pointer whitespace-nowrap"
                    >
                      Edit
                    </button>
                  </>
                ) : listing.status === 'active' ? (
                  <>
                    <button 
                      onClick={() => handleEndAuction(listing.id)}
                      className="flex-1 border border-red-200 text-red-600 py-2 rounded-lg text-sm font-medium hover:bg-red-50 transition-all cursor-pointer whitespace-nowrap"
                    >
                      End Auction
                    </button>
                  </>
                ) : dispatchedIds.includes(listing.id) ? (
                  <div className="flex-1 flex items-center justify-center gap-2 bg-emerald-50 text-emerald-700 py-2 rounded-lg text-sm font-medium whitespace-nowrap">
                    <i className="ri-checkbox-circle-line w-4 h-4 flex items-center justify-center"></i>
                    Dispatched ✓
                  </div>
                ) : (
                  // <button
                  //   onClick={() => handleOpenDeliver(listing)}
                  //   className="flex-1 bg-[#8B2635] text-white py-2 rounded-lg text-sm font-medium hover:bg-[#7A1F2B] transition-all cursor-pointer whitespace-nowrap"
                  // >
                  //   Update Delivery Info
                  // </button>
                  <></>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {filteredListings.length === 0 && (
        <div className="bg-white rounded-xl shadow-sm p-12 text-center">
          <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <i className="ri-store-2-line text-3xl text-gray-400 w-8 h-8 flex items-center justify-center"></i>
          </div>
          <h3 className="text-lg font-medium text-gray-900 mb-2">No listings found</h3>
          <p className="text-gray-500 mb-4">You don&apos;t have any {activeFilter !== 'all' ? activeFilter : ''} listings yet.</p>
          <button
            onClick={() => setShowCreateModal(true)}
            className="inline-flex items-center gap-2 bg-[#8B2635] text-white px-6 py-3 rounded-lg font-medium hover:bg-[#7A1F2B] transition-all cursor-pointer whitespace-nowrap"
          >
            <i className="ri-add-line w-5 h-5 flex items-center justify-center"></i>
            Create Your First Listing
          </button>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between bg-white rounded-xl shadow-sm px-5 py-3">
          <p className="text-sm text-gray-500">
            Showing <span className="font-medium text-gray-900">{(currentPage - 1) * ITEMS_PER_PAGE + 1}</span>–
            <span className="font-medium text-gray-900">{Math.min(currentPage * ITEMS_PER_PAGE, filteredListings.length)}</span>{' '}
            of <span className="font-medium text-gray-900">{filteredListings.length}</span> listings
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

      {/* Create/Edit Listing Modal */}
      {showCreateModal && (
        <CreateListingModal onClose={handleCloseModal} onSuccess={handleListingSaved} editingListing={editingListing} />
      )}

      {/* End Auction Confirmation Modal */}
      {showEndConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl p-8 max-w-md w-full">
            <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <i className="ri-alarm-warning-line text-3xl text-red-600 w-8 h-8 flex items-center justify-center"></i>
            </div>
            <h2 className="text-2xl font-serif font-bold text-gray-900 mb-2 text-center">End Auction Early?</h2>
            <p className="text-gray-500 text-center mb-6">
              Are you sure you want to end this auction now? This action cannot be undone. The current highest bidder will win the item.
            </p>
            <div className="flex items-center gap-3">
              <button
                onClick={() => {
                  setShowEndConfirm(false);
                  setEndingListingId(null);
                  setEndAuctionError(null);
                }}
                disabled={isEndingAuction}
                className="flex-1 border border-gray-200 text-gray-700 py-3 rounded-lg font-medium hover:bg-gray-50 transition-all cursor-pointer whitespace-nowrap"
              >
                Cancel
              </button>
              <button
                onClick={confirmEndAuction}
                disabled={isEndingAuction}
                className="flex-1 bg-red-600 text-white py-3 rounded-lg font-medium hover:bg-red-700 transition-all cursor-pointer whitespace-nowrap disabled:opacity-60"
              >
                {isEndingAuction ? 'Ending...' : 'End Auction'}
              </button>
            </div>
            {endAuctionError && (
              <p className="text-sm text-red-600 mt-3 text-center">{endAuctionError}</p>
            )}
          </div>
        </div>
      )}

      {/* Deliver Modal */}
      {showDeliverModal && deliveringListing && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden">
            {/* Modal Header */}
            <div className="border-b border-gray-100 px-6 py-4 flex items-center justify-between">
              <div>
                <h3 className="text-xl font-serif font-bold text-gray-900">Mark as Dispatched</h3>
                <p className="text-sm text-gray-500 mt-0.5">Enter shipping details for the buyer</p>
              </div>
              <button
                onClick={handleCloseDeliverModal}
                className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-gray-100 transition-colors cursor-pointer"
              >
                <i className="ri-close-line text-xl text-gray-500 w-5 h-5 flex items-center justify-center"></i>
              </button>
            </div>

            <div className="p-6">
              {!dispatchSuccess ? (
                <>
                  {/* Item Preview */}
                  <div className="flex gap-4 mb-6 p-4 bg-gray-50 rounded-xl">
                    <div className="w-20 h-16 rounded-lg overflow-hidden flex-shrink-0 bg-gray-200">
                      <img
                        src={deliveringListing.image}
                        alt={deliveringListing.title}
                        className="w-full h-full object-cover object-top"
                      />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-gray-900 text-sm line-clamp-2 mb-1">{deliveringListing.title}</p>
                      <p className="text-xs text-gray-500">Winning bid</p>
                      <p className="text-base font-bold text-[#8B2635]">${deliveringListing.currentBid.toLocaleString()}</p>
                    </div>
                  </div>

                  {/* Shipping Form */}
                  <div className="space-y-4 mb-6">
                    {/* Carrier */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1.5">
                        Carrier / Courier <span className="text-red-500">*</span>
                      </label>
                      <div className="relative">
                        <div className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 flex items-center justify-center text-gray-400">
                          <i className="ri-truck-line text-base"></i>
                        </div>
                        <input
                          type="text"
                          value={deliverForm.carrier}
                          onChange={(e) => setDeliverForm(prev => ({ ...prev, carrier: e.target.value }))}
                          placeholder="e.g. Hammerly Delivery"
                          className="w-full pl-9 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-[#8B2635] focus:border-transparent font-mono"
                        />
                      </div>
                    </div>

                    {/* Tracking Number */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1.5">
                        Tracking Number <span className="text-red-500">*</span>
                      </label>
                      <div className="relative">
                        <div className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 flex items-center justify-center text-gray-400">
                          <i className="ri-barcode-line text-base"></i>
                        </div>
                        <input
                          type="text"
                          value={deliverForm.trackingNumber}
                          onChange={(e) => setDeliverForm(prev => ({ ...prev, trackingNumber: e.target.value }))}
                          placeholder="e.g. TRK-UK-1234567"
                          className="w-full pl-9 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-[#8B2635] focus:border-transparent font-mono"
                        />
                      </div>
                    </div>

                  </div>

                  {/* Info Note */}
                  <div className="flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-lg p-3 mb-6">
                    <i className="ri-information-line text-amber-600 text-base w-4 h-4 flex items-center justify-center mt-0.5 flex-shrink-0"></i>
                    <p className="text-xs text-amber-800">
                      The buyer will be notified with these shipping details and their order status will update to <strong>In Transit</strong>.
                    </p>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex gap-3">
                    <button
                      onClick={handleCloseDeliverModal}
                      className="flex-1 py-3 border border-gray-300 text-gray-700 rounded-lg font-medium hover:bg-gray-50 transition-colors cursor-pointer whitespace-nowrap"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={handleConfirmDeliver}
                      disabled={!isFormValid || isDispatching}
                      className={`flex-1 py-3 rounded-lg font-medium transition-all whitespace-nowrap flex items-center justify-center gap-2 ${
                        !isFormValid
                          ? 'bg-gray-200 text-gray-400 cursor-not-allowed'
                          : isDispatching
                          ? 'bg-[#8B2635]/70 text-white cursor-wait'
                          : 'bg-[#8B2635] text-white hover:bg-[#7A1F2B] cursor-pointer'
                      }`}
                    >
                      {isDispatching ? (
                        <>
                          <i className="ri-loader-4-line animate-spin w-4 h-4 flex items-center justify-center"></i>
                          Confirming...
                        </>
                      ) : (
                        <>
                          <i className="ri-truck-line w-4 h-4 flex items-center justify-center"></i>
                          Confirm Dispatch
                        </>
                      )}
                    </button>
                  </div>
                </>
              ) : (
                /* Success State */
                <div className="py-10 text-center">
                  <div className="w-20 h-20 mx-auto mb-4 rounded-full bg-emerald-100 flex items-center justify-center">
                    <i className="ri-truck-line text-4xl text-emerald-600 w-10 h-10 flex items-center justify-center"></i>
                  </div>
                  <h4 className="text-xl font-serif font-bold text-gray-900 mb-2">Item Dispatched!</h4>
                  <p className="text-gray-500 text-sm">The buyer has been notified and their order is now marked as <strong>In Transit</strong>.</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
