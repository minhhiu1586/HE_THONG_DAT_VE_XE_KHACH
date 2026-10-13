import type { ApiError, Province, Station, Route, SearchValues, TripSummary, TripDetail, SeatMap, Order, Quote } from "../types";
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) }, cache: "no-store" });
  if (!response.ok) {
    let payload: ApiError = {};
    try { payload = await response.json(); } catch { /* status is still surfaced below */ }
    throw new Error(payload.message ?? payload.error ?? `Không thể kết nối máy chủ (${response.status}).`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export const api = {
  provinces: () => apiFetch<Province[]>("/api/tinh-thanh"),
  stations: () => apiFetch<Station[]>("/api/ben-xe"),
  routes: () => apiFetch<Route[]>("/api/tuyen-xe/cong-khai"),
  searchTrips: (values: SearchValues) => apiFetch<TripSummary[]>(`/api/chuyen-xe?from=${encodeURIComponent(values.from)}&to=${encodeURIComponent(values.to)}&date=${values.date}`),
  trip: (id: string) => apiFetch<TripDetail>(`/api/chuyen-xe/${encodeURIComponent(id)}`),
  seats: (vehicleId: string) => apiFetch<import("../types").Seat[]>(`/api/xe/${encodeURIComponent(vehicleId)}/ghe`),
  hold: (body: { maChuyen: string; danhSachMaGhe: string[] }) => apiFetch<Order>("/api/giu-cho", { method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify(body) }),
  updateOrder: (id: string, body: object) => apiFetch<Order>(`/api/don-dat-ve/${id}`, { method: "PATCH", body: JSON.stringify(body) }),
  passengers: (id: string, body: object) => apiFetch<Order>(`/api/don-dat-ve/${id}/hanh-khach`, { method: "PUT", body: JSON.stringify(body) }),
  quote: (id: string, body: object) => apiFetch<Quote>(`/api/don-dat-ve/${id}/bao-gia`, { method: "POST", body: JSON.stringify(body) })
};