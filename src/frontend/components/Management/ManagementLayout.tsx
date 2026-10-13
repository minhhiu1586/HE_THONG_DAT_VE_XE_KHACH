'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import SiteLogo from '@/components/SiteHeader/SiteLogo';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/components/Account/AuthProvider';
import { canManage, navigationFor } from '@/lib/management-navigation';
import styles from './Management.module.css';

/**
 * Zone 4 (NHA_XE) + Zone 5 (ADMIN) — Management Layout
 * Sidebar + content area.
 * Menu items and data scope differ by role (handled by navigationFor + backend).
 */
export default function ManagementLayout({ children }: { children: React.ReactNode }) {
  const { user, loading, request, clear } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [menuRoute, setMenuRoute] = useState<string | null>(null);
  const menuOpen = menuRoute === pathname;

  useEffect(() => {
    if (!loading && !user) router.replace(`/dang-nhap?next=${encodeURIComponent(pathname)}`);
  }, [loading, user, router, pathname]);


  if (loading || !user) {
    return (
      <div className={styles.message} role="status">
        Đang kiểm tra phiên đăng nhập…
      </div>
    );
  }
  if (!canManage(user.loaiTaiKhoan)) {
    return (
      <div className={styles.message} role="alert">
        Bạn không có quyền truy cập khu vực quản lý.{' '}
        <Link href="/home" style={{ color: 'var(--primary)', fontWeight: 700 }}>Về trang chủ</Link>
      </div>
    );
  }

  const isAdmin = user.loaiTaiKhoan === 'ADMIN';
  const navItems = navigationFor(user.loaiTaiKhoan);

  async function logout() {
    try { await request('/auth/dang-xuat', 'POST'); clear(); router.replace('/home'); }
    catch { /* silent */ }
  }

  return (
    <div className={styles.shell}>
      {/* ── Zones 4+5: Sidebar ── */}
      <aside className={styles.sidebar} role="navigation" aria-label="Điều hướng quản lý">
        {/* Sidebar header */}
        <Link className={`brand ${styles.sidebarBrand}`} href="/home" aria-label="Về trang đặt vé"><SiteLogo /></Link>
        <div className={styles.sidebarHeading}>
          <div>
            <strong>{isAdmin ? 'Quản trị hệ thống' : 'Quản lý nhà xe'}</strong>
            <small>{user.hoTen}</small>
          </div>
          <button
            className={styles.toggle}
            aria-label="Mở/đóng menu"
            aria-expanded={menuOpen}
            aria-controls="management-navigation"
            onClick={() => setMenuRoute(menuOpen ? null : pathname)}
          >
            {menuOpen ? '✕' : '☰'}
          </button>
        </div>

        {/* Navigation links */}
        <nav
          id="management-navigation"
          className={`${styles.navigation} ${menuOpen ? styles.expanded : ''}`}
        >
          <Link
            href="/quan-tri"
            aria-current={pathname === '/quan-tri' ? 'page' : undefined}
          >
            Tổng quan
          </Link>

          {navItems.map(item => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={
                pathname === item.href || pathname.startsWith(`${item.href}/`)
                  ? 'page'
                  : undefined
              }
            >
              {item.label}
            </Link>
          ))}

          <div className={styles.divider} />

          <Link href="/tai-khoan">
            Hồ sơ cá nhân
          </Link>
          <Link href="/home">
            Về trang đặt vé
          </Link>
          <button
            className={styles.logout}
            onClick={() => void logout()}
          >
            Đăng xuất
          </button>
        </nav>
      </aside>

      {/* ── Content Area ── */}
      <main className={styles.content} role="main" id="main-content">
        {children}
      </main>
    </div>
  );
}
