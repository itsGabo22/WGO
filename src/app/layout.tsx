import type { Metadata, Viewport } from "next";
import { Space_Grotesk } from "next/font/google";
import "./globals.css";

const spaceGrotesk = Space_Grotesk({
  subsets: ["latin"],
  variable: "--font-space-grotesk",
  weight: ["500", "600", "700"],
});

export const metadata: Metadata = {
  title: "WGO Wearable Navigator",
  description: "AI Smartwatch Navigator",
};

export const viewport: Viewport = {
  themeColor: "#111111",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${spaceGrotesk.variable} antialiased`}>
        {/* Wearable Simulator Wrapper */}
        <div className="relative w-[340px] h-[340px] bg-background rounded-full overflow-hidden shadow-2xl flex flex-col border-[4px] border-neutral/10">
          {children}
        </div>
      </body>
    </html>
  );
}
