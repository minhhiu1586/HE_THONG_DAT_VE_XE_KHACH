import BusFront from './BusFront';
import type { Seat } from "@/types";

import SeatItem from "./SeatItem";
import { getSeatLayout, getSeatPlacement } from "./seat-layout";

export default function SeatLayout({
  seats,
  selected,
  onToggle,
  disabled = false,
}: {
  seats: Seat[];
  selected: string[];
  onToggle: (id: string) => void;
  disabled?: boolean;
}) {
  const layout = getSeatLayout(seats.length);
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
          <BusFront floor={Number(floor)} />
          <p>{layout.label} · {floorSeats.length} chỗ</p>
          <div className="seat-grid" style={{ gridTemplateColumns: layout.template }}>
            {floorSeats.toSorted((a, b) => String(a.viTri ?? a.maGhe).localeCompare(String(b.viTri ?? b.maGhe), 'vi', { numeric: true })).map((seat, index) => (
              <div key={seat.maGhe} style={getSeatPlacement(seats.length, index, floorSeats.length)}>
                <SeatItem
                  seat={seat}
                  disabled={disabled}
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
