# T18 — Thanh toán VNPay sandbox và phát hành vé

Tài liệu này mô tả phạm vi T18 giản lược cho đồ án môn học. OpenAPI riêng nằm tại [payment-vnpay-openapi.json](payment-vnpay-openapi.json); hợp đồng tổng hợp nằm tại [openapi.yaml](openapi.yaml).

## Phạm vi

T18 nhận phương thức FE chọn, tạo giao dịch VNPay sandbox hoặc xử lý đơn 0 đồng bằng `MIEN_PHI`, xác minh kết quả rồi phát hành toàn bộ vé. Backend dùng Servlet → Service → Repository → JPA và tái sử dụng giá đã chốt ở T17.

Giữ nguyên class diagram và các trạng thái hiện có. `VeXe.maQR` đã có trong diagram nên được ánh xạ bằng token opaque duy nhất; không thêm entity thanh toán, bảng snapshot hành khách hoặc trạng thái mới. Hành khách và metadata provider tạm thời lưu trong bộ nhớ theo giao dịch; mất context sau khi restart được trả về rõ ràng, không tự tạo lần thu tiền mới. Chưa tích hợp FE, hoàn tiền, đối soát tự động hoặc kiểm vé.

## API

| ID | Method | Endpoint | Mục đích |
| --- | --- | --- | --- |
| D01 | POST | `/api/booking/{maDon}/thanh-toan` | Tạo lần thanh toán từ giá đã chốt. Body chỉ có `maPhuongThuc`: `VNPAY` hoặc `MIEN_PHI`. |
| D02 | GET | `/api/thanh-toan/{maGiaoDich}` | Đọc trạng thái giao dịch. |
| D03 | GET | `/api/booking/{maDon}/thanh-toan` | Lịch sử giao dịch; phân trang theo `maGiaoDich`. |
| D04 | GET | `/api/thanh-toan/callback/vnpay` | Nhận IPN VNPay, kiểm tra chữ ký và trả ACK. |
| D05 | GET | `/api/ve-xe/{maVe}` | Đọc vé đã phát hành và `qrToken`. |
| D06 | POST | `/api/thanh-toan/{maGiaoDich}/xac-minh` | Gọi QueryDR server-to-server khi chạy localhost. Body `{}`. |
| D07 | GET | `/api/thanh-toan/return/vnpay` | Kiểm tra chữ ký ReturnURL và thông báo cho trình duyệt. |

D01 cần `Idempotency-Key`; thao tác POST D01/D06 cần phiên khách hàng sở hữu đơn và CSRF. D02/D03/D05 dùng quyền sở hữu/phạm vi hiện có. D04 và D07 là hai đường dẫn public hẹp, không dùng cookie của trình duyệt.

Response `PaymentResponse` gồm mã giao dịch/đơn/phương thức, `soTien`, `donViTienTe`, `trangThai`, `ketQuaCongDaXacDinh`, `thoiDiemTao`, `urlThanhToan`, `thoiDiemHetHan`, `thoiGianThanhToan` và `canDoiSoat`. `thoiDiemTao` có thể `null` sau restart; `thoiGianThanhToan` là `null` trước khi thành công. `canDoiSoat` là `true` khi đã xác minh thu tiền nhưng đơn không còn điều kiện phát hành.

D01 yêu cầu phương thức tồn tại, đang hoạt động và đã được tích hợp. T18 hiện hỗ trợ `VNPAY` và `MIEN_PHI`; bật một phương thức khác trong T14 chưa đủ để xử lý thanh toán qua phương thức đó. D01 trả `201` khi tạo giao dịch, kể cả giao dịch miễn phí đã xác nhận ngay; `202` khi trả lại giao dịch đang chờ. Luôn đọc trường `trangThai` để biết kết quả.

Retry cùng key không tạo giao dịch mới; khác nội dung trả `IDEMPOTENCY_KEY_REUSED`. Kết quả replay là lịch sử của request; URL không còn dùng được được trả `null`. D02 là nguồn trạng thái hiện tại. Mất context của giao dịch đang chờ trả `PAYMENT_CONTEXT_MISSING`, không dựng lại URL để thu tiền lần nữa.

Ví dụ D01:

```json
{
  "maPhuongThuc": "VNPAY"
}
```

Frontend không gửi tiền, mã giao dịch provider, trạng thái callback hoặc hành khách trong D01. Backend lấy tổng thanh toán từ `DonDatVe` đã chốt giá.

## Luồng VNPay

