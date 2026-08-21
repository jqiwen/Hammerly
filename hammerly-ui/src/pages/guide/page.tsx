import Header from '../../components/feature/Header';
import Footer from '../../components/feature/Footer';
import GuideHero from './components/GuideHero';
import GuideSteps from './components/GuideSteps';

export default function Guide() {
  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      <main>
        <GuideHero />
        <GuideSteps />
      </main>
      <Footer />
    </div>
  );
}