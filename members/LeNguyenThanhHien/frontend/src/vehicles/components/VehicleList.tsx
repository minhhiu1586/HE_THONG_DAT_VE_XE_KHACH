import { vehicleStatusLabels } from '../lib/vehicle-types';
import type { Operator, Vehicle, VehicleType } from '../lib/vehicle-types';
import Field from './VehicleField';
import styles from './VehicleManagement.module.css';

type Props = {
  filters: { query: string; filterType: string; filterOwner: string; filterState: string };
  onFilterChange: (name: 'query' | 'filterType' | 'filterOwner' | 'filterState', value: string) => void;
  types: VehicleType[];
  operators: Operator[];
  isAdmin: boolean;
  busy: boolean;
  vehicles: Vehicle[];
  vehiclePage: number;
  setVehiclePage: (page: number) => void;
  handleFilterVehicles: () => void;
  handleEditVehicle: (vehicle: Vehicle) => void;
  loadVehicleDetails: (vehicle: Vehicle) => void;
  handleDeleteVehicle: (vehicle: Vehicle) => void;
};

export default function VehicleList({
  filters,
  onFilterChange,
  types,
  operators,
  isAdmin,
  busy,
  vehicles,
  vehiclePage,
  setVehiclePage,
  handleFilterVehicles,
  handleEditVehicle,
  loadVehicleDetails,
  handleDeleteVehicle,
}: Props) {
  const { query, filterType, filterOwner, filterState } = filters;
  const vehiclePages = Math.max(1, Math.ceil(vehicles.length / 10));
  const currentVehiclePage = Math.min(vehiclePage, vehiclePages);
  const visibleVehicles = vehicles.slice((currentVehiclePage - 1) * 10, currentVehiclePage * 10);
  return (
    <section className={styles.panel}>
      <h2>Danh sách xe</h2>
      <div className={styles.filters}>
        <Field label="Tìm biển số hoặc mã xe" value={query} onChange={e => onFilterChange('query', e.target.value)} />
        <label>
          Lọc loại xe
          <select value={filterType} onChange={e => onFilterChange('filterType', e.target.value)}>
            <option value="">Tất cả</option>
            {types.map(type => (
              <option key={type.maLoaiXe} value={type.maLoaiXe}>
                {type.tenLoaiXe}
              </option>
            ))}
          </select>
        </label>
        {isAdmin && (
          <label>
            Lọc nhà xe
            <select value={filterOwner} onChange={e => onFilterChange('filterOwner', e.target.value)}>
              <option value="">Tất cả</option>
              {operators.map(owner => (
                <option key={owner.maNhaXe} value={owner.maNhaXe}>
                  {owner.tenNhaXe}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Lọc trạng thái
          <select value={filterState} onChange={e => onFilterChange('filterState', e.target.value)}>
            <option value="">Tất cả</option>
            {Object.entries(vehicleStatusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <button disabled={busy} onClick={handleFilterVehicles}>
          Lọc xe
        </button>
      </div>
      <div className={styles.scroll}>
        <table>
          <thead>
            <tr>
              <th>Mã xe</th>
              <th>Biển số</th>
              <th>Loại xe</th>
              <th>Nhà xe</th>
              <th>Trạng thái</th>
              <th>Đăng kiểm</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {visibleVehicles.map(vehicle => (
              <tr key={vehicle.maXe}>
                <td>{vehicle.maXe}</td>
                <td>{vehicle.bienSo}</td>
                <td>{types.find(type => type.maLoaiXe === vehicle.maLoaiXe)?.tenLoaiXe || vehicle.maLoaiXe}</td>
                <td>{operators.find(owner => owner.maNhaXe === vehicle.maNhaXe)?.tenNhaXe || vehicle.maNhaXe}</td>
                <td>{vehicleStatusLabels[vehicle.trangThai]}</td>
                <td>{vehicle.dangKiemConHieuLuc ? 'Còn hiệu lực' : 'Chưa đủ điều kiện'}</td>
                <td>
                  <button onClick={() => handleEditVehicle(vehicle)}>Sửa</button>
                  <button onClick={() => void loadVehicleDetails(vehicle)}>Chi tiết / ghế</button>
                  <button disabled={busy} onClick={() => handleDeleteVehicle(vehicle)}>
                    Xóa
                  </button>
                </td>
              </tr>
            ))}
            {vehicles.length === 0 && (
              <tr>
                <td colSpan={7}>Không có xe phù hợp.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <div className={styles.pagination}>
        <span>
          {vehicles.length} xe · Hiển thị {vehicles.length ? (currentVehiclePage - 1) * 10 + 1 : 0}–
          {Math.min(currentVehiclePage * 10, vehicles.length)}
        </span>
        <div>
          <button type="button" disabled={busy || currentVehiclePage === 1} onClick={() => setVehiclePage(currentVehiclePage - 1)}>
            Trang trước
          </button>
          <span>
            Trang {currentVehiclePage}/{vehiclePages}
          </span>
          <button
            type="button"
            disabled={busy || currentVehiclePage >= vehiclePages}
            onClick={() => setVehiclePage(currentVehiclePage + 1)}
          >
            Trang sau
          </button>
        </div>
      </div>
    </section>
  );
}
