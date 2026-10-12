'use client';

import { useState } from 'react';
import Link from 'next/link';
import { getTripSeats, type SearchTrip, type TripSeat } from '@/lib/api';
import styles from './Search.module.css';

const money = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });
const dateFormatter = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'long', timeZone: 'Asia/Ho_Chi_Minh' });

function seatStatus(seat: TripSeat) {
  if (seat.trangThaiSuDung !== 'HOAT_DONG' || seat.tinhTrangKhaDung === 'KHONG_BAN') return 'Không bán';
  if (seat.tinhTrangKhaDung === 'DA_BAN') return 'Đã bán';
  if (seat.tinhTrangKhaDung === 'DANG_GIU') return 'Đang giữ';
  return 'Còn trống';
}

export default function TripCard({ trip }: { trip: SearchTrip }) {
  const [seats, setSeats] = useState<TripSeat[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showSeats, setShowSeats] = useState(false);
  const arrivalParts = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(trip.gioDenDuKien);
  const arrival = arrivalParts ? new Date(`${arrivalParts[1]}T12:00:00+07:00`) : null;
  const arrivalTime = arrivalParts?.[2] ?? '—';
  const arrivalDate = arrival ? dateFormatter.format(arrival) : '';

  async function toggleSeats() {
    if (showSeats) { setShowSeats(false); return; }
    setShowSeats(true);
    setLoading(true);
    setError('');
    setSeats(null);
    try {
      const response = await getTripSeats(trip.maChuyen);
      setSeats(response.danhSachGhe);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không tải được sơ đồ ghế.');
    } finally { setLoading(false); }
  }

  return <article className={styles.tripCard}>
    <div className={styles.tripTop}>
      <div className={styles.timeInfo}>
        <div className={styles.timeCol}><div className={styles.time}>{trip.gioKhoiHanh.slice(0, 5)}</div><div className={styles.station}>{trip.tenBenDi}</div><div className={styles.tripCity}>{trip.tenTinhDi}</div></div>
        <div className={styles.duration}><span>→</span> {trip.thoiLuongPhut ? `${Math.floor(trip.thoiLuongPhut / 60)} giờ${trip.thoiLuongPhut % 60 ? ` ${trip.thoiLuongPhut % 60} phút` : ''}` : 'Thời lượng chưa cập nhật'} <span>→</span></div>
        <div className={styles.timeCol}><div className={styles.time}>{arrivalTime}</div><div className={styles.station}>{trip.tenBenDen}</div><div className={styles.tripCity}>{trip.tenTinhDen}{arrivalDate ? ` · ${arrivalDate}` : ''}</div></div>
      </div>
      <div className={styles.companyInfo}><div className={styles.companyName}>{trip.tenNhaXe}</div><div className={styles.vehicleType}>{trip.tenLoaiXe} · {trip.soChoNgoi} chỗ</div><div className={styles.vehicleType}>Ngày đi: {trip.ngayKhoiHanh}</div></div>
    </div>
    <div className={styles.tripBottom}>
      <div className={styles.seatInfo}>{trip.soGheTrong ? `${trip.soGheTrong} / ${trip.tongSoGhe} chỗ còn trống` : 'Hết chỗ'}</div>
      <div className={styles.priceInfo}><span className={styles.price}>{money.format(Number(trip.giaVeDuKien))} ₫</span><div className={styles.cardActions}><Link className={styles.detailLink} href={`/tim-chuyen/${encodeURIComponent(trip.maChuyen)}`}>Xem chi tiết</Link><button type="button" className={styles.selectBtn} onClick={toggleSeats} disabled={!trip.coTheDatVe}>{showSeats ? 'Ẩn sơ đồ ghế' : 'Xem sơ đồ ghế'}</button></div></div>
    </div>
    {showSeats && <section className={styles.seatPanel} aria-live="polite"><h3>Sơ đồ ghế chuyến {trip.maChuyen}</h3>{loading && <p>Đang tải trạng thái ghế…</p>}{error && <p className={styles.error} role="alert">{error}</p>}{seats && <div className={styles.seatGrid}>{seats.map(seat => <div key={seat.maGhe} className={`${styles.seat} ${seat.tinhTrangKhaDung === 'TRONG' && seat.trangThaiSuDung === 'HOAT_DONG' ? styles.seatAvailable : ''}`} title={`${seat.maGhe} · tầng ${seat.tang} · ${seatStatus(seat)}`}><strong>{seat.viTri}</strong><span>{seatStatus(seat)}</span></div>)}</div>}</section>}
  </article>;
}
