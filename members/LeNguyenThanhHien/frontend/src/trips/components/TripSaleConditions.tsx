import { crewRoleLabels } from '../lib/trip-types';
import type { SaleReport, Trip } from '../lib/trip-types';
import styles from './TripManagement.module.css';

type Props = {
  selected: Trip;
  report: SaleReport | null;
};

export default function TripSaleConditions({ selected, report }: Props) {
  return (
    <>
      <h3>Nhân viên đã phân công</h3>
      <div className={styles.scroll}>
        <table>
          <thead>
            <tr>
              <th>Họ tên</th>
              <th>Vai trò</th>
              <th>Trạng thái</th>
              <th>Hạn bằng lái</th>
            </tr>
          </thead>
          <tbody>
            {selected.nhanVienPhanCong.map(c => (
              <tr key={c.maPhanCong}>
                <td>{c.hoTen}</td>
                <td>{crewRoleLabels[c.vaiTro] ?? c.vaiTro}</td>
                <td>{c.trangThai === 'DANG_LAM_VIEC' ? 'Đang làm việc' : c.trangThai}</td>
                <td>{c.ngayHetHanBangLai ?? 'Không áp dụng'}</td>
              </tr>
            ))}
            {selected.nhanVienPhanCong.length === 0 && (
              <tr>
                <td colSpan={4}>Chưa có nhân viên được phân công. Cần hoàn tất phân công trước khi mở bán.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <h3>Điều kiện mở bán</h3>
      {report && (
        <ul className={styles.checks}>
          {report.dieuKien.map(check => (
            <li key={check.maDieuKien} className={check.dat ? styles.passed : styles.failed}>
              <span aria-hidden="true">{check.dat ? '✓' : '✕'}</span>
              <div>
                <strong>{check.tenDieuKien}</strong>
                <small>{check.dat ? 'Đạt' : check.lyDo}</small>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
