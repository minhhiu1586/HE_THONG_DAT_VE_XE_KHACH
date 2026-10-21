'use client';

import styles from '@members/LeNguyenThanhHien/trips/components/TripManagement.module.css';
import TripPassengerModal from '@members/LeNguyenThanhHien/trips/components/TripPassengerModal';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage } from '@members/NguyenMinhHieu/account/lib/account-api';
import { sortedUnique } from '@shared/lib/management-options';
import { useManagementFocus } from '@shared/lib/use-management-focus';
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { emptyTripForm, estimateArrival, TRIP_API, tripToForm } from '../lib/trip-types';
import type { Operator, Route, SaleReport, Trip, TripLifecycle, TripPage, Vehicle } from '../lib/trip-types';
import TripDetails from './TripDetails';
import TripForm from './TripForm';
import TripList from './TripList';

export default function TripManagement() {
  const { request } = useAuth();
  const [routes, setRoutes] = useState<Route[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [operators, setOperators] = useState<Operator[]>([]);
  const [page, setPage] = useState<TripPage>({ items: [], page: 1, totalPages: 0, totalItems: 0 });
  const [form, setForm] = useState(emptyTripForm);
  const [editing, setEditing] = useState(false);
  const [filters, setFilters] = useState({ maTuyen: '', maXe: '', trangThai: '', tuNgay: '', denNgay: '' });
  const [selected, setSelected] = useState<Trip | null>(null);
  const [report, setReport] = useState<SaleReport | null>(null);
  const [lifecycle, setLifecycle] = useState<TripLifecycle | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [viewingPassengersTrip, setViewingPassengersTrip] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [initialLoading, setInitialLoading] = useState(true);
  const waiting = busy || initialLoading;
  const sending = useRef(false);
  const revision = useRef(0);
  const [formTarget, focusForm] = useManagementFocus<HTMLElement>();
  const [detailTarget, focusDetail] = useManagementFocus<HTMLElement>();
  function clearNotices() {
    revision.current++;
    setError('');
    setSuccess('');
  }
  function handleInteraction(event: React.SyntheticEvent<HTMLDivElement>) {
    if (event.target instanceof Element && event.target.closest('button,input,select,a')) clearNotices();
  }
  async function loadTrips(number = 1) {
    const params = new URLSearchParams({ page: String(number), size: '20' });
    Object.entries(filters).forEach(([key, value]) => {
      if (value) params.set(key, value);
    });
    setPage(await request<TripPage>(`${TRIP_API}?${params}`));
  }
  async function runTripAction(operation: () => Promise<void>, message = '') {
    if (sending.current || initialLoading) return;
    clearNotices();
    const version = revision.current;
    sending.current = true;
    setBusy(true);
    try {
      await operation();
      if (version === revision.current) setSuccess(message);
    } catch (failure) {
      if (version === revision.current) setError(errorMessage(failure));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  useEffect(() => {
    let active = true;
    Promise.all([
      request<Route[]>('/tuyen-xe'),
      request<Vehicle[]>('/xe'),
      request<TripPage>(`${TRIP_API}?page=1&size=20`),
      request<Operator[]>('/xe/pham-vi'),
    ])
      .then(([routeData, vehicleData, tripPage, operatorData]) => {
        if (!active) return;
        setRoutes(
          sortedUnique(
            routeData,
            item => item.maTuyen,
            item => item.tenTuyen,
          ),
        );
        setVehicles(
          sortedUnique(
            vehicleData,
            item => item.maXe,
            item => item.bienSo,
          ),
        );
        setPage(tripPage);
        setOperators(
          sortedUnique(
            operatorData,
            item => item.maNhaXe,
            item => item.tenNhaXe,
          ),
        );
      })
      .catch(failure => {
        if (active) setError(errorMessage(failure));
      })
      .finally(() => {
        if (active) setInitialLoading(false);
      });
    return () => {
      active = false;
    };
  }, [request]);
  async function loadTripDetails(code: string, navigate = true) {
    const [trip, checks, history] = await Promise.all([
      request<Trip>(`${TRIP_API}/${encodeURIComponent(code)}`),
      request<SaleReport>(`${TRIP_API}/${encodeURIComponent(code)}/dieu-kien-mo-ban`),
      request<TripLifecycle>(`${TRIP_API}/${encodeURIComponent(code)}/vong-doi`),
    ]);
    setSelected(trip);
    setReport(checks);
    setLifecycle(history);
    setConfirmCancel(false);
    if (navigate) focusDetail();
  }
  const route = routes.find(r => r.maTuyen === form.maTuyen);
  const arrival = form.gioDenDuKien || estimateArrival(form.ngayKhoiHanh, form.gioKhoiHanh, route?.thoiGianDuKien);
  function handleEditTrip(trip: Trip) {
    setEditing(true);
    setForm(tripToForm(trip));
    focusForm('input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not(:disabled)');
  }
  function handleSaveTrip(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runTripAction(
      async () => {
        const saved = await request<Trip>(
          editing ? `${TRIP_API}/${encodeURIComponent(form.maChuyen)}` : TRIP_API,
          editing ? 'PUT' : 'POST',
          {
            ...form,
            maXe: form.maXe || null,
            gioDenDuKien: arrival ? `${arrival}:00` : null,
          },
        );
        setForm(emptyTripForm);
        setEditing(false);
        await loadTrips();
        await loadTripDetails(saved.maChuyen);
      },
      editing ? 'Đã cập nhật chuyến nháp.' : 'Đã tạo chuyến nháp. Chuyến chưa được mở bán.',
    );
  }

  function handleFilterTrips(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runTripAction(() => loadTrips());
  }
  function handleLoadPage(number: number) {
    void runTripAction(() => loadTrips(number));
  }
  function handleViewTrip(code: string) {
    void runTripAction(() => loadTripDetails(code));
  }
  function handleDeleteTrip(trip: Trip) {
    if (!window.confirm(`Xóa chuyến nháp ${trip.maChuyen}?`)) return;
    void runTripAction(async () => {
      await request(`${TRIP_API}/${encodeURIComponent(trip.maChuyen)}`, 'DELETE');
      await loadTrips();
      if (selected?.maChuyen === trip.maChuyen) {
        setSelected(null);
        setReport(null);
      }
    }, 'Đã xóa chuyến nháp.');
  }
  function handleRefreshDetails() {
    if (selected) void runTripAction(() => loadTripDetails(selected.maChuyen, false));
  }
  function handleOpenTripSale() {
    if (!selected || !window.confirm(`Xác nhận mở bán chuyến ${selected.maChuyen}?`)) return;
    void runTripAction(async () => {
      try {
        await request(`${TRIP_API}/${encodeURIComponent(selected.maChuyen)}/mo-ban`, 'POST');
      } catch (failure) {
        // Cập nhật điều kiện vì xe hoặc nhân sự có thể đã đổi sau lần tải trước.
        await loadTripDetails(selected.maChuyen);
        throw failure;
      }
      await loadTripDetails(selected.maChuyen, false);
      await loadTrips(page.page);
    }, 'Đã mở bán chuyến.');
  }
  function handleStartTrip() {
    if (!selected) return;
    void runTripAction(async () => {
      await request(`${TRIP_API}/${encodeURIComponent(selected.maChuyen)}/bat-dau`, 'POST', {});
      await loadTripDetails(selected.maChuyen, false);
      await loadTrips(page.page);
    }, 'Đã bắt đầu chuyến.');
  }
  function handleCompleteTrip() {
    if (!selected) return;
    void runTripAction(async () => {
      await request(`${TRIP_API}/${encodeURIComponent(selected.maChuyen)}/hoan-thanh`, 'POST', {});
      await loadTripDetails(selected.maChuyen, false);
      await loadTrips(page.page);
    }, 'Đã hoàn tất chuyến.');
  }
  function handleCancelTrip(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    void runTripAction(async () => {
      await request(`${TRIP_API}/${encodeURIComponent(selected.maChuyen)}/huy`, 'POST', { lyDo: cancelReason });
      setConfirmCancel(false);
      await loadTripDetails(selected.maChuyen, false);
      await loadTrips(page.page);
    }, 'Chuyến đã hủy. Các khoản hoàn và đối soát được theo dõi riêng bên dưới.');
  }
  function handleRetryCancellation() {
    if (!selected) return;
    void runTripAction(async () => {
      await request(`${TRIP_API}/${encodeURIComponent(selected.maChuyen)}/xu-ly-hoan`, 'POST', {});
      await loadTripDetails(selected.maChuyen, false);
    }, 'Đã cập nhật trạng thái xử lý hoàn/đối soát.');
  }

  return (
    <div
      className={styles.page}
      onClickCapture={handleInteraction}
      onFocusCapture={handleInteraction}
      onInputCapture={handleInteraction}
      onChangeCapture={handleInteraction}
    >
      <header className={styles.heading}>
        <p className={styles.eyebrow}>QUẢN TRỊ VẬN HÀNH</p>
        <h1>Quản lý chuyến xe và mở bán</h1>
        <p>Tạo lịch chạy, hoàn tất phân công và kiểm tra điều kiện trước khi mở bán.</p>
      </header>
      {waiting && <p role="status">{initialLoading ? 'Đang tải dữ liệu…' : 'Đang xử lý…'}</p>}
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
      {success && (
        <p role="status" className={styles.success}>
          {success}
        </p>
      )}
      <section ref={formTarget} className={styles.panel}>
        <h2 data-management-focus tabIndex={-1}>
          {editing ? 'Sửa chuyến nháp' : 'Tạo chuyến nháp'}
        </h2>
        <TripForm
          form={form}
          setForm={setForm}
          editing={editing}
          waiting={waiting}
          routes={routes}
          vehicles={vehicles}
          operators={operators}
          arrival={arrival}
          handleSaveTrip={handleSaveTrip}
          handleCancelEdit={() => {
            setEditing(false);
            setForm(emptyTripForm);
          }}
        />
      </section>
      <TripList
        filters={filters}
        setFilters={setFilters}
        routes={routes}
        vehicles={vehicles}
        waiting={waiting}
        page={page}
        handleFilterTrips={handleFilterTrips}
        handleViewTrip={handleViewTrip}
        setViewingPassengersTrip={setViewingPassengersTrip}
        handleEditTrip={handleEditTrip}
        handleDeleteTrip={handleDeleteTrip}
        handleLoadPage={handleLoadPage}
      />
      {selected && (
        <TripDetails
          selected={selected}
          report={report}
          lifecycle={lifecycle}
          waiting={waiting}
          detailTarget={detailTarget}
          setViewingPassengersTrip={setViewingPassengersTrip}
          handleRefreshDetails={handleRefreshDetails}
          handleOpenTripSale={handleOpenTripSale}
          handleStartTrip={handleStartTrip}
          handleCompleteTrip={handleCompleteTrip}
          confirmCancel={confirmCancel}
          setConfirmCancel={setConfirmCancel}
          cancelReason={cancelReason}
          setCancelReason={setCancelReason}
          handleCancelTrip={handleCancelTrip}
          handleRetryCancellation={handleRetryCancellation}
        />
      )}
      {viewingPassengersTrip && <TripPassengerModal maChuyen={viewingPassengersTrip} onClose={() => setViewingPassengersTrip(null)} />}
    </div>
  );
}
