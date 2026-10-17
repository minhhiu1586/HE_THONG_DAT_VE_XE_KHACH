'use client';
import Link from 'next/link';
import OrderCode from '@members/NguyenTheAn/booking/components/OrderCode';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { bookingApi, money, type BookingOrder, type BookingQuote } from '@members/NguyenTheAn/booking/lib/booking-api';
import { getTripSeats } from '@members/NguyenTheAn/booking/lib/api';
import { useBookingStore } from '@members/NguyenTheAn/booking/store/useBookingStore';

export default function CheckoutPage() {
  const { maChuyen } = useParams<{ maChuyen: string }>();
  const params = useSearchParams();
  const booking = useBookingStore();
  const orderId = params.get('order') || booking.orderId;
  const { user, loading: authLoading, request } = useAuth();
  const router = useRouter();
  const [order, setOrder] = useState<BookingOrder>();
  const [labels, setLabels] = useState<Record<string,string>>({});
  const [quote, setQuote] = useState<BookingQuote>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const attempt = useRef<{ body: string; key: string } | null>(null);
  const freezeAttempt = useRef<{ quote: string; key: string } | null>(null);
  useEffect(() => {
    if (authLoading || !user || !orderId) return;
    let active = true;
    bookingApi(request).order(orderId).then(async data => {
    if (data.maChuyen !== maChuyen) throw new Error('Đơn giữ chỗ không thuộc chuyến này.');
    const map = await getTripSeats(maChuyen);
    if (!active) return;
    setOrder(data); setLabels(Object.fromEntries(map.danhSachGhe.map(s => [s.maGhe, s.viTri])));
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Không thể tải đơn.'); })
    .finally(() => { if (active) setLoaded(true); });
    return () => { active = false; };
  }, [authLoading, user, request, orderId, maChuyen]);
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!order || busy) return;
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? '').trim();
    const contact = { khachDatTen: text('contactName'), khachDatSoDienThoai: text('contactPhone'), khachDatEmail: text('contactEmail'), ghiChu: text('note') || null };
    const passengers = order.danhSachGiuCho.filter(s => s.trangThai === 'DANG_GIU').map((seat,index) => ({ maGhe: seat.maGhe, hoTen: text('name' + index), soDienThoai: text('phone' + index), cccd: text('identity' + index) || null }));
    const fingerprint = JSON.stringify(contact);
    if (attempt.current?.body !== fingerprint) attempt.current = { body: fingerprint, key: crypto.randomUUID() };
    setBusy(true); setError(''); setQuote(undefined);
    try {
    const api = bookingApi(request);
    await api.contact(order.maDon, contact, attempt.current.key);
    setOrder(await api.passengers(order.maDon, passengers));
    setQuote(await api.quote(order.maDon, text('promo') || null));
    } catch(e) { setError(e instanceof Error ? e.message : 'Không thể lưu thông tin.'); }
    finally { setBusy(false); }
  }
  async function confirm() {
    if (!order || !quote || busy) return;
    if (freezeAttempt.current?.quote !== quote.maBaoGia) freezeAttempt.current = { quote: quote.maBaoGia, key: crypto.randomUUID() };
    setBusy(true); setError('');
    try {
    await bookingApi(request).freeze(order.maDon, quote.maBaoGia, freezeAttempt.current.key);
    router.push('/tim-chuyen/' + encodeURIComponent(maChuyen) + '/success?order=' + encodeURIComponent(order.maDon));
    } catch(e) { setError(e instanceof Error ? e.message : 'Không thể xác nhận giá.'); }
    finally { setBusy(false); }
  }
  const seats = order?.danhSachGiuCho.filter(s => s.trangThai === 'DANG_GIU') ?? [];
  const ready = order?.trangThaiDon === 'CHO_THANH_TOAN' && order.giaDaChot;
  return <main className="page booking-checkout">
    <ol className="seat-steps" aria-label="Các bước đặt vé"><li><span>1</span><strong>Chọn ghế</strong></li><li className="active" aria-current="step"><span>2</span><strong>Thông tin hành khách</strong></li><li><span>3</span><strong>Thanh toán</strong></li></ol>
    <div className="page-title"><h1>Thông tin đặt vé</h1><p>Kiểm tra hành khách và giá vé trước khi thanh toán.</p></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {authLoading && <p role="status">Đang kiểm tra phiên đăng nhập…</p>}
    {!authLoading && !user && <Link className="primary-button" href={'/dang-nhap?next=' + encodeURIComponent('/tim-chuyen/' + maChuyen + '/checkout' + (orderId ? '?order=' + encodeURIComponent(orderId) : ''))}>Đăng nhập để tiếp tục</Link>}
    {!orderId && <div className="state"><p>Chưa có đơn giữ ghế. Vui lòng chọn ghế trước.</p><Link href={'/tim-chuyen/' + encodeURIComponent(maChuyen) + '/seats'}>Chọn ghế</Link></div>}
    {user && orderId && !loaded && <p role="status">Đang tải đơn giữ chỗ…</p>}
    {order && user && <div className="checkout-layout">
    <section className="checkout-form-card">
      {ready ? <><h2>Thông tin đã được xác nhận</h2><p>Giá vé đã chốt. Tiếp tục để chọn phương thức thanh toán.</p><Link className="primary-button" href={'/tim-chuyen/' + encodeURIComponent(maChuyen) + '/success?order=' + encodeURIComponent(order.maDon)}>Tiếp tục thanh toán</Link></> : order.trangThaiDon !== 'CHO_XU_LY' ? <><p>Đơn không còn ở bước nhập thông tin.</p><Link href={'/tim-chuyen/' + encodeURIComponent(maChuyen) + '/success?order=' + encodeURIComponent(order.maDon)}>Xem trạng thái đơn</Link></> : <form onSubmit={save} onChange={() => setQuote(undefined)}>
      <fieldset disabled={busy}><legend>Thông tin liên hệ</legend><div className="checkout-fields"><label>Họ tên người đặt<input name="contactName" required maxLength={120} defaultValue={order.khachDatTen ?? user.hoTen} /></label><label>Số điện thoại<input name="contactPhone" type="tel" required maxLength={20} defaultValue={order.khachDatSoDienThoai ?? user.soDienThoai} /></label><label className="full-width">Email nhận vé<input name="contactEmail" type="email" required maxLength={254} autoComplete="email" defaultValue={/\.(test|invalid|localhost)$/i.test(order.khachDatEmail ?? user.email) ? '' : order.khachDatEmail ?? user.email} /><small>Biên nhận thanh toán và vé sẽ được gửi tới địa chỉ này sau khi thanh toán thành công.</small></label></div></fieldset>
      {seats.map((seat,index) => { const passenger = order.hanhKhach.find(p => p.maGhe === seat.maGhe); return <fieldset key={seat.maGhe} disabled={busy}><legend>Hành khách · Ghế {labels[seat.maGhe] ?? seat.maGhe}</legend><div className="checkout-fields"><label>Họ và tên<input name={'name' + index} required maxLength={120} defaultValue={passenger?.hoTen ?? (index === 0 ? user.hoTen : '')} /></label><label>Số điện thoại<input name={'phone' + index} type="tel" required maxLength={20} defaultValue={passenger?.soDienThoai ?? user.soDienThoai} /></label><label className="full-width">CCCD / Hộ chiếu (không bắt buộc)<input name={'identity' + index} maxLength={20} defaultValue={passenger?.cccd ?? ''} /></label></div></fieldset>; })}
      <fieldset disabled={busy}><legend>Thông tin thêm</legend><label>Ghi chú<textarea name="note" maxLength={500} defaultValue={order.ghiChu ?? ''} /></label><label>Mã khuyến mãi (nếu có)<input name="promo" maxLength={40} /></label></fieldset>
      <button className="primary-button" disabled={busy || seats.length === 0} type="submit">{busy ? 'Đang xử lý…' : 'Lưu thông tin và xem giá'}</button>
      </form>}
    </section>
    <aside className="summary-card"><span className="eyebrow">ĐƠN CỦA BẠN</span><OrderCode value={order.maDon} /><p>{seats.length} ghế đang giữ</p><p>{seats.map(s => labels[s.maGhe] ?? s.maGhe).join(', ')}</p>{order.thoiDiemHetHan && <p>Giữ đến {new Date(order.thoiDiemHetHan).toLocaleTimeString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</p>}<hr />
      <div className="total-row"><span>Giá gốc</span><strong>{money(quote?.tongTienGoc ?? order.giaDaChot?.tongTienGoc ?? order.giaTamTinh?.tongTienGoc)}</strong></div>
      <div className="total-row"><span>Giảm giá</span><strong>{money(quote?.tongTienGiam ?? order.giaDaChot?.tongTienGiam ?? order.giaTamTinh?.tienGiam)}</strong></div>
      <div className="total-row"><span>{quote || ready ? 'Tổng thanh toán' : 'Tạm tính'}</span><strong>{money(quote?.tongThanhToan ?? order.giaDaChot?.tongThanhToan ?? order.giaTamTinh?.tongThanhToan)}</strong></div>
      {quote && <><p>Kiểm tra tổng tiền trước khi xác nhận. Ghế chưa được gia hạn.</p><button type="button" className="primary-button" disabled={busy} onClick={() => void confirm()}>{busy ? 'Đang xác nhận…' : 'Xác nhận và thanh toán'}</button></>}
    </aside>
    </div>}
  </main>;
}
