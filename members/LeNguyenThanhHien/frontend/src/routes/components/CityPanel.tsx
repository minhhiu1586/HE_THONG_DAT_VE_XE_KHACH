import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import type { Dispatch, FormEvent, RefCallback, SetStateAction } from 'react';
import type { City, CityForm } from '../lib/route-types';
import styles from './RouteManagement.module.css';

type Props = {
  cityTarget: RefCallback<HTMLElement>;
  citySearch: string;
  setCitySearch: (value: string) => void;
  loadCities: () => Promise<void>;
  cityForm: CityForm;
  setCityForm: Dispatch<SetStateAction<CityForm>>;
  editingCity: boolean;
  handleSaveCity: (event: FormEvent<HTMLFormElement>) => void;
  busy: boolean;
  cityResults: City[];
  handleEditCity: (city: City) => void;
  handleDeleteCity: (city: City) => void;
  handleCancelEdit: () => void;
};

export default function CityPanel({
  cityTarget,
  citySearch,
  setCitySearch,
  loadCities,
  cityForm,
  setCityForm,
  editingCity,
  handleSaveCity,
  busy,
  cityResults,
  handleEditCity,
  handleDeleteCity,
  handleCancelEdit,
}: Props) {
  return (
    <section ref={cityTarget} className={styles.panel}>
      <div className={styles.sectionTitle}>
        <div>
          <h2 data-management-focus tabIndex={-1}>
            Tỉnh/thành phố
          </h2>
          <p>Danh mục tỉnh thành được dùng để xác định tỉnh đi và tỉnh đến qua bến xe.</p>
        </div>
        <div className={styles.search}>
          <input
            aria-label="Tìm tỉnh thành"
            placeholder="Mã hoặc tên tỉnh/thành"
            value={citySearch}
            onChange={e => setCitySearch(e.target.value)}
          />
          <button onClick={() => void loadCities()}>Tìm</button>
        </div>
      </div>
      <form
        onInvalid={vietnameseValidation}
        onInput={clearValidation}
        onChange={clearValidation}
        className={styles.form}
        onSubmit={handleSaveCity}
      >
        <label>
          Mã tỉnh/thành
          <input
            data-validation-label="mã tỉnh/thành"
            value={cityForm.maTinhThanh}
            disabled={editingCity}
            maxLength={40}
            required
            onChange={e => setCityForm({ ...cityForm, maTinhThanh: e.target.value })}
          />
        </label>
        <label>
          Tên
          <input
            data-validation-label="tên tỉnh/thành phố"
            value={cityForm.tenTinhThanh}
            maxLength={120}
            required
            onChange={e => setCityForm({ ...cityForm, tenTinhThanh: e.target.value })}
          />
        </label>
        <label>
          Khu vực
          <input
            data-validation-label="khu vực"
            value={cityForm.khuVuc}
            maxLength={80}
            onChange={e => setCityForm({ ...cityForm, khuVuc: e.target.value })}
          />
        </label>
        <button disabled={busy}>{editingCity ? 'Lưu thay đổi' : 'Thêm tỉnh/thành'}</button>
        {editingCity && (
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
              <th>Tỉnh/thành</th>
              <th>Khu vực</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {cityResults.map(city => (
              <tr key={city.maTinhThanh}>
                <td>{city.maTinhThanh}</td>
                <td>{city.tenTinhThanh}</td>
                <td>{city.khuVuc || '—'}</td>
                <td className={styles.actions}>
                  <button onClick={() => handleEditCity(city)}>Sửa</button>
                  <button disabled={busy} onClick={() => handleDeleteCity(city)}>
                    Xóa
                  </button>
                </td>
              </tr>
            ))}
            {cityResults.length === 0 && (
              <tr>
                <td colSpan={4}>Chưa có tỉnh/thành phù hợp.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
