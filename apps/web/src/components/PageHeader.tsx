import Link from "next/link";

interface PageHeaderProps {
  title: string;
  description?: string;
  backHref?: string;
  backLabel?: string;
  action?: React.ReactNode;
}

/** 统一页面头部：返回链接 + 标题 + 说明 + 右侧主操作 */
export default function PageHeader({
  title,
  description,
  backHref,
  backLabel = "返回",
  action,
}: PageHeaderProps) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {backHref && (
          <Link
            href={backHref}
            className="text-xs text-zinc-500 transition hover:text-zinc-900"
          >
            ← {backLabel}
          </Link>
        )}
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-zinc-900">{title}</h1>
        {description && (
          <p className="mt-1 max-w-[62ch] text-sm leading-relaxed text-zinc-500">{description}</p>
        )}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </header>
  );
}
