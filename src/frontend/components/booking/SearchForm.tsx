"use client";

import { FormEvent, useEffect, useState } from "react";

import { api } from "../../services/api";
import type { SearchValues, Station } from "../../types";

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
      date: new Date().toISOString().slice(0, 10),
    },
  );
  const [stations, setStations] = useState<Station[]>([]);

useEffect(() => {
    if (
      initial &&
      (initial.from !== values.from ||
        initial.to !== values.to ||
        initial.date !== values.date)
    ) {
      setValues(initial);
    }
  }, [initial, values.from, values.to, values.date]);

  useEffect(() => {
    api.stations()
      .then(setStations)
      .catch(() => setStations([]));
  }, []);

  useEffect(() => {
    if (!initial || !stations.length) return;
    const label = (value: string) =>
      stations.find(
        (station) =>
          station.maBenXe === value || station.tenBenXe === value,
      )?.tenBenXe ?? value;
    setValues((current) => ({
      ...current,
      from: label(initial.from),
      to: label(initial.to),
    }));
  }, [initial?.from, initial?.to, stations]);

  const resolve = (value: string) =>
    stations.find(
      (station) =>
        station.maBenXe === value ||
        station.tenBenXe.toLowerCase() === value.toLowerCase(),
    )?.maBenXe ?? value;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!values.from || !values.to || !values.date || values.from === values.to) {
      return;
    }
    onSubmit({
      ...values,
      from: resolve(values.from),
      to: resolve(values.to),
    });
  };

  return (
    <form className="search-form" onSubmit={submit}>
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
        {stations.map((station) => (
          <option key={station.maBenXe} value={station.tenBenXe}>
            {station.maBenXe}
          </option>
        ))}
      </datalist>
      <label>
        <span>Ngày đi</span>
        <input
          required
          type="date"
          min={new Date().toISOString().slice(0, 10)}
          value={values.date}
          onChange={(e) => setValues({ ...values, date: e.target.value })}
        />
      </label>
      <button className="primary-button" type="submit">
        Tìm chuyến <span>→</span>
      </button>
      {values.from && values.to && values.from === values.to && (
        <small className="field-error">
          Điểm đi và điểm đến phải khác nhau.
        </small>
      )}
    </form>
  );
}