"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import { useRouter } from "next/navigation";
import { api } from "../../services/api";
import type { Route, Station } from "../../types";

/* Unsplash images mapped by destination keyword for visual flair */
const ROUTE_IMAGES: Record<string, string> = {
  "sa pa": "https://images.unsplash.com/photo-1570366583862-f91883984fde?w=600&q=80",
  "hải phòng": "https://images.unsplash.com/photo-1583417319070-4a69db38a482?w=600&q=80",
  "đà lạt": "https://images.unsplash.com/photo-1555921015-5532091f6026?w=600&q=80",
  "nha trang": "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?w=600&q=80",
  "huế": "https://images.unsplash.com/photo-1605811726683-ba78a5779519?w=600&q=80",
  "vũng tàu": "https://images.unsplash.com/photo-1540611025311-01df3cef54b5?w=600&q=80",
  "hà nội": "https://images.unsplash.com/photo-1509030450996-dd1a26dda07a?w=600&q=80",
  "sài gòn": "https://images.unsplash.com/photo-1583417319070-4a69db38a482?w=600&q=80",
  "đà nẵng": "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?w=600&q=80",
  "quảng ninh": "https://images.unsplash.com/photo-1528127269322-539801943592?w=600&q=80",
};

const FALLBACK_IMAGE = "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=600&q=80";

function getRouteImage(routeName: string): string {
  const lower = routeName.toLowerCase();
  for (const [key, url] of Object.entries(ROUTE_IMAGES)) {
    if (lower.includes(key)) return url;
  }
  return FALLBACK_IMAGE;
}

function formatPrice(price: string | number | undefined): string {
  if (!price) return "";
  const n = typeof price === "string" ? parseInt(price, 10) : price;
  if (isNaN(n)) return "";
  return n.toLocaleString("vi-VN") + "đ";
}

const CARDS_PER_PAGE = 4;

export default function PopularRoutes() {
  const router = useRouter();
  const [routes, setRoutes] = useState<Route[]>([]);
  const [stations, setStations] = useState<Station[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [page, setPage] = useState(0);
  const trackRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    Promise.all([api.routes(), api.stations()])
      .then(([r, s]) => {
        setRoutes(r);
        setStations(s);
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  const totalPages = Math.max(1, Math.ceil(routes.length / CARDS_PER_PAGE));

  const prev = useCallback(() => setPage((p) => Math.max(0, p - 1)), []);
  const next = useCallback(() => setPage((p) => Math.min(totalPages - 1, p + 1)), [totalPages]);

  const resolveStationName = (code: string) =>
    stations.find((s) => s.maBenXe === code)?.tenBenXe ?? code;

  const handleQuickSearch = (route: Route) => {
    const today = new Date().toISOString().slice(0, 10);
    router.push(
      `/trips?from=${encodeURIComponent(route.tramKhoiHanh)}&to=${encodeURIComponent(route.tramDen)}&date=${today}`
    );
  };

  const visibleRoutes = routes.slice(page * CARDS_PER_PAGE, (page + 1) * CARDS_PER_PAGE);

  /* ---------- render ---------- */

  if (loading) {
    return (
      <section className="popular-section" id="popular-routes">
        <div className="section-heading">
          <span className="eyebrow">TUYẾN XE PHỔ BIẾN</span>
          <h2>Khám phá những hành trình được yêu thích</h2>
        </div>
        <div className="carousel-loading">
          {[...Array(CARDS_PER_PAGE)].map((_, i) => (
            <div key={i} className="route-card-skeleton" />
          ))}
        </div>
      </section>
    );
  }

  if (error || routes.length === 0) {
    return (
      <section className="popular-section" id="popular-routes">
        <div className="section-heading">
          <span className="eyebrow">TUYẾN XE PHỔ BIẾN</span>
          <h2>Khám phá những hành trình được yêu thích</h2>
        </div>
        <div className="carousel-empty">
          <div className="empty-icon">🚌</div>
          <strong>Chưa có dữ liệu tuyến xe</strong>
          <p>Hệ thống hiện chưa có tuyến xe nào. Vui lòng quay lại sau.</p>
        </div>
      </section>
    );
  }

  return (
    <section className="popular-section" id="popular-routes">
      <div className="section-heading">
        <span className="eyebrow">TUYẾN XE PHỔ BIẾN</span>
        <div className="section-heading-row">
          <h2>Khám phá những hành trình được yêu thích</h2>
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

      <div className="carousel-track" ref={trackRef}>
        {visibleRoutes.map((route) => {
          const displayName =
            route.tenTuyen ??
            `${resolveStationName(route.tramKhoiHanh)} - ${resolveStationName(route.tramDen)}`;
          return (
            <div
              key={route.maTuyen}
              className="route-card"
              onClick={() => handleQuickSearch(route)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === "Enter" && handleQuickSearch(route)}
            >
              <div className="route-card-img">
                <img src={getRouteImage(displayName)} alt={displayName} loading="lazy" />
                <div className="route-card-overlay" />
              </div>
              <div className="route-card-body">
                <h3>{displayName}</h3>
                {route.giaCoBan ? (
                  <p className="route-card-price">
                    Từ <strong>{formatPrice(route.giaCoBan)}</strong>
                  </p>
                ) : (
                  <p className="route-card-price route-card-price--muted">Liên hệ</p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
