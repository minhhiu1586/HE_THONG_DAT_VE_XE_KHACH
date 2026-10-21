'use client';

import styles from '@members/LeNguyenThanhHien/routes/components/RouteManagement.module.css';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage } from '@members/NguyenMinhHieu/account/lib/account-api';
import { useManagementFocus } from '@shared/lib/use-management-focus';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import {
  cityList,
  cityToForm,
  emptyCity,
  emptyRoute,
  emptyStation,
  emptyStop,
  routeList,
  routeToForm,
  stationList,
  stationToForm,
  stopToForm,
} from '../lib/route-types';
import type { City, Route, Station, Stop, Tab } from '../lib/route-types';
import CityPanel from './CityPanel';
import RouteForm from './RouteForm';
import RouteList from './RouteList';
import RouteStopsPanel from './RouteStopsPanel';
import StationPanel from './StationPanel';

export default function RouteManagement() {
  const { user, loading, request } = useAuth();
  const router = useRouter();
  const [tab, setTab] = useState<Tab>('routes');
  const [cities, setCities] = useState<City[]>([]);
  const [stations, setStations] = useState<Station[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  // Kết quả lọc không được thu hẹp danh mục dùng cho các form và tab khác.
  const [cityResults, setCityResults] = useState<City[]>([]);
  const [stationResults, setStationResults] = useState<Station[]>([]);
  const [citySearch, setCitySearch] = useState('');
  const [stationSearch, setStationSearch] = useState('');
  const [stationCity, setStationCity] = useState('');
  const [stationStatus, setStationStatus] = useState('');
  const [filterStart, setFilterStart] = useState('');
  const [filterEnd, setFilterEnd] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterOperator, setFilterOperator] = useState('');
  const [cityForm, setCityForm] = useState(emptyCity);
  const [stationForm, setStationForm] = useState(emptyStation);
  const [routeForm, setRouteForm] = useState(emptyRoute);
  const [editingCity, setEditingCity] = useState(false);
  const [editingStation, setEditingStation] = useState(false);
  const [editingRoute, setEditingRoute] = useState(false);
  const [selectedRoute, setSelectedRoute] = useState('');
  const [editingStop, setEditingStop] = useState('');
  const [stopForm, setStopForm] = useState(emptyStop);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [cityTarget, focusCity] = useManagementFocus<HTMLElement>();
  const [stationTarget, focusStation] = useManagementFocus<HTMLElement>();
  const [routeTarget, focusRoute] = useManagementFocus<HTMLElement>();
  const [stopTarget, focusStop] = useManagementFocus<HTMLElement>();
  const noticeVersion = useRef(0);
  const sending = useRef(false);
  function clearNotices() {
    noticeVersion.current++;
    setError('');
    setSuccess('');
  }
  function clearOnInteraction(event: React.SyntheticEvent<HTMLDivElement>) {
    if (event.target instanceof Element && event.target.closest('button, input, select, textarea, a')) clearNotices();
  }
  const isAdmin = user?.loaiTaiKhoan === 'ADMIN';
  const mayManage = isAdmin || user?.loaiTaiKhoan === 'NHA_XE';

  const loadCities = useCallback(
    async () => setCityResults(cityList(await request<City[]>(`/tinh-thanh?q=${encodeURIComponent(citySearch)}`))),
    [request, citySearch],
  );
  const loadStations = useCallback(async () => {
    const query = new URLSearchParams();
    if (stationSearch) query.set('q', stationSearch);
    if (stationCity) query.set('maTinhThanh', stationCity);
    if (stationStatus) query.set('trangThai', stationStatus);
    setStationResults(stationList(await request<Station[]>(`/ben-xe${query.size ? `?${query}` : ''}`)));
  }, [request, stationSearch, stationCity, stationStatus]);
  const loadRoutes = useCallback(async () => {
    const query = new URLSearchParams();
    if (filterStart) query.set('tramKhoiHanh', filterStart);
    if (filterEnd) query.set('tramDen', filterEnd);
    if (filterStatus) query.set('trangThai', filterStatus);
    if (isAdmin && filterOperator) query.set('maNhaXe', filterOperator);
    setRoutes(routeList(await request<Route[]>(`/tuyen-xe${query.size ? `?${query}` : ''}`)));
  }, [request, filterStart, filterEnd, filterStatus, filterOperator, isAdmin]);
  const loadCatalogs = useCallback(async () => {
    const [cityData, stationData] = await Promise.all([request<City[]>('/tinh-thanh'), request<Station[]>('/ben-xe')]);
    setCities(cityList(cityData));
    setStations(stationList(stationData));
  }, [request]);
  const refreshRouteData = useCallback(async () => {
    await Promise.all([loadCities(), loadStations(), loadRoutes(), loadCatalogs()]);
  }, [loadCities, loadStations, loadRoutes, loadCatalogs]);
  useEffect(() => {
    if (!loading && !user) router.replace('/dang-nhap?next=/quan-tri/tuyen-duong');
    if (!mayManage) return;
    let active = true;
    Promise.all([request<City[]>('/tinh-thanh'), request<Station[]>('/ben-xe'), request<Route[]>('/tuyen-xe')])
      .then(([cityData, stationData, routeData]) => {
        if (active) {
          const allCities = cityList(cityData);
          const allStations = stationList(stationData);
          setCities(allCities);
          setCityResults(allCities);
          setStations(allStations);
          setStationResults(allStations);
          setRoutes(routeList(routeData));
        }
      })
      .catch(e => {
        if (active) setError(errorMessage(e));
      });
    return () => {
      active = false;
    };
  }, [loading, user, mayManage, router, request]);
  const currentRoute = useMemo(() => routes.find(route => route.maTuyen === selectedRoute) ?? routes[0], [routes, selectedRoute]);

  function handleEditCity(city: City) {
    setCityForm(cityToForm(city));
    setEditingCity(true);
    focusCity('input:not(:disabled):not([readonly])');
  }
  function handleEditStation(station: Station) {
    setStationForm(stationToForm(station));
    setEditingStation(true);
    focusStation('input:not(:disabled):not([readonly]),select:not(:disabled)');
  }
  function handleChooseRoute(routeCode: string) {
    setSelectedRoute(routeCode);
    setEditingStop('');
    focusStop();
  }
  function handleEditStop(stop: Stop) {
    setEditingStop(stop.maDiemDung);
    setStopForm(stopToForm(stop));
    focusStop('input:not(:disabled):not([readonly]),select:not(:disabled)');
  }

  async function runRouteAction(operation: () => Promise<unknown>, message = '') {
    if (sending.current) return;
    sending.current = true;
    clearNotices();
    const version = noticeVersion.current;
    setBusy(true);
    try {
      await operation();
      if (version === noticeVersion.current) setSuccess(message);
    } catch (error) {
      if (version === noticeVersion.current) setError(errorMessage(error));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  async function runFormSubmit(event: FormEvent<HTMLFormElement>, save: () => Promise<unknown>, message: string) {
    event.preventDefault();
    await runRouteAction(async () => {
      await save();
      await refreshRouteData();
    }, message);
  }
  function handleSaveCity(event: FormEvent<HTMLFormElement>) {
    const body = { tenTinhThanh: cityForm.tenTinhThanh, khuVuc: cityForm.khuVuc || null };
    void runFormSubmit(
      event,
      async () => {
        if (editingCity) await request(`/tinh-thanh/${encodeURIComponent(cityForm.maTinhThanh)}`, 'PUT', body);
        else await request('/tinh-thanh', 'POST', { ...cityForm, khuVuc: cityForm.khuVuc || null });
        setCityForm(emptyCity);
        setEditingCity(false);
      },
      editingCity ? 'Đã cập nhật tỉnh/thành phố.' : 'Đã thêm tỉnh/thành phố.',
    );
  }
  function handleSaveStation(event: FormEvent<HTMLFormElement>) {
    const body = { ...stationForm, soDienThoai: stationForm.soDienThoai || null };
    void runFormSubmit(
      event,
      async () => {
        if (editingStation) await request(`/ben-xe/${encodeURIComponent(stationForm.maBenXe)}`, 'PUT', body);
        else await request('/ben-xe', 'POST', body);
        setStationForm(emptyStation);
        setEditingStation(false);
      },
      editingStation ? 'Đã cập nhật bến xe.' : 'Đã thêm bến xe.',
    );
  }
  function handleSaveRoute(event: FormEvent<HTMLFormElement>) {
    const body = {
      ...routeForm,
      thoiGianKhoiHanh: routeForm.thoiGianKhoiHanh || null,
      khoangCachKM: routeForm.khoangCachKM ? Number(routeForm.khoangCachKM) : null,
      thoiGianDuKien: Number(routeForm.thoiGianDuKien),
      giaCoBan: Number(routeForm.giaCoBan),
      moTa: routeForm.moTa || null,
    };
    void runFormSubmit(
      event,
      async () => {
        if (editingRoute) await request(`/tuyen-xe/${encodeURIComponent(routeForm.maTuyen)}`, 'PUT', body);
        else await request('/tuyen-xe', 'POST', body);
        setSelectedRoute(routeForm.maTuyen);
        setRouteForm(emptyRoute);
        setEditingRoute(false);
      },
      editingRoute ? 'Đã cập nhật tuyến xe.' : 'Đã tạo tuyến xe.',
    );
  }
  function handleEditRoute(route: Route) {
    setSelectedRoute(route.maTuyen);
    setEditingRoute(true);
    setRouteForm(routeToForm(route));
    focusRoute('input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not(:disabled)');
  }
  async function mutateRouteData(path: string, method: string, body?: unknown, message = 'Đã cập nhật dữ liệu.') {
    await runRouteAction(async () => {
      await request(path, method, body);
      await refreshRouteData();
    }, message);
  }

  function handleSaveStop(event: FormEvent<HTMLFormElement>) {
    if (!currentRoute) {
      event.preventDefault();
      return;
    }
    const body = {
      tenDiemDung: stopForm.tenDiemDung,
      diaChi: stopForm.diaChi,
      thoiGianDung: Number(stopForm.thoiGianDung),
      loaiDiemDung: stopForm.loaiDiemDung,
    };
    void runFormSubmit(
      event,
      async () => {
        const path = `/tuyen-xe/${encodeURIComponent(currentRoute.maTuyen)}/diem-dung`;
        if (editingStop) await request(`${path}/${encodeURIComponent(editingStop)}`, 'PUT', body);
        else await request(path, 'POST', { ...body, maDiemDung: stopForm.maDiemDung, thuTu: currentRoute.tramTrungGian.length + 1 });
        setStopForm(emptyStop);
        setEditingStop('');
      },
      editingStop ? 'Đã cập nhật điểm dừng.' : 'Đã thêm điểm dừng.',
    );
  }
  function handleDeleteCity(city: City) {
    if (window.confirm(`Xóa ${city.tenTinhThanh}?`))
      void mutateRouteData(`/tinh-thanh/${encodeURIComponent(city.maTinhThanh)}`, 'DELETE', undefined, 'Đã xóa tỉnh/thành phố.');
  }
  function handleDeleteStation(station: Station) {
    if (window.confirm(`Xóa ${station.tenBenXe}?`))
      void mutateRouteData(`/ben-xe/${encodeURIComponent(station.maBenXe)}`, 'DELETE', undefined, 'Đã xóa bến xe.');
  }
  function handleDeleteRoute(route: Route) {
    if (window.confirm(`Xóa tuyến ${route.maTuyen}?`))
      void mutateRouteData(`/tuyen-xe/${encodeURIComponent(route.maTuyen)}`, 'DELETE', undefined, 'Đã xóa tuyến.');
  }
  function handleDeleteStop(stop: Stop) {
    if (!currentRoute || !window.confirm(`Xóa điểm dừng ${stop.tenDiemDung}?`)) return;
    void mutateRouteData(
      `/tuyen-xe/${encodeURIComponent(currentRoute.maTuyen)}/diem-dung/${encodeURIComponent(stop.maDiemDung)}`,
      'DELETE',
      undefined,
      'Đã xóa điểm dừng.',
    );
  }
  function handleMoveStop(index: number, direction: -1 | 1) {
    if (!currentRoute) return;
    const target = index + direction;
    const stops = currentRoute.tramTrungGian;
    if (target < 0 || target >= stops.length) return;
    const ids = stops.map(stop => stop.maDiemDung);
    [ids[index], ids[target]] = [ids[target], ids[index]];
    void mutateRouteData(
      `/tuyen-xe/${encodeURIComponent(currentRoute.maTuyen)}/diem-dung/sap-xep`,
      'PUT',
      { maDiemDungTheoThuTu: ids },
      'Đã đổi thứ tự điểm dừng.',
    );
  }
  function handleSaveRouteStatus() {
    void mutateRouteData(
      `/tuyen-xe/${encodeURIComponent(routeForm.maTuyen)}/trang-thai`,
      'PUT',
      { trangThai: routeForm.trangThai },
      'Đã cập nhật trạng thái tuyến.',
    );
  }

  if (loading || !user) return <div className={styles.page}>Đang kiểm tra phiên đăng nhập…</div>;
  if (!mayManage)
    return (
      <div className={styles.page}>
        <p role="alert" className={styles.error}>
          Bạn không có quyền quản lý tuyến xe.
        </p>
      </div>
    );

  return (
    <div
      className={styles.page}
      onClickCapture={clearOnInteraction}
      onFocusCapture={clearOnInteraction}
      onInputCapture={clearOnInteraction}
      onChangeCapture={clearOnInteraction}
    >
      <header className={styles.heading}>
        <div>
          <p className={styles.eyebrow}>QUẢN TRỊ VẬN HÀNH</p>
          <h1>Quản lý tuyến đường</h1>
          <p>Quản lý tỉnh thành, bến xe, tuyến và các điểm dừng.</p>
        </div>
      </header>
      <nav className={styles.tabs} aria-label="Nhóm dữ liệu">
        <button aria-pressed={tab === 'routes'} onClick={() => setTab('routes')}>
          Tuyến xe
        </button>
        {isAdmin && (
          <>
            <button aria-pressed={tab === 'stations'} onClick={() => setTab('stations')}>
              Bến xe
            </button>
            <button aria-pressed={tab === 'cities'} onClick={() => setTab('cities')}>
              Tỉnh/thành
            </button>
          </>
        )}
      </nav>
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
      {success && (
        <p className={styles.success} role="status">
          {success}
        </p>
      )}

      {tab === 'cities' && isAdmin && (
        <CityPanel
          cityTarget={cityTarget}
          citySearch={citySearch}
          setCitySearch={setCitySearch}
          loadCities={() => runRouteAction(loadCities)}
          cityForm={cityForm}
          setCityForm={setCityForm}
          editingCity={editingCity}
          handleSaveCity={handleSaveCity}
          busy={busy}
          cityResults={cityResults}
          handleEditCity={handleEditCity}
          handleDeleteCity={handleDeleteCity}
          handleCancelEdit={() => {
            setCityForm(emptyCity);
            setEditingCity(false);
          }}
        />
      )}

      {tab === 'stations' && isAdmin && (
        <StationPanel
          stationTarget={stationTarget}
          filters={{ search: stationSearch, city: stationCity, status: stationStatus }}
          onFilterChange={(key, value) => {
            const setters = { search: setStationSearch, city: setStationCity, status: setStationStatus };
            setters[key](value);
          }}
          loadStations={() => runRouteAction(loadStations)}
          cities={cities}
          stationForm={stationForm}
          setStationForm={setStationForm}
          editingStation={editingStation}
          handleSaveStation={handleSaveStation}
          busy={busy}
          stationResults={stationResults}
          handleEditStation={handleEditStation}
          handleDeleteStation={handleDeleteStation}
          handleCancelEdit={() => {
            setStationForm(emptyStation);
            setEditingStation(false);
          }}
        />
      )}

      {tab === 'routes' && (
        <>
          <RouteForm
            routeTarget={routeTarget}
            editingRoute={editingRoute}
            routeForm={routeForm}
            setRouteForm={setRouteForm}
            stations={stations}
            cities={cities}
            busy={busy}
            handleSaveRoute={handleSaveRoute}
            handleSaveRouteStatus={handleSaveRouteStatus}
            handleNewRoute={() => {
              setRouteForm(emptyRoute);
              setEditingRoute(false);
              focusRoute('input:not(:disabled):not([readonly]),select:not(:disabled)');
            }}
          />
          <RouteList
            filters={{ start: filterStart, end: filterEnd, status: filterStatus, operator: filterOperator }}
            onFilterChange={(key, value) => {
              const setters = { start: setFilterStart, end: setFilterEnd, status: setFilterStatus, operator: setFilterOperator };
              setters[key](value);
            }}
            stations={stations}
            isAdmin={isAdmin}
            loadRoutes={() => runRouteAction(loadRoutes)}
            routes={routes}
            busy={busy}
            handleEditRoute={handleEditRoute}
            handleChooseRoute={handleChooseRoute}
            handleDeleteRoute={handleDeleteRoute}
          />
          {currentRoute && (
            <RouteStopsPanel
              stopTarget={stopTarget}
              currentRoute={currentRoute}
              stopForm={stopForm}
              setStopForm={setStopForm}
              editingStop={editingStop}
              handleSaveStop={handleSaveStop}
              busy={busy}
              handleEditStop={handleEditStop}
              handleMoveStop={handleMoveStop}
              handleDeleteStop={handleDeleteStop}
              handleCancelEdit={() => {
                setEditingStop('');
                setStopForm(emptyStop);
              }}
            />
          )}
        </>
      )}
    </div>
  );
}
