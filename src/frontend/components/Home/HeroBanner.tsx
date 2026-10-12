'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type { SearchOption } from '@/lib/api';
import styles from './Home.module.css';

function dateInVietnam(daysToAdd = 0) {
  const date = new Date(Date.now() + daysToAdd * 86_400_000);
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(date);
  const value = (type: string) => parts.find(part => part.type === type)?.value ?? '';
  return `${value('year')}-${value('month')}-${value('day')}`;
}

function LocationOptions({ options }: { options: SearchOption[] }) {
  return <>
    <option value="">Chọn tỉnh/thành hoặc bến xe</option>
    <optgroup label="Tỉnh/thành phố">
      {options.filter(option => option.kind === 'CITY').map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </optgroup>
    <optgroup label="Bến xe đang hoạt động">
      {options.filter(option => option.kind === 'STATION').map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </optgroup>
  </>;
}

export default function HeroBanner({ options, loadError }: { options: SearchOption[]; loadError?: string }) {
  const router = useRouter();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState(() => dateInVietnam());
  const minDate = dateInVietnam();

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = new URLSearchParams({ from, to, date });
    router.push(`/tim-chuyen?${query.toString()}`);
  }

  return (
    <div className={styles.heroBanner}>
      <div className={styles.heroOverlay}>
        <div className="container">
          <div className={styles.heroContent}>
            <h1 className={styles.heroTitle}>Tìm chuyến xe khách phù hợp với hành trình của bạn</h1>
            <form className={styles.searchForm} onSubmit={submit}>
              <div className={styles.searchInputs}>
                <div className={styles.inputGroup}>
                  <span className={styles.inputIcon}>📍</span>
                  <div className={styles.inputField}>
                    <label htmlFor="home-from">Nơi xuất phát</label>
                    <select id="home-from" value={from} onChange={event => setFrom(event.target.value)} required disabled={!options.length}>
                      <LocationOptions options={options} />
                    </select>
                  </div>
                </div>
                <button type="button" className={styles.inputDivider} onClick={() => { setFrom(to); setTo(from); }} aria-label="Đổi chiều">⇄</button>
                <div className={styles.inputGroup}>
                  <span className={styles.inputIcon}>🎯</span>
                  <div className={styles.inputField}>
                    <label htmlFor="home-to">Nơi đến</label>
                    <select id="home-to" value={to} onChange={event => setTo(event.target.value)} required disabled={!options.length}>
                      <LocationOptions options={options} />
                    </select>
                  </div>
                </div>
                <div className={styles.inputGroup}>
                  <span className={styles.inputIcon}>📅</span>
                  <div className={styles.inputField}>
                    <label htmlFor="home-date">Ngày đi</label>
                    <input id="home-date" type="date" value={date} min={minDate} onChange={event => setDate(event.target.value)} required />
                  </div>
                </div>
              </div>
              <button type="submit" className={styles.searchBtn} disabled={!options.length}>Tìm chuyến</button>
            </form>
            {loadError && <p role="alert">Không tải được danh mục tìm kiếm: {loadError}</p>}
          </div>
        </div>
      </div>
    </div>
  );
}
