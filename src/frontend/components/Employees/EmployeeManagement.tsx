'use client';

import { FormEvent, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/components/Account/AuthProvider';
import { errorMessage } from '@/lib/account-api';
import { clearValidation, vietnameseValidation } from '@/lib/form-validation';
import AssignmentPanel from './AssignmentPanel';
import { Employee, Operator, roles, states } from './employee-types';
import styles from './EmployeeManagement.module.css';

const empty = { maNhanVien: '', hoTen: '', soDienThoai: '', email: '', ngaySinh: '', maNhaXe: '', loaiNhanVien: 'TAI_XE', trangThai: 'DANG_LAM_VIEC', soBangLai: '', ngayHetHanBangLai: '' };
const pageSize = 6;
export default function EmployeeManagement() {
  const { user, loading, request } = useAuth();
  const allowed = user?.loaiTaiKhoan === 'ADMIN' || user?.loaiTaiKhoan === 'NHA_XE';
  const [employees, setEmployees] = useState<Employee[]>([]), [operators, setOperators] = useState<Operator[]>([]);
  const [selected, setSelected] = useState<Employee | null>(null), [form, setForm] = useState(empty);
  const [query, setQuery] = useState(''), [role, setRole] = useState(''), [state, setState] = useState(''), [owner, setOwner] = useState('');
  const [page, setPage] = useState(1), [busy, setBusy] = useState(false), [fetching, setFetching] = useState(true);
  const [error, setError] = useState(''), [success, setSuccess] = useState('');
  const sending = useRef(false);
  useEffect(() => {
    if (!allowed) return;
    let active = true;
    Promise.all([request<Employee[]>('/nhan-vien'), request<Operator[]>('/xe/pham-vi')])
      .then(([staff, owners]) => { if (active) { setEmployees(staff); setOperators(owners); } })
      .catch(failure => { if (active) setError(errorMessage(failure)); })
      .finally(() => { if (active) setFetching(false); });
    return () => { active = false; };
  }, [allowed, request]);
  async function action(work: () => Promise<void>, message: string) {
    if (sending.current) return;
    sending.current = true; setBusy(true); setError(''); setSuccess('');
    try { await work(); setSuccess(message); } catch (failure) { setError(errorMessage(failure)); }
    finally { sending.current = false; setBusy(false); }
  }
  function select(employee: Employee) {
    setSelected(employee); setForm({ ...employee, maNhaXe: employee.maNhaXe ?? '', ngaySinh: employee.ngaySinh ?? '', soBangLai: employee.soBangLai ?? '', ngayHetHanBangLai: employee.ngayHetHanBangLai ?? '' });
    setError(''); setSuccess('');
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void action(async () => {
      const employee = await request<Employee>(`/nhan-vien${selected ? `/${encodeURIComponent(selected.maNhanVien)}` : ''}`, selected ? 'PUT' : 'POST', {
        ...form, ngaySinh: form.ngaySinh || null, soBangLai: form.soBangLai || null, ngayHetHanBangLai: form.ngayHetHanBangLai || null,
      });
      select(employee);
      if (!selected) { setQuery(employee.maNhanVien); setRole(''); setState(''); setOwner(''); setPage(1); }
      setEmployees(await request<Employee[]>('/nhan-vien'));
    }, selected ? 'Đã cập nhật hồ sơ.' : 'Đã thêm nhân viên.');
  }
  const visible = employees.filter(n => (!role || n.loaiNhanVien === role) && (!state || n.trangThai === state) && (!owner || n.maNhaXe === owner)
    && `${n.hoTen} ${n.maNhanVien} ${n.email} ${n.soDienThoai}`.toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi')));
  const pages = Math.max(1, Math.ceil(visible.length / pageSize)), current = Math.min(page, pages);
  if (loading) return <div className={styles.page} role="status">Đang kiểm tra phiên đăng nhập…</div>;
  if (!user) return <div className={styles.page}><h1>Quản lý nhân viên</h1><p>Đăng nhập tài khoản quản trị hoặc quản lý nhà xe để tiếp tục.</p><Link href="/dang-nhap?next=/quan-tri/nhan-vien">Đăng nhập →</Link></div>;
  if (!allowed) return <div className={styles.page} role="alert">Bạn không có quyền quản lý nhân viên.</div>;
  return <div className={styles.page}>
    <header className={styles.heading}><div><span className={styles.eyebrow}>Điều phối nhân sự</span><h1>Nhân viên và phân công chuyến</h1><p>Hồ sơ, lịch làm việc và nhân sự trên từng chuyến.</p></div><span className={styles.scope}>{user.loaiTaiKhoan === 'ADMIN' ? 'Quản trị hệ thống' : 'Quản lý nhà xe'}</span></header>
    <div className={styles.summary} aria-label="Tổng quan nhân viên trong phạm vi">
      <div><strong>{fetching ? '—' : employees.length}</strong><span>Nhân viên</span></div>
      <div><strong>{fetching ? '—' : employees.filter(n => n.loaiNhanVien === 'TAI_XE').length}</strong><span>Tài xế</span></div>
      <div><strong>{fetching ? '—' : employees.filter(n => n.trangThai === 'DANG_LAM_VIEC').length}</strong><span>Đang làm việc</span></div>
    </div>
    {error && <p className={styles.error} role="alert">{error}</p>}{success && <p className={styles.success} role="status">{success}</p>}
    <div className={styles.filters}>
      <label>Tìm nhân viên<input placeholder="Tên, mã, email, điện thoại" value={query} onChange={e => { setQuery(e.target.value); setPage(1); }} /></label>
      <label>Lọc nhà xe<select value={owner} onChange={e => { setOwner(e.target.value); setPage(1); }}><option value="">Tất cả trong phạm vi</option>{operators.map(o => <option key={o.maNhaXe} value={o.maNhaXe}>{o.tenNhaXe}</option>)}</select></label>
      <label>Lọc vai trò<select value={role} onChange={e => { setRole(e.target.value); setPage(1); }}><option value="">Tất cả</option>{Object.entries(roles).map(([id, label]) => <option key={id} value={id}>{label}</option>)}</select></label>
      <label>Lọc trạng thái<select value={state} onChange={e => { setState(e.target.value); setPage(1); }}><option value="">Tất cả</option>{Object.entries(states).map(([id, label]) => <option key={id} value={id}>{label}</option>)}</select></label>
      <button className={styles.primary} disabled={busy || fetching} onClick={() => { setSelected(null); setForm({ ...empty, maNhaXe: operators.length === 1 ? operators[0].maNhaXe : '' }); setError(''); setSuccess(''); }}>+ Thêm nhân viên</button>
    </div>
    <div className={styles.grid}><section className={styles.panel} aria-busy={fetching}>
      <div className={styles.listHeading}><h2>Danh sách nhân viên</h2><span className={styles.listCount}>{visible.length}</span></div>
      <p className={styles.listHint}>Chọn nhân viên để xem hồ sơ và phân công chuyến.</p>
      {fetching ? <p role="status">Đang tải nhân viên…</p> : visible.length === 0 ? <p>Không có nhân viên phù hợp.</p> : <div className={styles.employeeList}>
        {visible.slice((current - 1) * pageSize, current * pageSize).map(n => <button type="button" key={n.maNhanVien} className={styles.employeeRow} aria-pressed={selected?.maNhanVien === n.maNhanVien} disabled={busy} onClick={() => select(n)}>
          <span className={styles.rowAvatar} aria-hidden="true">{n.hoTen.split(' ').filter(Boolean).slice(-2).map(part => part[0]).join('')}</span>
          <span className={styles.rowContent}><strong>{n.hoTen}</strong><small>{roles[n.loaiNhanVien]} · {n.maNhanVien}</small><span className={styles.rowMeta}><span>{n.maNhaXe ? operators.find(o => o.maNhaXe === n.maNhaXe)?.tenNhaXe ?? n.maNhaXe : 'Nhân viên hệ thống'}</span><span className={n.trangThai === 'DANG_LAM_VIEC' ? styles.rowActive : styles.rowInactive}>{states[n.trangThai]}</span></span></span>
        </button>)}
      </div>}
      {!fetching && visible.length > 0 && <nav className={styles.pagination} aria-label="Phân trang nhân viên">
        <span className={styles.pageRange}>{(current - 1) * pageSize + 1}–{Math.min(current * pageSize, visible.length)} / {visible.length} nhân viên</span>
        <div className={styles.pageControls}><button type="button" aria-label="Trang nhân viên trước" disabled={current === 1 || busy} onClick={() => setPage(current - 1)}>Trước</button><span aria-live="polite">{current} / {pages}</span><button type="button" aria-label="Trang nhân viên sau" disabled={current === pages || busy} onClick={() => setPage(current + 1)}>Sau</button></div>
      </nav>}
    </section><div><section className={styles.panel} aria-busy={busy}>
      <h2>{selected ? 'Hồ sơ nhân viên' : 'Thêm hồ sơ nhân viên'}</h2>
      <p className={styles.hint}>{selected ? `${selected.maNhanVien} · ${roles[selected.loaiNhanVien]}` : 'Chọn nhà xe và nhập thông tin liên hệ để tạo hồ sơ.'}</p>
      <form onSubmit={submit} onInvalid={vietnameseValidation} onInput={clearValidation}>
        <fieldset disabled={busy || fetching} className={styles.form} style={{ border: 0, padding: 0 }}>
          <label>Phạm vi nhân viên<select required={form.loaiNhanVien !== 'KIEM_VE'} disabled={!!selected} value={form.maNhaXe} onChange={e => setForm({ ...form, maNhaXe: e.target.value })}><option value="">{form.loaiNhanVien === 'KIEM_VE' && user.loaiTaiKhoan === 'ADMIN' ? 'Nhân viên hệ thống' : 'Chọn nhà xe'}</option>{operators.map(o => <option key={o.maNhaXe} value={o.maNhaXe}>{o.tenNhaXe}</option>)}</select></label>
          <label>Mã nhân viên<input required disabled={!!selected} maxLength={40} value={form.maNhanVien} onChange={e => setForm({ ...form, maNhanVien: e.target.value })} /></label>
          <label>Họ tên<input required maxLength={160} value={form.hoTen} onChange={e => setForm({ ...form, hoTen: e.target.value })} /></label>
          <label>Điện thoại<input type="tel" required pattern="0[0-9]{9}" title="Điện thoại gồm 10 chữ số và bắt đầu bằng 0." maxLength={10} value={form.soDienThoai} onChange={e => setForm({ ...form, soDienThoai: e.target.value })} /></label>
          <label>Email<input type="email" required maxLength={254} value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label>
          <label>Ngày sinh<input type="date" value={form.ngaySinh} onChange={e => setForm({ ...form, ngaySinh: e.target.value })} /></label>
          <label>Vai trò nghiệp vụ<select value={form.loaiNhanVien} onChange={e => setForm({ ...form, loaiNhanVien: e.target.value })}>{Object.entries(roles).map(([id, label]) => <option key={id} value={id}>{label}</option>)}</select></label>
          <label>Trạng thái làm việc<select value={form.trangThai} onChange={e => setForm({ ...form, trangThai: e.target.value })}>{Object.entries(states).map(([id, label]) => <option key={id} value={id}>{label}</option>)}</select></label>
          <label>Số bằng lái<input required={form.loaiNhanVien === 'TAI_XE'} maxLength={80} value={form.soBangLai} onChange={e => setForm({ ...form, soBangLai: e.target.value })} /></label>
          <label>Hạn bằng lái<input type="date" required={form.loaiNhanVien === 'TAI_XE'} value={form.ngayHetHanBangLai} onChange={e => setForm({ ...form, ngayHetHanBangLai: e.target.value })} /></label>
        </fieldset>
        <div className={styles.actions}><button className={styles.primary} disabled={busy || fetching}>{busy ? 'Đang lưu…' : 'Lưu hồ sơ'}</button>{selected && <button type="button" className={styles.danger} disabled={busy} onClick={() => {
          if (window.confirm(`Xóa hồ sơ ${selected.hoTen}? Hồ sơ có lịch sử hoặc tài khoản liên kết sẽ bị từ chối.`)) void action(async () => {
            await request(`/nhan-vien/${encodeURIComponent(selected.maNhanVien)}`, 'DELETE'); setSelected(null); setForm(empty); setEmployees(await request<Employee[]>('/nhan-vien'));
          }, 'Đã xóa hồ sơ.');
        }}>Xóa hồ sơ</button>}</div>
      </form>
    </section>{selected && (selected.loaiNhanVien === 'KIEM_VE' && !selected.maNhaXe ? <section className={styles.panel}><h2>Kiểm vé chung của hệ thống</h2><p>Nhân viên kiểm vé chung không thuộc nhà xe và không cần lịch phân công theo hãng. Khi đăng nhập, nhân viên chọn chuyến cần kiểm và nhập mã vé hoặc chuỗi QR.</p></section> : <AssignmentPanel key={`${selected.maNhanVien}:${selected.loaiNhanVien}:${selected.trangThai}`} employee={selected} employees={employees} />)}</div></div>
  </div>;
}
