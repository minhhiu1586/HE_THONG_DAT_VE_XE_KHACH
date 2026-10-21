import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import type { Dispatch, FormEvent, RefCallback, SetStateAction } from 'react';
import { stationStatusLabels } from '../lib/route-types';
import type { City, Station, StationForm } from '../lib/route-types';
import styles from './RouteManagement.module.css';

type Props = {
  stationTarget: RefCallback<HTMLElement>;
  filters: { search: string; city: string; status: string };
  onFilterChange: (key: 'search' | 'city' | 'status', value: string) => void;
  loadStations: () => Promise<void>;
  cities: City[];
  stationForm: StationForm;
  setStationForm: Dispatch<SetStateAction<StationForm>>;
  editingStation: boolean;
  handleSaveStation: (event: FormEvent<HTMLFormElement>) => void;
  busy: boolean;
  stationResults: Station[];
  handleEditStation: (station: Station) => void;
  handleDeleteStation: (station: Station) => void;
  handleCancelEdit: () => void;
};

export default function StationPanel({
  stationTarget,
  filters,
  onFilterChange,
  loadStations,
  cities,
  stationForm,
  setStationForm,
  editingStation,
  handleSaveStation,
  busy,
  stationResults,
  handleEditStation,
  handleDeleteStation,
  handleCancelEdit,
}: Props) {
  const { search: stationSearch, city: stationCity, status: stationStatus } = filters;
  return (
    <section ref={stationTarget} className={styles.panel}>
      <div className={styles.sectionTitle}>
        <div>
          <h2 data-management-focus tabIndex={-1}>
            Bến xe
          </h2>
          <p>Mỗi bến thuộc một tỉnh/thành phố; bến đã gắn tuyến không thể xóa.</p>
        </div>
        <div className={styles.search}>
          <input
            aria-label="Tìm bến xe"
            placeholder="Mã hoặc tên bến xe"
            value={stationSearch}
            onChange={e => onFilterChange('search', e.target.value)}
          />
          <button onClick={() => void loadStations()}>Tìm</button>
        </div>
      </div>
      <div className={styles.filters}>
        <label>
          Tỉnh/thành
          <select data-validation-label="tỉnh/thành" value={stationCity} onChange={e => onFilterChange('city', e.target.value)}>
            <option value="">Tất cả</option>
            {cities.map(city => (
              <option key={city.maTinhThanh} value={city.maTinhThanh}>
                {city.tenTinhThanh}
              </option>
            ))}
          </select>
        </label>
        <label>
          Trạng thái
          <select data-validation-label="trạng thái" value={stationStatus} onChange={e => onFilterChange('status', e.target.value)}>
            <option value="">Tất cả</option>
            {Object.entries(stationStatusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <button onClick={() => void loadStations()}>Lọc bến xe</button>
      </div>
      <form
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
        onChange={clearValidation}
        className={styles.form}
        onSubmit={handleSaveStation}
      >
        <label>
          Mã bến
          <input
            data-validation-label="mã bến"
            value={stationForm.maBenXe}
            disabled={editingStation}
            maxLength={40}
            required
            onChange={e => setStationForm({ ...stationForm, maBenXe: e.target.value })}
          />
        </label>
        <label>
          Tên bến
          <input
            data-validation-label="tên bến"
            value={stationForm.tenBenXe}
            maxLength={160}
            required
            onChange={e => setStationForm({ ...stationForm, tenBenXe: e.target.value })}
          />
        </label>
        <label>
          Địa chỉ
          <input
            data-validation-label="địa chỉ"
            value={stationForm.diaChi}
            maxLength={255}
            required
            onChange={e => setStationForm({ ...stationForm, diaChi: e.target.value })}
          />
        </label>
        <label>
          Điện thoại
          <input
            data-validation-label="điện thoại"
            value={stationForm.soDienThoai}
            maxLength={30}
            onChange={e => setStationForm({ ...stationForm, soDienThoai: e.target.value })}
          />
        </label>
        <label>
          Tỉnh/thành
          <select
            data-validation-label="tỉnh/thành"
            required
            value={stationForm.maTinhThanh}
            onChange={e => setStationForm({ ...stationForm, maTinhThanh: e.target.value })}
          >
            <option value="">Chọn tỉnh/thành</option>
            {cities.map(city => (
              <option key={city.maTinhThanh} value={city.maTinhThanh}>
                {city.tenTinhThanh}
              </option>
            ))}
          </select>
        </label>
        <label>
          Trạng thái
          <select
            data-validation-label="trạng thái"
            value={stationForm.trangThai}
            onChange={e => setStationForm({ ...stationForm, trangThai: e.target.value })}
          >
            {Object.entries(stationStatusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <button disabled={busy}>{editingStation ? 'Lưu thay đổi' : 'Thêm bến xe'}</button>
        {editingStation && (
          <button type="button" onClick={handleCancelEdit}>
            Hủy
          </button>
        )}
      </form>
      <div className={styles.tableWrap}>
        <table>
          <thead>
            <tr>
              <th>Mã</th>
              <th>Bến xe</th>
              <th>Tỉnh</th>
              <th>Địa chỉ</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {stationResults.map(station => (
              <tr key={station.maBenXe}>
                <td>{station.maBenXe}</td>
                <td>{station.tenBenXe}</td>
                <td>{cities.find(c => c.maTinhThanh === station.maTinhThanh)?.tenTinhThanh || station.maTinhThanh}</td>
                <td>{station.diaChi}</td>
                <td>{stationStatusLabels[station.trangThai] ?? station.trangThai}</td>
                <td className={styles.actions}>
                  <button onClick={() => handleEditStation(station)}>Sửa</button>
                  <button disabled={busy} onClick={() => handleDeleteStation(station)}>
                    Xóa
                  </button>
                </td>
              </tr>
            ))}
            {stationResults.length === 0 && (
              <tr>
                <td colSpan={6}>Chưa có bến xe phù hợp.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
