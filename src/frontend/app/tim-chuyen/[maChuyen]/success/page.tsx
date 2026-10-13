'use client';
import Link from 'next/link';
import OrderCode from '@/components/booking/OrderCode';
import { useParams, useSearchParams } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/components/Account/AuthProvider';
import { bookingApi, money, type BookingOrder, type Payment, type PaymentMethod } from '@/lib/booking-api';
import { ApiError } from '@/lib/account-api';

export default function SuccessPage() {
 const { maChuyen } = useParams<{ maChuyen: string }>();
 const params = useSearchParams();
 const orderId = params.get('order');
 const { user, loading, request } = useAuth();
 const [order, setOrder] = useState<BookingOrder>();
 const [payment, setPayment] = useState<Payment>();
 const [methods, setMethods] = useState<PaymentMethod[]>([]);
 const [method, setMethod] = useState('VNPAY');
 const [busy, setBusy] = useState(false);
 const [error, setError] = useState('');
 const attempt = useRef<{ body: string; key: string } | null>(null);
 const read = useCallback(async () => {
  if (!orderId) return;
  const api = bookingApi(request);
  const detail = await api.order(orderId);
  if (detail.maChuyen !== maChuyen) throw new Error('Đơn không thuộc chuyến này.');
  const history = await api.payments(orderId);
  const current = history.items.find(p => p.trangThai === 'CHO_XU_LY') ?? history.items.toSorted((a,b) => (Date.parse(b.thoiDiemTao ?? '') || 0) - (Date.parse(a.thoiDiemTao ?? '') || 0))[0];
  const available = await api.methods();
  const supported = available.filter(m => m.maPhuongThuc === (Number(detail.giaDaChot?.tongThanhToan) === 0 && detail.giaDaChot ? 'MIEN_PHI' : 'VNPAY'));
  return { detail, current, supported };
 }, [request, orderId, maChuyen]);
 async function refresh() {
  const data = await read();
  if (!data) return;
  setOrder(data.detail); setPayment(data.current); setMethods(data.supported); setMethod(data.supported[0]?.maPhuongThuc ?? '');
 }
 useEffect(() => {
  if (loading || !user || !orderId) return;
  let active = true;
  read().then(data => { if (active && data) { setOrder(data.detail); setPayment(data.current); setMethods(data.supported); setMethod(data.supported[0]?.maPhuongThuc ?? ''); } }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Không thể tải đơn.'); });
  return () => { active = false; };
 }, [loading, user, orderId, read]);
 const emailChecks = useRef({ orderId: '', count: 0 });
 useEffect(() => {
  if (!order || order.trangThaiDon !== 'DA_THANH_TOAN' || !order.emailXacNhan || !['PENDING','SENDING'].includes(order.emailXacNhan.trangThai)) return;
  if (emailChecks.current.orderId !== order.maDon) emailChecks.current = { orderId: order.maDon, count: 0 };
  if (emailChecks.current.count >= 12) return;
  let active = true;
  const timer = window.setTimeout(() => {
   emailChecks.current.count++;
   bookingApi(request).order(order.maDon).then(next => { if (active) setOrder(next); }).catch(() => {});
  }, 5000);
  return () => { active = false; window.clearTimeout(timer); };
 }, [order, request]);
 async function pay() {
  if (!order || !method || busy) return;
  const fingerprint = order.maDon + ':' + method;
  if (attempt.current?.body !== fingerprint) attempt.current = { body: fingerprint, key: crypto.randomUUID() };
  setBusy(true); setError('');
  try {
   const created = await bookingApi(request).pay(order.maDon, method, attempt.current.key);
   const current = await bookingApi(request).payment(created.maGiaoDich);
   setPayment(current);
   if (current.trangThai === 'THAT_BAI') attempt.current = null;
   if (current.urlThanhToan && current.trangThai === 'CHO_XU_LY') {
    const url = new URL(current.urlThanhToan);
    if (url.protocol !== 'https:') throw new Error('Đường dẫn thanh toán không hợp lệ.');
    window.location.assign(url.href);
   } else { await refresh(); }
  } catch(e) {
   setError(e instanceof ApiError && (e.status === 404 || e.status === 405) ? 'Máy chủ chưa cập nhật API thanh toán. Vui lòng liên hệ quản trị viên.' : e instanceof Error ? e.message : 'Không thể tạo giao dịch.');
  } finally { setBusy(false); }
 }
 async function verify() {
  if (busy) return;
  setBusy(true); setError('');
  try {
   if (payment?.trangThai === 'CHO_XU_LY') setPayment(await bookingApi(request).verify(payment.maGiaoDich));
   await refresh();
  } catch(e) { setError(e instanceof Error ? e.message : 'Chưa thể xác minh thanh toán.'); }
  finally { setBusy(false); }
 }
 const paid = order?.trangThaiDon === 'DA_THANH_TOAN' && Boolean(order.danhSachVe.length);
 const closed = order && !['CHO_XU_LY','CHO_THANH_TOAN'].includes(order.trangThaiDon);
 const pending = payment?.trangThai === 'CHO_XU_LY';
 return <main className="page booking-payment">
  <ol className="seat-steps" aria-label="Các bước đặt vé"><li><span>1</span><strong>Chọn ghế</strong></li><li><span>2</span><strong>Thông tin hành khách</strong></li><li className="active" aria-current="step"><span>3</span><strong>Thanh toán</strong></li></ol>
  <div className="page-title"><h1>{paid ? 'Đặt vé thành công' : 'Thanh toán đặt vé'}</h1><p>{paid ? 'Thanh toán đã được xác nhận và vé đã phát hành.' : 'Vé chỉ được phát hành sau khi xác minh thanh toán.'}</p></div>
  {error && <p className="form-error" role="alert">{error}</p>}
  {loading && <p role="status">Đang kiểm tra phiên đăng nhập…</p>}
  {!loading && !user && <Link className="primary-button" href={'/dang-nhap?next=' + encodeURIComponent('/tim-chuyen/' + maChuyen + '/success?order=' + (orderId ?? ''))}>Đăng nhập để xem đơn</Link>}
  {!orderId && <p>Thiếu mã đơn đặt vé.</p>}
  {order && <div className="checkout-layout"><section className="checkout-form-card"><h2>{paid ? 'Vé của bạn' : 'Thông tin đơn'}</h2><dl className="payment-info"><div><dt>Mã đơn</dt><dd><OrderCode value={order.maDon} /></dd></div><div><dt>Người đặt</dt><dd>{order.khachDatTen}</dd></div><div><dt>Điện thoại</dt><dd>{order.khachDatSoDienThoai}</dd></div><div><dt>Email nhận vé</dt><dd>{order.khachDatEmail ?? 'Chưa cung cấp'}</dd></div><div><dt>Trạng thái</dt><dd>{paid ? 'Đã thanh toán' : pending ? 'Đang chờ xác minh' : order.trangThaiDon === 'CHO_THANH_TOAN' ? 'Chờ thanh toán' : order.trangThaiDon === 'CHO_XU_LY' ? 'Chưa chốt thông tin' : 'Đơn đã kết thúc'}</dd></div></dl>
   {paid && order.emailXacNhan && <div className="receipt-email-status" role="status"><strong>Email xác nhận</strong><p>{order.emailXacNhan.trangThai === 'SENT' ? 'Biên nhận và vé đã được gửi tới ' + order.emailXacNhan.diaChi + '. Vui lòng kiểm tra cả thư mục Spam.' : ['FAILED','UNKNOWN'].includes(order.emailXacNhan.trangThai) ? 'Chưa thể xác nhận gửi email. Vé vẫn hợp lệ; bạn có thể lưu thông tin vé bên dưới hoặc liên hệ hỗ trợ.' : 'Đang gửi biên nhận và vé tới ' + order.emailXacNhan.diaChi + '.'}</p></div>}
   {payment?.canDoiSoat && <p role="alert" className="form-error">Đã ghi nhận thanh toán nhưng chưa phát hành vé. Vui lòng liên hệ hỗ trợ và cung cấp mã giao dịch {payment.maGiaoDich}.</p>}
   {paid && order.danhSachVe.map(ticket => <article className="issued-ticket ticket-with-qr" key={ticket.maVe}><div><span className="ticket-label">VÉ ĐIỆN TỬ</span><h3>{ticket.hoTen}</h3><p>Mã vé: <strong className="ticket-code">{ticket.maTraCuu ?? ticket.maVe}</strong></p><p>Ghế: {ticket.maGhe} · {money(ticket.giaVe)}</p><small>Lưu QR để xuất trình cùng thông tin vé.</small></div>{ticket.qrImage && <div className="ticket-qr">{/* QR is an authenticated API-generated data URI, rendered without an image proxy. */}
{/* eslint-disable-next-line @next/next/no-img-element */}
<img src={ticket.qrImage} alt={'QR vé ' + (ticket.maTraCuu ?? ticket.maVe)} width={160} height={160} /><a download={'Ve-' + (ticket.maTraCuu ?? ticket.maVe) + '.png'} href={ticket.qrImage}>Tải QR vé</a></div>}</article>)}
   {!paid && order.trangThaiDon === 'CHO_XU_LY' && <Link className="primary-button" href={'/tim-chuyen/' + encodeURIComponent(maChuyen) + '/checkout?order=' + encodeURIComponent(order.maDon)}>Hoàn tất thông tin hành khách</Link>}
   {payment && <p className="payment-reference">Giao dịch: {payment.maGiaoDich}{payment.trangThai === 'THAT_BAI' ? ' · Thanh toán không thành công' : ''}</p>}
  </section><aside className="summary-card"><span className="eyebrow">TỔNG THANH TOÁN</span><h2>{money(order.giaDaChot?.tongThanhToan ?? (paid ? payment?.soTien : order.giaTamTinh?.tongThanhToan))}</h2>
   {!paid && !closed && order.giaDaChot && !pending && <><label>Phương thức thanh toán<select value={method} disabled={busy} onChange={e => { setMethod(e.target.value); attempt.current = null; }}>{methods.map(item => <option key={item.maPhuongThuc} value={item.maPhuongThuc}>{item.tenPhuongThuc}</option>)}</select></label>{!methods.length && <p>Chưa có phương thức thanh toán phù hợp.</p>}<button className="primary-button" disabled={busy || !method} onClick={() => void pay()}>{busy ? 'Đang xử lý…' : method === 'MIEN_PHI' ? 'Xác nhận vé miễn phí' : 'Thanh toán qua VNPAY'}</button></>}
   {pending && !paid && <><p>Giao dịch đang chờ kết quả. Nếu đã hoàn tất tại VNPAY, hãy kiểm tra lại thanh toán.</p>{payment.urlThanhToan && <a className="primary-button" href={payment.urlThanhToan}>Tiếp tục tại VNPAY</a>}</>}
   <button className="outline-button" disabled={busy} onClick={() => void verify()}>{busy ? 'Đang kiểm tra…' : 'Kiểm tra thanh toán'}</button><Link className="outline-button" href="/home">Về trang chủ</Link>
  </aside></div>}
 </main>;
}
