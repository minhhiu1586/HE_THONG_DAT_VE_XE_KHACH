'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/components/Account/AuthProvider';
import { canManage, navigationFor } from '@/lib/management-navigation';
import styles from './Management.module.css';

export default function ManagementLayout({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth(); const pathname = usePathname(); const router = useRouter();
  const [menuPath, setMenuPath] = useState<string | null>(null);
  const expanded = menuPath === pathname;
  useEffect(() => {
    if (!loading && !user) router.replace(`/dang-nhap?next=${encodeURIComponent(pathname)}`);
  }, [loading, user, router, pathname]);
  if (loading || !user) return <div className={styles.message} role="status">Đang kiểm tra phiên đăng nhập…</div>;
  if (!canManage(user.loaiTaiKhoan)) return <div className={styles.message} role="alert">Bạn không có quyền truy cập khu vực quản lý.</div>;
  return <div className={styles.shell}>
    <aside className={styles.sidebar}>
      <div className={styles.sidebarHeading}><div><strong>{user.loaiTaiKhoan === 'ADMIN' ? 'Quản trị hệ thống' : 'Quản lý nhà xe'}</strong><small>{user.hoTen}</small></div>
        <button className={styles.toggle} aria-label="Menu quản lý" aria-expanded={expanded} aria-controls="management-navigation" onClick={() => setMenuPath(expanded ? null : pathname)}>{expanded ? '✕' : '☰'}</button>
      </div>
      <nav id="management-navigation" aria-label="Điều hướng quản lý" className={`${styles.navigation} ${expanded ? styles.expanded : ''}`}>
        <Link href="/quan-tri" aria-current={pathname === '/quan-tri' ? 'page' : undefined} onClick={() => setMenuPath(null)}><span aria-hidden="true">▦</span>Tổng quan</Link>
        {navigationFor(user.loaiTaiKhoan).map(item => <Link key={item.href} href={item.href} aria-current={pathname === item.href || pathname.startsWith(`${item.href}/`) ? 'page' : undefined} onClick={() => setMenuPath(null)}><span aria-hidden="true">{item.icon}</span>{item.label}</Link>)}
        <div className={styles.divider} />
        <Link href="/tai-khoan" onClick={() => setMenuPath(null)}><span aria-hidden="true">👤</span>Hồ sơ cá nhân</Link>
        <Link href="/" onClick={() => setMenuPath(null)}><span aria-hidden="true">←</span>Về trang đặt vé</Link>
      </nav>
    </aside>
    <div className={styles.content}>{children}</div>
  </div>;
}
