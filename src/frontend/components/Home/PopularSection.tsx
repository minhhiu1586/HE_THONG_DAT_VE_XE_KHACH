import Link from 'next/link';
import Image from 'next/image';
import styles from './Home.module.css'; // Thay bằng đường dẫn CSS module thực tế của bạn
import { PopularRoute, Station } from '@/lib/api'; 

// Khai báo kiểu dữ liệu mà Component này sẽ nhận vào
interface PopularSectionProps {
  routes?: PopularRoute[];
  stations?: Station[];
}

// Nhận 2 props: routes và stations
export default function PopularSection({ routes = [], stations = [] }: PopularSectionProps) {
  return (
    <div className="container">
      {/* Tuyến đường phổ biến */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Tuyến đường phổ biến</h2>
        <div className={styles.grid}>
          {/* 3. Phải dùng biến routes ở đây thay vì popularRoutes */}
          {routes.length > 0 ? (
            routes.map((route) => (
              <Link 
                href={`/tim-chuyen?from=${encodeURIComponent(route.from)}&to=${encodeURIComponent(route.to)}`} 
                key={route.id} 
                className={styles.routeCard}
              >
                <div className={styles.imageWrapper}>
                  <Image 
                    src={route.image} 
                    alt={`${route.from} đi ${route.to}`} 
                    fill
                    sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 25vw"
                    className={styles.cardImage} 
                  />
                </div>
                <div className={styles.routeInfo}>
                  <h3 className={styles.routeName}>{route.from} ➔ {route.to}</h3>
                  <div className={styles.routePricing}>
                    <span className={styles.price}>
                      Từ {route.price.toLocaleString('vi-VN')}đ
                    </span>
                    {route.originalPrice && (
                      <span className={styles.originalPrice}>
                        {route.originalPrice.toLocaleString('vi-VN')}đ
                      </span>
                    )}
                  </div>
                </div>
              </Link>
            ))
          ) : (
            <p style={{ gridColumn: '1 / -1', textAlign: 'center' }}>Chưa có dữ liệu tuyến đường từ máy chủ.</p>
          )}
        </div>
      </section>

      {/* ---------- BẾN XE NỔI BẬT ---------- */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Bến xe nổi bật</h2>
        <div className={styles.grid}>
          {stations && stations.length > 0 ? (
            stations.map((station) => (
              <Link 
                href={`/tim-chuyen?from=${encodeURIComponent(station.city)}`} 
                key={station.id} 
                className={styles.stationCard}
              >
                <div className={styles.imageWrapper}>
                  <Image 
                    src={station.image} 
                    alt={station.name} 
                    fill
                    sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 25vw"
                    className={styles.cardImage} 
                  />
                </div>
                <div className={styles.stationInfo}>
                  <h3 className={styles.stationName}>{station.name}</h3>
                  <p className={styles.stationCity}>{station.city}</p>
                </div>
              </Link>
            ))
          ) : (
            <p style={{ gridColumn: '1 / -1', textAlign: 'center' }}>Chưa có dữ liệu bến xe từ máy chủ.</p>
          )}
        </div>
      </section>
    </div>
  );
}