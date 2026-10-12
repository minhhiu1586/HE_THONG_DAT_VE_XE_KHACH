import Link from 'next/link';
import { getTrip } from '@/lib/api';
import TripDetailView from '@/components/Search/TripDetailView';
import styles from '@/components/Search/Search.module.css';

export default async function TripDetailPage({ params }: { params: Promise<{ maChuyen: string }> }) {
  const { maChuyen } = await params;
  let trip;
  let message = '';
  try {
    trip = await getTrip(maChuyen);
  } catch (cause) {
    message = cause instanceof Error ? cause.message : 'Không thể tải chuyến xe.';
  }
  return <main className={styles.searchPage}><div className="container"><Link href="/tim-chuyen">← Tìm chuyến khác</Link>{trip ? <TripDetailView key={trip.maChuyen} initialTrip={trip} /> : <><h1>Không thể xem chi tiết chuyến</h1><p className={styles.error} role="alert">{message}</p></>}</div></main>;
}
