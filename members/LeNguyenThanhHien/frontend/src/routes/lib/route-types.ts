import { sortedUnique } from '@shared/lib/management-options';

export type City = { maTinhThanh: string; tenTinhThanh: string; khuVuc: string | null };

export type Station = {
  maBenXe: string;
  tenBenXe: string;
  diaChi: string;
  soDienThoai: string | null;
  maTinhThanh: string;
  trangThai: string;
};

export type Stop = { maDiemDung: string; tenDiemDung: string; diaChi: string; thuTu: number; thoiGianDung: number; loaiDiemDung: string };

export type Route = {
  maTuyen: string;
  tenTuyen: string;
  tramKhoiHanh: string;
  tramDen: string;
  tramTrungGian: Stop[];
  thoiGianKhoiHanh: string | null;
  khoangCachKM: number | null;
  thoiGianDuKien: number | null;
  giaCoBan: number;
  moTa: string | null;
  trangThai: string;
  maNhaXe: string | null;
};

export type Tab = 'routes' | 'stations' | 'cities';

export const emptyCity = { maTinhThanh: '', tenTinhThanh: '', khuVuc: '' };

export const emptyStation = { maBenXe: '', tenBenXe: '', diaChi: '', soDienThoai: '', maTinhThanh: '', trangThai: 'HOAT_DONG' };

export const emptyRoute = {
  maTuyen: '',
  tenTuyen: '',
  tramKhoiHanh: '',
  tramDen: '',
  thoiGianKhoiHanh: '',
  khoangCachKM: '',
  thoiGianDuKien: '',
  giaCoBan: '',
  moTa: '',
  trangThai: 'SAP_KHAI_THAC',
};

export const routeStatusLabels: Record<string, string> = {
  SAP_KHAI_THAC: 'Sắp khai thác',
  DANG_KHAI_THAC: 'Đang khai thác',
  TAM_NGUNG: 'Tạm ngừng',
  NGUNG_KHAI_THAC: 'Ngừng khai thác',
};

export const routeStatuses = ['SAP_KHAI_THAC', 'DANG_KHAI_THAC', 'TAM_NGUNG', 'NGUNG_KHAI_THAC'];

export const stationStatusLabels: Record<string, string> = {
  HOAT_DONG: 'Hoạt động',
  TAM_NGUNG: 'Tạm ngừng',
  DONG_CUA: 'Đóng cửa',
  BAO_TRI: 'Bảo trì',
};

export function cityList(items: City[]) {
  return sortedUnique(items, item => item.maTinhThanh, item => item.tenTinhThanh);
}

export function stationList(items: Station[]) {
  return sortedUnique(items, item => item.maBenXe, item => item.tenBenXe);
}

export function routeList(items: Route[]) {
  return sortedUnique(items, item => item.maTuyen, item => item.tenTuyen);
}

export type CityForm = typeof emptyCity;
export type StationForm = typeof emptyStation;
export type RouteFormValues = typeof emptyRoute;
export type StopForm = { maDiemDung: string; tenDiemDung: string; diaChi: string; thoiGianDung: string; loaiDiemDung: string };
export const emptyStop: StopForm = { maDiemDung: '', tenDiemDung: '', diaChi: '', thoiGianDung: '0', loaiDiemDung: 'CA_HAI' };

export function routeToForm(route: Route): RouteFormValues {
  return {
    maTuyen: route.maTuyen,
    tenTuyen: route.tenTuyen,
    tramKhoiHanh: route.tramKhoiHanh,
    tramDen: route.tramDen,
    thoiGianKhoiHanh: route.thoiGianKhoiHanh?.slice(0, 5) ?? '',
    khoangCachKM: route.khoangCachKM?.toString() ?? '',
    thoiGianDuKien: route.thoiGianDuKien?.toString() ?? '',
    giaCoBan: route.giaCoBan.toString(),
    moTa: route.moTa ?? '',
    trangThai: route.trangThai,
  };
}

export function cityToForm(city: City): CityForm {
  return { maTinhThanh: city.maTinhThanh, tenTinhThanh: city.tenTinhThanh, khuVuc: city.khuVuc || '' };
}

export function stationToForm(station: Station): StationForm {
  return { ...station, soDienThoai: station.soDienThoai || '' };
}

export function stopToForm(stop: Stop): StopForm {
  return {
    maDiemDung: stop.maDiemDung,
    tenDiemDung: stop.tenDiemDung,
    diaChi: stop.diaChi,
    thoiGianDung: String(stop.thoiGianDung),
    loaiDiemDung: stop.loaiDiemDung,
  };
}
