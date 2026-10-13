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
          <div className="seat-grid">
            {floorSeats.map((seat) => (
              <SeatItem
                key={seat.maGhe}
                seat={seat}
                selected={selected.includes(seat.maGhe)}
                onClick={() => onToggle(seat.maGhe)}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}