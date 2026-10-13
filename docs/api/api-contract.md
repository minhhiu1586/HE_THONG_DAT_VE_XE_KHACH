# Thiết kế hợp đồng API

Tài liệu mô tả hợp đồng mục tiêu cho 29 API A01–F03. Bản mô tả máy đọc được: [openapi.yaml](openapi.yaml).

## Cách đọc

- Trường bắt buộc, tùy chọn và nullable được ghi riêng theo API; thiếu trường khác với `null`. JSON thành công là object/array trực tiếp, không bọc thêm `data`.
- Quyền: `P` công khai; `KH` khách đã đăng nhập và là chủ tài nguyên; `NV` nhân viên có quyền/được phân công; `NX` nhà xe trong phạm vi của mình; `AD` quản trị có quyền; `CG` cổng thanh toán đã được xác minh. Backend phải kiểm tra chủ tài nguyên và phạm vi; T07 dùng phiên cookie/CSRF và T07.1 cung cấp helper phạm vi theo [account-auth.md](account-auth.md) và [hướng dẫn tích hợp](../tasks/T07.1-integration.md). Vai trò cho từng hành động nghiệp vụ vẫn phải đối chiếu T01.
- Quy ước chung về kiểu dữ liệu, phân trang, lỗi và idempotency nằm trong [README](README.md). Các API cần `Idempotency-Key`: **B01, B03, B07, C02, D01, E02, E03, F02**; các mã lỗi 409 dùng chung được mô tả tại [mục 8](README.md#8-chống-xử-lý-lặp). Bảng dưới đây chỉ liệt kê lỗi nghiệp vụ chính.
- Hợp đồng chi tiết của T17 nằm trong [booking-pricing.md](booking-pricing.md) và [booking-pricing-openapi.json](booking-pricing-openapi.json); C01/C02 ở dưới chỉ là chỉ mục ngắn.

## A — Tìm chuyến và xem ghế

| ID | Phương thức + đường dẫn | Quyền | Tham số / yêu cầu | Phản hồi thành công | Lỗi nghiệp vụ chính |
| --- | --- | --- | --- | --- | --- |
| A01 | GET `/api/tinh-thanh` | P | Không có | 200, `array<TinhThanh>` ([ví dụ](#a01--tỉnh-thành)) | — |
| A02 | GET `/api/ben-xe` | P | Query tùy chọn `maTinhThanh` | 200, `array<BenXe>` ([ví dụ](#a02--bến-xe)) | — |
| A03 | GET `/api/chuyen-xe` | P | Query bắt buộc `from`, `to`, `date`; `from` và `to` khác nhau. `page`, `size`, `sort` tùy chọn; sort allowlist: `gioKhoiHanh`, `giaThamKhao`, `soGheTrong` + `asc\|desc`. | 200, `Page<TripSummary>` ([ví dụ](#a03--kết-quả-tìm-chuyến)) | 400 `VALIDATION_ERROR` |
| A04 | GET `/api/chuyen-xe/{maChuyen}` | P | Path `maChuyen` | 200, `TripDetail` ([ví dụ](#a04--chi-tiết-chuyến)) | 404 `RESOURCE_NOT_FOUND` |
| A05 | GET `/api/chuyen-xe/{maChuyen}/ghe` | P | Path `maChuyen` | 200, `SeatMapResponse` ([ví dụ](#a05--sơ-đồ-ghế)) | 409 `INVALID_STATE_TRANSITION` nếu chuyến chưa được gán xe/đủ điều kiện (đề xuất) |
| A06 | GET `/api/chuyen-xe/{maChuyen}/gia` | P | Path `maChuyen` | 200, `ReferencePriceResponse` ([ví dụ](#a06--giá-tham-khảo)) | 404 `RESOURCE_NOT_FOUND` |

A03 hiện chuyển từ array đang có sang `Page` theo đề xuất. `maXe` và `nhaXe` trong A04 có thể là `null` khi chưa được gán; chi tiết đó không xác nhận chuyến đủ điều kiện đặt chỗ. Trạng thái khả dụng và `viTri` ở A05 còn cần đối chiếu mô hình xe.

### Ví dụ response thành công

#### A01 — Tỉnh thành

```json
[
  {
    "maTinhThanh": "HCM",
    "tenTinhThanh": "Thành phố Hồ Chí Minh",
    "khuVuc": "Miền Nam"
  }
]
```

#### A02 — Bến xe

```json
[
  {
    "maBenXe": "BX01",
    "tenBenXe": "Bến Miền Đông",
    "diaChi": "292 Đinh Bộ Lĩnh, Bình Thạnh",
    "soDienThoai": "02800000000",
    "maTinhThanh": "HCM",
    "trangThai": "HOAT_DONG"
  }
]
```

#### A03 — Kết quả tìm chuyến

```json
{
  "items": [
    {
      "maChuyen": "CX01",
      "maTuyen": "T01",
      "benDi": {
        "maBenXe": "BX01",
        "tenBenXe": "Bến Miền Đông"
      },
      "benDen": {
        "maBenXe": "BX02",
        "tenBenXe": "Bến Đà Lạt"
      },
      "ngayKhoiHanh": "2026-10-12",
      "gioKhoiHanh": "08:00:00",
      "giaThamKhao": "250000",
      "soGheTrong": 12
    }
  ],
  "page": 1,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

#### A04 — Chi tiết chuyến

```json
{
  "maChuyen": "CX01",
  "maTuyen": "T01",
  "maXe": "XE01",
  "ngayKhoiHanh": "2026-10-12",
  "gioKhoiHanh": "08:00:00",
  "trangThai": "CHUA_KHOI_HANH",
  "benDi": {
    "maBenXe": "BX01",
    "tenBenXe": "Bến Miền Đông"
  },
  "benDen": {
    "maBenXe": "BX02",
    "tenBenXe": "Bến Đà Lạt"
  },
  "nhaXe": {
    "maNhaXe": "NX01",
    "tenNhaXe": "Nhà xe mẫu"
  },
  "tienIch": [
    "WIFI",
    "MAY_LANH"
  ],
  "giaThamKhao": "250000"
}
```

#### A05 — Sơ đồ ghế

```json
{
  "maChuyen": "CX01",
  "maXe": "XE01",
  "serverTime": "2026-10-11T09:30:00+07:00",
  "danhSachGhe": [
    {
      "maGhe": "G11",
      "viTri": {
        "tang": 1,
        "hang": "A",
        "cot": 1
      },
      "trangThaiSuDung": "HOAT_DONG",
      "tinhTrangKhaDung": "TRONG"
    }
  ]
}
```

#### A06 — Giá tham khảo

```json
{
  "maChuyen": "CX01",
  "donViTienTe": "VND",
  "giaThamKhao": "250000",
  "danhSachGiaTheoLoaiGhe": [
    {
      "loaiGhe": "GIUONG_NAM",
      "gia": "250000"
    }
  ]
}
```

## B — Giữ chỗ và đơn đặt vé

| ID | Phương thức + đường dẫn | Quyền | Tham số / yêu cầu | Phản hồi thành công | Lỗi nghiệp vụ chính |
| --- | --- | --- | --- | --- | --- |
| B01 | POST `/api/booking/hold` | KH | Body JSON: `maChuyen`, `danhSachMaGhe` (không rỗng, không trùng). | 201 `HoldSeatResponse` ([ví dụ](#b01--giữ-ghế-và-tạo-đơn-nháp)) | 409 `SEAT_UNAVAILABLE`, `INVALID_STATE_TRANSITION` |
| B02/B05 | GET `/api/booking/{maDon}` | KH | Path `maDon`; trả đơn, thời hạn giữ và giá tạm tính theo hợp đồng T16. | 200 `OrderResponse` ([ví dụ đơn nháp](#b03-và-b04--đơn-nháp)) | 403 `FORBIDDEN`; 404 `RESOURCE_NOT_FOUND`; 409 `HOLD_EXPIRED` |
| B03 | PATCH `/api/booking/{maDon}` | KH | Path `maDon`; body cần ít nhất một trong `khachDatTen`, `khachDatSoDienThoai`, `ghiChu`. `ghiChu: null` xóa ghi chú. | 200 `OrderResponse` ([ví dụ nháp](#b03-và-b04--đơn-nháp)) | 409 `INVALID_STATE_TRANSITION` |
| B04 | PUT `/api/booking/{maDon}/hanh-khach` | KH | Path `maDon`; body thay toàn bộ `hanhKhach` (ít nhất 1). Mỗi phần tử cần `maGhe`, `hoTen`, `soDienThoai`; `maGhe` không trùng và tập `maGhe` phải khớp toàn bộ ghế đang giữ cho đơn. `cccd` tùy chọn, chuỗi tối đa 20 ký tự hoặc `null`. | 200 cùng `OrderResponse` ([ví dụ nháp](#b03-và-b04--đơn-nháp)) | 409 `HOLD_EXPIRED` |
| B06 | GET `/api/booking` | KH | Query tùy chọn `page`, `size`, `trangThaiDon`, `maChuyen`, `tuNgay`, `denNgay`, `sort`; sort theo `ngayTao` hoặc `thoiDiemKhoiHanh`, chiều `asc\|desc`. | 200 `Page<OrderResponse>` (wrapper mẫu tại [README](README.md#4-phân-trang-lọc-và-sắp-xếp)) | 400 `VALIDATION_ERROR` |
| B07 | POST `/api/booking/{maDon}/huy` | KH | Path `maDon`; body tùy chọn, nếu gửi thì `{}`. | 200 `OrderResponse` (hợp đồng T16 tại [booking-order.md](booking-order.md)) | 409 `INVALID_STATE_TRANSITION` |

B01 giữ toàn bộ ghế và tạo một đơn nháp trong cùng giao dịch nguyên tử; B03 và B04 chỉ cập nhật đơn do B01 tạo. Máy chủ nên kiểm tra lại chuyến trước khi giữ. Với B04, `soDienThoai` của từng hành khách là bắt buộc; danh sách hành khách được giữ tạm trong HTTP session, không tạo hàng hành khách hoặc vé nháp trong database. Cùng `JSESSIONID` đọc lại được snapshot; session mới vẫn đọc được đơn và ghế nhưng trả `hanhKhach: []` và `coTheChotGia: false`. Hợp đồng module [booking-order.md](booking-order.md) là nguồn có thẩm quyền cho chi tiết B01–B08 của T16; những mục B còn lại tại đây giữ phạm vi tương lai của T05.

### Request body

#### B01 — Giữ hai ghế và tạo đơn nháp

```json
{
  "maChuyen": "CX01",
  "danhSachMaGhe": ["G11", "G12"]
}
```

#### B03 — Cập nhật liên hệ và ghi chú

```json
{
  "khachDatTen": "Nguyen Van A",
  "khachDatSoDienThoai": "0900000001",
  "ghiChu": null
}
```

#### B04 — Thay danh sách hành khách

```json
{
  "hanhKhach": [
    {"maGhe": "G11", "hoTen": "Nguyen Van A", "soDienThoai": "0900000001", "cccd": null},
    {"maGhe": "G12", "hoTen": "Tran Thi B", "soDienThoai": "0900000002", "cccd": null}
  ]
}
```

Tên người đặt và hành khách dài 1–120 ký tự; số điện thoại người đặt dài 1–20; `ghiChu` tối đa 500. `hoTen` và `soDienThoai` bắt buộc trong mỗi hành khách; số điện thoại dài 1–20 ký tự. `cccd` tùy chọn, tối đa 20 ký tự hoặc `null`.

### Ví dụ response thành công

<a id="b01--giữ-ghế-và-tạo-đơn-nháp"></a>

#### B01 — Giữ ghế và tạo đơn nháp

```json
{
  "maDon": "DDV01",
  "maChuyen": "CX01",
  "trangThaiDon": "CHO_XU_LY",
  "serverTime": "2026-10-13T09:30:00+07:00",
  "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
  "thoiDiemHetHan": "2026-10-13T09:45:00+07:00",
  "danhSachGheGiu": [
    "G11",
    "G12"
  ],
  "danhSachGiuCho": [
    {
      "maGiuCho": "GC01",
      "maGhe": "G11",
      "trangThai": "DANG_GIU",
      "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
      "thoiDiemHetHan": "2026-10-13T09:45:00+07:00"
    },
    {
      "maGiuCho": "GC02",
      "maGhe": "G12",
      "trangThai": "DANG_GIU",
      "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
      "thoiDiemHetHan": "2026-10-13T09:45:00+07:00"
    }
  ]
}
```

#### B03 và B04 — Đơn nháp

```json
{
  "maDon": "DDV01",
  "maChuyen": "CX01",
  "trangThaiDon": "CHO_XU_LY",
  "khachDatTen": "Nguyen Van A",
  "khachDatSoDienThoai": "0900000001",
  "ghiChu": null,
  "serverTime": "2026-10-13T09:32:00+07:00",
  "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
  "thoiDiemHetHan": "2026-10-13T09:45:00+07:00",
  "danhSachGiuCho": [
    {
      "maGiuCho": "GC01",
      "maGhe": "G11",
      "trangThai": "DANG_GIU",
      "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
      "thoiDiemHetHan": "2026-10-13T09:45:00+07:00"
    }
  ],
  "hanhKhach": [
    {
      "maGhe": "G11",
      "hoTen": "Nguyen Van A",
      "soDienThoai": "0900000001",
      "cccd": null
    }
  ],
  "danhSachVe": [],
  "giaTamTinh": {
    "tongTienGoc": "125000",
    "tienGiam": "0",
    "tongThanhToan": "125000",
    "maKhuyenMaiDaChon": null,
    "maKhuyenMaiApDung": null,
    "lyDoKhongApDung": null
  },
  "coTheChotGia": true,
  "giaDaChot": null,
  "thongBao": []
}
```

#### B05 — Đơn và snapshot giá

`giaDaChot` là `null` trước khi chốt, sau khi C02 thành công thì chứa snapshot tiền đã chốt. Hợp đồng chi tiết nằm trong [booking-pricing.md](booking-pricing.md); snapshot không có `phienBanGia`, `maBaoGia` hoặc `thoiDiemBaoGia` khi đọc lại đơn.

```json
{
  "maDon": "DDV01",
  "maChuyen": "CX01",
  "trangThaiDon": "CHO_XU_LY",
  "khachDatTen": "Nguyen Van A",
  "khachDatSoDienThoai": "0900000001",
  "ghiChu": null,
  "danhSachGiuCho": [
    {
      "maGiuCho": "GC01",
      "maGhe": "G11",
      "trangThai": "DANG_GIU"
    },
    {
      "maGiuCho": "GC02",
      "maGhe": "G12",
      "trangThai": "DANG_GIU"
    }
  ],
  "hanhKhach": [
    {
      "maGhe": "G11",
      "hoTen": "Nguyen Van A",
      "soDienThoai": "0900000001",
      "cccd": null
    },
    {
      "maGhe": "G12",
      "hoTen": "Tran Thi B",
      "soDienThoai": "0900000002",
      "cccd": null
    }
  ],
  "danhSachVe": [],
  "giaDaChot": {
    "donViTienTe": "VND",
    "tongTienGoc": "400000",
    "tongTienGiam": "40000",
    "tongThanhToan": "360000",
    "chiTietGia": [
      {
        "maGhe": "G11",
        "giaGoc": "200000",
        "tienGiam": "20000",
        "thanhTien": "180000"
      },
      {
        "maGhe": "G12",
        "giaGoc": "200000",
        "tienGiam": "20000",
        "thanhTien": "180000"
      }
    ]
  }
}
```

Trước khi chốt, `giaDaChot` là `null`; sau khi chốt, đây là snapshot bất biến gồm đơn vị tiền, ba tổng tiền và phân bổ theo ghế. Frontend tải lại đơn qua B05 rồi dùng số tiền do backend trả khi bắt đầu thanh toán ở D01.

#### B07 — Hủy đơn

```json
{
  "maDon": "DDV01",
  "maChuyen": "CX01",
  "trangThaiDon": "DA_HUY",
  "khachDatTen": "Nguyen Van A",
  "khachDatSoDienThoai": "0900000001",
  "ghiChu": null,
  "serverTime": "2026-10-13T09:32:00+07:00",
  "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
  "thoiDiemHetHan": "2026-10-13T09:45:00+07:00",
  "danhSachGiuCho": [
    {
      "maGiuCho": "GC01",
      "maGhe": "G11",
      "trangThai": "DA_HUY",
      "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
      "thoiDiemHetHan": "2026-10-13T09:45:00+07:00"
    }
  ],
  "hanhKhach": [],
  "danhSachVe": [],
  "giaTamTinh": {
    "tongTienGoc": "0",
    "tienGiam": "0",
    "tongThanhToan": "0",
    "maKhuyenMaiDaChon": null,
    "maKhuyenMaiApDung": null,
    "lyDoKhongApDung": null
  },
  "coTheChotGia": false,
  "giaDaChot": null,
  "thongBao": []
}
```

## C — Báo giá và phương thức thanh toán

| ID | Phương thức + đường dẫn | Quyền | Tham số / yêu cầu | Phản hồi thành công | Lỗi nghiệp vụ chính |
| --- | --- | --- | --- | --- | --- |
| C01 | POST `/api/booking/{maDon}/bao-gia` | KH | Path `maDon`; body `{}` hoặc `maKhuyenMai` là chuỗi (1–40 ký tự) hoặc `null`; không nhận giá từ frontend. | 200 `QuoteResponse` ([ví dụ](#c01--báo-giá)) | 409 `QUOTE_EXPIRED`, `PRICE_CHANGED`, `PROMOTION_NOT_APPLICABLE` |
| C02 | POST `/api/booking/{maDon}/chot-gia` | KH | Path `maDon`; body bắt buộc `maBaoGia`; header `Idempotency-Key` bắt buộc. | 200 `FreezePriceResponse` ([ví dụ](#c02--chốt-giá)) | 409 `QUOTE_EXPIRED`, `PRICE_CHANGED`, `PROMOTION_EXHAUSTED`, `HOLD_EXPIRED` |
| C03 | GET `/api/phuong-thuc-thanh-toan` | P | Không có | 200, `array<PaymentMethodDto>` ([ví dụ](#c03--phương-thức-thanh-toán)) | — |

C01 chỉ xem trước báo giá và không giữ lượt khuyến mãi. C02 chốt đúng báo giá đã gửi, tính lượt giữ từ các đơn `CHO_THANH_TOAN` còn hạn và chuyển đơn sang `CHO_THANH_TOAN`; `giu_cho` vẫn `DANG_GIU`. Nếu báo giá hết hạn hoặc giá thay đổi, ứng dụng cần lấy báo giá mới. T16 preview có thể còn số thập phân chính xác; T17 làm tròn tổng giảm bằng `HALF_UP` khi chốt và trả VND nguyên. Mã khuyến mãi là mã dùng chung vì diagram chưa có trường phạm vi theo nhà xe hoặc tuyến; T17 cũng không thêm cột giá riêng cho từng vé. Hành khách chỉ nằm trong HTTP session nên callback T18 không được giả định session còn tồn tại. Các dòng vé gốc và tổng đơn phải được giữ để tra cứu sau hoàn một phần. Xem chi tiết công thức và rollback tại [booking-pricing.md](booking-pricing.md).

### Request body

#### C01 — Preview có mã khuyến mãi

```json
{
  "maKhuyenMai": "KM10"
}
```

#### C02 — Chốt đúng báo giá đã xem trước

```json
{
  "maBaoGia": "BG01"
}
```

### Ví dụ response thành công

#### C01 — Báo giá

```json
{
  "maBaoGia": "BG01",
  "maDon": "DDV01",
  "donViTienTe": "VND",
  "thoiDiemBaoGia": "2026-10-11T09:32:00+07:00",
  "thoiDiemHetHan": "2026-10-11T09:45:00+07:00",
  "tongTienGoc": "400000",
  "tongTienGiam": "40000",
  "tongThanhToan": "360000",
  "chiTietGia": [
    {
      "maGhe": "G11",
      "giaGoc": "200000",
      "tienGiam": "20000",
      "thanhTien": "180000"
    },
    {
      "maGhe": "G12",
      "giaGoc": "200000",
      "tienGiam": "20000",
      "thanhTien": "180000"
    }
  ],
  "ketQuaKhuyenMai": {
    "hopLe": true,
    "duocGiam": "40000"
  }
}
```

`ketQuaKhuyenMai` là `null` khi không áp mã; giá trị này không cho biết lượt khuyến mãi đã được giữ hay chưa.

#### C02 — Chốt giá

```json
{
  "maBaoGia": "BG01",
  "maDon": "DDV01",
  "donViTienTe": "VND",
  "trangThaiDon": "CHO_THANH_TOAN",
  "thoiDiemHetHan": "2026-10-11T09:45:00+07:00",
  "tongTienGoc": "400000",
  "tongTienGiam": "40000",
  "tongThanhToan": "360000",
  "chiTietGia": [
    {
      "maGhe": "G11",
      "giaGoc": "200000",
      "tienGiam": "20000",
      "thanhTien": "180000"
    },
    {
      "maGhe": "G12",
      "giaGoc": "200000",
      "tienGiam": "20000",
      "thanhTien": "180000"
    }
  ],
  "ketQuaKhuyenMai": {
    "hopLe": true,
    "duocGiam": "40000"
  }
}
```

#### C03 — Phương thức thanh toán

```json
[
  {
    "maPhuongThuc": "PT01",
    "tenPhuongThuc": "Chuyển khoản",
    "moTa": "Thanh toán trực tuyến",
    "trangThai": "HOAT_DONG"
  }
]
```

## D — Thanh toán và vé điện tử

| ID | Phương thức + đường dẫn | Quyền | Tham số / yêu cầu | Phản hồi thành công | Lỗi nghiệp vụ chính |
| --- | --- | --- | --- | --- | --- |
| D01 | POST `/api/booking/{maDon}/thanh-toan` | KH | Path `maDon`; body cần `maPhuongThuc`. Số tiền và snapshot đọc từ backend; không gửi `phienBanGia`. | 201 khi khởi tạo; 202 khi đang chờ. `PaymentResponse` ([ví dụ](#d01--tạo-lần-thanh-toán)) | 409 `PAYMENT_PROCESSING` |
| D02 | GET `/api/thanh-toan/{maGiaoDich}` | KH; NX/AD theo quyền | Path `maGiaoDich` | 200 cùng `PaymentResponse` ([ví dụ](#d01--tạo-lần-thanh-toán)) | 404 `RESOURCE_NOT_FOUND` |
| D03 | GET `/api/booking/{maDon}/thanh-toan` | KH; NX/AD theo quyền | Path `maDon`; query `page`, `size`, `trangThai`, `sort` (`thoiDiemTao,asc\|desc`). | 200 `Page<PaymentResponse>` (wrapper mẫu tại [README](README.md#4-phân-trang-lọc-và-sắp-xếp)) | 404 `RESOURCE_NOT_FOUND` |
| D04 | Method chưa chốt — `/api/thanh-toan/callback/{congThanhToan}` | CG | Path dự kiến có `congThanhToan`; body, chữ ký, response/ACK phụ thuộc cổng. | Chưa định nghĩa | Chờ đặc tả provider |
| D05 | GET `/api/ve-xe/{maVe}` | KH; NV/NX/AD theo quyền | Path `maVe` | 200 `TicketResponse` ([ví dụ](#d05--vé-điện-tử)) | 404 `RESOURCE_NOT_FOUND` |

D01 tạo giao dịch từ giá đã chốt; 201/202 không chứng minh đã thu tiền. D02/B05 là nguồn trạng thái có thẩm quyền. D04 chưa có method, schema, chữ ký hoặc ACK vì chưa chọn cổng thanh toán.

### Request body

#### D01 — Tạo lần thanh toán theo giá đã chốt

```json
{
  "maPhuongThuc": "PT01"
}
```

### Ví dụ response thành công

#### D01 — Tạo lần thanh toán

```json
{
  "maGiaoDich": "GD01",
  "maDon": "DDV01",
  "maPhuongThuc": "PT01",
  "soTien": "360000",
  "donViTienTe": "VND",
  "trangThai": "CHO_XU_LY",
  "ketQuaCongDaXacDinh": false,
  "thoiDiemTao": "2026-10-11T09:33:00+07:00",
  "urlThanhToan": "https://pay.example/checkout/GD01",
  "thoiDiemHetHan": "2026-10-11T09:45:00+07:00"
}
```

Tra cứu dùng cùng kiểu `PaymentResponse` với D01. `urlThanhToan` và `thoiDiemHetHan` có thể là `null`; `ketQuaCongDaXacDinh: false` nghĩa là kết quả cổng chưa xác định.

#### D05 — Vé điện tử

```json
{
  "maVe": "VE01",
  "maDon": "DDV01",
  "maChuyen": "CX01",
  "maGhe": "G11",
  "hoTenHanhKhach": "Nguyen Van A",
  "trangThai": "DA_XUAT_VE",
  "giaGoc": "200000",
  "tienGiam": "20000",
  "thanhTien": "180000",
  "donViTienTe": "VND",
  "qrToken": "opaque-server-token",
  "thoiDiemPhatHanh": "2026-10-11T09:40:00+07:00"
}
```

## E — Hủy vé và hoàn tiền

| ID | Phương thức + đường dẫn | Quyền | Tham số / yêu cầu | Phản hồi thành công | Lỗi nghiệp vụ chính |
| --- | --- | --- | --- | --- | --- |
| E01 | GET `/api/ve-xe/{maVe}/chinh-sach-hoan` | KH | Path `maVe` | 200 `RefundPolicyResponse` ([ví dụ](#e01--điều-kiện-hoàn)) | 404 `RESOURCE_NOT_FOUND` |
| E02 | POST `/api/ve-xe/{maVe}/huy` | KH; NV/NX theo chính sách | Path `maVe`; không có body | 200 `CancelTicketResponse` ([ví dụ](#e02--hủy-vé)) | 409 `REFUND_NOT_ALLOWED` |
| E03 | POST `/api/ve-xe/{maVe}/hoan-tien` | KH; NV/NX theo chính sách | Path `maVe`; body JSON bắt buộc. `lyDo` tùy chọn, là chuỗi tối đa 500 ký tự hoặc `null`; không có lý do thì gửi `{}`. | 202 `RefundResponse` ([ví dụ](#e03-và-e04--yêu-cầu-hoàn)) | 409 `REFUND_NOT_ALLOWED` |
| E04 | GET `/api/hoan-tien/{maHoanTien}` | KH; NX/AD theo quyền | Path `maHoanTien` | 200 cùng `RefundResponse` ([ví dụ](#e03-và-e04--yêu-cầu-hoàn)) | 404 `RESOURCE_NOT_FOUND` |
| E05 | GET `/api/ve-xe/{maVe}/hoan-tien` | KH; NX/AD theo quyền | Path `maVe`; query `page`, `size`, `sort` (`thoiDiemTao,asc\|desc`). | 200 `Page<RefundResponse>` (wrapper mẫu tại [README](README.md#4-phân-trang-lọc-và-sắp-xếp)) | 404 `RESOURCE_NOT_FOUND` |

Điều kiện hủy trực tiếp và vòng đời hoàn tiền còn chờ thống nhất. E01 chỉ xem khoản dự kiến; E03 để server chốt số tiền. Một yêu cầu gắn với một vé, không vượt phần tiền của vé. Không giải phóng ghế chỉ vì đã nhận yêu cầu hoàn.

### Request body

#### E03 — Gửi yêu cầu hoàn

```json
{
  "lyDo": "Thay đổi kế hoạch"
}
```

### Ví dụ response thành công

#### E01 — Điều kiện hoàn

```json
{
  "maVe": "VE01",
  "chinhSach": {
    "maChinhSach": "CS01",
    "soGioToiThieuTruocKhoiHanh": 0,
    "tyLeHoan": "100",
    "phiHoan": "0",
    "choPhepHoanHuyVe": true
  },
  "duocYeuCauHoan": true,
  "soTienDuKienHoan": "180000",
  "donViTienTe": "VND",
  "lyDoKhongDuocHoan": null,
  "thoiDiemTinh": "2026-10-11T10:00:00+07:00"
}
```

`chinhSach` có thể là `null`; cấu trúc trong ví dụ là đề xuất. `tyLeHoan` là chuỗi phần trăm.

#### E02 — Hủy vé

```json
{
  "maVe": "VE02",
  "trangThai": "DA_HUY",
  "thoiDiemHuy": "2026-10-11T10:00:00+07:00",
  "canTaoYeuCauHoan": false
}
```

Đây là tình huống giả định độc lập với ví dụ E03 bên dưới: vé `VE02` được hủy nhưng không tạo yêu cầu hoàn (`canTaoYeuCauHoan: false`). Nếu cần hoàn tiền cho vé `VE01`, dùng luồng E03 riêng. Điều kiện hủy và chính sách hoàn tiền còn chờ thống nhất.

#### E03 và E04 — Yêu cầu hoàn

```json
{
  "maHoanTien": "HT01",
  "maVe": "VE01",
  "maGiaoDichGoc": "GD01",
  "soTienHoan": "180000",
  "trangThai": "CHO_XU_LY",
  "thoiDiemTao": "2026-10-11T10:00:00+07:00"
}
```

`trangThai` là chuỗi; `CHO_XU_LY` hiện chỉ là giá trị ví dụ tạm, chưa phải enum đã chốt. `maGiaoDichGoc` có thể là `null` nếu chưa xác định.

## F — Kiểm vé

| ID | Phương thức + đường dẫn | Quyền | Tham số / yêu cầu | Phản hồi thành công | Lỗi nghiệp vụ chính |
| --- | --- | --- | --- | --- | --- |
| F01 | POST `/api/kiem-ve/xac-minh` | NV/NX/AD theo quyền vận hành | Body cần `maChuyen` và đúng một trong `maVe` hoặc `qrToken` (1–2048 ký tự). | 200 `TicketVerifyResponse` ([ví dụ](#f01--xác-minh-vé)) | `hopLe:false` vẫn là 200; lỗi nhập liệu là 400 `VALIDATION_ERROR` |
| F02 | POST `/api/ve-xe/{maVe}/len-xe` | NV được phân công | Path `maVe`; body cần `maChuyen`. | 200 `CheckInResponse` ([ví dụ](#f02--xác-nhận-lên-xe)) | 409 `INVALID_STATE_TRANSITION` nếu vé hoặc chuyến chưa ở trạng thái cho phép lên xe; `TICKET_ALREADY_CHECKED_IN` nếu vé đã được xác nhận lên xe. |
| F03 | GET `/api/chuyen-xe/{maChuyen}/kiem-ve` | NV/NX theo phạm vi | Path `maChuyen`; query `page`, `size`, `maGhe`, `tuThoiDiem`, `denThoiDiem`, `sort` (`thoiDiemKiem` hoặc `maVe`, `asc\|desc`). | 200 `Page<TicketCheckLog>` ([ví dụ](#f03--nhật-ký-kiểm-vé)) | 403 `FORBIDDEN`; 404 `RESOURCE_NOT_FOUND` |

F01 chỉ xác minh và không ghi nhận lên xe; QR token nằm trong body, không đặt trong URL. F02 kiểm tra lại trạng thái và chuyến tại thời điểm ghi nhận. Idempotency cùng key không tạo lần ghi nhận mới.

### Request body

#### F01 — Xác minh bằng mã vé

```json
{
  "maChuyen": "CX01",
  "maVe": "VE01"
}
```

Có thể dùng `qrToken` thay cho `maVe`, không gửi cả hai.

#### F02 — Xác nhận lên xe

```json
{
  "maChuyen": "CX01"
}
```

### Ví dụ response thành công

#### F01 — Xác minh vé

```json
{
  "hopLe": true,
  "maVe": "VE01",
  "maChuyen": "CX01",
  "maGhe": "G11",
  "maLyDo": null,
  "lyDo": null
}
```

Khi `hopLe:false`, `maLyDo` và `lyDo` là chuỗi giải thích; các trường `maVe`/`maGhe` có thể `null` tùy kết quả.

#### F02 — Xác nhận lên xe

```json
{
  "maVe": "VE01",
  "maChuyen": "CX01",
  "maGhe": "G11",
  "trangThai": "DA_LEN_XE",
  "thoiDiemLenXe": "2026-10-12T07:30:00+07:00"
}
```

#### F03 — Nhật ký kiểm vé

```json
{
  "items": [
    {
      "maVe": "VE01",
      "maChuyen": "CX01",
      "maGhe": "G11",
      "ketQua": "DA_LEN_XE",
      "thoiDiemKiem": "2026-10-12T07:30:00+07:00"
    }
  ],
  "page": 1,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

`ketQua` hiện có ví dụ `DA_LEN_XE`; giá trị và phạm vi log còn là đề xuất.
