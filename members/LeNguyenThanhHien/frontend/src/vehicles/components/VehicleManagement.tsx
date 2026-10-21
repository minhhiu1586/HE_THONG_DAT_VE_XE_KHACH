'use client';

import styles from '@members/LeNguyenThanhHien/vehicles/components/VehicleManagement.module.css';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage } from '@members/NguyenMinhHieu/account/lib/account-api';
import { useManagementFocus } from '@shared/lib/use-management-focus';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { emptyVehicleForm, emptyVehicleTypeForm, vehicleToForm, vehicleTypeToForm } from '../lib/vehicle-types';
import type { Operator, Seat, Vehicle, VehicleType } from '../lib/vehicle-types';
import VehicleForm from './VehicleForm';
import VehicleList from './VehicleList';
import VehicleSeatLayout from './VehicleSeatLayout';
import VehicleTypePanel from './VehicleTypePanel';

export default function VehicleManagement() {
  const { user, loading, request } = useAuth();
  const router = useRouter();
  const isAdmin = user?.loaiTaiKhoan === 'ADMIN';
  const allowed = isAdmin || user?.loaiTaiKhoan === 'NHA_XE';
  const [tab, setTab] = useState<'vehicles' | 'types'>('vehicles');
  const [types, setTypes] = useState<VehicleType[]>([]);
  const [operators, setOperators] = useState<Operator[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [vehiclePage, setVehiclePage] = useState(1);
  const [typeResults, setTypeResults] = useState<VehicleType[]>([]);
  const [typeForm, setTypeForm] = useState(emptyVehicleTypeForm);
  const [editingType, setEditingType] = useState(false);
  const [vehicleForm, setVehicleForm] = useState(emptyVehicleForm);
  const [editingVehicle, setEditingVehicle] = useState(false);
  const [selected, setSelected] = useState<Vehicle | null>(null);
  const [seats, setSeats] = useState<Seat[]>([]);
  const [query, setQuery] = useState('');
  const [typeQuery, setTypeQuery] = useState('');
  const [filterType, setFilterType] = useState('');
  const [filterOwner, setFilterOwner] = useState('');
  const [filterState, setFilterState] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const sending = useRef(false);
  const revision = useRef(0);
  const [typeFormTarget, focusTypeForm] = useManagementFocus<HTMLElement>();
  const [vehicleFormTarget, focusVehicleForm] = useManagementFocus<HTMLElement>();
  const [detailTarget, focusDetail] = useManagementFocus<HTMLElement>();
  function clearNotices() {
    revision.current++;
    setError('');
    setSuccess('');
  }
  function handleInteraction(event: React.SyntheticEvent<HTMLDivElement>) {
    if (event.target instanceof Element && event.target.closest('button,input,select,textarea,a')) clearNotices();
  }
  async function loadVehicles() {
    const params = new URLSearchParams({ q: query, maLoaiXe: filterType, maNhaXe: filterOwner, trangThai: filterState });
    const data = await request<Vehicle[]>(`/xe?${params}`);
    setVehicles(data);
    setVehiclePage(1);
    return data;
  }
  async function runVehicleAction(operation: () => Promise<void>, message: string) {
    if (sending.current) return;
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
    if (!loading && !user) router.replace('/dang-nhap?next=/quan-tri/xe');
    if (!allowed) return;
    let active = true;
    Promise.all([request<VehicleType[]>('/loai-xe'), request<Vehicle[]>('/xe'), request<Operator[]>('/xe/pham-vi')])
      .then(([kinds, cars, owners]) => {
        if (active) {
          setTypes(kinds);
          setTypeResults(kinds);
          setVehicles(cars);
          setOperators(owners);
        }
      })
      .catch(failure => {
        if (active) setError(errorMessage(failure));
      });
    return () => {
      active = false;
    };
  }, [allowed, loading, user, router, request]);
  async function loadVehicleDetails(vehicle: Vehicle, navigate = true) {
    let loaded = false;
    await runVehicleAction(async () => {
      const [current, map] = await Promise.all([
        request<Vehicle>(`/xe/${encodeURIComponent(vehicle.maXe)}`),
        request<Seat[]>(`/xe/${encodeURIComponent(vehicle.maXe)}/ghe`),
      ]);
      setSelected(current);
      setSeats(map);
      loaded = true;
    }, '');
    if (navigate && loaded) focusDetail();
  }
  function handleEditVehicle(vehicle: Vehicle) {
    setEditingVehicle(true);
    setVehicleForm(vehicleToForm(vehicle));
    focusVehicleForm('input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not(:disabled)');
  }
  function handleEditVehicleType(type: VehicleType) {
    setEditingType(true);
    setTypeForm(vehicleTypeToForm(type));
    focusTypeForm('input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not(:disabled)');
  }
  function handleSaveVehicleType(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runVehicleAction(
      async () => {
        await request(`/loai-xe${editingType ? `/${encodeURIComponent(typeForm.maLoaiXe)}` : ''}`, editingType ? 'PUT' : 'POST', {
          ...typeForm,
          soChoNgoi: Number(typeForm.soChoNgoi),
          soTang: Number(typeForm.soTang),
          moTa: typeForm.moTa || null,
        });
        const kinds = await request<VehicleType[]>('/loai-xe');
        setTypes(kinds);
        setTypeResults(kinds);
        setTypeQuery('');
        setTypeForm(emptyVehicleTypeForm);
        setEditingType(false);
      },
      editingType ? 'Đã cập nhật loại xe.' : 'Đã thêm loại xe.',
    );
  }
  function handleSaveVehicle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runVehicleAction(
      async () => {
        const current = await request<Vehicle>(
          `/xe${editingVehicle ? `/${encodeURIComponent(vehicleForm.maXe)}` : ''}`,
          editingVehicle ? 'PUT' : 'POST',
          {
            ...vehicleForm,
            maNhaXe: vehicleForm.maNhaXe || operators[0]?.maNhaXe,
            namSanXuat: vehicleForm.namSanXuat ? Number(vehicleForm.namSanXuat) : null,
            dungTichXang: vehicleForm.dungTichXang ? Number(vehicleForm.dungTichXang) : null,
            tocDo: vehicleForm.tocDo ? Number(vehicleForm.tocDo) : null,
          },
        );
        await loadVehicles();
        setSelected(current);
        setSeats(await request<Seat[]>(`/xe/${encodeURIComponent(current.maXe)}/ghe`));
        setEditingVehicle(false);
        setVehicleForm(emptyVehicleForm);
        focusDetail();
      },
      editingVehicle ? 'Đã cập nhật xe.' : 'Đã thêm xe và tạo sơ đồ ghế.',
    );
  }
  function handleUpdateSeat(index: number, changes: Partial<Seat>) {
    setSeats(current => current.map((seat, i) => (i === index ? { ...seat, ...changes } : seat)));
  }

  function handleSearchTypes() {
    void runVehicleAction(async () => {
      setTypeResults(await request<VehicleType[]>(`/loai-xe?q=${encodeURIComponent(typeQuery)}`));
    }, '');
  }
  function handleDeleteType(type: VehicleType) {
    if (!window.confirm(`Xóa loại xe ${type.tenLoaiXe}?`)) return;
    void runVehicleAction(async () => {
      await request(`/loai-xe/${encodeURIComponent(type.maLoaiXe)}`, 'DELETE');
      const kinds = await request<VehicleType[]>('/loai-xe');
      setTypes(kinds);
      setTypeResults(kinds);
      setTypeQuery('');
    }, 'Đã xóa loại xe.');
  }
  function handleDeleteVehicle(vehicle: Vehicle) {
    if (!window.confirm(`Xóa xe ${vehicle.bienSo}?`)) return;
    void runVehicleAction(async () => {
      await request(`/xe/${encodeURIComponent(vehicle.maXe)}`, 'DELETE');
      await loadVehicles();
      if (selected?.maXe === vehicle.maXe) {
        setSelected(null);
        setSeats([]);
      }
    }, 'Đã xóa xe.');
  }
  function handleFilterVehicles() {
    void runVehicleAction(async () => {
      await loadVehicles();
    }, '');
  }
  function handleSaveSeatLayout(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    void runVehicleAction(async () => {
      const danhSachGhe = seats.map(({ maGhe, viTri, tang, trangThai }) => ({ maGhe, viTri, tang, trangThai }));
      setSeats(await request<Seat[]>(`/xe/${encodeURIComponent(selected.maXe)}/so-do-ghe`, 'PUT', { danhSachGhe }));
      setSelected(await request<Vehicle>(`/xe/${encodeURIComponent(selected.maXe)}`));
      await loadVehicles();
    }, 'Đã lưu sơ đồ ghế.');
  }

  if (loading || !user) return <div className={styles.page}>Đang kiểm tra phiên đăng nhập…</div>;
  if (!allowed)
    return (
      <div className={styles.page}>
        <p role="alert">Bạn không có quyền quản lý xe.</p>
      </div>
    );
  return (
    <div
      className={styles.page}
      onClickCapture={handleInteraction}
      onFocusCapture={handleInteraction}
      onInputCapture={handleInteraction}
      onChangeCapture={handleInteraction}
    >
      <h1>Quản lý xe và sơ đồ ghế</h1>
      <p>Quản lý phương tiện, đăng kiểm và trạng thái ghế vật lý.</p>
      <nav className={styles.tabs}>
        <button aria-pressed={tab === 'vehicles'} onClick={() => setTab('vehicles')}>
          Xe và ghế
        </button>
        {isAdmin && (
          <button aria-pressed={tab === 'types'} onClick={() => setTab('types')}>
            Loại xe
          </button>
        )}
      </nav>
      {busy && <p role="status">Đang xử lý…</p>}
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
      {tab === 'types' && isAdmin && (
        <VehicleTypePanel
          typeFormTarget={typeFormTarget}
          editingType={editingType}
          typeForm={typeForm}
          setTypeForm={setTypeForm}
          busy={busy}
          handleSaveVehicleType={handleSaveVehicleType}
          handleCancelEdit={() => {
            setEditingType(false);
            setTypeForm(emptyVehicleTypeForm);
          }}
          typeQuery={typeQuery}
          setTypeQuery={setTypeQuery}
          handleSearchTypes={handleSearchTypes}
          typeResults={typeResults}
          handleEditVehicleType={handleEditVehicleType}
          handleDeleteType={handleDeleteType}
        />
      )}
      {tab === 'vehicles' && (
        <>
          <VehicleForm
            vehicleFormTarget={vehicleFormTarget}
            editingVehicle={editingVehicle}
            vehicleForm={vehicleForm}
            setVehicleForm={setVehicleForm}
            types={types}
            operators={operators}
            isAdmin={isAdmin}
            busy={busy}
            handleSaveVehicle={handleSaveVehicle}
            handleCancelEdit={() => {
              setEditingVehicle(false);
              setVehicleForm(emptyVehicleForm);
            }}
          />
          <VehicleList
            filters={{ query, filterType, filterOwner, filterState }}
            onFilterChange={(name, value) => {
              const setters = { query: setQuery, filterType: setFilterType, filterOwner: setFilterOwner, filterState: setFilterState };
              setters[name](value);
            }}
            types={types}
            operators={operators}
            isAdmin={isAdmin}
            busy={busy}
            vehicles={vehicles}
            vehiclePage={vehiclePage}
            setVehiclePage={setVehiclePage}
            handleFilterVehicles={handleFilterVehicles}
            handleEditVehicle={handleEditVehicle}
            loadVehicleDetails={loadVehicleDetails}
            handleDeleteVehicle={handleDeleteVehicle}
          />
          {selected && (
            <VehicleSeatLayout
              selected={selected}
              types={types}
              operators={operators}
              seats={seats}
              setSeats={setSeats}
              busy={busy}
              detailTarget={detailTarget}
              handleUpdateSeat={handleUpdateSeat}
              handleSaveSeatLayout={handleSaveSeatLayout}
              loadVehicleDetails={loadVehicleDetails}
            />
          )}
        </>
      )}
    </div>
  );
}
