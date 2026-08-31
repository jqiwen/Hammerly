import Footer from '../../../components/feature/Footer';
import Header from '../../../components/feature/Header';
import { RelatedItemsSkeleton } from './RelatedAuctionCardSkeleton';

const SkeletonBlock = ({ className }: { className: string }) => (
  <div className={`rounded bg-gray-200 ${className}`} />
);

export default function AuctionDetailSkeleton() {
  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      <main className="py-8 pt-24" aria-busy="true">
        <div className="mx-auto max-w-7xl px-6">
          <div aria-hidden="true" className="animate-pulse">
            <nav className="mb-8 flex items-center gap-2">
              <SkeletonBlock className="h-3 w-10" />
              <SkeletonBlock className="h-3 w-2" />
              <SkeletonBlock className="h-3 w-20" />
              <SkeletonBlock className="h-3 w-2" />
              <SkeletonBlock className="h-3 w-36" />
            </nav>

            <div className="mb-12 grid grid-cols-1 gap-8 lg:grid-cols-12">
              <div className="lg:col-span-7">
                <div className="overflow-hidden rounded-xl bg-white shadow-lg">
                  <div className="aspect-square bg-gray-200" />
                  <div className="flex gap-3 p-4">
                    {Array.from({ length: 4 }, (_, index) => (
                      <div key={index} className="h-20 w-20 rounded-lg bg-gray-200" />
                    ))}
                  </div>
                </div>
              </div>

              <div className="lg:col-span-5">
                <div className="space-y-6 rounded-xl bg-white p-6 shadow-lg">
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <SkeletonBlock className="h-6 w-24 rounded-full" />
                      <SkeletonBlock className="h-3 w-28" />
                    </div>
                    <SkeletonBlock className="h-7 w-11/12" />
                    <SkeletonBlock className="h-7 w-3/5" />
                  </div>

                  <div className="space-y-2">
                    <SkeletonBlock className="h-3 w-20" />
                    <SkeletonBlock className="h-10 w-40" />
                    <SkeletonBlock className="h-3 w-32" />
                  </div>

                  <div className="space-y-3">
                    <SkeletonBlock className="h-4 w-28" />
                    <SkeletonBlock className="h-12 w-full rounded-lg" />
                    <div className="flex gap-2">
                      <SkeletonBlock className="h-8 w-16 rounded-lg" />
                      <SkeletonBlock className="h-8 w-16 rounded-lg" />
                      <SkeletonBlock className="h-8 w-20 rounded-lg" />
                    </div>
                    <SkeletonBlock className="h-12 w-full rounded-lg" />
                    <SkeletonBlock className="h-12 w-full rounded-lg" />
                  </div>

                  <div className="border-t pt-5">
                    <SkeletonBlock className="h-4 w-3/4" />
                  </div>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
              <div className="overflow-hidden rounded-xl bg-white shadow-lg lg:col-span-2">
                <div className="grid grid-cols-2 border-b border-gray-200 p-4">
                  <SkeletonBlock className="mx-auto h-5 w-28" />
                  <SkeletonBlock className="mx-auto h-5 w-24" />
                </div>
                <div className="space-y-3 p-6">
                  <SkeletonBlock className="h-4 w-full" />
                  <SkeletonBlock className="h-4 w-11/12" />
                  <SkeletonBlock className="h-4 w-4/5" />
                  <SkeletonBlock className="h-4 w-2/3" />
                </div>
              </div>

              <div className="space-y-6 rounded-xl bg-white p-6 shadow-lg lg:col-span-1">
                <div className="flex items-center gap-4">
                  <div className="h-16 w-16 rounded-full bg-gray-200" />
                  <div className="flex-1 space-y-2">
                    <SkeletonBlock className="h-5 w-32" />
                    <SkeletonBlock className="h-3 w-24" />
                    <SkeletonBlock className="h-3 w-36" />
                  </div>
                </div>
                <div className="space-y-4">
                  <SkeletonBlock className="h-4 w-full" />
                  <SkeletonBlock className="h-4 w-11/12" />
                  <SkeletonBlock className="h-4 w-4/5" />
                </div>
                <SkeletonBlock className="h-12 w-full rounded-lg" />
                <div className="space-y-3 border-t pt-6">
                  <SkeletonBlock className="h-5 w-32" />
                  <SkeletonBlock className="h-3 w-full" />
                  <SkeletonBlock className="h-3 w-5/6" />
                </div>
              </div>
            </div>
          </div>

          <RelatedItemsSkeleton />
        </div>
      </main>
      <Footer />
    </div>
  );
}
