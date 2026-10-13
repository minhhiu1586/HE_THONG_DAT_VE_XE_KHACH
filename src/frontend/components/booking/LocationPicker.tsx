'use client';

import { useId, useState } from 'react';
import type { SearchOption } from '@/lib/api';
import { normalizeLocation } from '@/lib/search-locations';

export default function LocationPicker({ label, placeholder, value, options, disabled, onChange }: {
  label: string; placeholder: string; value: string; options: SearchOption[]; disabled?: boolean; onChange: (value: string) => void;
}) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState<string | null>(null);
  const [active, setActive] = useState(0);
  const selected = options.find(option => option.value === value || option.aliases?.includes(value));
  const normalized = normalizeLocation(query ?? '');
  const matches = options.filter(option => normalizeLocation(option.label + ' ' + (option.detail ?? '')).includes(normalized));
  const visible = matches.slice(0, 20);
  const index = Math.min(active, Math.max(0, visible.length - 1));
  const choose = (option: SearchOption) => { onChange(option.value); setQuery(null); setOpen(false); };
  return <div className="search-location">
    <label htmlFor={id}>{label}</label>
    <input id={id} type="text" role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls={id + '-options'}
      aria-activedescendant={open && visible.length ? id + '-option-' + index : undefined}
      autoComplete="off" required disabled={disabled} placeholder={placeholder} value={query ?? selected?.label ?? ''}
      onFocus={() => { setOpen(true); setActive(0); }} onBlur={() => setOpen(false)}
      onChange={event => { setQuery(event.target.value); onChange(''); setOpen(true); setActive(0); }}
      onKeyDown={event => {
        if (event.key === 'Escape') { setOpen(false); return; }
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
          event.preventDefault(); setOpen(true);
          setActive(current => event.key === 'ArrowDown' ? Math.min(current + 1, visible.length - 1) : Math.max(current - 1, 0));
        }
        if (event.key === 'Enter' && open) { event.preventDefault(); if (visible[index]) choose(visible[index]); }
      }} />
    {open && <div className="location-suggestions" id={id + '-options'} role="listbox" aria-label={label}>
      {visible.map((option, itemIndex) => <div key={option.value} id={id + '-option-' + itemIndex} role="option"
        aria-selected={itemIndex === index} className={itemIndex === index ? 'location-option active' : 'location-option'}
        onMouseDown={event => event.preventDefault()} onMouseEnter={() => setActive(itemIndex)} onClick={() => choose(option)}>
        <div><strong>{option.label}</strong><small>{option.kind === 'CITY' ? 'Tỉnh / thành phố' : 'Bến xe'}</small></div>
        {option.kind === 'STATION' && option.detail && <p>{option.detail}</p>}
      </div>)}
      {!visible.length && <p className="location-empty">Không có địa điểm phù hợp.</p>}
      {matches.length > visible.length && <p className="location-empty">Nhập thêm để thu hẹp danh sách.</p>}
    </div>}
  </div>;
}
