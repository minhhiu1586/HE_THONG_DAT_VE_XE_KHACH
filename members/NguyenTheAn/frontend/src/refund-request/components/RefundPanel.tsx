'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { money, type BookingOrder, type BookingRequest } from '@members/NguyenTheAn/booking/lib/booking-api';
import {
  refundApi,
  type RefundEligibility,
  type RefundPreview,
  type RefundRecord,
} from '@members/NguyenTheAn/booking/lib/refund-api';
import styles from '@members/NguyenTheAn/refund-request/components/RefundPanel.module.css';

type Props = {
  order: BookingOrder;
  request: BookingRequest;
  onUpdated: () => Promise<void>;
  labels?: Record<string, string>;
};

type EligibilityView =
  | { kind: 'ready'; value: RefundEligibility }
  | { kind: 'error'; message: string }
  | { kind: 'not-applicable'; message: string };

type Snapshot = {
  eligibility: Map<string, EligibilityView>;
  records: RefundRecord[];
  historyErrors: number;
};

type Attempt = { fingerprint: string; key: string };
type TicketFact = { maVe: string; trangThai: string; dangHoan?: boolean };

const isBlocking = (record: RefundRecord) =>
  record.trangThai === 'CHO_XU_LY' || record.trangThai === 'CAN_DOI_SOAT';

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function outcomeMayBeUnknown(error: unknown) {
  if (!error || typeof error !== 'object' || !('status' in error)) return true;
  const status = (error as { status?: unknown }).status;
  return typeof status !== 'number' || status === 0 || status >= 500;
}

function statusLabel(state: RefundRecord['trangThai']) {
  switch (state) {
    case 'CHO_XU_LY': return 'Đang xử lý';
    case 'THANH_CONG': return 'Đã hoàn tiền';
    case 'THAT_BAI': return 'Hoàn thất bại';
    case 'CAN_DOI_SOAT': return 'Cần đối soát';
  }
}

function formatDate(value: string | null | undefined) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(date);
}

function statusText(ticket: BookingOrder['danhSachVe'][number]) {
  if (ticket.dangHoan) return 'Đang có yêu cầu hoàn';
  switch (ticket.trangThai) {
    case 'DA_XUAT_VE': return 'Đã phát hành';
    case 'DA_HOAN': return 'Đã hoàn';
    case 'DA_LEN_XE': return 'Đã lên xe';
    case 'DA_HUY': return 'Đã hủy';
    default: return ticket.trangThai || 'Chưa phát hành';
  }
}

function localUnavailableReason(ticket: TicketFact) {
  if (ticket.dangHoan) return 'Vé đang có yêu cầu hoàn. Hãy kiểm tra trạng thái bên dưới.';
  switch (ticket.trangThai) {
    case 'DA_HOAN': return 'Vé này đã được hoàn.';
    case 'DA_LEN_XE': return 'Vé đã được dùng để lên xe.';
    case 'DA_HUY': return 'Vé đã bị hủy.';
    case 'DA_XUAT_VE': return '';
    default: return 'Vé chưa được phát hành nên chưa thể yêu cầu hoàn.';
  }
}

function ticketIsEligibleForCheck(ticket: TicketFact) {
  return ticket.trangThai === 'DA_XUAT_VE' && !ticket.dangHoan;
}

