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

  useEffect(() => {
    getSearchOptions().then(setOptions).catch(() => setOptions([]));
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
        <input
          list="stations"
          required
          value={values.from}
          placeholder="Nhập tỉnh hoặc bến xe"
          onChange={(e) => setValues({ ...values, from: e.target.value })}
        />
      </label>
      <div className="swap">↔</div>
      <label>
        <span>Điểm đến</span>
        <input
          list="stations"
          required
          value={values.to}
          placeholder="Bạn muốn đến đâu?"
          onChange={(e) => setValues({ ...values, to: e.target.value })}
        />
      </label>
      <datalist id="stations">
        {options.map((option) => (
          <option key={option.value} value={option.label}>
            {option.value}
          </option>
        ))}
      </datalist>
      <label>
        <span>Ngày đi</span>
        <input
          required
          type="date"
          min={new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())}
          value={values.date}
          onChange={(e) => setValues({ ...values, date: e.target.value })}
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
