import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import type { Dispatch, FormEvent, RefCallback, SetStateAction } from 'react';
import type { VehicleType, VehicleTypeForm } from '../lib/vehicle-types';
import Field from './VehicleField';
import styles from './VehicleManagement.module.css';

type Props = {
  typeFormTarget: RefCallback<HTMLElement>;
  editingType: boolean;
  typeForm: VehicleTypeForm;
  setTypeForm: Dispatch<SetStateAction<VehicleTypeForm>>;
  busy: boolean;
  handleSaveVehicleType: (event: FormEvent<HTMLFormElement>) => void;
  handleCancelEdit: () => void;
  typeQuery: string;
  setTypeQuery: (value: string) => void;
  handleSearchTypes: () => void;
  typeResults: VehicleType[];
  handleEditVehicleType: (type: VehicleType) => void;
  handleDeleteType: (type: VehicleType) => void;
};

export default function VehicleTypePanel({
  typeFormTarget,
  editingType,
  typeForm,
  setTypeForm,
  busy,
  handleSaveVehicleType,
  handleCancelEdit,
  typeQuery,
  setTypeQuery,
  handleSearchTypes,
  typeResults,
  handleEditVehicleType,
  handleDeleteType,
}: Props) {
  return (
    <section ref={typeFormTarget} className={styles.panel}>
      <h2 data-management-focus tabIndex={-1}>
        {editingType ? 'Sửa loại xe' : 'Thêm loại xe'}
      </h2>
      <form
        className={styles.form}
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
        onChange={clearValidation}
        onSubmit={handleSaveVehicleType}
      >
        <Field
          label="Mã loại xe"
          required
          maxLength={40}
          disabled={editingType}
          value={typeForm.maLoaiXe}
          onChange={e => setTypeForm({ ...typeForm, maLoaiXe: e.target.value })}
        />
        <Field
          label="Tên loại xe"
          required
          maxLength={120}
          value={typeForm.tenLoaiXe}
          onChange={e => setTypeForm({ ...typeForm, tenLoaiXe: e.target.value })}
        />
        <Field
          label="Sức chứa"
          type="number"
          min="1"
          step="1"
          required
          value={typeForm.soChoNgoi}
          onChange={e => setTypeForm({ ...typeForm, soChoNgoi: e.target.value })}
        />
        <Field
          label="Số tầng"
          type="number"
          min="1"
          step="1"
          required
          value={typeForm.soTang}
          onChange={e => setTypeForm({ ...typeForm, soTang: e.target.value })}
        />
        <Field
          label="Mô tả loại xe"
          maxLength={1000}
          value={typeForm.moTa}
          onChange={e => setTypeForm({ ...typeForm, moTa: e.target.value })}
        />
        <button disabled={busy}>{editingType ? 'Lưu loại xe' : 'Thêm loại xe'}</button>
        {editingType && (
          <button type="button" onClick={handleCancelEdit}>
            Hủy sửa
          </button>
        )}
      </form>
      <div className={styles.filters}>
        <Field label="Tìm loại xe" value={typeQuery} onChange={e => setTypeQuery(e.target.value)} />
        <button onClick={handleSearchTypes}>Tìm loại xe</button>
      </div>
      <div className={styles.scroll}>
        <table>
          <thead>
            <tr>
              <th>Mã</th>
              <th>Loại xe</th>
              <th>Sức chứa</th>
              <th>Tầng</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {typeResults.map(type => (
              <tr key={type.maLoaiXe}>
                <td>{type.maLoaiXe}</td>
                <td>{type.tenLoaiXe}</td>
                <td>{type.soChoNgoi}</td>
                <td>{type.soTang}</td>
                <td>
                  <button onClick={() => handleEditVehicleType(type)}>Sửa</button>
                  <button disabled={busy} onClick={() => handleDeleteType(type)}>
                    Xóa
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
