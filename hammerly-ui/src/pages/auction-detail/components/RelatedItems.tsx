import { useEffect, useState } from 'react';
import { auctionApi } from '../../../api/auctions';
import { RelatedItemsSkeleton } from './RelatedAuctionCardSkeleton';

interface RelatedItemsProps {
  currentId: number;
}

export default function RelatedItems({ currentId }: RelatedItemsProps) {
  const [relatedItems, setRelatedItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchRelatedItems = async () => {
      try {
        setLoading(true);
        const response = await auctionApi.getRelatedAuctions(currentId);
        setRelatedItems(response.data);
        setError(null);
      } catch (err) {
        console.error('Failed to fetch related items:', err);
        setError('Failed to load related items. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchRelatedItems();
  }, [currentId]);

  if (loading) {
    return <RelatedItemsSkeleton />;
  }

  if (error) {
    return <p>{error}</p>;
  }

  return (
    <section className="mt-12 pt-12 border-t">
      <div className="flex justify-between items-center mb-8">
        <h2 className="text-2xl font-bold text-gray-900">Related Auctions</h2>
        <a href="/auctions" className="text-[#8B2635] hover:underline font-medium cursor-pointer">
          View All Auctions
        </a>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {relatedItems.map((item: any) => (
          <div 
            key={item.id}
            className="bg-white rounded-xl shadow-lg hover:shadow-xl transition-shadow duration-300 cursor-pointer"
            onClick={() => window.REACT_APP_NAVIGATE(`/auction/${item.id}`)}
          >
            <div className="p-3">
              <img 
                src={item.image}
                alt={item.title}
                className="w-full h-48 object-cover object-top rounded-lg"
              />
            </div>
            
            <div className="p-4">
              <div className="mb-3">
                <p className="text-xs uppercase tracking-wider text-gray-500 mb-1">
                  {item.category}
                </p>
                <h3 className="text-sm font-semibold text-gray-900 line-clamp-2">
                  {item.title}
                </h3>
              </div>

              <div className="flex justify-between items-end">
                <div>
                  <p className="text-xs text-gray-500">Current Bid</p>
                  <p className="text-lg font-bold text-[#8B2635]">
                    ${item.currentBid.toLocaleString()}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-gray-500">{item.timeRemaining}</p>
                </div>
              </div>

              <div className="w-full bg-gray-200 rounded-full h-2 mt-3">
                <div 
                  className="bg-[#8B2635] h-2 rounded-full transition-all duration-300"
                  style={{ width: `${item.progress}%` }}
                ></div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
