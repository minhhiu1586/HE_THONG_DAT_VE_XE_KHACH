# Dữ liệu khởi tạo dự án

| File | Nội dung |
| --- | --- |
| `001_catalog.sql` | 5 nhà xe, 7 tỉnh/thành, 10 bến, loại xe 22/29/40, danh mục VNPAY |
| `002_accounts_staff_fleet.sql` | 35 nhân viên, 47 tài khoản, 10 xe và 320 ghế |
| `003_routes_trips.sql` | 20 tuyến, 40 điểm đón/trả, lịch 14 ngày và 490 phân công |

Database chạy dự án: **datvexe**. Database cũ không bị xóa. Seed cũ chuyển sang `tests/backend/fixtures/sql` để giữ hợp đồng test; Docker không nạp chúng vào database dự án.

Thống nhất `POSTGRES_DB=datvexe` và URL JDBC kết thúc bằng `/datvexe`. Trên máy đã chuyển bộ dữ liệu mới về tên này, dữ liệu cũ được giữ trong database lưu trữ; bản sao lưu nằm tại `src/backend/target/database-backups/` và không đưa lên Git. Thành viên khác dùng loader để nạp vào `datvexe` của mình, không cần đổi tên hoặc xóa database/volume.

```powershell
docker compose up -d postgres
python scripts/load_project_data.py --activate
python tests/backend/project_data_check.py --check-reload
```

Loader nạp trong transaction, giữ bản ghi đã có, cấu hình `.env` và mở bán bằng API T12. Docker mới tạo chuyến nháp; vẫn cần chạy loader để kiểm tra/mở bán. Chạy lại bổ sung lịch 14 ngày tiếp theo theo mã ngày, không dời chuyến hoặc ghi đè mật khẩu/hồ sơ.

## Thay bộ dữ liệu cũ bằng bộ chuẩn

Máy còn dữ liệu cũ (ví dụ 160 nhân viên) chạy tại thư mục gốc:

```powershell
docker compose up -d postgres
python scripts/load_project_data.py --reset --activate
python tests/backend/project_data_check.py --check-reload
```

`--reset` thay toàn bộ schema `public` và dữ liệu trong database đã chọn, gồm tài khoản, nhân viên, chuyến, đơn/vé và giao dịch; sau nạp có 35 nhân viên, 47 tài khoản và mật khẩu khởi tạo trong `accounts.md`. Script sao lưu đầy đủ database và kiểm tra bản sao trước khi thay. Backup nằm trong `src/backend/target/database-backups/*.dump`, không đưa lên Git. Nếu lỗi SQL, transaction giữ nguyên schema/dữ liệu cũ. Dùng chế độ này để thống nhất bộ khởi tạo, không chạy mỗi lần pull code.

Không có `--reset` thì loader vẫn giữ dữ liệu nhóm đã tạo. Không cần xóa Docker volume hoặc đổi tên database.

## Tài khoản đăng nhập

Danh sách đầy đủ tên, email và mật khẩu khởi tạo của 47 tài khoản: [accounts.md](accounts.md).

Mật khẩu khởi tạo: **DatVeXe2026!**. Database lưu hash PBKDF2 đúng backend. Tài khoản dùng ổn định, không cần tạo file tài khoản tạm hoặc cleanup sau sử dụng. Nếu đã đổi mật khẩu admin, đặt `PROJECT_ADMIN_PASSWORD` khi chạy loader.

| Vai trò | Email |
| --- | --- |
| 2 admin | `admin1@datvexe.test`, `admin2@datvexe.test` |
| 5 quản lý | `dieuhanh.futa@datvexe.test`, `dieuhanh.tb@datvexe.test`, `dieuhanh.kumho@datvexe.test`, `dieuhanh.hl@datvexe.test`, `dieuhanh.ct@datvexe.test` |
| 20 tài xế | `tx1.futa@datvexe.test` … `tx4.futa@datvexe.test`; tương tự tb/kumho/hl/ct |
| 10 phụ xe | `px1.futa@datvexe.test`, `px2.futa@datvexe.test`; tương tự tb/kumho/hl/ct |
| 10 khách | `khach1@datvexe.test` … `khach10@datvexe.test` |

Điều hành có quyền NHA_XE và liên kết hồ sơ đúng nhà xe. Tài xế/phụ xe chỉ đọc hồ sơ/lịch riêng. Admin và khách không có hồ sơ nhân viên.

## Lịch và khả năng sử dụng

Mỗi nhà xe có 1 xe tuyến ngắn đi-về trong ngày (1 tài xế + 1 phụ xe), 1 xe tuyến dài đổi chiều mỗi ngày (2 tài xế + 1 phụ xe). Tài xế 02 dự phòng. Lịch/giá và hồ sơ nhân sự là dữ liệu nội bộ đồ án, không xác nhận lịch thực của hãng. Tên/hotline hãng tham khảo thông tin nhóm gửi; không tạo mã số thuế/giấy phép/đánh giá chưa kiểm chứng.

Chỉ mở bán sau kiểm tra nhà xe, bến, xe/ghế, đăng kiểm, lịch và nhân sự. Dấu kế hoạch của dữ liệu khởi tạo không chứng nhận giờ lái thực tế; điều phối cần rà soát trước vận hành.

Code sau merge có API giữ ghế và đơn nháp tại `/api/booking/...`, xem `docs/api/booking-order.md`. Checkout cần dùng đúng hợp đồng này. Danh mục VNPAY đã có; giao dịch và vé phát sinh từ nghiệp vụ, không khởi tạo sẵn thanh toán thành công.

## Ghi chú riêng database: đã có và còn thiếu

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

## Kết quả kiểm tra 13/10/2026

- Đúng số lượng và FK; không trùng lịch xe/nhân viên; nạp lại không đổi dữ liệu đã có.
- 210 chuyến mở bán qua T12; 47 tài khoản đăng nhập đúng quyền, 174 lượt API đạt.
- 43 lượt API tìm chuyến/ghế qua proxy frontend đạt; 52 kiểm tra hồi quy T11 đạt.
- Backend: 90 test đạt, 14 test tùy chọn bỏ qua; frontend build production đạt. Không tính luồng tạo đơn/thanh toán chưa có API là đã đạt.
