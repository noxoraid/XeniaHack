import type { ReactNode } from 'react';

export const metadata = { title: 'Nihongo Promo' };

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body style={{ margin: 0, background: '#0b0b0c', color: '#f3efe7', fontFamily: 'system-ui, sans-serif' }}>
        {children}
      </body>
    </html>
  );
}
