import type { Profile } from '@members/NguyenMinhHieu/account/lib/account-api';

export function roleHome(role: Profile['loaiTaiKhoan']) {
  switch (role) {
    case 'ADMIN': return '/quan-tri';
    case 'NHA_XE': return '/quan-tri/nhan-vien';
    case 'NHAN_VIEN': return '/nhan-vien';
    default: return '/tai-khoan';
  }
}
