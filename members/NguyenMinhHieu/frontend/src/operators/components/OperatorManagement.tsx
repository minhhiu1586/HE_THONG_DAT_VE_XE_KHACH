'use client';
import OperatorForm from './OperatorForm';
import { FormEvent, useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import { errorMessage, Profile } from '@members/NguyenMinhHieu/account/lib/account-api';
import { useManagementFocus } from '@shared/lib/use-management-focus';
import styles from '@members/NguyenMinhHieu/operators/components/OperatorManagement.module.css';
import {
  Status,
  Operator,
  Manager,
  Impact,
  StateChange,
  Page,
  Form,
  empty,
  labels,
  formOf,
} from '../lib/operator-types';
export default function OperatorManagement() {
  const { user, loading, request } = useAuth();
  const router = useRouter();
  const [items, setItems] = useState<Operator[]>([]),
    [page, setPage] = useState(1),
    [totalPages, setTotalPages] = useState(0);
  const [query, setQuery] = useState(''),
    [search, setSearch] = useState(''),
    [filter, setFilter] = useState('');
  const [selected, setSelected] = useState<Operator | null>(null),
    [form, setForm] = useState<Form>(empty);
  const [managers, setManagers] = useState<Manager[]>([]),
    [accounts, setAccounts] = useState<Profile[]>([]);
  const [accountId, setAccountId] = useState(''),
    [busy, setBusy] = useState(false);
  const [loadingList, setLoadingList] = useState(true),
    [loadingManagers, setLoadingManagers] = useState(false);
  const [error, setError] = useState(''),
    [notice, setNotice] = useState('');
  const [detailsTarget, focusDetails] = useManagementFocus<HTMLElement>();
  const load = useCallback(async () => {
    setLoadingList(true);
    const params = new URLSearchParams({ page: String(page), size: '20' });
    if (search) params.set('q', search);
    if (filter) params.set('trangThai', filter);
    try {
      const result = await request<Page<Operator>>(`/quan-tri/nha-xe?${params}`);
      setItems(result.items);
      setTotalPages(result.totalPages);
      if (selected) {
        const current = result.items.find((x) => x.maNhaXe === selected.maNhaXe);
        if (current) setSelected(current);
      }
    } finally {
      setLoadingList(false);
    }
  }, [request, page, search, filter, selected]);
  useEffect(() => {
    if (!loading && !user) router.replace('/dang-nhap?next=/quan-tri/nha-xe');
  }, [loading, user, router]);
  useEffect(() => {
    if (user?.loaiTaiKhoan !== 'ADMIN') return;
    let active = true;
    const params = new URLSearchParams({ page: String(page), size: '20' });
    if (search) params.set('q', search);
    if (filter) params.set('trangThai', filter);
    request<Page<Operator>>(`/quan-tri/nha-xe?${params}`)
      .then((result) => {
        if (active) {
          setItems(result.items);
          setTotalPages(result.totalPages);
        }
      })
      .catch((e) => {
        if (active) setError(errorMessage(e));
      })
      .finally(() => {
        if (active) setLoadingList(false);
      });
    return () => {
      active = false;
    };
  }, [user, page, search, filter, request]);
  async function choose(operator: Operator) {
    setSelected(operator);
    setForm(formOf(operator));
    setError('');
    setNotice('');
    focusDetails(
      'input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not(:disabled)',
    );
    setLoadingManagers(true);
    try {
      const [grants, available] = await Promise.all([
        request<Manager[]>(`/quan-tri/nha-xe/${operator.maNhaXe}/quan-ly`),
        request<Page<Profile>>('/quan-tri/tai-khoan?page=1&size=100'),
      ]);
      setManagers(grants);
      setAccounts(
        available.items.filter(
          (x) => x.loaiTaiKhoan === 'KHACH_HANG' && x.trangThai === 'HOAT_DONG',
        ),
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoadingManagers(false);
    }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const data = {
        ...form,
        hotline: form.hotline || null,
        email: form.email || null,
        diaChi: form.diaChi || null,
        moTa: form.moTa || null,
        logoUrl: form.logoUrl || null,
        ngayThanhLap: form.ngayThanhLap || null,
      };
      const result = await request<Operator>(
        selected ? `/quan-tri/nha-xe/${selected.maNhaXe}` : '/quan-tri/nha-xe',
        selected ? 'PUT' : 'POST',
        data,
      );
      await load();
      await choose(result);
      setNotice(
        selected ? 'Đã cập nhật hồ sơ nhà xe.' : 'Đã tạo nhà xe ở trạng thái chờ phê duyệt.',
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function changeState(status: Status) {
    if (!selected) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const impact = await request<Impact>(`/quan-tri/nha-xe/${selected.maNhaXe}/anh-huong`);
      const summary =
        `Chuyến sắp khởi hành: ${impact.chuyenSapKhoiHanh}; đang chạy: ${impact.chuyenDangChay}; ` +
        `giữ chỗ: ${impact.giuChoDangGiu}; vé đã phát hành: ${impact.veDaPhatHanh}; ` +
        `vé có thanh toán thành công: ${impact.veCoThanhToanThanhCong}.`;
      if (
        !window.confirm(
          `Chuyển ${selected.tenNhaXe} sang "${labels[status]}"?\n${summary}\n` +
            'Các chuyến, vé và giao dịch sẽ giữ nguyên. Những trường hợp còn hoạt động cần xử lý theo quy trình vận hành.',
        )
      )
        return;
      const result = await request<StateChange>(
        `/quan-tri/nha-xe/${selected.maNhaXe}/trang-thai`,
        'PUT',
        { trangThai: status },
      );
      setSelected(result.nhaXe);
      await load();
      setNotice(`Đã cập nhật trạng thái. ${summary}`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function remove() {
    if (
      !selected ||
      !window.confirm(
        `Xóa ${selected.tenNhaXe}? Chỉ nhà xe chưa có dữ liệu liên quan mới xóa được.`,
      )
    )
      return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await request(`/quan-tri/nha-xe/${selected.maNhaXe}`, 'DELETE');
      setSelected(null);
      setForm(empty);
      setManagers([]);
      await load();
      setNotice('Đã xóa nhà xe chưa có dữ liệu liên quan.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function grant() {
    if (!selected || !accountId) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await request(`/quan-tri/nha-xe/${selected.maNhaXe}/quan-ly`, 'POST', {
        maNguoiDung: accountId,
      });
      await choose(selected);
      setAccountId('');
      setNotice('Đã cấp quyền; các phiên đăng nhập cũ của tài khoản đã bị thu hồi.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function revoke(manager: Manager) {
    if (!selected || !window.confirm(`Thu hồi quyền quản lý nhà xe của ${manager.hoTen}?`)) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await request(
        `/quan-tri/nha-xe/${selected.maNhaXe}/quan-ly/${manager.maNguoiDung}`,
        'DELETE',
      );
      await choose(selected);
      setNotice('Đã thu hồi quyền và các phiên đăng nhập cũ.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  if (loading || !user) return <main className={styles.page}>Đang kiểm tra quyền…</main>;
  if (user.loaiTaiKhoan !== 'ADMIN')
    return (
      <main className={styles.page} role="alert">
        Bạn không có quyền quản lý nhà xe.
      </main>
    );
  return (
    <main className={styles.page}>
      <header className={styles.heading}>
        <div>
          <p className={styles.eyebrow}>QUẢN TRỊ HỆ THỐNG</p>
          <h1>Quản lý nhà xe</h1>
          <p>Hồ sơ, trạng thái và tài khoản quản lý</p>
        </div>
        <button
          disabled={busy}
          onClick={() => {
            setSelected(null);
            setForm(empty);
            setManagers([]);
            setNotice('');
            focusDetails('input:not(:disabled):not([readonly])');
          }}
        >
          + Thêm nhà xe
        </button>
      </header>
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
      {notice && (
        <p className={styles.notice} role="status">
          {notice}
        </p>
      )}
      <div className={styles.grid}>
        <section className={styles.card}>
          <h2>Danh sách</h2>
          <form
            className={styles.filters}
            onSubmit={(e) => {
              e.preventDefault();
              setLoadingList(true);
              setPage(1);
              setSearch(query);
            }}
          >
            <input
              aria-label="Tìm nhà xe"
              placeholder="Mã hoặc tên nhà xe"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <select
              aria-label="Lọc trạng thái"
              value={filter}
              onChange={(e) => {
                setLoadingList(true);
                setPage(1);
                setFilter(e.target.value);
              }}
            >
              <option value="">Tất cả trạng thái</option>
              {Object.entries(labels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
            <button type="submit">Tìm</button>
          </form>
          {loadingList && <p role="status">Đang tải danh sách nhà xe…</p>}
          <div className={styles.list}>
            {items.map((item) => (
              <button
                key={item.maNhaXe}
                disabled={loadingManagers || busy}
                className={selected?.maNhaXe === item.maNhaXe ? styles.selected : ''}
                onClick={() => void choose(item)}
              >
                <strong>{item.tenNhaXe}</strong>
                <small>
                  {item.maNhaXe} · {labels[item.trangThai]}
                </small>
              </button>
            ))}
          </div>
          {items.length === 0 && <p>Không có nhà xe phù hợp.</p>}
          <div className={styles.pager}>
            <button
              disabled={page <= 1}
              onClick={() => {
                setLoadingList(true);
                setPage(page - 1);
              }}
            >
              Trước
            </button>
            <span>Trang {page}</span>
            <button
              disabled={page >= totalPages}
              onClick={() => {
                setLoadingList(true);
                setPage(page + 1);
              }}
            >
              Sau
            </button>
          </div>
        </section>
        <div className={styles.details}>
          <section ref={detailsTarget} className={styles.card}>
            <h2 data-management-focus tabIndex={-1}>
              {selected ? `Hồ sơ ${selected.maNhaXe}` : 'Tạo nhà xe'}
            </h2>
            <OperatorForm
              form={form}
              setForm={setForm}
              selected={selected}
              busy={busy}
              save={save}
            />
            {selected && (
              <div className={styles.states}>
                <strong>Trạng thái: {labels[selected.trangThai]}</strong>
                <div>
                  {(Object.keys(labels) as Status[])
                    .filter((state) => state !== selected.trangThai)
                    .map((state) => (
                      <button key={state} disabled={busy} onClick={() => void changeState(state)}>
                        {labels[state]}
                      </button>
                    ))}
                </div>
                <p>Tạm ngưng/ngừng hoạt động chặn thao tác mới; dữ liệu lịch sử không bị xóa.</p>
              </div>
            )}
            {selected && (
              <button className={styles.danger} disabled={busy} onClick={() => void remove()}>
                Xóa nhà xe chưa sử dụng
              </button>
            )}
          </section>
          {selected && (
            <section className={styles.card}>
              <h2>Tài khoản quản lý</h2>
              {loadingManagers && <p role="status">Đang tải tài khoản quản lý…</p>}
              <div className={styles.filters}>
                <input
                  aria-label="Mã tài khoản được cấp quyền"
                  list="operator-account-options"
                  placeholder="Nhập mã tài khoản"
                  value={accountId}
                  onChange={(e) => setAccountId(e.target.value)}
                />
                <datalist id="operator-account-options">
                  {accounts.map((account) => (
                    <option
                      key={account.maNguoiDung}
                      value={account.maNguoiDung}
                      label={`${account.hoTen} · ${account.email}`}
                    />
                  ))}
                </datalist>
                <button disabled={busy || !accountId} onClick={() => void grant()}>
                  Cấp quyền
                </button>
              </div>
              <p className={styles.hint}>
                Gợi ý tối đa 100 tài khoản đầu tiên; có thể nhập mã tài khoản khác. Backend kiểm tra
                quyền và trạng thái trước khi cấp.
              </p>
              {managers.map((manager) => (
                <div className={styles.manager} key={manager.maNguoiDung}>
                  <span>
                    <strong>{manager.hoTen}</strong>
                    <small>
                      {manager.email} · {manager.trangThai}
                    </small>
                  </span>
                  <button disabled={busy} onClick={() => void revoke(manager)}>
                    Thu hồi
                  </button>
                </div>
              ))}
              {managers.length === 0 && <p>Chưa có tài khoản quản lý.</p>}
            </section>
          )}
        </div>
      </div>
    </main>
  );
}
