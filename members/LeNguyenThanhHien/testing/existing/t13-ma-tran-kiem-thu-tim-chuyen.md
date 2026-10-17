# T13 — Test case tìm chuyến và chỗ còn

## Đối chiếu từng nhóm yêu cầu

| Mục yêu cầu | Triển khai / bằng chứng | Giới hạn kiểm chứng |
| --- | --- | --- |
| 2.1–2.2 Tìm theo tỉnh và xác định từ bến | Kiểm tra tồn tại/cùng tỉnh; truy vấn tỉnh của hai bến tuyến | H2 đã kiểm tra |
| 2.3 API tìm kiếm | Lọc trên backend, trả danh sách phù hợp và thông tin chỗ còn | Array hiện hành; chưa phân trang |
| 2.4 Chi tiết | Địa chỉ bến, điểm dừng theo thứ tự, xe, giá, trạng thái nhận đặt | Code và H2; cần nghiệm thu trình duyệt |
| 2.5 Thời gian chuyến | Lọc ngày khởi hành; giờ/đến lấy từ chuyến, hỗ trợ qua đêm | H2 đã kiểm tra |
| 2.6 Số ghế còn | Ghế hoạt động trừ vé bán và giữ còn hiệu lực bằng NOT EXISTS; không trừ trùng một ghế | H2 kiểm tra giữ/hết hạn, vé bán, bảo trì và độc lập hai chuyến |
| 2.7 Dữ liệu thay đổi | Chi tiết/cập nhật truy vấn mới; lựa chọn cũ bị xóa khi cập nhật | Logic đã rà; đồng thời giữ/mua thuộc module đặt vé |
| 2.8 Điều kiện nhận đặt | Dùng quy tắc T12 tại backend; hết chỗ vẫn xem được, không chọn thêm | H2 đã kiểm tra |
| 2.9 Tìm kiếm UI | Danh mục API, form, danh sách, lọc/sắp xếp, loading và lỗi | Lint/build đạt; chưa chạy browser tự động |
| 2.10 Chi tiết UI | Trang riêng, ghế khả dụng có thể chọn cục bộ | Chưa tạo giữ chỗ; thuộc module tiếp theo |
| 2.11 Rỗng/lỗi | Tách lỗi request với mảng rỗng; đổi query tạo lại state form/kết quả | Code đã rà; cần kiểm tra browser theo guide |
| 2.12 Backend/API | Service/controller, mapping response và validation; giao dịch đọc DB | H2 đạt; PostgreSQL/HTTP cần nghiệm thu riêng |
| 2.13 Tích hợp | Dùng dữ liệu T08/T10 và quy tắc T12; API công khai không yêu cầu login | Guard và service test đạt; chưa nghiệm thu giữ vé end-to-end |

| Trường hợp | Kết quả mong đợi | Cách kiểm tra |
| --- | --- | --- |
| Tỉnh đi/đến hợp lệ, ngày khởi hành hợp lệ | Trả toàn bộ chuyến mở bán phù hợp | H2 `RouteTripServiceTest` |
| Mã tỉnh không tồn tại hoặc hai điểm cùng tỉnh | 400 | H2 `RouteTripServiceTest` |
| Chuyến nháp, hủy, hết hạn nhận đặt | Không xuất hiện như chuyến đặt được | H2 `RouteTripServiceTest`, `TripServiceTest` |
| Chuyến cùng tuyến nhưng khác giờ, qua đêm | Giữ đúng giờ và ngày đến của từng chuyến | H2 `RouteTripServiceTest` |
| Chi tiết không có điểm dừng | Trả `diemDung=[]` | H2 `RouteTripServiceTest` |
| Chuyến không tồn tại | 404 | H2 `RouteTripServiceTest` |
| Một ghế được giữ ở chuyến A | Chỗ còn ở A giảm, chuyến B không đổi | H2 `RouteTripServiceTest` |
| Lượt giữ đã hết hạn | Chỗ còn tăng lại | H2 `RouteTripServiceTest` |
| Frontend nhận danh sách rỗng | Hiện trạng thái không có chuyến, không lỗi | Xem `/tim-chuyen` |
| Frontend nhận lỗi API | Hiện thông báo lỗi, không giữ kết quả cũ | Xem `/tim-chuyen` |
| Khách chưa đăng nhập | Tìm và xem chi tiết công khai | Kiểm tra HTTP |

Kết quả ngày 12/10/2026: frontend `npm run lint` và `npm run build` thành công. Backend chạy 66 test thuộc `RouteTripServiceTest`, `TripServiceTest`, `VehicleTask10Test`, `AccessControlTest`, `OperatorServiceTest`, `HealthPolicyTest`: 0 failure, 0 error. Môi trường chỉ có JRE 21 nên dùng cấu hình compiler Eclipse tạm để biên dịch Java 21; cấu hình tạm đã được xóa, POM dự án giữ nguyên. Các test này dùng H2; chưa chạy kiểm thử HTTP/trình duyệt hoặc tích hợp PostgreSQL cho T13.
