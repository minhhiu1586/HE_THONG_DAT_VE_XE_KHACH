import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import type { Dispatch, FormEvent, RefCallback, SetStateAction } from 'react';
import type { Route, Stop, StopForm } from '../lib/route-types';
import styles from './RouteManagement.module.css';

type Props = {
  stopTarget: RefCallback<HTMLElement>;
  currentRoute: Route;
  stopForm: StopForm;
  setStopForm: Dispatch<SetStateAction<StopForm>>;
  editingStop: string;
  handleSaveStop: (event: FormEvent<HTMLFormElement>) => void;
  handleCancelEdit: () => void;
  busy: boolean;
  handleEditStop: (stop: Stop) => void;
  handleMoveStop: (index: number, direction: -1 | 1) => void;
  handleDeleteStop: (stop: Stop) => void;
};

export default function RouteStopsPanel({
  stopTarget,
  currentRoute,
  stopForm,
  setStopForm,
  editingStop,
  handleSaveStop,
  handleCancelEdit,
  busy,
  handleEditStop,
  handleMoveStop,
  handleDeleteStop,
}: Props) {
  return (
    <section ref={stopTarget} className={styles.panel}>
      <div className={styles.sectionTitle}>
        <div>
          <h2 data-management-focus tabIndex={-1}>
            Điểm dừng · {currentRoute.maTuyen}
          </h2>
          <p>Điểm dừng không bắt buộc. Thứ tự bắt đầu từ 1 và cập nhật trong cùng một giao dịch.</p>
        </div>
      </div>
      <form
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
        onChange={clearValidation}
        className={styles.form}
        onSubmit={handleSaveStop}
      >
        {!editingStop && (
          <label>
            Mã điểm dừng
            <input
              data-validation-label="mã điểm dừng"
              required
              maxLength={40}
              value={stopForm.maDiemDung}
              onChange={e => setStopForm({ ...stopForm, maDiemDung: e.target.value })}
            />
          </label>
        )}
        <label>
          Tên điểm dừng
          <input
            data-validation-label="tên điểm dừng"
            required
            maxLength={160}
            value={stopForm.tenDiemDung}
            onChange={e => setStopForm({ ...stopForm, tenDiemDung: e.target.value })}
          />
        </label>
        <label>
          Địa chỉ
          <input
            data-validation-label="địa chỉ"
            required
            maxLength={255}
            value={stopForm.diaChi}
            onChange={e => setStopForm({ ...stopForm, diaChi: e.target.value })}
          />
        </label>
        <label>
          Thời gian dừng (phút)
          <input
            data-validation-label="thời gian dừng"
            type="number"
            min="0"
            required
            value={stopForm.thoiGianDung}
            onChange={e => setStopForm({ ...stopForm, thoiGianDung: e.target.value })}
          />
        </label>
        <label>
          Loại điểm dừng
          <select
            data-validation-label="loại điểm dừng"
            value={stopForm.loaiDiemDung}
            onChange={e => setStopForm({ ...stopForm, loaiDiemDung: e.target.value })}
          >
            {['DIEM_DON', 'DIEM_TRA', 'CA_HAI'].map(s => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
        <button disabled={busy}>{editingStop ? 'Lưu điểm dừng' : 'Thêm điểm dừng'}</button>
        {editingStop && (
          <button type="button" onClick={handleCancelEdit}>
            Hủy sửa
          </button>
        )}
      </form>
      <ol className={styles.stopList}>
        {currentRoute.tramTrungGian.map((stop, index) => (
          <li key={stop.maDiemDung}>
            <span>
              <b>
                {stop.thuTu}. {stop.tenDiemDung}
              </b>
              <small>
                {stop.diaChi} · dừng {stop.thoiGianDung} phút
              </small>
            </span>
            <span className={styles.actions}>
              <button disabled={busy} onClick={() => handleEditStop(stop)}>
                Sửa
              </button>
              <button disabled={busy || index === 0} onClick={() => handleMoveStop(index, -1)}>
                ↑
              </button>
              <button disabled={busy || index === currentRoute.tramTrungGian.length - 1} onClick={() => handleMoveStop(index, 1)}>
                ↓
              </button>
              <button disabled={busy} onClick={() => handleDeleteStop(stop)}>
                Xóa
              </button>
            </span>
          </li>
        ))}
        {currentRoute.tramTrungGian.length === 0 && <li>Tuyến chưa có điểm dừng trung gian.</li>}
      </ol>
    </section>
  );
}
