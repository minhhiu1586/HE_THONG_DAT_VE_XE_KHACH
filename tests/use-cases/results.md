# T06 — Kết quả kiểm tra 11/10/2026

| Kiểm tra | Kết quả | Bằng chứng chạy lại |
| --- | --- | --- |
| Nạp seed 003/004/005 và kiểm tra SQL | Đạt; nạp lại không đổi dữ liệu 20 bảng | `tests/backend/load_t06_transport.ps1 -IncludeScenarios` |
| Quan hệ, số lượng, ghế, lịch xe/nhân viên, đơn/vé, giữ chỗ, vé trùng | Đạt | Hai file `t06_*_assertions.sql` |
| API vận tải | 16 kiểm tra đạt | `tests/backend/t06_transport_api.ps1` |
| CRUD loại xe/xe/ghế/phân công và điều kiện | 28 kiểm tra HTTP đạt; tự dọn bản ghi test | `python tests/backend/vehicle_api_smoke.py` |
| Docker Compose | Cấu hình hợp lệ; đã thêm seed vận tải mặc định | `docker compose config --quiet` |
| Backend test và đóng gói WAR | 18 đạt, 2 PostgreSQL test bỏ qua | Maven `-o -f src/backend/pom.xml -B -DforkCount=0 verify` |

Lần Maven mặc định lỗi khám phá test ở tiến trình fork; chạy lại `-DforkCount=0` thành công. Không sửa cấu hình build. Hai PostgreSQL test cũ chưa được chạy, không tính là đạt; kiểm tra PostgreSQL seed trong bảng trên đã chạy trực tiếp.

Ma trận: **82 ca, 20 Pass, 0 Not Run, 62 Blocked**. Chín ca vận tải còn lại đã chạy qua `tests/backend/t06_remaining_api.py`: bảo vệ bến đang dùng, tên bến 160 ký tự, tuyến đầu/cuối trùng, sắp xếp điểm dừng, bảo vệ ghế có vé, xe/nhân viên khác nhà xe, CRUD phân công và hai yêu cầu phân công đồng thời (một 201, một 409). Script tự dọn bản ghi test. Các ca này chưa chứng minh phân quyền. `Blocked` ghi nguyên nhân ở từng ca; chờ API nghiệp vụ hoặc quy tắc được duyệt.

Chưa nghiệm thu giao diện, giao dịch thanh toán thật, kiểm thử đồng thời hoặc toàn bộ luồng UC-01–23. Seed SQL chỉ xác nhận dữ liệu/constraint, không thay thế kiểm thử nghiệp vụ. T06 chưa đủ căn cứ chuyển Done toàn bộ.

Chuẩn hóa giữ nguyên mã DEMO/T06 để tương thích test và FK. Đã nạp schema 001/002 và seed mặc định 001/002/003/005 trên database trống tạm trong container hiện có: kiểm tra vận tải đạt. Sau đó nạp seed 004 và kiểm tra tình huống đạt; database tạm đã xóa. Không tạo container hoặc reset volume. Chưa thử toàn bộ vòng đời Docker trên volume mới.
