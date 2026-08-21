import { useState } from 'react';
import { Auction } from '@/mocks/auctions';

interface ItemDetailsProps {
  auction: Auction;
}

export default function ItemDetails({ auction }: ItemDetailsProps) {
  const [activeTab, setActiveTab] = useState<'description' | 'history'>('description');
  const [showBidHistoryModal, setShowBidHistoryModal] = useState(false);

  const tabs = [
    { id: 'description', label: 'Description', icon: 'ri-file-text-line' },
    { id: 'history', label: 'Bid History', icon: 'ri-history-line' }
  ] as const;


  return (
    <div className="bg-white rounded-xl shadow-lg overflow-hidden">
      {/* Tab Navigation */}
      <div className="border-b border-gray-200">
        <nav className="flex">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex-1 flex items-center justify-center gap-2 px-6 py-4 text-sm font-medium border-b-2 transition-colors cursor-pointer whitespace-nowrap ${
                activeTab === tab.id
                  ? 'border-[#8B2635] text-[#8B2635] bg-red-50'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:bg-gray-50'
              }`}
            >
              <i className={`${tab.icon} text-lg`}></i>
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab Content */}
      <div className="p-6">
        {activeTab === 'description' && (
          <div className="space-y-4">
            <p className="text-gray-700">{auction.description}</p>
          </div>
        )}


        {activeTab === 'history' && (
          <div className="space-y-4">
            <h3 className="text-lg font-semibold text-gray-900">Bidding Activity</h3>
            <div className="space-y-3">
              {auction.bidHistory.slice(0, 5).map((bid, index) => (
                <div key={index} className="flex items-center justify-between py-3 border-b border-gray-100 last:border-b-0">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 bg-gray-100 rounded-full flex items-center justify-center">
                      <i className="ri-user-line text-gray-500"></i>
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">{bid.bidder}</p>
                      <p className="text-sm text-gray-500">{bid.time}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-[#8B2635]">${bid.amount.toLocaleString()}</p>
                    {index === 0 && (
                      <span className="text-xs bg-green-100 text-green-800 px-2 py-1 rounded-full">
                        Current High
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
            {auction.bidHistory.length > 5 && (
              <div className="text-center pt-4">
                <button 
                  onClick={() => setShowBidHistoryModal(true)}
                  className="text-[#8B2635] hover:underline text-sm font-medium cursor-pointer"
                >
                  View Complete Bid History ({auction.bidHistory.length})
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Bid History Modal */}
      {showBidHistoryModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl w-full max-w-2xl max-h-[80vh] flex flex-col">
            {/* Modal Header */}
            <div className="flex items-center justify-between p-6 border-b">
              <div>
                <h3 className="text-lg font-semibold text-gray-900">Complete Bid History</h3>
                <p className="text-sm text-gray-600 mt-1">
                  {auction.bidHistory.length} total bids • Current high: ${auction.currentBid.toLocaleString()}
                </p>
              </div>
              <button
                onClick={() => setShowBidHistoryModal(false)}
                className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 transition-colors cursor-pointer"
              >
                <i className="ri-close-line text-xl text-gray-500"></i>
              </button>
            </div>

            {/* Modal Body - Scrollable */}
            <div className="flex-1 overflow-y-auto p-6">
              <div className="space-y-4">
                {auction.bidHistory.map((bid, index) => (
                  <div key={index} className="flex items-center justify-between py-3 border-b border-gray-100 last:border-b-0 last:pb-0">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-gray-100 rounded-full flex items-center justify-center">
                        <i className="ri-user-line text-gray-500"></i>
                      </div>
                      <div>
                        <p className="font-medium text-gray-900 text-sm">{bid.bidder}</p>
                        <p className="text-xs text-gray-500">{bid.time}</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="font-semibold text-[#8B2635]">${bid.amount.toLocaleString()}</p>
                      {index === 0 && (
                        <span className="text-xs bg-green-100 text-green-800 px-2 py-1 rounded-full">
                          Current High
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t">
              <button
                onClick={() => setShowBidHistoryModal(false)}
                className="w-full py-3 bg-[#8B2635] text-white rounded-lg hover:bg-[#7A2230] transition-colors cursor-pointer text-sm font-medium whitespace-nowrap"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}