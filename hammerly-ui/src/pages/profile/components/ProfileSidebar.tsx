import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { logoutApi } from '@/api/auth';
interface ProfileSidebarProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
}

export default function ProfileSidebar({ activeTab, setActiveTab }: ProfileSidebarProps) {
  const navigate = useNavigate();
  const { isLoggedIn, user } = useAuthStore(); // Get login state and user from the store

  if (!isLoggedIn || !user) {
    return null; // If not logged in, don't render the sidebar
  }

  const handleLogout = () => {
    const token = useAuthStore.getState().token || localStorage.getItem('token');
    useAuthStore.getState().logout();
    navigate('/');
    void logoutApi(token).catch(() => {
      // Stateless logout is local; the server acknowledgement is best effort.
    });
  };

  const menuItems = [
    { id: 'settings', label: 'Settings', icon: 'ri-settings-3-line'},
    { id: 'bids', label: 'My Bids', icon: 'ri-hammer-line' },
    { id: 'listings', label: 'My Listings', icon: 'ri-store-2-line' },
  ];

  return (
    <div className="bg-white rounded-2xl shadow-sm p-6 sticky top-28">
      {/* Profile Header */}
      <div className="text-center mb-6 pb-6 border-b border-gray-100">
        <div className="w-20 h-20 mx-auto mb-4 rounded-full overflow-hidden bg-gradient-to-br from-[#8B2635] to-[#C4A484]">
          <img
            src={user.avatarImage || '/images/user.jpg'}
            alt="Profile"
            className="w-full h-full object-cover object-top"
          />
        </div>
        <h3 className="text-lg font-serif font-bold text-gray-900">{user.firstName} {user.lastName}</h3>
        {/* <p className="text-sm text-gray-500">Member since 2023</p>
        <div className="flex items-center justify-center gap-1 mt-2">
          <i className="ri-star-fill text-amber-400 w-4 h-4 flex items-center justify-center"></i>
          <span className="text-sm font-medium text-gray-700">4.9 Rating</span>
          <span className="text-sm text-gray-400">(127 reviews)</span>
        </div> */}
      </div>

      {/* Navigation Menu */}
      <nav className="space-y-2 pb-6 border-b border-gray-100">
        {menuItems.map((item, index) => (
          <button
            key={item.id}
            onClick={() => setActiveTab(item.id)}
            className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all cursor-pointer whitespace-nowrap ${
              activeTab === item.id
                ? 'bg-[#8B2635] text-white'
                : 'text-gray-600 hover:bg-gray-50'
            } ${index === menuItems.length - 1 ? 'mb-4' : ''}`}
          >
            <i className={`${item.icon} text-xl w-5 h-5 flex items-center justify-center`}></i>
            <span className="font-medium">{item.label}</span>
          </button>
        ))}
      </nav>

      {/* Logout Button */}
      <button
        onClick={handleLogout}
        className="w-full flex items-center gap-3 px-4 py-3 mt-6 rounded-lg text-red-600 hover:bg-red-50 transition-all cursor-pointer whitespace-nowrap"
      >
        <i className="ri-logout-box-line text-xl w-5 h-5 flex items-center justify-center"></i>
        <span className="font-medium">Sign Out</span>
      </button>
    </div>
  );
}
