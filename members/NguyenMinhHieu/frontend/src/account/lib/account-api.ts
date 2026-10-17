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
export interface Session { nguoiDung: Profile; csrfToken: string; hetHan: string }
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string,
    public fieldErrors: { field: string; message: string }[] = []) { super(message); }
}

export async function accountRequest<T>(path: string, method = 'GET', body?: unknown, csrfToken?: string, extraHeaders?: Record<string, string>): Promise<T> {
  const headers: Record<string, string> = { ...extraHeaders, 'X-Requested-With': 'datvexe' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (csrfToken) headers['X-CSRF-Token'] = csrfToken;
  let response: Response;
  try {
    response = await fetch(`/api${path}`, { method, headers, credentials: 'same-origin', cache: 'no-store',
      body: body === undefined ? undefined : JSON.stringify(body) });
  } catch { throw new ApiError(0, 'NETWORK_ERROR', 'Không thể kết nối máy chủ. Vui lòng thử lại.'); }
  if (response.status === 204) return undefined as T;
  const result = await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response.status, result?.code ?? 'INTERNAL_ERROR',
    result?.message ?? 'Không thể xử lý yêu cầu lúc này.', result?.details?.fieldErrors ?? []);
  return result as T;
}
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Đã xảy ra lỗi. Vui lòng thử lại.';
}
