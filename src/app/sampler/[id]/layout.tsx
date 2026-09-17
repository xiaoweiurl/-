import type { Metadata, Viewport } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = {
  title: '打样任务',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  viewportFit: 'cover',
};

export default function SamplerLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-[100dvh] bg-[#F2F2F7]" style={{ colorScheme: 'light' }}>
      {children}
    </div>
  );
}
