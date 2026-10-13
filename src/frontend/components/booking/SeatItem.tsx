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
    seat.trangThai !== "HOAT_DONG" ||
    (seat.tinhTrangKhaDung !== "TRONG" &&
      seat.tinhTrangKhaDung !== undefined);

  return (
    <button
      className={`seat ${selected ? "selected" : ""} ${
        unavailable ? "unavailable" : ""
      }`}
      disabled={unavailable}
      onClick={onClick}
      title={unavailable ? "Ghế đã được chọn" : seat.maGhe}
    >
      <span>{seat.maGhe}</span>
    </button>
  );
}