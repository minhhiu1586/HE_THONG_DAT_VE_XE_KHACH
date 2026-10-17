"use client";

import { FormEvent, useState } from "react";

import type { Passenger } from "@shared/types/index";

export default function PassengerForm({
  onSubmit,
}: {
  onSubmit: (passenger: Passenger) => void;
}) {
  const [value, setValue] = useState<Passenger>({
    hoTen: "",
    soDienThoai: "",
    email: "",
    ghiChu: "",
  });
  const [error, setError] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!value.hoTen.trim() || !/^0\d{9,10}$/.test(value.soDienThoai)) {
      setError("Vui lòng nhập họ tên và số điện thoại hợp lệ.");
      return;
    }
    setError("");
    onSubmit(value);
  };

  return (
    <form className="passenger-form" onSubmit={submit}>
      <label>
        Họ và tên
        <input
          required
          value={value.hoTen}
          onChange={(e) => setValue({ ...value, hoTen: e.target.value })}
          placeholder="Nguyễn Văn A"
        />
      </label>
      <label>
        Số điện thoại
        <input
          required
          inputMode="tel"
          value={value.soDienThoai}
          onChange={(e) => setValue({ ...value, soDienThoai: e.target.value })}
          placeholder="0901234567"
        />
      </label>
      <label>
        Email
        <input
          type="email"
          value={value.email}
          onChange={(e) => setValue({ ...value, email: e.target.value })}
          placeholder="email@example.com"
        />
      </label>
      <label>
        Ghi chú
        <textarea
          value={value.ghiChu}
          onChange={(e) => setValue({ ...value, ghiChu: e.target.value })}
          placeholder="Yêu cầu thêm (nếu có)"
        />
      </label>
      {error && <p className="field-error">{error}</p>}
      <button className="primary-button" type="submit">
        Tiếp tục đặt vé <span>→</span>
      </button>
    </form>
  );
}