# T20 — Kiểm vé và xác nhận lên xe

Nhân viên đăng nhập tại `/nhan-vien`, mở **Kiểm vé & Lên xe** và chọn chuyến được phân công. Chọn **Mã vé** để nhập mã tra cứu ngắn hoặc mã vé đầy đủ; chọn **QR** để nhập chuỗi QR. Giao diện hiện thông tin xác minh và danh sách hành khách, cập nhật trạng thái sau khi xác nhận lên xe.

## API

| Method | Đường dẫn | Mục đích |
|---|---|---|
| POST | `/api/kiem-ve/xac-minh` | Nhận `maChuyen` và đúng một trong `maVe`, `qrToken` |
| POST | `/api/ve-xe/{maVe}/len-xe` | Nhận `maChuyen`, yêu cầu CSRF và `Idempotency-Key` |
| GET | `/api/chuyen-xe/{maChuyen}/hanh-khach` | Danh sách hành khách và số lượng theo trạng thái |
| GET | `/api/chuyen-xe/{maChuyen}/kiem-ve` | Nhật ký phân trang; query `page`, `size`, `maGhe`, `sort` |

Chỉ nhân viên đang làm việc và được phân công chuyến được xác nhận lên xe. Nhà xe và Admin được tra cứu trong phạm vi của mình. Vé sai chuyến không trả thông tin hành khách; vé đang hoàn, đã hoàn, đã hủy hoặc đã lên xe không được xác nhận lại. Chuyến đã hủy hoặc hoàn tất không nhận xác nhận mới.

Trạng thái vé, nhật ký và kết quả gửi lặp được lưu trong cùng transaction. Cùng key và nội dung trả kết quả cũ; khác nội dung trả `409 IDEMPOTENCY_KEY_REUSED`. Thời điểm lên xe lấy từ bảng kỹ thuật `nhat_ky_kiem_ve` của migration `012_ticket_checkin.sql`, theo múi giờ Việt Nam. Giữ nguyên entity và class diagram.

## Kiểm thử đã thực hiện ngày 14/10/2026

- `python tests/backend/ticket_inspection_smoke.py`: **4 nhóm kiểm thử đạt, 26 kiểm tra HTTP, không bỏ qua** trên Docker backend `:8080` và PostgreSQL `:5432`. Bao gồm mã đầy đủ/mã ngắn/QR, quyền và CSRF, vé sai chuyến, gửi lặp, trạng thái không cho lên xe, nhật ký và danh sách hành khách. Script tạo rồi dọn dữ liệu riêng; không gọi cổng thanh toán hoặc gửi email.
- Giao diện `/nhan-vien`: đăng nhập nhân viên mẫu, xem chuyến được giao, tra mã và QR, xác nhận lên xe, kiểm tra danh sách cập nhật và từ chối xác minh lại vé đã lên xe.
- Màn hình 390px: bảng cuộn ngang trong vùng riêng, trang không tràn ngang.
- TypeScript, ESLint các file T20 và build frontend đạt; backend đóng gói WAR thành công. Không chạy lại kiểm thử luồng hoàn tiền trong đợt này theo yêu cầu.
