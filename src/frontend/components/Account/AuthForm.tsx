'use client';

import { useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { accountRequest, errorMessage, Profile, Session } from '@/lib/account-api';
import { useAuth } from './AuthProvider';
import styles from './Account.module.css';
import { vietnameseValidation, clearValidation } from '@/lib/form-validation';

export default function AuthForm({ register = false }: { register?: boolean }) {
  const { accept, notice } = useAuth(); const router = useRouter();
  const [busy, setBusy] = useState(false); const sending = useRef(false);
  const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (sending.current) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const email = String(form.get('email') ?? '').trim(), matKhau = String(form.get('matKhau') ?? '');
    if (register && matKhau !== form.get('xacNhan')) { setError('Hai mật khẩu chưa trùng nhau.'); return; }
    sending.current = true; setBusy(true); setError(''); setSuccess('');
    try {
      if (register) {
        await accountRequest<Profile>('/auth/dang-ky', 'POST', {
          hoTen: form.get('hoTen'), soDienThoai: form.get('soDienThoai'), email, matKhau,
        });
        setSuccess('Đăng ký thành công. Bạn có thể đăng nhập ngay.');
        formElement.reset();
      } else {
        accept(await accountRequest<Session>('/auth/dang-nhap', 'POST', { email, matKhau }));
        const next = new URLSearchParams(window.location.search).get('next');
        router.replace(next && next.startsWith('/') && !next.startsWith('//') && !next.includes('\\') ? next : '/tai-khoan');
      }
    } catch (error) { setError(errorMessage(error)); }
    finally { sending.current = false; setBusy(false); }
  }
  return <main className={styles.page}><section className={styles.card}>
    <p className={styles.eyebrow}>TÀI KHOẢN VEXERE</p>
    <h1>{register ? 'Tạo tài khoản' : 'Đăng nhập'}</h1>
    <p className={styles.muted}>{register ? 'Đăng ký tài khoản khách hàng để sử dụng VeXe.' : 'Đăng nhập để quản lý hồ sơ và sử dụng các chức năng của bạn.'}</p>
    {notice && <p className={styles.error} role="status">{notice}</p>}
    {error && <p className={styles.error} role="alert">{error}</p>}
    {success && <p className={styles.success} role="status">{success} <Link href="/dang-nhap">Đăng nhập</Link></p>}
    <form onInvalid={vietnameseValidation} onInput={clearValidation} className={styles.form} onSubmit={submit}>
      {register && <><label>Họ tên<input name="hoTen" autoComplete="name" maxLength={160} required /></label>
        <label>Số điện thoại<input name="soDienThoai" type="tel" autoComplete="tel" pattern="(0[35789][0-9]{8}|\+84[35789][0-9]{8})" title="Số Việt Nam 10 chữ số hoặc dạng +84" required /></label></>}
      <label>Email<input name="email" type="email" autoComplete="username" maxLength={254} required /></label>
      <label>Mật khẩu<input name="matKhau" type="password" autoComplete={register ? 'new-password' : 'current-password'}
        minLength={register ? 10 : undefined} maxLength={128} required /></label>
      {register && <><p className={styles.muted}>Mật khẩu cần 10–128 ký tự, có chữ và số.</p>
        <label>Xác nhận mật khẩu<input name="xacNhan" type="password" autoComplete="new-password" maxLength={128} required /></label></>}
      <button className={styles.primary} disabled={busy}>{busy ? 'Đang xử lý…' : register ? 'Đăng ký' : 'Đăng nhập'}</button>
    </form>
    <div className={styles.links}><Link href={register ? '/dang-nhap' : '/dang-ky'}>{register ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Đăng ký'}</Link><Link href="/">Về trang chủ</Link></div>
  </section></main>;
}
