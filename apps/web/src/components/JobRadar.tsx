"use client";

import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
} from "recharts";
import type { RadarDimension } from "@/lib/types";

/**
 * JD 能力雷达图（岗位对硬技能/软技能/学历经验/项目经验/工具熟练度的要求画像）
 */
export default function JobRadar({ data }: { data: RadarDimension[] }) {
  if (!data || data.length === 0) {
    return (
      <p className="py-6 text-center text-xs text-slate-400">
        暂无雷达数据（AI 未返回维度评分）
      </p>
    );
  }
  return (
    <div className="w-full">
      <ResponsiveContainer width="100%" height={230}>
        <RadarChart data={data} outerRadius="70%">
          <PolarGrid stroke="#e2e8f0" />
          <PolarAngleAxis dataKey="name" tick={{ fontSize: 12, fill: "#64748b" }} />
          <PolarRadiusAxis domain={[0, 100]} tick={false} axisLine={false} />
          <Radar
            dataKey="score"
            stroke="#2563eb"
            fill="#3b82f6"
            fillOpacity={0.45}
          />
          <Tooltip formatter={(v: number) => [`${v} 分`, "要求强度"]} />
        </RadarChart>
      </ResponsiveContainer>
      <div className="mt-1 flex flex-wrap justify-center gap-2 text-[11px] text-slate-500">
        {data.map((d) => (
          <span key={d.name} className="rounded bg-slate-100 px-1.5 py-0.5">
            {d.name} {d.score}
          </span>
        ))}
      </div>
    </div>
  );
}
