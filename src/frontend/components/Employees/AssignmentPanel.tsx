'use client';

import { FormEvent, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/components/Account/AuthProvider';
import { errorMessage } from '@/lib/account-api';
import { clearValidation, vietnameseValidation } from '@/lib/form-validation';
import { Assignment, canAssign, Employee, noteText, Route, Schedule, scheduleTime, Trip, tripRoles, tripStates } from './employee-types';
import styles from './EmployeeManagement.module.css';

const empty = { maPhanCong: '', maChuyen: '', vaiTro: 'TAI_XE_CHINH', ghiChu: '', xacNhanKeHoachLaiNghi: false };
export default function AssignmentPanel({ employee, employees }: { employee: Employee; employees: Employee[] }) {
  const { request } = useAuth();
  const [schedule, setSchedule] = useState<Schedule[]>([]), [crew, setCrew] = useState<Assignment[]>([]);
  const [historyPage, setHistoryPage] = useState(1);
  const fresh = { ...empty, vaiTro: employee.loaiNhanVien === 'PHU_XE' ? 'PHU_XE' : 'TAI_XE_CHINH' };
  const [form, setForm] = useState(fresh), [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false), [loading, setLoading] = useState(true);
  const [error, setError] = useState(''), [success, setSuccess] = useState(''), [preview, setPreview] = useState('');
  const sending = useRef(false);
  const [selectedTrip, setSelectedTrip] = useState<Trip | null>(null), [routeName, setRouteName] = useState('');
  useEffect(() => {
    let active = true;
    request<Schedule[]>(`/nhan-vien/${encodeURIComponent(employee.maNhanVien)}/phan-cong`)
      .then(data => { if (active) setSchedule(data); })
      .catch(failure => { if (active) setError(errorMessage(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [employee.maNhanVien, request]);
  useEffect(() => {
    if (!selectedTrip) return;
    let active = true;
    request<Assignment[]>('/phan-cong')
      .then(data => { if (active) setCrew(data.filter(p => p.maChuyen === selectedTrip.maChuyen)); })
      .catch(failure => { if (active) setError(errorMessage(failure)); });
    return () => { active = false; };
  }, [selectedTrip, request]);
  async function loadTrip(id: string) {
    const trip = await request<Trip>(`/chuyen-xe/${encodeURIComponent(id.trim())}`);
    const route = await request<Route>(`/tuyen-xe/${encodeURIComponent(trip.maTuyen)}`);
    if (route.maNhaXe !== employee.maNhaXe) throw new Error('Chuyến không thuộc nhà xe của nhân viên.');
    setSelectedTrip(trip); setRouteName(route.tenTuyen); setCrew([]);
  }
  async function action(work: () => Promise<void>, message: string) {
    if (sending.current) return;
    sending.current = true; setBusy(true); setError(''); setSuccess(''); setPreview('');
    try { await work(); setSuccess(message); }
    catch (failure) { setError(errorMessage(failure)); }
    finally { sending.current = false; setBusy(false); }
  }
  async function refresh() {
    setSchedule(await request<Schedule[]>(`/nhan-vien/${encodeURIComponent(employee.maNhanVien)}/phan-cong`));
    if (form.maChuyen) setCrew((await request<Assignment[]>('/phan-cong')).filter(p => p.maChuyen === form.maChuyen));
  }
  function edit(item: Schedule) {
    setForm({ maPhanCong: item.maPhanCong, maChuyen: item.maChuyen, vaiTro: item.vaiTro, ghiChu: noteText(item.ghiChu), xacNhanKeHoachLaiNghi: false });
    setEditing(true); setError(''); setSuccess(''); setPreview(''); setCrew([]);
    setSelectedTrip(null); void action(() => loadTrip(item.maChuyen), '');
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void action(async () => {
      await request(`/phan-cong${editing ? `/${encodeURIComponent(form.maPhanCong)}` : ''}`, editing ? 'PUT' : 'POST', { ...form, maNhanVien: employee.maNhanVien });
      await refresh(); setForm({ ...fresh, maChuyen: form.maChuyen }); setEditing(false);
    }, 'Đã lưu phân công.');
  }
  const inactive = employee.trangThai !== 'DANG_LAM_VIEC' || employee.loaiNhanVien === 'DIEU_HANH';
  const historyPages = Math.max(1, Math.ceil(schedule.length / 5));
  const currentHistory = Math.min(historyPage, historyPages);
  return <section className={styles.panel} aria-busy={busy || loading}>
    <h2>Lịch phân công — {employee.hoTen}</h2>
    {error && <p className={styles.error} role="alert">{error}</p>}{success && <p className={styles.success} role="status">{success}</p>}
    {loading ? <p role="status">Đang tải lịch…</p> : schedule.length === 0 ? <p>Chưa có chuyến được phân công.</p> : schedule.slice((currentHistory - 1) * 5, currentHistory * 5).map(item => <article className={styles.schedule} key={item.maPhanCong}>
      <strong>{item.tenTuyen}</strong><p>{scheduleTime(item.ngayKhoiHanh, item.gioKhoiHanh)} → {scheduleTime(...item.gioDenDuKien.split('T') as [string, string])}<br />{tripRoles[item.vaiTro]} · {tripStates[item.trangThai] ?? item.trangThai}</p>
      {noteText(item.ghiChu) && <p>{noteText(item.ghiChu)}</p>}
      <div className={styles.actions}><button disabled={busy || !canAssign(item.trangThai)} onClick={() => edit(item)}>Sửa phân công {item.maPhanCong}</button>
        <button className={styles.danger} disabled={busy || !canAssign(item.trangThai)} onClick={() => {
          if (window.confirm(`Thu hồi phân công ${item.maPhanCong} của ${employee.hoTen}?`)) void action(async () => { await request(`/phan-cong/${encodeURIComponent(item.maPhanCong)}`, 'DELETE'); await refresh(); if (form.maPhanCong === item.maPhanCong) { setForm(fresh); setEditing(false); } }, 'Đã thu hồi phân công.');
        }}>Thu hồi {item.maPhanCong}</button></div>
    </article>)}
    {historyPages > 1 && <div className={styles.actions}><button disabled={currentHistory === 1 || busy} onClick={() => setHistoryPage(currentHistory - 1)}>Lịch trước</button><span>{currentHistory}/{historyPages} · {schedule.length} chuyến</span><button disabled={currentHistory === historyPages || busy} onClick={() => setHistoryPage(currentHistory + 1)}>Lịch tiếp</button></div>}
    <h2>{editing ? 'Cập nhật phân công' : 'Phân công chuyến'}</h2>
    {inactive && <p className={styles.warning}>Nhân viên không đủ vai trò/trạng thái để nhận phân công mới.</p>}
    <form onSubmit={submit} onInvalid={vietnameseValidation} onInput={clearValidation}>
      <fieldset disabled={busy} className={styles.form} style={{ border: 0, padding: 0 }}>
        <label>Mã phân công<input required maxLength={40} value={form.maPhanCong} disabled={editing} onChange={e => setForm({ ...form, maPhanCong: e.target.value })} /></label>
        <label>Mã chuyến xe<input required maxLength={40} value={form.maChuyen} onChange={e => { setForm({ ...form, maChuyen: e.target.value, xacNhanKeHoachLaiNghi: false }); setSelectedTrip(null); setCrew([]); setPreview(''); }} /></label>
        <div className={styles.wide}><button type="button" disabled={busy || !form.maChuyen.trim()} onClick={() => void action(() => loadTrip(form.maChuyen), '')}>Tải thông tin chuyến</button>
          {selectedTrip && <p>{routeName} — {selectedTrip.ngayKhoiHanh} {selectedTrip.gioKhoiHanh} → {selectedTrip.gioDenDuKien.replace('T', ' ')} · {tripStates[selectedTrip.trangThai] ?? selectedTrip.trangThai}</p>}</div>
        <label>Vai trò trên chuyến<select value={form.vaiTro} onChange={e => { setForm({ ...form, vaiTro: e.target.value }); setPreview(''); }}>
          {Object.entries(tripRoles).map(([id, label]) => <option value={id} key={id} disabled={employee.loaiNhanVien === 'PHU_XE' ? id !== 'PHU_XE' : id === 'PHU_XE'}>{label}</option>)}</select></label>
        <label>Ghi chú<textarea maxLength={800} value={form.ghiChu} onChange={e => setForm({ ...form, ghiChu: e.target.value })} /></label>
        <p className={`${styles.warning} ${styles.wide}`}>Chuyến trên 4 giờ cần kiểm tra kế hoạch nghỉ/đổi tài xế. Hệ thống không tự chứng nhận đủ giờ nghỉ.</p>
        <label className={`${styles.check} ${styles.wide}`}><input type="checkbox" checked={form.xacNhanKeHoachLaiNghi} onChange={e => setForm({ ...form, xacNhanKeHoachLaiNghi: e.target.checked })} />Tôi đã kiểm tra kế hoạch nghỉ/đổi tài xế cho chuyến này.</label>
      </fieldset>
      {preview && <p className={styles.warning} role="status">{preview}</p>}
      <div className={styles.actions}><button type="button" disabled={busy || inactive || !selectedTrip || editing} onClick={() => void action(async () => {
        const result = await request<{ canhBao: string }>(`/nhan-vien/${encodeURIComponent(employee.maNhanVien)}/dieu-kien?${new URLSearchParams({ maChuyen: form.maChuyen, vaiTro: form.vaiTro })}`);
        setPreview(`Đủ điều kiện cơ bản. ${result.canhBao}`);
      }, '')}>Kiểm tra điều kiện</button><button className={styles.primary} disabled={busy || inactive || !selectedTrip || !canAssign(selectedTrip.trangThai)}>{busy ? 'Đang xử lý…' : 'Lưu phân công'}</button>
        <button type="button" disabled={busy} onClick={() => { setForm(fresh); setEditing(false); setSelectedTrip(null); setCrew([]); setPreview(''); setError(''); setSuccess(''); }}>Phân công mới</button></div>
    </form>
    {selectedTrip && <><h2>Nhân sự của chuyến {selectedTrip.maChuyen}</h2>{crew.length === 0 ? <p>Chưa có nhân sự hoặc đang tải.</p> : crew.map(p => <p key={p.maPhanCong}>{employees.find(n => n.maNhanVien === p.maNhanVien)?.hoTen ?? p.maNhanVien} — {tripRoles[p.vaiTro]}</p>)}</>}
  </section>;
}
