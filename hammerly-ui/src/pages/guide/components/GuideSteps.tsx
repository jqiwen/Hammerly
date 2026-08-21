export default function GuideSteps() {
  const buyerSteps = [
    {
      number: 1,
      title: 'Create Your Account',
      description: 'Sign up with your email and complete your profile. Add payment methods and set up notifications to stay updated on your bids.',
      icon: 'ri-user-add-line',
    },
    {
      number: 2,
      title: 'Browse & Search',
      description: 'Explore thousands of auctions across various categories.',
      icon: 'ri-search-line',
      tips: ['Watch your favorite searches', 'Check seller ratings and reviews before bidding']
    },
    {
      number: 3,
      title: 'Place Your Bid',
      description: 'Found something you love? Place a bid or set your maximum bid amount. Our system will automatically bid for you up to your limit.',
      icon: 'ri-auction-line',
      tips: ['Set a maximum budget', 'Bid in the final minutes for best results']
    },
    {
      number: 4,
      title: 'Win & Pay',
      description: 'If you win, you\'ll receive a notification immediately. Complete payment within 48 hours using your preferred payment method.',
      icon: 'ri-trophy-line',
      tips: ['Pay promptly to maintain good standing', 'Review item details before paying', 'Contact seller if you have questions']
    },
    {
      number: 5,
      title: 'Receive Your Item',
      description: 'Track your shipment and inspect your item upon arrival. Leave feedback for the seller to help the community.',
      icon: 'ri-gift-line',
      tips: ['Inspect items immediately', 'Document any issues with photos', 'Leave honest feedback']
    }
  ];

  const sellerSteps = [
    {
      number: 1,
      title: 'Prepare Your Item',
      description: 'Clean and photograph your item from multiple angles. Gather any documentation, certificates, or original packaging.',
      icon: 'ri-camera-line',
      tips: ['Use natural lighting for photos', 'Show any flaws or damage', 'Include size references']
    },
    {
      number: 2,
      title: 'Create Your Listing',
      description: 'Write a detailed description including condition, history, and specifications. Set your starting price, auction duration, and shipping options.',
      icon: 'ri-file-settings-line',
      tips: [
        'Be honest about condition', 'Use relevant keywords', 'Set realistic starting prices'
      ]
    },
    {
      number: 3,
      title: 'Monitor Your Auction',
      description: 'Respond to buyer questions promptly. Watch bidding activity and be ready to answer any concerns.',
      icon: 'ri-eye-line',
      tips: ['Answer questions within 24 hours', 'Update listing if needed', 'Promote on social media']
    },
    {
      number: 4,
      title: 'Complete the Sale',
      description: 'Once auction ends, contact the winner and arrange payment. Ship promptly with tracking and insurance.',
      icon: 'ri-truck-line',
      tips: ['Package items securely', 'Always use tracking', 'Communicate shipping updates']
    }
  ];

  return (
    <section className="py-20 bg-white">
      <div className="max-w-7xl mx-auto px-6">
        {/* Buyer's Guide */}
        <div className="mb-24">
          <div className="text-center mb-16">
            <h2 className="text-4xl font-bold text-gray-900 mb-4">Buyer's Guide</h2>
            <p className="text-xl text-gray-600 max-w-2xl mx-auto">
              Follow these steps to successfully bid and win items on Hammerly
            </p>
          </div>

          <div className="space-y-12">
            {buyerSteps.map((step) => (
              <div key={step.number} className="flex gap-8 items-start">
                <div className="flex-shrink-0">
                  <div className="w-16 h-16 bg-[#8B2635] rounded-full flex items-center justify-center text-white text-2xl font-bold">
                    {step.number}
                  </div>
                </div>
                <div className="flex-1">
                  <div className="bg-gray-50 rounded-xl p-8 hover:shadow-lg transition-shadow">
                    <div className="flex items-start gap-4 mb-4">
                      <div className="w-12 h-12 bg-white rounded-lg flex items-center justify-center">
                        <i className={`${step.icon} text-2xl text-[#8B2635]`}></i>
                      </div>
                      <div className="flex-1">
                        <h3 className="text-2xl font-bold text-gray-900 mb-3">{step.title}</h3>
                        <p className="text-gray-700 text-lg leading-relaxed mb-4">{step.description}</p>
                        {step.tips?.length > 0 && (
                          <div className="bg-white rounded-lg p-4 border-l-4 border-[#8B2635]">
                            <h4 className="font-semibold text-gray-900 mb-2 flex items-center gap-2">
                              <i className="ri-lightbulb-line text-[#8B2635]"></i>
                              Tips:
                            </h4>
                            <ul className="space-y-1">
                              {step.tips.map((tip, idx) => (
                                <li key={idx} className="text-gray-600 flex items-start gap-2">
                                  <span className="text-[#8B2635] mt-1">•</span>
                                  <span>{tip}</span>
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Seller's Guide */}
        <div>
          <div className="text-center mb-16">
            <h2 className="text-4xl font-bold text-gray-900 mb-4">Seller's Guide</h2>
            <p className="text-xl text-gray-600 max-w-2xl mx-auto">
              Learn how to create successful listings and maximize your sales
            </p>
          </div>

          <div className="space-y-12">
            {sellerSteps.map((step) => (
              <div key={step.number} className="flex gap-8 items-start">
                <div className="flex-shrink-0">
                  <div className="w-16 h-16 bg-[#D4AF37] rounded-full flex items-center justify-center text-white text-2xl font-bold">
                    {step.number}
                  </div>
                </div>
                <div className="flex-1">
                  <div className="bg-gray-50 rounded-xl p-8 hover:shadow-lg transition-shadow">
                    <div className="flex items-start gap-4 mb-4">
                      <div className="w-12 h-12 bg-white rounded-lg flex items-center justify-center">
                        <i className={`${step.icon} text-2xl text-[#D4AF37]`}></i>
                      </div>
                      <div className="flex-1">
                        <h3 className="text-2xl font-bold text-gray-900 mb-3">{step.title}</h3>
                        <p className="text-gray-700 text-lg leading-relaxed mb-4">{step.description}</p>
                        {step.tips?.length > 0 && (
                          <div className="bg-white rounded-lg p-4 border-l-4 border-[#D4AF37]">
                            <h4 className="font-semibold text-gray-900 mb-2 flex items-center gap-2">
                              <i className="ri-lightbulb-line text-[#D4AF37]"></i>
                              Tips:
                            </h4>
                            <ul className="space-y-1">
                              {step.tips.map((tip, idx) => (
                                <li key={idx} className="text-gray-600 flex items-start gap-2">
                                  <span className="text-[#D4AF37] mt-1">•</span>
                                  <span>{tip}</span>
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}