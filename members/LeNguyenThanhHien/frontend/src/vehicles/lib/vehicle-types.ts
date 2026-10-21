export type VehicleType = { maLoaiXe: string; tenLoaiXe: string; soChoNgoi: number; soTang: number; moTa: string | null };

export type Operator = { maNhaXe: string; tenNhaXe: string; trangThai: string };

export type Seat = { maGhe: string | null; viTri: string; tang: number; trangThai: string };

export type Vehicle = {
  maXe: string;
  bienSo: string;
  maLoaiXe: string;
  maNhaXe: string;
  soChoNgoi: number;
  namSanXuat: number | null;
  ngayDangKiem: string;
  hanDangKiem: string;
  dungTichXang: number | null;
  tocDo: number | null;
  trangThai: string;
  dangKiemConHieuLuc: boolean;
  soDoHopLe: boolean;
  coTheSuaSoDo: boolean;
  soGheThucTe: number;
};

export const vehicleStatusLabels: Record<string, string> = {
  SAN_SANG: 'Sẵn sàng',
  DANG_CHAY: 'Đang chạy',
  BAO_TRI: 'Bảo trì',
  HONG: 'Hỏng',
  NGUNG_HOAT_DONG: 'Ngừng hoạt động',
};

export const seatStatusLabels: Record<string, string> = { HOAT_DONG: 'Hoạt động', BAO_TRI: 'Bảo trì', NGUNG_SU_DUNG: 'Ngừng sử dụng' };

export const emptyVehicleTypeForm = { maLoaiXe: '', tenLoaiXe: '', soChoNgoi: '', soTang: '1', moTa: '' };

export const emptyVehicleForm = {
  maXe: '',
  bienSo: '',
  maLoaiXe: '',
  maNhaXe: '',
  namSanXuat: '',
  ngayDangKiem: '',
  hanDangKiem: '',
  dungTichXang: '',
  tocDo: '',
  trangThai: 'SAN_SANG',
};

export type VehicleTypeForm = typeof emptyVehicleTypeForm;
export type VehicleFormValues = typeof emptyVehicleForm;

export function vehicleToForm(vehicle: Vehicle): VehicleFormValues {
  return {
    maXe: vehicle.maXe,
    bienSo: vehicle.bienSo,
    maLoaiXe: vehicle.maLoaiXe,
    maNhaXe: vehicle.maNhaXe,
    ngayDangKiem: vehicle.ngayDangKiem ?? '',
    hanDangKiem: vehicle.hanDangKiem ?? '',
    trangThai: vehicle.trangThai,
    namSanXuat: vehicle.namSanXuat?.toString() ?? '',
    dungTichXang: vehicle.dungTichXang?.toString() ?? '',
    tocDo: vehicle.tocDo?.toString() ?? '',
  };
}

export function vehicleTypeToForm(type: VehicleType): VehicleTypeForm {
  return { ...type, soChoNgoi: String(type.soChoNgoi), soTang: String(type.soTang), moTa: type.moTa ?? '' };
}
