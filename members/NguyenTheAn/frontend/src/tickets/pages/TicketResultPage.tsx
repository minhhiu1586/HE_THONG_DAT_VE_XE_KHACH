'use client';
import Link from 'next/link';
import OrderCode from '@members/NguyenTheAn/booking/components/OrderCode';
import RefundPanel from '@members/NguyenTheAn/refund-request/components/RefundPanel';
import { useParams, useSearchParams } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { bookingApi, money, type BookingOrder, type Payment, type PaymentMethod } from '@members/NguyenTheAn/booking/lib/booking-api';
import { ApiError } from '@members/NguyenMinhHieu/account/lib/account-api';
import { getTripSeats } from '@members/NguyenTheAn/booking/lib/api';
import styles from './page.module.css';

export default function SuccessPage() {
 const { maChuyen } = useParams<{ maChuyen: string }>();
 const params = useSearchParams();
 const orderId = params.get('order');
 const { user, loading, request } = useAuth();
 const [storedOrder, setOrder] = useState<BookingOrder>();
 const [loadedFor, setLoadedFor] = useState('');
 const [expiredFor, setExpiredFor] = useState('');
 const [labels, setLabels] = useState<Record<string,string>>({});
 const accountKey = user ? user.maNguoiDung + ':' + orderId : '';
 const order = accountKey && loadedFor === accountKey ? storedOrder : undefined;
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
  if (['DA_HUY','HET_HAN'].includes(detail.trangThaiDon)) return { detail, current: undefined, supported: [], labels: {} };
  const history = await api.payments(orderId);
  const current = history.items.find(p => p.trangThai === 'CHO_XU_LY') ?? history.items.toSorted((a,b) => (Date.parse(b.thoiDiemTao ?? '') || 0) - (Date.parse(a.thoiDiemTao ?? '') || 0))[0];
  const available = detail.trangThaiDon === 'CHO_THANH_TOAN' ? await api.methods() : [];
  const supported = available.filter(m => m.maPhuongThuc === (Number(detail.giaDaChot?.tongThanhToan) === 0 && detail.giaDaChot ? 'MIEN_PHI' : 'VNPAY'));
  const seats = await getTripSeats(maChuyen).catch(() => null);
  return { detail, current, supported, labels: seats ? Object.fromEntries(seats.danhSachGhe.map(seat => [seat.maGhe, seat.viTri])) : {} };
 }, [request, orderId, maChuyen]);
 const refresh = useCallback(async () => {
  const data = await read();
  if (!data) return;
  setOrder(data.detail); setLoadedFor(accountKey); setLabels(data.labels); setPayment(data.current); setMethods(data.supported); setMethod(data.supported[0]?.maPhuongThuc ?? ''); setError('');
 }, [read, accountKey]);
 useEffect(() => {
  if (loading || !user || !orderId) return;
  let active = true;
  read().then(data => { if (active && data) { setOrder(data.detail); setLoadedFor(accountKey); setLabels(data.labels); setPayment(data.current); setMethods(data.supported); setMethod(data.supported[0]?.maPhuongThuc ?? ''); setError(''); } }).catch(e => { if (active) { if (e instanceof ApiError && e.code === 'HOLD_EXPIRED') setExpiredFor(accountKey); else setError(e instanceof Error ? e.message : 'Không thể tải đơn.'); } });
  return () => { active = false; };
 }, [loading, user, orderId, read, accountKey]);
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
   if (e instanceof ApiError && e.code === 'HOLD_EXPIRED') { setExpiredFor(accountKey); setError(''); }
   else setError(e instanceof ApiError && (e.status === 404 || e.status === 405) ? 'Máy chủ chưa cập nhật API thanh toán. Vui lòng liên hệ quản trị viên.' : e instanceof Error ? e.message : 'Không thể tạo giao dịch.');
  } finally { setBusy(false); }
 }
 async function verify() {
  if (busy) return;
  setBusy(true); setError('');
  try {
   if (payment?.trangThai === 'CHO_XU_LY') setPayment(await bookingApi(request).verify(payment.maGiaoDich));
   await refresh();
  } catch(e) { if (e instanceof ApiError && e.code === 'HOLD_EXPIRED') { setExpiredFor(accountKey); setError(''); } else setError(e instanceof Error ? e.message : 'Chưa thể xác minh thanh toán.'); }
  finally { setBusy(false); }
 }
 const paid = order?.trangThaiDon === 'DA_THANH_TOAN' && Boolean(order.danhSachVe.length);
 const closed = order && !['CHO_XU_LY','CHO_THANH_TOAN'].includes(order.trangThaiDon);
 const pending = payment?.trangThai === 'CHO_XU_LY';
 const ended = order && ['DA_HUY','HET_HAN'].includes(order.trangThaiDon);
 const expired = Boolean(accountKey) && expiredFor === accountKey;
 const anyRefund = order?.danhSachVe.some(ticket => ticket.dangHoan || ticket.trangThai === 'DA_HOAN');
 return <main className="page booking-payment">
  <ol className="seat-steps" aria-label="Các bước đặt vé"><li><span>1</span><strong>Chọn ghế</strong></li><li><span>2</span><strong>Thông tin hành khách</strong></li><li className="active" aria-current="step"><span>3</span><strong>Thanh toán</strong></li></ol>
  <div className={'page-title ' + styles.heading}><div><h1>{expired ? 'Đơn đã hết hạn' : ended ? 'Đơn đã kết thúc' : paid ? 'Chi tiết vé của bạn' : 'Thanh toán đặt vé'}</h1><p>{expired || ended ? 'Đơn này không còn ở bước thanh toán.' : paid ? 'Quản lý vé điện tử và theo dõi yêu cầu hoàn tiền.' : 'Vé chỉ được phát hành sau khi xác minh thanh toán.'}</p></div>{paid && <a className="outline-button" href="#refund">Hoàn vé & hoàn tiền</a>}</div>
  {error && <p className="form-error" role="alert">{error}</p>}
  {loading && <p role="status">Đang kiểm tra phiên đăng nhập…</p>}
  {!loading && !user && <Link className="primary-button" href={'/dang-nhap?next=' + encodeURIComponent('/tim-chuyen/' + maChuyen + '/success?order=' + (orderId ?? ''))}>Đăng nhập để xem đơn</Link>}
  {!orderId && <p>Thiếu mã đơn đặt vé.</p>}
  {user && (expired || ended) && <section className={styles.ended} role="status"><span className={styles.badge}>{expired || order?.trangThaiDon === 'HET_HAN' ? 'Hết hạn giữ chỗ' : 'Đã hủy'}</span><h2>Đơn đã kết thúc</h2><p>Ghế giữ cho đơn này đã được giải phóng. Bạn có thể xem lịch sử hoặc tìm chuyến mới để đặt lại.</p><div className={styles.actions}><Link className="primary-button" href="/tai-khoan">Về lịch sử đặt vé</Link><Link className="outline-button" href="/trips">Tìm chuyến mới</Link></div></section>}
  {user && orderId && !order && !expired && !error && !loading && <p role="status">Đang tải thông tin đơn…</p>}
  {order && !ended && !expired && <div className="checkout-layout"><section className="checkout-form-card"><h2>{paid ? 'Vé của bạn' : 'Thông tin đơn'}</h2><dl className="payment-info"><div><dt>Mã đơn</dt><dd><OrderCode value={order.maDon} /></dd></div><div><dt>Người đặt</dt><dd>{order.khachDatTen}</dd></div><div><dt>Điện thoại</dt><dd>{order.khachDatSoDienThoai}</dd></div><div><dt>Email nhận vé</dt><dd>{order.khachDatEmail ?? 'Chưa cung cấp'}</dd></div><div><dt>Trạng thái</dt><dd>{paid ? 'Đã thanh toán' : pending ? 'Đang chờ xác minh' : order.trangThaiDon === 'CHO_THANH_TOAN' ? 'Chờ thanh toán' : 'Chưa chốt thông tin'}</dd></div></dl>
   {paid && order.emailXacNhan && <div className="receipt-email-status" role="status"><strong>Email xác nhận</strong><p>{order.emailXacNhan.trangThai === 'SENT' ? 'Biên nhận và vé đã được gửi tới ' + order.emailXacNhan.diaChi + '. Vui lòng kiểm tra cả thư mục Spam.' : ['FAILED','UNKNOWN'].includes(order.emailXacNhan.trangThai) ? 'Chưa thể xác nhận gửi email. Vé vẫn hợp lệ; bạn có thể lưu thông tin vé bên dưới hoặc liên hệ hỗ trợ.' : 'Đang gửi biên nhận và vé tới ' + order.emailXacNhan.diaChi + '.'}</p></div>}
   {payment?.canDoiSoat && <p role="alert" className="form-error">Đã ghi nhận thanh toán nhưng chưa phát hành vé. Vui lòng liên hệ hỗ trợ và cung cấp mã giao dịch {payment.maGiaoDich}.</p>}
   {paid && order.danhSachVe.map(ticket => <article className="issued-ticket ticket-with-qr" key={ticket.maVe}><div><div className={styles.ticketHeading}><span className="ticket-label">VÉ ĐIỆN TỬ</span><span className={styles.badge}>{ticket.dangHoan ? 'Đang xử lý hoàn' : ticket.trangThai === 'DA_HOAN' ? 'Đã hoàn vé' : ticket.trangThai === 'DA_HUY' ? 'Đã hủy vé' : ticket.trangThai === 'DA_LEN_XE' ? 'Đã lên xe' : 'Có hiệu lực'}</span></div><h3>{ticket.hoTen}</h3><p>Mã vé: <strong className="ticket-code">{ticket.maTraCuu ?? ticket.maVe}</strong></p><p>Ghế: {labels[ticket.maGhe] ?? ticket.maGhe} · Đã trả {money(ticket.giaVe)}</p><small>{ticket.dangHoan ? 'QR tạm ẩn trong thời gian xử lý hoàn tiền.' : ticket.trangThai === 'DA_HOAN' || ticket.trangThai === 'DA_HUY' ? 'Vé không còn hiệu lực để lên xe.' : 'Lưu QR để xuất trình cùng thông tin vé.'}</small></div>{ticket.qrImage && !ticket.dangHoan && ['DA_XUAT_VE','DA_LEN_XE'].includes(ticket.trangThai) && <div className="ticket-qr">{/* QR is an authenticated API-generated data URI, rendered without an image proxy. */}
{/* eslint-disable-next-line @next/next/no-img-element */}
<img src={ticket.qrImage} alt={'QR vé ' + (ticket.maTraCuu ?? ticket.maVe)} width={160} height={160} /><a download={'Ve-' + (ticket.maTraCuu ?? ticket.maVe) + '.png'} href={ticket.qrImage}>Tải QR vé</a></div>}</article>)}
   {!paid && order.trangThaiDon === 'CHO_XU_LY' && <Link className="primary-button" href={'/tim-chuyen/' + encodeURIComponent(maChuyen) + '/checkout?order=' + encodeURIComponent(order.maDon)}>Hoàn tất thông tin hành khách</Link>}
   {payment && <p className="payment-reference">Giao dịch: {payment.maGiaoDich}{payment.trangThai === 'THAT_BAI' ? ' · Thanh toán không thành công' : ''}</p>}
  </section><aside className="summary-card"><span className="eyebrow">{paid ? 'ĐÃ THANH TOÁN BAN ĐẦU' : 'TỔNG THANH TOÁN'}</span><h2>{money(order.giaDaChot?.tongThanhToan ?? (paid ? payment?.soTien : order.giaTamTinh?.tongThanhToan))}</h2>{paid && anyRefund && <p>Số tiền và trạng thái hoàn được cập nhật riêng trong mục hoàn tiền bên dưới.</p>}
   {!paid && !closed && order.giaDaChot && !pending && <><label>Phương thức thanh toán<select value={method} disabled={busy} onChange={e => { setMethod(e.target.value); attempt.current = null; }}>{methods.map(item => <option key={item.maPhuongThuc} value={item.maPhuongThuc}>{item.tenPhuongThuc}</option>)}</select></label>{!methods.length && <p>Chưa có phương thức thanh toán phù hợp.</p>}<button className="primary-button" disabled={busy || !method} onClick={() => void pay()}>{busy ? 'Đang xử lý…' : method === 'MIEN_PHI' ? 'Xác nhận vé miễn phí' : 'Thanh toán qua VNPAY'}</button></>}
   {pending && !paid && <><p>Giao dịch đang chờ kết quả. Nếu đã hoàn tất tại VNPAY, hãy kiểm tra lại thanh toán.</p>{payment.urlThanhToan && <a className="primary-button" href={payment.urlThanhToan}>Tiếp tục tại VNPAY</a>}</>}
   <button className="outline-button" disabled={busy} onClick={() => void verify()}>{busy ? 'Đang kiểm tra…' : paid ? 'Làm mới đơn' : 'Kiểm tra thanh toán'}</button><Link className="outline-button" href="/tai-khoan">Lịch sử đặt vé</Link>
  </aside></div>}
  {paid && order && <div id="refund" className={styles.refundArea}><RefundPanel order={order} request={request} onUpdated={refresh} labels={labels} /></div>}
 </main>;
}
