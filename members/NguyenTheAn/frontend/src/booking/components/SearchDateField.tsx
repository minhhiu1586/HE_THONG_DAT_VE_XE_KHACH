'use client';

import { useId } from 'react';

export default function SearchDateField({ value, min, onChange }: { value: string; min: string; onChange: (value: string) => void }) {
  const id = useId();
  const display = /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.split('-').reverse().join('/') : 'dd/mm/yyyy';
  return <div className="search-date">
    <label htmlFor={id}>Ngày đi</label>
    <div className="search-date-control">
      <span className="search-date-value" aria-hidden="true">{display}</span>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
        <rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 11h18" />
      </svg>
      <input id={id} className="search-date-native" type="date" required aria-label="Ngày đi" value={value} min={min}
        onChange={event => onChange(event.target.value)}
        onClick={event => { try { event.currentTarget.showPicker?.(); } catch {} }} />
    </div>
  </div>;
}
