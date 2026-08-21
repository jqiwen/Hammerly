
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/feature/Header';
import Footer from '../../components/feature/Footer';
import ProfileSidebar from './components/ProfileSidebar';
import ProfileBids from './components/ProfileBids';
import ProfileListings from './components/ProfileListings';
import ProfileSettings from './components/ProfileSettings';
import { useAuthStore } from '@/store/useAuthStore';

const PROFILE_TAB_STORAGE_KEY = 'profile-active-tab';
const DEFAULT_PROFILE_TAB = 'settings';
const PROFILE_TABS = new Set(['settings', 'bids', 'listings']);

export default function Profile() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState(() => {
    const storedTab = localStorage.getItem(PROFILE_TAB_STORAGE_KEY);
    return storedTab && PROFILE_TABS.has(storedTab) ? storedTab : DEFAULT_PROFILE_TAB;
  });
  const { isLoggedIn, user } = useAuthStore();

  useEffect(() => {
    // Redirect to auth if not logged in
    if (!isLoggedIn || !user) {
      navigate('/auth');
    }
  }, [isLoggedIn, user, navigate]);

  useEffect(() => {
    localStorage.setItem(PROFILE_TAB_STORAGE_KEY, activeTab);
  }, [activeTab]);

  if (!isLoggedIn || !user) {
    return null;
  }

  const renderContent = () => {
    switch (activeTab) {
      case 'settings':
        return <ProfileSettings />;
      case 'bids':
        return <ProfileBids />;
      case 'listings':
        return <ProfileListings />;

      default:
        return <ProfileSettings />;
    }
  };

  return (
    <div className="min-h-screen bg-[#FDF8F6]  flex flex-col">
      <Header />
      <main className="pt-24 pb-16  flex-grow">
        <div className="max-w-7xl mx-auto px-6">
          <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
            {/* Sidebar */}
            <div className="lg:col-span-1">
              <ProfileSidebar activeTab={activeTab} setActiveTab={setActiveTab} />
            </div>
            
            {/* Main Content */}
            <div className="lg:col-span-3">
              {renderContent()}
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </div>
  );
}
