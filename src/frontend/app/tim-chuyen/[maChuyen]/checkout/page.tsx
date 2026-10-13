"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";

import PassengerForm from "@/components/booking/PassengerForm";
import { api } from "@/services/api";
import { useBookingStore } from "@/store/useBookingStore";
import type { Passenger } from "@/types";

export default function CheckoutPage() {
  const { tripId } = useParams<{ tripId: string }>();
  const router = useRouter();
  const booking = useBookingStore();
  const [promo, setPromo] = useState("");
  const [promoMessage, setPromoMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const price =
    booking.selectedSeats.length * Number(booking.trip?.giaThamKhao ?? 0);

  const submit = async (passenger: Passenger) => {
    setLoading(true);
    setError("");

    try {
      const order = await api.hold({
        maChuyen: tripId,
        danhSachMaGhe: booking.selectedSeats,
      });
      await api.updateOrder(order.maDon, {
        khachDatTen: passenger.hoTen,
        khachDatSoDienThoai: passenger.soDienThoai,
        ghiChu: passenger.ghiChu || null,
      });
      await api.passengers(order.maDon, {
        hanhKhach: [
          {
            hoTen: passenger.hoTen,
            soDienThoai: passenger.soDienThoai,
            email: passenger.email,
          },
        ],
      });
      router.push(`/${tripId}/success?order=${order.maDon}`);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Không thể tạo đơn đặt vé.",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="page">
      <div className="stepper">
        <span>01 Chọn ghế</span>
        <span className="active">02 Thông tin</span>
        <span>03 Xác nhận</span>
      </div>
      <div className="checkout-layout">
        <section>
          <div className="page-title">
            <span className="eyebrow">THÔNG TIN HÀNH KHÁCH</span>
            <h1>Hoàn tất đặt vé</h1>
            <p>Thông tin dùng để gửi xác nhận và liên hệ khi cần.</p>
          </div>
          <PassengerForm onSubmit={submit} />
          {loading && (
            <div className="inline-loading">Đang giữ chỗ và tạo đơn...</div>
          )}
          {error && <div className="form-error">{error}</div>}
        </section>
        <aside className="summary-card">
          <span className="eyebrow">TÓM TẮT ĐƠN</span>
          <h3>
            {booking.trip?.benDi?.tenBenXe ??
              booking.trip?.benDi?.maBenXe ??
              booking.trip?.maTuyen}{" "}
            <span>→</span>{" "}
            {booking.trip?.benDen?.tenBenXe ??
              booking.trip?.benDen?.maBenXe ??
              "Điểm đến"}
          </h3>
          <p>
            {booking.trip?.ngayKhoiHanh} ·{" "}
            {booking.trip?.gioKhoiHanh?.slice(0, 5)}
          </p>
          <hr />
          <p>
            Ghế đã chọn:{" "}
            <strong>
              {booking.selectedSeats.join(", ") || "Chưa chọn"}
            </strong>
          </p>
          <div className="promo">
            <input
              value={promo}
              onChange={(e) => setPromo(e.target.value.toUpperCase())}
              placeholder="Mã khuyến mãi"
            />
            <button
              onClick={() =>
                setPromoMessage(
                  promo
                    ? "Mã sẽ được kiểm tra khi báo giá."
                    : "Nhập mã khuyến mãi.",
                )
              }
            >
              Áp dụng
            </button>
          </div>
          {promoMessage && <small>{promoMessage}</small>}
          <div className="total-row">
            <span>Tạm tính</span>
            <strong>{price.toLocaleString("vi-VN")}đ</strong>
          </div>
        </aside>
      </div>
    </main>
  );
}