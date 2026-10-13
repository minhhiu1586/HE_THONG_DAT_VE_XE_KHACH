import type { SearchTrip } from "@/lib/api";

export default function TripCard({
  trip,
  onChoose,
}: {
  trip: SearchTrip;
  onChoose: () => void;
}) {
  return (
    <article className="trip-card">
      <div className="trip-company">
        <div className="company-logo">NX</div>
        <div>
          <strong>{trip.tenNhaXe ?? trip.tenTuyen}</strong>
          <small>
            {trip.maXe ? `Xe ${trip.maXe}` : "Đang cập nhật thông tin xe"}
          </small>
        </div>
      </div>
      <div className="trip-route">
        <div>
          <strong>{trip.gioKhoiHanh.slice(0, 5)}</strong>
          <small>
            {trip.tenBenDi}
          </small>
        </div>
        <div className="route-line">
          <i />
          <span>{trip.ngayKhoiHanh}</span>
          <i />
        </div>
        <div>
          <strong>{trip.gioDenDuKien.slice(11, 16)}</strong>
          <small>
            {trip.tenBenDen}
          </small>
        </div>
      </div>
      <div className="trip-price">
        <small>
          {trip.trangThaiNhanDatVe === 'HET_CHO' ? 'Hết chỗ' : `${trip.soGheTrong} ghế còn`}
        </small>
        <strong>
          {`${Number(trip.giaVeDuKien).toLocaleString("vi-VN")}đ`}
        </strong>
        <button className="outline-button" onClick={onChoose}>
          Xem chi tiết
        </button>
      </div>
    </article>
  );
}
