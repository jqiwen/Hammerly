export default function RelatedAuctionCardSkeleton() {
  return (
    <div aria-hidden="true" className="animate-pulse rounded-xl bg-white shadow-lg">
      <div className="p-3">
        <div className="h-48 w-full rounded-lg bg-gray-200" />
      </div>

      <div className="space-y-4 p-4">
        <div className="space-y-2">
          <div className="h-3 w-20 rounded bg-gray-200" />
          <div className="h-4 w-4/5 rounded bg-gray-200" />
          <div className="h-4 w-3/5 rounded bg-gray-200" />
        </div>

        <div className="flex items-end justify-between">
          <div className="space-y-2">
            <div className="h-3 w-16 rounded bg-gray-200" />
            <div className="h-6 w-24 rounded bg-gray-200" />
          </div>
          <div className="h-3 w-20 rounded bg-gray-200" />
        </div>

        <div className="h-2 w-full rounded-full bg-gray-200" />
      </div>
    </div>
  );
}

export function RelatedItemsSkeleton() {
  return (
    <section className="mt-12 border-t pt-12" aria-busy="true">
      <div className="mb-8 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">Related Auctions</h2>
        <div aria-hidden="true" className="h-4 w-28 animate-pulse rounded bg-gray-200" />
      </div>

      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <RelatedAuctionCardSkeleton key={index} />
        ))}
      </div>
    </section>
  );
}
