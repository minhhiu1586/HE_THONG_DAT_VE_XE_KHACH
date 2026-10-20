'use client';
import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { loginAccount } from '../lib/auth-api';
import { errorMessage } from '../lib/account-api';
import { loginDestination } from '../lib/role-home';
import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import { useAuth } from './AuthProvider';
import AuthFormLayout from './AuthFormLayout';
import styles from './Account.module.css';
export default function LoginForm() {
  const { accept } = useAuth();
  const router = useRouter();
  const sending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '').trim();
    const matKhau = String(form.get('matKhau') ?? '');
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      const session = await loginAccount({ email, matKhau });
      accept(session);
      const next = new URLSearchParams(window.location.search).get('next');
      router.replace(loginDestination(next, session.nguoiDung.loaiTaiKhoan));
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <AuthFormLayout error={error}>
      <form
        className={styles.form}
        onSubmit={submit}
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
      >
        <label>
          Email
          <input name="email" type="email" autoComplete="username" maxLength={254} required />
        </label>
        <label>
          Mật khẩu
          <input
            name="matKhau"
            type="password"
            autoComplete="current-password"
            maxLength={128}
            required
          />
        </label>
        <button className={styles.primary} disabled={busy}>
          {busy ? 'Đang xử lý…' : 'Đăng nhập'}
        </button>
      </form>
    </AuthFormLayout>
  );
}
