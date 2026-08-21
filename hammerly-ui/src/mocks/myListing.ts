export interface Listing {
  id: number;
  title: string;
  currentBid: number;
  startingPrice: number;
  bids: number;
  watchers: number;
  timeLeft: string;
  status: 'active' | 'ended' | 'draft';
  image: string;
  createdAt: string;
  category?: string;
  condition?: string;
  description?: string;
  duration?: string;
  shippingOption?: string;
  shippingCost?: string;
  reservePrice?: string;
}

export const myListings: Listing[] = [ 
    {
      id: 1,
      title: 'Antique Victorian Pocket Watch',
      currentBid: 850,
      startingPrice: 500,
      bids: 12,
      watchers: 28,
      timeLeft: '2d 5h',
      status: 'active',
      image: "/images/picture.jpg",
      createdAt: '3 days ago',
      category: 'Jewelry & Watches',
      condition: 'Excellent',
      description: 'Selling Item 1 description.',
      duration: '7',
      shippingOption: 'seller',
      shippingCost: '',
      reservePrice: '700'
    },
    {
      id: 2,
      title: 'Selling Item 2',
      currentBid: 4200,
      startingPrice: 3000,
      bids: 8,
      watchers: 45,
      timeLeft: '5d 12h',
      status: 'active',
      image: "/images/picture.jpg",
      createdAt: '1 week ago',
      category: 'Antiques & Collectibles',
      condition: 'Very Good',
      description: 'Selling Item 2 description.',
      duration: '10',
      shippingOption: 'buyer',
      shippingCost: '150',
      reservePrice: '3500'
    },
    {
      id: 3,
      title: 'Selling Item 3',
      currentBid: 1650,
      startingPrice: 1200,
      bids: 15,
      watchers: 32,
      timeLeft: 'Ended',
      status: 'ended',
      image: "/images/picture.jpg",
      createdAt: '2 weeks ago',
      category: 'Furniture',
      condition: 'Good',
      description: 'Selling Item 3 description.',
      duration: '7',
      shippingOption: 'seller',
      shippingCost: '',
      reservePrice: ''
    },
    {
      id: 4,
      title: 'Selling Item 4',
      currentBid: 0,
      startingPrice: 8500,
      bids: 0,
      watchers: 0,
      timeLeft: 'Draft',
      status: 'draft',
      image: "/images/picture.jpg",
      createdAt: 'Not published',
      category: 'Jewelry & Watches',
      condition: 'Very Good',
      description: 'Selling Item 4 description.',
      duration: '7',
      shippingOption: 'seller',
      shippingCost: '',
      reservePrice: '10000'
    },
    ];