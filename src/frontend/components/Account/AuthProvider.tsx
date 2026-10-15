'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { accountRequest, ApiError, Profile, Session } from '@/lib/account-api';

type Auth = {
  user: Profile | null; loading: boolean; notice: string;
  accept: (session: Session) => void; refresh: () => Promise<void>; clear: (notice?: string) => void;
  request: <T>(path: string, method?: string, body?: unknown, headers?: Record<string, string>) => Promise<T>;
};
// Context chia sẻ trạng thái phiên cho form, header và các trang; RootLayout bọc cây UI bằng AuthProvider.
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: React.ReactNode }) {
  // Sau F5, state trở lại null; chưa kết luận đã logout cho đến khi GET /me hoàn tất (loading=true).
  const [user, setUser] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');
  // Chỉ giữ CSRF trong bộ nhớ; raw token phiên nằm ở cookie HttpOnly, JS không đọc trực tiếp.
  const csrf = useRef<string | undefined>(undefined);
  // AuthForm gọi sau login; /me cũng dùng hàm này để khôi phục profile và CSRF.
  const accept = useCallback((session: Session) => {
    csrf.current = session.csrfToken; setUser(session.nguoiDung); setNotice('');
  }, []);
  // Chỉ xóa trạng thái phía frontend; muốn logout đầy đủ phải gọi API thu hồi phiên và xóa cookie.
  const clear = useCallback((message = '') => { csrf.current = undefined; setUser(null); setNotice(message); }, []);
  // Hỏi lại backend phiên hiện tại; 401 thì xóa state, lỗi mạng thì báo chưa thể kiểm tra.
  const refresh = useCallback(async () => {
    const hadSession = csrf.current !== undefined;
    try { accept(await accountRequest<Session>('/tai-khoan/me')); }
    catch (error) {
      if (error instanceof ApiError && error.status === 401) clear(hadSession ? 'Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.' : '');
      else setNotice('Chưa thể kiểm tra phiên đăng nhập. Vui lòng tải lại trang.');
    } finally { setLoading(false); }
  }, [accept, clear]);
  // KHÔI PHỤC PHIÊN: khi Provider mount/F5, gọi GET /api/tai-khoan/me; browser gửi cookie.
  // active tránh cập nhật state từ request đã hoàn tất sau khi effect bị cleanup.
  useEffect(() => {
    let active = true;
    accountRequest<Session>('/tai-khoan/me').then(session => {
      if (active) accept(session);
    }).catch(error => {
      if (!active) return;
      if (error instanceof ApiError && error.status === 401) {
        const privatePage = window.location.pathname === '/tai-khoan' || window.location.pathname.startsWith('/quan-tri/');
        clear(privatePage ? 'Vui lòng đăng nhập để tiếp tục. Phiên trước có thể đã hết hạn hoặc bị thu hồi.' : '');
      }
      else setNotice('Chưa thể kiểm tra phiên đăng nhập. Vui lòng tải lại trang.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [accept, clear]);
  useEffect(() => {
    // Khi quay lại tab, kiểm tra lại phiên để nhận biết hết hạn/thu hồi; không phải polling liên tục.
    const update = () => { if (document.visibilityState === 'visible') void refresh(); };
    document.addEventListener('visibilitychange', update);
    return () => document.removeEventListener('visibilitychange', update);
  }, [refresh]);
  // Các thao tác cần phiên gọi wrapper này để tự gắn CSRF; 401 xóa user, 403 vẫn ném lỗi mà không tự logout.
  const request = useCallback(async <T,>(path: string, method = 'GET', body?: unknown, headers?: Record<string, string>): Promise<T> => {
    try { return await accountRequest<T>(path, method, body, csrf.current, headers); }
    catch (error) {
      if (error instanceof ApiError && error.status === 401) clear('Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.');
      throw error;
    }
  }, [clear]);
  return <Context.Provider value={{ user, loading, notice, accept, refresh, clear, request }}>{children}</Context.Provider>;
}
// Hook cho component con lấy Context; chỉ được dùng trong cây có AuthProvider.
export function useAuth() {
  const auth = useContext(Context);
  if (!auth) throw new Error('useAuth requires AuthProvider');
  return auth;
}
