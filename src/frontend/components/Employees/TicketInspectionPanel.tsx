'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useAuth } from '@/components/Account/AuthProvider';
import { Schedule } from './employee-types';
import {
  checkInTicket,
  getPassengerList,
  PassengerItem,
  PassengerListResponse,
  verifyTicket,
  VerifyResponse,
} from '@/lib/ticket-inspection-api';
import { errorMessage, Profile } from '@/lib/account-api';
import styles from './TicketInspectionPanel.module.css';

interface Props {
  schedule: Schedule[];
}

type InputMode = 'ticket' | 'qr';
type Feedback = { owner: Profile; trip: string; type: 'success' | 'error'; message: string };
type VerifyState = {
  owner: Profile;
  trip: string;
  code: string;
  mode: InputMode;
  result: VerifyResponse;
};
type PassengerState =
  | { owner: Profile; trip: string; status: 'loading' }
  | { owner: Profile; trip: string; status: 'error'; message: string }
  | {
      owner: Profile;
      trip: string;
      status: 'ready';
      data: PassengerListResponse;
      refreshing: boolean;
      refreshError: string | null;
    };

export default function TicketInspectionPanel({ schedule }: Props) {
  const { request, user } = useAuth();
  const activeTrips = schedule.filter(s => !['DA_HUY', 'HOAN_TAT'].includes(s.trangThai));
  const [selectedTrip, setSelectedTrip] = useState(() => activeTrips[0]?.maChuyen ?? '');
  const currentTrip = activeTrips.some(trip => trip.maChuyen === selectedTrip)
    ? selectedTrip
    : activeTrips[0]?.maChuyen ?? '';
  const [inputMode, setInputMode] = useState<InputMode>('ticket');
  const [ticketCode, setTicketCode] = useState('');
  const [loadingVerify, setLoadingVerify] = useState(false);
  const [loadingCheckIn, setLoadingCheckIn] = useState(false);
  const [verifyState, setVerifyState] = useState<VerifyState | null>(null);
  const [passengerState, setPassengerState] = useState<PassengerState | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const viewGeneration = useRef(0);
  const verifyGeneration = useRef(0);
  const checkInGeneration = useRef(0);
  const checkInBusy = useRef(false);
  const attemptKeys = useRef(new Map<string, string>());
  const viewRef = useRef<{ owner: Profile | null; trip: string; code: string; mode: InputMode }>({
    owner: user,
    trip: currentTrip,
    code: ticketCode.trim(),
    mode: inputMode,
  });
  if (viewRef.current.owner !== user || viewRef.current.trip !== currentTrip) viewGeneration.current += 1;
  viewRef.current = { owner: user, trip: currentTrip, code: ticketCode.trim(), mode: inputMode };

  useEffect(() => {
    if (selectedTrip !== currentTrip) setSelectedTrip(currentTrip);
  }, [currentTrip, selectedTrip]);

  useEffect(() => {
    checkInGeneration.current += 1;
    checkInBusy.current = false;
    setLoadingCheckIn(false);
    verifyGeneration.current += 1;
    setVerifyState(null);
    setFeedback(null);
    setLoadingVerify(false);
  }, [currentTrip, user]);

  useEffect(() => {
    if (!user || !currentTrip) {
      setPassengerState(null);
      return;
    }

    let active = true;
    setPassengerState({ owner: user, trip: currentTrip, status: 'loading' });
    getPassengerList(request, currentTrip)
      .then(data => {
        if (!active) return;
        if (data.maChuyen !== currentTrip) throw new Error('Danh sách trả về không khớp chuyến đang chọn.');
        setPassengerState({ owner: user, trip: currentTrip, status: 'ready', data, refreshing: false, refreshError: null });
      })
      .catch(error => {
        if (active) setPassengerState({ owner: user, trip: currentTrip, status: 'error', message: errorMessage(error) });
      });

    return () => {
      active = false;
    };
  }, [currentTrip, request, user]);

  const currentPassengerState = passengerState?.owner === user && passengerState.trip === currentTrip
    ? passengerState
    : null;
  const passengers = currentPassengerState?.status === 'ready' ? currentPassengerState.data : null;
  const visibleVerify = verifyState
    && verifyState.owner === user
    && verifyState.trip === currentTrip
    && verifyState.code === ticketCode.trim()
    && verifyState.mode === inputMode
    && verifyState.result.maChuyen === currentTrip
    ? verifyState.result
    : null;
  const visibleFeedback = feedback?.owner === user && feedback.trip === currentTrip ? feedback : null;
  const total = passengers?.tongVe ?? 0;
  const boarded = passengers?.daLenXe ?? 0;
  const percent = total > 0 ? Math.round((boarded / total) * 100) : 0;

  function invalidateLookup() {
    verifyGeneration.current += 1;
    setVerifyState(null);
    setFeedback(null);
    setLoadingVerify(false);
  }

  function changeTrip(trip: string) {
    invalidateLookup();
    if (trip !== currentTrip) viewGeneration.current += 1;
    viewRef.current = { ...viewRef.current, trip, code: '' };
    setTicketCode('');
    setSelectedTrip(trip);
  }

  function changeCode(code: string) {
    invalidateLookup();
    viewRef.current = { ...viewRef.current, code: code.trim() };
    setTicketCode(code);
  }

  function changeMode(mode: InputMode) {
    invalidateLookup();
    viewRef.current = { ...viewRef.current, mode };
    setInputMode(mode);
  }

  function isCurrentLookup(snapshot: { owner: Profile; trip: string; code: string; mode: InputMode }, generation: number) {
    const current = viewRef.current;
    return verifyGeneration.current === generation
      && current.owner === snapshot.owner
      && current.trip === snapshot.trip
      && current.code === snapshot.code
      && current.mode === snapshot.mode;
  }

  async function handleVerify(event?: FormEvent) {
    event?.preventDefault();
    const code = ticketCode.trim();
    const owner = user;
    const trip = currentTrip;
    const mode = inputMode;
    if (!owner || !trip || !code || loadingVerify || loadingCheckIn) return;

    const snapshot = { owner, trip, code, mode };
    const generation = ++verifyGeneration.current;
    setLoadingVerify(true);
    setFeedback(null);
    setVerifyState(null);

    try {
      const payload = mode === 'qr'
        ? { maChuyen: trip, qrToken: code }
        : { maChuyen: trip, maVe: code };
      const result = await verifyTicket(request, payload);
      if (!isCurrentLookup(snapshot, generation)) return;
      if (result.maChuyen !== trip) {
        setFeedback({ owner, trip, type: 'error', message: 'Kết quả xác minh không thuộc chuyến đang chọn. Vui lòng thử lại.' });
        return;
      }
      setVerifyState({ ...snapshot, result });
      if (!result.hopLe) {
        setFeedback({ owner, trip, type: 'error', message: result.lyDo || 'Vé không hợp lệ cho chuyến xe này.' });
      }
    } catch (error) {
      if (isCurrentLookup(snapshot, generation)) {
        setFeedback({ owner, trip, type: 'error', message: errorMessage(error) });
      }
    } finally {
      if (isCurrentLookup(snapshot, generation)) setLoadingVerify(false);
    }
  }

  function findBoardableTicket(maVe: string): PassengerItem | undefined {
    return passengers?.danhSachHanhKhach.find(item => item.maVe === maVe && item.trangThai === 'DA_XUAT_VE' && !item.dangHoan);
  }

  async function refreshPassengerList(owner: Profile, trip: string) {
    const generation = viewGeneration.current;
    const current = viewRef.current;
    if (current.owner !== owner || current.trip !== trip) return;
    setPassengerState(previous => previous?.owner === owner && previous.trip === trip && previous.status === 'ready'
      ? { ...previous, refreshing: true, refreshError: null }
      : previous);
    try {
      const data = await getPassengerList(request, trip);
      if (viewGeneration.current !== generation || viewRef.current.owner !== owner || viewRef.current.trip !== trip) return;
      if (data.maChuyen !== trip) throw new Error('Danh sách trả về không khớp chuyến đang chọn.');
      setPassengerState({ owner, trip, status: 'ready', data, refreshing: false, refreshError: null });
    } catch (error) {
      if (viewGeneration.current !== generation || viewRef.current.owner !== owner || viewRef.current.trip !== trip) return;
      setPassengerState(previous => previous?.owner === owner && previous.trip === trip && previous.status === 'ready'
        ? { ...previous, refreshing: false, refreshError: errorMessage(error) }
        : { owner, trip, status: 'error', message: errorMessage(error) });
    }
  }

  async function handleCheckIn(maVe: string) {
    const owner = user;
    const trip = currentTrip;
    const item = findBoardableTicket(maVe);
    if (!owner || !trip || !item || checkInBusy.current) return;

    checkInBusy.current = true;
    setLoadingCheckIn(true);
    setFeedback(null);
    const generation = ++checkInGeneration.current;
    const currentViewGeneration = viewGeneration.current;
    const idempotencyId = `${owner.maNguoiDung}:${trip}:${maVe}`;
    let attemptKey = attemptKeys.current.get(idempotencyId);
    if (!attemptKey) {
      attemptKey = crypto.randomUUID();
      attemptKeys.current.set(idempotencyId, attemptKey);
    }

    try {
      const result = await checkInTicket(request, maVe, { maChuyen: trip }, attemptKey);
      if (checkInGeneration.current !== generation || viewGeneration.current !== currentViewGeneration || viewRef.current.owner !== owner || viewRef.current.trip !== trip) return;
      if (result.maChuyen !== trip || result.maVe !== maVe) {
        throw new Error('Phản hồi xác nhận lên xe không khớp vé và chuyến đã chọn.');
      }

      setPassengerState(previous => {
        if (previous?.owner !== owner || previous.trip !== trip || previous.status !== 'ready') return previous;
        const alreadyBoarded = previous.data.danhSachHanhKhach.some(row => row.maVe === maVe && row.trangThai === 'DA_LEN_XE');
        const updatedRows = previous.data.danhSachHanhKhach.map(row => row.maVe === maVe
          ? { ...row, trangThai: 'DA_LEN_XE', thoiDiemLenXe: result.thoiDiemLenXe }
          : row);
        return {
          ...previous,
          data: {
            ...previous.data,
            daLenXe: previous.data.daLenXe + (alreadyBoarded ? 0 : 1),
            chuaLenXe: Math.max(0, previous.data.chuaLenXe - (alreadyBoarded ? 0 : 1)),
            danhSachHanhKhach: updatedRows,
          },
        };
      });
      invalidateLookup();
      viewRef.current = { ...viewRef.current, code: '' };
      setTicketCode('');
      setFeedback({ owner, trip, type: 'success', message: `Xác nhận lên xe thành công cho vé ${result.maVe} (Ghế ${result.maGhe}).` });

      // A successful boarding stays successful even if the follow-up list refresh fails.
      await refreshPassengerList(owner, trip);
    } catch (error) {
      if (checkInGeneration.current === generation && viewGeneration.current === currentViewGeneration && viewRef.current.owner === owner && viewRef.current.trip === trip) {
        setFeedback({ owner, trip, type: 'error', message: errorMessage(error) });
      }
    } finally {
      if (checkInGeneration.current === generation) {
        checkInBusy.current = false;
        if (viewGeneration.current === currentViewGeneration && viewRef.current.owner === owner && viewRef.current.trip === trip) setLoadingCheckIn(false);
      }
    }
  }

  return (
    <div className={styles.panel}>
      <h2>Kiểm vé &amp; Xác nhận lên xe</h2>
      <p className={styles.intro}>
        Chọn chuyến được phân công, rồi tra mã vé hoặc nhập riêng chuỗi QR do hành khách xuất trình.
      </p>

      <div className={styles.tripSelector}>
        <label htmlFor="tripSelect">Chọn chuyến xe được phân công:</label>
        <select
          id="tripSelect"
          className={styles.selectInput}
          value={currentTrip}
          onChange={event => changeTrip(event.target.value)}
          disabled={loadingCheckIn || activeTrips.length === 0}
        >
          {activeTrips.map(trip => (
            <option key={trip.maChuyen} value={trip.maChuyen}>
              {trip.maChuyen} · {trip.tenTuyen} ({trip.ngayKhoiHanh} {trip.gioKhoiHanh?.slice(0, 5)})
            </option>
          ))}
          {activeTrips.length === 0 && <option value="">Không có chuyến nào đang hoạt động</option>}
        </select>
      </div>

      {visibleFeedback && (
        <div className={visibleFeedback.type === 'success' ? styles.successAlert : styles.errorAlert} role="alert">
          {visibleFeedback.message}
        </div>
      )}

      <section className={styles.scanSection}>
        <div className={styles.scanHeader}>
          <h3>Kiểm tra vé</h3>
          <p>Mã tra cứu ngắn và mã TKT đều tra bằng chế độ Mã vé.</p>
        </div>

        <div className={styles.modeTabs} role="group" aria-label="Chọn loại thông tin tra cứu">
          <button type="button" className={inputMode === 'ticket' ? styles.modeActive : styles.modeButton} onClick={() => changeMode('ticket')} aria-pressed={inputMode === 'ticket'}>
            Mã vé
          </button>
          <button type="button" className={inputMode === 'qr' ? styles.modeActive : styles.modeButton} onClick={() => changeMode('qr')} aria-pressed={inputMode === 'qr'}>
            QR
          </button>
        </div>

        <form onSubmit={handleVerify} className={styles.inputGroup}>
          <input
            type="text"
            className={styles.ticketInput}
            aria-label={inputMode === 'ticket' ? 'Mã vé hoặc mã tra cứu' : 'Chuỗi QR của vé'}
            placeholder={inputMode === 'ticket' ? 'Nhập mã vé TKT-… hoặc mã tra cứu…' : 'Nhập chuỗi QR…'}
            value={ticketCode}
            onChange={event => changeCode(event.target.value)}
            disabled={loadingVerify || loadingCheckIn || !currentTrip}
          />
          <button type="submit" className={styles.verifyBtn} disabled={loadingVerify || loadingCheckIn || !ticketCode.trim() || !currentTrip || !user}>
            {loadingVerify ? 'Đang kiểm tra…' : 'Xác minh vé'}
          </button>
        </form>

        {visibleVerify && (
          <div className={`${styles.resultCard} ${visibleVerify.hopLe ? styles.resultValid : styles.resultInvalid}`}>
            <div className={styles.resultTitle}>
              {visibleVerify.hopLe ? '✓ VÉ HỢP LỆ — ĐỦ ĐIỀU KIỆN LÊN XE' : `✕ VÉ KHÔNG HỢP LỆ (${visibleVerify.maLyDo})`}
            </div>
            <div className={styles.resultGrid}>
              <div className={styles.resultItem}><strong>Vị trí ghế</strong><span><span className={styles.seatBadge}>{visibleVerify.maGhe || '—'}</span></span></div>
              <div className={styles.resultItem}><strong>Họ tên hành khách</strong><span>{visibleVerify.hoTenHanhKhach || '—'}</span></div>
              <div className={styles.resultItem}><strong>Số điện thoại</strong><span>{visibleVerify.soDienThoaiHanhKhach || '—'}</span></div>
              <div className={styles.resultItem}><strong>Mã tra cứu</strong><span><code>{visibleVerify.maTraCuu || '—'}</code></span></div>
              <div className={styles.resultItem}><strong>Mã vé</strong><span className={styles.ticketId}>{visibleVerify.maVe || '—'}</span></div>
              <div className={styles.resultItem}><strong>Lý do từ chối</strong><span className={styles.rejectReason}>{visibleVerify.lyDo || 'Không có'}</span></div>
            </div>
            {visibleVerify.hopLe && visibleVerify.maVe && (
              <button
                type="button"
                className={styles.checkInBtn}
                onClick={() => handleCheckIn(visibleVerify.maVe!)}
                disabled={loadingCheckIn || !findBoardableTicket(visibleVerify.maVe!)}
              >
                {loadingCheckIn ? 'Đang xác nhận…' : !findBoardableTicket(visibleVerify.maVe!) ? 'Không thể lên xe với vé này' : 'Xác nhận lên xe ngay'}
              </button>
            )}
          </div>
        )}
      </section>

      {currentPassengerState?.status === 'loading' && <p className={styles.loadingMessage} role="status">Đang tải danh sách hành khách…</p>}
      {currentPassengerState?.status === 'error' && (
        <div className={styles.errorAlert} role="alert">
          Không tải được danh sách hành khách: {currentPassengerState.message}
          <button type="button" className={styles.retryButton} onClick={() => void refreshPassengerList(user!, currentTrip)}>Thử tải lại</button>
        </div>
      )}

      {passengers && (
        <section>
          <div className={styles.progressContainer}>
            <div className={styles.progressText}>
              <span>Tiến độ hành khách lên xe ({passengers.tenTuyen})</span>
              <span>{boarded} / {total} khách ({percent}%)</span>
            </div>
            <div className={styles.progressBar}><div className={styles.progressFill} style={{ width: `${percent}%` }} /></div>
          </div>

          {currentPassengerState?.status === 'ready' && currentPassengerState.refreshError && (
            <div className={styles.refreshAlert} role="alert">
              Đã xác nhận lên xe; danh sách chưa tải lại được. {currentPassengerState.refreshError}
              <button type="button" className={styles.retryButton} onClick={() => void refreshPassengerList(user!, currentTrip)}>Thử tải lại danh sách</button>
            </div>
          )}

          <div className={styles.tableWrap} aria-busy={currentPassengerState?.status === 'ready' && currentPassengerState.refreshing}>
            <table className={styles.table}>
              <thead><tr><th>Ghế</th><th>Họ tên hành khách</th><th>Số điện thoại</th><th>Mã tra cứu</th><th>Trạng thái</th><th>Lên xe lúc</th><th>Thao tác</th></tr></thead>
              <tbody>
                {passengers.danhSachHanhKhach.map(item => (
                  <tr key={item.maVe}>
                    <td><span className={styles.seatBadge}>{item.maGhe}</span></td>
                    <td><strong>{item.hoTenHanhKhach}</strong></td>
                    <td>{item.soDienThoaiHanhKhach}</td>
                    <td><code>{item.maTraCuu}</code></td>
                    <td>
                      {item.dangHoan ? <span className={`${styles.badge} ${styles.badgeRefunding}`}>Đang hoàn vé</span>
                        : item.trangThai === 'DA_LEN_XE' ? <span className={`${styles.badge} ${styles.badgeBoarded}`}>Đã lên xe</span>
                        : item.trangThai === 'DA_XUAT_VE' ? <span className={`${styles.badge} ${styles.badgeIssued}`}>Chưa lên xe</span>
                        : item.trangThai === 'DA_HOAN' ? <span className={`${styles.badge} ${styles.badgeRefunded}`}>Đã hoàn tiền</span>
                        : <span className={`${styles.badge} ${styles.badgeCancelled}`}>{item.trangThai}</span>}
                    </td>
                    <td>{item.thoiDiemLenXe ? item.thoiDiemLenXe.replace('T', ' ').slice(0, 16) : '—'}</td>
                    <td>
                      {item.trangThai === 'DA_XUAT_VE' && !item.dangHoan ? (
                        <button type="button" className={styles.quickActionBtn} disabled={loadingCheckIn || currentPassengerState?.status !== 'ready' || currentPassengerState.refreshing} onClick={() => void handleCheckIn(item.maVe)}>
                          Cho lên xe
                        </button>
                      ) : <span className={styles.noAction}>—</span>}
                    </td>
                  </tr>
                ))}
                {passengers.danhSachHanhKhach.length === 0 && <tr><td colSpan={7} className={styles.emptyState}>Chưa có vé nào được đặt trên chuyến này.</td></tr>}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
