import { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { auctionApi } from '@/api/auctions';

export default function Header() {
  const location = useLocation();
  const { isLoggedIn } = useAuthStore();
  const [watchedCount, setWatchedCount] = useState(0);

  useEffect(() => {
    const loadWatchedCount = async () => {
      if (!isLoggedIn) {
        setWatchedCount(0);
        return;
      }

      try {
        const response = await auctionApi.getWatchlist();
        setWatchedCount(Array.isArray(response?.data) ? response.data.length : 0);
      } catch {
        setWatchedCount(0);
      }
    };

    void loadWatchedCount();
  }, [location.pathname, isLoggedIn]);

  useEffect(() => {
    const handleStorageChange = async () => {
      if (!isLoggedIn) {
        setWatchedCount(0);
        return;
      }

      try {
        const response = await auctionApi.getWatchlist();
        setWatchedCount(Array.isArray(response?.data) ? response.data.length : 0);
      } catch {
        setWatchedCount(0);
      }
    };

    window.addEventListener('watchedItemsUpdated', handleStorageChange);
    window.addEventListener('storage', handleStorageChange);

    return () => {
      window.removeEventListener('watchedItemsUpdated', handleStorageChange);
      window.removeEventListener('storage', handleStorageChange);
    };
  }, [isLoggedIn]);

  const navLinks = [
    { name: 'Home', href: '/' },
    { name: 'Auction Listings', href: '/auctions' },
    { name: 'Guide', href: '/guide' },
    { name: 'FAQ', href: '/faq' },
  ];

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-white shadow-md">
      <div className="max-w-7xl mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          <Link to="/" className="flex items-center space-x-2 cursor-pointer">
            <div className="w-8 h-8 rounded-lg flex items-center justify-center bg-[#8B2635]">
              <span className="text-white font-bold text-lg">H</span>
            </div>
            <span className="text-xl font-bold text-gray-900">Hammerly</span>
          </Link>

          <nav className="hidden md:flex items-center space-x-8">
            {navLinks.map((link) => (
              <Link
                key={link.name}
                to={link.href}
                className="font-medium transition-colors cursor-pointer text-gray-700 hover:text-[#8B2635]"
              >
                {link.name}
              </Link>
            ))}
          </nav>

          <div className="flex items-center space-x-4">
            {isLoggedIn ? (
              <>
                <Link
                  to="/cart"
                  className="relative w-10 h-10 flex items-center justify-center rounded-full transition-colors cursor-pointer hover:bg-gray-100 text-gray-700"
                >
                  <i className="ri-heart-line text-xl text-gray-700"></i>

                  {watchedCount > 0 && (
                    <span className="absolute -top-1 -right-1 w-5 h-5 bg-[#8B2635] text-white text-xs font-bold rounded-full flex items-center justify-center">
                      {watchedCount > 9 ? '9+' : watchedCount}
                    </span>
                  )}
                </Link>

                <Link
                  to="/profile"
                  className="w-10 h-10 flex items-center justify-center rounded-full transition-colors cursor-pointer hover:bg-gray-100 text-gray-700"
                >
                  <i className="ri-user-line text-xl text-gray-700"></i>
                </Link>
              </>
            ) : (
              <Link
                to="/auth"
                className="bg-[#8B2635] text-white px-5 py-2 rounded-lg font-medium hover:bg-[#7A2230] transition-colors cursor-pointer whitespace-nowrap"
              >
                Register to Bid
              </Link>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
