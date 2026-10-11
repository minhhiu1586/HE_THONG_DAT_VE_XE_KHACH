'use client';

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/components/Account/AuthProvider';
import { errorMessage } from '@/lib/account-api';
import { vietnameseValidation, clearValidation } from '@/lib/form-validation';
import styles from './RouteManagement.module.css';

type City = { maTinhThanh: string; tenTinhThanh: string; khuVuc: string | null };
type Station = { maBenXe: string; tenBenXe: string; diaChi: string; soDienThoai: string | null; maTinhThanh: string; trangThai: string };
type Stop = { maDiemDung: string; tenDiemDung: string; diaChi: string; thuTu: number; thoiGianDung: number; loaiDiemDung: string };
type Route = { maTuyen: string; tenTuyen: string; tramKhoiHanh: string; tramDen: string; tramTrungGian: Stop[]; thoiGianKhoiHanh: string | null; khoangCachKM: number | null; thoiGianDuKien: number | null; giaCoBan: number; moTa: string | null; trangThai: string; maNhaXe: string | null };
type Tab = 'routes' | 'stations' | 'cities';
const emptyCity = { maTinhThanh: '', tenTinhThanh: '', khuVuc: '' };
const emptyStation = { maBenXe: '', tenBenXe: '', diaChi: '', soDienThoai: '', maTinhThanh: '', trangThai: 'HOAT_DONG' };
const emptyRoute = { maTuyen: '', tenTuyen: '', tramKhoiHanh: '', tramDen: '', thoiGianKhoiHanh: '', khoangCachKM: '', thoiGianDuKien: '', giaCoBan: '', moTa: '', trangThai: 'SAP_KHAI_THAC' };
const statusLabels: Record<string, string> = { SAP_KHAI_THAC: 'Sắp khai thác', DANG_KHAI_THAC: 'Đang khai thác', TAM_NGUNG: 'Tạm ngừng', NGUNG_KHAI_THAC: 'Ngừng khai thác' };
const statuses = ['SAP_KHAI_THAC', 'DANG_KHAI_THAC', 'TAM_NGUNG', 'NGUNG_KHAI_THAC'];

