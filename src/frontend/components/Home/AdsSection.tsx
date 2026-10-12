import type { ActivePromotion } from '@/lib/api';
import styles from './Home.module.css';

export default function AdsSection({ promotions }: { promotions: ActivePromotion[] }) {
  return <div className="container" style={{ marginTop: '2rem' }}>
    <section className={styles.sectionBlock}>
      <h2 className="section-title">Ưu đãi nổi bật</h2>
      {promotions.length ? <div className={styles.homePromotionGrid}>{promotions.map((promotion, index) => <article key={promotion.maKhuyenMai} className={styles.homePromotionCard}>
        <div className={styles.homePromotionArtwork} aria-hidden="true">{['🎟️', '⚡', '🆕', '🎁'][index % 4]}</div>
        <div className={styles.stationInfo}><h3 className={styles.stationName}>{promotion.tenKhuyenMai}</h3>
          {promotion.moTa && <p className={styles.stationCity}>{promotion.moTa}</p>}
          <p className={styles.stationCity}>Mã: {promotion.maKhuyenMai}</p>
          <p className={styles.stationCity}>{promotion.loaiGiam === 'PHAN_TRAM' ? `Giảm ${promotion.giaTriGiam}%` : `Giảm ${Number(promotion.giaTriGiam).toLocaleString('vi-VN')} ₫`}{promotion.giamToiDa ? ` · tối đa ${Number(promotion.giamToiDa).toLocaleString('vi-VN')} ₫` : ''}</p>
          <p className={styles.stationCity}>Từ {promotion.ngayBatDau} đến {promotion.ngayKetThuc}</p>
        </div>
      </article>)}</div> : <p className={styles.emptyData}>Hiện không có khuyến mãi nào đang áp dụng.</p>}
    </section>
  </div>;
}
