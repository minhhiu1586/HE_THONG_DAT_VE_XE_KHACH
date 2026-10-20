import { accountRequest } from './account-api';
import type { Profile, Session } from './account-types';
type Credentials = {
  email: string;
  matKhau: string;
};
type Registration = Credentials & {
  hoTen: string;
  soDienThoai: string;
};
export function loginAccount(input: Credentials) {
  return accountRequest<Session>('/auth/dang-nhap', 'POST', input);
}
export function registerAccount(input: Registration) {
  return accountRequest<Profile>('/auth/dang-ky', 'POST', input);
}
