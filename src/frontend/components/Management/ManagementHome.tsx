'use client';

import Link from 'next/link';
import { useAuth } from '@/components/Account/AuthProvider';
import { navigationFor } from '@/lib/management-navigation';
import styles from './Management.module.css';

export default function ManagementHome() {
  const { user } = useAuth();
  if (!user) return null;
  return <section className={styles.home}><h1>{user.loaiTaiKhoan === 'ADMIN' ? 'Quản trị hệ thống' : 'Quản lý nhà xe'}</h1>
    <p>Chọn chức năng trong menu hoặc các mục bên dưới để bắt đầu.</p>
    <div className={styles.cards}>{navigationFor(user.loaiTaiKhoan).map(item => <Link key={item.href} href={item.href} className={styles.card}>
      <span className={styles.icon} aria-hidden="true">{item.icon}</span><h2>{item.label}</h2><p>{item.description}</p><span className={styles.open} aria-hidden="true">Mở quản lý →</span>
    </Link>)}</div>
  </section>;
}
