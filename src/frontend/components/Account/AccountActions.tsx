'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useAuth } from './AuthProvider';
import { errorMessage } from '@/lib/account-api';
import { canManage } from '@/lib/management-navigation';

export default function AccountActions() {
  const { user, loading, request, notice } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function logout() {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await request('/auth/dang-xuat', 'POST');
      window.location.replace('/home');
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12 }}>
    {loading ? <span role="status">Đang kiểm tra…</span> : user ? <>
      {canManage(user.loaiTaiKhoan) && <Link href="/quan-tri">Quản lý</Link>}
      {user.loaiTaiKhoan === 'NHAN_VIEN' && <Link href="/nhan-vien">Lịch chuyến của tôi</Link>}
      <Link href="/tai-khoan">{user.hoTen}</Link>
      <button className="ghost-button" disabled={busy} onClick={logout}>Đăng xuất</button>
    </> : <Link className="ghost-button" href="/dang-nhap">Đăng nhập</Link>}
    {(error || notice) && <span role="status">{error || notice}</span>}
  </div>;
}
