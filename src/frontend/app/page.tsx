import AdsSection from '@/components/Home/AdsSection';
import HeroBanner from '@/components/Home/HeroBanner';
import PopularSection from '@/components/Home/PopularSection';
import { getActivePromotions, getPopularRoutes, getPopularStations, getSearchOptions } from '@/lib/api';

async function load<T>(request: () => Promise<T>, fallback: T): Promise<{ data: T; error: string }> {
  try { return { data: await request(), error: '' }; }
  catch (cause) { return { data: fallback, error: cause instanceof Error ? cause.message : 'Không thể tải dữ liệu.' }; }
}

export default async function Home() {
  const [options, routes, stations, promotions] = await Promise.all([
    load(getSearchOptions, []), load(getPopularRoutes, []), load(getPopularStations, []), load(getActivePromotions, []),
  ]);
  return <main>
    <HeroBanner options={options.data} loadError={options.error} />
    {routes.error && <p className="container" role="alert">Không tải được tuyến xe: {routes.error}</p>}
    <PopularSection routes={routes.data} stations={stations.data} />
    {promotions.error && <p className="container" role="alert">Không tải được khuyến mãi: {promotions.error}</p>}
    <AdsSection promotions={promotions.data} />
  </main>;
}
