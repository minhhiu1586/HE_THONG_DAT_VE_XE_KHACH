'use client';
import type { FormEventHandler } from 'react';
import type { Profile } from '../lib/account-types';
import { vietnameseValidation, clearValidation } from '@shared/lib/form-validation';
import styles from './Account.module.css';
const roles = {
  KHACH_HANG: 'Khách hàng',
  NHA_XE: 'Quản lý nhà xe',
  NHAN_VIEN: 'Nhân viên',
  ADMIN: 'Quản trị viên',
};
type Props = {
  user: Profile;
  busy: boolean;
  onSubmit: FormEventHandler<HTMLFormElement>;
};
export default function ProfileDetailsForm({ user, busy, onSubmit }: Props) {
  return (
    <section className={styles.card}>
      <h2>Thông tin cá nhân</h2>
      <p className={styles.muted}>Vai trò: {roles[user.loaiTaiKhoan]}</p>
      <form
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
        className={styles.form}
        onSubmit={onSubmit}
        key={user.maNguoiDung + user.email + user.soDienThoai}
      >
        <label>
          Họ tên
          <input
            name="hoTen"
            defaultValue={user.hoTen}
            autoComplete="name"
            maxLength={160}
            required
          />
        </label>
        <label>
          Email
          <input
            name="email"
            type="email"
            defaultValue={user.email}
            autoComplete="email"
            maxLength={254}
            required
          />
        </label>
        <label>
          Số điện thoại
          <input
            name="soDienThoai"
            type="tel"
            defaultValue={user.soDienThoai}
            autoComplete="tel"
            pattern="(0[35789][0-9]{8}|\+84[35789][0-9]{8})"
            title="Số Việt Nam 10 chữ số hoặc dạng +84"
            required
          />
        </label>
        <label>
          Giới tính
          <select name="gioiTinh" defaultValue={user.gioiTinh ?? ''}>
            <option value="">Chưa cung cấp</option>
            <option value="NAM">Nam</option>
            <option value="NU">Nữ</option>
            <option value="KHAC">Khác</option>
          </select>
        </label>
        <label>
          Ngày sinh
          <input name="ngaySinh" type="date" defaultValue={user.ngaySinh ?? ''} min="1900-01-01" />
        </label>
        <label>
          CCCD / Hộ chiếu (không bắt buộc)
          <input name="cccd" defaultValue={user.cccd ?? ''} maxLength={20} />
        </label>
        <button className={styles.primary} disabled={busy}>
          {busy ? 'Đang xử lý…' : 'Lưu hồ sơ'}
        </button>
      </form>
    </section>
  );
}
