'use client';

import { useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { accountRequest, errorMessage, Profile, Session } from '@/lib/account-api';
import { useAuth } from './AuthProvider';
import { roleHome } from '@/lib/role-home';
import styles from './Account.module.css';
import { vietnameseValidation, clearValidation } from '@/lib/form-validation';

// ĐIỂM BẮT ĐẦU: trang /dang-nhap dùng <AuthForm />; /dang-ky dùng <AuthForm register />.
// register=false chạy đăng nhập; true hiển thị thêm họ tên, điện thoại và xác nhận mật khẩu.
export default function AuthForm({ register = false }: { register?: boolean }) {
  // accept cập nhật người dùng/CSRF trong AuthProvider sau khi backend xác thực; notice là thông báo phiên.
  const { accept, notice } = useAuth(); const router = useRouter();
  // busy làm React cập nhật nút chờ; sending là cờ tức thời chặn submit trùng trước lần render kế tiếp.
  const [busy, setBusy] = useState(false); const sending = useRef(false);
  const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    // BƯỚC 1: ngăn form tải lại trang theo mặc định; dừng nếu request trước chưa kết thúc.
    event.preventDefault(); if (sending.current) return;
    // BƯỚC 2: FormData đọc các input theo name; giá trị input do DOM giữ, không có state riêng cho từng ô.
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    // Chỉ trim email; giữ nguyên mật khẩu vì khoảng trắng có thể là một phần mật khẩu.
    const email = String(form.get('email') ?? '').trim(), matKhau = String(form.get('matKhau') ?? '');
    // Chỉ nhánh đăng ký cần so khớp xác nhận; xacNhan không gửi xuống backend.
    if (register && matKhau !== form.get('xacNhan')) { setError('Hai mật khẩu chưa trùng nhau.'); return; }
    // BƯỚC 3: khóa thao tác gửi và xóa thông báo lần trước trước khi chờ API.
    sending.current = true; setBusy(true); setError(''); setSuccess('');
    try {
      if (register) {
        // ĐĂNG KÝ: helper account-api.ts gửi POST /api/auth/dang-ky; backend tự cấp vai trò khách hàng.
        await accountRequest<Profile>('/auth/dang-ky', 'POST', {
          hoTen: form.get('hoTen'), soDienThoai: form.get('soDienThoai'), email, matKhau,
        });
        // Đăng ký thành công chỉ báo kết quả và reset form; nhánh này không tự đăng nhập.
        setSuccess('Đăng ký thành công. Bạn có thể đăng nhập ngay.');
        formElement.reset();
      } else {
        // ĐĂNG NHẬP: chuyển sang accountRequest() trong lib/account-api.ts.
        // await chờ kết quả; <Session> mô tả kiểu TypeScript, không tự kiểm định JSON khi chạy.
        const session = await accountRequest<Session>('/auth/dang-nhap', 'POST', { email, matKhau });
        // BƯỚC 4: nhận profile/CSRF/hạn phiên từ backend và cập nhật trạng thái dùng chung.
        // Cookie phiên HttpOnly do browser nhận từ response; không lưu raw token vào localStorage tại đây.
        accept(session);
        // BƯỚC 5: ưu tiên đường dẫn quay lại nội bộ; nếu không phù hợp thì chọn trang bằng roleHome().
        // Chuyển trang theo role chỉ phục vụ điều hướng; backend vẫn kiểm tra quyền khi gọi API.
        const next = new URLSearchParams(window.location.search).get('next');
        router.replace(next && next.startsWith('/') && !next.startsWith('//') && !next.includes('\\') ? next : roleHome(session.nguoiDung.loaiTaiKhoan));
      }
    // API ném lỗi thì hiển thị thông báo, không chạy accept/chuyển trang; finally luôn mở lại nút gửi.
    } catch (error) { setError(errorMessage(error)); }
    finally { sending.current = false; setBusy(false); }
  }
  // GIAO DIỆN: register quyết định các trường; error/success/notice chỉ hiện khi có nội dung.
  // Form dùng HTML5 required/type/pattern trước onSubmit; backend vẫn phải kiểm tra lại dữ liệu.
  // onInvalid đặt thông báo tiếng Việt, onInput xóa lỗi tùy chỉnh; disabled={busy} khóa nút khi đang chờ.
  return <main className={styles.page}><section className={styles.card}>
    <p className={styles.eyebrow}>TÀI KHOẢN VEXEXIN</p>
    <h1>{register ? 'Tạo tài khoản' : 'Đăng nhập'}</h1>
    <p className={styles.muted}>{register ? 'Đăng ký tài khoản khách hàng để sử dụng VeXe.' : 'Đăng nhập để quản lý hồ sơ và sử dụng các chức năng của bạn.'}</p>
    {notice && <p className={styles.error} role="status">{notice}</p>}
    {error && <p className={styles.error} role="alert">{error}</p>}
    {success && <p className={styles.success} role="status">{success} <Link href="/dang-nhap">Đăng nhập</Link></p>}
    <form onInvalid={vietnameseValidation} onInput={clearValidation} className={styles.form} onSubmit={submit}>
      {register && <><label>Họ tên<input name="hoTen" autoComplete="name" maxLength={160} required /></label>
        <label>Số điện thoại<input name="soDienThoai" type="tel" autoComplete="tel" pattern="(0[35789][0-9]{8}|\+84[35789][0-9]{8})" title="Số Việt Nam 10 chữ số hoặc dạng +84" required /></label></>}
      <label>Email<input name="email" type="email" autoComplete="username" maxLength={254} required /></label>
      <label>Mật khẩu<input name="matKhau" type="password" autoComplete={register ? 'new-password' : 'current-password'}
        minLength={register ? 10 : undefined} maxLength={128} required /></label>
      {register && <><p className={styles.muted}>Dùng ít nhất 10 ký tự, kết hợp chữ và số.</p>
        <label>Xác nhận mật khẩu<input name="xacNhan" type="password" autoComplete="new-password" maxLength={128} required /></label></>}
      <button className={styles.primary} disabled={busy}>{busy ? 'Đang xử lý…' : register ? 'Đăng ký' : 'Đăng nhập'}</button>
    </form>
    <div className={styles.links}><Link href={register ? '/dang-nhap' : '/dang-ky'}>{register ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Đăng ký'}</Link><Link href="/">Về trang chủ</Link></div>
  </section></main>;
}
