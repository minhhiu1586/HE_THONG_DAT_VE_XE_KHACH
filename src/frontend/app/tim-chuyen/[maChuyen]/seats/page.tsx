"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";

import { getTrip, getTripSeats, type SearchTrip, type HoldSeatResponse } from "@/lib/api";
import { useAuth } from '@/components/Account/AuthProvider';
import { bookingStore, useBookingStore } from "@/store/useBookingStore";
import SeatLayout from "@/components/booking/SeatLayout";
import type { SeatMap } from "@/types";

export default function SeatsPage() {
  const { maChuyen: tripId } = useParams<{ maChuyen: string }>();
  const { user, request } = useAuth();
  const router = useRouter();
  const booking = useBookingStore();
  const [trip, setTrip] = useState<SearchTrip>();
  const [map, setMap] = useState<SeatMap>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  // State mới để khóa nút bấm khi đang gọi API giữ ghế
  const [isHolding, setIsHolding] = useState(false);
  const holdAttempt = useRef<{ body: string; key: string } | null>(null);

 useEffect(() => {
    getTrip(tripId) // Sửa api.trip thành getTrip
      .then(async (detail) => {
        if (!detail.maXe) {
          throw new Error("Chuyến chưa được gán xe nên chưa có sơ đồ ghế.");
        }
        const seatResponse = await getTripSeats(tripId);
        setTrip(detail);
        setMap({
          maChuyen: tripId,
          maXe: detail.maXe,
          danhSachGhe: seatResponse.danhSachGhe,
        });
        if (bookingStore.get().trip?.maChuyen !== tripId) bookingStore.clear();
        bookingStore.set({ trip: detail });
      })
      .catch((e) =>
        setError(e instanceof Error ? e.message : "Không thể tải sơ đồ ghế.")
      )
      .finally(() => setLoading(false));
  }, [tripId]);

  // Hàm xử lý giữ ghế trước khi sang trang Checkout
  const handleContinue = async () => {
    if (booking.selectedSeats.length === 0) return;
    if (!user) { router.push(`/dang-nhap?next=${encodeURIComponent(`/tim-chuyen/${tripId}/seats`)}`); return; }

    setIsHolding(true);
    try {
      // Gọi trực tiếp hàm holdSeats
      const body = {
        maChuyen: tripId,
        danhSachMaGhe: booking.selectedSeats,
      };
      const fingerprint = JSON.stringify(body);
      if (holdAttempt.current?.body !== fingerprint) holdAttempt.current = { body: fingerprint, key: crypto.randomUUID() };
      const response = await request<HoldSeatResponse>('/booking/hold', 'POST', body, { 'Idempotency-Key': holdAttempt.current.key });

      // Lưu mã đơn và thời gian hết hạn vào Store
      bookingStore.setHoldSession(response.maDon, response.thoiDiemHetHan);

      // Chuyển sang trang Checkout
      router.push(`/tim-chuyen/${tripId}/checkout`);
    } catch (e) {
      alert(e instanceof Error ? e.message : "Ghế đã bị người khác chọn. Vui lòng chọn ghế khác!");
      if (map?.maXe) {
        getTripSeats(tripId).then(res => { setMap({ ...map, danhSachGhe: res.danhSachGhe }); bookingStore.set({ selectedSeats: [] }); }).catch(() => setError('Không thể cập nhật ghế. Vui lòng tải lại trang.'));
      }
    } finally {
      setIsHolding(false);
    }
  };

  return (
    <main className="page seat-selection-page">
      <Link className="seat-back-link" href={`/tim-chuyen/${encodeURIComponent(tripId)}`}>← Chi tiết chuyến</Link>
      <ol className="seat-steps" aria-label="Các bước đặt vé">
        <li className="active" aria-current="step"><span>1</span><strong>Chọn ghế</strong></li>
        <li><span>2</span><strong>Thông tin hành khách</strong></li>
        <li><span>3</span><strong>Xác nhận</strong></li>
      </ol>
      {loading && (
        <div className="state">Đang tải chuyến và sơ đồ ghế...</div>
      )}
      {error && (
        <div className="state error-state">
          <strong>Không thể tải sơ đồ ghế</strong>
          <p>{error}</p>
          <button onClick={() => location.reload()}>Thử lại</button>
        </div>
      )}
      {!loading && !error && trip && map && (
        <div className="seat-page">
          <section>
            <div className="page-title">
              <span className="eyebrow">CHI TIẾT CHUYẾN</span>
              <h1>
                {trip.tenBenDi}{" "}
                <span>→</span>{" "}
                {trip.tenBenDen}
              </h1>
              <p>
                {trip.ngayKhoiHanh.split('-').reverse().join('/')} · {trip.gioKhoiHanh.slice(0, 5)} ·{" "}
                {trip.tenNhaXe ?? "Nhà xe"}
              </p>
            </div>
            <div className="legend">
              <span>
                <i className="seat-demo" />
                Còn trống
              </span>
              <span>
                <i className="seat-demo selected" />
                Đang chọn
              </span>
              <span>
                <i className="seat-demo unavailable" />
                Không khả dụng
              </span>
            </div>
            <SeatLayout
              seats={map.danhSachGhe}
              selected={booking.selectedSeats}
              onToggle={id => { if (trip.coTheDatVe && !isHolding) bookingStore.toggleSeat(id); }}
            />
          </section>
          <aside className="summary-card seat-ticket-summary" aria-label="Vé của bạn">
            <span className="eyebrow">VÉ CỦA BẠN</span>
            <h2>{booking.selectedSeats.length ? booking.selectedSeats.length + ' ghế đã chọn' : 'Chưa chọn ghế'}</h2>
            <div className="ticket-selected-seats" aria-label="Ghế đã chọn">
              {booking.selectedSeats.length ? booking.selectedSeats.map(id => {
                const seat = map.danhSachGhe.find(item => item.maGhe === id);
                return <span key={id}>{typeof seat?.viTri === 'string' ? seat.viTri : id}</span>;
              }) : <p>Chọn ghế trống trên sơ đồ bên cạnh.</p>}
            </div>
            <div className="ticket-price-row"><span>Giá mỗi ghế</span><strong>{Number(trip.giaVeDuKien ?? 0).toLocaleString('vi-VN')}đ</strong></div>
            <div className="ticket-total"><p>Tổng tham khảo</p><strong className="total">{(booking.selectedSeats.length * Number(trip.giaVeDuKien ?? 0)).toLocaleString('vi-VN')}đ</strong></div>
            <button className="primary-button full" type="button" disabled={!booking.selectedSeats.length || isHolding || !trip.coTheDatVe} onClick={handleContinue}>
              {isHolding ? 'Đang giữ ghế…' : 'Giữ ghế và tiếp tục'}
            </button>
            <small>{!trip.coTheDatVe ? 'Chuyến không còn nhận đặt vé.' : !booking.selectedSeats.length ? 'Chọn ít nhất một ghế để tiếp tục.' : 'Giá cuối cùng được xác nhận theo đơn đặt vé.'}</small>
          </aside>
        </div>
      )}
    </main>
  );
}
