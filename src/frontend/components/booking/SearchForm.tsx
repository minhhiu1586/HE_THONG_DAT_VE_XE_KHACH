'use client';

import { type FormEvent, useEffect, useState } from 'react';
import { getSearchOptions, type SearchOption } from '@/lib/api';
import { clearValidation, vietnameseValidation } from '@/lib/form-validation';
import LocationPicker from './LocationPicker';
import SearchDateField from './SearchDateField';
import type { SearchValues } from '../../types';

const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());

export default function SearchForm({ onSubmit, initial }: { onSubmit: (values: SearchValues) => void; initial?: SearchValues }) {
  const [values, setValues] = useState<SearchValues>(initial ?? { from: '', to: '', date: today() });
  const [options, setOptions] = useState<SearchOption[]>([]);
  const [optionError, setOptionError] = useState('');
  const [formError, setFormError] = useState('');
  useEffect(() => {
    let active = true;
    getSearchOptions().then(data => { if (active) setOptions(data); })
      .catch(() => { if (active) setOptionError('Không thể tải địa điểm. Vui lòng tải lại trang.'); });
    return () => { active = false; };
  }, []);
  const cities = options.filter(option => option.kind === 'CITY');
  const resolve = (value: string) => {
    const selected = options.find(option => option.value === value || option.aliases?.includes(value)
      || option.label.toLocaleLowerCase('vi') === value.trim().toLocaleLowerCase('vi'));
    const cityValue = selected?.kind === 'STATION' ? selected.cityValue : selected?.value;
    return cities.find(city => city.value === cityValue || (cityValue && city.aliases?.includes(cityValue)))?.value ?? '';
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const from = resolve(values.from), to = resolve(values.to);
    if (!from || !to) { setFormError('Hãy chọn điểm đi và điểm đến từ danh sách gợi ý.'); return; }
    if (from === to) { setFormError('Điểm đi và điểm đến phải khác nhau.'); return; }
    if (!values.date || values.date < today()) { setFormError('Hãy chọn ngày đi từ hôm nay trở đi.'); return; }
    setFormError(''); onSubmit({ from, to, date: values.date });
  };
  const update = (field: 'from' | 'to', value: string) => { setValues(current => ({ ...current, [field]: value })); setFormError(''); };
  return <form className="search-form" onInvalid={vietnameseValidation} onInput={clearValidation} onSubmit={submit}>
    <LocationPicker label="Điểm đi" placeholder="Bạn xuất phát từ đâu?" value={resolve(values.from)} options={cities} disabled={!cities.length} onChange={value => update('from', value)} />
    <button className="swap search-swap" type="button" aria-label="Đổi điểm đi và điểm đến" onClick={() => { setValues(current => ({ ...current, from: current.to, to: current.from })); setFormError(''); }}>↔</button>
    <LocationPicker label="Điểm đến" placeholder="Bạn muốn đến đâu?" value={resolve(values.to)} options={cities} disabled={!cities.length} onChange={value => update('to', value)} />
    <SearchDateField value={values.date} min={today()} onChange={date => setValues(current => ({ ...current, date }))} />
    <button className="primary-button search-submit" type="submit" disabled={!options.length}>Tìm chuyến</button>
    {(optionError || formError) && <small className="field-error search-error" role="alert">{optionError || formError}</small>}
  </form>;
}
