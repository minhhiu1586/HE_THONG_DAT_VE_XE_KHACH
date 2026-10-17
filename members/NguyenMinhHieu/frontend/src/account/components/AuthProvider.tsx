'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { accountRequest, ApiError, Profile, Session } from '@members/NguyenMinhHieu/account/lib/account-api';

type Auth = {
  user: Profile | null; loading: boolean; notice: string;
  accept: (session: Session) => void; refresh: () => Promise<void>; clear: (notice?: string) => void;
  request: <T>(path: string, method?: string, body?: unknown, headers?: Record<string, string>) => Promise<T>;
};
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');
  const csrf = useRef<string | undefined>(undefined);
  const accept = useCallback((session: Session) => {
    csrf.current = session.csrfToken; setUser(session.nguoiDung); setNotice('');
  }, []);
  const clear = useCallback((message = '') => { csrf.current = undefined; setUser(null); setNotice(message); }, []);
  const refresh = useCallback(async () => {
    const hadSession = csrf.current !== undefined;
    try { accept(await accountRequest<Session>('/tai-khoan/me')); }
    catch (error) {
      if (error instanceof ApiError && error.status === 401) clear(hadSession ? 'Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.' : '');
      else setNotice('Chưa thể kiểm tra phiên đăng nhập. Vui lòng tải lại trang.');
    } finally { setLoading(false); }
  }, [accept, clear]);
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
    const update = () => { if (document.visibilityState === 'visible') void refresh(); };
    document.addEventListener('visibilitychange', update);
    return () => document.removeEventListener('visibilitychange', update);
  }, [refresh]);
  const request = useCallback(async <T,>(path: string, method = 'GET', body?: unknown, headers?: Record<string, string>): Promise<T> => {
    try { return await accountRequest<T>(path, method, body, csrf.current, headers); }
    catch (error) {
      if (error instanceof ApiError && error.status === 401) clear('Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.');
      throw error;
    }
  }, [clear]);
  return <Context.Provider value={{ user, loading, notice, accept, refresh, clear, request }}>{children}</Context.Provider>;
}
export function useAuth() {
  const auth = useContext(Context);
  if (!auth) throw new Error('useAuth requires AuthProvider');
  return auth;
}
