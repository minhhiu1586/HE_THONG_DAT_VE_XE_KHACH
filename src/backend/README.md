# Backend quản lý tuyến và chuyến xe

Phần này dùng Java 21, Jakarta Servlet 6.0, Hibernate/JPA, PostgreSQL 16 và Tomcat 10.1. `Entities.java` và `persistence.xml` khai báo mapping JPA cho đủ 20 entity trong class diagram đã chốt: `TinhThanh`, `BenXe`, `TuyenXe`, `DiemDung`, `ChuyenXe`, `NhaXe`, `NhanVien`, `PhanCongChuyenXe`, `LoaiXe`, `Xe`, `Ghe`, `GiuCho`, `NguoiDung`, `DanhGia`, `DonDatVe`, `VeXe`, `ThanhToan`, `PhuongThucThanhToan`, `ChinhSachHoanVe` và `KhuyenMai`, cùng các enum trạng thái/loại.

`TuyenXe` giữ hai `BenXe` đầu/cuối riêng; `DiemDung` là danh sách điểm dừng trung gian có thứ tự và **không** có khóa ngoại tới `BenXe`. Các entity xe, nhân viên, tài khoản, đặt vé, thanh toán và khuyến mãi đã có mapping quan hệ/schema làm nền cho các thành viên phụ trách module; API/service CRUD của những module đó chưa nằm trong backend này.

## Chạy trên máy phát triển

Từ `src/backend`, chạy `mvn -B verify` bằng Maven 3.9.9 và Java 21. Từ thư mục gốc repository, chạy `docker compose up -d backend`. Compose tạo PostgreSQL, chạy `docs/database/schema/001_route_trip.sql` **khi volume mới được tạo**, rồi triển khai WAR vào Tomcat 10.1. File schema duy nhất cũng có thể nâng cấp database route-trip cũ; nếu volume đã tồn tại, áp dụng file một lần:

```bash
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/001_route_trip.sql
```

Sau mỗi lần build lại WAR, dùng `docker compose restart backend`. Kiểm tra `GET http://localhost:8080/api/tinh-thanh` (trả `[]` khi chưa có dữ liệu). Các biến `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` cấu hình kết nối DB cho WAR; Compose cung cấp giá trị phát triển. Servlet chỉ cho CORS từ `http://localhost:3000`.

## API

Mọi URL dưới đây có tiền tố `/api`. Tên trường JSON trùng với tên thuộc tính trong request record ở `src/main/java/com/datvexe/dto/Inputs.java`. Ngày dùng `YYYY-MM-DD`, giờ dùng `HH:mm:ss`, enum dùng đúng giá trị trong `States.java`.

| Phương thức | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET, POST | `/tinh-thanh` | Liệt kê, tạo tỉnh/thành để gán bến xe |
| GET, POST | `/ben-xe` | Liệt kê, tạo bến xe |
| GET, PUT, DELETE | `/ben-xe/{id}` | Xem, cập nhật, xóa bến xe |
| GET, POST | `/tuyen-xe` | Liệt kê, tạo tuyến |
| GET, PUT, DELETE | `/tuyen-xe/{id}` | Xem, cập nhật, xóa tuyến |
| PUT | `/tuyen-xe/{id}/tram-dau-cuoi` | Thiết lập hai bến xe đầu/cuối |
| POST | `/tuyen-xe/{id}/diem-dung` | Thêm điểm dừng, `thuTu` tùy chọn |
| DELETE | `/tuyen-xe/{id}/diem-dung/{stopId}` | Xóa điểm dừng và đánh lại thứ tự |
| PUT | `/tuyen-xe/{id}/diem-dung/sap-xep` | Gửi `maDiemDungTheoThuTu`, đủ mỗi ID một lần |
| POST | `/chuyen-xe` | Tạo chuyến từ tuyến; dùng giờ tuyến nếu không truyền giờ |
| GET | `/chuyen-xe?from=...&to=...&date=...` | Tìm theo mã bến xe **hoặc** mã tỉnh/thành và ngày đi |
| GET, PUT | `/chuyen-xe/{id}` | Xem, cập nhật lịch chuyến chưa khởi hành |
| GET | `/chuyen-xe/{id}/trang-thai` | Kiểm tra trạng thái chuyến |
| PUT | `/chuyen-xe/{id}/xe` | Gán xe có sẵn bằng `maXe` |
| POST | `/chuyen-xe/{id}/bat-dau`, `/hoan-thanh`, `/huy` | Chuyển trạng thái chuyến |

Ví dụ tạo tuyến sau khi đã có hai bến `BX1`, `BX2`:

```json
{"maTuyen":"TX1","tenTuyen":"BX1 - BX2","tramKhoiHanh":"BX1","tramDen":"BX2","thoiGianKhoiHanh":"08:00:00","thoiGianDuKien":6.5,"giaCoBan":250000,"trangThai":"DANG_KHAI_THAC"}
```

`thoiGianDuKien` được tính theo **giờ** để suy ra `gioDenDuKien`. Chuyến mới chỉ tạo trên tuyến `DANG_KHAI_THAC`. Chuyến chưa khởi hành được sửa/hủy; bắt đầu cần đã gán xe; chuyến đang chạy mới được hoàn thành. Hệ thống chặn xe không sẵn sàng và các chuyến chưa hoàn tất có lịch sử dụng xe giao nhau. Xóa bến đang dùng bởi tuyến hoặc xóa tuyến đã có chuyến trả `409`. API trả lỗi JSON với khóa `error`.

Không có chuyến phù hợp trả `200` với `[]`. Chuyến hủy vẫn đọc được qua API chi tiết/trạng thái nhưng không nằm trong kết quả tìm kiếm; chuyến hoàn tất cũng được loại khỏi kết quả. Ngày/giờ đầu ra là chuỗi ISO. Hướng dẫn chạy test và bảng đối chiếu yêu cầu nằm ở `tests/backend/README.md` từ gốc repository.

Chưa có đăng nhập, phân quyền, API CRUD đầy đủ cho các module nhà xe/nhân viên/xe/đặt vé/thanh toán hoặc luồng đặt vé trên nhánh này. Các entity và schema là nền dữ liệu, không thay thế phần service/controller của từng module. Không triển khai API này ra Internet trước khi hoàn thiện xác thực và phân quyền.
