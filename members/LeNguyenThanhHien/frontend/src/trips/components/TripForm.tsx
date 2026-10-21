import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import { routeChoices } from '@shared/lib/management-options';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import type { Operator, Route, TripFormValues, Vehicle } from '../lib/trip-types';
import styles from './TripManagement.module.css';

type Props = {
  form: TripFormValues;
  setForm: Dispatch<SetStateAction<TripFormValues>>;
  editing: boolean;
  waiting: boolean;
  routes: Route[];
  vehicles: Vehicle[];
  operators: Operator[];
  arrival: string;
  handleSaveTrip: (event: FormEvent<HTMLFormElement>) => void;
  handleCancelEdit: () => void;
};

export default function TripForm({
  form,
  setForm,
  editing,
  waiting,
  routes,
  vehicles,
  operators,
  arrival,
  handleSaveTrip,
  handleCancelEdit,
}: Props) {
  const route = routes.find(item => item.maTuyen === form.maTuyen);
  const choices = routeChoices(routes, operators);
  return (
    <form
      className={styles.form}
      onSubmit={handleSaveTrip}
      onInvalid={vietnameseValidation}
      onInput={clearValidation}
      onChange={clearValidation}
    >
      <label>
        Mã chuyến
        <input
          data-validation-label="mã chuyến"
          required
          maxLength={40}
          pattern="[A-Za-z0-9_-]+"
          disabled={editing}
          value={form.maChuyen}
          onChange={e => setForm({ ...form, maChuyen: e.target.value })}
        />
      </label>
      <label>
        Tuyến xe
        <select
          aria-label="Tuyến xe"
          data-validation-label="tuyến xe"
          required
          value={form.maTuyen}
          onChange={e => setForm({ ...form, maTuyen: e.target.value, maXe: '', gioDenDuKien: '' })}
        >
          <option value="">Chọn tuyến</option>
          {choices.map(choice => (
            <option key={choice.value} value={choice.value} disabled={choice.disabled}>
              {choice.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Xe dự kiến (không bắt buộc)
        <select aria-label="Xe dự kiến (không bắt buộc)" value={form.maXe} onChange={e => setForm({ ...form, maXe: e.target.value })}>
          <option value="">Chưa gán xe</option>
          {vehicles
            .filter(v => v.maNhaXe === route?.maNhaXe)
            .map(v => (
              <option key={v.maXe} value={v.maXe}>
                {v.bienSo} · {v.maXe}
              </option>
            ))}
        </select>
      </label>
      <label>
        Nhà xe vận hành
        <input readOnly value={operators.find(o => o.maNhaXe === route?.maNhaXe)?.tenNhaXe ?? route?.maNhaXe ?? ''} />
      </label>
      <label>
        Ngày khởi hành
        <input
          data-validation-label="ngày khởi hành"
          required
          type="date"
          value={form.ngayKhoiHanh}
          onChange={e => setForm({ ...form, ngayKhoiHanh: e.target.value, gioDenDuKien: '' })}
        />
      </label>
      <label>
        Giờ khởi hành
        <input
          data-validation-label="giờ khởi hành"
          required
          type="time"
          value={form.gioKhoiHanh}
          onChange={e => setForm({ ...form, gioKhoiHanh: e.target.value, gioDenDuKien: '' })}
        />
      </label>
      <label>
        Ngày giờ đến dự kiến
        <input
          data-validation-label="ngày giờ đến dự kiến"
          required
          type="datetime-local"
          value={arrival}
          onChange={e => setForm({ ...form, gioDenDuKien: e.target.value })}
        />
      </label>
      <label>
        Giá vé dự kiến (đồng)
        <input readOnly value={route?.giaCoBan?.toLocaleString('vi-VN') ?? ''} />
      </label>
      <p className={styles.wide}>Giá lấy từ tuyến. Có thể lưu nháp khi chưa gán xe hoặc chưa phân công đủ nhân viên.</p>
      <div className={`${styles.buttons} ${styles.wide}`}>
        <button disabled={waiting}>{editing ? 'Lưu chuyến nháp' : 'Tạo chuyến nháp'}</button>
        {editing && (
          <button type="button" onClick={handleCancelEdit}>
            Hủy sửa
          </button>
        )}
      </div>
    </form>
  );
}
