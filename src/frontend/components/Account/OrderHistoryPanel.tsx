'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { errorMessage, type Profile } from '@/lib/account-api';
import OrderCode from '@/components/booking/OrderCode';
import { useAuth } from './AuthProvider';
import accountStyles from './Account.module.css';
import styles from './OrderHistoryPanel.module.css';

export interface OrderHistoryItem {
  maDon: string;
  trangThai: 'CHO_XU_LY' | 'DA_XAC_NHAN' | 'CHO_THANH_TOAN' | 'DA_THANH_TOAN' | 'HET_HAN' | 'DA_HUY';
  tongTien: string;
  tienGiam: string;
  thoiDiemHetHan: string | null;
  ngayDat: string;
  maChuyen?: string;
  tuyenXe?: string;
  ngayKhoiHanh?: string;
  gioKhoiHanh?: string;
  trangThaiChuyen?: string;
  trangThaiXuLyHuyChuyen?: string;
  lyDoHuyChuyen?: string;
}

type HistoryState =
  | { owner: Profile; status: 'loading' }
  | { owner: Profile; status: 'error'; message: string }
  | { owner: Profile; status: 'success'; orders: OrderHistoryItem[] };

export default function OrderHistoryPanel() {
  const { user, loading, request } = useAuth();
  const [history, setHistory] = useState<HistoryState | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const visibleHistory = history?.owner === user ? history : null;

  useEffect(() => {
    if (loading || !user || user.loaiTaiKhoan !== 'KHACH_HANG') return;

    let active = true;

    request<OrderHistoryItem[]>('/booking/me')
      .then((data) => {
        if (!Array.isArray(data)) {
          throw new Error('Dữ liệu lịch sử đơn hàng không đúng định dạng. Vui lòng thử lại.');
        }
        if (active) setHistory({ owner: user, status: 'success', orders: data });
      })
      .catch((error: unknown) => {
        if (active) setHistory({ owner: user, status: 'error', message: errorMessage(error) });
      });

    return () => {
      active = false;
    };
  }, [loading, user, request, retryCount]);

  if (loading || !user || user.loaiTaiKhoan !== 'KHACH_HANG') return null;

  return (
    <section className={styles.panel} aria-labelledby="order-history-title">
      <header className={`${accountStyles.profileHeading} ${styles.heading}`}>
        <h2 id="order-history-title">Lịch sử đặt vé</h2>
        <p>Quản lý chuyến đi và thanh toán đơn đang giữ chỗ.</p>
      </header>

      {!visibleHistory || visibleHistory.status === 'loading' ? (
        <p className={accountStyles.muted} role="status">Đang tải dữ liệu chuyến đi...</p>
      ) : visibleHistory.status === 'error' ? (
        <div className={styles.errorBox} role="alert">
          <p>{visibleHistory.message}</p>
          <button
            type="button"
            className={accountStyles.secondary}
            onClick={() => {
              setHistory({ owner: user, status: 'loading' });
              setRetryCount((count) => count + 1);
            }}
          >
            Thử tải lại
          </button>
        </div>
      ) : visibleHistory.orders.length === 0 ? (
        <div className={`${accountStyles.card} ${styles.empty}`}>
          <p className={accountStyles.muted}>Bạn chưa có đơn đặt vé nào.</p>
          <Link href="/" className={accountStyles.primary}>Tìm chuyến ngay</Link>
        </div>
      ) : (
        <div className={styles.historyGrid}>
          {visibleHistory.orders.map((order) => (
            <OrderCard key={order.maDon} order={order} />
          ))}
        </div>
      )}
    </section>
  );
}

