'use client';

import { FormEvent, InputHTMLAttributes, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/components/Account/AuthProvider';
import { errorMessage } from '@/lib/account-api';
import { vietnameseValidation, clearValidation } from '@/lib/form-validation';
import { useManagementFocus } from '@/lib/use-management-focus';
import styles from './VehicleManagement.module.css';

type Kind = { maLoaiXe: string; tenLoaiXe: string; soChoNgoi: number; soTang: number; moTa: string | null };
type Operator = { maNhaXe: string; tenNhaXe: string; trangThai: string };
type Seat = { maGhe: string | null; viTri: string; tang: number; trangThai: string };
type Vehicle = { maXe: string; bienSo: string; maLoaiXe: string; maNhaXe: string; soChoNgoi: number; namSanXuat: number | null;
  ngayDangKiem: string; hanDangKiem: string; dungTichXang: number | null; tocDo: number | null; trangThai: string;
  dangKiemConHieuLuc: boolean; soDoHopLe: boolean; coTheSuaSoDo: boolean; soGheThucTe: number };
const vehicleStates: Record<string, string> = { SAN_SANG: 'Sẵn sàng', DANG_CHAY: 'Đang chạy', BAO_TRI: 'Bảo trì', HONG: 'Hỏng', NGUNG_HOAT_DONG: 'Ngừng hoạt động' };
const seatStates: Record<string, string> = { HOAT_DONG: 'Hoạt động', BAO_TRI: 'Bảo trì', NGUNG_SU_DUNG: 'Ngừng sử dụng' };
const emptyType = { maLoaiXe: '', tenLoaiXe: '', soChoNgoi: '', soTang: '1', moTa: '' };
const emptyVehicle = { maXe: '', bienSo: '', maLoaiXe: '', maNhaXe: '', namSanXuat: '', ngayDangKiem: '', hanDangKiem: '', dungTichXang: '', tocDo: '', trangThai: 'SAN_SANG' };

function Field({ label, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return <label>{label}<input data-validation-label={label.toLowerCase()} {...props}
    onKeyDown={event => { if (props.type === 'number' && ['ArrowUp', 'ArrowDown'].includes(event.key)) event.preventDefault(); }} /></label>;
}

export default function VehicleManagement() {
  const { user, loading, request } = useAuth(); const router = useRouter();
  const isAdmin = user?.loaiTaiKhoan === 'ADMIN'; const allowed = isAdmin || user?.loaiTaiKhoan === 'NHA_XE';
  const [tab, setTab] = useState<'vehicles' | 'types'>('vehicles');
  const [types, setTypes] = useState<Kind[]>([]); const [operators, setOperators] = useState<Operator[]>([]); const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [typeResults, setTypeResults] = useState<Kind[]>([]);
  const [typeForm, setTypeForm] = useState(emptyType); const [editingType, setEditingType] = useState(false);
  const [vehicleForm, setVehicleForm] = useState(emptyVehicle); const [editingVehicle, setEditingVehicle] = useState(false);
  const [selected, setSelected] = useState<Vehicle | null>(null); const [seats, setSeats] = useState<Seat[]>([]);
  const [query, setQuery] = useState(''); const [typeQuery, setTypeQuery] = useState('');
  const [filterType, setFilterType] = useState(''); const [filterOwner, setFilterOwner] = useState(''); const [filterState, setFilterState] = useState('');
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  const sending = useRef(false); const revision = useRef(0);
  const [typeFormTarget, focusTypeForm] = useManagementFocus<HTMLElement>();
  const [vehicleFormTarget, focusVehicleForm] = useManagementFocus<HTMLElement>();
  const [detailTarget, focusDetail] = useManagementFocus<HTMLElement>();
  function clearNotice() { revision.current++; setError(''); setSuccess(''); }
  function interaction(event: React.SyntheticEvent<HTMLDivElement>) { if (event.target instanceof Element && event.target.closest('button,input,select,textarea,a')) clearNotice(); }
  async function loadVehicles() {
    const params = new URLSearchParams({ q: query, maLoaiXe: filterType, maNhaXe: filterOwner, trangThai: filterState });
    const data = await request<Vehicle[]>(`/xe?${params}`); setVehicles(data); return data;
  }
  async function action(operation: () => Promise<void>, message: string) {
    if (sending.current) return;
    clearNotice(); const version = revision.current; sending.current = true; setBusy(true);
    try { await operation(); if (version === revision.current) setSuccess(message); }
    catch (failure) { if (version === revision.current) setError(errorMessage(failure)); }
    finally { sending.current = false; setBusy(false); }
  }
  useEffect(() => {
    if (!loading && !user) router.replace('/dang-nhap?next=/quan-tri/xe');
    if (!allowed) return;
    let active = true;
    Promise.all([request<Kind[]>('/loai-xe'), request<Vehicle[]>('/xe'), request<Operator[]>('/xe/pham-vi')])
      .then(([kinds, cars, owners]) => { if (active) { setTypes(kinds); setTypeResults(kinds); setVehicles(cars); setOperators(owners); } })
      .catch(failure => { if (active) setError(errorMessage(failure)); });
    return () => { active = false; };
  }, [allowed, loading, user, router, request]);
  async function detail(vehicle: Vehicle, navigate = true) {
    let loaded = false;
    await action(async () => {
      const [current, map] = await Promise.all([request<Vehicle>(`/xe/${encodeURIComponent(vehicle.maXe)}`), request<Seat[]>(`/xe/${encodeURIComponent(vehicle.maXe)}/ghe`)]);
      setSelected(current); setSeats(map);
      loaded = true;
    }, '');
    if (navigate && loaded) focusDetail();
  }
  function editVehicle(vehicle: Vehicle) {
    setEditingVehicle(true);
    setVehicleForm({ maXe: vehicle.maXe, bienSo: vehicle.bienSo, maLoaiXe: vehicle.maLoaiXe, maNhaXe: vehicle.maNhaXe,
      ngayDangKiem: vehicle.ngayDangKiem ?? '', hanDangKiem: vehicle.hanDangKiem ?? '', trangThai: vehicle.trangThai,
      namSanXuat: vehicle.namSanXuat?.toString() ?? '', dungTichXang: vehicle.dungTichXang?.toString() ?? '', tocDo: vehicle.tocDo?.toString() ?? '' });
    focusVehicleForm('input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not(:disabled)');
  }
  function editType(type: Kind) {
    setEditingType(true); setTypeForm({ ...type, soChoNgoi: String(type.soChoNgoi), soTang: String(type.soTang), moTa: type.moTa ?? '' });
    focusTypeForm('input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not(:disabled)');
  }
  function submitType(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void action(async () => {
      await request(`/loai-xe${editingType ? `/${encodeURIComponent(typeForm.maLoaiXe)}` : ''}`, editingType ? 'PUT' : 'POST',
        { ...typeForm, soChoNgoi: Number(typeForm.soChoNgoi), soTang: Number(typeForm.soTang), moTa: typeForm.moTa || null });
      const kinds = await request<Kind[]>('/loai-xe'); setTypes(kinds); setTypeResults(kinds); setTypeQuery(''); setTypeForm(emptyType); setEditingType(false);
    }, editingType ? 'Đã cập nhật loại xe.' : 'Đã thêm loại xe.');
  }
  function submitVehicle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void action(async () => {
      const current = await request<Vehicle>(`/xe${editingVehicle ? `/${encodeURIComponent(vehicleForm.maXe)}` : ''}`, editingVehicle ? 'PUT' : 'POST',
        { ...vehicleForm, maNhaXe: vehicleForm.maNhaXe || operators[0]?.maNhaXe,
          namSanXuat: vehicleForm.namSanXuat ? Number(vehicleForm.namSanXuat) : null,
          dungTichXang: vehicleForm.dungTichXang ? Number(vehicleForm.dungTichXang) : null, tocDo: vehicleForm.tocDo ? Number(vehicleForm.tocDo) : null });
      await loadVehicles(); setSelected(current); setSeats(await request<Seat[]>(`/xe/${encodeURIComponent(current.maXe)}/ghe`));
      setEditingVehicle(false); setVehicleForm(emptyVehicle);
      focusDetail();
    }, editingVehicle ? 'Đã cập nhật xe.' : 'Đã thêm xe và tạo sơ đồ ghế.');
  }
  function updateSeat(index: number, changes: Partial<Seat>) { setSeats(current => current.map((seat, i) => i === index ? { ...seat, ...changes } : seat)); }
  const selectedType = types.find(type => type.maLoaiXe === vehicleForm.maLoaiXe);
  const layoutType = types.find(type => type.maLoaiXe === selected?.maLoaiXe);
  if (loading || !user) return <div className={styles.page}>Đang kiểm tra phiên đăng nhập…</div>;
  if (!allowed) return <div className={styles.page}><p role="alert">Bạn không có quyền quản lý xe.</p></div>;
  return <div className={styles.page} onClickCapture={interaction} onFocusCapture={interaction} onInputCapture={interaction} onChangeCapture={interaction}>
    <h1>Quản lý xe và sơ đồ ghế</h1><p>Quản lý phương tiện, đăng kiểm và trạng thái ghế vật lý.</p>
    <nav className={styles.tabs}><button aria-pressed={tab === 'vehicles'} onClick={() => setTab('vehicles')}>Xe và ghế</button>
      {isAdmin && <button aria-pressed={tab === 'types'} onClick={() => setTab('types')}>Loại xe</button>}</nav>
    {busy && <p role="status">Đang xử lý…</p>}{error && <p className={styles.error} role="alert">{error}</p>}{success && <p className={styles.success} role="status">{success}</p>}
    {tab === 'types' && isAdmin && <section ref={typeFormTarget} className={styles.panel}><h2 data-management-focus tabIndex={-1}>{editingType ? 'Sửa loại xe' : 'Thêm loại xe'}</h2>
      <form className={styles.form} onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation} onSubmit={submitType}>
        <Field label="Mã loại xe" required maxLength={40} disabled={editingType} value={typeForm.maLoaiXe} onChange={e => setTypeForm({ ...typeForm, maLoaiXe: e.target.value })} />
        <Field label="Tên loại xe" required maxLength={120} value={typeForm.tenLoaiXe} onChange={e => setTypeForm({ ...typeForm, tenLoaiXe: e.target.value })} />
        <Field label="Sức chứa" type="number" min="1" step="1" required value={typeForm.soChoNgoi} onChange={e => setTypeForm({ ...typeForm, soChoNgoi: e.target.value })} />
        <Field label="Số tầng" type="number" min="1" step="1" required value={typeForm.soTang} onChange={e => setTypeForm({ ...typeForm, soTang: e.target.value })} />
        <Field label="Mô tả loại xe" maxLength={1000} value={typeForm.moTa} onChange={e => setTypeForm({ ...typeForm, moTa: e.target.value })} />
        <button disabled={busy}>{editingType ? 'Lưu loại xe' : 'Thêm loại xe'}</button>
        {editingType && <button type="button" onClick={() => { setEditingType(false); setTypeForm(emptyType); }}>Hủy sửa</button>}
      </form>
      <div className={styles.filters}><Field label="Tìm loại xe" value={typeQuery} onChange={e => setTypeQuery(e.target.value)} />
        <button onClick={() => void action(async () => setTypeResults(await request<Kind[]>(`/loai-xe?q=${encodeURIComponent(typeQuery)}`)), '')}>Tìm loại xe</button></div>
      <div className={styles.scroll}><table><thead><tr><th>Mã</th><th>Loại xe</th><th>Sức chứa</th><th>Tầng</th><th>Thao tác</th></tr></thead><tbody>
        {typeResults.map(type => <tr key={type.maLoaiXe}><td>{type.maLoaiXe}</td><td>{type.tenLoaiXe}</td><td>{type.soChoNgoi}</td><td>{type.soTang}</td><td>
          <button onClick={() => editType(type)}>Sửa</button>
          <button disabled={busy} onClick={() => { if (window.confirm(`Xóa loại xe ${type.tenLoaiXe}?`)) void action(async () => { await request(`/loai-xe/${encodeURIComponent(type.maLoaiXe)}`, 'DELETE'); const kinds = await request<Kind[]>('/loai-xe'); setTypes(kinds); setTypeResults(kinds); setTypeQuery(''); }, 'Đã xóa loại xe.'); }}>Xóa</button>
        </td></tr>)}</tbody></table></div>
    </section>}
    {tab === 'vehicles' && <>
      <section ref={vehicleFormTarget} className={styles.panel}><h2 data-management-focus tabIndex={-1}>{editingVehicle ? `Sửa xe ${vehicleForm.maXe}` : 'Thêm xe'}</h2>
        <p>Sức chứa lấy từ loại xe. Xe không đủ đăng kiểm hoặc đang bảo trì không được gán vào chuyến mới.</p>
        <form className={styles.form} onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation} onSubmit={submitVehicle}>
          <Field label="Mã xe" required maxLength={40} disabled={editingVehicle} value={vehicleForm.maXe} onChange={e => setVehicleForm({ ...vehicleForm, maXe: e.target.value })} />
          <Field label="Biển số" required maxLength={30} value={vehicleForm.bienSo} onChange={e => setVehicleForm({ ...vehicleForm, bienSo: e.target.value })} />
          <label>Loại xe<select data-validation-label="loại xe" required disabled={editingVehicle} value={vehicleForm.maLoaiXe} onChange={e => setVehicleForm({ ...vehicleForm, maLoaiXe: e.target.value })}><option value="">Chọn loại xe</option>{types.map(type => <option key={type.maLoaiXe} value={type.maLoaiXe}>{type.tenLoaiXe} · {type.soChoNgoi} chỗ / {type.soTang} tầng</option>)}</select></label>
          <label>Nhà xe<select data-validation-label="nhà xe" required disabled={editingVehicle || !isAdmin} value={vehicleForm.maNhaXe || (!isAdmin ? operators[0]?.maNhaXe ?? '' : '')} onChange={e => setVehicleForm({ ...vehicleForm, maNhaXe: e.target.value })}><option value="">Chọn nhà xe</option>{operators.map(owner => <option key={owner.maNhaXe} value={owner.maNhaXe}>{owner.tenNhaXe}</option>)}</select></label>
          <Field label="Số ghế" readOnly value={selectedType?.soChoNgoi ?? ''} />
          <Field label="Năm sản xuất" type="number" min="1" step="1" value={vehicleForm.namSanXuat} onChange={e => setVehicleForm({ ...vehicleForm, namSanXuat: e.target.value })} />
          <Field label="Ngày đăng kiểm" type="date" required value={vehicleForm.ngayDangKiem} onChange={e => setVehicleForm({ ...vehicleForm, ngayDangKiem: e.target.value })} />
          <Field label="Hạn đăng kiểm" type="date" min={vehicleForm.ngayDangKiem || undefined} required value={vehicleForm.hanDangKiem} onChange={e => setVehicleForm({ ...vehicleForm, hanDangKiem: e.target.value })} />
          <Field label="Dung tích nhiên liệu (lít)" type="number" min="0.01" step="0.01" value={vehicleForm.dungTichXang} onChange={e => setVehicleForm({ ...vehicleForm, dungTichXang: e.target.value })} />
          <Field label="Tốc độ (km/giờ)" type="number" min="0.01" step="0.01" value={vehicleForm.tocDo} onChange={e => setVehicleForm({ ...vehicleForm, tocDo: e.target.value })} />
          <label>Trạng thái xe<select value={vehicleForm.trangThai} onChange={e => setVehicleForm({ ...vehicleForm, trangThai: e.target.value })}>{Object.entries(vehicleStates).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <button disabled={busy}>{editingVehicle ? 'Lưu xe' : 'Thêm xe'}</button>
          {editingVehicle && <button type="button" onClick={() => { setEditingVehicle(false); setVehicleForm(emptyVehicle); }}>Hủy sửa xe</button>}
        </form>
      </section>
      <section className={styles.panel}><h2>Danh sách xe</h2><div className={styles.filters}>
        <Field label="Tìm biển số hoặc mã xe" value={query} onChange={e => setQuery(e.target.value)} />
        <label>Lọc loại xe<select value={filterType} onChange={e => setFilterType(e.target.value)}><option value="">Tất cả</option>{types.map(type => <option key={type.maLoaiXe} value={type.maLoaiXe}>{type.tenLoaiXe}</option>)}</select></label>
        {isAdmin && <label>Lọc nhà xe<select value={filterOwner} onChange={e => setFilterOwner(e.target.value)}><option value="">Tất cả</option>{operators.map(owner => <option key={owner.maNhaXe} value={owner.maNhaXe}>{owner.tenNhaXe}</option>)}</select></label>}
        <label>Lọc trạng thái<select value={filterState} onChange={e => setFilterState(e.target.value)}><option value="">Tất cả</option>{Object.entries(vehicleStates).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <button disabled={busy} onClick={() => void action(async () => { await loadVehicles(); }, '')}>Lọc xe</button></div>
        <div className={styles.scroll}><table><thead><tr><th>Mã xe</th><th>Biển số</th><th>Loại xe</th><th>Nhà xe</th><th>Trạng thái</th><th>Đăng kiểm</th><th>Thao tác</th></tr></thead><tbody>
          {vehicles.map(vehicle => <tr key={vehicle.maXe}><td>{vehicle.maXe}</td><td>{vehicle.bienSo}</td><td>{types.find(type => type.maLoaiXe === vehicle.maLoaiXe)?.tenLoaiXe || vehicle.maLoaiXe}</td><td>{operators.find(owner => owner.maNhaXe === vehicle.maNhaXe)?.tenNhaXe || vehicle.maNhaXe}</td><td>{vehicleStates[vehicle.trangThai]}</td><td>{vehicle.dangKiemConHieuLuc ? 'Còn hiệu lực' : 'Chưa đủ điều kiện'}</td><td>
            <button onClick={() => editVehicle(vehicle)}>Sửa</button><button onClick={() => void detail(vehicle)}>Chi tiết / ghế</button>
            <button disabled={busy} onClick={() => { if (window.confirm(`Xóa xe ${vehicle.bienSo}?`)) void action(async () => { await request(`/xe/${encodeURIComponent(vehicle.maXe)}`, 'DELETE'); await loadVehicles(); if (selected?.maXe === vehicle.maXe) { setSelected(null); setSeats([]); } }, 'Đã xóa xe.'); }}>Xóa</button>
          </td></tr>)}{vehicles.length === 0 && <tr><td colSpan={7}>Không có xe phù hợp.</td></tr>}
        </tbody></table></div>
      </section>
      {selected && <section ref={detailTarget} className={styles.panel}><h2 data-management-focus tabIndex={-1}>Chi tiết xe · {selected.bienSo}</h2>
        <p>Nhà xe: {operators.find(owner => owner.maNhaXe === selected.maNhaXe)?.tenNhaXe || selected.maNhaXe}. Đăng kiểm: {selected.ngayDangKiem} → {selected.hanDangKiem}.</p>
        <p>Loại xe: {layoutType?.tenLoaiXe || selected.maLoaiXe} · Sức chứa: {selected.soChoNgoi} · Trạng thái: {vehicleStates[selected.trangThai]}.</p>
        <p>Năm sản xuất: {selected.namSanXuat ?? 'Chưa nhập'} · Dung tích nhiên liệu: {selected.dungTichXang == null ? 'Chưa nhập' : `${selected.dungTichXang} lít`} · Tốc độ: {selected.tocDo == null ? 'Chưa nhập' : `${selected.tocDo} km/giờ`}.</p>
        {!selected.dangKiemConHieuLuc && <p className={styles.warning}>Xe chưa đủ hiệu lực đăng kiểm; không được mở bán chuyến mới.</p>}
        {!selected.soDoHopLe && <p className={styles.warning}>Sơ đồ hiện tại chưa khớp sức chứa hoặc có cấu hình không hợp lệ.</p>}
        {!selected.coTheSuaSoDo && <p className={styles.warning}>Xe có lịch chuyến hoặc ghế có lịch sử vé/giữ chỗ. Không được sửa sơ đồ; cần xử lý lịch vận hành trước.</p>}
        <h3>Sơ đồ ghế · {seats.length}/{selected.soChoNgoi} chỗ</h3><p>Mỗi ô là ghế vật lý. Màu thể hiện hoạt động/bảo trì, không phải trạng thái đã bán của một chuyến.</p>
        {Array.from({ length: layoutType?.soTang ?? 1 }, (_, floor) => <div key={floor}><h4>Tầng {floor + 1}</h4><div className={styles.seatGrid}>{seats.filter(seat => seat.tang === floor + 1).slice().sort((a, b) => a.viTri.localeCompare(b.viTri, 'vi', { numeric: true })).map((seat, index) => <span key={seat.maGhe ?? `${floor}-${index}`} className={`${styles.seat} ${seat.trangThai === 'HOAT_DONG' ? styles.activeSeat : styles.maintenanceSeat}`}>{seat.viTri}<small>{seatStates[seat.trangThai]}</small></span>)}</div></div>)}
        <form onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation} onSubmit={event => { event.preventDefault(); void action(async () => {
          setSeats(await request<Seat[]>(`/xe/${encodeURIComponent(selected.maXe)}/so-do-ghe`, 'PUT', { danhSachGhe: seats.map(({ maGhe, viTri, tang, trangThai }) => ({ maGhe, viTri, tang, trangThai })) }));
          const current = await request<Vehicle>(`/xe/${encodeURIComponent(selected.maXe)}`); setSelected(current); await loadVehicles();
        }, 'Đã lưu sơ đồ ghế.'); }}>
          <div className={styles.scroll}><table><thead><tr><th>Mã/vị trí</th><th>Tầng</th><th>Trạng thái ghế</th><th>ID ghế (cố định)</th><th>Thao tác</th></tr></thead><tbody>{seats.map((seat, index) => <tr key={seat.maGhe ?? `new-${index}`}>
            <td><input aria-label={`Mã/vị trí ghế ${index + 1}`} data-validation-label="mã/vị trí ghế" required maxLength={20} disabled={!selected.coTheSuaSoDo} value={seat.viTri} onChange={e => updateSeat(index, { viTri: e.target.value })} /></td>
            <td><select aria-label={`Tầng ghế ${index + 1}`} disabled={!selected.coTheSuaSoDo} value={seat.tang} onChange={e => updateSeat(index, { tang: Number(e.target.value) })}>{Array.from({ length: layoutType?.soTang ?? 1 }, (_, floor) => <option key={floor} value={floor + 1}>{floor + 1}</option>)}</select></td>
            <td><select aria-label={`Trạng thái ghế ${index + 1}`} disabled={!selected.coTheSuaSoDo} value={seat.trangThai} onChange={e => updateSeat(index, { trangThai: e.target.value })}>{Object.entries(seatStates).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></td>
            <td><small>{seat.maGhe ?? 'Tự sinh khi lưu'}</small></td><td><button type="button" disabled={!selected.coTheSuaSoDo || busy} onClick={() => setSeats(current => current.filter((_, i) => i !== index))}>Bỏ vị trí</button></td>
          </tr>)}</tbody></table></div>
          <div className={styles.buttons}><button type="button" disabled={!selected.coTheSuaSoDo || busy} onClick={() => setSeats(current => [...current, { maGhe: null, viTri: '', tang: 1, trangThai: 'HOAT_DONG' }])}>Thêm vị trí</button>
            <button disabled={!selected.coTheSuaSoDo || busy || seats.length !== selected.soChoNgoi}>Lưu sơ đồ ghế</button>
            <button type="button" disabled={busy} onClick={() => void detail(selected, false)}>Tải lại sơ đồ</button></div>
          {seats.length !== selected.soChoNgoi && <p className={styles.warning}>Danh sách đang có {seats.length} ghế; cần đúng {selected.soChoNgoi} ghế để lưu. Các thay đổi chưa được lưu.</p>}
        </form>
      </section>}
    </>}
  </div>;
}
