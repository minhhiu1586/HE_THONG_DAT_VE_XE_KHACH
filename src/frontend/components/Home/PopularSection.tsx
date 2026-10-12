import Link from 'next/link';
import styles from './Home.module.css';
import type { PopularRoute, Station } from '@/lib/api';

export default function PopularSection({ routes, stations }: { routes: PopularRoute[]; stations: Station[] }) {
  return <div className="container">
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Tuyến đường phổ biến</h2>
      <div className={styles.homeRouteList}>{routes.length ? routes.map(route => <Link href={`/tim-chuyen?${new URLSearchParams({ from: route.fromValue, to: route.toValue }).toString()}`} key={route.id} className={styles.homeRouteCard}>
        <h3 className={styles.routeName}>{route.from} ➜ {route.to}</h3>
        <span className={styles.price}>Từ {route.price.toLocaleString('vi-VN')}đ</span>
      </Link>) : <p className={styles.emptyData}>Chưa có tuyến đang khai thác từ máy chủ.</p>}</div>
    </section>
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Bến xe nổi bật</h2>
      <div className={styles.homeStationList}>{stations.length ? stations.map(station => <Link href={`/tim-chuyen?${new URLSearchParams({ from: station.searchValue }).toString()}`} key={station.id} className={styles.homeStationItem}>
        <h3 className={styles.stationName}>{station.name}</h3><p className={styles.stationCity}>{station.city}</p>
      </Link>) : <p className={styles.emptyData}>Chưa có bến xe đang hoạt động từ máy chủ.</p>}</div>
    </section>
  </div>;
}
