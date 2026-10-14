'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useRef, useState } from 'react';
import { getTrip, getTripSeats, type SearchTrip, type TripSeat } from '@/lib/api';
import styles from './TripDetails.module.css';
import PublicReviews from '@/components/Reviews/PublicReviews';
import BusFront from './BusFront';
import { getSeatLayout, getSeatPlacement } from './seat-layout';

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
  const router = useRouter();
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

  const layout = getSeatLayout(seats?.length ?? 0);
  const floors = seats
    ? [...new Set(seats.map((seat) => seat.tang))].sort((a, b) => a - b)
    : [];

  const formatDate = (value: string) => value.slice(0, 10).split('-').reverse().join('/');
  const minutes = Math.round(Number(trip.thoiLuongPhut));
  const duration = Number.isFinite(minutes) && minutes > 0 ? [Math.floor(minutes / 60) ? Math.floor(minutes / 60) + ' giờ' : '', minutes % 60 ? minutes % 60 + ' phút' : ''].filter(Boolean).join(' ') : 'Đang cập nhật';

  return <main className={`page ${styles.detailPage}`}>
    <Link className={styles.backLink} href="/trips">← Tìm chuyến khác</Link>
    <header className={styles.intro}><span className="eyebrow">CHI TIẾT CHUYẾN</span>
      <h1>{trip.tenTinhDi} <span aria-hidden="true">→</span> {trip.tenTinhDen}</h1>
      <p>{trip.tenNhaXe ?? trip.tenTuyen} · {trip.tenLoaiXe ?? 'Thông tin xe đang cập nhật'}</p>
    </header>
    <div className={styles.detailLayout}>
      <div className={styles.detailContent}>
        <article className={styles.journeyCard}>
          <div className={styles.journeyHeader}><h2>Thông tin hành trình</h2><span>{formatDate(trip.ngayKhoiHanh)}</span></div>
          <div className={styles.journeyTimes}>
            <div><span>Khởi hành</span><strong>{trip.gioKhoiHanh.slice(0, 5)}</strong><time dateTime={trip.ngayKhoiHanh}>{formatDate(trip.ngayKhoiHanh)}</time></div>
            <div className={styles.journeyDuration}><span>{duration}</span><div aria-hidden="true">○ ───── ○</div><small>Thời gian dự kiến</small></div>
            <div><span>Đến dự kiến</span><strong>{trip.gioDenDuKien.slice(11, 16)}</strong><time dateTime={trip.gioDenDuKien}>{formatDate(trip.gioDenDuKien)}</time></div>
          </div>
          <div className={styles.stationGrid}>
            <section><span>Điểm đi</span><h3>{trip.tenBenDi}</h3><p>{trip.diaChiBenDi}</p></section>
            <section><span>Điểm đến</span><h3>{trip.tenBenDen}</h3><p>{trip.diaChiBenDen}</p></section>
          </div>
          <div className={styles.vehicleInfo}><span>{trip.tenLoaiXe ?? 'Xe đang cập nhật'}</span>{trip.bienSo && <span>Biển số: {trip.bienSo}</span>}</div>
        </article>
        <section className={styles.stops}><h2>Hành trình và điểm dừng</h2>
          {trip.diemDung.length ? <ol>{[...trip.diemDung].sort((a, b) => a.thuTu - b.thuTu).map((stop, index) => <li key={stop.maDiemDung}>
            <span className={styles.stopNumber}>{index + 1}</span><div className={styles.stopInfo}><strong>{stop.tenDiemDung}</strong><p>{stop.diaChi}</p></div>
            <span className={styles.stopDuration}>{stop.thoiGianDung} phút dừng</span>
          </li>)}</ol> : <p>Không có điểm dừng trung gian.</p>}
        </section>
      </div>
      <aside className={styles.bookingPanel} aria-label="Giá vé và đặt chỗ">
        <span className={styles.fareLabel}>Giá vé dự kiến</span><div className={styles.detailFare}><strong>{Number(trip.giaVeDuKien).toLocaleString('vi-VN')}đ</strong><span>/ vé</span></div>
        <p className={styles.operatorName}>{trip.tenNhaXe ?? trip.tenTuyen}</p>
        <div ref={availabilityRef} tabIndex={-1} className={styles.availability} role="status" aria-live="polite">
          <span className={styles.availabilityLabel}>Chỗ còn hiện tại</span><strong>{trip.soGheTrong}<span> / {trip.tongSoGhe} ghế</span></strong>
          <p>{updatedAt ? `Đã cập nhật lúc ${updatedAt} (giờ Việt Nam).` : 'Cập nhật chỗ còn trước khi chọn ghế.'}</p>
        </div>
        <div className={styles.bookingControls}><div className={styles.bookingAction}>{trip.coTheDatVe ? <Link className="primary-button" href={`/tim-chuyen/${encodeURIComponent(trip.maChuyen)}/seats`}>Chọn ghế và đặt vé</Link> : <p>Chuyến không còn nhận đặt vé.</p>}</div>
        <div className={styles.panelActions}><button className="outline-button" disabled={busy} onClick={() => void refresh(false)}>{busy ? 'Đang cập nhật…' : 'Cập nhật chỗ còn'}</button>
          <button className="outline-button" disabled={busy || !trip.coTheDatVe} onClick={() => void refresh(true)}>{busy ? 'Đang tải…' : 'Xem sơ đồ ghế'}</button></div></div>
      </aside>
    </div>
    {error && <p ref={errorRef} className="form-error" role="alert">{error}</p>}
    {seats && <section ref={seatsRef} tabIndex={-1} className={styles.seatPanel} aria-label={`Ghế của chuyến ${trip.maChuyen}`}>
      <div className={styles.seatHeader}>
        <div><span className="eyebrow">SƠ ĐỒ GHẾ</span><h2>Sơ đồ ghế của chuyến</h2><p>Vị trí và trạng thái ghế được cập nhật theo chuyến này.</p></div>
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
          <BusFront floor={Number(floor)} />
          <p className={styles.layoutCaption}>{layout.label} · {floorSeats.length} chỗ</p>
          <div className={styles.seatGrid} style={{ gridTemplateColumns: layout.template }}>{floorSeats.map((seat, index) => {
            const state = seatStatus(seat);
            return <button type="button" disabled={state !== 'available' || !trip.coTheDatVe} onClick={() => router.push(`/tim-chuyen/${trip.maChuyen}/seats`)} key={seat.maGhe} style={getSeatPlacement(seats.length, index, floorSeats.length)} className={`${styles.seat} ${styles[state]}`} title={`${seat.viTri} · tầng ${seat.tang} · ${seatLabels[state]}`} aria-label={`Ghế ${seat.viTri}, tầng ${seat.tang}, ${seatLabels[state]}`}>
              <strong>{seat.viTri}</strong><small>{seatLabels[state]}</small>
            </button>;
          })}</div>
        </div>;
      })}
      {seats.length === 0 && <p>Chuyến này chưa có sơ đồ ghế.</p>}
    </section>}
    <PublicReviews maChuyen={trip.maChuyen}/>
  </main>;
}
