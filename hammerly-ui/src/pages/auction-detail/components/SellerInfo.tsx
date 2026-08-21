import { useState } from 'react';
import { sellerData } from "@/mocks/seller";

export default function SellerInfo() {

  const [showReviewsModal, setShowReviewsModal] = useState(false);

  return (
    <div className="bg-white rounded-xl shadow-lg p-6">
      {/* Seller Header */}
      <div className="flex items-start gap-4 mb-6">
        <div className="w-16 h-16 bg-[#8B2635] rounded-full flex items-center justify-center text-white text-xl font-bold">
          S
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1">
            <h3 className="font-semibold text-gray-900">{sellerData.name}</h3>
            <i className="ri-verified-badge-fill text-green-500"></i>
          </div>
          <p className="text-sm text-gray-600 mb-2">{sellerData.verification}</p>
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1">
              {[1,2,3,4,5].map((star) => (
                <i 
                  key={star}
                  className={`ri-star-${star <= Math.floor(sellerData.rating) ? 'fill' : 'line'} text-yellow-400 text-sm`}
                ></i>
              ))}
            </div>
            <span className="text-sm text-gray-600">
              {sellerData.rating} ({sellerData.totalSales} sales)
            </span>
          </div>
        </div>
      </div>

      {/* Seller Stats */}
      <div className="space-y-4 mb-6">
        <div className="flex justify-between items-center">
          <span className="text-sm text-gray-600">Shipping Time:</span>
          <span className="text-sm font-medium text-gray-900">{sellerData.shippingTime}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-sm text-gray-600">Location:</span>
          <span className="text-sm font-medium text-gray-900">{sellerData.location}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-sm text-gray-600">Member Since:</span>
          <span className="text-sm font-medium text-gray-900">{sellerData.joinDate}</span>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="space-y-3">
        <button className="w-full py-3 px-4 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer text-sm font-medium whitespace-nowrap">
          <div className="flex items-center justify-center gap-2">
            <i className="ri-message-2-line"></i>
            Contact Seller
          </div>
        </button>
      </div>

      {/* Recent Reviews */}
      <div className="mt-6 pt-6 border-t">
        <h4 className="font-medium text-gray-900 mb-4">Recent Reviews</h4>
        <div className="space-y-4">
          {sellerData.reviews.slice(0, 2).map((review) => (
            <div key={review.id} className="text-sm">
              <div className="flex items-center gap-2 mb-2">
                <div className="flex">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <i
                      key={star}
                      className={`ri-star-${star <= review.rating ? 'fill' : 'line'} text-yellow-400 text-xs`}
                    ></i>
                  ))}
                </div>
                <span className="text-gray-500">by {review.reviewer}</span>
              </div>
              <p className="text-gray-600">"{review.comment}"</p>
            </div>
          ))}
        </div>
        {sellerData.reviews.length > 2 && (
          <div className="text-center mt-4">
            <button
              onClick={() => setShowReviewsModal(true)}
              className="text-[#8B2635] hover:underline text-sm font-medium cursor-pointer"
            >
              View All Reviews ({sellerData.reviews.length})
            </button>
          </div>
        )}
      </div>

      {/* Reviews Modal */}
      {showReviewsModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl w-full max-w-2xl max-h-[80vh] flex flex-col">
            {/* Modal Header */}
            <div className="flex items-center justify-between p-6 border-b">
              <div>
                <h3 className="text-lg font-semibold text-gray-900">All Reviews</h3>
                <div className="flex items-center gap-2 mt-1">
                  <div className="flex">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <i
                        key={star}
                        className={`ri-star-${star <= Math.floor(sellerData.rating) ? 'fill' : 'line'} text-yellow-400 text-sm`}
                      ></i>
                    ))}
                  </div>
                  <span className="text-sm text-gray-600">
                    {sellerData.rating} average from {sellerData.reviews.length} reviews
                  </span>
                </div>
              </div>
              <button
                onClick={() => setShowReviewsModal(false)}
                className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 transition-colors cursor-pointer"
              >
                <i className="ri-close-line text-xl text-gray-500"></i>
              </button>
            </div>

            {/* Modal Body - Scrollable */}
            <div className="flex-1 overflow-y-auto p-6">
              <div className="space-y-6">
                {sellerData.reviews.map((review) => (
                  <div key={review.id} className="pb-6 border-b border-gray-100 last:border-b-0 last:pb-0">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-gray-100 rounded-full flex items-center justify-center">
                          <i className="ri-user-line text-gray-500"></i>
                        </div>
                        <div>
                          <p className="font-medium text-gray-900 text-sm">{review.reviewer}</p>
                          <p className="text-xs text-gray-500">{review.date}</p>
                        </div>
                      </div>
                      <div className="flex">
                        {[1, 2, 3, 4, 5].map((star) => (
                          <i
                            key={star}
                            className={`ri-star-${star <= review.rating ? 'fill' : 'line'} text-yellow-400 text-sm`}
                          ></i>
                        ))}
                      </div>
                    </div>
                    <p className="text-sm text-gray-600 ml-13">"{review.comment}"</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t">
              <button
                onClick={() => setShowReviewsModal(false)}
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