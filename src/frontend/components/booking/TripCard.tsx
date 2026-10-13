import type { TripSummary } from "@/types";

export default function TripCard({
  trip,
  onChoose,
}: {
  trip: TripSummary;
  onChoose: () => void;
}) {
  return (
    <article className="trip-card">
      <div className="trip-company">
        <div className="company-logo">NX</div>
        <div>
          <strong>{trip.maTuyen ?? "Chuyến xe"}</strong>
          <small>
            {trip.maXe ? `Xe ${trip.maXe}` : "Đang cập nhật thông tin xe"}
          </small>
        </div>
      </div>
      <div className="trip-route">
        <div>
          <strong>{trip.gioKhoiHanh.slice(0, 5)}</strong>
          <small>
            {trip.benDi?.tenBenXe ?? trip.benDi?.maBenXe ?? "Điểm đi theo tuyến"}
          </small>
        </div>
        <div className="route-line">
          <i />
          <span>{trip.ngayKhoiHanh}</span>
          <i />
        </div>
        <div>
          <strong>{trip.gioDenDuKien?.slice(0, 5) ?? "--:--"}</strong>
          <small>
            {trip.benDen?.tenBenXe ??
              trip.benDen?.maBenXe ??
              "Điểm đến theo tuyến"}
          </small>
        </div>
      </div>
      <div className="trip-price">
        <small>
          {trip.trangThai === "CHUA_KHOI_HANH"
            ? "Sẵn sàng đặt vé"
            : trip.trangThai ?? "Đang cập nhật"}
        </small>
        <strong>
          {trip.giaThamKhao
            ? `${Number(trip.giaThamKhao).toLocaleString("vi-VN")}đ`
            : "Liên hệ"}
        </strong>
        <button className="outline-button" onClick={onChoose}>
          Chọn chuyến
        </button>
      </div>
    </article>
  );
}