export type Review = {
  maDanhGia: string;
  maChuyen: string;
  soSao: number;
  binhLuan: string;
  ngayDanhGia: string;
  trangThai?: string;
  tenHienThi: string;
};
export type ReviewPage = {
  items: Review[];
  page: number;
  totalPages: number;
  totalItems: number;
  rating?: number | null;
};
export type Eligibility = {
  coTheDanhGia: boolean;
  coTheSua: boolean;
  lyDo: string;
  danhGia: Review | null;
};
export const reviewStates: Record<string, string> = {
  CHO_DUYET: 'Chờ duyệt',
  DA_DUYET: 'Đã duyệt',
  AN: 'Đã ẩn',
  DA_XOA: 'Đã xóa',
};
