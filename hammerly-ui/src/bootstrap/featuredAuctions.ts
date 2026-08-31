export interface FeaturedAuctionSummary {
  id: number;
  title: string;
  category: string;
  currentBid: number;
  image: string;
  startTime: string;
  endTime: string;
  timeRemaining: string;
  condition?: string;
  status?: string;
  createdAt?: string;
  seller?: string;
  totalBids?: number;
  progress?: number;
}

const formatTimeRemaining = (endTime: string, now = Date.now()): string => {
  const differenceMinutes = Math.floor((Date.parse(endTime) - now) / 60_000);
  if (!Number.isFinite(differenceMinutes) || differenceMinutes <= 0) return 'Updating';

  const days = Math.floor(differenceMinutes / (24 * 60));
  const hours = Math.floor((differenceMinutes % (24 * 60)) / 60);
  const minutes = differenceMinutes % 60;

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
};

const bootstrapRows = [
  {
    id: 45,
    title: 'Big Agnes Copper Spur Tent',
    category: 'Outdoor',
    currentBid: 77,
    image: '/demo-auctions/big-agnes-copper-spur-tent.webp',
    condition: 'Very Good',
    status: 'active',
    startTime: '2026-08-30T02:44:25.682129Z',
    endTime: '2026-08-31T14:44:25.682129Z'
  },
  {
    id: 3,
    title: 'Apple MacBook Pro 14-inch M3',
    category: 'Electronics',
    currentBid: 253,
    image: '/demo-auctions/macbook-pro-m3.webp',
    condition: 'Like New',
    status: 'active',
    startTime: '2026-08-30T02:44:25.682129Z',
    endTime: '2026-08-31T20:44:25.682129Z'
  },
  {
    id: 46,
    title: 'Osprey Atmos Hiking Backpack',
    category: 'Outdoor',
    currentBid: 310,
    image: '/demo-auctions/osprey-atmos-backpack.webp',
    condition: 'Good',
    status: 'active',
    startTime: '2026-08-29T02:44:25.682129Z',
    endTime: '2026-09-01T07:44:25.682129Z'
  },
  {
    id: 4,
    title: 'Apple iPhone 15 Pro 256GB',
    category: 'Electronics',
    currentBid: 486,
    image: '/demo-auctions/iphone-15-pro.webp',
    condition: 'Excellent',
    status: 'active',
    startTime: '2026-08-29T02:44:25.682129Z',
    endTime: '2026-09-01T13:44:25.682129Z'
  },
  {
    id: 5,
    title: 'Custom RTX 4080 Gaming PC',
    category: 'Electronics',
    currentBid: 719,
    image: '/demo-auctions/rtx-4080-gaming-pc.webp',
    condition: 'Very Good',
    status: 'active',
    startTime: '2026-08-28T02:44:25.682129Z',
    endTime: '2026-09-02T06:44:25.682129Z'
  }
] as const;

// These five IDs were read back from the seeded production database. PostgreSQL
// remains authoritative; this small bundle exists only for the first React paint.
export const FEATURED_AUCTIONS_BOOTSTRAP: FeaturedAuctionSummary[] = bootstrapRows.map(row => ({
  ...row,
  timeRemaining: formatTimeRemaining(row.endTime)
}));

