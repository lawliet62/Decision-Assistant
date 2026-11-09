import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import Link from "next/link";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Decision Assistant",
  description: "비교형 개인 의사결정을 구조화하고 결과를 회고하는 도구",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="ko"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col bg-zinc-50 text-zinc-950">{children}
        <footer className="mx-auto w-full max-w-5xl px-6 py-6 text-sm text-zinc-500"><Link href="/privacy" className="underline">데이터 이용·삭제 안내</Link></footer>
      </body>
    </html>
  );
}
