import { groupSearchOptions } from './search-locations';

export interface CityRecord {
  maTinhThanh: string;
  tenTinhThanh: string;
  khuVuc?: string | null;
}

export interface StationRecord {
  maBenXe: string;
  tenBenXe: string;
  diaChi: string;
  maTinhThanh: string;
  trangThai: string;
}

export interface SearchOption {
  value: string;
  label: string;
  kind: 'CITY' | 'STATION';
  aliases?: string[];
  detail?: string;
  cityValue?: string;
}

export interface PopularRoute {
  id: string;
  name: string;
  from: string;
  to: string;
  fromValue: string;
  toValue: string;
  price: number;
}

export interface Station {
  id: string;
  name: string;
  city: string;
  searchValue: string;
}

export interface SearchTrip {
  maChuyen: string;
  maTuyen: string;
  tenTuyen: string;
  maNhaXe: string | null;
  tenNhaXe: string | null;
  logoNhaXe?: string | null;
  maXe: string | null;
  bienSo: string | null;
  maLoaiXe: string | null;
  tenLoaiXe: string | null;
  soChoNgoi: number;
  tongSoGhe: number;
  soGheTrong: number;
  maBenDi: string;
  tenBenDi: string;
  diaChiBenDi: string;
  maTinhDi: string;
  tenTinhDi: string;
  maBenDen: string;
  tenBenDen: string;
  diaChiBenDen: string;
  maTinhDen: string;
  tenTinhDen: string;
  ngayKhoiHanh: string;
  gioKhoiHanh: string;
  gioDenDuKien: string;
  thoiLuongPhut: number;
  giaVeDuKien: string;
  trangThai: string;
  ngungNhanDatVeLuc: string;
  coTheDatVe: boolean;
  trangThaiNhanDatVe: 'CON_CHO' | 'HET_CHO' | 'NGUNG_NHAN_DAT';
  diemDung: { maDiemDung: string; tenDiemDung: string; diaChi: string; thuTu: number; thoiGianDung: number }[];
}

export interface TripSeat {
  maGhe: string;
  viTri: string;
  tang: number;
  trangThaiSuDung: 'HOAT_DONG' | 'BAO_TRI' | 'NGUNG_SU_DUNG';
  tinhTrangKhaDung: 'TRONG' | 'DANG_GIU' | 'DA_BAN' | 'KHONG_BAN';
}

export interface TripSeatResponse {
  maChuyen: string;
  maXe: string;
  serverTime: string;
  danhSachGhe: TripSeat[];
}

export interface ActivePromotion {
  maKhuyenMai: string;
  tenKhuyenMai: string;
  moTa?: string | null;
  loaiGiam: 'PHAN_TRAM' | 'SO_TIEN_CO_DINH';
  giaTriGiam: number | string;
  giamToiDa?: string | null;
  ngayBatDau: string;
  ngayKetThuc: string;
}

interface RawRoute {
  maTuyen: string;
  tenTuyen: string;
  tramKhoiHanh: string;
  tramDen: string;
  giaCoBan: number | string;
  trangThai: string;
}

export interface HoldSeatRequest {
  maChuyen: string;
  danhSachMaGhe: string[];
}

export interface HoldSeatResponse {
  maDon: string;
  thoiDiemHetHan: string;
  danhSachGheGiu: string[];
}

function apiBase(): string {
  const configured = process.env.NEXT_PUBLIC_API_URL;
  if (configured) return configured.replace(/\/$/, '');
  if (typeof window !== 'undefined') return '/api';
  return `${(process.env.BACKEND_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')}/api`;
}

