'use client';

/**
 * ZoneShell — Route-based layout dispatcher
 *
 * Maps URL prefixes to the 5 UI zones defined in T27:
 *
 *   Zone 1: Public          → /home, /trips, /tim-chuyen, …      → PublicShell (header + footer)
 *   Zone 2: Customer        → /dang-nhap, /dang-ky, /tai-khoan,  → PublicShell (same shell, auth-gated pages)
 *                             /dat-ve, /gio-hang, /thanh-toan
 *   Zone 3: Employee        → /nhan-vien                          → EmployeeShell (dark topbar, no sidebar)
 *   Zone 4: Bus Operator    → /quan-tri  (NHA_XE role)            → ManagementLayout (sidebar, via quan-tri/layout.tsx)
 *   Zone 5: System Admin    → /quan-tri  (ADMIN role)             → ManagementLayout (sidebar, via quan-tri/layout.tsx)
 *
 * Zones 4+5 share ManagementLayout — role-scoped via navigationFor().
 * ZoneShell only wraps PublicShell for zones 1 & 2.
 * Zones 3–5 manage their own layout via their route-segment layout.tsx.
 */

import { usePathname } from 'next/navigation';
import PublicShell from '@/components/SiteHeader/PublicShell';

/** Routes that own their own full-page layout (no public header/footer) */
const PRIVATE_ZONE_PREFIXES = [
  '/quan-tri',   // Zone 4 + 5: ManagementLayout via /quan-tri/layout.tsx
  '/nhan-vien',  // Zone 3: EmployeeShell via /nhan-vien/layout.tsx
];

function isPrivateZone(pathname: string): boolean {
  return PRIVATE_ZONE_PREFIXES.some(
    prefix => pathname === prefix || pathname.startsWith(prefix + '/')
  );
}

export default function ZoneShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  // Zones 3, 4, 5 — render children directly; their own layout.tsx handles shell
  if (isPrivateZone(pathname)) {
    return <>{children}</>;
  }

  // Zones 1 & 2 — wrap with public header + footer
  return <PublicShell>{children}</PublicShell>;
}
