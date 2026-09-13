export default function Loading() {
  return (
    <main className="mx-auto max-w-5xl animate-pulse p-6">
      <div className="h-7 w-44 rounded-md bg-zinc-200" />
      <div className="mt-2 h-4 w-72 rounded bg-zinc-200/70" />
      <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }).map((_, index) => (
          <div key={index} className="h-32 rounded-2xl bg-zinc-200/60" />
        ))}
      </div>
    </main>
  );
}
