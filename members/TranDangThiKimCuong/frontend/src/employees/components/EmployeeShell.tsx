'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import SiteLogo from '@shared/components/SiteHeader/SiteLogo';
import { useRouter } from 'next/navigation';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';

/**
 * Zone 3 — Employee Shell
 * Dark topbar, no sidebar, content centered.
 * Only accessible to NHAN_VIEN role.
 */
export default function EmployeeShell({ children }: { children: React.ReactNode }) {
  const { user, loading, request, clear } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.replace('/dang-nhap?next=/nhan-vien');
    }
  }, [loading, user, router]);

  if (loading) {
    return (
      <div className="employee-layout">
        <div style={{ padding: '60px 24px', textAlign: 'center', color: 'var(--text-secondary)' }}>
          Đang kiểm tra phiên đăng nhập…
        </div>
      </div>
    );
  }

  if (!user) return null;

  if (user.loaiTaiKhoan !== 'NHAN_VIEN') {
    return (
      <div className="employee-layout">
        <div style={{ padding: '60px 24px', textAlign: 'center', color: 'var(--text-secondary)' }}>
          Khu vực này chỉ dành cho tài khoản nhân viên.{' '}
          <Link href="/home" style={{ color: 'var(--primary)', fontWeight: 700 }}>Về trang chủ</Link>
        </div>
      </div>
    );
  }

  async function logout() {
    try { await request('/auth/dang-xuat', 'POST'); clear(); router.replace('/home'); }
    catch { /* silent */ }
  }

  return (
    <div className="employee-layout">
      {/* Zone 3 — Employee Topbar */}
      <header className="employee-topbar" role="banner">
        <Link className="brand" href="/home" aria-label="Về trang đặt vé">
          <SiteLogo />
        </Link>

        <span className="employee-badge">NHÂN VIÊN</span>

        <nav aria-label="Điều hướng nhân viên" style={{ display: 'flex', gap: '24px', flex: 1 }}>
          <Link href="/nhan-vien" style={{ color: 'rgba(255,255,255,.85)', fontSize: '14px', fontWeight: 600 }}>
            📋 Không gian nhân viên
          </Link>
        </nav>

        <div className="employee-user">
          <span style={{ color: 'rgba(255,255,255,.7)', fontSize: '14px' }}>{user.hoTen}</span>
          <button
            onClick={() => void logout()}
            style={{
              padding: '8px 16px',
              borderRadius: 'var(--radius-md)',
              background: 'rgba(255,255,255,.12)',
              color: '#fff',
              fontSize: '13px',
              fontWeight: 600,
              transition: 'background var(--transition)',
            }}
          >
            Đăng xuất
          </button>
        </div>
      </header>

      {/* Zone 3 — Content */}
      <main className="employee-content" role="main">
        {children}
      </main>
    </div>
  );
}
