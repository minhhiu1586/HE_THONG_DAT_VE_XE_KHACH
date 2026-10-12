'use client';

import { useState } from 'react';
import { getTrip, getTripSeats, type SearchTrip, type TripSeat } from '@/lib/api';
import styles from './Search.module.css';

const money = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });

function seatLabel(seat: TripSeat) {
  if (seat.trangThaiSuDung !== 'HOAT_DONG' || seat.tinhTrangKhaDung === 'KHONG_BAN') return 'Bảo trì/không bán';
  if (seat.tinhTrangKhaDung === 'DA_BAN') return 'Đã bán';
  if (seat.tinhTrangKhaDung === 'DANG_GIU') return 'Đang giữ';
  return 'Còn trống';
}

export default function TripDetailView({ initialTrip }: { initialTrip: SearchTrip }) {
  const [trip, setTrip] = useState(initialTrip);
  const [seats, setSeats] = useState<TripSeat[] | null>(null);
  const [selectedSeats, setSelectedSeats] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const arrival = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(trip.gioDenDuKien);

  async function refresh(showSeats: boolean) {
    setBusy(true);
    setError('');
    setSeats(null);
    setSelectedSeats([]);
    try {
      const latest = await getTrip(trip.maChuyen);
      setTrip(latest);
      if (showSeats && latest.coTheDatVe) {
        setSeats((await getTripSeats(trip.maChuyen)).danhSachGhe);
      } else {
        setSeats(null);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể cập nhật trạng thái chuyến.');
    } finally { setBusy(false); }
  }

  return <article className={styles.detailPanel}>
    <h1>Chi tiết chuyến {trip.maChuyen}</h1>
    <p className={styles.info}>{trip.tenTuyen} · {trip.tenNhaXe ?? 'Nhà xe chưa cập nhật'}</p>
    <div className={styles.detailGrid}>
      <div><strong>Bến khởi hành</strong><p>{trip.tenBenDi} · {trip.tenTinhDi}</p><p>{trip.diaChiBenDi}</p></div>
      <div><strong>Bến đến</strong><p>{trip.tenBenDen} · {trip.tenTinhDen}</p><p>{trip.diaChiBenDen}</p></div>
      <div><strong>Khởi hành</strong><p>{trip.ngayKhoiHanh} lúc {trip.gioKhoiHanh.slice(0, 5)}</p></div>
      <div><strong>Đến dự kiến</strong><p>{arrival ? `${arrival[1]} lúc ${arrival[2]}` : trip.gioDenDuKien}</p></div>
      <div><strong>Thời lượng chuyến</strong><p>{Math.floor(trip.thoiLuongPhut / 60)} giờ {trip.thoiLuongPhut % 60} phút</p></div>
      <div><strong>Xe và loại xe</strong><p>{trip.tenLoaiXe ?? 'Chưa gán xe'}{trip.bienSo ? ` · ${trip.bienSo}` : ''}</p></div>
      <div><strong>Giá vé</strong><p>{money.format(Number(trip.giaVeDuKien))} ₫</p></div>
      <div><strong>Ghế</strong><p>{trip.soGheTrong} chỗ còn / {trip.tongSoGhe} ghế</p></div>
    </div>
    <section className={styles.stopsSection}><h2>Điểm dừng trung gian</h2>
      {trip.diemDung.length ? <ol>{trip.diemDung.map(stop => <li key={stop.maDiemDung}>{stop.tenDiemDung} · {stop.diaChi} ({stop.thoiGianDung} phút)</li>)}</ol> : <p>Chuyến này không có điểm dừng trung gian.</p>}
    </section>
    <p className={trip.coTheDatVe ? styles.seatInfo : styles.error}>{trip.trangThaiNhanDatVe === 'HET_CHO' ? 'Hết chỗ' : trip.coTheDatVe ? 'Đang nhận đặt vé' : 'Chuyến không còn nhận đặt vé'}</p>
    <div className={styles.cardActions}><button type="button" className={styles.selectBtn} disabled={busy} onClick={() => void refresh(false)}>Cập nhật chỗ còn</button><button type="button" className={styles.selectBtn} disabled={busy || !trip.coTheDatVe} onClick={() => void refresh(true)}>Chọn ghế</button></div>
    {busy && <p role="status">Đang cập nhật thông tin chuyến…</p>}
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {seats && <section className={styles.seatPanel} aria-live="polite"><h2>Ghế của chuyến {trip.maChuyen}</h2><div className={styles.seatGrid}>{seats.map(seat => {
      const available = seat.tinhTrangKhaDung === 'TRONG' && seat.trangThaiSuDung === 'HOAT_DONG';
      return <button type="button" key={seat.maGhe} disabled={!available} aria-pressed={selectedSeats.includes(seat.maGhe)} onClick={() => setSelectedSeats(previous => previous.includes(seat.maGhe) ? previous.filter(code => code !== seat.maGhe) : [...previous, seat.maGhe])} className={`${styles.seat} ${available ? styles.seatAvailable : ''} ${selectedSeats.includes(seat.maGhe) ? styles.seatSelected : ''}`}><strong>{seat.viTri} · tầng {seat.tang}</strong><span>{seatLabel(seat)}</span></button>;
    })}</div>{selectedSeats.length > 0 && <p>Đang chọn {selectedSeats.length} ghế: {selectedSeats.map(code => seats.find(seat => seat.maGhe === code)?.viTri ?? code).join(', ')}.</p>}</section>}
    <p className={styles.info}>Việc chọn ghế ở đây chưa giữ chỗ. Trạng thái sẽ được kiểm tra lại khi thực hiện giữ ghế.</p>
  </article>;
}
