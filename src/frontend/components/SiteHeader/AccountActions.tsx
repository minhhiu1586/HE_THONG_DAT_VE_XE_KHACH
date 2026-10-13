'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useAuth } from '@/components/Account/AuthProvider';
import { canManage } from '@/lib/management-navigation';

export default function AccountActions() {
  const { user, loading, request, clear } = useAuth();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);

  async function logout() {
    if (busy) return;
    setBusy(true); setError('');
    try {
      await request('/auth/dang-xuat', 'POST');
      clear();
      router.replace('/home');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể đăng xuất.');
    } finally {
      setBusy(false); setOpen(false);
    }
  }

  if (loading) {
    return (
      <span role="status" style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
        Đang kiểm tra…
      </span>
    );
  }

  if (!user) {
    return (
      <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
        <Link
          href="/dang-ky"
          style={{
            fontSize: '14px',
            color: 'var(--primary)',
            fontWeight: 700,
            padding: '9px 16px',
            borderRadius: 'var(--radius-md)',
            border: '1.5px solid var(--border)',
            background: '#fff',
            transition: 'all var(--transition)',
            display: 'inline-flex',
            alignItems: 'center',
            textDecoration: 'none',
          }}
          onMouseEnter={e => {
            (e.currentTarget as HTMLAnchorElement).style.background = 'var(--primary-light)';
            (e.currentTarget as HTMLAnchorElement).style.borderColor = 'var(--primary)';
          }}
          onMouseLeave={e => {
            (e.currentTarget as HTMLAnchorElement).style.background = '#fff';
            (e.currentTarget as HTMLAnchorElement).style.borderColor = 'var(--border)';
          }}
        >
          Đăng ký
        </Link>
        <Link
          href="/dang-nhap"
          id="btn-dang-nhap"
          style={{
            fontSize: '14px',
            fontWeight: 800,
            padding: '9px 20px',
            borderRadius: 'var(--radius-md)',
            background: 'linear-gradient(135deg, #1a56e8 0%, #0d2fa8 100%)',
            color: '#fff',
            border: 'none',
            boxShadow: '0 4px 14px rgba(26,86,232,.32)',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            textDecoration: 'none',
            transition: 'all var(--transition)',
          }}
          onMouseEnter={e => {
            (e.currentTarget as HTMLAnchorElement).style.transform = 'translateY(-2px)';
            (e.currentTarget as HTMLAnchorElement).style.boxShadow = '0 8px 22px rgba(26,86,232,.42)';
          }}
          onMouseLeave={e => {
            (e.currentTarget as HTMLAnchorElement).style.transform = '';
            (e.currentTarget as HTMLAnchorElement).style.boxShadow = '0 4px 14px rgba(26,86,232,.32)';
          }}
        >
          Đăng nhập
        </Link>
      </div>
    );
  }

  return (
    <div className="account-actions" style={{ position: 'relative' }}>
      {error && <span role="alert" style={{ color: 'var(--rose)', fontSize: '12px' }}>{error}</span>}

      {/* Dropdown trigger */}
      <button
        id="account-menu-trigger"
        aria-haspopup="true"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '8px 14px',
          borderRadius: 'var(--radius-md)',
          border: '1.5px solid var(--border)',
          background: '#fff',
          color: 'var(--text-primary)',
          fontWeight: 600,
          fontSize: '14px',
          cursor: 'pointer',
          transition: 'all var(--transition)',
        }}
        onMouseEnter={e => {
          (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--primary)';
          (e.currentTarget as HTMLButtonElement).style.color = 'var(--primary)';
        }}
        onMouseLeave={e => {
          if (!open) {
            (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--border)';
            (e.currentTarget as HTMLButtonElement).style.color = 'var(--text-primary)';
          }
        }}
      >
        <span className="account-name">
          {user.hoTen}
        </span>
        <span aria-hidden="true" style={{ fontSize: '10px', opacity: .6 }}>{open ? '▲' : '▼'}</span>
      </button>

      {/* Dropdown menu */}
      {open && (
        <>
          {/* Backdrop */}
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 99 }}
            onClick={() => setOpen(false)}
            aria-hidden="true"
          />
          <div
            className="account-menu"
            role="menu"
            aria-label="Menu tài khoản"
            style={{
              position: 'absolute',
              right: 0,
              top: 'calc(100% + 8px)',
              zIndex: 100,
              background: '#fff',
              border: '1.5px solid var(--border-2)',
              borderRadius: 'var(--radius-lg)',
              boxShadow: 'var(--shadow-lg)',
              maxWidth: 'calc(100vw - 32px)',
              overflow: 'hidden',
              animation: 'dropIn .18s ease',
            }}
          >
            <style>{`@keyframes dropIn{from{opacity:0;transform:translateY(-8px)}to{opacity:1;transform:none}}`}</style>

            {/* User info header */}
            <div style={{ padding: '16px 18px 12px', borderBottom: '1px solid var(--border-2)' }}>
              <div style={{ fontWeight: 700, fontSize: '15px' }}>{user.hoTen}</div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '3px' }}>
                {user.loaiTaiKhoan === 'ADMIN' ? '⚙ Quản trị viên'
                  : user.loaiTaiKhoan === 'NHA_XE' ? '🏢 Quản lý nhà xe'
                  : user.loaiTaiKhoan === 'NHAN_VIEN' ? '👷 Nhân viên'
                  : '👤 Hành khách'}
              </div>
            </div>

            {/* Menu items */}
            <div style={{ padding: '8px 0' }}>
              <Link
                href="/tai-khoan"
                role="menuitem"
                onClick={() => setOpen(false)}
                style={{
                  display: 'flex', gap: '10px', alignItems: 'center',
                  padding: '11px 18px', fontSize: '14px', color: 'var(--text-primary)',
                  transition: 'background var(--transition)',
                }}
                onMouseEnter={e => { (e.currentTarget as HTMLAnchorElement).style.background = 'var(--bg)'; }}
                onMouseLeave={e => { (e.currentTarget as HTMLAnchorElement).style.background = ''; }}
              >
                <span aria-hidden="true">👤</span> Hồ sơ cá nhân
              </Link>

              {canManage(user.loaiTaiKhoan) && (
                <Link
                  href="/quan-tri"
                  role="menuitem"
                  onClick={() => setOpen(false)}
                  style={{
                    display: 'flex', gap: '10px', alignItems: 'center',
                    padding: '11px 18px', fontSize: '14px', color: 'var(--text-primary)',
                    transition: 'background var(--transition)',
                  }}
                  onMouseEnter={e => { (e.currentTarget as HTMLAnchorElement).style.background = 'var(--bg)'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLAnchorElement).style.background = ''; }}
                >
                  <span aria-hidden="true">⚙</span>
                  {user.loaiTaiKhoan === 'ADMIN' ? 'Quản trị hệ thống' : 'Quản lý nhà xe'}
                </Link>
              )}

              {user.loaiTaiKhoan === 'NHAN_VIEN' && (
                <Link
                  href="/nhan-vien"
                  role="menuitem"
                  onClick={() => setOpen(false)}
                  style={{
                    display: 'flex', gap: '10px', alignItems: 'center',
                    padding: '11px 18px', fontSize: '14px', color: 'var(--text-primary)',
                    transition: 'background var(--transition)',
                  }}
                  onMouseEnter={e => { (e.currentTarget as HTMLAnchorElement).style.background = 'var(--bg)'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLAnchorElement).style.background = ''; }}
                >
                  <span aria-hidden="true">📋</span> Lịch chuyến của tôi
                </Link>
              )}

              <div style={{ borderTop: '1px solid var(--border-2)', margin: '8px 0' }} />

              <button
                role="menuitem"
                disabled={busy}
                onClick={() => void logout()}
                style={{
                  display: 'flex', gap: '10px', alignItems: 'center',
                  padding: '11px 18px', fontSize: '14px',
                  color: busy ? 'var(--text-muted)' : '#e11d48',
                  width: '100%', textAlign: 'left',
                  transition: 'background var(--transition)',
                  cursor: busy ? 'wait' : 'pointer',
                }}
                onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = '#fff1f2'; }}
                onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = ''; }}
              >
                <span aria-hidden="true">🚪</span>
                {busy ? 'Đang đăng xuất…' : 'Đăng xuất'}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
