const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

type CreateAuctionPayload = {
  title: string;
  category: string;
  description?: string;
  sellerId?: number | string;
  // New form-native fields
  startingPrice?: string | number;
  reservePrice?: string | number;
  duration?: string | number;
  images?: string[];
  shippingOption?: 'seller' | 'buyer' | string;
  shippingCost?: string | number;
  // Backward-compatible fields
  startPrice?: number;
  condition?: string;
  image?: string;
  endTime?: string;
};

const getAuthHeaders = () => {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  };
};

const parseResponseOrThrow = async (response: Response, fallbackError: string) => {
  let data: any = null;

  try {
    data = await response.json();
  } catch {
    if (!response.ok) {
      throw new Error(fallbackError);
    }
    return data;
  }

  if (!response.ok) {
    throw new Error(data?.message || fallbackError);
  }

  return data;
};

export const auctionApi = {
  // // Get all auctions with pagination
  // getAuctions: async (page = 1) => {
  //   try {
  //     const response = await fetch(`${API_BASE_URL}/auctions/search?page=${page}`);
  //     if (!response.ok) throw new Error('Failed to fetch auctions');
  //     return await response.json();
  //   } catch (error) {
  //     console.error('Error fetching auctions:', error);
  //     throw error;
  //   }
  // },

  // Get top auctions
  getTopAuctions: async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/get-top`); 
      if (!response.ok) throw new Error('Failed to fetch auctions'); 
      return await response.json();
    } catch (error) {
      console.error('Error fetching top auctions:', error);
      throw error;
    }     
  },

  // Get single auction by ID
  getAuctionById: async (id: number) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/get/${id}`);
      if (!response.ok) throw new Error('Auction not found');
      return await response.json();
    } catch (error) {
      console.error('Error fetching auction:', error);
      throw error;
    }
  },

  // Place a bid
  placeBid: async (auctionId: number, bidAmount: number) => {
    try {
      const response = await fetch(
        `${API_BASE_URL}/auctions/${auctionId}/bid?bidAmount=${encodeURIComponent(String(bidAmount))}`,
        {
          method: 'GET',
          headers: getAuthHeaders()
        }
      );
      return await parseResponseOrThrow(response, 'Failed to place bid');
    } catch (error) {
      console.error('Error placing bid:', error);
      throw error;
    }
  },

  // Search auctions by title substring with pagination
  searchAuctions: async (query: string, page = 1) => {
    try {
      const response = await fetch(
        `${API_BASE_URL}/auctions/search?q=${encodeURIComponent(query)}&page=${page}`
      );
      if (!response.ok) throw new Error('Failed to search auctions');
      return await response.json();
    } catch (error) {
      console.error('Error searching auctions:', error);
      throw error;
    }
  },

  // Get related auctions by item ID
  getRelatedAuctions: async (id: number) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/get-related/${id}`);
      if (!response.ok) throw new Error('Failed to fetch related auctions');
      return await response.json();
    } catch (error) {
      console.error('Error fetching related auctions:', error);
      throw error;
    }
  },

  // Add auction to user's watchlist
  watchAuction: async (auctionId: number) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/watch/${auctionId}`, {
        method: 'POST',
        headers: getAuthHeaders()
      });
      return await parseResponseOrThrow(response, 'Failed to add item to watchlist');
    } catch (error) {
      console.error('Error adding item to watchlist:', error);
      throw error;
    }
  },

  // Remove auction from user's watchlist
  unwatchAuction: async (auctionId: number) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/unwatch/${auctionId}`, {
        method: 'DELETE',
        headers: getAuthHeaders()
      });
      return await parseResponseOrThrow(response, 'Failed to remove item from watchlist');
    } catch (error) {
      console.error('Error removing item from watchlist:', error);
      throw error;
    }
  },

  // Get user's watchlist
  getWatchlist: async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/get-watchlist`, {
        method: 'GET',
        headers: getAuthHeaders()
      });
      return await parseResponseOrThrow(response, 'Failed to fetch watchlist');
    } catch (error) {
      console.error('Error fetching watchlist:', error);
      throw error;
    }
  },

  // Check if auction is watched by current user
  isAuctionWatched: async (auctionId: number) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/is-watched/${auctionId}`, {
        method: 'GET',
        headers: getAuthHeaders()
      });
      return await parseResponseOrThrow(response, 'Failed to check watch status');
    } catch (error) {
      console.error('Error checking watch status:', error);
      throw error;
    }
  },

  // Create a new auction listing
  createAuction: async (payload: CreateAuctionPayload) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/create`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify(payload)
      });
      return await parseResponseOrThrow(response, 'Failed to create auction');
    } catch (error) {
      console.error('Error creating auction:', error);
      throw error;
    }
  },

  // Delete an auction listing
  deleteAuction: async (auctionId: number) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/delete/${auctionId}`, {
        method: 'DELETE',
        headers: getAuthHeaders()
      });
      return await parseResponseOrThrow(response, 'Failed to delete auction');
    } catch (error) {
      console.error('Error deleting auction:', error);
      throw error;
    }
  },

  // End an auction listing
  endAuction: async (auctionId: number) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auctions/end/${auctionId}`, {
        method: 'PATCH',
        headers: getAuthHeaders()
      });
      return await parseResponseOrThrow(response, 'Failed to end auction');
    } catch (error) {
      console.error('Error ending auction:', error);
      throw error;
    }
  }
};
