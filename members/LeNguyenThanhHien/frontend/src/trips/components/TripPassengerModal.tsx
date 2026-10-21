'use client';

import styles from '@members/LeNguyenThanhHien/trips/components/TripPassengerModal.module.css';
import { getPassengerList, PassengerItem, PassengerListResponse } from '@members/LeNguyenThanhHien/trips/lib/trip-passengers-api';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage } from '@members/NguyenMinhHieu/account/lib/account-api';
import type { Profile } from '@members/NguyenMinhHieu/account/lib/account-api';
import { useEffect, useState } from 'react';

interface Props {
  maChuyen: string;
  onClose: () => void;
}

type LoadState =
  | { owner: Profile; trip: string; status: 'loading' }
  | { owner: Profile; trip: string; status: 'error'; message: string }
  | { owner: Profile; trip: string; status: 'ready'; data: PassengerListResponse };

export default function TripPassengerModal({ maChuyen, onClose }: Props) {
  const { request, user, loading: authLoading } = useAuth();
  const [loadState, setLoadState] = useState<LoadState | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const [searchState, setSearchState] = useState<{ owner: Profile; trip: string; value: string } | null>(null);
  const [filterState, setFilterState] = useState<{ owner: Profile; trip: string; value: string } | null>(null);
  const search = searchState?.owner === user && searchState.trip === maChuyen ? searchState.value : '';
  const statusFilter = filterState?.owner === user && filterState.trip === maChuyen ? filterState.value : 'ALL';

  useEffect(() => {
    if (!user) return;

    let active = true;
    getPassengerList(request, maChuyen)
      .then(data => {
        if (!active) return;
        if (data.maChuyen !== maChuyen) throw new Error('Danh sách trả về không khớp chuyến đang xem.');
        setLoadState({ owner: user, trip: maChuyen, status: 'ready', data });
      })
      .catch(error => {
        if (active) setLoadState({ owner: user, trip: maChuyen, status: 'error', message: errorMessage(error) });
      });
    return () => {
      active = false;
    };
  }, [maChuyen, reloadToken, request, user]);

  const currentState = loadState?.owner === user && loadState.trip === maChuyen ? loadState : null;
  const data = currentState?.status === 'ready' ? currentState.data : null;
  const items = (data?.danhSachHanhKhach ?? []).filter(item => {
    const matchStatus =
      statusFilter === 'ALL' || (statusFilter === 'REFUNDING' && item.dangHoan) || (statusFilter === item.trangThai && !item.dangHoan);

    const term = search.toLowerCase().trim();
    const matchSearch =
      !term ||
      item.hoTenHanhKhach.toLowerCase().includes(term) ||
      item.soDienThoaiHanhKhach.includes(term) ||
      item.maGhe.toLowerCase().includes(term) ||
      item.maTraCuu.toLowerCase().includes(term) ||
      item.maVe.toLowerCase().includes(term);

    return matchStatus && matchSearch;
  });

  function renderStatusBadge(item: PassengerItem) {
    if (item.dangHoan) return <span className={`${styles.badge} ${styles.badgeRefunding}`}>Đang hoàn vé</span>;
    switch (item.trangThai) {
      case 'DA_XUAT_VE':
        return <span className={`${styles.badge} ${styles.badgeIssued}`}>Đã phát hành</span>;
      case 'DA_HOAN':
        return <span className={`${styles.badge} ${styles.badgeRefunded}`}>Đã hoàn tiền</span>;
      case 'DA_HUY':
        return <span className={`${styles.badge} ${styles.badgeCancelled}`}>Đã hủy</span>;
      default:
        return <span className={styles.badge}>{item.trangThai}</span>;
    }
  }

  return (
    <div className={styles.overlay} onClick={onClose} role="dialog" aria-modal="true">
      <div className={styles.modal} onClick={event => event.stopPropagation()}>
        <header className={styles.header}>
          <div className={styles.titleArea}>
            <h2>Danh sách hành khách &amp; Vé theo chuyến</h2>
            {data && (
              <p className={styles.meta}>
                <span className={styles.metaItem}>
                  <strong>Mã chuyến:</strong> {data.maChuyen}
                </span>
                <span className={styles.metaItem}>
                  <strong>Tuyến:</strong> {data.tenTuyen}
                </span>
                <span className={styles.metaItem}>
                  <strong>Xe:</strong> {data.bienSo}
                </span>
                <span className={styles.metaItem}>
                  <strong>Khởi hành:</strong> {data.ngayKhoiHanh} {data.gioKhoiHanh?.slice(0, 5)}
                </span>
              </p>
            )}
          </div>
          <button className={styles.closeButton} onClick={onClose} aria-label="Đóng">
            ✕
          </button>
        </header>

        <div className={styles.body}>
          {(authLoading || currentState?.status === 'loading' || (!currentState && user)) && (
            <p role="status">Đang tải danh sách hành khách…</p>
          )}
          {!authLoading && !user && <p role="alert">Phiên nhân viên đã kết thúc. Vui lòng đăng nhập lại.</p>}
          {currentState?.status === 'error' && (
            <div className={styles.loadError} role="alert">
              Không tải được danh sách hành khách: {currentState.message}
              <button className={styles.retryButton} type="button" onClick={() => setReloadToken(value => value + 1)}>
                Thử tải lại
              </button>
            </div>
          )}

          {data && (
            <>
              <div className={styles.statsGrid}>
                <div className={`${styles.statCard} ${styles.statTotal}`}>
                  <span className={styles.statNumber}>{data.tongVe}</span>
                  <span className={styles.statLabel}>Tổng số vé</span>
                </div>
                <div className={`${styles.statCard} ${styles.statPending}`}>
                  <span className={styles.statNumber}>{data.daXuatVe}</span>
                  <span className={styles.statLabel}>Đã phát hành</span>
                </div>
                <div className={`${styles.statCard} ${styles.statRefunded}`}>
                  <span className={styles.statNumber}>{data.daHoan}</span>
                  <span className={styles.statLabel}>Đã hoàn</span>
                </div>
                <div className={`${styles.statCard} ${styles.statCancelled}`}>
                  <span className={styles.statNumber}>{data.daHuy}</span>
                  <span className={styles.statLabel}>Đã hủy</span>
                </div>
              </div>

              <div className={styles.toolbar}>
                <input
                  type="search"
                  placeholder="Tìm theo tên khách, SĐT, ghế, mã tra cứu…"
                  value={search}
                  onChange={event => user && setSearchState({ owner: user, trip: maChuyen, value: event.target.value })}
                  className={styles.searchInput}
                />
                <select
                  value={statusFilter}
                  onChange={event => user && setFilterState({ owner: user, trip: maChuyen, value: event.target.value })}
                  className={styles.filterSelect}
                  aria-label="Lọc trạng thái vé"
                >
                  <option value="ALL">Tất cả trạng thái</option>
                  <option value="DA_XUAT_VE">Đã phát hành</option>
                  <option value="REFUNDING">Đang hoàn tiền</option>
                  <option value="DA_HOAN">Đã hoàn tiền</option>
                  <option value="DA_HUY">Đã hủy</option>
                </select>
              </div>

              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Vị trí ghế</th>
                      <th>Họ tên hành khách</th>
                      <th>Số điện thoại</th>
                      <th>CCCD</th>
                      <th>Mã tra cứu</th>
                      <th>Giá vé</th>
                      <th>Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map(item => (
                      <tr key={item.maVe}>
                        <td>
                          <span className={styles.seatBadge}>{item.maGhe}</span>
                        </td>
                        <td>
                          <strong>{item.hoTenHanhKhach}</strong>
                        </td>
                        <td>{item.soDienThoaiHanhKhach}</td>
                        <td>{item.cccdHanhKhach || '—'}</td>
                        <td>
                          <code>{item.maTraCuu}</code>
                        </td>
                        <td>{Number(item.giaVe).toLocaleString('vi-VN')} đ</td>
                        <td>{renderStatusBadge(item)}</td>
                      </tr>
                    ))}
                    {items.length === 0 && (
                      <tr>
                        <td colSpan={7} className={styles.emptyState}>
                          {data.danhSachHanhKhach.length === 0
                            ? 'Chưa có hành khách nào đặt vé trên chuyến này.'
                            : 'Không tìm thấy hành khách phù hợp với bộ lọc.'}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>

        <footer className={styles.footer}>
          <button className={styles.primaryButton} onClick={onClose}>
            Đóng
          </button>
        </footer>
      </div>
    </div>
  );
}
