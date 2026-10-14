# T20 — Kiểm vé và xác nhận lên xe

Nhân viên kiểm vé chung đăng nhập tại `/nhan-vien`, mở **Kiểm vé & Lên xe** và chọn chuyến cần kiểm trong hệ thống. Chọn **Mã vé** để nhập mã tra cứu ngắn hoặc mã vé đầy đủ; chọn **QR** để nhập chuỗi QR. Giao diện hiện thông tin xác minh và danh sách hành khách, cập nhật trạng thái sau khi xác nhận lên xe.

## Dữ liệu nhân viên và cách kiểm thử giao diện

Yêu cầu module: [T20 — DATVEXE-28 trên Plane](https://app.plane.so/he_thong_dat_ve_xe_khach/browse/DATVEXE-28/).

Hai nhân viên kiểm vé chung của hệ thống được khởi tạo trong `002_accounts_staff_fleet.sql`, tài khoản và mật khẩu tại [accounts.md](database/seed/accounts.md). Hai hồ sơ có mã `NV_KV01`, `NV_KV02`, không gắn nhà xe hoặc lịch phân công. Vai trò nghiệp vụ dùng `KIEM_VE`; quyền đăng nhập vẫn là `NHAN_VIEN`. Migration `015_system_ticket_inspectors.sql` cho phép chỉ hồ sơ kiểm vé không gắn nhà xe. Migration `016_global_inspector_identities.sql` chuyển mã cũ sang mã chung, giữ thông tin hồ sơ và mật khẩu.

1. Build backend và frontend, khởi động backend mới rồi chạy `python scripts/load_project_data.py` để nạp vào `datvexe` hiện có.
2. Mở `http://localhost:3000/dang-nhap`, dùng `kiemve1@datvexe.test` hoặc `kiemve2@datvexe.test`, mật khẩu khởi tạo `DatVeXe2026!`.
3. Tại `/nhan-vien`, mục **Kiểm vé & Lên xe** tự mở. Chọn chuyến cần kiểm vé; xem hành khách, nhập mã vé hoặc chuỗi QR rồi xác minh. Xác minh chưa làm vé chuyển sang đã lên xe.
4. Với vé đã xuất trên đúng chuyến, bấm xác nhận lên xe; kiểm tra danh sách và nhật ký cập nhật. Vé sai chuyến, đã lên xe, hủy/hoàn hoặc đang hoàn phải bị từ chối.
5. Kiểm tra **Hồ sơ nhân viên**: mã `NV_KV01` hoặc `NV_KV02`, phạm vi **Nhân viên hệ thống**. Admin xem 37 hồ sơ toàn hệ thống, lọc **Kiểm vé** để thấy 2 hồ sơ. Điều hành mỗi hãng vẫn chỉ có 7 nhân viên thuộc hãng mình.

UI hiện nhận chuỗi QR, chưa tích hợp camera. Seed không tạo vé hoặc giao dịch thanh toán thành công sẵn; danh sách hành khách chỉ có khi phát sinh vé qua nghiệp vụ. HTTP smoke tạo và dọn dữ liệu kiểm thử riêng, không gọi cổng thanh toán.

## API kiểm vé

| Method | Đường dẫn | Mục đích |
|---|---|---|
| GET | `/api/kiem-ve/chuyen-xe` | Nhân viên kiểm vé chung lấy các chuyến đang mở bán/đang chạy của nhà xe hoạt động |
| POST | `/api/kiem-ve/xac-minh` | Nhận `maChuyen` và đúng một trong `maVe`, `qrToken` |
| POST | `/api/ve-xe/{maVe}/len-xe` | Nhận `maChuyen`, yêu cầu CSRF và `Idempotency-Key` |
| GET | `/api/chuyen-xe/{maChuyen}/hanh-khach` | Danh sách hành khách và số lượng theo trạng thái |
| GET | `/api/chuyen-xe/{maChuyen}/kiem-ve` | Nhật ký phân trang; query `page`, `size`, `maGhe`, `sort` |

Nhân viên kiểm vé chung đang làm việc được xác nhận lên xe trên chuyến của nhà xe hoạt động. Tài xế/phụ xe vẫn cần phân công đúng chuyến. Nhà xe và Admin được tra cứu trong phạm vi của mình. Vé sai chuyến không trả thông tin hành khách; vé đang hoàn, đã hoàn, đã hủy hoặc đã lên xe không được xác nhận lại. Chuyến đã hủy hoặc hoàn tất không nhận xác nhận mới.

Trạng thái vé, nhật ký và kết quả gửi lặp được lưu trong cùng transaction. Cùng key và nội dung trả kết quả cũ; khác nội dung trả `409 IDEMPOTENCY_KEY_REUSED`. Thời điểm lên xe lấy từ bảng kỹ thuật `nhat_ky_kiem_ve` của migration `012_ticket_checkin.sql`, theo múi giờ Việt Nam. Không thêm bảng nghiệp vụ; quan hệ nhân viên với nhà xe cho phép rỗng riêng với kiểm vé chung.

## Kiểm thử đã thực hiện ngày 14/10/2026

- HTTP T20: 5 nhóm đạt, 33 kiểm tra, không lỗi hoặc bỏ qua. Kiểm mã đầy đủ/mã ngắn/QR, quyền và CSRF, sai chuyến, gửi lặp, trạng thái hủy/hoàn/đang hoàn, nhật ký, danh sách hành khách; kiểm vé chung tra và xác nhận lên xe không có phân công; nhân viên tạm nghỉ bị từ chối.
- Giao diện `/nhan-vien`: đăng nhập cả hai tài khoản kiểm vé chung, mở mục kiểm vé, xem danh sách chuyến toàn hệ thống và hồ sơ đúng `NV_KV01`/`NV_KV02`, phạm vi **Nhân viên hệ thống**. Giao diện dùng nhập mã vé/chuỗi QR của nhóm, không tích hợp camera.
- Backend package đạt: 268 test, 205 thực thi đạt, 63 test có điều kiện không chạy. Frontend production build và TypeScript đạt; lint không lỗi, còn 2 cảnh báo ảnh có sẵn ngoài module T20.
- Docker khởi tạo mới đạt 16 migration, 37 nhân viên, 49 tài khoản; nạp lại giữ nguyên tài khoản. Database dự án kiểm tra đủ quan hệ, không trùng lịch, reload không đổi dữ liệu và 43 lượt API tìm chuyến/sơ đồ ghế đạt. Dữ liệu kiểm thử riêng đã dọn.

Nhà cung cấp VNPay/Brevo thật cần cấu hình và kiểm thử của nhóm; kết quả HTTP nội bộ không chứng nhận giao dịch hoặc email bên ngoài.