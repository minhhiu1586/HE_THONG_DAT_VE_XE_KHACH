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

  async function logout() {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await request('/auth/dang-xuat', 'POST');
      clear();
      router.replace('/home');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể đăng xuất.');
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <span role="status">Đang kiểm tra phiên…</span>;
  if (!user) return <Link className="ghost-button" href="/dang-nhap">Đăng nhập</Link>;
  return <div className="account-actions">
    {canManage(user.loaiTaiKhoan) && <Link href="/quan-tri">Quản lý</Link>}
    {user.loaiTaiKhoan === 'NHAN_VIEN' && <Link href="/nhan-vien">Lịch chuyến của tôi</Link>}
    <Link href="/tai-khoan">{user.hoTen}</Link>
    <button className="ghost-button" type="button" disabled={busy} onClick={() => void logout()}>Đăng xuất</button>
    {error && <span role="alert">{error}</span>}
  </div>;
}
