import Image from 'next/image';
import type { SearchTrip } from '@/lib/api';

const operatorLogos: Record<string, string> = {
  NX_FUTA: '/operators/futa.jpg', NX_TB: '/operators/thanh-buoi.jpg',
  NX_KUMHO: '/operators/kumho.png', NX_HL: '/operators/hoang-long.png', NX_CT: '/operators/cuc-tung.png',
};

export default function TripCard({ trip, onChoose }: { trip: SearchTrip; onChoose: () => void }) {
  const company = trip.tenNhaXe ?? trip.tenTuyen;
  const logo = trip.maNhaXe ? operatorLogos[trip.maNhaXe] : undefined;
  const date = trip.ngayKhoiHanh.split('-').reverse().join('/');
  const minutes = Math.round(Number(trip.thoiLuongPhut));
  const duration = Number.isFinite(minutes) && minutes > 0
    ? [Math.floor(minutes / 60) ? `${Math.floor(minutes / 60)} giờ` : '', minutes % 60 ? `${minutes % 60} phút` : ''].filter(Boolean).join(' ')
    : 'Đang cập nhật';
  const arrivalDate = trip.gioDenDuKien.slice(0, 10);
  const dayOffset = Math.round((Date.parse(arrivalDate + 'T00:00:00Z') - Date.parse(trip.ngayKhoiHanh + 'T00:00:00Z')) / 86400000);
  const available = trip.trangThaiNhanDatVe === 'CON_CHO' && trip.soGheTrong > 0;
  const availability = trip.trangThaiNhanDatVe === 'NGUNG_NHAN_DAT' ? 'Đã ngừng nhận vé' : available ? `Còn ${trip.soGheTrong} ghế` : 'Hết chỗ';
  return <article className="trip-card trip-card-refined" aria-label={`${company}: ${trip.tenBenDi} đến ${trip.tenBenDen}`}>
    <header className="trip-card-header">
      <div className="trip-operator">
        <div className="trip-operator-logo">{logo ? <Image src={logo} alt="" width={48} height={48} sizes="48px" /> : <span>NX</span>}</div>
        <div className="trip-operator-info"><h2>{company}</h2>
          <div className="trip-card-meta"><span>{trip.tenLoaiXe ?? `${trip.soChoNgoi} chỗ`}</span><time dateTime={trip.ngayKhoiHanh}>{date}</time>{trip.bienSo && <span>{trip.bienSo}</span>}</div>
          <div className="trip-card-meta"><span>Mã chuyến: {trip.maChuyen}</span></div>
        </div>
      </div>
      <span className={available ? 'trip-availability available' : 'trip-availability'}>{availability}</span>
    </header>
    <div className="trip-card-body">
      <div className="trip-route-panel">
        <div className="trip-endpoint"><span className="trip-endpoint-label">Khởi hành</span><strong>{trip.gioKhoiHanh.slice(0, 5)}</strong><span className="trip-station">{trip.tenBenDi}</span></div>
        <div className="trip-duration"><span>{duration}</span><div className="trip-timeline" aria-hidden="true"><i /><b /><i /></div><small>Thời gian dự kiến</small></div>
        <div className="trip-endpoint trip-arrival"><span className="trip-endpoint-label">Đến dự kiến</span><strong>{trip.gioDenDuKien.slice(11, 16)}{dayOffset > 0 && <small>+{dayOffset} ngày</small>}</strong><span className="trip-station">{trip.tenBenDen}</span></div>
      </div>
      <div className="trip-fare-panel"><div><span className="trip-fare-label">Giá vé</span><p><strong>{Number(trip.giaVeDuKien).toLocaleString('vi-VN')}đ</strong><small>/ vé</small></p></div>
        <button className="primary-button trip-details-button" type="button" onClick={onChoose}>Xem chi tiết</button>
      </div>
    </div>
  </article>;
}
