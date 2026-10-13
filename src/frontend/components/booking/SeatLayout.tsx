import type { Seat } from "@/types";

import SeatItem from "./SeatItem";

export default function SeatLayout({
  seats,
  selected,
  onToggle,
}: {
  seats: Seat[];
  selected: string[];
  onToggle: (id: string) => void;
}) {
  const groups = seats.reduce<Record<string, Seat[]>>((result, seat) => {
    const floor = String(
      seat.tang ??
        (typeof seat.viTri === "object" ? seat.viTri.tang : undefined) ??
        1,
    );
    (result[floor] ??= []).push(seat);
    return result;
  }, {});

  return (
    <div className="seat-map">
      {Object.entries(groups).map(([floor, floorSeats]) => (
        <div className="floor" key={floor}>
          <h3>Tầng {floor}</h3>
          {floor === '1' && <div className="driver-position" aria-label="Vị trí tài xế, không bán vé"><span aria-hidden="true">◉</span><div><strong>Tài xế</strong><small>Không bán vé · không chọn</small></div></div>}
          <p>Đầu xe ↑ · {seats.length === 22 ? 'Cabin: 1 + 1' : seats.length === 40 ? 'Giường: 2 + 1' : 'Ghế ngồi: 2 + 2'} · {floorSeats.length} chỗ</p>
          <div className="seat-grid" style={{gridTemplateColumns: seats.length === 22 ? '1fr 28px 1fr' : seats.length === 40 ? '1fr 1fr 28px 1fr' : '1fr 1fr 28px 1fr 1fr'}}>
            {floorSeats.toSorted((a,b) => String(a.viTri ?? a.maGhe).localeCompare(String(b.viTri ?? b.maGhe),'vi',{numeric:true})).map((seat, index) => (
              <div key={seat.maGhe} style={{gridColumn: seats.length === 22 ? (index % 2 === 0 ? 1 : 3) : seats.length === 40 ? [1,2,4][index % 3] : [1,2,4,5][index % 4]}}>
              <SeatItem
                seat={seat}
                selected={selected.includes(seat.maGhe)}
                onClick={() => onToggle(seat.maGhe)}
              />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
