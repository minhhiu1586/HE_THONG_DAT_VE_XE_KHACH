'use client';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { useAuth } from './AuthProvider';
import styles from './Account.module.css';
type Props = {
  register?: boolean;
  error: string;
  success?: string;
  children: ReactNode;
};
export default function AuthFormLayout({ register = false, error, success, children }: Props) {
  const { notice } = useAuth();
  return (
    <main className={styles.page}>
      <section className={styles.card}>
        <p className={styles.eyebrow}>TÀI KHOẢN VEXEXIN</p>
        <h1>{register ? 'Tạo tài khoản' : 'Đăng nhập'}</h1>
        <p className={styles.muted}>
          {register
            ? 'Đăng ký tài khoản khách hàng để sử dụng VeXe.'
            : 'Đăng nhập để quản lý hồ sơ và sử dụng các chức năng của bạn.'}
        </p>
        {notice && (
          <p className={styles.error} role="status">
            {notice}
          </p>
        )}
        {error && (
          <p className={styles.error} role="alert">
            {error}
          </p>
        )}
        {success && (
          <p className={styles.success} role="status">
            {success} <Link href="/dang-nhap">Đăng nhập</Link>
          </p>
        )}
        {children}
        <div className={styles.links}>
          <Link href={register ? '/dang-nhap' : '/dang-ky'}>
            {register ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Đăng ký'}
          </Link>
          <Link href="/">Về trang chủ</Link>
        </div>
      </section>
    </main>
  );
}
