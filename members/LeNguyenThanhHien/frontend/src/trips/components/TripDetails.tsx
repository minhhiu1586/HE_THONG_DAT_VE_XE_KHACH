import type { FormEvent, RefCallback } from 'react';
import { formatTripDateTime, getTripStatusLabel } from '../lib/trip-types';
import type { SaleReport, Trip, TripLifecycle } from '../lib/trip-types';
import TripLifecyclePanel from './TripLifecyclePanel';
import styles from './TripManagement.module.css';
import TripSaleConditions from './TripSaleConditions';

type Props = {
  selected: Trip;
  report: SaleReport | null;
  lifecycle: TripLifecycle | null;
  waiting: boolean;
  detailTarget: RefCallback<HTMLElement>;
  setViewingPassengersTrip: (code: string) => void;
  handleRefreshDetails: () => void;
  handleOpenTripSale: () => void;
  handleStartTrip: () => void;
  handleCompleteTrip: () => void;
  confirmCancel: boolean;
  setConfirmCancel: (value: boolean) => void;
  cancelReason: string;
  setCancelReason: (value: string) => void;
  handleCancelTrip: (event: FormEvent<HTMLFormElement>) => void;
  handleRetryCancellation: () => void;
};

export default function TripDetails({
  selected,
  report,
  lifecycle,
  waiting,
  detailTarget,
  setViewingPassengersTrip,
  handleRefreshDetails,
  handleOpenTripSale,
  handleStartTrip,
  handleCompleteTrip,
  confirmCancel,
  setConfirmCancel,
  cancelReason,
  setCancelReason,
  handleCancelTrip,
  handleRetryCancellation,
}: Props) {
  return (
    <section ref={detailTarget} className={styles.panel}>
      <h2 data-management-focus tabIndex={-1}>
        Chi tiết chuyến · {selected.maChuyen}
      </h2>
      <p>
        {selected.tenTuyen} · {selected.tenNhaXe} · {selected.bienSo ?? 'Chưa gán xe'}
      </p>
      <p>
        Trạng thái: <strong>{getTripStatusLabel(selected)}</strong>. Giá dự kiến: {Number(selected.giaVeDuKien).toLocaleString('vi-VN')}{' '}
        đồng.
      </p>
      <p>
        Ngừng nhận đặt vé: <strong>{formatTripDateTime(selected.ngungNhanDatVeLuc)}</strong> ({selected.phutDungNhanDatVe} phút trước khởi
        hành, giờ Việt Nam).
      </p>
      <TripSaleConditions selected={selected} report={report} />
      <div className={styles.buttons}>
        <button disabled={waiting} onClick={handleRefreshDetails}>
          Kiểm tra lại điều kiện
        </button>
        <button disabled={waiting} onClick={() => setViewingPassengersTrip(selected.maChuyen)}>
          Xem danh sách hành khách
        </button>
        <button disabled={waiting || !report?.duDieuKien || selected.trangThai === 'DANG_MO_BAN'} onClick={handleOpenTripSale}>
          Mở bán chuyến
        </button>
        <button disabled={waiting || selected.trangThai !== 'DANG_MO_BAN'} onClick={handleStartTrip}>
          Bắt đầu chuyến
        </button>
        <button disabled={waiting || selected.trangThai !== 'DANG_CHAY'} onClick={handleCompleteTrip}>
          Hoàn tất chuyến
        </button>
        <button
          disabled={waiting || !selected.coTheHuy}
          onClick={() => {
            setCancelReason('');
            setConfirmCancel(true);
          }}
        >
          Hủy chuyến
        </button>
      </div>
      {confirmCancel && (
        <form className={styles.form} onSubmit={handleCancelTrip}>
          <label className={styles.wide}>
            Lý do hủy chuyến
            <input
              required
              minLength={3}
              maxLength={1000}
              value={cancelReason}
              onChange={event => setCancelReason(event.target.value)}
              placeholder="Nhập lý do để thông báo và lưu lịch sử"
            />
          </label>
          <p className={`${styles.wide} ${styles.error}`}>
            Xác nhận hủy sẽ ngừng giữ chỗ/đặt vé. Khoản đã thu được đưa vào xử lý hoàn hoặc đối soát; hủy chuyến không đồng nghĩa hoàn tiền
            thành công.
          </p>
          <div className={`${styles.buttons} ${styles.wide}`}>
            <button disabled={waiting}>Xác nhận hủy chuyến</button>
            <button type="button" disabled={waiting} onClick={() => setConfirmCancel(false)}>
              Quay lại
            </button>
          </div>
        </form>
      )}
      <TripLifecyclePanel lifecycle={lifecycle} waiting={waiting} handleRetryCancellation={handleRetryCancellation} />
    </section>
  );
}
