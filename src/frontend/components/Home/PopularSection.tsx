import Link from 'next/link';
import styles from './Home.module.css';
import type { PopularRoute, Station } from '@/lib/api';

export default function PopularSection({ routes, stations }: { routes: PopularRoute[]; stations: Station[] }) {
  return <div className="container">
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Tuyến đang khai thác</h2>
      <div className={styles.grid}>{routes.length ? routes.map(route => <Link href={`/tim-chuyen?${new URLSearchParams({ from: route.fromValue, to: route.toValue }).toString()}`} key={route.id} className={styles.routeCard}>
        <div className={styles.routeInfo}><h3 className={styles.routeName}>{route.from} → {route.to}</h3><span className={styles.price}>Giá tham khảo từ {route.price.toLocaleString('vi-VN')} ₫</span></div>
      </Link>) : <p className={styles.emptyData}>Chưa có tuyến đang khai thác từ máy chủ.</p>}</div>
    </section>
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Bến xe đang hoạt động</h2>
      <div className={styles.grid}>{stations.length ? stations.map(station => <Link href={`/tim-chuyen?${new URLSearchParams({ from: station.searchValue }).toString()}`} key={station.id} className={styles.stationCard}>
        <div className={styles.stationInfo}><h3 className={styles.stationName}>{station.name}</h3><p className={styles.stationCity}>{station.city}</p></div>
      </Link>) : <p className={styles.emptyData}>Chưa có bến xe đang hoạt động từ máy chủ.</p>}</div>
    </section>
  </div>;
}
