
import Header from '../../components/feature/Header';
import Footer from '../../components/feature/Footer';
import AuctionListings from './components/AuctionListings';
import HowItWorks from './components/HowItWorks';

export default function Home() {
  return (
    <div className="min-h-screen flex flex-col">
      <Header />
      <main className="pt-20 flex-grow">
        <AuctionListings />
        <HowItWorks />
      </main>
      <Footer />
    </div>
  );
}
