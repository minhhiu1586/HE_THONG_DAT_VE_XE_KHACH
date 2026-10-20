'use client';
import type { FormEventHandler } from 'react';
import { vietnameseValidation, clearValidation } from '@shared/lib/form-validation';
import styles from './Account.module.css';
type Props = {
  busy: boolean;
  onSubmit: FormEventHandler<HTMLFormElement>;
};
export default function ChangePasswordForm({ busy, onSubmit }: Props) {
  return (
    <section className={styles.card}>
      <h2>Đổi mật khẩu</h2>
      <p className={styles.muted}>
        Sau khi đổi mật khẩu, bạn cần đăng nhập lại trên tất cả thiết bị.
      </p>
      <form
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
        className={`${styles.form} ${styles.passwordForm}`}
        onSubmit={onSubmit}
      >
        <label>
          Mật khẩu hiện tại
          <input
            name="matKhauHienTai"
            type="password"
            autoComplete="current-password"
            maxLength={128}
            required
          />
        </label>
        <label>
          Mật khẩu mới
          <input
            name="matKhauMoi"
            aria-describedby="new-password-hint"
            type="password"
            autoComplete="new-password"
            minLength={10}
            maxLength={128}
            required
          />
        </label>
        <p id="new-password-hint" className={styles.muted}>
          Dùng ít nhất 10 ký tự, kết hợp chữ và số.
        </p>
        <label>
          Xác nhận mật khẩu mới
          <input
            name="xacNhan"
            type="password"
            autoComplete="new-password"
            maxLength={128}
            required
          />
        </label>
        <button className={styles.primary} disabled={busy}>
          {busy ? 'Đang xử lý…' : 'Đổi mật khẩu'}
        </button>
      </form>
    </section>
  );
}
