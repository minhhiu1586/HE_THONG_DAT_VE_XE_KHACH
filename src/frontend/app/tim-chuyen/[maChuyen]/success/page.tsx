"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";

export default function SuccessPage() {
  const order = useSearchParams().get("order");

  return (
    <main className="page success-page">
      <div className="success-icon">✓</div>
      <span className="eyebrow">ĐẶT VÉ THÀNH CÔNG</span>
      <h1>Cảm ơn bạn đã đặt vé!</h1>
      <p>
        Thông tin đặt vé đã được ghi nhận. Vui lòng lưu lại mã đơn để tra cứu.
      </p>
      <div className="order-code">
        <small>MÃ ĐƠN ĐẶT VÉ</small>
        <strong>{order ?? "ĐANG CẬP NHẬT"}</strong>
      </div>
      <Link className="primary-button" href="/home">
        Về trang chủ <span>→</span>
      </Link>
    </main>
  );
}