
export const auctionStats = {
  activeLots: 4,
  totalValue: 1200000,
  averageBid: 850,
  completedToday: 32
};

export interface Auction {
  id: number;
  title?: string;
  category?: string;
  currentBid?: number;
  timeRemaining?: string;
  image?: string;
  progress?: number;
  condition?: string;
  totalBids?: number;
  seller?: string;
  description?: string;
  bidHistory?: { bidder: string; amount: number; time: string }[];
}

export const auctionListings = [
  {
    id: 1,
    title: "Auction item 1",
    category: "Category 1",
    currentBid: 40000,
    timeRemaining: "2h 15m",
    image: "/images/picture.jpg",
    progress: 85,
    condition: "Very Good",
    totalBids: 28,
    seller: "seller 1"
  },
  {
    id: 2,
    title: "Auction item 2",
    category: "Category 1",
    currentBid: 50000,
    timeRemaining: "4h 30m",
    image: "/images/picture.jpg",
    progress: 65,
    condition: "Good",
    totalBids: 15,
    seller: "seller 2"
  },
  {
    id: 3,
    title: "Auction item 3",
    category: "Category 2",
    currentBid: 10000,
    timeRemaining: "1d 6h",
    image: "/images/picture.jpg",
    progress: 40,
    condition: "Very Good",
    totalBids: 22,
    seller: "seller 3"
  },
  {
    id: 4,
    title: "Auction item 4",
    category: "Category 3",
    currentBid: 20000,
    timeRemaining: "3d 12h",
    image: "/images/picture.jpg",
    progress: 25,
    condition: "Excellent",
    totalBids: 8,
    seller: "seller 1"
  },

];
