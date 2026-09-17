import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = {
  title: '打样任务',
};

export default function SamplerLayout({ children }: { children: ReactNode }) {
  return children;
}
