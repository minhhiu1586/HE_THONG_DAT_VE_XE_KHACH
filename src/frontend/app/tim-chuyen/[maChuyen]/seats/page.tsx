"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { api } from "@/services/api";
import { bookingStore, useBookingStore } from "@/store/useBookingStore";
import SeatLayout from "@/components/booking/SeatLayout";
import type { SeatMap, TripDetail } from "@/types";

export default function SeatsPage() {
  const { tripId } = useParams<{ tripId: string }>();
  const router = useRouter();
  const booking = useBookingStore();
  const [trip, setTrip] = useState<TripDetail>();
  const [map, setMap] = useState<SeatMap>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .trip(tripId)
      .then(async (detail) => {
        if (!detail.maXe) {
          throw new Error(
            "Chuyến chưa được gán xe nên chưa có sơ đồ ghế.",
          );
        }
        const seats = await api.seats(detail.maXe);
        setTrip(detail);
        setMap({
          maChuyen: tripId,
          maXe: detail.maXe,
          danhSachGhe: seats,
        });
        bookingStore.set({ trip: detail });
      })
      .catch((e) =>
        setError(
          e instanceof Error ? e.message : "Không thể tải sơ đồ ghế.",
        ),
      )
      .finally(() => setLoading(false));
  }, [tripId]);

  return (
    <main className="page">
      <div className="stepper">
        <span className="active">01 Chọn ghế</span>
        <span>02 Thông tin</span>
        <span>03 Xác nhận</span>
      </div>
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
                {trip.benDi?.tenBenXe ?? trip.benDi?.maBenXe ?? trip.maTuyen}{" "}
                <span>→</span>{" "}
                {trip.benDen?.tenBenXe ??
                  trip.benDen?.maBenXe ??
                  "Điểm đến"}
              </h1>
              <p>
                {trip.ngayKhoiHanh} · {trip.gioKhoiHanh.slice(0, 5)} ·{" "}
                {trip.nhaXe?.tenNhaXe ?? "Nhà xe"}
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
                Đã bán
              </span>
            </div>
            <SeatLayout
              seats={map.danhSachGhe}
              selected={booking.selectedSeats}
              onToggle={bookingStore.toggleSeat}
            />
          </section>
          <aside className="summary-card">
            <span className="eyebrow">VÉ CỦA BẠN</span>
            <h2>{booking.selectedSeats.length} ghế</h2>
            <p>Giá tham khảo</p>
            <strong className="total">
              {(
                booking.selectedSeats.length *
                Number(trip.giaThamKhao ?? 0)
              ).toLocaleString("vi-VN")}
              đ
            </strong>
            <button
              className="primary-button full"
              disabled={!booking.selectedSeats.length}
              onClick={() => router.push(`/${tripId}/checkout`)}
            >
              Tiếp tục <span>→</span>
            </button>
            {!booking.selectedSeats.length && (
              <small>Chọn ít nhất một ghế để tiếp tục</small>
            )}
          </aside>
        </div>
      )}
    </main>
  );
}