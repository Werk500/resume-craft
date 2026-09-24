"use client";

import type { ReactNode } from "react";

/**
 * 轻量 Markdown 渲染器。
 *
 * <h3>为什么自己写而不是引库</h3>
 * Agent 的回复只用到有限的几种语法（标题、无序/有序列表、表格、加粗、行内代码），
 * 为此引入 react-markdown + remark-gfm 会增加约 100KB 依赖。
 * 这里用不到 100 行覆盖实际用到的子集，且完全可控。
 *
 * <h3>支持的语法</h3>
 * - `## 标题` / `### 标题`
 * - `- 项` 无序列表、`1. 项` 有序列表
 * - `| a | b |` 表格（含分隔行 `|---|---|`）
 * - `**加粗**`、`` `行内代码` ``
 *
 * 不支持的语法原样按段落输出，不会报错。
 */
export default function MarkdownLite({ text }: { text: string }) {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  const blocks: ReactNode[] = [];

  let i = 0;
  let key = 0;

  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();

    // 空行：跳过
    if (!trimmed) {
      i += 1;
      continue;
    }

    // ---------- 表格 ----------
    // 判定条件：当前行是 |...|，且下一行是分隔行 |---|---|
    if (trimmed.startsWith("|") && isTableSeparator(lines[i + 1])) {
      const header = splitRow(trimmed);
      const rows: string[][] = [];
      i += 2; // 跳过表头与分隔行
      while (i < lines.length && lines[i].trim().startsWith("|")) {
        rows.push(splitRow(lines[i].trim()));
        i += 1;
      }
      blocks.push(
        <div key={key++} className="my-2 overflow-x-auto">
          <table className="w-full border-collapse text-xs">
            <thead>
              <tr className="border-b border-zinc-200">
                {header.map((cell, ci) => (
                  <th key={ci} className="px-2 py-1.5 text-left font-medium text-zinc-500">
                    {inline(cell)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, ri) => (
                <tr key={ri} className="border-b border-zinc-100 last:border-0">
                  {row.map((cell, ci) => (
                    <td key={ci} className="px-2 py-1.5 align-top text-zinc-700">
                      {inline(cell)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>,
      );
      continue;
    }

    // ---------- 标题 ----------
    const heading = /^(#{2,4})\s+(.*)$/.exec(trimmed);
    if (heading) {
      const level = heading[1].length;
      const content = inline(heading[2]);
      blocks.push(
        <p
          key={key++}
          className={
            level <= 2
              ? "mt-3 mb-1 text-sm font-semibold text-zinc-900"
              : "mt-2 mb-1 text-xs font-semibold text-zinc-700"
          }
        >
          {content}
        </p>,
      );
      i += 1;
      continue;
    }

    // ---------- 无序列表 ----------
    if (/^[-*]\s+/.test(trimmed)) {
      const items: string[] = [];
      while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^[-*]\s+/, ""));
        i += 1;
      }
      blocks.push(
        <ul key={key++} className="my-1.5 space-y-1">
          {items.map((item, ii) => (
            <li key={ii} className="flex gap-2 text-sm leading-relaxed text-zinc-700">
              <span className="shrink-0 text-zinc-400" aria-hidden>
                •
              </span>
              <span className="min-w-0">{inline(item)}</span>
            </li>
          ))}
        </ul>,
      );
      continue;
    }

    // ---------- 有序列表 ----------
    if (/^\d+\.\s+/.test(trimmed)) {
      const items: string[] = [];
      while (i < lines.length && /^\d+\.\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^\d+\.\s+/, ""));
        i += 1;
      }
      blocks.push(
        <ol key={key++} className="my-1.5 space-y-1">
          {items.map((item, ii) => (
            <li key={ii} className="flex gap-2 text-sm leading-relaxed text-zinc-700">
              <span className="shrink-0 text-zinc-400" aria-hidden>
                {ii + 1}.
              </span>
              <span className="min-w-0">{inline(item)}</span>
            </li>
          ))}
        </ol>,
      );
      continue;
    }

    // ---------- 普通段落 ----------
    blocks.push(
      <p key={key++} className="my-1 text-sm leading-relaxed text-zinc-700">
        {inline(trimmed)}
      </p>,
    );
    i += 1;
  }

  return <div className="min-w-0">{blocks}</div>;
}

/** 是否为表格分隔行，如 |---|---| 或 | :--- | ---: | */
function isTableSeparator(line: string | undefined): boolean {
  if (!line) return false;
  const trimmed = line.trim();
  return trimmed.startsWith("|") && /^\|[\s:|-]+\|$/.test(trimmed) && trimmed.includes("-");
}

/** 拆分表格行，去掉首尾空单元格 */
function splitRow(line: string): string[] {
  const body = line.replace(/^\|/, "").replace(/\|$/, "");
  return body.split("|").map((c) => c.trim());
}

/**
 * 处理行内语法：**加粗** 与 `代码`。
 * 用一次性扫描避免正则嵌套带来的转义问题，且不引入 dangerouslySetInnerHTML。
 */
function inline(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let key = 0;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }
    const token = match[0];
    if (token.startsWith("**")) {
      nodes.push(
        <strong key={key++} className="font-semibold text-zinc-900">
          {token.slice(2, -2)}
        </strong>,
      );
    } else {
      nodes.push(
        <code
          key={key++}
          className="rounded bg-zinc-100 px-1 py-0.5 font-mono text-[0.85em] text-zinc-800"
        >
          {token.slice(1, -1)}
        </code>,
      );
    }
    lastIndex = match.index + token.length;
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }
  return nodes;
}
