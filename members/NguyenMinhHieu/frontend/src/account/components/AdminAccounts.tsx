'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage, Profile } from '@members/NguyenMinhHieu/account/lib/account-api';
import styles from '@members/NguyenMinhHieu/account/components/Account.module.css';

const roleLabels = { ADMIN: 'Quản trị viên', NHA_XE: 'Quản lý nhà xe', NHAN_VIEN: 'Nhân viên', KHACH_HANG: 'Khách hàng' };
const stateLabels = { HOAT_DONG: 'Hoạt động', KHOA: 'Đã khóa', CHO_XAC_THUC: 'Chờ xác thực' };

type Page = { items: Profile[]; totalPages: number };
export default function AdminAccounts() {
  const { user, loading, request } = useAuth(); const router = useRouter();
  const [page, setPage] = useState(1), [data, setData] = useState<Page>({ items: [], totalPages: 0 });
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [success, setSuccess] = useState('');
  const sending = useRef(false);
  const load = useCallback(async () => {
    setBusy(true);
    try { setData(await request<Page>(`/quan-tri/tai-khoan?page=${page}&size=20`)); setError(''); }
    catch (error) { setError(errorMessage(error)); }
    finally { setBusy(false); }
  }, [page, request]);
  useEffect(() => {
    if (!loading && !user) router.replace('/dang-nhap?next=/quan-tri/tai-khoan');
    if (user?.loaiTaiKhoan !== 'ADMIN') return;
    let active = true;
    request<Page>(`/quan-tri/tai-khoan?page=${page}&size=20`).then(result => {
      if (active) { setData(result); setError(''); }
    }).catch(error => { if (active) setError(errorMessage(error)); });
    return () => { active = false; };
  }, [loading, user, router, page, request]);
  async function toggle(account: Profile) {
    if (sending.current) return; sending.current = true; setBusy(true); setError(''); setSuccess('');
    try {
      const trangThai = account.trangThai === 'KHOA' ? 'HOAT_DONG' : 'KHOA';
      await request(`/quan-tri/tai-khoan/${account.maNguoiDung}/trang-thai`, 'PUT', { trangThai });
      setSuccess(trangThai === 'KHOA' ? 'Đã khóa tài khoản và thu hồi các phiên đăng nhập.' : 'Đã mở khóa tài khoản.'); await load();
    } catch (error) { setError(errorMessage(error)); }
    finally { sending.current = false; setBusy(false); }
  }
  if (loading || !user) return <div className={styles.page}>Đang kiểm tra quyền…</div>;
  if (user.loaiTaiKhoan !== 'ADMIN') return <div className={styles.page}><p className={styles.error} role="alert">Bạn không có quyền quản lý tài khoản.</p></div>;
  return <div className={styles.admin}><header className={styles.adminHeading}><p className={styles.eyebrow}>QUẢN TRỊ HỆ THỐNG</p><h1>Quản lý tài khoản</h1><p>Kiểm tra trạng thái và quyền truy cập của người dùng.</p></header><section className={styles.card}><div className={styles.sectionTitle}><h2>Danh sách tài khoản</h2><Link href="/quan-tri/nha-xe">Quản lý nhà xe →</Link></div>
    <p className={styles.muted}>Khóa tài khoản sẽ thu hồi tất cả phiên đăng nhập. Không thể tự khóa tài khoản quản trị hiện tại.</p>
    {error && <p className={styles.error} role="alert">{error}</p>}{success && <p className={styles.success} role="status">{success}</p>}
    <div className={styles.scroll}><table className={styles.table}><thead><tr><th>Họ tên</th><th>Email</th><th>Vai trò</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{data.items.map(account => <tr key={account.maNguoiDung}><td>{account.hoTen}</td><td>{account.email}</td><td>{roleLabels[account.loaiTaiKhoan]}</td><td>{stateLabels[account.trangThai]}</td>
        <td><button className={styles.secondary} disabled={busy || account.maNguoiDung === user.maNguoiDung} onClick={() => toggle(account)}>{account.trangThai === 'KHOA' ? 'Mở khóa' : 'Khóa'}</button></td></tr>)}</tbody></table></div>
    <div className={styles.links}><button disabled={busy || page === 1} onClick={() => setPage(page - 1)}>Trang trước</button><span>Trang {page}</span>
      <button disabled={busy || page >= data.totalPages} onClick={() => setPage(page + 1)}>Trang sau</button></div>
  </section></div>;
}
