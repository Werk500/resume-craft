import type { Config } from "tailwindcss";
import defaultTheme from "tailwindcss/defaultTheme";

const config: Config = {
  darkMode: ["class"],
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          "var(--font-outfit)",
          ...defaultTheme.fontFamily.sans,
          "PingFang SC",
          "Microsoft YaHei",
          "Noto Sans SC",
        ],
        mono: ["var(--font-mono)", ...defaultTheme.fontFamily.mono],
      },
      colors: {
        // 单一强调色（替代 Tailwind 默认 blue，降低“AI 默认色”观感）
        brand: {
          50: "#eef4ff",
          100: "#dbe6ff",
          200: "#bed0ff",
          300: "#93b0ff",
          400: "#6289fb",
          500: "#3d63e8",
          600: "#2c4cc9",
          700: "#243da3",
          800: "#1f3582",
          900: "#1d2f6b",
        },
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
        control: "0.625rem",
      },
      boxShadow: {
        card: "0 1px 2px rgba(15,23,42,0.04), 0 8px 24px -12px rgba(15,23,42,0.12)",
        "card-hover": "0 2px 4px rgba(15,23,42,0.05), 0 16px 32px -16px rgba(15,23,42,0.18)",
        focus: "0 0 0 3px rgba(61,99,232,0.20)",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
};

export default config;
