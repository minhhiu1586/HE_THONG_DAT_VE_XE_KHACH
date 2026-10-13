"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";

import SearchForm from "../../components/booking/SearchForm";
import TripCard from "../../components/booking/TripCard";
import { api } from "../../services/api";
import type { Route, SearchValues, Station, TripSummary } from "../../types";

function TripsContent() {
  const params = useSearchParams();
  const router = useRouter();
  const values = {
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
    date: params.get("date") ?? new Date().toISOString().slice(0, 10),
  };
  const [trips, setTrips] = useState<TripSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const enrich = (
    items: TripSummary[],
    stations: Station[],
    routes: Route[],
  ) =>
    items.map((trip) => {
      const route = routes.find((item) => item.maTuyen === trip.maTuyen);
      const from = stations.find(
        (item) => item.maBenXe === route?.tramKhoiHanh,
      );
      const to = stations.find((item) => item.maBenXe === route?.tramDen);
      return {
        ...trip,
        benDi: from,
        benDen: to,
        giaThamKhao: route?.giaCoBan?.toString(),
      };
    });

  const search = async (next: SearchValues) => {
    router.replace(
      `/trips?from=${encodeURIComponent(next.from)}&to=${encodeURIComponent(
        next.to,
      )}&date=${next.date}`,
    );
    setLoading(true);
    setError("");
    try {
      const [items, stations, routes] = await Promise.all([
        api.searchTrips(next),
        api.stations(),
        api.routes(),
      ]);
      setTrips(enrich(items, stations, routes));
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Không thể tải danh sách chuyến.",
      );
      setTrips([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!values.from || !values.to || !values.date) return;
    let cancelled = false;
    const load = async () => {
      try {
        const stations = await api.stations();
        const resolve = (value: string) =>
          stations.find(
            (station) =>
              station.maBenXe === value ||
              station.tenBenXe.toLowerCase() === value.toLowerCase(),
          )?.maBenXe ?? value;
        if (!cancelled) {
          void search({
            ...values,
            from: resolve(values.from),
            to: resolve(values.to),
          });
        }
      } catch {
        if (!cancelled) void search(values);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [values.from, values.to, values.date]);

  return (
    <main className="page">
      <div className="compact-search">
        <SearchForm initial={values} onSubmit={search} />
      </div>
      <div className="results-layout">
        <aside className="filters">
          <p className="eyebrow">BỘ LỌC</p>
          <label>
            Giờ khởi hành
            <select>
              <option>Tất cả khung giờ</option>
              <option>Buổi sáng (05:00 - 12:00)</option>
              <option>Buổi chiều (12:00 - 18:00)</option>
            </select>
          </label>
          <label>
            Sắp xếp
            <select>
              <option>Giờ khởi hành sớm nhất</option>
              <option>Giá thấp nhất</option>
            </select>
          </label>
        </aside>
        <section className="results">
          <div className="results-head">
            <div>
              <span className="eyebrow">KẾT QUẢ TÌM KIẾM</span>
              <h1>
                {values.from || "Điểm đi"} <span>→</span>{" "}
                {values.to || "Điểm đến"}
              </h1>
              <p>
                {values.date} ·{" "}
                {loading
                  ? "Đang tìm chuyến..."
                  : `${trips.length} chuyến phù hợp`}
              </p>
            </div>
          </div>
          {error && (
            <div className="state error-state">
              <strong>Không thể tải dữ liệu</strong>
              <p>{error}</p>
              <button onClick={() => void search(values)}>Thử lại</button>
            </div>
          )}
          {loading && (
            <div className="state">Đang tải các chuyến xe...</div>
          )}
          {!loading && !error && trips.length === 0 && (
            <div className="state">
              <strong>Chưa tìm thấy chuyến xe</strong>
              <p>Hãy thử đổi ngày hoặc điểm đi, điểm đến.</p>
            </div>
          )}
          {!loading &&
            !error &&
            trips.map((trip) => (
              <TripCard
                key={trip.maChuyen}
                trip={trip}
                onChoose={() => router.push(`/${trip.maChuyen}/seats`)}
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