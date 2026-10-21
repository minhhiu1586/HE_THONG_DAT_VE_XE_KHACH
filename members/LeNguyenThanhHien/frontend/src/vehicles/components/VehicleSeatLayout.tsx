import { clearValidation, vietnameseValidation } from '@shared/lib/form-validation';
import type { Dispatch, FormEvent, RefCallback, SetStateAction } from 'react';
import { seatStatusLabels, vehicleStatusLabels } from '../lib/vehicle-types';
import type { Operator, Seat, Vehicle, VehicleType } from '../lib/vehicle-types';
import styles from './VehicleManagement.module.css';

type Props = {
  selected: Vehicle;
  types: VehicleType[];
  operators: Operator[];
  seats: Seat[];
  setSeats: Dispatch<SetStateAction<Seat[]>>;
  busy: boolean;
  detailTarget: RefCallback<HTMLElement>;
  handleUpdateSeat: (index: number, changes: Partial<Seat>) => void;
  handleSaveSeatLayout: (event: FormEvent<HTMLFormElement>) => void;
  loadVehicleDetails: (vehicle: Vehicle, navigate?: boolean) => void;
};

export default function VehicleSeatLayout({
  selected,
  types,
  operators,
  seats,
  setSeats,
  busy,
  detailTarget,
  handleUpdateSeat,
  handleSaveSeatLayout,
  loadVehicleDetails,
}: Props) {
  const layoutType = types.find(type => type.maLoaiXe === selected.maLoaiXe);
  return (
    <section ref={detailTarget} className={styles.panel}>
      <h2 data-management-focus tabIndex={-1}>
        Chi tiết xe · {selected.bienSo}
      </h2>
      <p>
        Nhà xe: {operators.find(owner => owner.maNhaXe === selected.maNhaXe)?.tenNhaXe || selected.maNhaXe}. Đăng kiểm:{' '}
        {selected.ngayDangKiem} → {selected.hanDangKiem}.
      </p>
      <p>
        Loại xe: {layoutType?.tenLoaiXe || selected.maLoaiXe} · Sức chứa: {selected.soChoNgoi} · Trạng thái:{' '}
        {vehicleStatusLabels[selected.trangThai]}.
      </p>
      <p>
        Năm sản xuất: {selected.namSanXuat ?? 'Chưa nhập'} · Dung tích nhiên liệu:{' '}
        {selected.dungTichXang == null ? 'Chưa nhập' : `${selected.dungTichXang} lít`} · Tốc độ:{' '}
        {selected.tocDo == null ? 'Chưa nhập' : `${selected.tocDo} km/giờ`}.
      </p>
      {!selected.dangKiemConHieuLuc && <p className={styles.warning}>Xe chưa đủ hiệu lực đăng kiểm; không được mở bán chuyến mới.</p>}
      {!selected.soDoHopLe && <p className={styles.warning}>Sơ đồ hiện tại chưa khớp sức chứa hoặc có cấu hình không hợp lệ.</p>}
      {!selected.coTheSuaSoDo && (
        <p className={styles.warning}>
          Xe có lịch chuyến hoặc ghế có lịch sử vé/giữ chỗ. Không được sửa sơ đồ; cần xử lý lịch vận hành trước.
        </p>
      )}
      <h3>
        Sơ đồ ghế · {seats.length}/{selected.soChoNgoi} chỗ
      </h3>
      <p>Mỗi ô là ghế vật lý. Màu thể hiện hoạt động/bảo trì, không phải trạng thái đã bán của một chuyến.</p>
      {Array.from({ length: layoutType?.soTang ?? 1 }, (_, floor) => (
        <div key={floor}>
          <h4>Tầng {floor + 1}</h4>
          <div className={styles.seatGrid}>
            {seats
              .filter(seat => seat.tang === floor + 1)
              .slice()
              .sort((a, b) => a.viTri.localeCompare(b.viTri, 'vi', { numeric: true }))
              .map((seat, index) => (
                <span
                  key={seat.maGhe ?? `${floor}-${index}`}
                  className={`${styles.seat} ${seat.trangThai === 'HOAT_DONG' ? styles.activeSeat : styles.maintenanceSeat}`}
                >
                  {seat.viTri}
                  <small>{seatStatusLabels[seat.trangThai]}</small>
                </span>
              ))}
          </div>
        </div>
      ))}
      <form onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation} onSubmit={handleSaveSeatLayout}>
        <div className={styles.scroll}>
          <table>
            <thead>
              <tr>
                <th>Mã/vị trí</th>
                <th>Tầng</th>
                <th>Trạng thái ghế</th>
                <th>ID ghế (cố định)</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {seats.map((seat, index) => (
                <tr key={seat.maGhe ?? `new-${index}`}>
                  <td>
                    <input
                      aria-label={`Mã/vị trí ghế ${index + 1}`}
                      data-validation-label="mã/vị trí ghế"
                      required
                      maxLength={20}
                      disabled={!selected.coTheSuaSoDo}
                      value={seat.viTri}
                      onChange={e => handleUpdateSeat(index, { viTri: e.target.value })}
                    />
                  </td>
                  <td>
                    <select
                      aria-label={`Tầng ghế ${index + 1}`}
                      disabled={!selected.coTheSuaSoDo}
                      value={seat.tang}
                      onChange={e => handleUpdateSeat(index, { tang: Number(e.target.value) })}
                    >
                      {Array.from({ length: layoutType?.soTang ?? 1 }, (_, floor) => (
                        <option key={floor} value={floor + 1}>
                          {floor + 1}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <select
                      aria-label={`Trạng thái ghế ${index + 1}`}
                      disabled={!selected.coTheSuaSoDo}
                      value={seat.trangThai}
                      onChange={e => handleUpdateSeat(index, { trangThai: e.target.value })}
                    >
                      {Object.entries(seatStatusLabels).map(([value, label]) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <small>{seat.maGhe ?? 'Tự sinh khi lưu'}</small>
                  </td>
                  <td>
                    <button
                      type="button"
                      disabled={!selected.coTheSuaSoDo || busy}
                      onClick={() => setSeats(current => current.filter((_, i) => i !== index))}
                    >
                      Bỏ vị trí
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className={styles.buttons}>
          <button
            type="button"
            disabled={!selected.coTheSuaSoDo || busy}
            onClick={() => setSeats(current => [...current, { maGhe: null, viTri: '', tang: 1, trangThai: 'HOAT_DONG' }])}
          >
            Thêm vị trí
          </button>
          <button disabled={!selected.coTheSuaSoDo || busy || seats.length !== selected.soChoNgoi}>Lưu sơ đồ ghế</button>
          <button type="button" disabled={busy} onClick={() => void loadVehicleDetails(selected, false)}>
            Tải lại sơ đồ
          </button>
        </div>
        {seats.length !== selected.soChoNgoi && (
          <p className={styles.warning}>
            Danh sách đang có {seats.length} ghế; cần đúng {selected.soChoNgoi} ghế để lưu. Các thay đổi chưa được lưu.
          </p>
        )}
      </form>
    </section>
  );
}
