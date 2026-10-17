# Database — Thiết kế, migration và dữ liệu mẫu

Các mục dưới đây được gộp từ tài liệu hiện có; kết quả kiểm thử lịch sử giữ nguyên thời điểm và giới hạn của lần chạy.

## Mục lục

- [Database và JPA mapping](#section-1)
- [Dữ liệu khởi tạo dự án](#section-2)
- [Dữ liệu demo và kết quả test](#section-3)
- [T06 — Dữ liệu dự án và kiểm thử](#section-4)
- [Chuẩn hóa tỉnh và tìm kiếm chuyến](#section-5)

<a id="section-1"></a>

## Database và JPA mapping

Database mặc định chạy dự án là `datvexe`. Dữ liệu, tài khoản và cách nạp: [hướng dẫn nạp dữ liệu](#section-2). Seed cũ nằm trong `tests/backend/fixtures/sql`, chỉ phục vụ test hồi quy. Không xóa database cũ hoặc volume để nạp bộ mới.

Class diagram đã chốt có 20 entity. JPA mapping nằm trong `shared/backend/src/main/java/com/datvexe/domain/Entities.java`; persistence unit đăng ký tường minh trong `apps/backend/src/main/resources/META-INF/persistence.xml`. Các enum được định nghĩa trong `States.java` và lưu dưới dạng chuỗi (`EnumType.STRING`).

### Danh sách entity

| Nhóm | Entity |
| --- | --- |
| Tuyến đường | `TinhThanh`, `BenXe`, `TuyenXe`, `DiemDung`, `ChuyenXe` |
| Công ty vận tải | `NhaXe`, `NhanVien`, `PhanCongChuyenXe` |
| Phương tiện | `LoaiXe`, `Xe`, `Ghe`, `GiuCho` |
| Khách hàng | `NguoiDung`, `DanhGia` |
| Đặt vé | `DonDatVe`, `VeXe` |
| Thanh toán | `ThanhToan`, `PhuongThucThanhToan`, `ChinhSachHoanVe`, `KhuyenMai` |

### Quan hệ chính

- `TinhThanh` 1–n `BenXe`.
- `BenXe` được tham chiếu riêng ở vai trò bến khởi hành và bến đến của `TuyenXe`; một tuyến có nhiều `DiemDung` theo `thuTu` và có nhiều `ChuyenXe`.
- `NhaXe` 1–n `NhanVien`, `Xe` và `TuyenXe`; `NhanVien` có các bản ghi `PhanCongChuyenXe`, mỗi bản ghi thuộc một chuyến và một nhân viên, kèm `VaiTroChuyen`.
- `LoaiXe` 1–n `Xe`; `Xe` 1–n `Ghe`. `ChuyenXe` có thể gán một `Xe` và có nhiều `GiuCho`, `VeXe`, `DanhGia` và `PhanCongChuyenXe`.
- `GiuCho` gắn với một `ChuyenXe`, một `Ghe` và có thể gắn với `DonDatVe` sau khi xác nhận.
- `NguoiDung` 1–n `DonDatVe` và `DanhGia`; `DonDatVe` chứa các `VeXe`, có thể áp dụng một `KhuyenMai` và một `ChinhSachHoanVe`, và có nhiều `ThanhToan`.
- Mỗi `VeXe` thuộc một đơn, một chuyến và một ghế. Ràng buộc unique trên `(ma_ghe, ma_chuyen)` ngăn bán trùng ghế trong cùng chuyến.
- `PhuongThucThanhToan` 1–n `ThanhToan`.

`NguoiDung` có phạm vi nhà xe tùy chọn, liên kết nhân viên tùy chọn duy nhất; `AuthSession` thuộc một người dùng, chỉ lưu hash token và thời điểm hết hạn. Xem [T07](../docs/api/tai-khoan-va-xac-thuc.md) để nâng cấp và khởi tạo quản trị viên.

Các quan hệ ngược dạng `List<>` được khai báo trong entity cha; khóa ngoại nằm ở entity con. `TuyenXe` giữ hai quan hệ `BenXe` riêng cho điểm đầu/cuối; `DiemDung` là điểm dừng trung gian độc lập, không tham chiếu bến xe theo class diagram.

### Schema và migration

- `migrations/001_route_trip.sql` tạo schema 20 entity nghiệp vụ khi database mới; đồng thời bổ sung các bảng/cột thiếu cho database đã dùng schema route-trip cũ. Các câu lệnh lặp lại an toàn với `IF NOT EXISTS`.
- `migrations/002_vehicle_alignment.sql` bổ sung cấu trúc xe/phân công; `migrations/003_account_auth.sql` thêm liên kết phạm vi của NguoiDung và bảng `phien_dang_nhap` (entity thứ 21).
- Docker Compose chạy migration 001 → 018 rồi seed khi tạo volume mới; script cuối ghi lịch sử migration. Với volume cũ, loader hoặc `docker compose run --rm migrate` dùng `schema_migrations` và checksum để chỉ áp dụng phiên bản còn thiếu, dừng nếu file cũ bị sửa. Không xóa volume để chạy lại init scripts. Seed tự nạp khi tạo volume mới; dùng `python scripts/load_project_data.py` để bổ sung dữ liệu vào volume hiện có.
- Nếu volume đã tồn tại, từ thư mục gốc repository áp dụng file một lần:

```bash
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/001_route_trip.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/002_vehicle_alignment.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/003_account_auth.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/004_booking_order.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/005_booking_session.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/006_payment_vnpay.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/007_booking_email.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/008_ticket_public_code.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/009_ticket_qr_email.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/010_refund_policy.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/011_refund_tracking.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/012_ticket_checkin.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/013_trip_lifecycle.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/014_trip_reviews.sql
```

Hibernate dùng `hibernate.hbm2ddl.auto=validate`: nó kiểm tra schema khớp mapping khi khởi động, không tự tạo hoặc sửa bảng. Các module khác có thể dùng entity và quan hệ JPA làm nền; controller/service nghiệp vụ của những module đó vẫn do thành viên phụ trách triển khai.

<a id="section-2"></a>

## Dữ liệu khởi tạo dự án

| File | Nội dung |
| --- | --- |
| `001_catalog.sql` | 5 nhà xe, 7 tỉnh/thành, 10 bến, loại xe 22/29/40, danh mục VNPAY |
| `002_accounts_staff_fleet.sql` | 35 nhân viên, 47 tài khoản, 10 xe và 320 ghế |
| `003_routes_trips.sql` | 20 tuyến, 40 điểm đón/trả, lịch 14 ngày và 490 phân công |

Database chạy dự án: **datvexe**. Database cũ không bị xóa. Seed cũ chuyển sang `tests/backend/fixtures/sql` để giữ hợp đồng test; Docker không nạp chúng vào database dự án.

Thống nhất `POSTGRES_DB=datvexe` và URL JDBC kết thúc bằng `/datvexe`. Trên máy đã chuyển bộ dữ liệu mới về tên này, dữ liệu cũ được giữ trong database lưu trữ; bản sao lưu nằm tại `apps/backend/target/database-backups/` và không đưa lên Git. Thành viên khác dùng loader để nạp vào `datvexe` của mình, không cần đổi tên hoặc xóa database/volume.

```powershell
docker compose up -d postgres
python scripts/load_project_data.py --activate
python tests/backend/project_data_check.py --check-reload
```

Loader kiểm tra lịch sử/checksum migration rồi nạp phần còn thiếu và seed trong transaction, giữ bản ghi đã có, cấu hình `.env` khi dùng `--activate` và mở bán bằng API T12. Docker mới tạo chuyến nháp; vẫn cần chạy loader để kiểm tra/mở bán. Chạy lại bổ sung lịch 14 ngày tiếp theo theo mã ngày, không dời chuyến hoặc ghi đè mật khẩu/hồ sơ.

### Tài khoản đăng nhập

Danh sách đầy đủ tên, email và mật khẩu khởi tạo của 47 tài khoản: [tai-khoan-mau.md](tai-khoan-mau.md).

Mật khẩu khởi tạo: **DatVeXe2026!**. Database lưu hash PBKDF2 đúng backend. Tài khoản dùng ổn định, không cần tạo file tài khoản tạm hoặc cleanup sau sử dụng. Nếu đã đổi mật khẩu admin, đặt `PROJECT_ADMIN_PASSWORD` khi chạy loader.

| Vai trò | Email |
| --- | --- |
| 2 admin | `admin1@datvexe.test`, `admin2@datvexe.test` |
| 5 quản lý | `dieuhanh.futa@datvexe.test`, `dieuhanh.tb@datvexe.test`, `dieuhanh.kumho@datvexe.test`, `dieuhanh.hl@datvexe.test`, `dieuhanh.ct@datvexe.test` |
| 20 tài xế | `tx1.futa@datvexe.test` … `tx4.futa@datvexe.test`; tương tự tb/kumho/hl/ct |
| 10 phụ xe | `px1.futa@datvexe.test`, `px2.futa@datvexe.test`; tương tự tb/kumho/hl/ct |
| 10 khách | `khach1@datvexe.test` … `khach10@datvexe.test` |

Điều hành có quyền NHA_XE và liên kết hồ sơ đúng nhà xe. Tài xế/phụ xe chỉ đọc hồ sơ/lịch riêng. Admin và khách không có hồ sơ nhân viên.

### Lịch và khả năng sử dụng

Mỗi nhà xe có 1 xe tuyến ngắn đi-về trong ngày (1 tài xế + 1 phụ xe), 1 xe tuyến dài đổi chiều mỗi ngày (2 tài xế + 1 phụ xe). Tài xế 02 dự phòng. Lịch/giá và hồ sơ nhân sự là dữ liệu nội bộ đồ án, không xác nhận lịch thực của hãng. Tên/hotline hãng tham khảo thông tin nhóm gửi; không tạo mã số thuế/giấy phép/đánh giá chưa kiểm chứng.

Chỉ mở bán sau kiểm tra nhà xe, bến, xe/ghế, đăng kiểm, lịch và nhân sự. Dấu kế hoạch của dữ liệu khởi tạo không chứng nhận giờ lái thực tế; điều phối cần rà soát trước vận hành.

Code sau merge có API giữ ghế và đơn nháp tại `/api/booking/...`, xem `docs/api/giu-cho-va-don-dat-ve.md`. Checkout cần dùng đúng hợp đồng này. Danh mục VNPAY đã có; giao dịch và vé phát sinh từ nghiệp vụ, không khởi tạo sẵn thanh toán thành công.

### Ghi chú riêng database: đã có và còn thiếu

Dữ liệu nền đã có đủ cho hồ sơ/lịch/phân công T11 và đọc tuyến/chuyến/ghế: 5 nhà xe, 35 nhân viên, 47 tài khoản, 10 xe, 320 ghế, 20 tuyến, 210 chuyến mở bán và 490 phân công trong đợt khởi tạo. Nạp lại theo ngày sẽ bổ sung chuyến mới; tổng lịch tích lũy có thể tăng.

Các bảng sau hiện chưa có bản ghi khởi tạo; chưa thể gọi toàn bộ dữ liệu của mọi nghiệp vụ là hoàn tất:

| Nhóm dữ liệu | Hiện có | Phần cần bổ sung |
| --- | --- | --- |
| Khuyến mãi | 0 | Giá trị, thời hạn và điều kiện áp dụng được duyệt |
| Chính sách hoàn vé | 0 | Mốc thời gian, tỷ lệ hoàn và phạm vi áp dụng được duyệt |
| Giữ chỗ | 0 | Sinh từ luồng giữ ghế với thời hạn thực của hệ thống |
| Đơn đặt vé / vé xe | 0 / 0 | Sinh từ luồng đặt vé, liên kết khách/chuyến/ghế đúng ràng buộc |
| Thanh toán | 0 | Sinh từ giao dịch, không ghi sẵn kết quả thanh toán thành công |
| Đánh giá | 0 | Gắn khách và chuyến đủ điều kiện đánh giá |

Danh mục phương thức thanh toán VNPAY đã có. Các nhóm trên không cản kiểm tra T11; bổ sung theo nghiệp vụ đã duyệt, không tự đặt chính sách hoặc tạo lịch sử giao dịch giả.

### Kết quả kiểm tra 13/10/2026

- Đúng số lượng và FK; không trùng lịch xe/nhân viên; nạp lại không đổi dữ liệu đã có.
- 210 chuyến mở bán qua T12; 47 tài khoản đăng nhập đúng quyền, 174 lượt API đạt.
- 43 lượt API tìm chuyến/ghế qua proxy frontend đạt; 52 kiểm tra hồi quy T11 đạt.
- Backend: 90 test đạt, 14 test tùy chọn bỏ qua; frontend build production đạt. Không tính luồng tạo đơn/thanh toán chưa có API là đã đạt.

<a id="section-3"></a>

> Tài liệu kiểm thử lịch sử: các tình huống T06 và seed bổ sung bên dưới phục vụ hồi quy. Để chạy ứng dụng hiện tại, dùng hướng dẫn nạp dữ liệu ở [mục 2](#section-2) và 3 file trong `database/seed/`.

## Dữ liệu demo và kết quả test

Dữ liệu mô phỏng cho Xe/Ghế/Phân công. Địa danh dùng tên thực tế; thông tin doanh nghiệp, nhân viên, liên hệ, biển số, bằng lái, giá vé và lịch chạy là giả lập.

### Dữ liệu trên database mới

| Nhà xe | Loại xe | Xe | Ghế/giường | Nhân viên | Tuyến | Chuyến | Phân công |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2 | 3: 22/29/40 chỗ | 10 | 309 | 17 | 4 | 46 | 87 |

Có xe sẵn sàng/bảo trì/hết đăng kiểm; tài xế hợp lệ/hết bằng/tạm nghỉ; tài xế chính/phụ và phụ xe; chuyến trùng giờ để test từ chối. Sáu xe bổ sung có lịch 7 ngày, cùng nhà xe và không trùng lịch nhân viên.

Nạp: schema 001 → schema 002 → seed 001 → seed 002. Seed 001 chuyển riêng bộ demo 16 chỗ cũ sang 29 chỗ và bổ sung ghế; chặn nếu có vé/giữ chỗ hoặc xe ngoài demo dùng loại đó. Các bản ghi khác không bị ghi đè, lịch không tự dời ngày.

### Kết quả kiểm tra ngày 09/10/2026

| Kiểm tra | Kết quả |
| --- | --- |
| Maven/H2/PostgreSQL 16 | 20 test đạt, không bỏ qua; build WAR thành công |
| HTTP trên Tomcat 10.1, WAR và seed mới | 28 kiểm tra đạt: CRUD loại xe/xe/ghế/phân công và điều kiện xe |
| Hai yêu cầu phân công cùng lúc | Chỉ một yêu cầu thành công, yêu cầu trùng bị từ chối |
| Nạp lại seed | Không tạo trùng; số lượng giữ nguyên |
| Ghế từng xe; nhà xe của phân công | Không có bản ghi sai |
| Dọn dữ liệu test | Không còn bản ghi HTTP_/TEST_ của lượt kiểm tra |

Maven kiểm tra trên database test riêng; HTTP kiểm tra backend tại cổng 8080 với bộ seed trên. Test đồng thời bao phủ một tình huống phân công, chưa bao phủ tải toàn hệ thống.

### Chạy lại

HTTP trên backend đã có seed: `py -3 tests/backend/vehicle_api_smoke.py`. Đổi địa chỉ bằng biến môi trường `VEHICLE_API_URL` nếu cần.

PostgreSQL: tạo database test riêng và nạp đủ schema/seed trước khi chạy. Thay các giá trị `<...>` bằng kết nối của database đó; không dùng database đang sử dụng chung:

```powershell
mvn -f apps/backend/pom.xml -B "-Duser.timezone=Asia/Ho_Chi_Minh" "-Dvehicle.pg.url=jdbc:postgresql://<host>:<port>/<database_test>" "-Dvehicle.pg.user=<user>" "-Dvehicle.pg.password=<password>" verify
```

Maven không có `vehicle.pg.url` sẽ bỏ qua 2 test PostgreSQL. Dùng múi giờ trên để tránh lỗi tên cũ `Asia/Saigon` ở một số image PostgreSQL.

Test PostgreSQL tạo/xóa phân công `TEST_OK`, `TEST_CONCURRENT_1/2`. Script HTTP tạo mã `HTTP_...` riêng cho từng lượt và tự dọn dữ liệu; nếu dọn thất bại sẽ in mã cần kiểm tra. Báo cáo Maven: `apps/backend/target/surefire-reports`.

Phần Xe/Ghế/Phân công đã có dữ liệu và test các luồng chính. Frontend đang dùng mock; đặt vé/thanh toán và kết nối giao diện thuộc module của thành viên phụ trách. Cách chạy và điểm nhóm cần review: [thiet-ke-du-lieu-xe-va-ghe.md](../docs/plans/thiet-ke-du-lieu-xe-va-ghe.md).

<a id="section-4"></a>

> Tài liệu kiểm thử lịch sử: các tình huống T06 và seed bổ sung bên dưới phục vụ hồi quy. Để chạy ứng dụng hiện tại, dùng hướng dẫn nạp dữ liệu ở [mục 2](#section-2) và 3 file trong `database/seed/`.

## T06 — Dữ liệu dự án và kiểm thử

Theo schema/Class Diagram hiện tại; không sửa API, frontend hoặc schema. Dữ liệu mẫu phục vụ kiểm thử, tham khảo địa điểm thực tế. Nhà xe, biển số, nhân viên, giá và lịch chạy là dữ liệu test, không đại diện hoạt động của doanh nghiệp. Dùng lại tỉnh/thành và loại xe demo hiện có.

| Nhóm | Dữ liệu thêm |
| --- | --- |
| Nhà xe / nhân viên | 6 nhà xe tổng cộng (4 mã T06 + 2 mã nền); T06 có 96 nhân viên, 2.796 phân công cùng nhà xe |
| Địa điểm | 2 tỉnh/thành mới, 7 bến; sử dụng 6 tỉnh/thành tổng cộng |
| Tuyến | 16 tuyến hai chiều: HCM–Đà Lạt/Cần Thơ/Nha Trang/Đà Nẵng/Hà Nội và Đà Nẵng–Hà Nội; 24 điểm dừng |
| Xe / ghế | 50 xe, 1.507 ghế; mỗi tuyến có xe 22/29/40 chỗ; thêm xe bảo trì/hết đăng kiểm |
| Chuyến | 1.401 chuyến: lịch 31 ngày, giờ 08:00/14:00/22:00; chuyến hoàn tất/hủy/chưa gán xe |
| Nghiệp vụ mẫu | 25 tài khoản khóa, 66 đơn, 119 vé, 62 giao dịch, 4 giữ chỗ, 6 khuyến mãi, 3 chính sách tạm ngừng, 1 phương thức thanh toán, 1 đánh giá |

Xe 22 chỗ chia tầng 11/11, xe 29 chỗ một tầng, xe 40 chỗ chia 20/20. Ghế cuối T06_X_1 bảo trì, ghế cuối T06_X_2 ngừng sử dụng. Các ghế khác hoạt động. Tuyến dài 36 giờ chạy cách ngày để tránh trùng lịch xe/nhân viên.

### Nạp và kiểm tra

Nhà xe: Phương Trang, Thành Bưởi, Mai Linh Express, Kumho Samco, Hoàng Long, Hải Sơn. Dùng tên thương hiệu để trình diễn; không khẳng định xe/lịch/giá là dữ liệu hoạt động thật của hãng. Hoàng Long và Hải Sơn đã có xe, ghế, tuyến hai chiều, chuyến và phân công.

Docker PostgreSQL cần chạy và đã có schema 001/002, seed 001/002. Tại thư mục gốc:

```powershell
powershell -ExecutionPolicy Bypass -File tests/backend/load_t06_transport.ps1
# Thêm dữ liệu tình huống cho các module còn lại:
powershell -ExecutionPolicy Bypass -File tests/backend/load_t06_transport.ps1 -IncludeScenarios
# Backend cần chạy cho kiểm tra API:
powershell -ExecutionPolicy Bypass -File tests/backend/t06_transport_api.ps1
```

Script nạp seed nền 001/002 rồi thử nạp/nạp lại trong transaction rồi rollback; chỉ commit sau khi kiểm tra đạt. Không tạo container mới, không reset volume. Seed 005 chuẩn hóa trường hiển thị của các mã mẫu DEMO/T06 chỉ định; giữ nguyên ID, lịch, tiền và trạng thái nghiệp vụ. Docker nạp 001 → 002 → 003 → 005 trên database mới; 004 chỉ nạp với -IncludeScenarios. Ngày lấy tại lần nạp đầu, chạy lại không dời lịch; khi hết lịch cần bổ sung đợt mới.

### Mã dùng cho frontend

| API hiện có | Dữ liệu cần đọc |
| --- | --- |
| `/api/ben-xe` | `maBenXe`, `tenBenXe`, `maTinhThanh`; lọc mã T06_ |
| `/api/tuyen-xe` | T06_R_1…16; điểm đầu/cuối là mã bến, ghép tên từ danh sách bến |
| `/api/chuyen-xe/T06_C_1_1` | Chuyến ngày; lấy `maXe=T06_X_1` và ngày khởi hành |
| `/api/chuyen-xe?from=T06_BX_HCM_E&to=T06_BX_LD&date=YYYY-MM-DD` | Thay ngày bằng ngày thuộc lịch seed |
| `/api/xe/T06_X_1/ghe` | 22 ghế; T06_X_2: 29 ghế; T06_X_3: 40 ghế |
| `/api/chuyen-xe/T06_C_3_1` | Chuyến 22:00, đến ngày hôm sau |

Nhãn ghế: xe 22 chỗ P01–P22, xe 29 chỗ 01–29, xe 40 chỗ A01–A20/B01–B20. Mã ghế giữ nguyên; frontend đọc `viTri` và `tang` từ API từng xe. Schema không có tọa độ hàng/cột; cách bố trí này là quy ước dữ liệu test, không khẳng định sơ đồ của hãng cụ thể. Trạng thái vật lý không phải trống/giữ/đặt theo chuyến; trạng thái đặt/giữ cần API nghiệp vụ.

Seed 004 có đơn một/nhiều vé, vé đã hoàn/hoàn một phần, thanh toán thành công/thất bại/đang xử lý, giữ chỗ hết hạn/hủy và khuyến mãi còn/hết lượt. Đây là tình huống test theo schema hiện có: không thêm enum hoặc bảng. Giá trị khuyến mãi và chính sách mẫu chưa phải quy định chính thức; chính sách để tạm ngừng. Tài khoản khóa, mật khẩu đánh dấu không dùng đăng nhập; giao dịch mô phỏng không dùng thanh toán thật. Giữ chỗ/khuyến mãi có mốc thời gian cố định khi nạp đầu; kiểm tra biên cần đồng hồ test phù hợp, không tự dời mốc khi nạp lại.

### Kết quả 11/10/2026

Đã nạp vào database dự án; lịch mới 11/10–10/11/2026. SQL và nạp lại 20 bảng đạt; API đạt 16 kiểm tra đọc. Backend build đạt, 18 test đạt và 2 test PostgreSQL cũ bỏ qua. [Ma trận và giới hạn nghiệm thu](../tests/use-cases/README.md): 82 ca, 20 Pass, 0 Not Run, 62 Blocked; chưa nghiệm thu toàn bộ T06/giao diện.

### File bàn giao

Seed nền `001_demo.sql` và `002_fleet_demo.sql` giữ mã DEMO để test/FK tương thích, nhưng tên hiển thị đã chuẩn hóa. Tên file/mã là nội bộ. Docker nạp dữ liệu vận tải mặc định; `004_t06_scenarios.sql` là bộ tình huống test riêng. Không xóa bản ghi hoặc reset volume để dọn dự án.

Giữ riêng theo trách nhiệm: 003 tạo dữ liệu vận tải; 004 tạo tình huống nghiệp vụ; 005 cập nhật dữ liệu T06 đã nạp. Hai SQL assertion kiểm tra hai seed tương ứng; script nạp điều phối thứ tự 001 → 002 → 003 → 004 (tùy chọn) → 005 → kiểm tra. API test chỉ đọc HTTP. Ma trận là kế hoạch kiểm thử; kết quả chạy lưu riêng trong `tests/use-cases/ket-qua-kiem-thu.md`.

Khi cập nhật: giữ nguyên ID đang được vé/đơn tham chiếu; sửa assertion khi thay đổi số lượng hoặc quy ước dữ liệu; chạy script nạp và API test trước khi commit. Không thay đổi schema, mã tham chiếu hoặc logic backend/frontend.

- Dữ liệu: `tests/backend/fixtures/sql/003_transport.sql`, `004_t06_scenarios.sql`, `005_t06_display_alignment.sql`.
- Hướng dẫn: file này.
- Nạp/kiểm tra: `tests/backend/load_t06_transport.ps1`, `t06_transport_assertions.sql`, `t06_scenario_assertions.sql`, `t06_transport_api.ps1`.
- Kiểm tra chín ca vận tải còn lại: `python tests/backend/t06_remaining_api.py`; tạo mã test riêng và tự dọn. Database trống đã được kiểm tra schema/seed trong container hiện có; xem kết quả ở ma trận.
- Ma trận: `tests/use-cases/README.md`, `test-cases.json`, `check_matrix.py`, `ket-qua-kiem-thu.md`.

Seed 001/002 và Docker Compose được chỉnh sửa; các file T06 là file mới trên nhánh `feature/t06-seed-test-matrix`. Không sửa code backend/frontend hoặc schema. Docker Compose đã thêm seed vận tải 003 và cập nhật 005 vào khởi tạo database mới. Thành viên dùng cùng database sẽ thấy thêm mã T06_; seed khởi tạo chỉ chạy khi volume PostgreSQL trống; volume hiện có dùng script nạp, không xóa volume.

Địa điểm tham khảo: [Miền Đông mới](https://benxemiendongmoi.com.vn/), [Đà Lạt](https://benxehue.vn/tuyen-xe/129), [Cần Thơ](https://cand.vn/Giao-thong/Chuyen-toan-bo-hoat-dong-ben-xe-91B-ve-ben-xe-trung-tam-Can-Tho-i503995/), [Đà Nẵng](https://blog.vexere.com/ben-xe-trung-tam-da-nang/), [Nước Ngầm](https://benxenuocngam.com/ben-xe-nuoc-ngam-o-dau.html). Địa chỉ lưu đường/địa danh để tránh phụ thuộc tên quận/phường cũ. Điểm nghỉ trung gian và thời gian dừng là dữ liệu test, không phải danh sách trạm được cấp phép của hãng.

<a id="section-5"></a>

## Chuẩn hóa tỉnh và tìm kiếm chuyến

Migration `017_canonical_cities.sql` gộp tỉnh trùng tên, cập nhật mã tỉnh của bến xe,
giữ nguyên mã bến, tuyến, chuyến, đơn và vé. Mã chuẩn của danh mục seed:
`CT`, `HCM`, `LD`, `KH`, `DN`, `HN`, `NB`.
Tỉnh ngoài danh mục chọn một mã hiện có, ưu tiên mã không có tiền tố `DEMO_`, `T06_`.
Tên được so sánh sau khi bỏ dấu, chuẩn hóa hoa/thường và khoảng trắng.
Nếu mã chuẩn thuộc tỉnh khác, migration dừng và transaction rollback để kiểm tra thủ công.
Unique index ngăn tạo cùng tên tỉnh dưới mã khác, kể cả hai request đồng thời.

Frontend chỉ tải `/api/tinh-thanh`. `searchTrips` gửi một GET `/api/chuyen-xe`
với `from`, `to`, `date`; backend giữ nguyên quy tắc đang mở bán, thời hạn đặt vé,
điều kiện xe, nhân sự và số ghế còn. Bộ lọc giờ và sắp xếp vẫn ở frontend.
Giao diện không thay đổi. URL cũ chứa mã tỉnh bị gộp cần chọn lại địa điểm trên form.

### Áp dụng trên máy đang có dữ liệu

Chạy từ thư mục gốc sau khi lấy bản sửa. Dừng backend để tránh ghi dữ liệu trong lúc gộp:

```bash
docker compose stop backend
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > backup-before-city-normalization.sql
docker compose run --rm migrate
```

Chỉ tiếp tục nếu backup và migration đều thành công. Migration không xóa volume,
không nạp lại tài khoản và không đổi mật khẩu. Không commit file backup vào Git.

```bash
docker compose up -d backend
cd apps/frontend
npm ci
npm run dev
```

Nếu frontend đang chạy, dừng bằng Ctrl+C trước khi chạy lại. Chọn lại điểm đi/đến.
Kiểm tra PostgreSQL từ thư mục gốc:

```bash
docker compose exec postgres psql -U datvexe_user -d datvexe -c "SELECT ma_tinh_thanh,ten_tinh_thanh FROM tinh_thanh ORDER BY ten_tinh_thanh;"
docker compose exec postgres psql -U datvexe_user -d datvexe -c "SELECT city_name_key(ten_tinh_thanh),count(*) FROM tinh_thanh GROUP BY city_name_key(ten_tinh_thanh) HAVING count(*)>1;"
```

Truy vấn thứ hai phải trả `(0 rows)`. Trong Network của trình duyệt, mỗi lần tìm
chỉ có một request `/api/chuyen-xe`, không gọi `/api/ben-xe` để tải địa điểm.

### Kiểm tra tự động

```bash
node tests/frontend/trip_search_check.cjs
python3 tests/backend/canonical_cities_check.py
```

Kiểm tra PostgreSQL tạo rồi xóa CSDL thử riêng; không chỉnh CSDL đang sử dụng.
Có thể cấu hình `DB_CONTAINER`, `DB_USER` nếu khác mặc định.
Bao phủ sáu nhóm tỉnh trùng, chỉ có mã demo, nạp lại seed, chạy migration lại,
chặn tên trùng và giữ nguyên dữ liệu chuyến khi chuyển liên kết bến.
