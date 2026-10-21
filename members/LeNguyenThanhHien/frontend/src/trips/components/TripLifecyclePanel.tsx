import { cancellationWorkLabels, tripStatusLabels } from '../lib/trip-types';
import type { TripLifecycle } from '../lib/trip-types';
import styles from './TripManagement.module.css';

type Props = {
  lifecycle: TripLifecycle | null;
  waiting: boolean;
  handleRetryCancellation: () => void;
};

export default function TripLifecyclePanel({ lifecycle, waiting, handleRetryCancellation }: Props) {
  return (
    <>
      <h3>Khách bị ảnh hưởng và xử lý hoàn/đối soát</h3>
      {lifecycle && (
        <p>
          Vé bị ảnh hưởng: {lifecycle.tongQuanHoan.tongSoVeBiAnhHuong} · Cần hoàn: {lifecycle.tongQuanHoan.soVeCanHoan} · Vé đã hoàn:{' '}
          {lifecycle.tongQuanHoan.soVeDaHoan} · Đã hoàn: {Number(lifecycle.tongQuanHoan.tongTienDaHoan).toLocaleString('vi-VN')}đ · Yêu cầu
          thành công: {lifecycle.tongQuanHoan.soYeuCauThanhCong} · Cần theo dõi: {lifecycle.tongQuanHoan.soYeuCauCanTheoDoi}
        </p>
      )}
      {lifecycle?.khachBiAnhHuong.length ? (
        <div className={styles.scroll}>
          <table>
            <thead>
              <tr>
                <th>Mã đơn</th>
                <th>Vé còn hiệu lực</th>
                <th>Trạng thái xử lý</th>
                <th>Yêu cầu hoàn</th>
                <th>Lý do hủy</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {lifecycle.khachBiAnhHuong.map(item => (
                <tr key={item.maDon}>
                  <td>
                    {item.maDon}
                    {item.veBiAnhHuong.map(ticket => (
                      <small key={ticket.maVe}>
                        {ticket.maVe} · {ticket.trangThai}
                      </small>
                    ))}
                  </td>
                  <td>{item.soVeConHieuLuc}</td>
                  <td>
                    {cancellationWorkLabels[item.trangThaiXuLy] ?? 'Không cần hoàn'}
                    {item.lyDoXuLy && <small>{item.lyDoXuLy}</small>}
                  </td>
                  <td>
                    {item.yeuCauHoan.map(refund => (
                      <small key={refund.maHoanTien}>
                        {refund.maHoanTien} · {refund.trangThai} · {Number(refund.soTienHoan).toLocaleString('vi-VN')}đ
                        {refund.lyDoLoi ? ` · ${refund.lyDoLoi}` : ''}
                      </small>
                    ))}
                  </td>
                  <td>{item.lyDo}</td>
                  <td>
                    {['CHO_XU_LY', 'CAN_DOI_SOAT'].includes(item.trangThaiXuLy) && (
                      <button disabled={waiting} onClick={handleRetryCancellation}>
                        Tiếp tục xử lý
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p>Chưa có đơn hàng bị ảnh hưởng.</p>
      )}
      <p>Thông báo email: {lifecycle?.thongBao.map(item => `${item.trangThai}: ${item.soLuong}`).join(' · ') || 'chưa có'}.</p>
      <h3>Lịch sử vòng đời</h3>
      {lifecycle?.lyLich.length ? (
        <ul className={styles.checks}>
          {lifecycle.lyLich.map((item, index) => (
            <li key={`${item.thoiDiem}-${index}`} className={styles.passed}>
              <div>
                <strong>
                  {tripStatusLabels[item.trangThaiCu] ?? item.trangThaiCu} → {tripStatusLabels[item.trangThaiMoi] ?? item.trangThaiMoi}
                </strong>
                <small>
                  {item.maNguoiThaoTac} · {item.vaiTro} · {item.thoiDiem}
                  {item.lyDo ? ` · ${item.lyDo}` : ''}
                </small>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p>Chưa có thao tác vòng đời được ghi nhận.</p>
      )}
    </>
  );
}
