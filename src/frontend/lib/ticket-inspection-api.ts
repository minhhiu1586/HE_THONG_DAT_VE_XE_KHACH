export interface VerifyRequest {
  maChuyen: string;
  maVe?: string;
  qrToken?: string;
}

export interface VerifyResponse {
  hopLe: boolean;
  maVe: string | null;
  maChuyen: string;
  maGhe: string | null;
  maLyDo: string | null;
  lyDo: string | null;
  hoTenHanhKhach?: string | null;
  soDienThoaiHanhKhach?: string | null;
  cccdHanhKhach?: string | null;
  maTraCuu?: string | null;
  giaVe?: number | null;
  tenTuyen?: string | null;
  bienSoXe?: string | null;
}

export interface CheckInRequest {
  maChuyen: string;
}

export interface CheckInResponse {
  maVe: string;
  maChuyen: string;
  maGhe: string;
  trangThai: string;
  thoiDiemLenXe: string;
}

export interface PassengerItem {
  maVe: string;
  maTraCuu: string;
  maGhe: string;
  hoTenHanhKhach: string;
  soDienThoaiHanhKhach: string;
  cccdHanhKhach: string | null;
  trangThai: string;
  giaVe: number;
  thoiDiemLenXe: string | null;
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
  daLenXe: number;
  chuaLenXe: number;
  daHoan: number;
  daHuy: number;
  danhSachHanhKhach: PassengerItem[];
}

export async function verifyTicket(
  reqFn: <T>(url: string, method?: string, body?: unknown) => Promise<T>,
  payload: VerifyRequest
): Promise<VerifyResponse> {
  return reqFn<VerifyResponse>('/kiem-ve/xac-minh', 'POST', payload);
}

export async function checkInTicket(
  reqFn: <T>(url: string, method?: string, body?: unknown, headers?: Record<string, string>) => Promise<T>,
  maVe: string,
  payload: CheckInRequest,
  attemptKey?: string
): Promise<CheckInResponse> {
  const headers = attemptKey ? { 'Idempotency-Key': attemptKey } : undefined;
  return reqFn<CheckInResponse>(`/ve-xe/${encodeURIComponent(maVe)}/len-xe`, 'POST', payload, headers);
}

export async function getPassengerList(
  reqFn: <T>(url: string, method?: string, body?: unknown) => Promise<T>,
  maChuyen: string
): Promise<PassengerListResponse> {
  return reqFn<PassengerListResponse>(`/chuyen-xe/${encodeURIComponent(maChuyen)}/hanh-khach`);
}
