"use client";

import { useState } from 'react';
import styles from '@members/NguyenTheAn/booking/components/OrderCode.module.css';

export default function OrderCode({ value }: { value: string }) {
  const [expanded, setExpanded] = useState(false);
  const [notice, setNotice] = useState('');
  const compact = value.length > 26;
  async function copy() {
    try { await navigator.clipboard.writeText(value); setNotice('Đã sao chép mã đơn'); }
    catch { setExpanded(true); setNotice('Vui lòng chọn và sao chép mã đơn bên trên.'); }
  }
  return <div className={styles.container}>
    <code className={styles.code} title={value}>{compact && !expanded ? value.slice(0, 8) + '…' + value.slice(-6) : value}</code>
    <div className={styles.actions}><button type="button" onClick={() => void copy()}>Sao chép</button>{compact && <button type="button" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>{expanded ? 'Thu gọn' : 'Xem đầy đủ'}</button>}</div>
    {notice && <small className={styles.notice} role="status">{notice}</small>}
  </div>;
}
