import type { RefundPolicy } from './refund-api';

export type BookingRequest = <T>(path: string, method?: string, body?: unknown, headers?: Record<string, string>) => Promise<T>;
export type BookingPassenger = { maGhe: string; hoTen: string; soDienThoai: string; cccd?: string | null };
export type PriceSummary = { donViTienTe: string; tongTienGoc: string; tongTienGiam: string; tongThanhToan: string };
export type BookingQuote = PriceSummary & { maBaoGia: string; maDon: string; thoiDiemHetHan: string; chinhSachHoanVe?: RefundPolicy | null };
export type BookingOrder = {
 maDon: string; maChuyen: string; trangThaiDon: string; khachDatTen: string | null; khachDatSoDienThoai: string | null; khachDatEmail: string | null; emailXacNhan: { trangThai: string; diaChi: string; thoiDiemGui: string | null } | null; ghiChu: string | null;
 serverTime: string; thoiDiemHetHan: string | null; hanhKhach: BookingPassenger[];
 danhSachGiuCho: { maGhe: string; trangThai: string }[];
 danhSachVe: { maVe: string; maTraCuu?: string; qrImage?: string | null; maGhe: string; hoTen: string; giaVe: string; trangThai: string; dangHoan?: boolean }[];
 giaTamTinh: { tongTienGoc: string; tienGiam: string; tongThanhToan: string } | null;
 giaDaChot: PriceSummary | null; coTheChotGia: boolean;
 chinhSachHoanVe?: RefundPolicy | null;
};
export type Payment = { maGiaoDich: string; maDon: string; maPhuongThuc: string; trangThai: string; soTien: string; thoiDiemTao: string | null; donViTienTe: string; urlThanhToan: string | null; canDoiSoat: boolean; ketQuaCongDaXacDinh: boolean };
export type PaymentMethod = { maPhuongThuc: string; tenPhuongThuc: string };
export function bookingApi(request: BookingRequest) {
 const path = (id: string) => '/booking/' + encodeURIComponent(id);
 const key = (id: string) => ({ 'Idempotency-Key': id });
 return {
  order: (id: string) => request<BookingOrder>(path(id)),
  contact: (id: string, body: object, attempt: string) => request<BookingOrder>(path(id), 'PATCH', body, key(attempt)),
  passengers: (id: string, hanhKhach: BookingPassenger[]) => request<BookingOrder>(path(id) + '/hanh-khach', 'PUT', { hanhKhach }),
  quote: (id: string, maKhuyenMai: string | null) => request<BookingQuote>(path(id) + '/bao-gia', 'POST', { maKhuyenMai }),
  freeze: (id: string, maBaoGia: string, attempt: string) => request<BookingQuote>(path(id) + '/chot-gia', 'POST', { maBaoGia }, key(attempt)),
  methods: () => request<PaymentMethod[]>('/phuong-thuc-thanh-toan'),
  payments: async (id: string) => {
   const items: Payment[] = [];
   let page = 1; let totalPages = 1;
   do {
    const result = await request<{ items: Payment[]; totalPages: number }>(path(id) + '/thanh-toan?page=' + page + '&size=100&sort=maGiaoDich,desc');
    items.push(...result.items); totalPages = result.totalPages; page++;
   } while (page <= totalPages);
   return { items };
  },
  pay: (id: string, maPhuongThuc: string, attempt: string) => request<Payment>(path(id) + '/thanh-toan', 'POST', { maPhuongThuc }, key(attempt)),
  payment: (id: string) => request<Payment>('/thanh-toan/' + encodeURIComponent(id)),
  verify: (id: string) => request<Payment>('/thanh-toan/' + encodeURIComponent(id) + '/xac-minh', 'POST', {}),
 };
}
export function money(value: string | number | undefined | null) { return Number(value ?? 0).toLocaleString('vi-VN') + 'đ'; }
