'use client';
import { useEffect, useState } from 'react';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage } from '@members/NguyenMinhHieu/account/lib/account-api';
import styles from '@members/NguyenMinhHieu/account/components/Account.module.css';
import reviewStyles from '@members/NguyenMinhHieu/reviews/components/Reviews.module.css';
import { Eligibility, reviewStates } from '@members/NguyenMinhHieu/reviews/components/review-types';
export default function ReviewEditor({
  maChuyen,
  tenTuyen,
}: {
  maChuyen: string;
  tenTuyen: string;
}) {
  const { request, user } = useAuth();
  const [eligibility, setEligibility] = useState<Eligibility | null>(null);
  const [stars, setStars] = useState(5),
    [text, setText] = useState(''),
    [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [notice, setNotice] = useState('');
  useEffect(() => {
    let active = true;
    request<Eligibility>(`/danh-gia/dieu-kien?maChuyen=${encodeURIComponent(maChuyen)}`)
      .then((data) => {
        if (active) {
          setEligibility(data);
          setStars(data.danhGia?.soSao ?? 5);
          setText(data.danhGia?.binhLuan ?? '');
        }
      })
      .catch((cause) => {
        if (active) setError(errorMessage(cause));
      });
    return () => {
      active = false;
    };
  }, [request, maChuyen, user]);
  async function save(remove = false) {
    if (busy || !eligibility) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const previous = eligibility.danhGia;
      if (remove) await request(`/danh-gia/${previous!.maDanhGia}`, 'DELETE');
      else if (previous)
        await request(`/danh-gia/${previous.maDanhGia}`, 'PUT', { soSao: stars, binhLuan: text });
      else await request('/danh-gia', 'POST', { maChuyen, soSao: stars, binhLuan: text });
      const refreshed = await request<Eligibility>(
        `/danh-gia/dieu-kien?maChuyen=${encodeURIComponent(maChuyen)}`,
      );
      setEligibility(refreshed);
      setEditing(false);
      setNotice(
        remove
          ? 'Đã xóa đánh giá.'
          : refreshed.danhGia?.trangThai === 'CHO_DUYET'
            ? 'Đã lưu đánh giá và chuyển sang chờ duyệt.'
            : 'Đã lưu đánh giá.',
      );
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }
  const review = eligibility?.danhGia;
  return (
    <article className={styles.card}>
      <h3>Đánh giá chuyến · {tenTuyen}</h3>
      <p className={styles.muted}>{maChuyen} · Mỗi tài khoản đánh giá một lần cho mỗi chuyến.</p>
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
      {notice && (
        <p className={styles.success} role="status">
          {notice}
        </p>
      )}
      {!eligibility && !error && <p role="status">Đang kiểm tra quyền đánh giá…</p>}
      {review && (
        <>
          <p>
            <strong>{review.soSao}/5 sao</strong> · {reviewStates[review.trangThai ?? '']}
          </p>
          <p>{review.binhLuan}</p>
        </>
      )}
      {eligibility && !review && !eligibility.coTheDanhGia && (
        <p className={styles.muted}>{eligibility.lyDo}</p>
      )}
      {(eligibility?.coTheDanhGia || (editing && eligibility?.coTheSua)) && (
        <form
          className={styles.form}
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <label>
            Số sao
            <select
              value={stars}
              disabled={busy}
              onChange={(event) => setStars(Number(event.target.value))}
            >
              {[5, 4, 3, 2, 1].map((n) => (
                <option key={n} value={n}>
                  {n} sao
                </option>
              ))}
            </select>
          </label>
          <label>
            Nhận xét (không bắt buộc, tối đa 2000 ký tự)
            <textarea
              className={reviewStyles.comment}
              rows={4}
              maxLength={2000}
              value={text}
              disabled={busy}
              onChange={(event) => setText(event.target.value)}
            />
          </label>
          <div className={styles.row}>
            <button className={styles.primary} disabled={busy}>
              {busy ? 'Đang lưu…' : review ? 'Lưu chỉnh sửa' : 'Gửi đánh giá'}
            </button>
            {editing && (
              <button
                type="button"
                className={styles.secondary}
                disabled={busy}
                onClick={() => setEditing(false)}
              >
                Quay lại
              </button>
            )}
          </div>
        </form>
      )}
      {review && review.trangThai !== 'DA_XOA' && !editing && (
        <div className={styles.row}>
          {eligibility?.coTheSua && (
            <button
              className={styles.secondary}
              disabled={busy}
              onClick={() => {
                setStars(review.soSao);
                setText(review.binhLuan);
                setEditing(true);
              }}
            >
              Sửa đánh giá
            </button>
          )}
          <button
            className={styles.secondary}
            disabled={busy}
            onClick={() => {
              if (
                window.confirm('Xóa đánh giá này? Bạn không thể tạo đánh giá mới cho cùng chuyến.')
              )
                void save(true);
            }}
          >
            Xóa đánh giá
          </button>
        </div>
      )}
    </article>
  );
}
