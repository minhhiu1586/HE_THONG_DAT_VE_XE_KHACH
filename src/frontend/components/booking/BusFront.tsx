import styles from './BusFront.module.css';

export default function BusFront({ floor }: { floor: number }) {
  return <div className={styles.front} aria-label={floor === 1 ? 'Đầu xe, tài xế bên trái, cửa lên xe bên phải' : `Phía trước tầng ${floor}`}>
    <div className={styles.windshield}>
      <span className={styles.direction}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20V4m-6 6 6-6 6 6" /></svg>Hướng di chuyển</span>
      <span className={styles.wiperLeft} aria-hidden="true" /><span className={styles.wiperRight} aria-hidden="true" />
    </div>
    {floor === 1 ? <div className={styles.cockpit}>
      <div className={styles.driver}>
        <svg className={styles.wheel} viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="19" /><circle cx="24" cy="24" r="5" /><path d="M19 22 7 17m22 5 12-5M24 29v14" /></svg>
        <div><strong>Tài xế</strong><small>Khoang lái</small></div>
      </div>
      <div className={styles.entry}><svg viewBox="0 0 32 40" aria-hidden="true"><rect x="4" y="3" width="24" height="34" rx="3" /><path d="M16 3v34M8 9h4v12H8zm12 0h4v12h-4z" /></svg><span>Cửa lên xe</span></div>
    </div> : <div className={styles.upper}>Phía trước tầng {floor}</div>}
  </div>;
}