function OrderCard({ order }: { order: OrderHistoryItem }) {
  const [clock, setClock] = useState<{ status: OrderHistoryItem['trangThai']; deadline: number; now: number } | null>(null);
  const deadline = order.thoiDiemHetHan ? Date.parse(order.thoiDiemHetHan) : Number.NaN;
  const hasDeadline = Number.isFinite(deadline);
  const isDraftPending = order.trangThai === 'CHO_XU_LY' || order.trangThai === 'DA_XAC_NHAN';
  const isHoldPending = isDraftPending || order.trangThai === 'CHO_THANH_TOAN';

  useEffect(() => {
    if (!isHoldPending || !hasDeadline) return;

    let active = true;
    let timer: number | undefined;
    const updateClock = () => {
      if (!active) return;
      setClock({ status: order.trangThai, deadline, now: Date.now() });
      timer = window.setTimeout(updateClock, 1000);
    };
    const firstTick = window.setTimeout(updateClock, 0);

    return () => {
      active = false;
      window.clearTimeout(firstTick);
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [order.trangThai, deadline, hasDeadline, isHoldPending]);

  const clockMatchesOrder = clock?.status === order.trangThai && clock.deadline === deadline;
  const currentTime = clockMatchesOrder ? clock.now : null;
  const pendingExpired = isHoldPending
    && hasDeadline
    && currentTime !== null
    && currentTime >= deadline;
  const ended = order.trangThai === 'DA_HUY' || order.trangThai === 'HET_HAN' || pendingExpired;

  const badgeClass = order.trangThai === 'DA_THANH_TOAN'
    ? styles.statusPaid
    : ended
      ? styles.statusEnded
        : order.trangThai === 'CHO_THANH_TOAN'
          ? styles.statusPending
          : styles.statusProcessing;
  const statusText = order.trangThai === 'DA_THANH_TOAN'
    ? 'Đã thanh toán'
    : order.trangThai === 'DA_HUY'
      ? 'Đã hủy'
      : order.trangThai === 'HET_HAN' || pendingExpired
        ? 'Đã hết hạn'
        : order.trangThai === 'CHO_THANH_TOAN'
          ? 'Chờ thanh toán'
          : order.trangThai === 'DA_XAC_NHAN'
            ? 'Đã xác nhận'
            : 'Chờ xử lý';

  const grossAmount = parseVnd(order.tongTien);
  const discountAmount = parseVnd(order.tienGiam);
  const payableAmount = grossAmount !== null && discountAmount !== null
    ? grossAmount - discountAmount
    : null;
  const showDiscount = discountAmount !== null && discountAmount > 0;
  const formattedCountdown = currentTime !== null && hasDeadline
    ? formatCountdown(Math.max(0, Math.ceil((deadline - currentTime) / 1000)))
    : null;

  const encodedTripId = order.maChuyen ? encodeURIComponent(order.maChuyen) : null;
  const encodedOrderId = encodeURIComponent(order.maDon);
  const detailHref = encodedTripId
    ? `/tim-chuyen/${encodedTripId}/success?order=${encodedOrderId}`
    : null;
  const checkoutHref = encodedTripId
    ? `/tim-chuyen/${encodedTripId}/checkout?order=${encodedOrderId}`
    : null;
  const canShowDraftAction = isDraftPending
    && !pendingExpired
    && (!hasDeadline || clockMatchesOrder);
  const canShowPaymentAction = order.trangThai === 'CHO_THANH_TOAN'
    && !pendingExpired
    && (!hasDeadline || clockMatchesOrder);

  return (
    <article className={`${accountStyles.card} ${styles.orderCard}`}>
      <div className={styles.orderHeader}>
        <div className={styles.orderCode}>
          <span className={styles.label}>Mã đơn</span>
          <OrderCode value={order.maDon} />
        </div>
        <span className={`${styles.statusBadge} ${badgeClass}`}>{statusText}</span>
      </div>

      <p className={`${accountStyles.muted} ${styles.tripInfo}`}>
        <strong>Tuyến:</strong> {order.tuyenXe ?? 'Chưa có thông tin'}<br />
        <strong>Khởi hành:</strong>{' '}
        {order.gioKhoiHanh ? `${order.gioKhoiHanh.slice(0, 5)} · ` : ''}
        {order.ngayKhoiHanh ?? 'Chưa có thông tin'}
      </p>

      {order.trangThaiChuyen === 'DA_HUY' && (
        <p className={styles.endNote} role="status">
          Chuyến xe đã bị hủy{order.lyDoHuyChuyen ? `: ${order.lyDoHuyChuyen}` : '.'}
          {order.trangThaiXuLyHuyChuyen && ` Trạng thái xử lý vé/hoàn: ${order.trangThaiXuLyHuyChuyen === 'HOAN_TAT' ? 'đã xử lý' : order.trangThaiXuLyHuyChuyen === 'CAN_DOI_SOAT' ? 'đang đối soát' : 'đang chờ xử lý'}.`}
          {' '}Thông báo này không xác nhận hoàn tiền thành công.
        </p>
      )}

      <div className={styles.divider} />

      <div className={styles.priceBlock}>
        <div className={styles.priceRow}>
          <span>Tổng thanh toán</span>
          <strong>{payableAmount === null ? 'Chưa có dữ liệu' : `${formatVnd(payableAmount)} VND`}</strong>
        </div>
        {showDiscount && (
          <p className={styles.discountNote}>Đã áp dụng giảm giá {formatVnd(discountAmount)} VND.</p>
        )}
      </div>

      {isHoldPending && !ended && hasDeadline && (
        <p className={styles.timer} role="timer">
          {formattedCountdown === null
            ? 'Đang kiểm tra thời hạn giữ chỗ…'
            : `⏳ Hết hạn giữ chỗ sau: ${formattedCountdown}`}
        </p>
      )}

      {ended && (
        <p className={styles.endNote}>
          {order.trangThai === 'DA_HUY'
            ? 'Đơn đã bị hủy và không còn thao tác được.'
            : 'Đơn đã kết thúc vì thời hạn giữ chỗ đã hết.'}
        </p>
      )}

      {((!ended && (canShowDraftAction || canShowPaymentAction || order.trangThai === 'DA_THANH_TOAN')) || (ended && detailHref)) && (
        <div className={styles.actions}>
          {!ended && canShowDraftAction && checkoutHref && (
            <Link href={checkoutHref} className={`${accountStyles.primary} ${styles.action}`}>
              Xem chi tiết
            </Link>
          )}
          {!ended && canShowPaymentAction && detailHref && (
            <Link href={detailHref} className={`${accountStyles.primary} ${styles.action}`}>
              Thanh toán ngay
            </Link>
          )}
          {!ended && canShowPaymentAction && detailHref && (
            <Link href={detailHref} className={`${accountStyles.secondary} ${styles.action}`}>
              Xem chi tiết
            </Link>
          )}
          {!ended && order.trangThai === 'DA_THANH_TOAN' && detailHref && (
            <Link href={detailHref} className={`${accountStyles.secondary} ${styles.action}`}>
              Xem chi tiết
            </Link>
          )}
          {ended && detailHref && (
            <Link href={detailHref} className={`${accountStyles.secondary} ${styles.action}`}>
              Xem chi tiết
            </Link>
          )}
        </div>
      )}
    </article>
  );
}

function parseVnd(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined || (typeof value === 'string' && value.trim() === '')) return null;
  const amount = Number(value);
  return Number.isFinite(amount) ? amount : null;
}

function formatVnd(value: number): string {
  return value.toLocaleString('vi-VN', { maximumFractionDigits: 0 });
}

function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}
