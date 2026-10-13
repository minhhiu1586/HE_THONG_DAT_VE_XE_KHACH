# Database và JPA mapping

Class diagram đã chốt có 20 entity. JPA mapping nằm trong `src/backend/src/main/java/com/datvexe/domain/Entities.java`; persistence unit đăng ký tường minh trong `src/backend/src/main/resources/META-INF/persistence.xml`. Các enum được định nghĩa trong `States.java` và lưu dưới dạng chuỗi (`EnumType.STRING`).

## Danh sách entity

| Nhóm | Entity |
| --- | --- |
| Tuyến đường | `TinhThanh`, `BenXe`, `TuyenXe`, `DiemDung`, `ChuyenXe` |
| Công ty vận tải | `NhaXe`, `NhanVien`, `PhanCongChuyenXe` |
| Phương tiện | `LoaiXe`, `Xe`, `Ghe`, `GiuCho` |
| Khách hàng | `NguoiDung`, `DanhGia` |
| Đặt vé | `DonDatVe`, `VeXe` |
| Thanh toán | `ThanhToan`, `PhuongThucThanhToan`, `ChinhSachHoanVe`, `KhuyenMai` |

## Quan hệ chính

- `TinhThanh` 1–n `BenXe`.
- `BenXe` được tham chiếu riêng ở vai trò bến khởi hành và bến đến của `TuyenXe`; một tuyến có nhiều `DiemDung` theo `thuTu` và có nhiều `ChuyenXe`.
- `NhaXe` 1–n `NhanVien`, `Xe` và `TuyenXe`; `NhanVien` có các bản ghi `PhanCongChuyenXe`, mỗi bản ghi thuộc một chuyến và một nhân viên, kèm `VaiTroChuyen`.
- `LoaiXe` 1–n `Xe`; `Xe` 1–n `Ghe`. `ChuyenXe` có thể gán một `Xe` và có nhiều `GiuCho`, `VeXe`, `DanhGia` và `PhanCongChuyenXe`.
- `GiuCho` gắn với một `ChuyenXe`, một `Ghe` và có thể gắn với `DonDatVe` sau khi xác nhận.
- `NguoiDung` 1–n `DonDatVe` và `DanhGia`; `DonDatVe` chứa các `VeXe`, có thể áp dụng một `KhuyenMai` và một `ChinhSachHoanVe`, và có nhiều `ThanhToan`.
- Mỗi `VeXe` thuộc một đơn, một chuyến và một ghế. Ràng buộc unique trên `(ma_ghe, ma_chuyen)` ngăn bán trùng ghế trong cùng chuyến.
- `PhuongThucThanhToan` 1–n `ThanhToan`.

Các quan hệ ngược dạng `List<>` được khai báo trong entity cha; khóa ngoại nằm ở entity con. `TuyenXe` giữ hai quan hệ `BenXe` riêng cho điểm đầu/cuối; `DiemDung` là điểm dừng trung gian độc lập, không tham chiếu bến xe theo class diagram.

## Schema và migration

- `schema/001_route_trip.sql` là file duy nhất, tạo schema đủ 20 entity khi database mới; đồng thời bổ sung các bảng/cột thiếu cho database đã dùng schema route-trip cũ. Các câu lệnh lặp lại an toàn với `IF NOT EXISTS`.
- Docker Compose mount và chạy file này khi khởi tạo volume PostgreSQL mới.
- Nếu volume đã tồn tại, từ thư mục gốc repository áp dụng file một lần:

```bash
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/001_route_trip.sql
```

Hibernate dùng `hibernate.hbm2ddl.auto=validate`: nó kiểm tra schema khớp mapping khi khởi động, không tự tạo hoặc sửa bảng. Các module khác có thể dùng entity và quan hệ JPA làm nền; controller/service nghiệp vụ của những module đó vẫn do thành viên phụ trách triển khai.
