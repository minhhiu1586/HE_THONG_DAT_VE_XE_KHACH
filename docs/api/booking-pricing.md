# Báo giá và chốt giá đơn đặt vé — T17

T17 bổ sung bước tính giá sau T16 và trước T18. Tài liệu này là hợp đồng module; OpenAPI độc lập nằm tại [booking-pricing-openapi.json](booking-pricing-openapi.json). T17 dùng lại đơn, ghế giữ, hành khách tạm trong HTTP session và quy tắc khuyến mãi của T14. Không thêm entity, cột hoặc trạng thái vào class diagram.

## API

| API | Phương thức và đường dẫn | Thành công | Mục đích |
| --- | --- | --- | --- |
| C01 | `POST /api/booking/{maDon}/bao-gia` | 200 `QuoteResponse` | Tính báo giá và phân bổ giảm giá; không giữ lượt mã. |
| C02 | `POST /api/booking/{maDon}/chot-gia` | 200 `FreezePriceResponse` | Kiểm tra lại báo giá, giữ lượt mã logic và chuyển đơn sang `CHO_THANH_TOAN`. |
| B05 | `GET /api/booking/{maDon}` | 200 `OrderResponse` | Trả `giaDaChot` khi đơn đã chốt; không tính lại snapshot đã lưu. |

Tất cả API yêu cầu tài khoản `KHACH_HANG`, phiên đăng nhập và quyền sở hữu đơn. C01/C02 cần hành khách đầy đủ trong cùng HTTP session với T16. C02 và mọi thao tác ghi dùng CSRF; C02 bắt buộc `Idempotency-Key`.

## Request và response

C01 nhận `{}` hoặc `{"maKhuyenMai": null}` khi không áp mã. Frontend chỉ gửi mã; không gửi giá, tiền giảm, tổng thanh toán hoặc phiên bản giá.

```json
{
  "maKhuyenMai": "KM10"
}
```

C02 nhận mã báo giá từ C01:

```json
{
  "maBaoGia": "BG01"
}
```

`QuoteResponse` gồm `maBaoGia`, `maDon`, `donViTienTe`, `thoiDiemBaoGia`, `thoiDiemHetHan`, `tongTienGoc`, `tongTienGiam`, `tongThanhToan`, `chiTietGia` và `ketQuaKhuyenMai`. `giaTamTinh` của T16 có thể còn số thập phân chính xác; C01/C02 của T17 làm tròn tổng giảm bằng `HALF_UP` và trả các trường tiền dưới dạng chuỗi số nguyên VND.

```json
{
  "maBaoGia": "BG01",
  "maDon": "DDV01",
  "donViTienTe": "VND",
  "thoiDiemBaoGia": "2026-10-13T09:32:00+07:00",
  "thoiDiemHetHan": "2026-10-13T09:45:00+07:00",
  "tongTienGoc": "400000",
  "tongTienGiam": "40000",
  "tongThanhToan": "360000",
  "chiTietGia": [
    {"maGhe": "G11", "giaGoc": "200000", "tienGiam": "20000", "thanhTien": "180000"},
    {"maGhe": "G12", "giaGoc": "200000", "tienGiam": "20000", "thanhTien": "180000"}
  ],
  "ketQuaKhuyenMai": {"hopLe": true, "duocGiam": "40000"}
}
```

`ketQuaKhuyenMai` là `null` khi không chọn mã. C02 trả cùng phân bổ nhưng thêm `trangThaiDon: "CHO_THANH_TOAN"` và không có `thoiDiemBaoGia` hay `phienBanGia`:

```json
{
  "maBaoGia": "BG01",
  "maDon": "DDV01",
  "trangThaiDon": "CHO_THANH_TOAN",
  "donViTienTe": "VND",
  "thoiDiemHetHan": "2026-10-13T09:45:00+07:00",
  "tongTienGoc": "400000",
  "tongTienGiam": "40000",
  "tongThanhToan": "360000",
  "chiTietGia": [
    {"maGhe": "G11", "giaGoc": "200000", "tienGiam": "20000", "thanhTien": "180000"},
    {"maGhe": "G12", "giaGoc": "200000", "tienGiam": "20000", "thanhTien": "180000"}
  ],
  "ketQuaKhuyenMai": {"hopLe": true, "duocGiam": "40000"}
}
```

