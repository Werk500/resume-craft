# UI 优化方案（基于 redesign-existing-projects skill）

> 状态：分批实施中（每批完成后验收并同步）
> 范围：`apps/web`（Next.js 14 App Router + Tailwind v3）

## 0. Design Read

读到的是：**面向校招面试官与求职者的产品型 SaaS 工具（B2C 效率工具 + 作品展示面），希望呈现克制、可信、有工程质感的语言，倾向 Tailwind 工具类 + 有性格的字体 + 单一强调色 + 克制动效。**

**Dials**

| 面 | DESIGN_VARIANCE | MOTION_INTENSITY | VISUAL_DENSITY |
|---|---|---|---|
| 门面页（首页 / 登录 / 注册 / 404） | 7 | 5 | 4 |
| 产品页（简历 / 匹配 / 版本 / 投递） | 4 | 3 | 6 |

**范围声明**：密集产品 UI（投递看板、匹配结果、版本列表）不在 taste skill 的核心范围，只应用其可访问性、状态、排版、反馈规则；完整设计语言重构用于门面页。

## 1. 审计结果（实测）

| 检查项 | 实测 | 问题 |
|---|---|---|
| 字体 | 无 `next/font`、无自定义字体 | 依赖系统默认，跨平台不一致，数字不好看 |
| 焦点可见性 | 25 处 `outline-none` | 键盘用户看不到焦点，无障碍硬伤 |
| 卡片模式 | 19 处 `rounded-2xl border border-slate-200 bg-white shadow-sm` | 通用 AI 卡片三件套，层级感弱 |
| 圆角系统 | `rounded-lg` 62 / `rounded-2xl` 31 / `rounded-xl` 31 / `rounded-full` 15 | 四套混用 |
| 强调色 | `blue-600` 17 次 | Tailwind 默认蓝，AI 默认色 |
| 渐变 | 3 处 | 平均线性渐变，可替换为更有质感的处理 |
| Emoji | 27 处 | 跨平台不一致，专业感弱 |
| Em-dash | 1 处 | skill 前置检查要求零 em-dash |
| 容器宽度 | `max-w-2xl/3xl/4xl/5xl/6xl` 混用 | 页面横向跳动 |
| 状态页 | 已补 error / loading / 404 | 局部空状态与骨架仍缺 |

## 2. 三批任务

### 第 1 批：低风险高收益（样式与配置）

- [x] 字体方案：Outfit（显示）+ JetBrains Mono（数字）+ 中文系统字体栈（next/font 内联，构建通过）
- [ ] 数字排版：分数 / 统计统一 `tabular-nums`（与第 3 批数据展示改造一起做）
- [x] 焦点可见性：8 个文件的 `outline-none` 补 `focus-visible` ring + 全局 `:focus-visible` 兜底
- [x] 设计 token 收口：新增 `brand` 色阶、`shadow-card/card-hover/focus`、`radius-control`
- [x] 主色校准：门面页（首页 / 登录 / 注册 / 导航）`blue-600` → `brand-600`
- [ ] Emoji → 图标（lucide）：移到第 2 批，与页面重构一起替换，避免重复改动
- [x] Em-dash 文案清理（岗位卡占位符改为"未提供"）
- [x] favicon（`app/icon.svg`）+ metadata（title 模板 + description + openGraph）
- [x] 首页后端健康检查改为超时 5s + 最多 3 次重试（原来只请求一次，失败后永久显示"未连接"）

### 第 2 批：门面页重构

- [x] 首页首屏改左对齐 hero（标题两行、主 CTA 首屏可见、右侧流程轨道）
- [x] 首页改"流程型 Bento"：8 个环节按使用顺序排布，非对称 6 列网格，CTA 底部对齐
- [x] 删除首页"后端未连接"状态显示（改为纯流程展示）
- [x] 配色改为灰白基底 + 科技绿点缀（zinc + emerald），去掉蓝白观感
- [x] 同源代理：`next.config.mjs` rewrites 把 `/api/*`、`/actuator/*` 转发到网关 8080，前端默认同源请求，不再依赖 CORS
- [ ] 登录 / 注册改分屏：左侧价值主张，右侧表单（inline 校验 / 加载 / 错误态）—— 配色已统一，布局待做
- [ ] 404 / error 品牌化轻量插画与微动效
- [ ] 统一主容器（1200px）与 `PageHeader`（返回 / 标题 / 主操作）—— 首页已用 `max-w-6xl`，其余页面待统一

### 第 3 批：产品面体验

- [ ] 简历列表 / 岗位库 / 投递看板 / 匹配结果：骨架屏 + 有引导的空状态
- [ ] 数据展示改造：大数字 + 细线刻度替代填充进度条；关键词 chip 墙
- [ ] 基础组件统一（Button / Input / Field）四态：hover / active / disabled / loading
- [ ] 页面级过渡与列表 stagger 入场（含 `prefers-reduced-motion` 兜底）
- [ ] 无障碍复审：焦点顺序、对比度 ≥ 4.5:1、label 关联、图片 alt

## 3. 验证方式（每批必做）

1. `npx tsc --noEmit` 与 `npm run build` 必须通过
2. 键盘 Tab 走查所有交互元素有可见焦点
3. 375px 移动端走查（导航、表单、卡片不溢出）
4. `prefers-reduced-motion` 下动效降级
5. Lighthouse / axe 抽查对比度与可访问性

## 4. 不做的改动（Preservation Rules）

- 不改路由 slug、导航标签、表单字段名（避免破坏已有习惯与埋点）
- 不更换组件库（继续 Tailwind + lucide-react，不引入新 UI 库）
- 不引入 dark mode（除非明确要求）
- 不在产品页做高方差布局（保持数据可读性）

## 5. 进度记录

| 批次 | 状态 | 提交 |
|---|---|---|
| 第 1 批 | 已完成（`tsc` + `build` 通过） | 见 git log |
| 第 2 批 | 进行中（首页流程 Bento + 灰白科技风 + 同源代理已完成；登录分屏 / 404 待做） | 见 git log |
| 第 3 批 | 待开始 | - |
