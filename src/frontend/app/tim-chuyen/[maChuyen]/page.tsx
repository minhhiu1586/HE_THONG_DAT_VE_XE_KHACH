import Link from 'next/link';
import { getTrip } from '@/lib/api';
import TripDetails from '@/components/booking/TripDetails';

export default async function TripPage({ params }: { params: Promise<{ maChuyen: string }> }) {
  const { maChuyen } = await params;
  let trip;
  let error = '';
  try {
    trip = await getTrip(maChuyen);
  } catch (cause) {
    error = cause instanceof Error ? cause.message : 'Chuyến không tồn tại.';
  }
  return trip ? <TripDetails trip={trip} /> : <main className="page"><Link href="/trips">← Tìm chuyến khác</Link><div className="state error-state"><strong>Không thể tải chuyến</strong><p>{error}</p></div></main>;
}
