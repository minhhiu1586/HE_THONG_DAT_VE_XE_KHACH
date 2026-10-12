# Kiểm tra backend tuyến/chuyến

## T10 — Quản lý xe và sơ đồ ghế

Hướng dẫn mới: [t10-vscode-guide.md](../../docs/plans/t10-vscode-guide.md), [ma trận T10](../../docs/tasks/T10-test-matrix.md), [API/helper T10](../../docs/api/t10-vehicle-management.md).

```bash
python3 tests/backend/vehicle_management_smoke.py
```

Chạy ở thư mục gốc với WAR T10 vừa build và PostgreSQL phát triển có schema 001/002/003. Có thể đặt `API_BASE_URL`, `DB_NAME`, `DB_CONTAINER`. Script tạo riêng loại xe/xe/ghế/tuyến/chuyến/đơn/vé và kiểm tra quyền bằng tài khoản fixture; tự dọn dữ liệu của lượt chạy. Bộ test xe cũ đã được điều chỉnh để thay ghế bằng toàn sơ đồ, không xóa một ghế làm thiếu sức chứa.

Chạy test nghiệp vụ từ thư mục gốc repository:

```bash
mvn -f src/backend/pom.xml -B verify
```

JUnit dùng H2 để kiểm tra transaction, validation, mapping và quy tắc nghiệp vụ. Kiểm tra HTTP dùng Tomcat 10.1 và PostgreSQL 16 thật, nên cần WAR vừa build và stack Compose đang chạy:

```bash
docker compose up -d backend
# Nếu backend đang chạy với WAR cũ:
docker compose restart backend
# Chờ GET /api/tinh-thanh trả HTTP 200, sau đó:
python3 tests/backend/api_smoke.py
```

Script HTTP chỉ dùng thư viện chuẩn Python và Docker CLI. Nó tạo dữ liệu với tiền tố ngẫu nhiên, chuẩn bị xe tham chiếu bằng SQL, gọi API rồi dọn đúng dữ liệu của lượt chạy trong `finally`. Không xóa bảng hay dữ liệu có sẵn. Nếu volume PostgreSQL có từ trước, áp dụng các schema theo thứ tự trong `src/backend/README.md` trước khi chạy. Có thể đặt `API_BASE_URL` và `DB_CONTAINER` để kiểm tra một stack riêng; mặc định lần lượt là `http://localhost:8080/api` và container `datvexe-postgres`.

| Yêu cầu | Bằng chứng kiểm tra |
| --- | --- |
| 20 entity theo class diagram | JPA tạo schema H2; test kiểm tra đủ 20 entity và quan hệ tiêu biểu; Hibernate validate schema PostgreSQL khi chạy WAR |
| CRUD bến xe, tuyến | Tạo, liệt kê, đọc chi tiết, cập nhật, xóa; lỗi trùng mã/không tồn tại; chặn xóa khi đã được tham chiếu |
| Thêm/xóa/sắp xếp điểm dừng | Chèn giữa danh sách; đảo thứ tự; xóa và đánh lại số; chặn danh sách thiếu, trùng, điểm ngoài tuyến, thứ tự và thời gian dừng không hợp lệ |
| Trạm khởi hành/trạm đến | Thay đổi và đọc lại; chặn hai trạm giống nhau và tham chiếu không tồn tại |
| Tạo chuyến từ tuyến | Dùng giờ mặc định của tuyến; tính giờ đến; chuyến qua nửa đêm; chặn tuyến ngừng khai thác, thiếu ngày/giờ/thời lượng |
| Gán xe | Gán xe có sẵn; chặn xe không tồn tại, bảo trì, khác nhà xe và lịch giao nhau; cho phép giờ bắt đầu bằng giờ kết thúc chuyến trước |
| Cập nhật/hủy chuyến | Đổi ngày/giờ; kiểm tra lại lịch xe; rollback khi đổi lịch thất bại; hủy giữ bản ghi và trạng thái `DA_HUY` |
| Tìm chuyến | Theo mã bến hoặc mã tỉnh/thành và ngày; thứ tự giờ đi; sai chiều, sai ngày, điểm không có dữ liệu trả `200 []` |
| Trạng thái chuyến | Bắt đầu cần xe hợp lệ; hoàn thành sau khi bắt đầu; chặn sửa/gán xe cho chuyến hủy hoặc đã chạy; không đưa chuyến hủy/hoàn tất vào kết quả tìm kiếm |
| Controller + Service + validation | HTTP status 200/201/204/400/404/409; lỗi JSON; dữ liệu bắt buộc, độ dài, giá âm và giới hạn số thập phân; ngày/giờ đầu ra là chuỗi ISO |

Kết quả được xác minh ngày 08/10/2026: 13 test JUnit đạt, 0 thất bại/lỗi/bỏ qua; 5 kịch bản API đạt với 75 phản hồi HTTP được kiểm tra trên Tomcat 10.1.60/PostgreSQL 16.15. Hibernate cũng đã validate mapping đủ 20 entity với schema PostgreSQL mới. Lượt kiểm tra ban đầu phát hiện lỗi cursor PostgreSQL khi liệt kê tuyến; code đã chuyển sang lấy danh sách trước khi ánh xạ response và lượt kiểm tra sau đạt.

Phạm vi tìm kiếm hiện dùng mã bến đầu/cuối hoặc mã tỉnh/thành của bến, không tìm theo tên tự do hay điểm dừng trung gian. Frontend vẫn dùng mock data; test này xác minh API backend. Mapping các entity khác cung cấp nền JPA/schema, còn API/service đặt vé, thanh toán, tài khoản, nhân viên và quản lý xe vẫn do các module phụ trách triển khai. Chưa kiểm tra tải lớn hoặc luồng đặt vé/thanh toán/phân quyền.

## T07 / T07.1

Chạy migration 003 trên database cũ trước khi chạy WAR mới. Các smoke cũ nay tạo ADMIN kiểm thử tạm qua `auth_fixture.py`, gửi cookie/CSRF và dọn tài khoản sau chạy. Chỉ chạy trên database phát triển, nơi người chạy có quyền SQL.

```bash
python3 tests/backend/account_auth_smoke.py
python3 tests/backend/api_smoke.py
python3 tests/backend/vehicle_api_smoke.py
```

Đăng ký/đăng nhập giới hạn 20 lần/phút/IP: khi lặp cả bộ liên tục, chờ hết cửa sổ một phút trước lần kế tiếp. Chạy Maven với `-Dvehicle.pg.url=jdbc:postgresql://localhost:5432/<database_test>` cùng `-Dvehicle.pg.user`/`-Dvehicle.pg.password` để bật các test PostgreSQL. Database này phải riêng, đã nạp schema 001/002/003 và seed 001/002. Không dùng database sản xuất. `PostgresAccessControlTest` kiểm tra cạnh tranh khóa tài khoản và thu hồi phân công.

Kiểm tra trình duyệt: cài Playwright ngoài thư mục ứng dụng hoặc dùng runner sẵn có, rồi chạy:

```bash
NODE_PATH=/duong/dan/node_modules node tests/frontend/account_smoke.cjs
```

Yêu cầu frontend localhost:3000, backend localhost:8080, Chromium `/usr/bin/chromium` (hoặc `CHROMIUM_PATH`), PostgreSQL container phát triển. Có thể thay `FRONTEND_URL`, `API_BASE_URL`, `DB_CONTAINER`. Test chỉ tạo/xóa tài khoản riêng. Xem [ma trận](../../docs/tasks/T07-test-matrix.md) và [hướng dẫn tích hợp](../../docs/tasks/T07.1-integration.md).
