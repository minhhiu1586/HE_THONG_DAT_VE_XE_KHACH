import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import type { Dispatch, FormEvent, RefCallback, SetStateAction } from 'react';
import { vehicleStatusLabels } from '../lib/vehicle-types';
import type { Operator, VehicleFormValues, VehicleType } from '../lib/vehicle-types';
import Field from './VehicleField';
import styles from './VehicleManagement.module.css';

type Props = {
  vehicleFormTarget: RefCallback<HTMLElement>;
  editingVehicle: boolean;
  vehicleForm: VehicleFormValues;
  setVehicleForm: Dispatch<SetStateAction<VehicleFormValues>>;
  types: VehicleType[];
  operators: Operator[];
  isAdmin: boolean;
  busy: boolean;
  handleSaveVehicle: (event: FormEvent<HTMLFormElement>) => void;
  handleCancelEdit: () => void;
};

export default function VehicleForm({
  vehicleFormTarget,
  editingVehicle,
  vehicleForm,
  setVehicleForm,
  types,
  operators,
  isAdmin,
  busy,
  handleSaveVehicle,
  handleCancelEdit,
}: Props) {
  const selectedType = types.find(type => type.maLoaiXe === vehicleForm.maLoaiXe);
  return (
    <section ref={vehicleFormTarget} className={styles.panel}>
      <h2 data-management-focus tabIndex={-1}>
        {editingVehicle ? `Sửa xe ${vehicleForm.maXe}` : 'Thêm xe'}
      </h2>
      <p>Sức chứa lấy từ loại xe. Xe không đủ đăng kiểm hoặc đang bảo trì không được gán vào chuyến mới.</p>
      <form
        className={styles.form}
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
        onChange={clearValidation}
        onSubmit={handleSaveVehicle}
      >
        <Field
          label="Mã xe"
          required
          maxLength={40}
          disabled={editingVehicle}
          value={vehicleForm.maXe}
          onChange={e => setVehicleForm({ ...vehicleForm, maXe: e.target.value })}
        />
        <Field
          label="Biển số"
          required
          maxLength={30}
          value={vehicleForm.bienSo}
          onChange={e => setVehicleForm({ ...vehicleForm, bienSo: e.target.value })}
        />
        <label>
          Loại xe
          <select
            data-validation-label="loại xe"
            required
            disabled={editingVehicle}
            value={vehicleForm.maLoaiXe}
            onChange={e => setVehicleForm({ ...vehicleForm, maLoaiXe: e.target.value })}
          >
            <option value="">Chọn loại xe</option>
            {types.map(type => (
              <option key={type.maLoaiXe} value={type.maLoaiXe}>
                {type.tenLoaiXe} · {type.soChoNgoi} chỗ / {type.soTang} tầng
              </option>
            ))}
          </select>
        </label>
        <label>
          Nhà xe
          <select
            data-validation-label="nhà xe"
            required
            disabled={editingVehicle || !isAdmin}
            value={vehicleForm.maNhaXe || (!isAdmin ? (operators[0]?.maNhaXe ?? '') : '')}
            onChange={e => setVehicleForm({ ...vehicleForm, maNhaXe: e.target.value })}
          >
            <option value="">Chọn nhà xe</option>
            {operators.map(owner => (
              <option key={owner.maNhaXe} value={owner.maNhaXe}>
                {owner.tenNhaXe}
              </option>
            ))}
          </select>
        </label>
        <Field label="Số ghế" readOnly value={selectedType?.soChoNgoi ?? ''} />
        <Field
          label="Năm sản xuất"
          type="number"
          min="1"
          step="1"
          value={vehicleForm.namSanXuat}
          onChange={e => setVehicleForm({ ...vehicleForm, namSanXuat: e.target.value })}
        />
        <Field
          label="Ngày đăng kiểm"
          type="date"
          required
          value={vehicleForm.ngayDangKiem}
          onChange={e => setVehicleForm({ ...vehicleForm, ngayDangKiem: e.target.value })}
        />
        <Field
          label="Hạn đăng kiểm"
          type="date"
          min={vehicleForm.ngayDangKiem || undefined}
          required
          value={vehicleForm.hanDangKiem}
          onChange={e => setVehicleForm({ ...vehicleForm, hanDangKiem: e.target.value })}
        />
        <Field
          label="Dung tích nhiên liệu (lít)"
          type="number"
          min="0.01"
          step="0.01"
          value={vehicleForm.dungTichXang}
          onChange={e => setVehicleForm({ ...vehicleForm, dungTichXang: e.target.value })}
        />
        <Field
          label="Tốc độ (km/giờ)"
          type="number"
          min="0.01"
          step="0.01"
          value={vehicleForm.tocDo}
          onChange={e => setVehicleForm({ ...vehicleForm, tocDo: e.target.value })}
        />
        <label>
          Trạng thái xe
          <select value={vehicleForm.trangThai} onChange={e => setVehicleForm({ ...vehicleForm, trangThai: e.target.value })}>
            {Object.entries(vehicleStatusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <button disabled={busy}>{editingVehicle ? 'Lưu xe' : 'Thêm xe'}</button>
        {editingVehicle && (
          <button type="button" onClick={handleCancelEdit}>
            Hủy sửa xe
          </button>
        )}
      </form>
    </section>
  );
}
