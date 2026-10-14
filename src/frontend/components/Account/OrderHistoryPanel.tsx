'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import type { AppRouterInstance } from 'next/dist/shared/lib/app-router-context.shared-runtime';
import { useAuth } from './AuthProvider';
import styles from './Account.module.css';

// Định nghĩa Interface cho Đơn đặt vé
export interface OrderHistoryItem {
  maDon: string;
  trangThai: 'CHO_XU_LY' | 'CHO_THANH_TOAN' | 'DA_THANH_TOAN' | 'HET_HAN' | 'DA_HUY';
  tongTien: string;
  tienGiam: string;
  thoiDiemHetHan: string | null;
  ngayDat: string;
  maChuyen?: string;
  tuyenXe?: string;
  ngayKhoiHanh?: string;
  gioKhoiHanh?: string;
}

export default function OrderHistoryPanel() {
  const { user, loading, request } = useAuth();
  const router = useRouter();
  
  const [orders, setOrders] = useState<OrderHistoryItem[]>([]);
  const [fetchingOrders, setFetchingOrders] = useState(true);

  // Gọi API lấy lịch sử đơn hàng
useEffect(() => {
    if (loading || !user || user.loaiTaiKhoan !== 'KHACH_HANG') {
        return;
    }
    
    let active = true;
    request<OrderHistoryItem[]>('/booking/me')
      .then(data => { if (active) setOrders(data); })
      .catch(() => { /* Bỏ qua lỗi hiển thị nếu không tải được */ })
      .finally(() => { if (active) setFetchingOrders(false); });
      
    return () => { active = false; };
  }, [loading, user, request]);

  if (loading || !user || user.loaiTaiKhoan !== 'KHACH_HANG') return null;

  return (
    <div style={{ marginTop: '56px' }}>
      <header className={styles.profileHeading} style={{ borderTop: '1px solid #e0eaf6', paddingTop: '32px' }}>
        <h2>Lịch sử đặt vé</h2>
        <p>Quản lý chuyến đi và thanh toán đơn đang giữ chỗ.</p>
      </header>

      {fetchingOrders ? (
        <p className={styles.muted}>Đang tải dữ liệu chuyến đi...</p>
      ) : orders.length === 0 ? (
        <div className={styles.card} style={{ textAlign: 'center', padding: '40px 20px' }}>
          <p className={styles.muted}>Bạn chưa có chuyến đi nào.</p>
          <Link href="/" className={styles.primary} style={{ display: 'inline-block', marginTop: '16px', textDecoration: 'none' }}>
            Tìm chuyến ngay
          </Link>
        </div>
      ) : (
        <div className={styles.profileGrid}>
          {orders.map(order => (
            <OrderCard key={order.maDon} order={order} router={router} />
          ))}
        </div>
      )}
    </div>
  );
}

// Component phụ hiển thị từng thẻ vé và đồng hồ đếm ngược
function OrderCard({ order, router }: { order: OrderHistoryItem, router: AppRouterInstance }) {
  const [timeLeft, setTimeLeft] = useState<string>('');
  const [isExpired, setIsExpired] = useState(order.trangThai === 'HET_HAN' || order.trangThai === 'DA_HUY');

  useEffect(() => {
    if (order.trangThai !== 'CHO_THANH_TOAN' || !order.thoiDiemHetHan || isExpired) return;

    const targetTime = new Date(order.thoiDiemHetHan).getTime();
    
    const timer = setInterval(() => {
      const now = new Date().getTime();
      const diff = Math.floor((targetTime - now) / 1000);

      if (diff <= 0) {
        clearInterval(timer);
        setTimeLeft('00:00');
        setIsExpired(true);
      } else {
        const m = Math.floor(diff / 60);
        const s = diff % 60;
        setTimeLeft(`${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`);
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [order.thoiDiemHetHan, order.trangThai, isExpired]);

  let badgeStyle = { background: '#f5f8ff', color: '#3d5280' };
  let statusText = 'Chờ xử lý';
  if (order.trangThai === 'CHO_THANH_TOAN') {
    badgeStyle = { background: '#fff3cd', color: '#856404' };
    statusText = 'Chờ thanh toán';
  } else if (order.trangThai === 'DA_THANH_TOAN') {
    badgeStyle = { background: '#edfaf4', color: '#186039' };
    statusText = 'Đã thanh toán';
  } else if (isExpired) {
    badgeStyle = { background: '#fff0f0', color: '#962020' };
    statusText = 'Đã hủy / Hết hạn';
  }

  return (
    <div className={styles.card} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ fontSize: '18px', margin: 0 }}>Mã đơn: {order.maDon}</h2>
        <span style={{ ...badgeStyle, padding: '4px 10px', borderRadius: '12px', fontSize: '12px', fontWeight: 'bold' }}>
          {statusText}
        </span>
      </div>
      
      <p className={styles.muted} style={{ margin: 0 }}>
        <strong>Tuyến:</strong> {order.tuyenXe ?? 'N/A'}<br/>
        <strong>Khởi hành:</strong> {order.gioKhoiHanh ? order.gioKhoiHanh.slice(0, 5) : ''} · {order.ngayKhoiHanh}
      </p>

      <div style={{ borderTop: '1px dashed #c8d8f0', margin: '8px 0' }} />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontSize: '14px', color: '#5a728a' }}>Tổng tiền:</span>
        <strong style={{ fontSize: '16px', color: '#1a56e8' }}>
          {Number(order.tongTien).toLocaleString('vi-VN')} đ
        </strong>
      </div>

      {/* Hiển thị Nút thanh toán và Đồng hồ đếm ngược */}
      {order.trangThai === 'CHO_THANH_TOAN' && !isExpired && (
        <div style={{ marginTop: '12px' }}>
          <p style={{ color: '#d9534f', fontSize: '13px', fontWeight: 'bold', marginBottom: '8px' }}>
            ⏳ Hết hạn thanh toán sau: {timeLeft}
          </p>
          <button 
            className={styles.primary} 
            style={{ width: '100%', padding: '10px' }} 
            onClick={() => order.maChuyen && router.push(`/tim-chuyen/${order.maChuyen}/success?order=${order.maDon}`)}
          >
            Thanh toán ngay
          </button>
        </div>
      )}
      
      {/* Nút Xem chi tiết đơn */}
      <button 
         className={styles.secondary} 
         style={{ width: '100%', marginTop: (order.trangThai === 'CHO_THANH_TOAN' && !isExpired) ? '8px' : '12px', padding: '10px' }}
         onClick={() => order.maChuyen && router.push(`/tim-chuyen/${order.maChuyen}/success?order=${order.maDon}`)}
      >
        Xem chi tiết đơn
      </button>
    </div>
  );
}