'use client';

import { useState } from 'react';
import Link from 'next/link';
import styles from './Header.module.css';
import { useAuth } from '../Account/AuthProvider';
import { errorMessage } from '@/lib/account-api';

export default function Header() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { user, loading, notice, request } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  async function logout() {
    if (loggingOut) return;
    setLoggingOut(true); setLogoutError('');
    try { await request('/auth/dang-xuat', 'POST'); setMobileMenuOpen(false); window.location.replace('/'); }
    catch (error) { setLogoutError(errorMessage(error)); }
    finally { setLoggingOut(false); }
  }
  const accountActions = loading ? <span className={styles.actionLink}>Đang kiểm tra…</span> : user ? <>
    <Link href="/tai-khoan" className={styles.loginBtn}>👤 {user.hoTen}</Link>
    <button className={styles.loginBtn} onClick={logout} disabled={loggingOut}>{loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
  </> : <Link href="/dang-nhap" className={styles.loginBtn}>👤 Đăng nhập</Link>;

  return (
    <header>
      {/* Main Header */}
      <div className={styles.mainHeader}>
        <div className={styles.mainHeaderInner}>
          <Link href="/" className={styles.logo}>
            <span className={styles.logoIcon}>🚌</span>
            <span className={styles.logoText}>
              <span>VeXe</span>
              <span>Đặt vé xe khách online</span>
            </span>
          </Link>

          <nav className={styles.navTabs}>
            <button className={`${styles.navTab} ${styles.navTabActive}`}>
              <span className={styles.navTabIcon}>🚌</span>
              Xe khách
            </button>
          </nav>
          <div className={styles.headerRight}>
            <div className={styles.actionLinks}>
               <span className={styles.actionLink}>📋 Đơn hàng của tôi</span>
               <span className={styles.actionLink}>🏢 Mở bán vé trên VeXe</span>
               <span className={styles.actionLink}>🤝 Trở thành đối tác</span>
               <span className={`${styles.actionLink} ${styles.hotline}`}>
                  📞 Hotline 24/7
               </span>
            </div>
          </div>  
          <div className={styles.headerRight}>
            {accountActions}
          </div>

          <button
            className={styles.mobileMenuBtn}
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label="Menu"
          >
            {mobileMenuOpen ? '✕' : '☰'}
          </button>
        </div>
      </div>

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <div className={styles.mobileMenu}>
          <button className={`${styles.navTab} ${styles.navTabActive}`}>
            🚌 Xe khách
          </button>
          {user ? <>
            <Link href="/tai-khoan" onClick={() => setMobileMenuOpen(false)}>Hồ sơ cá nhân</Link>
            <button className={styles.navTab} onClick={logout} disabled={loggingOut}>Đăng xuất</button>
          </> : <Link href="/dang-nhap" onClick={() => setMobileMenuOpen(false)}>Đăng nhập / Đăng ký</Link>}
        </div>
      )}

      {(logoutError || notice) && <p role="status" style={{ padding: '12px', background: 'var(--warning-light)', textAlign: 'center' }}>{logoutError || notice}</p>}
      {/* Trust Badges */}
      <div className={styles.trustBar}>
        <div className={styles.trustBarInner}>
          <div className={styles.trustBadge}>
            <span className={styles.trustBadgeIcon}>✅</span>
            Chắc chắn có chỗ
          </div>
          <div className={styles.trustBadge}>
            <span className={styles.trustBadgeIcon}>🕐</span>
            Hỗ trợ 24/7
          </div>
          <div className={styles.trustBadge}>
            <span className={styles.trustBadgeIcon}>🎁</span>
            Nhiều ưu đãi
          </div>
          <div className={styles.trustBadge}>
            <span className={styles.trustBadgeIcon}>💳</span>
            Thanh toán đa dạng
          </div>
        </div>
      </div>
    </header>
  );
}
