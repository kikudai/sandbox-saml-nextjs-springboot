import type { ReactNode } from "react";
import "./globals.css";

export const metadata = {
  title: "SAML SSO Sandbox",
  description: "Next.js frontend for SAML and password login"
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ja">
      <body>{children}</body>
    </html>
  );
}
