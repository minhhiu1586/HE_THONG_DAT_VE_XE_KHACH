import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import type { Dispatch, FormEvent, RefCallback, SetStateAction } from 'react';
import { routeStatuses, routeStatusLabels } from '../lib/route-types';
import type { City, RouteFormValues, Station } from '../lib/route-types';
import styles from './RouteManagement.module.css';

type Props = {
  routeTarget: RefCallback<HTMLElement>;
  editingRoute: boolean;
  routeForm: RouteFormValues;
  setRouteForm: Dispatch<SetStateAction<RouteFormValues>>;
  stations: Station[];
  cities: City[];
  busy: boolean;
  handleSaveRoute: (event: FormEvent<HTMLFormElement>) => void;
  handleNewRoute: () => void;
  handleSaveRouteStatus: () => void;
};

export default function RouteForm({
  routeTarget,
  editingRoute,
  routeForm,
  setRouteForm,
  stations,
  cities,
  busy,
  handleSaveRoute,
  handleNewRoute,
  handleSaveRouteStatus,
}: Props) {
  return (
    <section ref={routeTarget} className={styles.panel}>
      <div className={styles.sectionTitle}>
        <div>
          <h2 data-management-focus tabIndex={-1}>
            {editingRoute ? `Cập nhật tuyến ${routeForm.maTuyen}` : 'Tạo tuyến xe'}
          </h2>
          <p>Thời lượng dự kiến là thuộc tính tuyến; giờ khởi hành thực tế được lưu riêng cho từng chuyến.</p>
        </div>
        <button type="button" onClick={handleNewRoute}>
          Tuyến mới
        </button>
      </div>
      <form
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
        onChange={clearValidation}
        className={styles.form}
        onSubmit={handleSaveRoute}
      >
        <label>
          Mã tuyến
          <input
            data-validation-label="mã tuyến"
            disabled={editingRoute}
            required
            maxLength={40}
            value={routeForm.maTuyen}
            onChange={e => setRouteForm({ ...routeForm, maTuyen: e.target.value })}
          />
        </label>
        <label>
          Tên tuyến
          <input
            data-validation-label="tên tuyến"
            required
            maxLength={160}
            value={routeForm.tenTuyen}
            onChange={e => setRouteForm({ ...routeForm, tenTuyen: e.target.value })}
          />
        </label>
        <label>
          Bến khởi hành
          <select
            data-validation-label="bến khởi hành"
            required
            value={routeForm.tramKhoiHanh}
            onChange={e => setRouteForm({ ...routeForm, tramKhoiHanh: e.target.value })}
          >
            <option value="">Chọn bến đi</option>
            {stations
              .filter(s => s.trangThai === 'HOAT_DONG')
              .map(s => (
                <option key={s.maBenXe} value={s.maBenXe}>
                  {s.tenBenXe} · {cities.find(c => c.maTinhThanh === s.maTinhThanh)?.tenTinhThanh || s.maTinhThanh}
                </option>
              ))}
          </select>
        </label>
        <label>
          Bến đến
          <select
            data-validation-label="bến đến"
            required
            value={routeForm.tramDen}
            onChange={e => setRouteForm({ ...routeForm, tramDen: e.target.value })}
          >
            <option value="">Chọn bến đến</option>
            {stations
              .filter(s => s.trangThai === 'HOAT_DONG')
              .map(s => (
                <option key={s.maBenXe} value={s.maBenXe}>
                  {s.tenBenXe} · {cities.find(c => c.maTinhThanh === s.maTinhThanh)?.tenTinhThanh || s.maTinhThanh}
                </option>
              ))}
          </select>
        </label>
        <label>
          Giờ mặc định (không bắt buộc)
          <input
            data-validation-label="giờ mặc định"
            type="time"
            value={routeForm.thoiGianKhoiHanh}
            onChange={e => setRouteForm({ ...routeForm, thoiGianKhoiHanh: e.target.value })}
          />
        </label>
        <label>
          Thời lượng dự kiến (giờ)
          <input
            className={styles.manualNumber}
            onKeyDown={event => {
              if (event.key === 'ArrowUp' || event.key === 'ArrowDown') event.preventDefault();
            }}
            data-validation-label="thời lượng dự kiến"
            type="number"
            step="0.01"
            min="0.01"
            required
            value={routeForm.thoiGianDuKien}
            onChange={e => setRouteForm({ ...routeForm, thoiGianDuKien: e.target.value })}
          />
        </label>
        <label>
          Khoảng cách (km)
          <input
            className={styles.manualNumber}
            onKeyDown={event => {
              if (event.key === 'ArrowUp' || event.key === 'ArrowDown') event.preventDefault();
            }}
            data-validation-label="khoảng cách"
            type="number"
            step="0.01"
            min="0.01"
            value={routeForm.khoangCachKM}
            onChange={e => setRouteForm({ ...routeForm, khoangCachKM: e.target.value })}
          />
        </label>
        <label>
          Giá cơ bản (đồng)
          <input
            className={styles.manualNumber}
            onKeyDown={event => {
              if (event.key === 'ArrowUp' || event.key === 'ArrowDown') event.preventDefault();
            }}
            data-validation-label="giá cơ bản"
            type="number"
            step="0.01"
            min="0"
            required
            value={routeForm.giaCoBan}
            onChange={e => setRouteForm({ ...routeForm, giaCoBan: e.target.value })}
          />
        </label>
        <label className={styles.wide}>
          Mô tả
          <textarea
            data-validation-label="mô tả"
            maxLength={1000}
            value={routeForm.moTa}
            onChange={e => setRouteForm({ ...routeForm, moTa: e.target.value })}
          />
        </label>
        <label>
          Trạng thái khai thác
          <select
            data-validation-label="trạng thái khai thác"
            value={routeForm.trangThai}
            onChange={e => setRouteForm({ ...routeForm, trangThai: e.target.value })}
          >
            {routeStatuses.map(status => (
              <option key={status} value={status}>
                {routeStatusLabels[status]}
              </option>
            ))}
          </select>
        </label>
        {editingRoute && (
          <button type="button" disabled={busy} onClick={handleSaveRouteStatus}>
            Lưu trạng thái
          </button>
        )}
        <button disabled={busy}>{editingRoute ? 'Lưu tuyến' : 'Tạo tuyến'}</button>
      </form>
    </section>
  );
}
