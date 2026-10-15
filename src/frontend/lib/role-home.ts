import type { Profile } from './account-api';

// Chọn trang mặc định sau đăng nhập; vai trò do backend trả về. Đây là điều hướng, không cấp quyền API.
export function roleHome(role: Profile['loaiTaiKhoan']) {
  switch (role) {
    case 'ADMIN': return '/quan-tri';
    case 'NHA_XE': return '/quan-tri/nhan-vien';
    case 'NHAN_VIEN': return '/nhan-vien';
    default: return '/tai-khoan';
  }
}
