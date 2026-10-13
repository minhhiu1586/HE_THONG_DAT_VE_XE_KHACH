'use client';

import Link from 'next/link';
import SiteLogo from '@/components/SiteHeader/SiteLogo';
import { usePathname } from 'next/navigation';
import AccountActions from '@/components/SiteHeader/AccountActions';

/**
 * Zone 1 & 2 shell — Public guests + Customer pages.
 * Shows sticky header with nav + footer.
 * Skipped automatically for /quan-tri and /nhan-vien via root layout.
 */
export default function PublicShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  // Active nav helper
  const isActive = (href: string) =>
    pathname === href || (href !== '/home' && pathname.startsWith(href));

  return (
    <>
      {/* ── Zone 1 & 2: Sticky Public Header ── */}
      <header className="topbar">
        <Link className="brand" href="/home" aria-label="Trang chủ Vexere">
          <SiteLogo />
        </Link>

        <nav aria-label="Điều hướng chính">
          <Link
            href="/home"
            aria-current={pathname === '/home' ? 'page' : undefined}
          >
            Trang chủ
          </Link>
          <Link
            href="/trips"
            aria-current={isActive('/trips') ? 'page' : undefined}
          >
            Tìm chuyến
          </Link>
          <a href="#support">Hỗ trợ</a>
        </nav>

        <AccountActions />
      </header>

      {/* ── Page Content ── */}
      {children}

      {/* ── Zone 1: Footer ── */}
      <footer id="support" role="contentinfo">
        <div>
          <Link className="brand footer-brand" href="/home" aria-label="Trang chủ Vexere"><SiteLogo /></Link>
          <p className="footer-tagline">Đặt vé nhanh chóng, an tâm trên mọi hành trình.</p>
        </div>
        <div>
          <strong>Hỗ trợ khách hàng</strong>
          <p>Trung tâm trợ giúp</p>
          <p>Chính sách hoàn vé</p>
          <p>Câu hỏi thường gặp</p>
        </div>
        <div>
          <strong>Hotline 24/7</strong>
          <p className="hotline">1900 6067</p>
          <p style={{ fontSize: '13px', marginTop: '8px', color: '#7aafab' }}>
            Thứ 2 – Chủ nhật, 7:00 – 22:00
          </p>
        </div>
        <div className="footer-bottom">
          <span>© {new Date().getFullYear()} Vexere. Bảo lưu mọi quyền.</span>
          <span>Đặt vé xe khách trực tuyến · An toàn · Tiện lợi</span>
        </div>
      </footer>
    </>
  );
}
