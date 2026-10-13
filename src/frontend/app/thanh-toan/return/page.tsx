'use client';
import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/Account/AuthProvider';
import { bookingApi } from '@/lib/booking-api';
function PaymentReturnContent() {
 const params = useSearchParams(); const query = params.toString(); const router = useRouter();
 const { user, loading, request } = useAuth(); const [error, setError] = useState('');
 useEffect(() => {
  if (loading || !user) return;
  let active = true;
  request<{ maGiaoDich: string }>('/thanh-toan/return/vnpay?' + query).then(async result => {
   const api = bookingApi(request);
   const payment = await api.payment(result.maGiaoDich);
   const order = await api.order(payment.maDon);
   if (active) router.replace('/tim-chuyen/' + encodeURIComponent(order.maChuyen) + '/success?order=' + encodeURIComponent(order.maDon));
  }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Không thể kiểm tra kết quả trả về.'); });
  return () => { active = false; };
 }, [loading, user, query, request, router]);
 return <main className="page booking-payment"><div className="checkout-form-card"><h1>Kết quả thanh toán</h1>{error ? <p className="form-error" role="alert">{error}</p> : <p role="status">{!loading && !user ? 'Vui lòng đăng nhập để xem trạng thái đơn.' : 'Đang kiểm tra kết quả trả về…'}</p>}{!loading && !user && <Link className="primary-button" href={'/dang-nhap?next=' + encodeURIComponent('/thanh-toan/return?' + query)}>Đăng nhập</Link>}<Link href="/home">Về trang chủ</Link></div></main>;
}

export default function PaymentReturn() {
 return <Suspense fallback={<main className="page booking-payment"><p role="status">Đang kiểm tra kết quả thanh toán…</p></main>}><PaymentReturnContent /></Suspense>;
}