Khi D01 nhận `VNPAY`, backend kiểm tra đơn ở `CHO_THANH_TOAN`, còn hạn giữ và có snapshot hành khách trong session. Backend tạo `ThanhToan.CHO_XU_LY`, dùng `maGiaoDich` làm `vnp_TxnRef`, tạo URL ký HMACSHA512 và giữ nguyên hạn ghế ban đầu. Số tiền gửi VNPay là VND nguyên nhân 100; không lấy từ request FE và không gia hạn giữ ghế. VNPay yêu cầu tham số sắp xếp, URL encoding, thời gian GMT+7 và mã tham chiếu duy nhất. Xem [VNPay PAY API](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

VNPay ReturnURL chỉ kiểm tra chữ ký và hiển thị kết quả. Nó không cập nhật `ThanhToan`, không đổi trạng thái đơn và không phát hành vé. IPN thật cần HTTPS public nên không thể gọi trực tiếp vào localhost; smoke test dùng fixture có chữ ký. Khi phát triển trên localhost, gọi D06 để backend dùng QueryDR và tự kiểm tra phản hồi VNPay. `vnp_ResponseCode=00` của QueryDR chỉ cho biết yêu cầu truy vấn được xử lý; backend còn phải kiểm tra trạng thái giao dịch. Xem [VNPay QueryDR](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

IPN và D06 dùng chung hàm xử lý kết quả đã xác minh. Backend kiểm tra chữ ký, merchant, mã giao dịch và số tiền; sau đó khóa theo thứ tự nhà xe → chuyến → đơn → giao dịch → khuyến mãi. Khi thành công và giữ chỗ còn hợp lệ, một transaction duy nhất:

1. chuyển giao dịch sang `THANH_CONG`;
2. chuyển đơn sang `DA_THANH_TOAN` và giữ chỗ sang `DA_XAC_NHAN`;
3. tạo toàn bộ `VeXe.DA_XUAT_VE`, lưu hành khách, giá thực trả và token QR;
4. tăng lượt mã khuyến mãi đúng một lần.

Callback lặp hoặc D06 lặp không tạo thêm vé. Nếu provider trả kết quả chưa rõ, giao dịch vẫn `CHO_XU_LY`. Nếu xác minh thành công sau khi giữ ghế hết hạn hoặc thiếu snapshot hành khách, backend chỉ ghi nhận kết quả tài chính, không phát hành vé và trả `canDoiSoat: true`; việc xử lý thuộc T19 hoặc thao tác thủ công.

`MIEN_PHI` chỉ hợp lệ khi tổng thanh toán bằng `0`. Backend xác nhận và phát hành vé trong transaction nội bộ, không gọi VNPay.

## Cấu hình và giới hạn demo

Đặt cấu hình qua biến môi trường:

```text
VNP_TMN_CODE=<merchant sandbox code>
VNP_HASH_SECRET=<sandbox secret>
VNP_RETURN_URL=http://localhost:8080/api/thanh-toan/return/vnpay
```

Không commit secret. Nếu thiếu cấu hình VNPay, D01 trả lỗi cấu hình rõ ràng; `MIEN_PHI` và các API khác vẫn hoạt động. Context tạm giữ tối đa 24 giờ trong memory; sau restart D02 vẫn đọc được bản ghi DB nhưng không dựng lại URL hoặc phát hành nếu thiếu dữ liệu cần thiết.

## Smoke và nghiệm thu

Chạy sau khi backend và PostgreSQL Docker đã sẵn sàng:

```powershell
python tests/backend/payment_vnpay_smoke.py
```

Script tạo tài khoản và dữ liệu riêng rồi dọn theo đúng phạm vi đó. Các ca có chữ ký cần `VNP_TMN_CODE`/`VNP_HASH_SECRET` trong tiến trình chạy script khớp cấu hình backend. Thiếu điều kiện được ghi `SKIP`, không tính là đạt. Để kiểm thử cục bộ, có thể cấu hình cặp giả `TESTT180` / `t18-local-fixture-only` cho cả backend và script; đây là fixture, không phải tài khoản merchant VNPay.

Giao dịch sandbox thật và QueryDR cần merchant hợp lệ. QueryDR chạy được từ localhost; chỉ IPN thật cần HTTPS public. T18 chưa tích hợp giao diện FE hoặc luồng hoàn tiền/đối soát T19.

## Kết quả thực chạy — 13/10/2026

| Kiểm tra | Kết quả |
| --- | --- |
| `mvn package` | 146 test: 132 đạt, 14 test PostgreSQL có điều kiện được bỏ qua; không lỗi |
| Test mới T18 | 17 service, 7 VNPay client, 2 policy; bao gồm rollback do DB từ chối vé và scheduler/callback đồng thời |
| Smoke T18 trên Docker `:8080` / PostgreSQL `:5432` | 13 nhóm đạt, 86 kiểm tra HTTP; callback có chữ ký cục bộ |
| Hồi quy T16 | 11 nhóm đạt, 58 kiểm tra HTTP |
| Hồi quy T17 | 12 nhóm đạt, 116 kiểm tra HTTP |
| Hồi quy T14 | 6 nhóm đạt, 101 kiểm tra HTTP |
| Migration 006 | Áp dụng và chạy lại thành công; giữ nguyên dữ liệu, không reset volume |
| OpenAPI | JSON/YAML đọc được; 64 ví dụ tổng hợp, 2 ví dụ module và 32 response HTTP thật khớp schema |

Smoke xác nhận phát hành nhiều ghế, tổng tiền vé khớp đơn, dùng một lượt mã, callback lặp/đồng thời, sai chữ ký/số tiền, thất bại rồi thử lại, giá tuyến đổi sau chốt, đơn 0 đồng và thanh toán muộn không phát hành vé. T14 lần chạy đầu gặp giới hạn đăng ký `429` do chạy các bộ hồi quy sát nhau; lần chạy lại riêng đã đạt.

QueryDR được kiểm tra bằng phản hồi HTTP giả lập và service test, gồm timeout và IPN hoàn tất khi QueryDR đang chạy; chưa thực hiện giao dịch hoặc QueryDR thật trên VNPay vì chưa có merchant được cấu hình. IPN thật qua Internet và FE chưa được nghiệm thu. Sau kiểm thử, cấu hình ký giả được gỡ khỏi container.

Runner migration so sánh checksum SQL sau khi chuẩn hóa CRLF/LF và chấp nhận checksum gốc đã ghi; script shell giữ LF theo `.gitattributes`. Không sửa lại checksum lịch sử hoặc SQL các migration 001–005.