export default function RouteManagement() {
  const { user, loading, request } = useAuth(); const router = useRouter();
  const [tab, setTab] = useState<Tab>('routes'); const [cities, setCities] = useState<City[]>([]);
  const [stations, setStations] = useState<Station[]>([]); const [routes, setRoutes] = useState<Route[]>([]);
  const [citySearch, setCitySearch] = useState(''); const [stationSearch, setStationSearch] = useState('');
  const [stationCity, setStationCity] = useState(''); const [stationStatus, setStationStatus] = useState('');
  const [filterStart, setFilterStart] = useState(''); const [filterEnd, setFilterEnd] = useState(''); const [filterStatus, setFilterStatus] = useState(''); const [filterOperator, setFilterOperator] = useState('');
  const [cityForm, setCityForm] = useState(emptyCity); const [stationForm, setStationForm] = useState(emptyStation);
  const [routeForm, setRouteForm] = useState(emptyRoute); const [editingCity, setEditingCity] = useState(false);
  const [editingStation, setEditingStation] = useState(false); const [editingRoute, setEditingRoute] = useState(false);
  const [selectedRoute, setSelectedRoute] = useState(''); const [editingStop, setEditingStop] = useState('');
  const [stopForm, setStopForm] = useState({ maDiemDung: '', tenDiemDung: '', diaChi: '', thoiGianDung: '0', loaiDiemDung: 'CA_HAI' });
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  const noticeVersion = useRef(0);
  function clearNotices() { noticeVersion.current++; setError(''); setSuccess(''); }
  function clearOnInteraction(event: React.SyntheticEvent<HTMLDivElement>) {
    if (event.target instanceof Element && event.target.closest('button, input, select, textarea, a')) clearNotices();
  }
  const isAdmin = user?.loaiTaiKhoan === 'ADMIN'; const mayManage = isAdmin || user?.loaiTaiKhoan === 'NHA_XE';

  const loadCities = useCallback(async () => setCities(await request<City[]>(`/tinh-thanh?q=${encodeURIComponent(citySearch)}`)), [request, citySearch]);
  const loadStations = useCallback(async () => {
    const query = new URLSearchParams(); if (stationSearch) query.set('q', stationSearch);
    if (stationCity) query.set('maTinhThanh', stationCity); if (stationStatus) query.set('trangThai', stationStatus);
    setStations(await request<Station[]>(`/ben-xe${query.size ? `?${query}` : ''}`));
  }, [request, stationSearch, stationCity, stationStatus]);
  const loadRoutes = useCallback(async () => {
    const query = new URLSearchParams();
    if (filterStart) query.set('tramKhoiHanh', filterStart); if (filterEnd) query.set('tramDen', filterEnd); if (filterStatus) query.set('trangThai', filterStatus);
    if (isAdmin && filterOperator) query.set('maNhaXe', filterOperator);
    setRoutes(await request<Route[]>(`/tuyen-xe${query.size ? `?${query}` : ''}`));
  }, [request, filterStart, filterEnd, filterStatus, filterOperator, isAdmin]);
  const refresh = useCallback(async () => { await Promise.all([loadCities(), loadStations(), loadRoutes()]); }, [loadCities, loadStations, loadRoutes]);
  useEffect(() => {
    if (!loading && !user) router.replace('/dang-nhap?next=/quan-tri/tuyen-duong');
    if (!mayManage) return;
    let active = true;
    Promise.all([request<City[]>('/tinh-thanh'), request<Station[]>('/ben-xe'), request<Route[]>('/tuyen-xe')])
      .then(([cityData, stationData, routeData]) => {
        if (active) { setCities(cityData); setStations(stationData); setRoutes(routeData); }
      }).catch(e => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, [loading, user, mayManage, router, request]);
  const currentRoute = useMemo(() => routes.find(route => route.maTuyen === selectedRoute) ?? routes[0], [routes, selectedRoute]);

  async function submit(event: FormEvent<HTMLFormElement>, action: () => Promise<unknown>, message: string) {
    event.preventDefault(); clearNotices(); const version = noticeVersion.current; setBusy(true);
    try { await action(); await refresh(); if (version === noticeVersion.current) setSuccess(message); }
    catch (e) { if (version === noticeVersion.current) setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  function citySubmit(event: FormEvent<HTMLFormElement>) {
    const body = { tenTinhThanh: cityForm.tenTinhThanh, khuVuc: cityForm.khuVuc || null };
    void submit(event, async () => {
      if (editingCity) await request(`/tinh-thanh/${encodeURIComponent(cityForm.maTinhThanh)}`, 'PUT', body);
      else await request('/tinh-thanh', 'POST', { ...cityForm, khuVuc: cityForm.khuVuc || null });
      setCityForm(emptyCity); setEditingCity(false);
    }, editingCity ? 'Đã cập nhật tỉnh/thành phố.' : 'Đã thêm tỉnh/thành phố.');
  }
  function stationSubmit(event: FormEvent<HTMLFormElement>) {
    const body = { ...stationForm, soDienThoai: stationForm.soDienThoai || null };
    void submit(event, async () => {
      if (editingStation) await request(`/ben-xe/${encodeURIComponent(stationForm.maBenXe)}`, 'PUT', body);
      else await request('/ben-xe', 'POST', body);
      setStationForm(emptyStation); setEditingStation(false);
    }, editingStation ? 'Đã cập nhật bến xe.' : 'Đã thêm bến xe.');
  }
  function routeSubmit(event: FormEvent<HTMLFormElement>) {
    const body = { ...routeForm, thoiGianKhoiHanh: routeForm.thoiGianKhoiHanh || null,
      khoangCachKM: routeForm.khoangCachKM ? Number(routeForm.khoangCachKM) : null,
      thoiGianDuKien: Number(routeForm.thoiGianDuKien), giaCoBan: Number(routeForm.giaCoBan), moTa: routeForm.moTa || null };
    void submit(event, async () => {
      if (editingRoute) await request(`/tuyen-xe/${encodeURIComponent(routeForm.maTuyen)}`, 'PUT', body);
      else await request('/tuyen-xe', 'POST', body);
      setSelectedRoute(routeForm.maTuyen);
      setRouteForm(emptyRoute); setEditingRoute(false);
    }, editingRoute ? 'Đã cập nhật tuyến xe.' : 'Đã tạo tuyến xe.');
  }
  function editRoute(route: Route) {
    setSelectedRoute(route.maTuyen); setEditingRoute(true);
    setRouteForm({ maTuyen: route.maTuyen, tenTuyen: route.tenTuyen, tramKhoiHanh: route.tramKhoiHanh, tramDen: route.tramDen,
      thoiGianKhoiHanh: route.thoiGianKhoiHanh?.slice(0, 5) ?? '', khoangCachKM: route.khoangCachKM?.toString() ?? '',
      thoiGianDuKien: route.thoiGianDuKien?.toString() ?? '', giaCoBan: route.giaCoBan.toString(), moTa: route.moTa ?? '', trangThai: route.trangThai });
  }
  async function mutate(path: string, method: string, body?: unknown, message = 'Đã cập nhật dữ liệu.') {
    clearNotices(); const version = noticeVersion.current; setBusy(true);
    try { await request(path, method, body); await refresh(); if (version === noticeVersion.current) setSuccess(message); }
    catch (e) { if (version === noticeVersion.current) setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  if (loading || !user) return <div className={styles.page}>Đang kiểm tra phiên đăng nhập…</div>;
  if (!mayManage) return <div className={styles.page}><p role="alert" className={styles.error}>Bạn không có quyền quản lý tuyến xe.</p></div>;

  return <div className={styles.page} onClickCapture={clearOnInteraction} onFocusCapture={clearOnInteraction} onInputCapture={clearOnInteraction} onChangeCapture={clearOnInteraction}>
    <header className={styles.heading}><div><p className={styles.eyebrow}>QUẢN TRỊ VẬN HÀNH</p><h1>Quản lý tuyến đường</h1><p>Quản lý tỉnh thành, bến xe, tuyến và các điểm dừng.</p></div></header>
    <nav className={styles.tabs} aria-label="Nhóm dữ liệu">
      <button aria-pressed={tab === 'routes'} onClick={() => setTab('routes')}>Tuyến xe</button>
      {isAdmin && <><button aria-pressed={tab === 'stations'} onClick={() => setTab('stations')}>Bến xe</button><button aria-pressed={tab === 'cities'} onClick={() => setTab('cities')}>Tỉnh/thành</button></>}
    </nav>
    {error && <p className={styles.error} role="alert">{error}</p>}{success && <p className={styles.success} role="status">{success}</p>}

    {tab === 'cities' && isAdmin && <section className={styles.panel}>
      <div className={styles.sectionTitle}><div><h2>Tỉnh/thành phố</h2><p>Danh mục tỉnh thành được dùng để xác định tỉnh đi và tỉnh đến qua bến xe.</p></div><div className={styles.search}><input aria-label="Tìm tỉnh thành" placeholder="Tìm theo mã hoặc tên" value={citySearch} onChange={e => setCitySearch(e.target.value)} /><button onClick={() => void loadCities()}>Tìm</button></div></div>
      <form onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation} className={styles.form} onSubmit={citySubmit}><label>Mã tỉnh/thành<input data-validation-label="mã tỉnh/thành" value={cityForm.maTinhThanh} disabled={editingCity} maxLength={40} required onChange={e => setCityForm({ ...cityForm, maTinhThanh: e.target.value })} /></label>
        <label>Tên<input data-validation-label="tên tỉnh/thành phố" value={cityForm.tenTinhThanh} maxLength={120} required onChange={e => setCityForm({ ...cityForm, tenTinhThanh: e.target.value })} /></label>
        <label>Khu vực<input data-validation-label="khu vực" value={cityForm.khuVuc} maxLength={80} onChange={e => setCityForm({ ...cityForm, khuVuc: e.target.value })} /></label>
        <button disabled={busy}>{editingCity ? 'Lưu thay đổi' : 'Thêm tỉnh/thành'}</button>{editingCity && <button type="button" onClick={() => { setCityForm(emptyCity); setEditingCity(false); }}>Hủy</button>}
      </form>
      <div className={styles.tableWrap}><table><thead><tr><th>Mã</th><th>Tỉnh/thành</th><th>Khu vực</th><th>Thao tác</th></tr></thead><tbody>
        {cities.map(city => <tr key={city.maTinhThanh}><td>{city.maTinhThanh}</td><td>{city.tenTinhThanh}</td><td>{city.khuVuc || '—'}</td><td className={styles.actions}>
          <button onClick={() => { setCityForm({ maTinhThanh: city.maTinhThanh, tenTinhThanh: city.tenTinhThanh, khuVuc: city.khuVuc || '' }); setEditingCity(true); }}>Sửa</button>
          <button disabled={busy} onClick={() => { if (window.confirm(`Xóa ${city.tenTinhThanh}?`)) void mutate(`/tinh-thanh/${encodeURIComponent(city.maTinhThanh)}`, 'DELETE', undefined, 'Đã xóa tỉnh/thành phố.'); }}>Xóa</button>
        </td></tr>)}{cities.length === 0 && <tr><td colSpan={4}>Chưa có tỉnh/thành phù hợp.</td></tr>}</tbody></table></div>
    </section>}

    {tab === 'stations' && isAdmin && <section className={styles.panel}>
      <div className={styles.sectionTitle}><div><h2>Bến xe</h2><p>Mỗi bến thuộc một tỉnh/thành phố; bến đã gắn tuyến không thể xóa.</p></div><div className={styles.search}><input aria-label="Tìm bến xe" placeholder="Tìm theo mã hoặc tên" value={stationSearch} onChange={e => setStationSearch(e.target.value)} /><button onClick={() => void loadStations()}>Tìm</button></div></div>
      <div className={styles.filters}><label>Tỉnh/thành<select data-validation-label="tỉnh/thành" value={stationCity} onChange={e => setStationCity(e.target.value)}><option value="">Tất cả</option>{cities.map(city => <option key={city.maTinhThanh} value={city.maTinhThanh}>{city.tenTinhThanh}</option>)}</select></label>
        <label>Trạng thái<select data-validation-label="trạng thái" value={stationStatus} onChange={e => setStationStatus(e.target.value)}><option value="">Tất cả</option>{['HOAT_DONG','TAM_NGUNG','DONG_CUA','BAO_TRI'].map(s => <option key={s}>{s}</option>)}</select></label><button onClick={() => void loadStations()}>Lọc bến xe</button></div>
      <form onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation} className={styles.form} onSubmit={stationSubmit}><label>Mã bến<input data-validation-label="mã bến" value={stationForm.maBenXe} disabled={editingStation} maxLength={40} required onChange={e => setStationForm({ ...stationForm, maBenXe: e.target.value })} /></label>
        <label>Tên bến<input data-validation-label="tên bến" value={stationForm.tenBenXe} maxLength={160} required onChange={e => setStationForm({ ...stationForm, tenBenXe: e.target.value })} /></label>
        <label>Địa chỉ<input data-validation-label="địa chỉ" value={stationForm.diaChi} maxLength={255} required onChange={e => setStationForm({ ...stationForm, diaChi: e.target.value })} /></label>
        <label>Điện thoại<input data-validation-label="điện thoại" value={stationForm.soDienThoai} maxLength={30} onChange={e => setStationForm({ ...stationForm, soDienThoai: e.target.value })} /></label>
        <label>Tỉnh/thành<select data-validation-label="tỉnh/thành" required value={stationForm.maTinhThanh} onChange={e => setStationForm({ ...stationForm, maTinhThanh: e.target.value })}><option value="">Chọn tỉnh/thành</option>{cities.map(city => <option key={city.maTinhThanh} value={city.maTinhThanh}>{city.tenTinhThanh}</option>)}</select></label>
        <label>Trạng thái<select data-validation-label="trạng thái" value={stationForm.trangThai} onChange={e => setStationForm({ ...stationForm, trangThai: e.target.value })}>{['HOAT_DONG','TAM_NGUNG','DONG_CUA','BAO_TRI'].map(s => <option key={s}>{s}</option>)}</select></label>
        <button disabled={busy}>{editingStation ? 'Lưu thay đổi' : 'Thêm bến xe'}</button>{editingStation && <button type="button" onClick={() => { setStationForm(emptyStation); setEditingStation(false); }}>Hủy</button>}
      </form>
      <div className={styles.tableWrap}><table><thead><tr><th>Mã</th><th>Bến xe</th><th>Tỉnh</th><th>Địa chỉ</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
        {stations.map(station => <tr key={station.maBenXe}><td>{station.maBenXe}</td><td>{station.tenBenXe}</td><td>{cities.find(c => c.maTinhThanh === station.maTinhThanh)?.tenTinhThanh || station.maTinhThanh}</td><td>{station.diaChi}</td><td>{station.trangThai}</td><td className={styles.actions}>
          <button onClick={() => { setStationForm({ ...station, soDienThoai: station.soDienThoai || '' }); setEditingStation(true); }}>Sửa</button>
          <button disabled={busy} onClick={() => { if (window.confirm(`Xóa ${station.tenBenXe}?`)) void mutate(`/ben-xe/${encodeURIComponent(station.maBenXe)}`, 'DELETE', undefined, 'Đã xóa bến xe.'); }}>Xóa</button>
        </td></tr>)}{stations.length === 0 && <tr><td colSpan={6}>Chưa có bến xe phù hợp.</td></tr>}</tbody></table></div>
    </section>}

    {tab === 'routes' && <>
      <section className={styles.panel}>
        <div className={styles.sectionTitle}><div><h2>{editingRoute ? `Cập nhật tuyến ${routeForm.maTuyen}` : 'Tạo tuyến xe'}</h2><p>Thời lượng dự kiến là thuộc tính tuyến; giờ khởi hành thực tế được lưu riêng cho từng chuyến.</p></div>
          <button type="button" onClick={() => { setRouteForm(emptyRoute); setEditingRoute(false); }}>Tuyến mới</button></div>
        <form onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation} className={styles.form} onSubmit={routeSubmit}>
          <label>Mã tuyến<input data-validation-label="mã tuyến" disabled={editingRoute} required maxLength={40} value={routeForm.maTuyen} onChange={e => setRouteForm({ ...routeForm, maTuyen: e.target.value })} /></label>
          <label>Tên tuyến<input data-validation-label="tên tuyến" required maxLength={160} value={routeForm.tenTuyen} onChange={e => setRouteForm({ ...routeForm, tenTuyen: e.target.value })} /></label>
          <label>Bến khởi hành<select data-validation-label="bến khởi hành" required value={routeForm.tramKhoiHanh} onChange={e => setRouteForm({ ...routeForm, tramKhoiHanh: e.target.value })}><option value="">Chọn bến đi</option>{stations.filter(s => s.trangThai === 'HOAT_DONG').map(s => <option key={s.maBenXe} value={s.maBenXe}>{s.tenBenXe} · {cities.find(c => c.maTinhThanh === s.maTinhThanh)?.tenTinhThanh || s.maTinhThanh}</option>)}</select></label>
          <label>Bến đến<select data-validation-label="bến đến" required value={routeForm.tramDen} onChange={e => setRouteForm({ ...routeForm, tramDen: e.target.value })}><option value="">Chọn bến đến</option>{stations.filter(s => s.trangThai === 'HOAT_DONG').map(s => <option key={s.maBenXe} value={s.maBenXe}>{s.tenBenXe} · {cities.find(c => c.maTinhThanh === s.maTinhThanh)?.tenTinhThanh || s.maTinhThanh}</option>)}</select></label>
          <label>Giờ mặc định (không bắt buộc)<input data-validation-label="giờ mặc định" type="time" value={routeForm.thoiGianKhoiHanh} onChange={e => setRouteForm({ ...routeForm, thoiGianKhoiHanh: e.target.value })} /></label>
          <label>Thời lượng dự kiến (giờ)<input data-validation-label="thời lượng dự kiến" type="number" step="0.01" min="0.01" required value={routeForm.thoiGianDuKien} onChange={e => setRouteForm({ ...routeForm, thoiGianDuKien: e.target.value })} /></label>
          <label>Khoảng cách (km)<input data-validation-label="khoảng cách" type="number" step="0.01" min="0.01" value={routeForm.khoangCachKM} onChange={e => setRouteForm({ ...routeForm, khoangCachKM: e.target.value })} /></label>
          <label>Giá cơ bản (đồng)<input data-validation-label="giá cơ bản" type="number" step="0.01" min="0" required value={routeForm.giaCoBan} onChange={e => setRouteForm({ ...routeForm, giaCoBan: e.target.value })} /></label>
          <label className={styles.wide}>Mô tả<textarea data-validation-label="mô tả" maxLength={1000} value={routeForm.moTa} onChange={e => setRouteForm({ ...routeForm, moTa: e.target.value })} /></label>
          <label>Trạng thái khai thác<select data-validation-label="trạng thái khai thác" value={routeForm.trangThai} onChange={e => setRouteForm({ ...routeForm, trangThai: e.target.value })}>{statuses.map(status => <option key={status} value={status}>{statusLabels[status]}</option>)}</select></label>
          {editingRoute && <button type="button" disabled={busy} onClick={() => void mutate(`/tuyen-xe/${encodeURIComponent(routeForm.maTuyen)}/trang-thai`, 'PUT', { trangThai: routeForm.trangThai }, 'Đã cập nhật trạng thái tuyến.')}>Lưu trạng thái</button>}
          <button disabled={busy}>{editingRoute ? 'Lưu tuyến' : 'Tạo tuyến'}</button>
        </form>
      </section>
      <section className={styles.panel}>
        <div className={styles.sectionTitle}><div><h2>Danh sách tuyến</h2><p>Nhà xe chỉ xem và quản lý tuyến thuộc phạm vi của mình.</p></div></div>
        <div className={styles.filters}><label>Bến đi<select data-validation-label="bến đi" value={filterStart} onChange={e => setFilterStart(e.target.value)}><option value="">Tất cả</option>{stations.map(s => <option key={s.maBenXe} value={s.maBenXe}>{s.tenBenXe}</option>)}</select></label>
          <label>Bến đến<select data-validation-label="bến đến" value={filterEnd} onChange={e => setFilterEnd(e.target.value)}><option value="">Tất cả</option>{stations.map(s => <option key={s.maBenXe} value={s.maBenXe}>{s.tenBenXe}</option>)}</select></label>
          <label>Trạng thái<select data-validation-label="trạng thái" value={filterStatus} onChange={e => setFilterStatus(e.target.value)}><option value="">Tất cả</option>{statuses.map(s => <option key={s}>{s}</option>)}</select></label>
          {isAdmin && <label>Mã nhà xe<input data-validation-label="mã nhà xe" value={filterOperator} onChange={e => setFilterOperator(e.target.value)} placeholder="Không giới hạn" /></label>}
          <button onClick={() => void loadRoutes()}>Lọc tuyến</button>
        </div>
        <div className={styles.tableWrap}><table><thead><tr><th>Mã tuyến</th><th>Tuyến</th><th>Thời lượng</th><th>Nhà xe</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
          {routes.map(route => <tr key={route.maTuyen}><td>{route.maTuyen}</td><td>{stations.find(s => s.maBenXe === route.tramKhoiHanh)?.tenBenXe || route.tramKhoiHanh} → {stations.find(s => s.maBenXe === route.tramDen)?.tenBenXe || route.tramDen}</td>
            <td>{route.thoiGianDuKien ?? '—'} giờ</td><td>{route.maNhaXe || '—'}</td><td><span aria-label={`Trạng thái tuyến ${route.maTuyen}`}>{statusLabels[route.trangThai] || route.trangThai}</span></td>
            <td className={styles.actions}><button onClick={() => editRoute(route)}>Sửa</button><button onClick={() => setSelectedRoute(route.maTuyen)}>Điểm dừng</button><button disabled={busy} onClick={() => { if (window.confirm(`Xóa tuyến ${route.maTuyen}?`)) void mutate(`/tuyen-xe/${encodeURIComponent(route.maTuyen)}`, 'DELETE', undefined, 'Đã xóa tuyến.'); }}>Xóa</button></td></tr>)}
          {routes.length === 0 && <tr><td colSpan={6}>Chưa có tuyến phù hợp.</td></tr>}</tbody></table></div>
      </section>
      {currentRoute && <section className={styles.panel}><div className={styles.sectionTitle}><div><h2>Điểm dừng · {currentRoute.maTuyen}</h2><p>Điểm dừng không bắt buộc. Thứ tự bắt đầu từ 1 và cập nhật trong cùng một giao dịch.</p></div></div>
        <form onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation} className={styles.form} onSubmit={event => {
          const body = { tenDiemDung: stopForm.tenDiemDung, diaChi: stopForm.diaChi,
            thoiGianDung: Number(stopForm.thoiGianDung), loaiDiemDung: stopForm.loaiDiemDung };
          void submit(event, async () => {
            if (editingStop) await request(`/tuyen-xe/${encodeURIComponent(currentRoute.maTuyen)}/diem-dung/${encodeURIComponent(editingStop)}`, 'PUT', body);
            else await request(`/tuyen-xe/${encodeURIComponent(currentRoute.maTuyen)}/diem-dung`, 'POST', { ...body, maDiemDung: stopForm.maDiemDung, thuTu: currentRoute.tramTrungGian.length + 1 });
            setStopForm({ maDiemDung: '', tenDiemDung: '', diaChi: '', thoiGianDung: '0', loaiDiemDung: 'CA_HAI' }); setEditingStop('');
          }, editingStop ? 'Đã cập nhật điểm dừng.' : 'Đã thêm điểm dừng.');
        }}>
          {!editingStop && <label>Mã điểm dừng<input data-validation-label="mã điểm dừng" required maxLength={40} value={stopForm.maDiemDung} onChange={e => setStopForm({ ...stopForm, maDiemDung: e.target.value })} /></label>}
          <label>Tên điểm dừng<input data-validation-label="tên điểm dừng" required maxLength={160} value={stopForm.tenDiemDung} onChange={e => setStopForm({ ...stopForm, tenDiemDung: e.target.value })} /></label>
          <label>Địa chỉ<input data-validation-label="địa chỉ" required maxLength={255} value={stopForm.diaChi} onChange={e => setStopForm({ ...stopForm, diaChi: e.target.value })} /></label>
          <label>Thời gian dừng (phút)<input data-validation-label="thời gian dừng" type="number" min="0" required value={stopForm.thoiGianDung} onChange={e => setStopForm({ ...stopForm, thoiGianDung: e.target.value })} /></label>
          <label>Loại điểm dừng<select data-validation-label="loại điểm dừng" value={stopForm.loaiDiemDung} onChange={e => setStopForm({ ...stopForm, loaiDiemDung: e.target.value })}>{['DIEM_DON','DIEM_TRA','CA_HAI'].map(s => <option key={s}>{s}</option>)}</select></label>
          <button disabled={busy}>{editingStop ? 'Lưu điểm dừng' : 'Thêm điểm dừng'}</button>
          {editingStop && <button type="button" onClick={() => { setEditingStop(''); setStopForm({ maDiemDung: '', tenDiemDung: '', diaChi: '', thoiGianDung: '0', loaiDiemDung: 'CA_HAI' }); }}>Hủy sửa</button>}
        </form>
        <ol className={styles.stopList}>{currentRoute.tramTrungGian.map((stop, index) => <li key={stop.maDiemDung}><span><b>{stop.thuTu}. {stop.tenDiemDung}</b><small>{stop.diaChi} · dừng {stop.thoiGianDung} phút</small></span><span className={styles.actions}>
          <button disabled={busy} onClick={() => { setEditingStop(stop.maDiemDung); setStopForm({ maDiemDung: stop.maDiemDung, tenDiemDung: stop.tenDiemDung, diaChi: stop.diaChi, thoiGianDung: String(stop.thoiGianDung), loaiDiemDung: stop.loaiDiemDung }); }}>Sửa</button>
          <button disabled={busy || index === 0} onClick={() => void mutate(`/tuyen-xe/${encodeURIComponent(currentRoute.maTuyen)}/diem-dung/sap-xep`, 'PUT', { maDiemDungTheoThuTu: currentRoute.tramTrungGian.map((_, i, all) => all[i === index - 1 ? index : i === index ? index - 1 : i].maDiemDung) }, 'Đã đổi thứ tự điểm dừng.')}>↑</button>
          <button disabled={busy || index === currentRoute.tramTrungGian.length - 1} onClick={() => void mutate(`/tuyen-xe/${encodeURIComponent(currentRoute.maTuyen)}/diem-dung/sap-xep`, 'PUT', { maDiemDungTheoThuTu: currentRoute.tramTrungGian.map((_, i, all) => all[i === index + 1 ? index : i === index ? index + 1 : i].maDiemDung) }, 'Đã đổi thứ tự điểm dừng.')}>↓</button>
          <button disabled={busy} onClick={() => { if (window.confirm(`Xóa điểm dừng ${stop.tenDiemDung}?`)) void mutate(`/tuyen-xe/${encodeURIComponent(currentRoute.maTuyen)}/diem-dung/${encodeURIComponent(stop.maDiemDung)}`, 'DELETE', undefined, 'Đã xóa điểm dừng.'); }}>Xóa</button>
        </span></li>)}{currentRoute.tramTrungGian.length === 0 && <li>Tuyến chưa có điểm dừng trung gian.</li>}</ol>
      </section>}
    </>}
  </div>;
}