function makeAttemptKey() {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') return cryptoApi.randomUUID();
  return `refund-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export default function RefundPanel({ order, request, onUpdated, labels }: Props) {
  const api = useMemo(() => refundApi(request), [request]);
  const tickets = order.danhSachVe ?? [];
  const ticketSignature = JSON.stringify(tickets.map(({ maVe, trangThai, dangHoan }) => ({ maVe, trangThai, dangHoan })));
  const requestSignature = `${order.maDon}:${ticketSignature}`;

  const [eligibility, setEligibility] = useState<Map<string, EligibilityView>>(() => new Map());
  const [records, setRecords] = useState<RefundRecord[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [reason, setReason] = useState('');
  const [preview, setPreview] = useState<RefundPreview | null>(null);
  const [previewFingerprint, setPreviewFingerprint] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [loadedSignature, setLoadedSignature] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [sending, setSending] = useState(false);
  const [verifyingId, setVerifyingId] = useState('');
  const [historyErrors, setHistoryErrors] = useState(0);
  const [actionError, setActionError] = useState('');
  const [historyNotice, setHistoryNotice] = useState('');
  const [retryFingerprint, setRetryFingerprint] = useState('');
  const [awaitingOutcomeId, setAwaitingOutcomeId] = useState('');

  const attemptRef = useRef<Attempt | null>(null);
  const selectedIdsRef = useRef(selectedIds);
  const errorRef = useRef<HTMLParagraphElement>(null);
  const previewRef = useRef<HTMLElement>(null);
  const selectedSorted = [...selectedIds].sort();
  const normalizedReason = reason.trim();
  const bodyFingerprint = JSON.stringify({
    orderId: order.maDon,
    ticketIds: selectedSorted,
    reason: normalizedReason || undefined,
  });
  const bodyFingerprintRef = useRef(bodyFingerprint);
  useEffect(() => { bodyFingerprintRef.current = bodyFingerprint; }, [bodyFingerprint]);
  const initialLoading = loadedSignature !== requestSignature;
  const busy = initialLoading || refreshing || previewing || sending || Boolean(verifyingId);
  const seatLabel = (seatId: string) => labels?.[seatId] ?? seatId;

  const loadSnapshot = useCallback(async (): Promise<Snapshot> => {
    const ticketFacts = JSON.parse(ticketSignature) as TicketFact[];
    const candidateTickets = ticketFacts.filter(ticketIsEligibleForCheck);
    const [eligibilityResults, historyResults] = await Promise.all([
      Promise.all(candidateTickets.map(async (ticket) => {
        try {
          return [ticket.maVe, { kind: 'ready', value: await api.eligibility(ticket.maVe) } as EligibilityView] as const;
        } catch (error) {
          return [ticket.maVe, { kind: 'error', message: errorMessage(error, 'Không thể kiểm tra điều kiện hoàn.') } as EligibilityView] as const;
        }
      })),
      Promise.all(ticketFacts.map(async (ticket) => {
        try {
          return { items: (await api.history(ticket.maVe)).items, failed: false };
        } catch {
          return { items: [] as RefundRecord[], failed: true };
        }
      })),
    ]);

    const eligibilityMap = new Map<string, EligibilityView>();
    for (const ticket of ticketFacts) {
      if (!ticketIsEligibleForCheck(ticket)) {
        eligibilityMap.set(ticket.maVe, {
          kind: 'not-applicable',
          message: localUnavailableReason(ticket),
        });
      }
    }
    for (const [ticketId, result] of eligibilityResults) eligibilityMap.set(ticketId, result);

    const uniqueRecords = new Map<string, RefundRecord>();
    for (const history of historyResults) {
      for (const record of history.items) uniqueRecords.set(record.maHoanTien, record);
    }
    const orderedRecords = [...uniqueRecords.values()].sort((a, b) =>
      new Date(b.thoiDiemTao).getTime() - new Date(a.thoiDiemTao).getTime(),
    );

    return {
      eligibility: eligibilityMap,
      records: orderedRecords,
      historyErrors: historyResults.filter((result) => result.failed).length,
    };
  }, [api, ticketSignature]);

  const applySnapshot = useCallback((snapshot: Snapshot) => {
    setEligibility(snapshot.eligibility);
    setRecords(snapshot.records);
    setHistoryErrors(snapshot.historyErrors);
    const blockedTicketIds = new Set(snapshot.records
      .filter(isBlocking)
      .flatMap((record) => record.chiTiet.map((line) => line.maVe)));
    const finalTicketIds = new Set(snapshot.records
      .filter((record) => record.trangThai === 'THANH_CONG' || record.trangThai === 'THAT_BAI')
      .flatMap((record) => record.chiTiet.map((line) => line.maVe)));
    const knownIneligibleIds = new Set([...snapshot.eligibility]
      .filter(([, view]) => view.kind === 'not-applicable' || (view.kind === 'ready' && !view.value.duocYeuCauHoan))
      .map(([ticketId]) => ticketId));
    const invalidTicketIds = new Set([...blockedTicketIds, ...finalTicketIds, ...knownIneligibleIds]);
    const currentSelection = selectedIdsRef.current;
    const nextSelection = currentSelection.filter((ticketId) => !invalidTicketIds.has(ticketId));
    const selectionChanged = nextSelection.length !== currentSelection.length;
    if (selectionChanged) {
      selectedIdsRef.current = nextSelection;
      setSelectedIds(nextSelection);
    }
    if (blockedTicketIds.size || selectionChanged) {
      setPreview(null);
      setPreviewFingerprint('');
      setConfirmed(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    void loadSnapshot()
      .then((snapshot) => {
        if (active) {
          applySnapshot(snapshot);
          setLoadedSignature(requestSignature);
        }
      })
      .catch((error) => {
        if (active) {
          setActionError(errorMessage(error, 'Không thể tải thông tin hoàn vé.'));
          setLoadedSignature(requestSignature);
        }
      });
    return () => { active = false; };
  }, [loadSnapshot, applySnapshot, requestSignature]);

  const recordsByTicket = useMemo(() => {
    const map = new Map<string, RefundRecord[]>();
    for (const record of records) {
      for (const line of record.chiTiet) {
        const current = map.get(line.maVe) ?? [];
        current.push(record);
        map.set(line.maVe, current);
      }
    }
    return map;
  }, [records]);

  const eligibleTickets = tickets.filter((ticket) => {
    const result = eligibility.get(ticket.maVe);
    const hasFailedRequest = (recordsByTicket.get(ticket.maVe) ?? []).some((record) => record.trangThai === 'THAT_BAI');
    return result?.kind === 'ready' && result.value.duocYeuCauHoan && !hasFailedRequest;
  });
  const eligibleIds = eligibleTickets.map((ticket) => ticket.maVe);
  const allEligibleSelected = eligibleIds.length > 0 && eligibleIds.every((id) => selectedIds.includes(id));
  const someEligibleSelected = eligibleIds.some((id) => selectedIds.includes(id));
  const hasBlockingRecord = records.some(isBlocking);
  const canSelectTickets = !busy && !hasBlockingRecord && historyErrors === 0 && retryFingerprint !== bodyFingerprint;
  const previewIsCurrent = Boolean(preview && previewFingerprint === bodyFingerprint);
  const canRequestPreview = selectedSorted.length > 0
    && selectedSorted.every((id) => eligibleIds.includes(id))
    && !busy && !hasBlockingRecord && historyErrors === 0;
  const canSubmit = previewIsCurrent && confirmed && selectedSorted.length > 0
    && selectedSorted.every((id) => eligibleIds.includes(id))
    && !busy && !hasBlockingRecord && historyErrors === 0;
  const policyFromEligibility = [...eligibility.values()].find(
    (view): view is Extract<EligibilityView, { kind: 'ready' }> => view.kind === 'ready' && Boolean(view.value.chinhSach),
  )?.value.chinhSach;
  const policy = order.chinhSachHoanVe ?? policyFromEligibility ?? null;
  const mixedSelection = someEligibleSelected && !allEligibleSelected;

  useEffect(() => {
    if (actionError) requestAnimationFrame(() => errorRef.current?.focus());
  }, [actionError]);

  function clearDraft() {
    setPreview(null);
    setPreviewFingerprint('');
    setConfirmed(false);
    setActionError('');
    setRetryFingerprint('');
  }

  function changeSelection(next: string[]) {
    selectedIdsRef.current = next;
    bodyFingerprintRef.current = JSON.stringify({
      orderId: order.maDon,
      ticketIds: [...next].sort(),
      reason: normalizedReason || undefined,
    });
    clearDraft();
    setSelectedIds(next);
  }

  function changeReason(nextReason: string) {
    bodyFingerprintRef.current = JSON.stringify({
      orderId: order.maDon,
      ticketIds: selectedSorted,
      reason: nextReason.trim() || undefined,
    });
    clearDraft();
    setReason(nextReason);
  }

  async function refreshHistory() {
    if (busy) return;
    setRefreshing(true);
    setActionError('');
    setHistoryNotice('');
    try {
      applySnapshot(await loadSnapshot());
      setHistoryNotice('Đã cập nhật điều kiện và lịch sử hoàn vé.');
    } catch (error) {
      setActionError(errorMessage(error, 'Không thể cập nhật lịch sử hoàn vé.'));
    } finally {
      setRefreshing(false);
    }
  }

  async function showPreview() {
    if (!canRequestPreview) return;
    const fingerprint = bodyFingerprint;
    setPreviewing(true);
    setActionError('');
    setHistoryNotice('');
    try {
      const result = await api.preview(order.maDon, selectedSorted, normalizedReason || undefined);
      if (bodyFingerprintRef.current !== fingerprint) return;
      setPreview(result);
      setPreviewFingerprint(fingerprint);
      setConfirmed(false);
      requestAnimationFrame(() => {
        previewRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        previewRef.current?.focus({ preventScroll: true });
      });
    } catch (error) {
      if (bodyFingerprintRef.current === fingerprint) {
        setActionError(errorMessage(error, 'Máy chủ không thể tính tiền hoàn dự kiến.'));
      }
    } finally {
      setPreviewing(false);
    }
  }

  async function submitRefund() {
    if (!canSubmit || !preview) return;
    const fingerprint = bodyFingerprint;
    if (!attemptRef.current || attemptRef.current.fingerprint !== fingerprint) {
      attemptRef.current = { fingerprint, key: makeAttemptKey() };
    }
    const attemptKey = attemptRef.current.key;
    setSending(true);
    setActionError('');
    setHistoryNotice('');
    setRetryFingerprint('');
    try {
      const accepted = await api.create(order.maDon, selectedSorted, normalizedReason || undefined, attemptKey);
      setRecords((current) => [accepted, ...current.filter((record) => record.maHoanTien !== accepted.maHoanTien)]);
      let latest: RefundRecord;
      try {
        latest = await api.get(accepted.maHoanTien);
      } catch (error) {
        setAwaitingOutcomeId(accepted.maHoanTien);
        setActionError(`Yêu cầu đã được tiếp nhận. Chưa tải được trạng thái mới nhất (${errorMessage(error, 'lỗi kết nối')}); hãy kiểm tra trạng thái bên dưới.`);
        return;
      }
      setRecords((current) => [latest, ...current.filter((record) => record.maHoanTien !== latest.maHoanTien)]);
      setAwaitingOutcomeId('');
      selectedIdsRef.current = [];
      setSelectedIds([]);
      setPreview(null);
      setPreviewFingerprint('');
      setConfirmed(false);
      try {
        await onUpdated();
      } catch (error) {
        setActionError(`Trạng thái hoàn đã được cập nhật, nhưng chưa tải lại được đơn: ${errorMessage(error, 'lỗi kết nối')}`);
      }
      try {
        applySnapshot(await loadSnapshot());
        setHistoryNotice('Yêu cầu đã được tiếp nhận và trạng thái mới nhất đã được tải.');
      } catch {
        setHistoryNotice('Đã nhận trạng thái từ máy chủ. Lịch sử chưa tải lại được; bạn có thể cập nhật thủ công.');
      }
    } catch (error) {
      const uncertain = outcomeMayBeUnknown(error);
      if (uncertain) setRetryFingerprint(fingerprint);
      setActionError(uncertain
        ? `${errorMessage(error, 'Không nhận được phản hồi từ máy chủ.')} Hãy thử lại đúng yêu cầu này; không đổi vé hoặc lý do khi chưa rõ trạng thái.`
        : errorMessage(error, 'Máy chủ không thể tiếp nhận yêu cầu hoàn.'));
    } finally {
      setSending(false);
    }
  }

  async function verifyRecord(record: RefundRecord) {
    if (!isBlocking(record) || busy) return;
    setVerifyingId(record.maHoanTien);
    setActionError('');
    setHistoryNotice('');
    try {
      const verified = await api.verify(record.maHoanTien);
      let latest = verified;
      try {
        latest = await api.get(verified.maHoanTien);
      } catch {
        // The verification response is still useful when a follow-up read is unavailable.
      }
      setRecords((current) => [latest, ...current.filter((item) => item.maHoanTien !== latest.maHoanTien)]);
      if (awaitingOutcomeId === latest.maHoanTien) {
        setAwaitingOutcomeId('');
        selectedIdsRef.current = [];
        setSelectedIds([]);
        setPreview(null);
        setPreviewFingerprint('');
        setConfirmed(false);
      }
      try {
        await onUpdated();
      } catch (error) {
        setActionError(`Đã kiểm tra trạng thái hoàn, nhưng chưa tải lại được đơn: ${errorMessage(error, 'lỗi kết nối')}`);
      }
      try {
        applySnapshot(await loadSnapshot());
      } catch {
        // The GET above is authoritative; the separate history refresh can be retried manually.
      }
      setHistoryNotice(`Đã kiểm tra trạng thái: ${statusLabel(latest.trangThai)}.`);
    } catch (error) {
      setActionError(errorMessage(error, 'Không thể kiểm tra trạng thái hoàn tiền.'));
    } finally {
      setVerifyingId('');
    }
  }

  return (
    <section className={styles.panel} aria-labelledby="refund-title">
      <header className={styles.header}>
        <div>
          <span className={styles.eyebrow}>HỖ TRỢ SAU ĐẶT VÉ</span>
          <h2 id="refund-title">Hoàn vé & hoàn tiền</h2>
          <p>Chọn vé muốn hoàn, xem mức tiền do máy chủ tính và theo dõi trạng thái xử lý.</p>
        </div>
        <button type="button" className={styles.refreshButton} disabled={busy} onClick={() => void refreshHistory()}>
          {refreshing ? 'Đang cập nhật…' : 'Cập nhật lịch sử'}
        </button>
      </header>

      <div className={styles.policy} role="note">
        <span className={styles.policyIcon} aria-hidden="true">i</span>
        {policy ? (
          <div>
            <strong>Chính sách của đơn: {policy.tenChinhSach}</strong>
            <p>Yêu cầu trước ít nhất {policy.soGioToiThieuTruocKhoiHanh} giờ · hoàn {policy.tyLeHoan}% · phí {money(policy.phiHoan)} mỗi vé.</p>
            <small>Máy chủ kiểm tra thời hạn và tính tiền riêng cho từng vé; số tiền cuối cùng hiển thị ở phần dự kiến.</small>
          </div>
        ) : (
          <div>
            <strong>Điều kiện hoàn do máy chủ xác nhận</strong>
            <p>Đơn này không có chính sách hoàn được lưu để hiển thị. Hãy dựa vào kết quả kiểm tra và phần dự kiến từ máy chủ.</p>
          </div>
        )}
      </div>

      {actionError && <p ref={errorRef} tabIndex={-1} className={styles.error} role="alert">{actionError}</p>}
      {historyErrors > 0 && <p className={styles.warning} role="alert">Chưa tải được lịch sử của {historyErrors} vé. Hệ thống tạm khóa yêu cầu hoàn để tránh gửi trùng; hãy cập nhật lịch sử rồi thử lại.</p>}
      {hasBlockingRecord && <p className={styles.warning} role="status">Đơn đang có yêu cầu hoàn chờ xử lý hoặc cần đối soát. Bạn chưa thể gửi thêm yêu cầu cho đến khi trạng thái được xác định.</p>}
      {historyNotice && <p className={styles.notice} role="status" aria-live="polite">{historyNotice}</p>}
      {awaitingOutcomeId && <p className={styles.warning} role="status">Yêu cầu đã được tiếp nhận nhưng chưa đọc được kết quả mới nhất. Hãy dùng “Kiểm tra trạng thái” bên dưới.</p>}

      <div className={styles.ticketSection}>
        <div className={styles.sectionHeading}>
          <div><h3>Vé trong đơn</h3><p>Điều kiện được kiểm tra lại với máy chủ trước khi gửi.</p></div>
          <label className={`${styles.selectAll} ${!eligibleIds.length ? styles.mutedControl : ''}`}>
            <input
              type="checkbox"
              checked={allEligibleSelected}
              aria-checked={mixedSelection ? 'mixed' : allEligibleSelected}
              disabled={!canSelectTickets || !eligibleIds.length}
              onChange={(event) => changeSelection(event.target.checked ? eligibleIds : [])}
            />
            <span>Chọn tất cả vé đủ điều kiện</span>
          </label>
        </div>

        {initialLoading ? <p className={styles.loading} role="status">Đang tải điều kiện hoàn vé và lịch sử…</p> : tickets.length === 0 ? (
          <p className={styles.empty}>Đơn chưa có vé đã phát hành để kiểm tra hoàn vé.</p>
        ) : (
          <ul className={styles.ticketList}>
            {tickets.map((ticket) => {
              const view = eligibility.get(ticket.maVe);
              const priorRecords = recordsByTicket.get(ticket.maVe) ?? [];
              const failedBefore = priorRecords.some((record) => record.trangThai === 'THAT_BAI');
              const completedBefore = priorRecords.some((record) => record.trangThai === 'THANH_CONG');
              const activeRecord = priorRecords.find(isBlocking);
              const isEligible = view?.kind === 'ready' && view.value.duocYeuCauHoan && !failedBefore && !completedBefore && !activeRecord;
              const unavailable = activeRecord
                  ? 'Đang có yêu cầu hoàn. Hãy kiểm tra trạng thái bên dưới.'
                  : completedBefore
                    ? 'Vé này đã được hoàn thành công.'
                    : failedBefore
                      ? 'Yêu cầu hoàn trước đã thất bại. Khách hàng không thể tự gửi lại; vui lòng liên hệ hỗ trợ.'
                      : view?.kind === 'ready' && !view.value.duocYeuCauHoan
                        ? view.value.lyDoKhongDuocHoan || 'Vé chưa đủ điều kiện hoàn theo chính sách.'
                        : view?.kind === 'error'
                          ? view.message
                          : view?.kind === 'not-applicable'
                            ? view.message
                            : view?.kind === 'ready'
                              ? ''
                              : 'Đang kiểm tra điều kiện hoàn…';
              const checked = selectedIds.includes(ticket.maVe);
              const displayStatus = activeRecord ? statusLabel(activeRecord.trangThai) : completedBefore ? 'Đã hoàn' : failedBefore ? 'Yêu cầu hoàn thất bại' : statusText(ticket);
              return (
                <li className={`${styles.ticketCard} ${checked ? styles.ticketSelected : ''}`} key={ticket.maVe}>
                  <label className={styles.ticketChoice}>
                    <input
                      type="checkbox"
                      checked={checked}
                      disabled={!canSelectTickets || !isEligible}
                      onChange={(event) => changeSelection(event.target.checked
                        ? [...new Set([...selectedIds, ticket.maVe])]
                        : selectedIds.filter((id) => id !== ticket.maVe))}
                      aria-label={`Chọn vé ghế ${seatLabel(ticket.maGhe)}, hành khách ${ticket.hoTen}`}
                      aria-describedby={unavailable ? `refund-ticket-reason-${ticket.maVe}` : undefined}
                    />
                    <span className={styles.checkVisual} aria-hidden="true" />
                  </label>
                  <div className={styles.ticketInfo}>
                    <div className={styles.ticketTopline}>
                      <strong>Ghế {seatLabel(ticket.maGhe)}</strong>
                      <span className={`${styles.ticketStatus} ${isEligible ? styles.ticketReady : ''}`}>{displayStatus}</span>
                    </div>
                    <span>{ticket.hoTen}</span>
                    {ticket.maTraCuu && <small>Mã vé {ticket.maTraCuu}</small>}
                    {view?.kind === 'ready' && <small>Giá vé: {money(ticket.giaVe)}</small>}
                    {unavailable && <small id={`refund-ticket-reason-${ticket.maVe}`} className={styles.ticketReason}>{unavailable}</small>}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <div className={styles.reasonBlock}>
        <label htmlFor="refund-reason">Lý do hoàn <span>(không bắt buộc)</span></label>
        <textarea
          id="refund-reason"
          value={reason}
          maxLength={500}
          rows={3}
          disabled={busy || hasBlockingRecord || historyErrors > 0 || retryFingerprint === bodyFingerprint}
          onChange={(event) => changeReason(event.target.value)}
          placeholder="Ví dụ: Thay đổi kế hoạch"
          aria-describedby="refund-reason-count"
        />
        <small id="refund-reason-count">{reason.length}/500 ký tự</small>
      </div>

      <div className={styles.previewAction}>
        <button type="button" className={styles.secondaryButton} disabled={!canRequestPreview} onClick={() => void showPreview()}>
          {previewing ? 'Đang tính tiền hoàn…' : 'Xem tiền hoàn dự kiến'}
        </button>
        {!selectedSorted.length && <small>Chọn ít nhất một vé đủ điều kiện để xem dự kiến.</small>}
      </div>

      {previewIsCurrent && preview && (
        <section ref={previewRef} tabIndex={-1} className={styles.preview} aria-labelledby="refund-preview-title">
          <div className={styles.previewHeading}>
            <div><span className={styles.eyebrow}>KẾT QUẢ TỪ MÁY CHỦ</span><h3 id="refund-preview-title">Tiền hoàn dự kiến</h3></div>
            <time dateTime={preview.thoiDiemTinh}>Tính lúc {formatDate(preview.thoiDiemTinh)}</time>
          </div>
          <div className={styles.tableWrap}>
            <table className={styles.previewTable}>
              <caption className={styles.visuallyHidden}>Giá đã trả, tỷ lệ hoàn, phí và tiền hoàn dự kiến theo từng vé</caption>
              <thead><tr><th scope="col">Vé</th><th scope="col">Giá đã trả</th><th scope="col">Tỷ lệ</th><th scope="col">Phí</th><th scope="col">Dự kiến hoàn</th></tr></thead>
              <tbody>{preview.chiTiet.map((line) => {
                const ticket = tickets.find((item) => item.maVe === line.maVe);
                return <tr key={line.maVe}>
                  <th scope="row">Ghế {ticket ? seatLabel(ticket.maGhe) : line.maVe}</th>
                  <td data-label="Giá đã trả">{money(line.giaThucTra)}</td>
                  <td data-label="Tỷ lệ">{line.tyLeHoan}%</td>
                  <td data-label="Phí">{money(line.phiHoan)}</td>
                  <td data-label="Dự kiến hoàn">{money(line.soTienHoan)}</td>
                </tr>;
              })}</tbody>
            </table>
          </div>
          <div className={styles.totalRow}><span>Tổng tiền hoàn dự kiến</span><strong>{money(preview.soTienHoan)}</strong></div>
          <p className={styles.previewNote}>Đây là kết quả máy chủ tính cho thời điểm hiện tại. Máy chủ sẽ kiểm tra lại điều kiện khi tiếp nhận yêu cầu.</p>
          <label className={styles.confirmChoice}>
            <input type="checkbox" checked={confirmed} disabled={busy || hasBlockingRecord || historyErrors > 0} onChange={(event) => setConfirmed(event.target.checked)} />
            <span>Tôi hiểu rằng khi hoàn thành công, vé sẽ được đánh dấu đã hoàn và không thể hoàn lại lần nữa. Nếu đang chờ xử lý hoặc cần đối soát, tôi sẽ kiểm tra trạng thái thay vì gửi lại yêu cầu.</span>
          </label>
          <button type="button" className={styles.primaryButton} disabled={!canSubmit} onClick={() => void submitRefund()}>
            {sending ? 'Đang gửi yêu cầu…' : retryFingerprint === bodyFingerprint ? 'Thử lại cùng yêu cầu' : 'Gửi yêu cầu hoàn'}
          </button>
          {retryFingerprint === bodyFingerprint && !sending && <small className={styles.retryHint}>Lần thử tiếp theo sẽ dùng cùng mã yêu cầu cho đúng các vé và lý do này.</small>}
        </section>
      )}

      <section className={styles.historySection} aria-labelledby="refund-history-title">
        <div className={styles.sectionHeading}>
          <div><h3 id="refund-history-title">Lịch sử hoàn tiền</h3><p>Theo dõi từng yêu cầu đã tạo cho các vé trong đơn.</p></div>
          {refreshing && <span className={styles.refreshingLabel} role="status">Đang cập nhật…</span>}
        </div>
        {initialLoading ? <p className={styles.loading} role="status">Đang tải lịch sử…</p> : records.length === 0 ? (
          <p className={styles.empty}>Chưa có yêu cầu hoàn tiền nào trong lịch sử các vé của đơn này.</p>
        ) : (
          <ul className={styles.historyList}>
            {records.map((record) => {
              const successful = record.trangThai === 'THANH_CONG';
              const showVerify = isBlocking(record);
              return <li key={record.maHoanTien} className={styles.historyCard}>
                <div className={styles.historyTopline}>
                  <div><strong>Yêu cầu {record.maHoanTien}</strong><small>Tạo lúc {formatDate(record.thoiDiemTao)}</small></div>
                  <span className={`${styles.badge} ${styles[`status_${record.trangThai}`]}`}>{statusLabel(record.trangThai)}</span>
                </div>
                <div className={styles.historyAmount}>
                  <span>{successful ? 'Số tiền đã xác nhận hoàn' : 'Số tiền yêu cầu hoàn'}</span>
                  <strong>{money(record.soTienHoan)}</strong>
                </div>
                {successful && record.thoiDiemXacNhan && <small className={styles.confirmedAt}>Xác nhận lúc {formatDate(record.thoiDiemXacNhan)}</small>}
                <div className={styles.historyTickets}>
                  <span>Vé được yêu cầu:</span>
                  {record.chiTiet.map((line) => {
                    const ticket = tickets.find((item) => item.maVe === line.maVe);
                    return <span className={styles.historyTicket} key={line.maVe}>Ghế {ticket ? seatLabel(ticket.maGhe) : line.maVe} · {money(line.soTienHoan)}</span>;
                  })}
                </div>
                {record.trangThai === 'CHO_XU_LY' && <p className={styles.statusMessage}>Yêu cầu đang được xử lý. Không gửi lại; bạn có thể kiểm tra trạng thái thủ công.</p>}
                {record.trangThai === 'CAN_DOI_SOAT' && <p className={styles.statusWarning}>Kết quả từ cổng chưa xác định. Không gửi lại yêu cầu; vui lòng liên hệ bộ phận hỗ trợ nếu cần.</p>}
                {record.trangThai === 'THAT_BAI' && <p className={styles.statusWarning}>Yêu cầu thất bại; khách hàng không thể tự thử lại. Vui lòng liên hệ bộ phận hỗ trợ.</p>}
                {record.lyDoLoi && <small className={styles.failureReason}>{record.lyDoLoi}</small>}
                {showVerify && <button type="button" className={styles.verifyButton} disabled={busy} onClick={() => void verifyRecord(record)}>
                  {verifyingId === record.maHoanTien ? 'Đang kiểm tra…' : 'Kiểm tra trạng thái'}
                </button>}
              </li>;
            })}
          </ul>
        )}
      </section>
    </section>
  );
}
