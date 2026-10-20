'use client';
import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage, Profile } from '@members/NguyenMinhHieu/account/lib/account-api';
import styles from '@members/NguyenMinhHieu/account/components/Account.module.css';
import ProfileDetailsForm from './ProfileDetailsForm';
import ChangePasswordForm from './ChangePasswordForm';
import OrderHistoryPanel from '@members/NguyenTheAn/orders/components/OrderHistoryPanel';
export default function ProfilePanel() {
  const { user, loading, request, refresh, clear } = useAuth();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const sending = useRef(false);
  useEffect(() => {
    if (!loading && !user) router.replace('/dang-nhap?next=/tai-khoan');
  }, [loading, user, router]);
  async function saveProfile(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const form = new FormData(event.currentTarget);
    await submit(async () => {
      await request<Profile>('/tai-khoan/me', 'PUT', {
        hoTen: form.get('hoTen'),
        email: form.get('email'),
        soDienThoai: form.get('soDienThoai'),
        gioiTinh: form.get('gioiTinh') || null,
        ngaySinh: form.get('ngaySinh') || null,
        cccd: form.get('cccd') || null,
      });
      await refresh();
      setSuccess('Đã cập nhật hồ sơ.');
    });
  }

  async function changePassword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const element = event.currentTarget;
    const form = new FormData(element);
    if (form.get('matKhauMoi') !== form.get('xacNhan')) {
      setError('Hai mật khẩu mới chưa trùng nhau.');
      return;
    }
    await submit(async () => {
      await request('/tai-khoan/me/mat-khau', 'PUT', {
        matKhauHienTai: form.get('matKhauHienTai'),
        matKhauMoi: form.get('matKhauMoi'),
      });
      element.reset();
      clear('Đổi mật khẩu thành công. Các phiên cũ đã kết thúc; vui lòng đăng nhập lại.');
      router.replace('/dang-nhap');
    });
  }

  async function submit(action: () => Promise<void>) {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await action();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  if (loading || !user)
    return (
      <div className={styles.page}>
        <p role="status">Đang kiểm tra phiên đăng nhập…</p>
      </div>
    );
  return (
    <div className={`${styles.page} ${styles.profile}`}>
      <header className={styles.profileHeading}>
        <p className={styles.eyebrow}>TÀI KHOẢN CỦA BẠN</p>
        <h1>Hồ sơ cá nhân</h1>
        <p>Quản lý thông tin và bảo mật tài khoản.</p>
      </header>

      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
      {success && (
        <p className={styles.success} role="status">
          {success}
        </p>
      )}

      <div className={styles.profileGrid}>
        <ProfileDetailsForm user={user} busy={busy} onSubmit={saveProfile} />

        <ChangePasswordForm busy={busy} onSubmit={changePassword} />
      </div>

      <OrderHistoryPanel />
    </div>
  );
}
