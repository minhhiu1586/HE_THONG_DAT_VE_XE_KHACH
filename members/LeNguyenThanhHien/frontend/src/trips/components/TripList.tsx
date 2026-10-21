import { routeFilterChoices } from '@shared/lib/management-options';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { formatTripDateTime, getTripStatusLabel, tripStatusLabels } from '../lib/trip-types';
import type { Route, Trip, TripFilters, TripPage, Vehicle } from '../lib/trip-types';
import styles from './TripManagement.module.css';

type Props = {
  filters: TripFilters;
  setFilters: Dispatch<SetStateAction<TripFilters>>;
  routes: Route[];
  vehicles: Vehicle[];
  waiting: boolean;
  page: TripPage;
  handleFilterTrips: (event: FormEvent<HTMLFormElement>) => void;
  handleViewTrip: (code: string) => void;
  setViewingPassengersTrip: (code: string) => void;
  handleEditTrip: (trip: Trip) => void;
  handleDeleteTrip: (trip: Trip) => void;
  handleLoadPage: (number: number) => void;
};

export default function TripList({
  filters,
  setFilters,
  routes,
  vehicles,
  waiting,
  page,
  handleFilterTrips,
  handleViewTrip,
  setViewingPassengersTrip,
  handleEditTrip,
  handleDeleteTrip,
  handleLoadPage,
}: Props) {
  const filterChoices = routeFilterChoices(routes);
  return (
    <section className={styles.panel}>
      <h2>Danh sách chuyến</h2>
      <form className={styles.filters} onSubmit={handleFilterTrips}>
        <label>
          Lọc tuyến
          <select aria-label="Lọc tuyến" value={filters.maTuyen} onChange={e => setFilters({ ...filters, maTuyen: e.target.value })}>
            <option value="">Tất cả tuyến</option>
            {filterChoices.map(choice => (
              <option key={choice.value} value={choice.value}>
                {choice.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Lọc xe
          <select aria-label="Lọc xe" value={filters.maXe} onChange={e => setFilters({ ...filters, maXe: e.target.value })}>
            <option value="">Tất cả xe</option>
            {vehicles.map(v => (
              <option key={v.maXe} value={v.maXe}>
                {v.bienSo}
              </option>
            ))}
          </select>
        </label>
        <label>
          Lọc trạng thái
          <select
            aria-label="Lọc trạng thái"
            value={filters.trangThai}
            onChange={e => setFilters({ ...filters, trangThai: e.target.value })}
          >
            <option value="">Tất cả trạng thái</option>
            {Object.entries(tripStatusLabels).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Từ ngày
          <input type="date" value={filters.tuNgay} onChange={e => setFilters({ ...filters, tuNgay: e.target.value })} />
        </label>
        <label>
          Đến ngày
          <input type="date" value={filters.denNgay} onChange={e => setFilters({ ...filters, denNgay: e.target.value })} />
        </label>
        <button disabled={waiting}>Lọc chuyến</button>
      </form>
      <div className={styles.scroll}>
        <table>
          <thead>
            <tr>
              <th>Mã chuyến / tuyến</th>
              <th>Khởi hành → đến</th>
              <th>Xe</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {page.items.map(trip => (
              <tr key={trip.maChuyen}>
                <td>
                  {trip.maChuyen}
                  <small>{trip.tenTuyen}</small>
                  <small>{trip.tenNhaXe}</small>
                </td>
                <td>
                  {trip.ngayKhoiHanh} {trip.gioKhoiHanh.slice(0, 5)}
                  <small>→ {formatTripDateTime(trip.gioDenDuKien)}</small>
                </td>
                <td>{trip.bienSo ?? 'Chưa gán'}</td>
                <td>{getTripStatusLabel(trip)}</td>
                <td className={styles.buttons}>
                  <button disabled={waiting} onClick={() => handleViewTrip(trip.maChuyen)}>
                    Chi tiết / mở bán
                  </button>
                  <button disabled={waiting} onClick={() => setViewingPassengersTrip(trip.maChuyen)}>
                    Hành khách & Vé
                  </button>
                  <button disabled={waiting || !trip.coTheSua} onClick={() => handleEditTrip(trip)}>
                    Sửa
                  </button>
                  <button disabled={waiting || !trip.coTheXoa} onClick={() => handleDeleteTrip(trip)}>
                    Xóa
                  </button>
                </td>
              </tr>
            ))}
            {page.items.length === 0 && (
              <tr>
                <td colSpan={5}>Không có chuyến phù hợp.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <div className={styles.buttons}>
        <button disabled={waiting || page.page <= 1} onClick={() => handleLoadPage(page.page - 1)}>
          Trang trước
        </button>
        <span>
          Trang {page.page} · {page.totalItems} chuyến
        </span>
        <button disabled={waiting || page.page >= page.totalPages} onClick={() => handleLoadPage(page.page + 1)}>
          Trang sau
        </button>
      </div>
    </section>
  );
}
