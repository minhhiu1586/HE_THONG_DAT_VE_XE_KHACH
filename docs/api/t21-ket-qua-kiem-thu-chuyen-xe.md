# Kết quả rà soát T21

Đối chiếu với đặc tả T21 người dùng cung cấp, ngày 14/10/2026. Giữ giao diện và các luồng cũ; bổ sung vào các màn quản lý, lịch nhân viên và lịch sử đơn hiện có.

## Các lỗi đã sửa

- API hủy đọc đúng DTO `Cancellation`, bắt buộc lý do.
- Bắt đầu chuyến kiểm tra lại xe và phân công; tài xế chỉ thao tác chuyến được giao. Nhân viên không có quyền hủy.
- Hoàn tất không sửa trạng thái vé hoặc tạo lịch sử kiểm vé.
- Hủy giải phóng cả giữ ghế chưa gắn đơn, cập nhật chính xác đơn chưa thanh toán sau khi khởi tạo đối tượng JPA.
- Lưu công việc và thông báo theo cả mã đơn và mã chuyến để không ghi đè các lần hủy khác nhau.
- Đơn chưa thu tiền không tạo yêu cầu hoàn; đơn có nhiều chuyến hoặc giao dịch chưa rõ được giữ để đối soát.
- Khoản đã thu nhưng chưa phát hành vé không bị hoàn trùng khi gửi lại thao tác xử lý.
- Sau timeout, kiểm tra yêu cầu đã lưu thay vì gửi khoản hoàn mới.
- Khóa bản ghi thông báo và kiểm tra trạng thái trước khi gửi để tránh hai worker cùng gửi.
- Theo dõi vé bị ảnh hưởng, số tiền hoàn, giao dịch lỗi và lý do chưa xử lý.
- Mức hoàn 100% chỉ được tạo mới khi `TRIP_CANCELLATION_REFUND_APPROVED=true`; mặc định chờ phê duyệt T01. Yêu cầu hoàn đã lưu vẫn có thể đối soát.

## Kiểm tra tự động

Bộ chạy gồm `TripServiceTest`, `RouteTripServiceTest`, `ThanhToanServiceTest`, `HoanVeServicePostgresTest`: 83 trường hợp. PostgreSQL kiểm thử chạy riêng, không dùng database đang phục vụ ứng dụng. Cổng hoàn tiền được giả lập, không thực hiện giao dịch VNPay thật.

Các trường hợp T21 bổ sung bao gồm: bắt đầu/hoàn tất lặp, xe bảo trì, khách hàng bị từ chối, tài xế được phân công và tài xế thao tác chuyến khác, cấm nhân viên hủy, hoàn tất giữ nguyên vé chưa kiểm, lý do hủy bắt buộc, giữ ghế chưa gắn đơn, hủy đơn chưa thanh toán, hoàn theo giá thực trả, khoản hoàn T19 đang chờ, timeout, hai yêu cầu hủy đồng thời, chính sách chưa phê duyệt và hoàn khoản đã thu chưa có vé đúng một lần.

Đã biên dịch toàn bộ mã nguồn chính và mã kiểm thử bằng trình biên dịch Java 21; chạy JUnit trực tiếp vì Maven trong môi trường gặp lỗi phân giải kho plugin. Lint và TypeScript của frontend được kiểm tra riêng. Đây không phải kết quả `mvn clean verify` hoặc kiểm thử giao diện bằng trình duyệt.

Lưu ý kiểm thử hồi quy T19: tình huống đối soát sau khởi động lại dùng trạng thái cổng `01` (chưa rõ). Trạng thái `05` được mã T19 hiện tại xem là hoàn thành công, nên không thể dùng để kiểm tra trạng thái chờ.

## Phần chưa có bằng chứng nghiệm thu đầy đủ

- Chưa kiểm chứng hoàn tiền trên VNPay sandbox thật và gửi email qua Brevo thật.
- Chưa có kiểm thử chuyên biệt cho cuộc đua giữa API hủy T21 và giữ ghế/callback/kiểm vé T20. Mã hiện tại dùng khóa chuyến và các kiểm tra trạng thái có sẵn; bộ thanh toán đã kiểm tra callback cho đơn bị hủy và callback đồng thời, nhưng không thay thế kiểm thử cuộc đua T21 này.
- Chưa kiểm thử toàn bộ thao tác trên trình duyệt, bao gồm màn theo dõi hoàn tiền và retry thông báo.
- Đơn nhiều chuyến cần đối soát riêng; hiện không có giao diện quyết định hoàn thủ công cho trường hợp này.
- Chính sách T01 và review tích hợp T19/T20 cần được nhóm xác nhận theo đặc tả.

Do đó không coi số lượng kiểm thử đạt là bằng chứng T21 đã nghiệm thu toàn bộ mọi tình huống. Hướng dẫn API và migration: [t21-vong-doi-chuyen-xe.md](t21-vong-doi-chuyen-xe.md).
