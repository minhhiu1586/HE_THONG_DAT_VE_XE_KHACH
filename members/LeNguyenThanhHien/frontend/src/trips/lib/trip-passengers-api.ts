export interface PassengerItem {
  maVe: string;
  maTraCuu: string;
  maGhe: string;
  hoTenHanhKhach: string;
  soDienThoaiHanhKhach: string;
  cccdHanhKhach: string | null;
  trangThai: string;
  giaVe: number;
  dangHoan: boolean;
}
export interface PassengerListResponse {
  maChuyen: string;
  tenTuyen: string;
  tenNhaXe: string;
  bienSo: string;
  ngayKhoiHanh: string;
  gioKhoiHanh: string;
  tongVe: number;
  daXuatVe: number;
  daHoan: number;
  daHuy: number;
  danhSachHanhKhach: PassengerItem[];
}
export function getPassengerList(
  request: <T>(url: string, method?: string, body?: unknown) => Promise<T>,
  maChuyen: string,
): Promise<PassengerListResponse> {
  return request<PassengerListResponse>(`/chuyen-xe/${encodeURIComponent(maChuyen)}/hanh-khach`);
}
