import type { BookingRequest } from './booking-api';

export type RefundState = 'CHO_XU_LY' | 'THANH_CONG' | 'THAT_BAI' | 'CAN_DOI_SOAT';

export type RefundPolicy = {
  maChinhSach: string;
  tenChinhSach: string;
  soGioToiThieuTruocKhoiHanh: number;
  tyLeHoan: number;
  phiHoan: string;
  choPhepHoanVeKhuyenMai: boolean;
  trangThai: 'HOAT_DONG' | 'TAM_NGUNG' | 'NGUNG_AP_DUNG';
};

export type RefundEligibility = {
  maVe: string;
  chinhSach: RefundPolicy | null;
  duocYeuCauHoan: boolean;
  soTienDuKienHoan: string;
  donViTienTe: string;
  lyDoKhongDuocHoan: string | null;
  thoiDiemTinh: string;
};

export type RefundLine = {
  maVe: string;
  giaThucTra: string;
  tyLeHoan: string;
  phiHoan: string;
  soTienHoan: string;
};

export type RefundPreview = {
  maDon: string;
  maGiaoDichGoc: string | null;
  maChinhSach: string | null;
  soTienHoan: string;
  donViTienTe: string;
  chiTiet: RefundLine[];
  thoiDiemTinh: string;
};

type RefundHistoryEntry = {
  thaoTac: string;
  maRequestCong: string | null;
  maKetQua: string | null;
  maGiaoDichCong: string | null;
  thoiDiem: string;
};

export type RefundRecord = {
  maHoanTien: string;
  maDon: string;
  maGiaoDichGoc: string | null;
  maChinhSach: string | null;
  soTienHoan: string;
  donViTienTe: string;
  trangThai: RefundState;
  chiTiet: RefundLine[];
  thoiDiemTao: string;
  thoiDiemXacNhan: string | null;
  maGiaoDichCong: string | null;
  lyDoLoi: string | null;
  lichSu: RefundHistoryEntry[];
};

export type RefundPage = {
  items: RefundRecord[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export function refundApi(request: BookingRequest) {
  const orderPath = (orderId: string) => `/booking/${encodeURIComponent(orderId)}`;
  const ticketPath = (ticketId: string) => `/ve-xe/${encodeURIComponent(ticketId)}`;
  const refundPath = (refundId: string) => `/hoan-tien/${encodeURIComponent(refundId)}`;
  const refundBody = (ticketIds: string[], reason?: string) => ({
    danhSachMaVe: ticketIds,
    ...(reason === undefined ? {} : { lyDo: reason }),
  });

  return {
    eligibility: (ticketId: string) =>
      request<RefundEligibility>(`${ticketPath(ticketId)}/chinh-sach-hoan`),
    preview: (orderId: string, ticketIds: string[], reason?: string) =>
      request<RefundPreview>(`${orderPath(orderId)}/hoan-tien/du-kien`, 'POST', refundBody(ticketIds, reason)),
    create: (orderId: string, ticketIds: string[], reason: string | undefined, attemptKey: string) =>
      request<RefundRecord>(
        `${orderPath(orderId)}/hoan-tien`,
        'POST',
        refundBody(ticketIds, reason),
        { 'Idempotency-Key': attemptKey },
      ),
    get: (refundId: string) => request<RefundRecord>(refundPath(refundId)),
    history: async (ticketId: string): Promise<{ items: RefundRecord[] }> => {
      const items: RefundRecord[] = [];
      let page = 1;
      let totalPages = 1;
      do {
        const result = await request<RefundPage>(
          `${ticketPath(ticketId)}/hoan-tien?page=${page}&size=100&sort=thoiDiemTao,desc`,
        );
        items.push(...result.items);
        totalPages = result.totalPages;
        page += 1;
      } while (page <= totalPages);
      return { items };
    },
    verify: (refundId: string) => request<RefundRecord>(`${refundPath(refundId)}/xac-minh`, 'POST', {}),
  };
}
