import type { Profile } from './account-api';

export const managementItems = [
  { href: '/quan-tri/nhan-vien', label: 'Nhân viên và phân công', icon: '👥', description: 'Hồ sơ nhân viên và lịch phân công chuyến.', adminOnly: false },
  { href: '/quan-tri/tai-khoan', label: 'Tài khoản', icon: '👤', description: 'Quản lý trạng thái tài khoản người dùng.', adminOnly: true },
  { href: '/quan-tri/nha-xe', label: 'Nhà xe', icon: '🏢', description: 'Hồ sơ nhà xe và tài khoản quản lý.', adminOnly: true },
  { href: '/quan-tri/tuyen-duong', label: 'Tuyến đường và bến xe', icon: '🗺️', description: 'Tỉnh thành, bến xe, tuyến xe và điểm dừng.', adminOnly: false },
  { href: '/quan-tri/xe', label: 'Xe và sơ đồ ghế', icon: '🚌', description: 'Loại xe, phương tiện, đăng kiểm và bảo trì ghế.', adminOnly: false },
  { href: '/quan-tri/chuyen-xe', label: 'Chuyến xe và mở bán', icon: '📅', description: 'Tạo chuyến nháp, kiểm tra vận hành và xác nhận mở bán.', adminOnly: false },
  { href: '/quan-tri/danh-gia', label: 'Kiểm duyệt đánh giá', icon: '⭐', description: 'Duyệt và quản lý nhận xét chuyến xe.', adminOnly: false },
];
export function canManage(role?: Profile['loaiTaiKhoan']) { return role === 'ADMIN' || role === 'NHA_XE'; }
export function navigationFor(role: Profile['loaiTaiKhoan']) {
  return canManage(role) ? managementItems.filter(item => role === 'ADMIN' || !item.adminOnly) : [];
}
