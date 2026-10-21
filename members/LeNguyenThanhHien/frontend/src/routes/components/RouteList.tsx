import { routeStatuses, routeStatusLabels } from '../lib/route-types';
import type { Route, Station } from '../lib/route-types';
import styles from './RouteManagement.module.css';

type Props = {
  filters: { start: string; end: string; status: string; operator: string };
  onFilterChange: (key: 'start' | 'end' | 'status' | 'operator', value: string) => void;
  stations: Station[];
  isAdmin: boolean;
  loadRoutes: () => Promise<void>;
  routes: Route[];
  busy: boolean;
  handleEditRoute: (route: Route) => void;
  handleChooseRoute: (code: string) => void;
  handleDeleteRoute: (route: Route) => void;
};

export default function RouteList({
  filters,
  onFilterChange,
  stations,
  isAdmin,
  loadRoutes,
  routes,
  busy,
  handleEditRoute,
  handleChooseRoute,
  handleDeleteRoute,
}: Props) {
  const { start: filterStart, end: filterEnd, status: filterStatus, operator: filterOperator } = filters;
  return (
    <section className={styles.panel}>
      <div className={styles.sectionTitle}>
        <div>
          <h2>Danh sách tuyến</h2>
          <p>Nhà xe chỉ xem và quản lý tuyến thuộc phạm vi của mình.</p>
        </div>
      </div>
      <div className={styles.filters}>
        <label>
          Bến đi
          <select data-validation-label="bến đi" value={filterStart} onChange={e => onFilterChange('start', e.target.value)}>
            <option value="">Tất cả</option>
            {stations.map(s => (
              <option key={s.maBenXe} value={s.maBenXe}>
                {s.tenBenXe}
              </option>
            ))}
          </select>
        </label>
        <label>
          Bến đến
          <select data-validation-label="bến đến" value={filterEnd} onChange={e => onFilterChange('end', e.target.value)}>
            <option value="">Tất cả</option>
            {stations.map(s => (
              <option key={s.maBenXe} value={s.maBenXe}>
                {s.tenBenXe}
              </option>
            ))}
          </select>
        </label>
        <label>
          Trạng thái
          <select data-validation-label="trạng thái" value={filterStatus} onChange={e => onFilterChange('status', e.target.value)}>
            <option value="">Tất cả</option>
            {routeStatuses.map(s => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
        {isAdmin && (
          <label>
            Mã nhà xe
            <input
              data-validation-label="mã nhà xe"
              value={filterOperator}
              onChange={e => onFilterChange('operator', e.target.value)}
              placeholder="Không giới hạn"
            />
          </label>
        )}
        <button onClick={() => void loadRoutes()}>Lọc tuyến</button>
      </div>
      <div className={styles.tableWrap}>
        <table>
          <thead>
            <tr>
              <th>Mã tuyến</th>
              <th>Tuyến</th>
              <th>Thời lượng</th>
              <th>Nhà xe</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {routes.map(route => (
              <tr key={route.maTuyen}>
                <td>{route.maTuyen}</td>
                <td>
                  {stations.find(s => s.maBenXe === route.tramKhoiHanh)?.tenBenXe || route.tramKhoiHanh} →{' '}
                  {stations.find(s => s.maBenXe === route.tramDen)?.tenBenXe || route.tramDen}
                </td>
                <td>{route.thoiGianDuKien ?? '—'} giờ</td>
                <td>{route.maNhaXe || '—'}</td>
                <td>
                  <span aria-label={`Trạng thái tuyến ${route.maTuyen}`}>{routeStatusLabels[route.trangThai] || route.trangThai}</span>
                </td>
                <td className={styles.actions}>
                  <button onClick={() => handleEditRoute(route)}>Sửa</button>
                  <button onClick={() => handleChooseRoute(route.maTuyen)}>Điểm dừng</button>
                  <button disabled={busy} onClick={() => handleDeleteRoute(route)}>
                    Xóa
                  </button>
                </td>
              </tr>
            ))}
            {routes.length === 0 && (
              <tr>
                <td colSpan={6}>Chưa có tuyến phù hợp.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
