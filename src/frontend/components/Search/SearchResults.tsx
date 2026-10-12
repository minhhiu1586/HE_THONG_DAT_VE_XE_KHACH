'use client';

import { useState } from 'react';
import TripCard from './TripCard';
import styles from './Search.module.css';
import type { SearchTrip } from '@/lib/api';

type SortOption = 'departure-asc' | 'departure-desc' | 'price-asc' | 'price-desc' | 'seats-desc';

export default function SearchResults({ initialTrips }: { initialTrips: SearchTrip[] }) {
  const [selectedVehicles, setSelectedVehicles] = useState<string[]>([]);
  const [selectedCompanies, setSelectedCompanies] = useState<string[]>([]);
  const [sortOption, setSortOption] = useState<SortOption>('departure-asc');
  const vehicles = Array.from(new Set(initialTrips.map(trip => trip.tenLoaiXe ?? 'Chưa cập nhật')));
  const companies = Array.from(new Map(initialTrips.map(trip => [trip.maNhaXe ?? '', trip.tenNhaXe ?? 'Chưa cập nhật'])).entries());

  const trips = initialTrips.filter(trip =>
    (!selectedVehicles.length || selectedVehicles.includes(trip.tenLoaiXe ?? 'Chưa cập nhật')) &&
    (!selectedCompanies.length || selectedCompanies.includes(trip.maNhaXe ?? ''))
  ).sort((a, b) => {
    const departure = `${a.ngayKhoiHanh}T${a.gioKhoiHanh}`.localeCompare(`${b.ngayKhoiHanh}T${b.gioKhoiHanh}`);
    const price = Number(a.giaVeDuKien) - Number(b.giaVeDuKien);
    switch (sortOption) {
      case 'departure-desc': return -departure;
      case 'price-asc': return price;
      case 'price-desc': return -price;
      case 'seats-desc': return b.soGheTrong - a.soGheTrong;
      default: return departure;
    }
  });

  function toggle(value: string, current: string[], setter: (next: string[]) => void) {
    setter(current.includes(value) ? current.filter(item => item !== value) : [...current, value]);
  }

  return <div className={styles.searchLayout}>
    <aside className={styles.sidebarWrapper}>
      <section className={styles.sidebarBlock}>
        <h2 className={styles.sidebarTitle}>Sắp xếp</h2>
        <div className={styles.filterList}>
          <label className={styles.radioLabel}><input type="radio" name="sort" checked={sortOption === 'departure-asc'} onChange={() => setSortOption('departure-asc')} />Giờ đi sớm nhất</label>
          <label className={styles.radioLabel}><input type="radio" name="sort" checked={sortOption === 'departure-desc'} onChange={() => setSortOption('departure-desc')} />Giờ đi muộn nhất</label>
          <label className={styles.radioLabel}><input type="radio" name="sort" checked={sortOption === 'price-asc'} onChange={() => setSortOption('price-asc')} />Giá tăng dần</label>
          <label className={styles.radioLabel}><input type="radio" name="sort" checked={sortOption === 'price-desc'} onChange={() => setSortOption('price-desc')} />Giá giảm dần</label>
          <label className={styles.radioLabel}><input type="radio" name="sort" checked={sortOption === 'seats-desc'} onChange={() => setSortOption('seats-desc')} />Còn nhiều chỗ</label>
        </div>
      </section>
      <section className={styles.sidebarBlock}>
        <h2 className={styles.sidebarTitle}>Lọc theo dữ liệu chuyến</h2>
        {vehicles.length > 0 && <div className={styles.filterGroup}><h3 className={styles.filterTitle}>Loại xe</h3><div className={styles.filterList}>
          {vehicles.map(vehicle => <label key={vehicle} className={styles.filterLabel}><input type="checkbox" checked={selectedVehicles.includes(vehicle)} onChange={() => toggle(vehicle, selectedVehicles, setSelectedVehicles)} />{vehicle}</label>)}
        </div></div>}
        {companies.length > 0 && <div className={styles.filterGroup}><h3 className={styles.filterTitle}>Nhà xe</h3><div className={styles.filterList}>
          {companies.map(([id, name]) => <label key={id} className={styles.filterLabel}><input type="checkbox" checked={selectedCompanies.includes(id)} onChange={() => toggle(id, selectedCompanies, setSelectedCompanies)} />{name}</label>)}
        </div></div>}
        {(selectedVehicles.length > 0 || selectedCompanies.length > 0) && <button type="button" className={styles.clearFilterBtn} onClick={() => { setSelectedVehicles([]); setSelectedCompanies([]); }}>Xóa bộ lọc</button>}
      </section>
    </aside>
    <section className={styles.mainContent} aria-live="polite">
      {trips.length ? trips.map(trip => <TripCard key={trip.maChuyen} trip={trip} />) : <div className={styles.emptyState}><h2>Không có chuyến đang nhận đặt phù hợp</h2><p>Hãy đổi ngày, điểm đi, điểm đến hoặc bộ lọc để tìm lại.</p></div>}
    </section>
  </div>;
}
