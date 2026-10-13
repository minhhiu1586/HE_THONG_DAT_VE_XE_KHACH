'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from './AuthProvider';
import { errorMessage, Profile } from '@/lib/account-api';
import styles from './Account.module.css';
import { vietnameseValidation, clearValidation } from '@/lib/form-validation';

const roles = { KHACH_HANG: 'Khách hàng', NHA_XE: 'Quản lý nhà xe', NHAN_VIEN: 'Nhân viên', ADMIN: 'Quản trị viên' };
export default function ProfilePanel() {
  const { user, loading, request, refresh, clear } = useAuth(); const router = useRouter();
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [success, setSuccess] = useState('');
  const sending = useRef(false);
  useEffect(() => { if (!loading && !user) router.replace('/dang-nhap?next=/tai-khoan'); }, [loading, user, router]);
  async function submit(event: React.FormEvent<HTMLFormElement>, password = false) {
    event.preventDefault(); if (sending.current) return;
    const formElement = event.currentTarget, form = new FormData(formElement);
    if (password && form.get('matKhauMoi') !== form.get('xacNhan')) { setError('Hai mật khẩu mới chưa trùng nhau.'); return; }
    sending.current = true; setBusy(true); setError(''); setSuccess('');
    try {
      if (password) {
        await request('/tai-khoan/me/mat-khau', 'PUT', { matKhauHienTai: form.get('matKhauHienTai'), matKhauMoi: form.get('matKhauMoi') });
        formElement.reset(); clear('Đổi mật khẩu thành công. Các phiên cũ đã kết thúc; vui lòng đăng nhập lại.'); router.replace('/dang-nhap');
      } else {
        await request<Profile>('/tai-khoan/me', 'PUT', {
          hoTen: form.get('hoTen'), email: form.get('email'), soDienThoai: form.get('soDienThoai'),
          gioiTinh: form.get('gioiTinh') || null, ngaySinh: form.get('ngaySinh') || null, cccd: form.get('cccd') || null,
        });
        await refresh(); setSuccess('Đã cập nhật hồ sơ.');
      }
    } catch (error) { setError(errorMessage(error)); }
    finally { sending.current = false; setBusy(false); }
  }
  if (loading || !user) return <div className={styles.page}><p role="status">Đang kiểm tra phiên đăng nhập…</p></div>;
  return <div className={`${styles.page} ${styles.profile}`}>
    <header className={styles.profileHeading}><p className={styles.eyebrow}>TÀI KHOẢN CỦA BẠN</p><h1>Hồ sơ cá nhân</h1><p>Quản lý thông tin và bảo mật tài khoản.</p></header>
    {error && <p className={styles.error} role="alert">{error}</p>}
    {success && <p className={styles.success} role="status">{success}</p>}
    <section className={styles.card}><h2>Thông tin cá nhân</h2><p className={styles.muted}>Vai trò: {roles[user.loaiTaiKhoan]}</p>
      <form onInvalid={vietnameseValidation} onInput={clearValidation} className={styles.form} onSubmit={event => submit(event)} key={user.maNguoiDung + user.email + user.soDienThoai}>
        <label>Họ tên<input name="hoTen" defaultValue={user.hoTen} autoComplete="name" maxLength={160} required /></label>
        <label>Email<input name="email" type="email" defaultValue={user.email} autoComplete="email" maxLength={254} required /></label>
        <label>Số điện thoại<input name="soDienThoai" type="tel" defaultValue={user.soDienThoai} autoComplete="tel"
          pattern="(0[35789][0-9]{8}|\+84[35789][0-9]{8})" title="Số Việt Nam 10 chữ số hoặc dạng +84" required /></label>
        <div className={styles.row}><label>Giới tính<select name="gioiTinh" defaultValue={user.gioiTinh ?? ''}>
          <option value="">Chưa cung cấp</option><option value="NAM">Nam</option><option value="NU">Nữ</option><option value="KHAC">Khác</option>
        </select></label><label>Ngày sinh<input name="ngaySinh" type="date" defaultValue={user.ngaySinh ?? ''} min="1900-01-01" /></label></div>
        <label>CCCD / Hộ chiếu (không bắt buộc)<input name="cccd" defaultValue={user.cccd ?? ''} maxLength={20} /></label>
        <button className={styles.primary} disabled={busy}>{busy ? 'Đang xử lý…' : 'Lưu hồ sơ'}</button>
      </form>
    </section>
    <section className={styles.card}><h2>Đổi mật khẩu</h2><p className={styles.muted}>Sau khi đổi mật khẩu, bạn cần đăng nhập lại trên tất cả thiết bị.</p>
      <form onInvalid={vietnameseValidation} onInput={clearValidation} className={styles.form} onSubmit={event => submit(event, true)}>
        <label>Mật khẩu hiện tại<input name="matKhauHienTai" type="password" autoComplete="current-password" maxLength={128} required /></label>
        <label>Mật khẩu mới<input name="matKhauMoi" type="password" autoComplete="new-password" minLength={10} maxLength={128} required /></label>
        <p className={styles.muted}>Mật khẩu cần 10–128 ký tự, có chữ và số.</p>
        <label>Xác nhận mật khẩu mới<input name="xacNhan" type="password" autoComplete="new-password" maxLength={128} required /></label>
        <button className={styles.primary} disabled={busy}>{busy ? 'Đang xử lý…' : 'Đổi mật khẩu'}</button>
      </form>
    </section>
  </div>;
}
