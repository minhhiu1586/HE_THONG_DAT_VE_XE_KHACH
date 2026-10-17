export type Employee = {
  maNhanVien: string; hoTen: string; soDienThoai: string; email: string;
  ngaySinh: string | null; maNhaXe: string | null; loaiNhanVien: string; trangThai: string;
  soBangLai: string | null; ngayHetHanBangLai: string | null;
};
export type Assignment = {
  maPhanCong: string; maChuyen: string; maNhanVien: string; vaiTro: string; ghiChu: string | null;
};
export type Schedule = Assignment & {
  tenTuyen: string; ngayKhoiHanh: string; gioKhoiHanh: string; gioDenDuKien: string; trangThai: string;
};
export type Trip = { maChuyen: string; maTuyen: string; maXe: string | null; ngayKhoiHanh: string; gioKhoiHanh: string; gioDenDuKien: string; trangThai: string };
export type Operator = { maNhaXe: string; tenNhaXe: string };
export type Route = { maTuyen: string; tenTuyen: string; maNhaXe: string };
export const roles: Record<string, string> = { TAI_XE: 'Tài xế', PHU_XE: 'Phụ xe', DIEU_HANH: 'Điều hành' };
export const states: Record<string, string> = { DANG_LAM_VIEC: 'Đang làm việc', TAM_NGHI: 'Tạm nghỉ', DA_NGHI_VIEC: 'Đã nghỉ việc' };
export const tripRoles: Record<string, string> = { TAI_XE_CHINH: 'Tài xế chính', TAI_XE_PHU: 'Tài xế phụ', PHU_XE: 'Phụ xe' };
export const tripStates: Record<string, string> = { NHAP: 'Nháp', DANG_MO_BAN: 'Đang mở bán', CHUA_KHOI_HANH: 'Chưa khởi hành', DANG_CHAY: 'Đang chạy', HOAN_TAT: 'Hoàn tất', DA_HUY: 'Đã hủy' };
export function canAssign(state: string) { return ['NHAP', 'CHUA_KHOI_HANH', 'DANG_MO_BAN'].includes(state); }
export function noteText(note: string | null) { return (note ?? '').replace(/^\[T11_PLAN:[^\]]*\]\s*/, ''); }

// Giữ nguyên ngày giờ địa phương từ API, tránh chuyển múi giờ lịch chuyến.
export function scheduleTime(day: string, time: string) {
  const [year, month, date] = day.split('-');
  return `${time.slice(0, 5)} · ${date}/${month}/${year}`;
}