Trong B05, `giaDaChot` là `null` trước C02 hoặc sau khi đơn đã đóng/hủy/hết hạn; khi còn snapshot đã chốt, giá trị chỉ gồm `donViTienTe`, ba tổng tiền và `chiTietGia`. Snapshot không có mã báo giá, thời điểm báo giá hoặc phiên bản giá.

## Quy tắc tính giá

- Giá gốc mỗi ghế là `TuyenXe.giaCoBan`; T17 chưa hỗ trợ giá khác nhau theo ghế.
- Tính giảm bằng `BigDecimal` theo T14, áp dụng trần và không vượt tổng gốc.
- Làm tròn tổng giảm bằng `HALF_UP` đến 1 VND. Phân bổ theo tỷ lệ giá gốc, lấy phần nguyên trước rồi chia phần dư theo phần lẻ giảm lớn nhất; nếu hòa, `maGhe` tăng dần.
- Tổng chi tiết phải khớp tuyệt đối với tổng đơn. Đơn 0 đồng được chốt; không gửi giao dịch 0 đồng tới cổng ở T17.
- C01 chỉ preview và không tăng `soLuongDaDung`. C02 kiểm tra lại mã sau khi khóa dữ liệu; số đơn còn hạn ở `CHO_THANH_TOAN` được tính như lượt đang giữ. Cạnh tranh lượt cuối chỉ một đơn thành công, bên còn lại nhận `409 PROMOTION_EXHAUSTED`.
- Chốt giá giữ `giu_cho` ở `DANG_GIU`, không tạo `VeXe`, không bán ghế và không gọi cổng thanh toán. T18 sẽ đọc tổng đã chốt từ DB và ghi `VeXe.giaVe` khi phát hành.

## Giới hạn theo class diagram

- Mã khuyến mãi hiện là mã dùng chung; không hỗ trợ phạm vi riêng theo nhà xe hoặc tuyến vì diagram chưa có trường phạm vi tương ứng.
- T17 không thêm cột giá riêng cho từng vé. Database chỉ lưu tổng gốc, tổng giảm và mã áp dụng trên đơn; chi tiết phân bổ được dựng lại từ tổng đã chốt và tập ghế. T18 mới ghi giá thực trả vào `VeXe.giaVe`.
- Hành khách chỉ nằm trong HTTP session. Callback T18 không được giả định session hoặc thông tin hành khách còn tồn tại; phần phát hành phải có cách lấy lại dữ liệu đã được thống nhất ở T18.
- Khi hoàn một phần, các dòng vé gốc và tổng tiền của đơn vẫn phải giữ để tra cứu lịch sử; hoàn tiền không sửa ngược snapshot giá đã chốt.
- `PriceSummary`/`giaDaChot` không có phiên bản hoặc thời điểm báo giá. B05 trả các dòng và tổng của snapshot đang giữ; trước khi chốt, T16 vẫn trả preview hiện tại với số thập phân chính xác.

## Trạng thái, sửa đơn và gửi lại

Đơn phải ở `CHO_XU_LY`, còn hạn và có đủ hành khách để C01 chạy. Khi C02 thành công, đơn chuyển `CHO_THANH_TOAN`. Nếu dữ liệu ghế, hành khách, liên hệ, giá tuyến hoặc quy tắc mã khác với fingerprint của báo giá, trả `409 PRICE_CHANGED`; báo giá mất khỏi session trả `409 QUOTE_EXPIRED`. Hết hạn trả `409 HOLD_EXPIRED` sau khi đã commit việc chuyển đơn/giữ sang trạng thái hết hạn.

