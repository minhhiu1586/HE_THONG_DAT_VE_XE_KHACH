'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/components/Account/AuthProvider';
import { errorMessage } from '@/lib/account-api';
import { Employee, noteText, roles, Schedule, scheduleTime, tripRoles, tripStates } from './employee-types';
import styles from './EmployeeManagement.module.css';

export default function EmployeeSchedule() {
  const { user, loading, request } = useAuth(); const router = useRouter();
  const [employee, setEmployee] = useState<Employee | null>(null), [schedule, setSchedule] = useState<Schedule[]>([]);
  const [fetching, setFetching] = useState(true), [error, setError] = useState('');
  const [page, setPage] = useState(1);
  useEffect(() => {
    if (!loading && !user) router.replace('/dang-nhap?next=/nhan-vien');
    if (user?.loaiTaiKhoan !== 'NHAN_VIEN') return;
    let active = true;
    request<Employee[]>('/nhan-vien').then(async data => {
      const own = data[0];
      const assigned = own ? await request<Schedule[]>(`/nhan-vien/${encodeURIComponent(own.maNhanVien)}/phan-cong`) : [];
      if (active) { setEmployee(own ?? null); setSchedule(assigned); }
    }).catch(failure => { if (active) setError(errorMessage(failure)); }).finally(() => { if (active) setFetching(false); });
    return () => { active = false; };
  }, [loading, user, router, request]);
  if (loading || !user) return <div className={styles.page} role="status">Đang kiểm tra phiên đăng nhập…</div>;
  if (user.loaiTaiKhoan !== 'NHAN_VIEN') return <div className={styles.page} role="alert">Trang lịch riêng dành cho tài khoản nhân viên.</div>;
  const pages = Math.max(1, Math.ceil(schedule.length / 8));
  return <main className={styles.page}><header className={styles.heading}><div><span className={styles.eyebrow}>Lịch làm việc</span><h1>Lịch chuyến của tôi</h1><p>Theo dõi thời gian khởi hành và vai trò được phân công.</p></div><span className={styles.scope}>{schedule.length} chuyến</span></header>
    {error && <p className={styles.error} role="alert">{error}</p>}
    {fetching ? <p role="status">Đang tải lịch…</p> : !employee ? <p>Tài khoản chưa được liên kết hồ sơ nhân viên.</p> : <section className={styles.panel}>
      <h2>{employee.hoTen}</h2><p className={styles.hint}>{employee.maNhanVien} · {roles[employee.loaiNhanVien]}</p>{schedule.length === 0 ? <p>Chưa có chuyến được phân công.</p> : schedule.slice((page - 1) * 8, page * 8).map(p => <article className={styles.schedule} key={p.maPhanCong}>
        <strong>{p.tenTuyen}</strong><p>{scheduleTime(p.ngayKhoiHanh, p.gioKhoiHanh)} → {scheduleTime(...p.gioDenDuKien.split('T') as [string, string])}<br />{tripRoles[p.vaiTro]} · {tripStates[p.trangThai] ?? p.trangThai}</p>
        {noteText(p.ghiChu) && <p>{noteText(p.ghiChu)}</p>}
      </article>)}
      {pages > 1 && <div className={styles.actions}><button disabled={page === 1} onClick={() => setPage(page - 1)}>Trang trước</button><span>Trang {page}/{pages}</span><button disabled={page === pages} onClick={() => setPage(page + 1)}>Trang sau</button></div>}
    </section>}
  </main>;
}
