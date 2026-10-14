# T21 – Vòng đời chuyến xe

T21 bổ sung thao tác vận hành chuyến, tái sử dụng quy tắc xe/phân công T10–T12, đơn và vé hiện có, cùng luồng hoàn tiền T19. Các trạng thái vé không tự chuyển thành “đã lên xe” khi hoàn tất chuyến.

## API

- `POST /api/quan-ly/chuyen-xe/{maChuyen}/bat-dau` — nhà xe/quản trị bắt đầu chuyến đang mở bán. Xe, đăng kiểm, sơ đồ ghế, phân công và điều kiện vận hành phải hợp lệ.
- `POST /api/quan-ly/chuyen-xe/{maChuyen}/hoan-thanh` — nhà xe/quản trị hoàn tất chuyến đang chạy. Nhân viên dùng `/api/chuyen-xe/{maChuyen}/bat-dau|hoan-thanh`; API `/api/quan-ly/chuyen-xe` dành cho quản trị và nhà xe. Vé chỉ giữ trạng thái lên xe do luồng kiểm vé ghi nhận.
- `POST /api/quan-ly/chuyen-xe/{maChuyen}/huy` với JSON `{"lyDo":"..."}` — nhà xe/quản trị hủy chuyến nháp, mở bán hoặc trạng thái cũ chưa khởi hành. Lý do bắt buộc.
- `GET /api/quan-ly/chuyen-xe/{maChuyen}/vong-doi` — lịch sử, đơn bị ảnh hưởng, yêu cầu hoàn và tình trạng thông báo.
- `POST /api/quan-ly/chuyen-xe/{maChuyen}/xu-ly-hoan` — tiếp tục công việc hoàn/đối soát còn tồn.

Các endpoint cũ `/api/chuyen-xe/{maChuyen}/bat-dau|hoan-thanh` vẫn được giữ để tương thích luồng hiện hành. Endpoint hủy cũ bị từ chối vì thiếu lý do; dùng API quản lý mới.

## Hủy chuyến, hoàn tiền và thông báo

Trạng thái chuyến, giải phóng giữ chỗ, hủy đơn chưa hoàn tất, công việc xử lý, outbox email và lịch sử được ghi trong cùng transaction. Lỗi khi gọi cổng thanh toán không khôi phục chuyến; công việc còn lại được lưu để retry hoặc đối soát. Callback đến sau khi hủy không được phát hành vé.

T19 lưu yêu cầu hoàn theo cấp đơn/giao dịch. Vì vậy hệ thống chỉ tự tạo hoàn khi khoản thu xác định duy nhất, đơn chỉ chứa dữ liệu của chuyến bị hủy, và chính sách hoàn gắn với đơn còn tồn tại. Đơn nhiều chuyến, giao dịch không rõ kết quả, thiếu chính sách hoặc lỗi không xác định được đưa vào đối soát để tránh hoàn nhầm/trùng.

Giá trị hoàn 100% giá vé đã thu là quy tắc đề xuất trong đặc tả T21. Mặc định không tạo khoản hoàn mới; sau khi T01 chốt chính sách, đặt `TRIP_CANCELLATION_REFUND_APPROVED=true` trong `.env` và tạo lại backend. Các khoản hoàn đã được tạo vẫn có thể đối soát. đây không phải cam kết hoàn tiền thành công của VNPay. Nhân viên được phân công có thể bắt đầu/hoàn tất, nhưng không hủy.

## Cơ sở dữ liệu

Áp dụng migration `docs/database/schema/013_trip_lifecycle.sql` sau các migration trước đó. Với volume Docker đã tồn tại, chạy `docker compose run --rm migrate`. Không xóa volume. Bản ghi `trip_lifecycle_history`, `trip_cancellation_work` và `trip_cancellation_notification` là phần schema mới của T21.
