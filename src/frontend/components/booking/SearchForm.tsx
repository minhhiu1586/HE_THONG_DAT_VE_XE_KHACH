"use client";

import { FormEvent, useEffect, useState } from "react";

import { getSearchOptions, type SearchOption } from '@/lib/api';
import { clearValidation, vietnameseValidation } from '@/lib/form-validation';
import type { SearchValues } from "../../types";

export default function SearchForm({
  onSubmit,
  initial,
}: {
  onSubmit: (values: SearchValues) => void;
  initial?: SearchValues;
}) {
  const [values, setValues] = useState<SearchValues>(
    initial ?? {
      from: "",
      to: "",
      date: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()),
    },
  );
  const [options, setOptions] = useState<SearchOption[]>([]);
  const [optionError, setOptionError] = useState('');

  useEffect(() => {
    let active = true;
    getSearchOptions().then(data => { if (active) setOptions(data); })
      .catch(() => { if (active) setOptionError('Không thể tải điểm đi/đến. Vui lòng tải lại trang.'); });
    return () => { active = false; };
  }, []);

  const resolve = (value: string) =>
    options.find(option => option.value === value || option.label.toLowerCase() === value.trim().toLowerCase()
      || option.value.split(':')[1]?.toLowerCase() === value.trim().toLowerCase())?.value ?? value.trim();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!values.from || !values.to || !values.date || resolve(values.from) === resolve(values.to)) {
      return;
    }
    onSubmit({
      ...values,
      from: resolve(values.from),
      to: resolve(values.to),
    });
  };

  return (
    <form className="search-form" onInvalid={vietnameseValidation} onInput={clearValidation} onSubmit={submit}>
      <label>
        <span>Điểm đi</span>
        <select
          aria-label="Điểm đi"
          required
          value={resolve(values.from)}
          onChange={(e) => setValues({ ...values, from: e.target.value })}
        >
          <option value="">Chọn điểm đi</option>
          <optgroup label="Tỉnh / thành phố">{options.filter(option => option.kind === 'CITY').map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</optgroup>
          <optgroup label="Bến xe">{options.filter(option => option.kind === 'STATION').map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</optgroup>
        </select>
      </label>
      <div className="swap">↔</div>
      <label>
        <span>Điểm đến</span>
        <select
          aria-label="Điểm đến"
          required
          value={resolve(values.to)}
          onChange={(e) => setValues({ ...values, to: e.target.value })}
        >
          <option value="">Chọn điểm đến</option>
          <optgroup label="Tỉnh / thành phố">{options.filter(option => option.kind === 'CITY').map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</optgroup>
          <optgroup label="Bến xe">{options.filter(option => option.kind === 'STATION').map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</optgroup>
        </select>
      </label>
      {optionError && <small className="field-error" role="alert">{optionError}</small>}
      <label>
        <span>Ngày đi</span>
        <input
          required
          type="date"
          aria-label="Ngày đi"
          min={new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())}
          value={values.date}
          onInput={(e) => setValues({ ...values, date: e.currentTarget.value })}
        />
      </label>
      <button className="primary-button" type="submit">
        Tìm chuyến <span>→</span>
      </button>
      {values.from && values.to && resolve(values.from) === resolve(values.to) && (
        <small className="field-error">
          Điểm đi và điểm đến phải khác nhau.
        </small>
      )}
    </form>
  );
}
