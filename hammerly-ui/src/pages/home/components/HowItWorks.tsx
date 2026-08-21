import { Link } from 'react-router-dom';

export default function HowItWorks() {
  const steps = [
    {
      icon: 'ri-user-add-line',
      title: 'Create Account',
      description: 'Sign up to start exploring thousands of items up for auction.'
    },
    {
      icon: 'ri-search-line',
      title: 'Browse Auctions',
      description: 'Discover rare collectibles, antiques, and more from verified sellers.'
    },
    {
      icon: 'ri-auction-line',
      title: 'Place Your Bid',
      description: 'Bid on items you love and watch them in real-time.'
    },
    {
      icon: 'ri-trophy-line',
      title: 'Win & Enjoy',
      description: 'Winning the bid and receive your item with fast shipping.'
    }
  ];

  const handleGuideClick = (e: React.MouseEvent<HTMLAnchorElement>) => {
    e.preventDefault();
    window.REACT_APP_NAVIGATE('/guide');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <section className="py-20 bg-white">
      <div className="max-w-7xl mx-auto px-6">
        <div className="text-center mb-16">
          <h2 className="text-4xl font-bold text-gray-900 mb-4">How It Works</h2>
          <p className="text-xl text-gray-600 max-w-2xl mx-auto">
            Start bidding in four simple steps
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8 mb-12">
          {steps.map((step, index) => (
            <div key={index} className="text-center">
              <div className="relative mb-6">
                <div className="w-20 h-20 bg-[#8B2635] rounded-full flex items-center justify-center mx-auto">
                  <i className={`${step.icon} text-3xl text-white`}></i>
                </div>
                <div className="absolute -top-2 -right-2 w-10 h-10 bg-[#D4AF37] rounded-full flex items-center justify-center text-white font-bold text-lg mx-auto left-0 right-0 ml-auto mr-8">
                  {index + 1}
                </div>
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">{step.title}</h3>
              <p className="text-gray-600 leading-relaxed">{step.description}</p>
            </div>
          ))}
        </div>

        <div className="text-center">
          <Link 
            to="/guide" 
            onClick={handleGuideClick}
            className="text-[#8B2635] underline hover:no-underline text-lg font-medium cursor-pointer"
          >
            View Full Guide
          </Link>
        </div>
      </div>
    </section>
  );
}