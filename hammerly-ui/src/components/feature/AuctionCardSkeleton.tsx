interface AuctionCardSkeletonProps {
  compact?: boolean;
}

export default function AuctionCardSkeleton({ compact = false }: AuctionCardSkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded-xl bg-white shadow-sm ${compact ? 'min-w-[320px]' : ''}`}
    >
      <div className="p-3">
        <div className="h-64 rounded-lg bg-gray-200" />
      </div>
      <div className="space-y-4 p-6">
        <div className="h-3 w-24 rounded bg-gray-200" />
        <div className="h-5 w-4/5 rounded bg-gray-200" />
        <div className="flex items-end justify-between pt-4">
          <div className="h-8 w-28 rounded bg-gray-200" />
          <div className="h-4 w-20 rounded bg-gray-200" />
        </div>
      </div>
    </div>
  );
}
