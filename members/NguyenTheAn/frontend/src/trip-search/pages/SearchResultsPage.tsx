"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";

import SearchForm from "@members/NguyenTheAn/booking/components/SearchForm";
import TripCard from "@members/NguyenTheAn/booking/components/TripCard";
import { getSearchOptions, searchTrips, type SearchOption, type SearchTrip } from "@members/NguyenTheAn/booking/lib/api";
import type { SearchValues } from "@shared/types/index";

function TripsContent() {
  const params = useSearchParams();
  const router = useRouter();
  const values = {
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
    date: params.get("date") ?? new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()),
  };
  const [trips, setTrips] = useState<SearchTrip[]>([]);
  const [locations, setLocations] = useState<SearchOption[]>([]);
  useEffect(() => {
    let active = true;
    getSearchOptions().then(data => { if (active) setLocations(data); }).catch(() => { });
    return () => { active = false; };
  }, []);
  const locationName = (value: string, fallback: string) =>
    locations.find(option => option.value === value)?.label ?? (value.startsWith('CITY:') || value.startsWith('STATION:') ? fallback : value || fallback);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [timeFilter, setTimeFilter] = useState('all');
  const [sort, setSort] = useState('departure');
  const [retry, setRetry] = useState(0);

  const search = async (next: SearchValues) => {
    if (next.from === values.from && next.to === values.to && next.date === values.date) {
      setRetry(current => current + 1);
      return;
    }
    router.replace(
      `/trips?from=${encodeURIComponent(next.from)}&to=${encodeURIComponent(
        next.to,
      )}&date=${next.date}`,
    );
  };

  useEffect(() => {
    if (!values.from || !values.to || !values.date) return;
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const items = await searchTrips(values.from, values.to, values.date);
        if (!cancelled) setTrips(items);
      } catch (cause) {
        if (!cancelled) { setError(cause instanceof Error ? cause.message : 'Không thể tải danh sách chuyến.'); setTrips([]); }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [values.from, values.to, values.date, retry]);

  const visibleTrips = trips.filter(trip => {
    const hour = Number(trip.gioKhoiHanh.slice(0, 2));
    return timeFilter === 'all' || (timeFilter === 'morning' ? hour >= 5 && hour < 12 : hour >= 12 && hour < 18);
  }).sort((a, b) => sort === 'price'
    ? Number(a.giaVeDuKien) - Number(b.giaVeDuKien)
    : `${a.ngayKhoiHanh}T${a.gioKhoiHanh}`.localeCompare(`${b.ngayKhoiHanh}T${b.gioKhoiHanh}`));

  return (
    <main className="page">
      <div className="compact-search">
        <SearchForm key={`${values.from}|${values.to}|${values.date}`} initial={values} onSubmit={search} />
      </div>
      <div className="results-layout">
        <aside className="filters">
          <p className="eyebrow">BỘ LỌC</p>
          <label>
            Giờ khởi hành
            <select value={timeFilter} onChange={event => setTimeFilter(event.target.value)}>
              <option value="all">Tất cả khung giờ</option>
              <option value="morning">Buổi sáng (05:00 - 12:00)</option>
              <option value="afternoon">Buổi chiều (12:00 - 18:00)</option>
            </select>
          </label>
          <label>
            Sắp xếp
            <select value={sort} onChange={event => setSort(event.target.value)}>
              <option value="departure">Giờ khởi hành sớm nhất</option>
              <option value="price">Giá thấp nhất</option>
            </select>
          </label>
        </aside>
        <section className="results">
          <div className="results-head">
            <div>
              <span className="eyebrow">KẾT QUẢ TÌM KIẾM</span>
              <h1>
                {locationName(values.from, 'Điểm đi')} <span>→</span>{" "}
                {locationName(values.to, 'Điểm đến')}
              </h1>
              <p>
                {values.date} ·{" "}
                {loading
                  ? "Đang tìm chuyến..."
                  : `${visibleTrips.length} chuyến phù hợp`}
              </p>
            </div>
          </div>
          {error && (
            <div className="state error-state">
              <strong>Không thể tải dữ liệu</strong>
              <p>{error}</p>
              <button onClick={() => setRetry(current => current + 1)}>Thử lại</button>
            </div>
          )}
          {loading && (
            <div className="state">Đang tải các chuyến xe...</div>
          )}
          {!loading && !error && visibleTrips.length === 0 && (
            <div className="state">
              <strong>Chưa tìm thấy chuyến xe</strong>
              <p>Hãy thử đổi ngày hoặc điểm đi, điểm đến.</p>
            </div>
          )}
          {!loading &&
            !error &&
            visibleTrips.map((trip) => (
              <TripCard
                key={trip.maChuyen}
                trip={trip}
                onChoose={() => router.push(`/tim-chuyen/${encodeURIComponent(trip.maChuyen)}`)}
              />
            ))}
        </section>
      </div>
    </main>
  );
}

export default function Trips() {
  return (
    <Suspense
      fallback={
        <main className="page">
          <div className="state">Đang tải...</div>
        </main>
      }
    >
      <TripsContent />
    </Suspense>
  );
}
