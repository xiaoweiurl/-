'use client';

import { SettingsProvider } from '@/contexts/SettingsContext';
import { NotificationProvider } from '@/contexts/NotificationContext';

export function ClientProviders({ children }: { children: React.ReactNode }) {
  return (
    <SettingsProvider>
      <NotificationProvider>{children}</NotificationProvider>
    </SettingsProvider>
  );
}
