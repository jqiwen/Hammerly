interface AuctionCardProps {
  auction: {
    id: number;
    title: string;
    category: string;
    currentBid: number;
    timeRemaining: string;
    image: string;
    progress: number;
    condition?: string;
    totalBids?: number;
    seller?: string;
  };
  viewType: 'grid' | 'list';
}

export default function AuctionCard({ auction, viewType }: AuctionCardProps) {
  const handleClick = () => {
    window.REACT_APP_NAVIGATE(`/auction/${auction.id}`);
  };

  if (viewType === 'list') {
    return (
      <div 
        className="bg-white rounded-xl shadow-sm hover:shadow-md transition-shadow duration-300 cursor-pointer overflow-hidden"
        onClick={handleClick}
      >
        <div className="flex">
          {/* Image */}
          <div className="w-48 h-32 flex-shrink-0">
            <img 
              src={auction.image}
              alt={auction.title}
              className="w-full h-full object-cover object-top"
            />
          </div>

          {/* Content */}
          <div className="flex-1 p-6 flex justify-between">
            <div className="flex-1">
              <div className="mb-2">
                <span className="text-xs uppercase tracking-wider text-gray-500 bg-gray-100 px-2 py-1 rounded">
                  {auction.category}
                </span>
              </div>
              <h3 className="text-xl font-semibold text-gray-900 mb-2 line-clamp-1">
                {auction.title}
              </h3>
              <div className="flex items-center gap-4 text-sm text-gray-600 mb-3">
                <span>Condition: {auction.condition || 'Good'}</span>
                <span>•</span>
                <span>{auction.totalBids || Math.floor(Math.random() * 15 + 5)} bids</span>
              </div>
              
              {/* Progress Bar */}
              <div className="w-full bg-gray-200 rounded-full h-2 mb-2">
                <div 
                  className="bg-[#8B2635] h-2 rounded-full transition-all duration-300"
                  style={{ width: `${auction.progress}%` }}
                ></div>
              </div>
              <p className="text-xs text-gray-500">{auction.timeRemaining} remaining</p>
            </div>

            {/* Bid Info */}
            <div className="text-right ml-6">
              <p className="text-sm text-gray-500 mb-1">Current Bid</p>
              <p className="text-3xl font-bold text-black mb-2">
                ${auction.currentBid.toLocaleString()}
              </p>
              <button 
                className="bg-[#8B2635] text-white px-6 py-2 rounded-lg hover:bg-[#7A1F2B] transition-colors text-sm font-semibold whitespace-nowrap"
                onClick={(e) => {
                  e.stopPropagation();
                  handleClick();
                }}
              >
                Place Bid
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Grid view
  return (
    <div 
      className="bg-white rounded-xl shadow-lg hover:shadow-xl transition-shadow duration-300 cursor-pointer"
      onClick={handleClick}
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


        {/* Additional Info */}
        <div className="flex justify-end items-center text-sm text-gray-600">
          <span>Condition: {auction.condition || 'Good'}</span>
        </div>
      </div>
    </div>
  );
}