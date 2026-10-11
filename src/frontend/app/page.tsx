import AdsSection from '@/components/Home/AdsSection';
import HeroBanner from '../components/Home/HeroBanner';
import PopularSection from '../components/Home/PopularSection';

// Nhúng hàm fetch API (đảm bảo đường dẫn import tới file api.ts là chính xác)
import { getPopularRoutes, getPopularStations } from '@/lib/api'; 

export default async function Home() {
  // 1. Fetch dữ liệu từ API Backend (Chạy trên Server của Next.js)
  const popularRoutes = await getPopularRoutes();
  const popularStations = await getPopularStations();

  return (
    <main>
      <HeroBanner />
      
      {/* 2. Truyền dữ liệu lấy được xuống component PopularSection */}
      <PopularSection routes={popularRoutes} stations={popularStations} />
      
      <AdsSection />
      {/* Các sections khác sẽ được thêm vào sau (Platform Features, Media Mentions...) */}
      <div className="container" style={{ textAlign: 'center', padding: '4rem 0', color: 'var(--text-muted)' }}>
        <p>Các phần Ưu đãi và Đối tác sẽ được tiếp tục phát triển ở các bước sau.</p>
      </div>
    </main>
  );
}