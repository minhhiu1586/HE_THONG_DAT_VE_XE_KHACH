# Seed database

| File | Vai trò | Nạp mặc định Docker |
| --- | --- | --- |
| `001_demo.sql` | Dữ liệu nền và mã được test cũ tham chiếu | Có |
| `002_fleet_demo.sql` | Bổ sung đội xe/ghế nền | Có |
| `003_t06_transport.sql` | Dữ liệu vận tải T06 | Có |
| `004_t06_scenarios.sql` | Kịch bản test T06, gồm dữ liệu chưa vận hành | Không |
| `005_t06_display_alignment.sql` | Chuẩn hóa tên/nhãn dữ liệu nền | Có |
| `006_t11_staff.sql` | Nhân viên, xe/ghế và chuyến đi-về phục vụ phân công T11 | Có |

Nạp theo thứ tự Docker; 004 chỉ dùng khi chạy bộ test T06. Với volume đã có, dùng script nạp tương ứng: Docker không tự chạy lại seed khi khởi động.

T11 giữ một seed 006, không sửa/xóa seed của T06. Các mã DEMO cũ là khóa nội bộ còn được FK/test dùng; tên hiển thị và dữ liệu mới dùng tên rõ ràng. Dữ liệu mẫu không phải hồ sơ nhân sự hoặc lịch chạy thực của hãng.

Có thể hợp nhất seed trong một thay đổi riêng: cập nhật Docker, các script/test tham chiếu và kiểm tra cả database mới lẫn database có sẵn. Không reset volume để dọn file.

Quy tắc, phạm vi và kết quả T11: [t11-employees.md](../../api/t11-employees.md).