Sửa liên hệ, hành khách, ghế, mã hoặc hủy đơn đã chốt chỉ được phép khi chưa có giao dịch thanh toán `CHO_XU_LY` hoặc `THANH_CONG`. Sửa thành công đưa đơn về `CHO_XU_LY`, bỏ giá chốt và giải phóng lượt logic trong cùng transaction; cần báo giá/chốt lại. Lỗi validation hoặc đổi ghế thất bại rollback toàn bộ. Đơn đã thanh toán hoặc đang thanh toán không được sửa giá.

Hủy hoặc hết hạn đơn đã chốt giữ tổng gốc, tổng giảm và mã để tra cứu lịch sử. Lượt mã được giải phóng theo trạng thái/hạn giữ, kể cả khi scheduler chưa quét đơn hết hạn. Các thao tác không gia hạn lượt giữ ghế 15 phút.

Cùng `Idempotency-Key` và nội dung trả lại kết quả lịch sử; đổi nội dung trả `409 IDEMPOTENCY_KEY_REUSED`. Replay không làm sống lại đơn đã sửa, hủy hoặc hết hạn; GET B05 là nguồn trạng thái hiện tại. Mất session làm mất hành khách và báo giá; khách nhập lại dữ liệu. Giá đã chốt trong DB vẫn đọc được cho đến khi đơn đóng.

## Kiểm thử và bàn giao

`tests/backend/booking_pricing_smoke.py` kiểm tra backend thật tại `:8080` và PostgreSQL tại `:5432`, dùng tài khoản và fixture có tiền tố riêng, chỉ dọn dữ liệu do chính lượt chạy tạo. Smoke bao phủ báo giá không mã, mã phần trăm/trần, đơn 0 đồng, phân bổ phần dư, mã hết lượt và hai chuyến tranh lượt cuối, retry idempotency, fingerprint thay đổi, mất session, sửa sau chốt, hết hạn/scheduler, trạng thái thanh toán và GET snapshot bất biến. Kết quả chỉ ghi sau khi script được chạy thực tế.

T17 không sửa class diagram, không cần migration và chưa tích hợp FE. T18 chịu trách nhiệm thanh toán, cập nhật lượt đã dùng, phát hành toàn bộ vé nguyên tử và lưu giá thực trả từng vé; tổng gốc/tổng giảm của đơn và bản ghi vé lịch sử được giữ nguyên khi hoàn một phần.

## Kết quả thực thi ngày 2026-10-13

| Kiểm tra | Kết quả |
| --- | --- |
| Maven package qua Docker, Java 21 | 119 test: 105 đạt, 14 test PostgreSQL chuyên biệt bỏ qua theo cấu hình; không failure/error. |
| Lifecycle tại thời gian cố định, chạy riêng sau khi thêm test đồng thời | 9 test đạt, gồm scheduler và GET cùng chạy tại đúng `expiresAt`. |
| Smoke T17, backend :8080 / PostgreSQL :5432 | 12 nhóm đạt, 116 kiểm tra HTTP; không FAIL hoặc HTTP 500. |
| Hồi quy booking T16 | 11 nhóm đạt, 58 kiểm tra HTTP. |
| Hồi quy khuyến mãi/phương thức thanh toán T14 | 6 nhóm đạt, 101 kiểm tra HTTP. |

Smoke xác nhận giữ lượt cuối an toàn giữa hai đơn khác chuyến, replay không tái chốt, báo giá cũ bị từ chối, mất session, sửa đơn rollback hoặc bỏ giá chốt đúng cách, giá chốt không đổi khi cấu hình thay đổi, và chốt đơn 0 đồng không tạo vé/giao dịch. Kiểm tra phát hành và hoàn một phần sử dụng fixture database, chưa phải tích hợp các API T18 hoặc cổng thanh toán.

Fixture được dọn theo mã/tài khoản riêng của lượt chạy. Ba file FE và class diagram có cùng SHA-256 trước/sau triển khai.
