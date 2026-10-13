'use client';

import Link from 'next/link';
import { useRef, useState } from 'react';
import { getTrip, getTripSeats, type SearchTrip, type TripSeat } from '@/lib/api';
import styles from './TripDetails.module.css';

function seatStatus(seat: TripSeat) {
  if (seat.trangThaiSuDung !== 'HOAT_DONG' || seat.tinhTrangKhaDung === 'KHONG_BAN') return 'blocked';
  if (seat.tinhTrangKhaDung === 'DA_BAN') return 'sold';
  if (seat.tinhTrangKhaDung === 'DANG_GIU') return 'held';
  return 'available';
}

const seatLabels = {
  available: 'Còn trống',
  held: 'Đang giữ',
  sold: 'Đã bán',
  blocked: 'Không bán',
};

export default function TripDetails({ trip: initial }: { trip: SearchTrip }) {
  const [trip, setTrip] = useState(initial);
  const [seats, setSeats] = useState<TripSeat[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState('');
  const availabilityRef = useRef<HTMLDivElement>(null);
  const seatsRef = useRef<HTMLElement>(null);
  const errorRef = useRef<HTMLParagraphElement>(null);

  async function refresh(withSeats: boolean) {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const latest = await getTrip(trip.maChuyen);
      const latestSeats = latest.coTheDatVe && (withSeats || seats !== null)
        ? (await getTripSeats(trip.maChuyen)).danhSachGhe
        : null;
      setTrip(latest);
      setSeats(latestSeats);
      setUpdatedAt(new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', second: '2-digit',
      }).format(new Date()));
      requestAnimationFrame(() => {
        const target = withSeats && latestSeats ? seatsRef.current : availabilityRef.current;
        target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        target?.focus({ preventScroll: true });
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể cập nhật chuyến.');
      requestAnimationFrame(() => errorRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    } finally {
      setBusy(false);
    }
  }

  const floors = seats
    ? [...new Set(seats.map((seat) => seat.tang))].sort((a, b) => a - b)
    : [];

  return <main className="page">
    <Link href="/trips">← Tìm chuyến khác</Link>
    <div className="page-title"><span className="eyebrow">CHI TIẾT CHUYẾN</span><h1>{trip.tenBenDi} <span>→</span> {trip.tenBenDen}</h1>
      <p>{trip.ngayKhoiHanh} · {trip.gioKhoiHanh.slice(0, 5)} · {trip.tenNhaXe ?? trip.tenTuyen}</p></div>
    <article className="trip-card"><div><strong>Điểm đi</strong><p>{trip.tenBenDi} · {trip.tenTinhDi}</p><p>{trip.diaChiBenDi}</p></div>
      <div><strong>Điểm đến</strong><p>{trip.tenBenDen} · {trip.tenTinhDen}</p><p>{trip.diaChiBenDen}</p></div>
      <div><strong>Thông tin chuyến</strong><p>Đến dự kiến: {trip.gioDenDuKien.replace('T', ' ')}</p><p>{trip.tenLoaiXe ?? 'Xe chưa cập nhật'} · {trip.soGheTrong}/{trip.tongSoGhe} ghế còn</p><p>{Number(trip.giaVeDuKien).toLocaleString('vi-VN')}đ</p></div></article>
    <section className="state"><strong>Điểm dừng trung gian</strong>{trip.diemDung.length ? <ol>{trip.diemDung.map(stop => <li key={stop.maDiemDung}>{stop.tenDiemDung} · {stop.diaChi} ({stop.thoiGianDung} phút)</li>)}</ol> : <p>Không có điểm dừng trung gian.</p>}</section>
    <div className="detail-actions"><button className="outline-button" disabled={busy} onClick={() => void refresh(false)}>{busy ? 'Đang cập nhật...' : 'Cập nhật chỗ còn'}</button>
      <button className="primary-button" disabled={busy || !trip.coTheDatVe} onClick={() => void refresh(true)}>{busy ? 'Đang tải...' : 'Xem ghế của chuyến'}</button></div>
    <div ref={availabilityRef} tabIndex={-1} className={styles.availability} role="status" aria-live="polite">
      <span className={styles.availabilityLabel}>Chỗ còn hiện tại</span>
      <strong>{trip.soGheTrong}<span> / {trip.tongSoGhe} ghế</span></strong>
      <p>{updatedAt ? `Đã cập nhật lúc ${updatedAt} (giờ Việt Nam).` : 'Nhấn “Cập nhật chỗ còn” để xem số ghế mới nhất.'}</p>
    </div>
    {!trip.coTheDatVe && <p>Chuyến không còn nhận đặt vé.</p>}
    {error && <p ref={errorRef} className="form-error" role="alert">{error}</p>}
    {seats && <section ref={seatsRef} tabIndex={-1} className={styles.seatPanel} aria-label={`Ghế của chuyến ${trip.maChuyen}`}>
      <div className={styles.seatHeader}>
        <div><span className="eyebrow">SƠ ĐỒ GHẾ</span><h2>Ghế của chuyến {trip.maChuyen}</h2><p>Vị trí và trạng thái ghế được cập nhật theo chuyến này.</p></div>
        <strong className={styles.seatCount}>{trip.soGheTrong} ghế còn</strong>
      </div>
      <div className={styles.legend} aria-label="Chú giải trạng thái ghế">
        {Object.entries(seatLabels).map(([key, label]) => <span key={key}><i className={styles[key]} aria-hidden="true" />{label}</span>)}
      </div>
      {floors.map((floor) => {
        const floorSeats = seats.filter((seat) => seat.tang === floor).sort((a, b) => a.viTri.localeCompare(b.viTri, 'vi', { numeric: true }));
        const free = floorSeats.filter((seat) => seatStatus(seat) === 'available').length;
        return <div className={styles.floor} key={floor}>
          <div className={styles.floorHeader}><h3>Tầng {floor}</h3><span>{free}/{floorSeats.length} ghế trống</span></div>
          <div className={styles.seatGrid}>{floorSeats.map((seat) => {
            const state = seatStatus(seat);
            return <div key={seat.maGhe} className={`${styles.seat} ${styles[state]}`} title={`${seat.viTri} · tầng ${seat.tang} · ${seatLabels[state]}`} aria-label={`Ghế ${seat.viTri}, tầng ${seat.tang}, ${seatLabels[state]}`}>
              <strong>{seat.viTri}</strong><small>{seatLabels[state]}</small>
            </div>;
          })}</div>
        </div>;
      })}
      {seats.length === 0 && <p>Chuyến này chưa có sơ đồ ghế.</p>}
    </section>}
  </main>;
}
