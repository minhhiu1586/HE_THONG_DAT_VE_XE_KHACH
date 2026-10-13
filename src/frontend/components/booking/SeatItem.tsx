import type { Seat } from "../../types";

export default function SeatItem({
  seat,
  selected,
  onClick,
}: {
  seat: Seat;
  selected: boolean;
  onClick: () => void;
}) {
  const unavailable =
    (seat.trangThaiSuDung ?? seat.trangThai) !== "HOAT_DONG" ||
    (seat.tinhTrangKhaDung !== "TRONG" &&
      seat.tinhTrangKhaDung !== undefined);

  return (
    <button
      className={`seat ${selected ? "selected" : ""} ${
        unavailable ? "unavailable" : ""
      }`}
      disabled={unavailable}
      type="button"
      aria-pressed={selected}
      aria-label={`Ghế ${typeof seat.viTri === 'string' ? seat.viTri : seat.maGhe}${unavailable ? ', không khả dụng' : ''}`}
      onClick={onClick}
      title={unavailable ? "Ghế đã được chọn" : seat.maGhe}
    >
      <span>{typeof seat.viTri === 'string' ? seat.viTri : seat.maGhe}</span>
    </button>
  );
}
