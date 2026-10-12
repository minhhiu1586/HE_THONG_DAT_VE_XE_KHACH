'use client';

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { SearchOption } from '@/lib/api';
import homeStyles from '../Home/Home.module.css';
import { clearValidation, vietnameseValidation } from '@/lib/form-validation';

function LocationOptions({ options }: { options: SearchOption[] }) {
  return <>
    <option value="">Chọn tỉnh/thành hoặc bến xe</option>
    <optgroup label="Tỉnh/thành phố">{options.filter(item => item.kind === 'CITY').map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</optgroup>
    <optgroup label="Bến xe đang hoạt động">{options.filter(item => item.kind === 'STATION').map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</optgroup>
  </>;
}

function todayInVietnam() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const get = (type: string) => parts.find(part => part.type === type)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}`;
}

export default function CompactSearch({
  initialFrom, initialTo, initialDate, options,
}: { initialFrom: string; initialTo: string; initialDate: string; options: SearchOption[] }) {
  const router = useRouter();
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [date, setDate] = useState(initialDate);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    router.push(`/tim-chuyen?${new URLSearchParams({ from, to, date }).toString()}`);
  }

  function resumeEditing(event: React.FormEvent<HTMLFormElement>) {
    clearValidation(event);
    submittingRef.current = false;
    setSubmitting(false);
  }

  return <div style={{ width: '100%', marginBottom: '2rem' }}>
    <form onSubmit={submit} onInvalid={vietnameseValidation} onInput={resumeEditing} onChange={resumeEditing} style={{ display: 'flex', gap: '16px', width: '100%', alignItems: 'center', backgroundColor: 'var(--surface)', padding: '12px', borderRadius: '16px', boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }}>
      <div className={homeStyles.searchInputs} style={{ flex: 1, margin: 0, boxShadow: 'none' }}>
        <div className={homeStyles.inputGroup}><span className={homeStyles.inputIcon}>📍</span><div className={homeStyles.inputField}>
          <label htmlFor="search-from">Nơi xuất phát</label><select id="search-from" data-validation-label="nơi xuất phát" value={from} onChange={event => setFrom(event.target.value)} required disabled={!options.length}><LocationOptions options={options} /></select>
        </div></div>
        <button type="button" className={homeStyles.inputDivider} onClick={() => { setFrom(to); setTo(from); }} aria-label="Đổi chiều">⇄</button>
        <div className={homeStyles.inputGroup}><span className={homeStyles.inputIcon}>🎯</span><div className={homeStyles.inputField}>
          <label htmlFor="search-to">Nơi đến</label><select id="search-to" data-validation-label="nơi đến" value={to} onChange={event => setTo(event.target.value)} required disabled={!options.length}><LocationOptions options={options} /></select>
        </div></div>
        <div className={homeStyles.inputGroup}><span className={homeStyles.inputIcon}>📅</span><div className={homeStyles.inputField}>
          <label htmlFor="search-date">Ngày đi</label><input id="search-date" data-validation-label="ngày đi" type="date" value={date} min={todayInVietnam()} onChange={event => setDate(event.target.value)} required />
        </div></div>
      </div>
      <button type="submit" className={homeStyles.searchBtn} style={{ height: '56px', padding: '0 32px', margin: 0, flexShrink: 0 }} disabled={!options.length || submitting}>{submitting ? 'Đang tìm…' : 'Tìm kiếm'}</button>
    </form>
  </div>;
}
