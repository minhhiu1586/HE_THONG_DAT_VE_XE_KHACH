export function getSeatLayout(seatCount: number) {
  if (seatCount === 22) {
    return { columns: [1, 3], template: '1fr 28px 1fr', label: 'Cabin: 1 + 1' };
  }
  if (seatCount === 40) {
    return { columns: [1, 3, 5], template: '1fr 24px 1fr 24px 1fr', label: 'Giường: 1 + 1 + 1' };
  }
  return { columns: [1, 2, 4, 5], template: seatCount === 29 ? 'repeat(5, minmax(0, 1fr))' : '1fr 1fr 28px 1fr 1fr', label: 'Ghế ngồi: 2 + 2' };
}

export function getSeatPlacement(seatCount: number, index: number, floorSeatCount: number) {
  if (seatCount === 29 && floorSeatCount === 29 && index >= 24) {
    return { gridRow: 7, gridColumn: index - 23 };
  }
  const { columns } = getSeatLayout(seatCount);
  const row = Math.floor(index / columns.length) + 1;
  if (seatCount === 40 && floorSeatCount === 20 && index >= 18) {
    return { gridRow: row, gridColumn: index === 18 ? 1 : 5 };
  }
  return { gridRow: row, gridColumn: columns[index % columns.length] };
}
