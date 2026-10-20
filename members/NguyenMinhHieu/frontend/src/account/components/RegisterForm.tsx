'use client';
import { useRef, useState } from 'react';
import { registerAccount } from '../lib/auth-api';
import { errorMessage } from '../lib/account-api';
import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import AuthFormLayout from './AuthFormLayout';
import styles from './Account.module.css';
export default function RegisterForm() {
  const sending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const element = event.currentTarget;
    const form = new FormData(element);
    const matKhau = String(form.get('matKhau') ?? '');
    if (matKhau !== form.get('xacNhan')) {
      setError('Hai mật khẩu chưa trùng nhau.');
      return;
    }
    sending.current = true;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await registerAccount({
        hoTen: String(form.get('hoTen') ?? ''),
        soDienThoai: String(form.get('soDienThoai') ?? ''),
        email: String(form.get('email') ?? '').trim(),
        matKhau,
      });
      setSuccess('Đăng ký thành công. Bạn có thể đăng nhập ngay.');
      element.reset();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <AuthFormLayout register error={error} success={success}>
      <form
        className={styles.form}
        onSubmit={submit}
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
      >
        <label>
          Họ tên
          <input name="hoTen" autoComplete="name" maxLength={160} required />
        </label>
        <label>
          Số điện thoại
          <input
            name="soDienThoai"
            type="tel"
            autoComplete="tel"
            pattern="(0[35789][0-9]{8}|\+84[35789][0-9]{8})"
            title="Số Việt Nam 10 chữ số hoặc dạng +84"
            required
          />
        </label>
        <label>
          Email
          <input name="email" type="email" autoComplete="username" maxLength={254} required />
        </label>
        <label>
          Mật khẩu
          <input
            name="matKhau"
            type="password"
            autoComplete="new-password"
            minLength={10}
            maxLength={128}
            required
          />
        </label>
        <p className={styles.muted}>Dùng ít nhất 10 ký tự, kết hợp chữ và số.</p>
        <label>
          Xác nhận mật khẩu
          <input
            name="xacNhan"
            type="password"
            autoComplete="new-password"
            maxLength={128}
            required
          />
        </label>
        <button className={styles.primary} disabled={busy}>
          {busy ? 'Đang xử lý…' : 'Đăng ký'}
        </button>
      </form>
    </AuthFormLayout>
  );
}
