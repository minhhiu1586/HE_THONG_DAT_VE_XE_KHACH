export type Route = {
  maTuyen: string;
  tenTuyen: string;
  maNhaXe: string | null;
  giaCoBan: number;
  thoiGianDuKien: number;
  trangThai: string;
};

export type Operator = { maNhaXe: string; tenNhaXe: string };

export type Vehicle = { maXe: string; bienSo: string; maNhaXe: string; trangThai: string };

export type AssignedCrew = {
  maPhanCong: string;
  maNhanVien: string;
  hoTen: string;
  vaiTro: string;
  trangThai: string;
  ngayHetHanBangLai: string | null;
  ghiChu: string | null;
};

export type TripStatus = 'NHAP' | 'CHUA_KHOI_HANH' | 'DANG_MO_BAN' | 'DANG_CHAY' | 'HOAN_TAT' | 'DA_HUY';

export type Trip = {
  maChuyen: string;
  maTuyen: string;
  tenTuyen: string;
  maNhaXe: string;
  tenNhaXe: string;
  maXe: string | null;
  bienSo: string | null;
  ngayKhoiHanh: string;
  gioKhoiHanh: string;
  gioDenDuKien: string;
  giaVeDuKien: string;
  trangThai: TripStatus;
  ngungNhanDatVeLuc: string;
  phutDungNhanDatVe: number;
  coTheDatVe: boolean;
  daHetHanDatVe: boolean;
  coTheSua: boolean;
  coTheXoa: boolean;
  coTheHuy: boolean;
  nhanVienPhanCong: AssignedCrew[];
};

export type TripPage = { items: Trip[]; page: number; totalPages: number; totalItems: number };

export type SaleReport = {
  duDieuKien: boolean;
  ngungNhanDatVeLuc: string;
  dieuKien: { maDieuKien: string; tenDieuKien: string; dat: boolean; lyDo: string | null }[];
};

export type TripLifecycle = {
  maChuyen: string;
  trangThai: string;
  tongQuanHoan: {
    tongSoVeBiAnhHuong: number;
    soVeCanHoan: number;
    soVeDaHoan: number;
    tongTienDuKien: string;
    tongTienDaHoan: string;
    soYeuCauThanhCong: number;
    soYeuCauCanTheoDoi: number;
  };
  lyLich: { trangThaiCu: string; trangThaiMoi: string; maNguoiThaoTac: string; vaiTro: string; lyDo: string; thoiDiem: string }[];
  khachBiAnhHuong: {
    veBiAnhHuong: { maVe: string; trangThai: string; giaThucTra: string }[];
    maDon: string;
    trangThaiXuLy: string;
    lyDoXuLy: string;
    lyDo: string;
    soVeConHieuLuc: number;
    yeuCauHoan: { maHoanTien: string; trangThai: string; soTienHoan: string; lyDoLoi: string }[];
  }[];
  thongBao: { trangThai: string; soLuong: number }[];
};

export const tripStatusLabels: Record<string, string> = {
  NHAP: 'Nháp',
  CHUA_KHOI_HANH: 'Chưa mở bán (dữ liệu cũ)',
  DANG_MO_BAN: 'Đang mở bán',
  DANG_CHAY: 'Đang chạy',
  HOAN_TAT: 'Hoàn tất',
  DA_HUY: 'Đã hủy',
};

export const crewRoleLabels: Record<string, string> = {
  TAI_XE_CHINH: 'Tài xế chính',
  TAI_XE_PHU: 'Tài xế phụ',
  PHU_XE: 'Phụ xe',
  TAI_XE: 'Tài xế',
};

export const emptyTripForm = { maChuyen: '', maTuyen: '', maXe: '', ngayKhoiHanh: '', gioKhoiHanh: '', gioDenDuKien: '' };

export const TRIP_API = '/quan-ly/chuyen-xe';

export function estimateArrival(date: string, time: string, hours: number | undefined) {
  if (!date || !time || !hours) return '';
  const value = new Date(`${date}T${time}:00Z`);
  if (Number.isNaN(value.getTime())) return '';
  return new Date(value.getTime() + Math.round(hours * 60) * 60000).toISOString().slice(0, 16);
}

export function formatTripDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}

export function getTripStatusLabel(trip: Trip): string {
  if (trip.trangThai !== 'DANG_MO_BAN' || trip.coTheDatVe) return tripStatusLabels[trip.trangThai];
  if (trip.daHetHanDatVe) return 'Đã hết hạn nhận vé';
  return 'Tạm không đủ điều kiện bán';
}

export type TripFormValues = typeof emptyTripForm;
export type TripFilters = { maTuyen: string; maXe: string; trangThai: string; tuNgay: string; denNgay: string };

export function tripToForm(trip: Trip): TripFormValues {
  return {
    maChuyen: trip.maChuyen,
    maTuyen: trip.maTuyen,
    maXe: trip.maXe ?? '',
    ngayKhoiHanh: trip.ngayKhoiHanh,
    gioKhoiHanh: trip.gioKhoiHanh.slice(0, 5),
    gioDenDuKien: trip.gioDenDuKien.slice(0, 16),
  };
}

export const cancellationWorkLabels: Record<string, string> = {
  CHO_XU_LY: 'Chờ xử lý hoàn',
  CAN_DOI_SOAT: 'Cần đối soát thanh toán',
  HOAN_TAT: 'Đã xử lý',
};
