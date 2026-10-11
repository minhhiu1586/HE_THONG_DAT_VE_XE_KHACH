// Sửa lại import từ mock-data
import { popularRoutes as mockRoutes, busStationCards as mockStations } from './mock-data';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export interface PopularRoute {
  id: string;
  from: string;
  to: string;
  image: string;
  price: number;
  originalPrice?: number;
}

export interface Station {
  id: string;
  name: string;
  city: string;
  image: string;
}

interface RawTuyenXe {
  id?: string;
  maTuyen?: string;
  tramKhoiHanh?: string;
  tramDen?: string;
  giaCoBan?: number;
}

interface RawBenXe {
  id?: string;
  maBen?: string;
  tenBen?: string;
  tinhThanh?: string;
}

export async function getPopularRoutes(): Promise<PopularRoute[]> {
  try {
    const res = await fetch(`${API_URL}/tuyen-xe`, { cache: 'no-store' });
    if (!res.ok) throw new Error('Lỗi fetch tuyến xe');

    const rawData: RawTuyenXe[] = await res.json();

    if (!rawData || rawData.length === 0) {
      return mockRoutes;
    }

    return rawData.slice(0, 4).map((item) => ({
      id: item.maTuyen || item.id || Math.random().toString(),
      from: item.tramKhoiHanh || 'Chưa rõ',
      to: item.tramDen || 'Chưa rõ',
      image: '/images/sapa.jpg',
      price: item.giaCoBan || 200000,
      originalPrice: item.giaCoBan ? item.giaCoBan + 50000 : 250000
    }));

  } catch (error) {
    console.error("Dùng mock data Tuyến xe");
    return mockRoutes;
  }
}

export async function getPopularStations(): Promise<Station[]> {
  try {
    const res = await fetch(`${API_URL}/ben-xe`, { cache: 'no-store' });
    if (!res.ok) throw new Error('Lỗi fetch bến xe');

    const rawData: RawBenXe[] = await res.json();

    if (!rawData || rawData.length === 0) {
       // Sử dụng mockStations đã được map từ busStationCards
      return mockStations.map(station => ({
        id: station.id,
        name: station.name,
        city: station.city,
        image: station.image
      }));
    }

    return rawData.slice(0, 4).map((item) => ({
      id: item.id || item.maBen || Math.random().toString(),
      name: item.tenBen || 'Bến xe',
      city: item.tinhThanh || 'Tỉnh/Thành phố',
      image: '/images/haiphong.jpg'
    }));

  } catch (error) {
    console.error("Dùng mock data Bến xe");
     // Trả về mock data được ánh xạ chuẩn xác
    return mockStations.map(station => ({
      id: station.id,
      name: station.name,
      city: station.city,
      image: station.image
    }));
  }
}