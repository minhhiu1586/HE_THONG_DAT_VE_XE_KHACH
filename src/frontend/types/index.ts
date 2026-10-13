export type ApiError = { code?: string; message?: string; error?: string; details?: { fieldErrors?: Array<{ field: string; message: string }> } };
export type Province = { maTinhThanh: string; tenTinhThanh: string; khuVuc?: string };
export type Station = { maBenXe: string; tenBenXe: string; diaChi?: string; maTinhThanh?: string };
export type Route = { maTuyen: string; tenTuyen?: string; tramKhoiHanh: string; tramDen: string; giaCoBan?: string | number };
export type TripSummary = {
  maChuyen: string; maTuyen?: string; maXe?: string | null; benDi?: Station; benDen?: Station;
  ngayKhoiHanh: string; gioKhoiHanh: string; gioDenDuKien?: string; trangThai?: string; giaThamKhao?: string; soGheTrong?: number;
};
export type TripDetail = TripSummary & { maXe?: string | null; trangThai?: string; nhaXe?: { maNhaXe: string; tenNhaXe: string } | null; tienIch?: string[] };
export type Seat = { maGhe: string; maXe?: string; viTri?: string | { tang?: number; hang?: string; cot?: number }; tang?: number; trangThai?: string; trangThaiSuDung?: string; tinhTrangKhaDung?: string };
export type SeatMap = { maChuyen: string; maXe?: string; serverTime?: string; danhSachGhe: Seat[] };
export type Passenger = { hoTen: string; soDienThoai: string; email: string; ghiChu: string };
export type SearchValues = { from: string; to: string; date: string };
export type Order = { maDon: string; maChuyen: string; trangThaiDon?: string; khachDatTen?: string; khachDatSoDienThoai?: string; giaDaChot?: Quote };
export type Quote = { tongTienGoc: string; tongTienGiam: string; tongThanhToan: string; ketQuaKhuyenMai?: { hopLe: boolean; duocGiam: string } };