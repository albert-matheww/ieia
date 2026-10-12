/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class", '[data-theme="dark"]'],
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // "Monitoring station" palette — a precision instrument panel, not eco-marketing.
        pine: "#1B4332",
        moss: "#2D6A4F",
        sage: "#52796F",
        paper: "#F6F4EF",
        ink: "#14201C",
        signal: "#E9C46A", // analog-gauge amber; used sparingly for key readouts & alerts
        // environmental categories
        "cat-carbon": "#40514E",
        "cat-energy": "#E9C46A",
        "cat-water": "#457B9D",
        "cat-waste": "#8A5A44",
        "cat-transport": "#6D6875",
        // semantic surface tokens (see index.css for light/dark values)
        surface: "rgb(var(--surface) / <alpha-value>)",
        "surface-2": "rgb(var(--surface-2) / <alpha-value>)",
        "surface-3": "rgb(var(--surface-3) / <alpha-value>)",
        line: "rgb(var(--line) / <alpha-value>)",
        content: "rgb(var(--content) / <alpha-value>)",
        "content-muted": "rgb(var(--content-muted) / <alpha-value>)",
        "content-dim": "rgb(var(--content-dim) / <alpha-value>)",
        brand: "rgb(var(--brand) / <alpha-value>)",
        "brand-strong": "rgb(var(--brand-strong) / <alpha-value>)",
        positive: "rgb(var(--positive) / <alpha-value>)",
        negative: "rgb(var(--negative) / <alpha-value>)",
      },
      fontFamily: {
        display: ['"Space Grotesk"', "system-ui", "sans-serif"],
        sans: ['"Inter"', "system-ui", "sans-serif"],
        mono: ['"IBM Plex Mono"', "ui-monospace", "SFMono-Regular", "monospace"],
      },
      borderRadius: { xs: "3px", sm: "4px", DEFAULT: "5px", md: "6px", lg: "8px" },
      boxShadow: {
        card: "0 1px 2px rgb(20 32 28 / 0.04), 0 1px 3px rgb(20 32 28 / 0.06)",
        pop: "0 8px 28px -6px rgb(20 32 28 / 0.18)",
      },
      keyframes: {
        "fade-up": { from: { opacity: 0, transform: "translateY(6px)" }, to: { opacity: 1, transform: "none" } },
      },
      animation: { "fade-up": "fade-up 0.28s cubic-bezier(0.22,1,0.36,1) both" },
    },
  },
  plugins: [],
};
