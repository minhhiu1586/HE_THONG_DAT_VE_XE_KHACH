export type Status = 'CHO_PHE_DUYET' | 'HOAT_DONG' | 'TAM_NGUNG' | 'NGUNG_HOAT_DONG';
export type Operator = {
  maNhaXe: string;
  tenNhaXe: string;
  hotline: string | null;
  email: string | null;
  diaChi: string | null;
  moTa: string | null;
  logoUrl: string | null;
  ngayThanhLap: string | null;
  trangThai: Status;
};
export type Manager = {
  maNguoiDung: string;
  hoTen: string;
  email: string;
  soDienThoai: string;
  trangThai: string;
};
export type Impact = {
  chuyenSapKhoiHanh: number;
  chuyenDangChay: number;
  giuChoDangGiu: number;
  veDaPhatHanh: number;
  veCoThanhToanThanhCong: number;
};
export type StateChange = {
  nhaXe: Operator;
  anhHuong: Impact;
};
export type Page<T> = {
  items: T[];
  page: number;
  totalPages: number;
  totalItems: number;
};
export const empty = {
  maNhaXe: '',
  tenNhaXe: '',
  hotline: '',
  email: '',
  diaChi: '',
  moTa: '',
  logoUrl: '',
  ngayThanhLap: '',
};
export type Form = typeof empty;
export const labels: Record<Status, string> = {
  CHO_PHE_DUYET: 'Chờ phê duyệt',
  HOAT_DONG: 'Hoạt động',
  TAM_NGUNG: 'Tạm ngưng',
  NGUNG_HOAT_DONG: 'Ngừng hoạt động',
};
export function formOf(operator: Operator): Form {
  return {
    maNhaXe: operator.maNhaXe,
    tenNhaXe: operator.tenNhaXe,
    hotline: operator.hotline ?? '',
    email: operator.email ?? '',
    diaChi: operator.diaChi ?? '',
    moTa: operator.moTa ?? '',
    logoUrl: operator.logoUrl ?? '',
    ngayThanhLap: operator.ngayThanhLap ?? '',
  };
}
