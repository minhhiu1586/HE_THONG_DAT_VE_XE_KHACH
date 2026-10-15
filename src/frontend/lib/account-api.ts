// Dữ liệu hồ sơ được backend chọn lọc để trả cho frontend; không chứa hash mật khẩu.
export interface Profile {
  maNguoiDung: string;
  hoTen: string;
  email: string;
  soDienThoai: string;
  gioiTinh: 'NAM' | 'NU' | 'KHAC' | null;
  ngaySinh: string | null;
  cccd: string | null;
  loaiTaiKhoan: 'KHACH_HANG' | 'NHA_XE' | 'NHAN_VIEN' | 'ADMIN';
  trangThai: 'HOAT_DONG' | 'KHOA' | 'CHO_XAC_THUC';
}
// Session ở đây là kiểu JSON trả về từ login/me, không phải HttpSession của Servlet hay raw cookie token.
export interface Session { nguoiDung: Profile; csrfToken: string; hetHan: string }
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string,
    public fieldErrors: { field: string; message: string }[] = []) { super(message); }
}
/** All modules use the same cookie session and structured error contract. No token in localStorage. */
// LUỒNG HTTP: AuthForm gọi helper này → fetch /api/... → Next.js rewrite → Java AuthFilter.
// T là kiểu kết quả mong đợi cho TypeScript; bản thân generic không kiểm định JSON khi chạy.
export async function accountRequest<T>(path: string, method = 'GET', body?: unknown, csrfToken?: string, extraHeaders?: Record<string, string>): Promise<T> {
  // Header ứng dụng dùng cho request public thay đổi dữ liệu; CSRF được thêm khi caller đã có phiên.
  const headers: Record<string, string> = { ...extraHeaders, 'X-Requested-With': 'datvexe' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (csrfToken) headers['X-CSRF-Token'] = csrfToken;
  let response: Response;
  try {
    // Cùng origin nên browser có thể gửi cookie phiên; body JavaScript được đổi thành chuỗi JSON.
    // Đường dẫn /api được next.config.ts chuyển tiếp đến backend; không kết nối trực tiếp database.
    response = await fetch(`/api${path}`, { method, headers, credentials: 'same-origin', cache: 'no-store',
      body: body === undefined ? undefined : JSON.stringify(body) });
  } catch { throw new ApiError(0, 'NETWORK_ERROR', 'Không thể kết nối máy chủ. Vui lòng thử lại.'); }
  // Logout thành công trả 204 không có JSON; bỏ qua đọc body để tránh lỗi parse.
  if (response.status === 204) return undefined as T;
  const result = await response.json().catch(() => null);
  // fetch không tự ném lỗi vì HTTP 401/403; helper chuyển phản hồi lỗi thành ApiError cho form/provider xử lý.
  if (!response.ok) throw new ApiError(response.status, result?.code ?? 'INTERNAL_ERROR',
    result?.message ?? 'Không thể xử lý yêu cầu lúc này.', result?.details?.fieldErrors ?? []);
  return result as T;
}
// Chuyển lỗi thành thông báo hiển thị; AuthForm dùng trong catch để setError().
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Đã xảy ra lỗi. Vui lòng thử lại.';
}
