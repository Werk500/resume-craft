/** 与列表布局同形的骨架屏，替代"加载中…"文本 */
export default function ListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-3" aria-hidden>
      {Array.from({ length: rows }).map((_, index) => (
        <div
          key={index}
          className="flex items-center justify-between rounded-2xl border border-zinc-200 bg-white p-5"
        >
          <div className="flex items-center gap-4">
            <div className="h-11 w-11 animate-pulse rounded-xl bg-zinc-100" />
            <div className="space-y-2">
              <div className="h-3.5 w-44 animate-pulse rounded bg-zinc-100" />
              <div className="h-3 w-28 animate-pulse rounded bg-zinc-100" />
            </div>
          </div>
          <div className="h-3 w-16 animate-pulse rounded bg-zinc-100" />
        </div>
      ))}
    </div>
  );
}
