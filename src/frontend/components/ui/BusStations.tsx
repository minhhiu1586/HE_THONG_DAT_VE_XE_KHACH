"use client";

import { useEffect, useState, useCallback } from "react";
import { api } from "../../services/api";
import type { Station } from "../../types";

const STATION_IMAGES: Record<string, string> = {
  "miền đông": "https://images.unsplash.com/photo-1570125909232-eb263c188f7e?w=600&q=80",
  "miền tây": "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=600&q=80",
  "giáp bát": "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=600&q=80",
  "mỹ đình": "https://images.unsplash.com/photo-1570125909232-eb263c188f7e?w=600&q=80",
  "đà nẵng": "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?w=600&q=80",
  "an sương": "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=600&q=80",
};

const FALLBACK_IMAGE = "https://images.unsplash.com/photo-1570125909232-eb263c188f7e?w=600&q=80";

function getStationImage(name: string): string {
  const lower = name.toLowerCase();
  for (const [key, url] of Object.entries(STATION_IMAGES)) {
    if (lower.includes(key)) return url;
  }
  return FALLBACK_IMAGE;
}

const CARDS_PER_PAGE = 4;

export default function BusStations() {
  const [stations, setStations] = useState<Station[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [page, setPage] = useState(0);

  useEffect(() => {
    api
      .stations()
      .then(setStations)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  const totalPages = Math.max(1, Math.ceil(stations.length / CARDS_PER_PAGE));

  const prev = useCallback(() => setPage((p) => Math.max(0, p - 1)), []);
  const next = useCallback(
    () => setPage((p) => Math.min(totalPages - 1, p + 1)),
    [totalPages]
  );

  const visibleStations = stations.slice(
    page * CARDS_PER_PAGE,
    (page + 1) * CARDS_PER_PAGE
  );

  /* ---------- render ---------- */

  if (loading) {
    return (
      <section className="popular-section" id="bus-stations">
        <div className="section-heading">
          <span className="eyebrow">BẾN XE</span>
          <h2>Tra cứu các bến xe trong hệ thống</h2>
        </div>
        <div className="carousel-loading">
          {[...Array(CARDS_PER_PAGE)].map((_, i) => (
            <div key={i} className="route-card-skeleton" />
          ))}
        </div>
      </section>
    );
  }

  if (error || stations.length === 0) {
    return (
      <section className="popular-section" id="bus-stations">
        <div className="section-heading">
          <span className="eyebrow">BẾN XE</span>
          <h2>Tra cứu các bến xe trong hệ thống</h2>
        </div>
        <div className="carousel-empty">
          <div className="empty-icon">🏢</div>
          <strong>Chưa có dữ liệu bến xe</strong>
          <p>Hệ thống hiện chưa có bến xe nào. Vui lòng quay lại sau.</p>
        </div>
      </section>
    );
  }

  return (
    <section className="popular-section" id="bus-stations">
      <div className="section-heading">
        <span className="eyebrow">BẾN XE</span>
        <div className="section-heading-row">
          <h2>Tra cứu các bến xe trong hệ thống</h2>
          <div className="carousel-nav">
            <button
              className="carousel-arrow"
              onClick={prev}
              disabled={page === 0}
              aria-label="Trang trước"
            >
              ‹
            </button>
            <span className="carousel-page">
              {page + 1} / {totalPages}
            </span>
            <button
              className="carousel-arrow"
              onClick={next}
              disabled={page === totalPages - 1}
              aria-label="Trang sau"
            >
              ›
            </button>
          </div>
        </div>
      </div>

      <div className="carousel-track">
        {visibleStations.map((station) => (
          <div key={station.maBenXe} className="route-card station-card">
            <div className="route-card-img">
              <img
                src={getStationImage(station.tenBenXe)}
                alt={station.tenBenXe}
                loading="lazy"
              />
              <div className="route-card-overlay" />
            </div>
            <div className="route-card-body">
              <h3>{station.tenBenXe}</h3>
              {station.diaChi && (
                <p className="route-card-price route-card-price--muted">
                  📍 {station.diaChi}
                </p>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
