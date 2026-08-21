export type FaqItem = {
  id: string;
  question: string;
  answer: string;
};

export type FaqCategory = {
  id: string;
  title: string;
  description: string;
  questions: FaqItem[];
};

export const faqCategories: FaqCategory[] = [
  {
    id: 'bidding',
    title: 'Bidding',
    description: 'Placing bids and understanding bid activity.',
    questions: [
      {
        id: 'place-a-bid',
        question: 'How do I place a bid?',
        answer:
          'Sign in, open an active auction, enter an amount higher than the current bid, and confirm your bid. The auction page will update after the bid is accepted.',
      },
      {
        id: 'cancel-a-bid',
        question: 'Can I cancel or change a bid?',
        answer:
          'Accepted bids cannot currently be cancelled or edited. You may place a new, higher bid while the auction is active, so review the amount carefully before confirming.',
      },
      {
        id: 'bid-rejected',
        question: 'Why was my bid rejected?',
        answer:
          'A bid may be rejected if you are signed out, the auction has ended, or the amount does not exceed the current bid. Refresh the auction and check the latest price before trying again.',
      },
      {
        id: 'outbid',
        question: 'What happens if someone outbids me?',
        answer:
          'The higher accepted bid becomes the current bid. You can review your bidding status in Profile under My Bids and place another bid before the auction ends.',
      },
    ],
  },
  {
    id: 'auctions',
    title: 'Auctions',
    description: 'How listings, timing, and winning bids work.',
    questions: [
      {
        id: 'auction-basics',
        question: 'How do Hammerly auctions work?',
        answer:
          'Sellers publish an item with a starting price and end time. Buyers place competing bids while the auction is active, and the highest accepted bid is leading when bidding closes.',
      },
      {
        id: 'create-auction',
        question: 'How do I create an auction?',
        answer:
          'Sign in, open your Profile, choose My Listings, and select Create New. Add the item details, starting price, duration, condition, and image before submitting the listing.',
      },
      {
        id: 'auction-ending',
        question: 'When does an auction end?',
        answer:
          'Each listing shows its remaining time. Bidding closes when that countdown reaches the scheduled end time; sellers can also end an active auction early from My Listings.',
      },
      {
        id: 'winning-auction',
        question: 'What happens if I win an auction?',
        answer:
          'When bidding closes, the highest accepted bidder is the winner. Keep your profile and payment information current so you are ready for the next fulfilment steps shown by Hammerly.',
      },
      {
        id: 'current-bid',
        question: 'How is the current bid determined?',
        answer:
          'Before any bids, the current bid starts at the seller’s starting price. After bidding begins, it reflects the highest bid Hammerly has accepted for that auction.',
      },
    ],
  },
  {
    id: 'account',
    title: 'Account',
    description: 'Registration, profile settings, and saved items.',
    questions: [
      {
        id: 'create-account',
        question: 'How do I create an account?',
        answer:
          'Select Register to Bid in the header, choose the registration option, and enter your name, email, phone number, and password. You can sign in as soon as registration succeeds.',
      },
      {
        id: 'update-profile',
        question: 'How do I update my profile?',
        answer:
          'Sign in and open the profile icon in the header. Profile Settings lets you update your name, email, phone number, and avatar.',
      },
      {
        id: 'change-password',
        question: 'How do I change my password?',
        answer:
          'Open Profile Settings, find the password section, enter your current password, and choose a new password. Confirm the new value before saving.',
      },
      {
        id: 'manage-watchlist',
        question: 'How do I manage my watchlist?',
        answer:
          'Use the watch control on an auction to save or remove it. Your saved auctions are available from the heart icon in the header when you are signed in.',
      },
    ],
  },
  {
    id: 'selling',
    title: 'Selling',
    description: 'Creating and managing your Hammerly listings.',
    questions: [
      {
        id: 'list-item',
        question: 'How do I list an item?',
        answer:
          'Sign in, go to Profile, open My Listings, and select Create New. Provide a clear title, category, description, condition, starting price, duration, and item image.',
      },
      {
        id: 'starting-price',
        question: 'How should I choose a starting price?',
        answer:
          'Consider the item’s condition, rarity, and comparable listings. A realistic starting price can encourage early bids while still reflecting the minimum value you expect.',
      },
      {
        id: 'edit-auction',
        question: 'Can I edit my auction?',
        answer:
          'Draft listings can be reviewed before publishing. Active auction details are not currently editable, so verify the information before submission; you can end an active auction from My Listings if necessary.',
      },
      {
        id: 'my-auctions',
        question: 'Where can I see my auctions?',
        answer:
          'Open Profile and select My Listings. You can filter your auctions by all, active, or ended status and manage eligible listings from there.',
      },
    ],
  },
];

export const popularFaqIds = [
  'place-a-bid',
  'cancel-a-bid',
  'auction-basics',
  'create-auction',
  'winning-auction',
  'update-profile',
];

export const allFaqItems = faqCategories.flatMap((category) => category.questions);
