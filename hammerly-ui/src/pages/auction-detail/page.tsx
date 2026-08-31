import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import Header from '../../components/feature/Header';
import Footer from '../../components/feature/Footer';
import { auctionApi } from '../../api/auctions';
import BiddingSection from './components/BiddingSection';
import ItemDetails from './components/ItemDetails';
import SellerInfo from './components/SellerInfo';
import RelatedItems from './components/RelatedItems';
import AuctionDetailSkeleton from './components/AuctionDetailSkeleton';

export default function AuctionDetail() {
  const { id } = useParams();
  const auctionId = Number.parseInt(id || '0', 10);
  const [auction, setAuction] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [resolvedAuctionId, setResolvedAuctionId] = useState<number | null>(null);

  useEffect(() => {
    let isCurrentRequest = true;

    const fetchAuction = async () => {
      try {
        setLoading(true);
        const response = await auctionApi.getAuctionById(auctionId);
        if (!isCurrentRequest) return;
        setAuction(response.data);
        setError(null);
      } catch (err) {
        if (!isCurrentRequest) return;
        console.error('Failed to fetch auction:', err);
        setError('Failed to load auction. Please try again later.');
      } finally {
        if (isCurrentRequest) {
          setResolvedAuctionId(auctionId);
          setLoading(false);
        }
      }
    };

    void fetchAuction();

    return () => {
      isCurrentRequest = false;
    };
  }, [auctionId]);

  if (loading || resolvedAuctionId !== auctionId) {
    return <AuctionDetailSkeleton />;
  }

  if (error || !auction) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-gray-900 mb-4">Auction Not Found</h1>
          <p className="text-gray-600">{error || "The auction you're looking for doesn't exist."}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      <main className="pt-24 py-8">
        <div className="max-w-7xl mx-auto px-6">
          {/* Breadcrumb */}
          <nav className="mb-8">
            <ol className="flex items-center space-x-2 text-sm">
              <li><a href="/" className="text-gray-500 hover:text-gray-700 cursor-pointer">Home</a></li>
              <li className="text-gray-300">/</li>
              <li><a href="/auctions" className="text-gray-500 hover:text-gray-700 cursor-pointer">All auctions</a></li>
              <li className="text-gray-300">/</li>
              <li className="text-gray-900 font-medium">{auction.title}</li>
            </ol>
          </nav>

          {/* Main Content Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 mb-12">
            {/* Left Column - Image Gallery */}
            <div className="lg:col-span-7">
              <div className="bg-white rounded-xl shadow-lg overflow-hidden">
                <div className="aspect-square">
                  <img 
                    src={auction.image}
                    alt={auction.title}
                    className="w-full h-full object-cover object-top"
                  />
                </div>
                {/* Thumbnail Gallery */}
                <div className="p-4 flex gap-3">
                  {[1,2,3,4].map((thumb) => (
                    <div key={thumb} className="w-20 h-20 bg-gray-100 rounded-lg cursor-pointer hover:ring-2 hover:ring-[#8B2635] transition-all">
                      <img 
                        src={auction.image}
                        alt={`Thumbnail ${thumb}`}
                        className="w-full h-full object-cover rounded-lg opacity-80 hover:opacity-100 transition-opacity"
                      />
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Right Column - Bidding Info */}
            <div className="lg:col-span-5">
              <BiddingSection auction={auction} />
            </div>
          </div>

          {/* Bottom Section - Tabs */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div className="lg:col-span-2">
              <ItemDetails auction={auction} />
            </div>
            <div className="lg:col-span-1">
              <SellerInfo />
            </div>
          </div>

          {/* Related Items */}
          <RelatedItems currentId={auction.id} />
        </div>
      </main>
      <Footer />
    </div>
  );
}

