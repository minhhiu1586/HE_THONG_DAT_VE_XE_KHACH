'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage } from '@members/NguyenMinhHieu/account/lib/account-api';
import { Employee, noteText, roles, Schedule, scheduleTime, states, tripRoles, tripStates } from '@members/TranDangThiKimCuong/employees/components/employee-types';
import styles from '@members/TranDangThiKimCuong/employees/components/EmployeeManagement.module.css';

const sections = { overview: 'Tổng quan', schedule: 'Lịch phân công', profile: 'Hồ sơ nhân viên' };
type Section = keyof typeof sections;

export default function EmployeeSchedule() {
  const { user, loading, request } = useAuth();
  const router = useRouter();
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [schedule, setSchedule] = useState<Schedule[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState('');
  const [section, setSection] = useState<Section>('overview');
  const [page, setPage] = useState(1);
  const [date, setDate] = useState('');
  const [transitioning,setTransitioning]=useState('');
  const [notice,setNotice]=useState('');
  useEffect(() => {
    if (!loading && !user) router.replace('/dang-nhap?next=/nhan-vien');
    if (user?.loaiTaiKhoan !== 'NHAN_VIEN') return;
    let active = true;
    // Backend xác định hồ sơ từ phiên đăng nhập, không nhận ID người khác từ URL.
    request<Employee[]>('/nhan-vien').then(async data => {
      const own = data[0];
      const assigned = own ? await request<Schedule[]>(`/nhan-vien/${encodeURIComponent(own.maNhanVien)}/phan-cong`) : [];
      if (active) { setEmployee(own ?? null); setSchedule(assigned); }
    }).catch(cause => { if (active) setError(errorMessage(cause)); })
      .finally(() => { if (active) setFetching(false); });
    return () => { active = false; };
  }, [loading, user, router, request]);

  if (loading || !user) return <div className={styles.page} role="status">Đang kiểm tra phiên đăng nhập…</div>;
  if (user.loaiTaiKhoan !== 'NHAN_VIEN') return <div className={styles.page} role="alert">Không gian này dành cho tài khoản nhân viên.</div>;
  const working = schedule.filter(item => !['DA_HUY', 'HOAN_TAT'].includes(item.trangThai));
  const filtered = schedule.filter(item => !date || item.ngayKhoiHanh === date);
  const pages = Math.max(1, Math.ceil(filtered.length / 8));
  const current = Math.min(page, pages);
  const nextTrip = working[0];
  const title = employee?.loaiNhanVien === 'TAI_XE' ? 'Không gian tài xế' : employee?.loaiNhanVien === 'PHU_XE' ? 'Không gian phụ xe' : 'Không gian nhân viên';

  async function changeTripState(item:Schedule,target:'bat-dau'|'hoan-thanh') {
    if(transitioning)return;setTransitioning(item.maChuyen);setNotice('');setError('');
    try {
      await request(`/chuyen-xe/${encodeURIComponent(item.maChuyen)}/${target}`,'POST',{});
      const updated=await request<Schedule[]>(`/nhan-vien/${encodeURIComponent(employee!.maNhanVien)}/phan-cong`);
      setSchedule(updated);setNotice(target==='bat-dau'?'Đã bắt đầu chuyến.':'Đã hoàn tất chuyến.');
    } catch(cause) {setError(errorMessage(cause));}
    finally {setTransitioning('');}
  }

  function journey(item: Schedule) {
    return <article className={styles.schedule} key={item.maPhanCong}>
      <strong>{item.tenTuyen}</strong>
      <p>{scheduleTime(item.ngayKhoiHanh, item.gioKhoiHanh)} → {scheduleTime(...item.gioDenDuKien.split('T') as [string, string])}</p>
      <span className={styles.badge}>{tripRoles[item.vaiTro]}</span>
      <p>{tripStates[item.trangThai] ?? item.trangThai} · {item.maChuyen}</p>
      {noteText(item.ghiChu) && <p>{noteText(item.ghiChu)}</p>}
      <div className={styles.actions}>
        {['TAI_XE_CHINH','TAI_XE_PHU','TAI_XE'].includes(item.vaiTro)&&item.trangThai==='DANG_MO_BAN'&&<button className={styles.primary} disabled={!!transitioning} onClick={()=>void changeTripState(item,'bat-dau')}>{transitioning===item.maChuyen?'Đang cập nhật…':'Bắt đầu chuyến'}</button>}
        {['TAI_XE_CHINH','TAI_XE_PHU','TAI_XE'].includes(item.vaiTro)&&item.trangThai==='DANG_CHAY'&&<button className={styles.primary} disabled={!!transitioning} onClick={()=>void changeTripState(item,'hoan-thanh')}>{transitioning===item.maChuyen?'Đang cập nhật…':'Hoàn tất chuyến'}</button>}
      </div>
    </article>;
  }

  return <main className={styles.page}>
    <header className={styles.heading}>
      <div><span className={styles.eyebrow}>{title}</span><h1>Xin chào, {employee?.hoTen ?? user.hoTen}</h1><p>Hồ sơ công việc và lịch chuyến dành riêng cho bạn.</p></div>
      <Link className={styles.scope} href="/tai-khoan">Tài khoản và bảo mật →</Link>
    </header>
    {error && <p className={styles.error} role="alert">{error}</p>}
    {notice && <p className={styles.success} role="status">{notice}</p>}
    {fetching ? <p role="status">Đang tải hồ sơ và lịch…</p> : !employee ? <section className={styles.panel}><h2>Hồ sơ nhân viên</h2><p>Tài khoản chưa được liên kết hồ sơ. Liên hệ quản lý nhà xe để cập nhật.</p></section> : <>
      <nav className={styles.workspaceNav} aria-label="Không gian nhân viên">
        {Object.entries(sections).map(([id, label]) => <button key={id} aria-current={section === id ? 'page' : undefined} onClick={() => setSection(id as Section)}>{label}</button>)}
      </nav>
      {section === 'overview' && <>
        <div className={styles.summary}>
          <div><strong>{roles[employee.loaiNhanVien]}</strong><span>Vai trò nghiệp vụ</span></div>
          <div><strong>{working.length}</strong><span>Chuyến đang được giao</span></div>
          <div><strong>{states[employee.trangThai]}</strong><span>Trạng thái làm việc</span></div>
        </div>
        <section className={styles.panel}><h2>Chuyến tiếp theo trong lịch</h2>{nextTrip ? journey(nextTrip) : <p>Chưa có chuyến đang được giao.</p>}<button className={styles.primary} onClick={() => setSection('schedule')}>Xem toàn bộ lịch</button></section>
        <section className={styles.panel}><h2>Thông tin công việc</h2><p>{employee.loaiNhanVien === 'TAI_XE' ? 'Kiểm tra giờ khởi hành, vai trò tài xế chính/phụ và ghi chú điều phối trước mỗi chuyến.' : 'Kiểm tra giờ khởi hành, tuyến phục vụ và ghi chú điều phối trước mỗi chuyến.'}</p><p>Liên hệ người điều phối khi hồ sơ hoặc lịch cần điều chỉnh.</p></section>
      </>}
      {section === 'profile' && <section className={styles.panel}>
        <h2>Hồ sơ nhân viên của tôi</h2>
        <dl className={styles.details}>
          <div><dt>Mã nhân viên</dt><dd>{employee.maNhanVien}</dd></div>
          <div><dt>Họ tên</dt><dd>{employee.hoTen}</dd></div>
          <div><dt>Phạm vi nhân viên</dt><dd>{employee.maNhaXe ?? 'Nhân viên hệ thống'}</dd></div>
          <div><dt>Vai trò</dt><dd>{roles[employee.loaiNhanVien]}</dd></div>
          <div><dt>Trạng thái</dt><dd>{states[employee.trangThai]}</dd></div>
          <div><dt>Ngày sinh</dt><dd>{employee.ngaySinh ? employee.ngaySinh.split('-').reverse().join('/') : 'Chưa cung cấp'}</dd></div>
          <div><dt>Email công việc</dt><dd>{employee.email}</dd></div>
          <div><dt>Điện thoại công việc</dt><dd>{employee.soDienThoai}</dd></div>
          {employee.loaiNhanVien === 'TAI_XE' && <><div><dt>Số bằng lái</dt><dd>{employee.soBangLai ?? 'Chưa cung cấp'}</dd></div><div><dt>Hạn bằng lái</dt><dd>{employee.ngayHetHanBangLai?.split('-').reverse().join('/') ?? 'Chưa cung cấp'}</dd></div></>}
        </dl>
        <p className={styles.hint}>Hồ sơ công việc do quản lý nhà xe cập nhật. Bạn có thể chỉnh thông tin tài khoản tại trang tài khoản và bảo mật.</p>
      </section>}
      {section === 'schedule' && <section className={styles.panel}>
        <h2>Lịch phân công của tôi</h2>
        <div className={styles.filters}><label>Ngày khởi hành<input type="date" value={date} onInput={event => { setDate(event.currentTarget.value); setPage(1); }} /></label><button onClick={() => { setDate(''); setPage(1); }}>Tất cả ngày</button><span>{filtered.length} chuyến</span></div>
        {filtered.length ? filtered.slice((current - 1) * 8, current * 8).map(journey) : <p>Không có chuyến phù hợp với ngày đã chọn.</p>}
        {pages > 1 && <div className={styles.actions}><button disabled={current === 1} onClick={() => setPage(current - 1)}>Trang trước</button><span>Trang {current}/{pages}</span><button disabled={current === pages} onClick={() => setPage(current + 1)}>Trang sau</button></div>}
      </section>}
    </>}
  </main>;
}
