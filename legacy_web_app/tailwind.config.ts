import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "#FFFFFF",
        foreground: "#000000",
        primary: "#0052FF",
        "primary-hover": "#0038B6",
        secondary: "#00D4FF",
        neutral: "#000000",
        surface: "#F2F4F7",
        "surface-outline": "#E1E4EA",
      },
      fontFamily: {
        sans: ["var(--font-space-grotesk)"],
      },
    },
  },
  plugins: [],
};
export default config;
