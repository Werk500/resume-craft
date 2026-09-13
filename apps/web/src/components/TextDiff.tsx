"use client";

/**
 * 真正的行级 Diff（LCS 对齐，无第三方依赖）
 * 左右并排：左 = oldText，右 = newText。
 * 逐行对齐后：
 *   - 两侧都有的行 → 灰色（未变化）
 *   - 只存在于左侧 → 红色（被删除/改写）
 *   - 只存在于右侧 → 绿色（新增）
 */

type SideState = "same" | "deleted" | "added";

interface DiffRow {
  oldLine: { text: string; state: SideState } | null;
  newLine: { text: string; state: SideState } | null;
}

interface TextDiffProps {
  oldText: string;
  newText: string;
}

export default function TextDiff({ oldText, newText }: TextDiffProps) {
  const rows = computeDiff(oldText.split("\n"), newText.split("\n"));

  const added = rows.filter((r) => !r.oldLine && r.newLine).length;
  const removed = rows.filter((r) => r.oldLine && !r.newLine).length;
  const unchanged = rows.length - added - removed;

  if (rows.length === 0) {
    return (
      <p className="rounded-xl border border-zinc-200 bg-zinc-50 p-6 text-center text-sm text-zinc-400">
        两个版本内容均为空，无可对比内容
      </p>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white">
      <div className="flex items-center justify-between border-b border-zinc-100 bg-zinc-50 px-3 py-2 text-xs text-zinc-500">
        <span>共 {rows.length} 行</span>
        <span className="flex gap-3">
          <span className="text-green-600">+ {added}</span>
          <span className="text-red-500">- {removed}</span>
          <span className="text-zinc-400">= {unchanged}</span>
        </span>
      </div>

      <div className="max-h-[560px] overflow-auto">
        {rows.map((row, i) => {
          const isDeleted = !!row.oldLine && !row.newLine;
          const isAdded = !!row.newLine && !row.oldLine;
          const rowBg = isDeleted
            ? "bg-red-50/70"
            : isAdded
              ? "bg-green-50/70"
              : "bg-white";

          return (
            <div
              key={i}
              className={`grid grid-cols-[minmax(0,1fr)_2rem_minmax(0,1fr)] border-b border-zinc-100 last:border-b-0 ${rowBg}`}
            >
              <LineCell
                text={row.oldLine?.text ?? ""}
                state={row.oldLine?.state ?? null}
                empty={!row.oldLine}
              />
              <div className="flex items-center justify-center text-[10px]">
                {isDeleted ? (
                  <span className="font-bold text-red-400">-</span>
                ) : isAdded ? (
                  <span className="font-bold text-green-500">+</span>
                ) : (
                  <span className="text-zinc-300">·</span>
                )}
              </div>
              <LineCell
                text={row.newLine?.text ?? ""}
                state={row.newLine?.state ?? null}
                empty={!row.newLine}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
}

function LineCell({
  text,
  state,
  empty,
}: {
  text: string;
  state: SideState | null;
  empty: boolean;
}) {
  if (empty) return <div className="min-h-[1.5rem]" />;

  const color =
    state === "deleted"
      ? "text-red-700"
      : state === "added"
        ? "text-green-700"
        : "text-zinc-600";

  return (
    <div
      className={`whitespace-pre-wrap break-words px-3 py-1 text-xs leading-relaxed ${color}`}
    >
      {text || "\u00A0"}
    </div>
  );
}

/**
 * 最长公共子序列（LCS）行对齐。
 * 相同行保持原位（灰色），删除行留在左侧，新增行排到右侧。
 */
function computeDiff(oldLines: string[], newLines: string[]): DiffRow[] {
  const n = oldLines.length;
  const m = newLines.length;

  // dp[i][j]：oldLines[i..] 与 newLines[j..] 的 LCS 长度
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] =
        oldLines[i] === newLines[j]
          ? dp[i + 1][j + 1] + 1
          : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }

  const rows: DiffRow[] = [];
  let i = 0;
  let j = 0;

  while (i < n && j < m) {
    if (oldLines[i] === newLines[j]) {
      rows.push({
        oldLine: { text: oldLines[i], state: "same" },
        newLine: { text: newLines[j], state: "same" },
      });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      // 优先判定为“旧版删除”，保证并排时删除显示在新增之前
      rows.push({ oldLine: { text: oldLines[i], state: "deleted" }, newLine: null });
      i++;
    } else {
      rows.push({ oldLine: null, newLine: { text: newLines[j], state: "added" } });
      j++;
    }
  }

  while (i < n) {
    rows.push({ oldLine: { text: oldLines[i], state: "deleted" }, newLine: null });
    i++;
  }
  while (j < m) {
    rows.push({ oldLine: null, newLine: { text: newLines[j], state: "added" } });
    j++;
  }

  return rows;
}
