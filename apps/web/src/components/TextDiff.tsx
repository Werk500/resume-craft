"use client";

/**
 * 版本 Diff 对比（左右并排）：
 * 左 = 旧版本，右 = 新版本
 * 只存在于左侧的行标红（被删除），只存在于右侧的行标绿（新增），其余灰色
 */
export default function TextDiff({ oldText, newText }: { oldText: string; newText: string }) {
  const oldLines = oldText.split("\n");
  const newLines = newText.split("\n");
  const oldSet = new Set(oldLines);
  const newSet = new Set(newLines);

  return (
    <div className="grid gap-2 sm:grid-cols-2">
      <DiffColumn
        title="旧版本"
        lines={oldLines}
        added={false}
        marker={newSet}
      />
      <DiffColumn
        title="新版本"
        lines={newLines}
        added
        marker={oldSet}
      />
    </div>
  );
}

function DiffColumn({
  title,
  lines,
  marker,
  added,
}: {
  title: string;
  lines: string[];
  marker: Set<string>;
  added: boolean;
}) {
  const bg = added ? "bg-green-50" : "bg-red-50";
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200">
      <p className={`px-3 py-1.5 text-xs font-medium ${added ? "bg-green-100 text-green-700" : "bg-red-100 text-red-600"}`}>
        {title}
      </p>
      <pre className="max-h-96 overflow-auto p-3 text-xs leading-relaxed text-slate-600">
        {lines.map((line, i) => {
          const changed = !marker.has(line); // 不在对方 = 本侧独有（新增/删除）
          return (
            <div
              key={i}
              className={`${changed ? `${bg} text-slate-700` : "text-slate-500"}`}
            >
              {changed ? (added ? "+ " : "- ") : "  "}
              {line}
            </div>
          );
        })}
      </pre>
    </div>
  );
}
