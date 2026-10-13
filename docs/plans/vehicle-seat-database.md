# Xe, ghế, phân công và database

Phạm vi bàn giao: Xe, Ghế, Phân công và Database. Nhánh `feature/vehicle-seat-database`; PR vào `develop`.

## Phần đã làm

CRUD loại xe/xe/ghế/phân công; tự sinh ghế; quản lý đăng kiểm/trạng thái; kiểm tra xe, bằng lái, vai trò và lịch trùng; Repository/JPA; schema/seed; Docker PostgreSQL.

Sơ đồ: [CLASS_DIAGRAM.drawio](../diagrams/class-diagram/CLASS_DIAGRAM.drawio). Không sửa frontend hoặc logic/test của module chuyến xe.

## Nhóm cần đọc trước khi merge

| Thay đổi dùng chung | Cần làm |
| --- | --- |
| Entities.java thêm dungTichXang, soBangLai, ghiChu phân công | Nạp schema 002 trước khi chạy backend mới; cột mới cho phép NULL |
| States.java thêm TAI_XE_CHINH/TAI_XE_PHU, giữ TAI_XE cũ | Các backend dùng cùng database cần cập nhật enum trước khi đọc phân công mới |
| Docker Compose nạp schema và seed | Tự nạp với volume mới; volume cũ nạp thủ công, không xóa volume |
| Servlet xe/loại xe/phân công | Không đăng ký trùng URL khi tích hợp |

Schema 002 chỉ thêm cột, giữ dữ liệu cũ. API điều kiện xe không tự gán xe vào chuyến. Frontend đang dùng mock; thành viên phụ trách nối API.

## Các lựa chọn cần nhóm chốt

- Ghế G001… chia gần đều theo tầng; 40 giường/2 tầng là tổng 40 chỗ.
- Sức chứa lấy từ loại xe; không đổi loại/nhà xe sau tạo hoặc cấu trúc loại xe đã dùng.
- Chặn sửa/xóa ghế có vé/giữ chỗ hoặc xe có chuyến chưa kết thúc; thêm ghế không vượt sức chứa. Xe thiếu ghế không đạt điều kiện.
- Xe sẵn sàng, đăng kiểm và bằng lái còn hạn hết chuyến; nhân viên đang làm việc, đúng vai trò/nhà xe, không trùng lịch.
- Chỉ thay đổi phân công khi chuyến chưa khởi hành; chưa quy định thời gian nghỉ/số nhân viên tối thiểu.
- Chặn xóa dữ liệu đang được tham chiếu; biển số chuẩn hóa chữ hoa.

Đây là lựa chọn triển khai để nhóm review, chưa phải quy định nhóm đã chốt. Giữ tên cột cũ để tương thích; không sửa khác biệt thuộc module khác.

## API

| Đường dẫn | Phương thức |
| --- | --- |
| /api/loai-xe, /api/xe, /api/phan-cong | GET, POST; /{id}: GET, PUT, DELETE |
| /api/xe/{id}/ghe | GET, POST; /{maGhe}: GET, PUT, DELETE |
| /api/xe/{id}/ghe/sinh | POST, chỉ sinh khi danh sách trống |
| /api/xe/{id}/dieu-kien/{maChuyen} | GET |

Đầu vào: VehicleInputs.java; PUT gửi đủ trường.

## Chạy nhanh

Tại thư mục gốc, cần Java 21+, Maven và Docker Desktop đang chạy:

```powershell
mvn -f src/backend/pom.xml -B verify
docker compose up -d postgres
# Nạp schema/seed nếu volume đã tồn tại, xem ghi chú bên dưới.
docker compose up -d backend
# Nếu backend đã chạy: docker compose restart backend
Invoke-RestMethod http://localhost:8080/api/xe
py -3 tests/backend/vehicle_api_smoke.py
```

Volume cũ: nạp theo thứ tự schema 001 → schema 002 → seed 001 → seed 002; thay đường dẫn bên dưới cho từng file, chỉ tiếp tục khi thành công:

```powershell
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding UTF8 docs/database/schema/002_vehicle_alignment.sql | docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1
```

Seed 001 nâng bộ demo 16 chỗ cũ lên 29 chỗ, giữ mã xe/ghế; chỉ thực hiện khi chưa có vé/giữ chỗ và không có xe ngoài demo dùng loại đó. Sao lưu trước khi nạp vào database đã dùng. Chờ Tomcat khởi động trước khi test; dừng bằng `docker compose stop`.

Frontend: `cd src/frontend`, `npm.cmd run dev`, mở http://localhost:3000. Kiểm tra: `npm.cmd run lint`, `npm.cmd run build`.

Kết quả và cách test PostgreSQL: [demo-test-data.md](../database/demo-test-data.md). Maven mặc định chỉ chạy H2; báo cáo ở `src/backend/target/surefire-reports`.