async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${apiBase()}${path}`, { cache: 'no-store' });
  if (!response.ok) {
    let message = `Máy chủ trả về lỗi ${response.status}.`;
    try {
      const problem = await response.json() as { message?: string };
      if (problem.message) message = problem.message;
    } catch {
      // Giữ thông báo HTTP nếu server không trả JSON.
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

async function apiPost<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${apiBase()}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
    cache: 'no-store'
  });

  if (!response.ok) {
    let message = `Máy chủ trả về lỗi ${response.status}.`;
    try {
      const problem = await response.json() as { message?: string };
      if (problem.message) message = problem.message;
    } catch {
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

export async function holdSeats(request: HoldSeatRequest): Promise<HoldSeatResponse> {
  return apiPost<HoldSeatResponse>('/booking/hold', request);
}

export async function getSearchOptions(): Promise<SearchOption[]> {
  const [cities, stations] = await Promise.all([
    apiGet<CityRecord[]>('/tinh-thanh'),
    apiGet<StationRecord[]>('/ben-xe?trangThai=HOAT_DONG'),
  ]);
  return groupSearchOptions(cities, stations);
}

export async function searchTrips(from: string, to: string, date: string): Promise<SearchTrip[]> {
  const options = await getSearchOptions();
  const aliasesFor = (value: string) => options.find(option => option.value === value || option.aliases?.includes(value))?.aliases ?? [value];
  const requests = aliasesFor(from).flatMap(origin => aliasesFor(to).map(destination => ({ origin, destination })));
  const trips = new Map<string, SearchTrip>();
  for (let offset = 0; offset < requests.length; offset += 4) {
    const results = await Promise.all(requests.slice(offset, offset + 4).map(({ origin, destination }) => {
      const query = new URLSearchParams({ from: origin, to: destination, date });
      return apiGet<SearchTrip[]>(`/chuyen-xe?${query.toString()}`);
    }));
    for (const result of results) for (const trip of result) trips.set(trip.maChuyen, trip);
  }
  return [...trips.values()].sort((a, b) => a.gioKhoiHanh.localeCompare(b.gioKhoiHanh) || a.maChuyen.localeCompare(b.maChuyen));
}

export async function getTrip(tripId: string): Promise<SearchTrip> {
  return apiGet<SearchTrip>(`/chuyen-xe/${encodeURIComponent(tripId)}`);
}

export async function getTripSeats(tripId: string): Promise<TripSeatResponse> {
  return apiGet<TripSeatResponse>(`/chuyen-xe/${encodeURIComponent(tripId)}/ghe`);
}

export async function getPopularRoutes(): Promise<PopularRoute[]> {
  const [routes, stations, cities] = await Promise.all([
    apiGet<RawRoute[]>('/tuyen-xe/cong-khai'),
    apiGet<StationRecord[]>('/ben-xe?trangThai=HOAT_DONG'),
    apiGet<CityRecord[]>('/tinh-thanh'),
  ]);
  const stationById = new Map(stations.map(station => [station.maBenXe, station]));
  const cityById = new Map(cities.map(city => [city.maTinhThanh, city]));
  return routes.flatMap(route => {
    const origin = stationById.get(route.tramKhoiHanh);
    const destination = stationById.get(route.tramDen);
    if (!origin || !destination) return [];
    const from = cityById.get(origin.maTinhThanh)?.tenTinhThanh ?? origin.tenBenXe;
    const to = cityById.get(destination.maTinhThanh)?.tenTinhThanh ?? destination.tenBenXe;
    return [{
      id: route.maTuyen,
      name: route.tenTuyen,
      from,
      to,
      fromValue: `STATION:${origin.maBenXe}`,
      toValue: `STATION:${destination.maBenXe}`,
      price: Number(route.giaCoBan),
    }];
  });
}

export async function getPopularStations(): Promise<Station[]> {
  const [stations, cities] = await Promise.all([
    apiGet<StationRecord[]>('/ben-xe?trangThai=HOAT_DONG'),
    apiGet<CityRecord[]>('/tinh-thanh'),
  ]);
  const cityById = new Map(cities.map(city => [city.maTinhThanh, city]));
  return stations.map(station => ({
    id: station.maBenXe,
    name: station.tenBenXe,
    city: cityById.get(station.maTinhThanh)?.tenTinhThanh ?? '',
    searchValue: `STATION:${station.maBenXe}`,
  }));
}

export async function getActivePromotions(): Promise<ActivePromotion[]> {
  return apiGet<ActivePromotion[]>('/khuyen-mai');
}
