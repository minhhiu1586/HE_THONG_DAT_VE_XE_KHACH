'use client';
import type { FormEventHandler } from 'react';
import type { Form, Operator } from '../lib/operator-types';
import styles from './OperatorManagement.module.css';
type Props = {
  form: Form;
  setForm: (form: Form) => void;
  selected: Operator | null;
  busy: boolean;
  save: FormEventHandler<HTMLFormElement>;
};
export default function OperatorForm({ form, setForm, selected, busy, save }: Props) {
  return (
    <form className={styles.form} onSubmit={save}>
      <label>
        Mã nhà xe
        <input
          required
          maxLength={40}
          disabled={!!selected || busy}
          value={form.maNhaXe}
          onChange={(e) => setForm({ ...form, maNhaXe: e.target.value })}
        />
      </label>
      <label>
        Tên nhà xe
        <input
          required
          maxLength={160}
          disabled={busy}
          value={form.tenNhaXe}
          onChange={(e) => setForm({ ...form, tenNhaXe: e.target.value })}
        />
      </label>
      <label>
        Hotline
        <input
          maxLength={30}
          disabled={busy}
          value={form.hotline}
          onChange={(e) => setForm({ ...form, hotline: e.target.value })}
        />
      </label>
      <label>
        Email
        <input
          type="email"
          maxLength={254}
          disabled={busy}
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
        />
      </label>
      <label>
        Địa chỉ
        <input
          maxLength={255}
          disabled={busy}
          value={form.diaChi}
          onChange={(e) => setForm({ ...form, diaChi: e.target.value })}
        />
      </label>
      <label>
        Ngày thành lập
        <input
          type="date"
          disabled={busy}
          value={form.ngayThanhLap}
          onChange={(e) => setForm({ ...form, ngayThanhLap: e.target.value })}
        />
      </label>
      <label>
        URL logo
        <input
          type="url"
          maxLength={500}
          disabled={busy}
          value={form.logoUrl}
          onChange={(e) => setForm({ ...form, logoUrl: e.target.value })}
        />
      </label>
      <label className={styles.wide}>
        Mô tả
        <textarea
          maxLength={1000}
          disabled={busy}
          value={form.moTa}
          onChange={(e) => setForm({ ...form, moTa: e.target.value })}
        />
      </label>
      <button type="submit" disabled={busy}>
        {selected ? 'Lưu thay đổi' : 'Tạo nhà xe'}
      </button>
    </form>
  );
}
