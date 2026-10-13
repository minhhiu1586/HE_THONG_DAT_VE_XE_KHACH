'use client';

import Link from 'next/link';
import { useState } from 'react';
import { getTrip, getTripSeats, type SearchTrip, type TripSeat } from '@/lib/api';

function status(seat: TripSeat) {
  if (seat.trangThaiSuDung !== 'HOAT_DONG' || seat.tinhTrangKhaDung === 'KHONG_BAN') return 'Không bán';
  if (seat.tinhTrangKhaDung === 'DA_BAN') return 'Đã bán';
  if (seat.tinhTrangKhaDung === 'DANG_GIU') return 'Đang giữ';
  return 'Còn trống';
}

export default function TripDetails({ trip: initial }: { trip: SearchTrip }) {
  const [trip, setTrip] = useState(initial);
  const [seats, setSeats] = useState<TripSeat[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function refresh(withSeats: boolean) {
    setBusy(true);
    setError('');
    try {
      const latest = await getTrip(trip.maChuyen);
      setTrip(latest);
      setSeats(withSeats && latest.coTheDatVe ? (await getTripSeats(trip.maChuyen)).danhSachGhe : null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể cập nhật chuyến.');
    } finally { setBusy(false); }
  }

  return <main className="page">
    <Link href="/trips">← Tìm chuyến khác</Link>
    <div className="page-title"><span className="eyebrow">CHI TIẾT CHUYẾN</span><h1>{trip.tenBenDi} <span>→</span> {trip.tenBenDen}</h1>
      <p>{trip.ngayKhoiHanh} · {trip.gioKhoiHanh.slice(0, 5)} · {trip.tenNhaXe ?? trip.tenTuyen}</p></div>
    <article className="trip-card"><div><strong>Điểm đi</strong><p>{trip.tenBenDi} · {trip.tenTinhDi}</p><p>{trip.diaChiBenDi}</p></div>
      <div><strong>Điểm đến</strong><p>{trip.tenBenDen} · {trip.tenTinhDen}</p><p>{trip.diaChiBenDen}</p></div>
      <div><strong>Thông tin chuyến</strong><p>Đến dự kiến: {trip.gioDenDuKien.replace('T', ' ')}</p><p>{trip.tenLoaiXe ?? 'Xe chưa cập nhật'} · {trip.soGheTrong}/{trip.tongSoGhe} ghế còn</p><p>{Number(trip.giaVeDuKien).toLocaleString('vi-VN')}đ</p></div></article>
    <section className="state"><strong>Điểm dừng trung gian</strong>{trip.diemDung.length ? <ol>{trip.diemDung.map(stop => <li key={stop.maDiemDung}>{stop.tenDiemDung} · {stop.diaChi} ({stop.thoiGianDung} phút)</li>)}</ol> : <p>Không có điểm dừng trung gian.</p>}</section>
    <div className="detail-actions"><button className="outline-button" disabled={busy} onClick={() => void refresh(false)}>Cập nhật chỗ còn</button>
      <button className="primary-button" disabled={busy || !trip.coTheDatVe} onClick={() => void refresh(true)}>Xem ghế của chuyến</button></div>
    {!trip.coTheDatVe && <p>Chuyến không còn nhận đặt vé.</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {seats && <section className="state"><strong>Ghế của chuyến {trip.maChuyen}</strong><div className="seat-grid">{seats.map(seat => <div key={seat.maGhe} className={`seat ${status(seat) === 'Còn trống' ? '' : 'unavailable'}`} title={`${seat.viTri} · tầng ${seat.tang} · ${status(seat)}`}>{seat.viTri}<small>{status(seat)}</small></div>)}</div></section>}
  </main>;
}
