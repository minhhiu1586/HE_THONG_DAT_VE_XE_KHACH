# Email biên nhận và vé sau thanh toán

## API và lưu trữ

- `PATCH /api/booking/{maDon}` nhận thêm `khachDatEmail` (chuỗi email tối đa 254 ký tự); giữ phân quyền chủ đơn, CSRF và Idempotency-Key.
- `GET /api/booking/{maDon}` trả `khachDatEmail` và `emailXacNhan` khi đã có yêu cầu gửi.
- Migration `007_booking_email.sql` thêm cột email và bảng kỹ thuật `email_outbox`. Không reset dữ liệu cũ.
- Chỉ ghi hàng đợi trong transaction đã xác minh thanh toán, chuyển đơn sang `DA_THANH_TOAN` và phát hành đủ vé. ReturnURL, chờ thanh toán, thất bại, hết hạn/đối soát không tạo email thành công.
- Mỗi đơn chỉ có một hàng đợi. Worker claim bằng khóa database và gửi sau commit. Lỗi Brevo không rollback thanh toán/vé.

## Trạng thái email

`PENDING` / `SENDING`: đang chờ gửi; `SENT`: Brevo đã tiếp nhận và trả messageId, không phải xác nhận người nhận đã mở thư. `FAILED`: provider từ chối hoặc hết lượt thử; `UNKNOWN`: timeout/mất kết nối/crash trong lúc gửi, không tự gửi lại để tránh email trùng. HTTP 429 được thử lại tối đa 5 lần. Hàng đợi lưu trong PostgreSQL, tiếp tục khi restart; các trường hợp UNKNOWN cần kiểm tra log giao dịch Brevo trước khi thao tác gửi lại.

## Cấu hình backend

Biến môi trường: `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME`, `EMAIL_NOTIFICATIONS_ENABLED`. Lưu khóa trong `.env` không tracked; không dùng NEXT_PUBLIC. Gửi qua `POST https://api.brevo.com/v3/smtp/email` với HTML và text; template escape nội dung người dùng. Email có thông tin hành trình, nhà xe, biển số, ghế/tầng, hành khách, mã vé, mã giao dịch, giá gốc, giảm giá và tiền đã thanh toán. Đây là biên nhận đặt vé, không tạo hóa đơn VAT.

## Kiểm thử

`ReceiptTemplateTest`, `BrevoClientTest`, `EmailDispatcherTest`, và ca thành công/callback lặp/thu tiền muộn trong `ThanhToanServiceTest`. Preview mẫu sinh tại `apps/backend/target/receipt-email-preview.html`. Test mẫu ghi rõ không phải vé hợp lệ. Không gửi mail thật trong JUnit; Brevo được giả lập bằng HTTP server cục bộ.

## Ticket reference and Brevo idempotency

Migration 008 assigns a unique five-character `maTraCuu` to existing and new tickets. The PostgreSQL sequence does not cycle; an invertible mapping encodes each value in Crockford base32, and a database unique constraint prevents duplicates. `maVe` and QR tokens remain internal identifiers. Customers see `maTraCuu` on the success page and receipt.

Brevo requires a UUID `headers.idempotencyKey`. The client derives a stable UUID from the order ID, reusing the same key for retries. Rejected receipts remain FAILED until a deliberate retry after the cause is fixed.

## Ticket QR

Migration 009 stores base64 PNG attachments alongside the receipt in the durable outbox. QR images are generated locally with ZXing and encode only the existing opaque `maQR` token. The authenticated order response exposes `qrImage` as a PNG data URI for valid tickets; cancelled tickets do not receive an image. Each confirmation email includes `Ve-<maTraCuu>.png` attachments, without publishing tokens through an external image service.

QR generation does not implement boarding validation. A future staff scanning endpoint must resolve the QR token, check the trip, staff scope and current ticket state before recording boarding. The QR token itself does not authorize an API action.
