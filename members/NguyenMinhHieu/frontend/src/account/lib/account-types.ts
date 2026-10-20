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
export interface Session {
  nguoiDung: Profile;
  csrfToken: string;
  hetHan: string;
}
