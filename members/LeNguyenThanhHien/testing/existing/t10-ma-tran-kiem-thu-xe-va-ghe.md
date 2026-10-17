# T10 — Ma trận kiểm thử và bàn giao

Nhánh `feat/t10-vehicle-management`, ngày kiểm tra 12/10/2026. Giữ nguyên các entity/thuộc tính và schema đã chốt. Mỗi script HTTP/browser tạo fixture riêng và dọn sau chạy; PostgreSQL tests chạy trên database riêng.

## Đối chiếu đề bài

| Ca | Điều kiện/thao tác | Kết quả mong đợi | Bằng chứng |
| --- | --- | --- | --- |
| T10-01 | Tạo loại xe hợp lệ | 201 và lưu đúng sức chứa/tầng | VehicleServiceTest, HTTP T10, browser |
| T10-02 | Loại xe trùng mã | Từ chối, không thêm bản ghi | VehicleTask10Test, HTTP T10 |
| T10-03 | Tạo xe mới hợp lệ | Xe và đủ ghế cùng transaction | VehicleServiceTest, HTTP T10, browser |
| T10-04 | Biển số trùng, khác chữ hoa/thường/khoảng trắng đầu cuối | 409, không tạo ghế dở dang | VehicleTask10Test, HTTP T10 |
| T10-05 | Nhà xe/loại xe không tồn tại | 404 | HTTP T10 |
| T10-06 | Số ghế 0/âm hoặc không khớp loại | 400 | VehicleTask10Test, HTTP T10 |
| T10-07 | Dung tích nhiên liệu âm | 400 | VehicleTask10Test, HTTP T10 |
| T10-08 | Lưu đăng kiểm hợp lệ | Thành công; đúng hai ngày | VehicleTask10Test, browser |
| T10-09 | Đăng kiểm hết hạn hoặc không bao phủ ngày đến qua đêm | Không gán xe/tạo chuyến mở bán | VehicleTask10Test, HTTP T10 |
| T10-10 | Xe bảo trì | Không gán vào chuyến mới | VehicleTask10Test, HTTP T10 |
| T10-11 | Sơ đồ đúng sức chứa | Lưu và đọc lại đúng | VehicleTask10Test, HTTP T10, browser |
| T10-12 | Sơ đồ ít hơn sức chứa | Từ chối lưu; legacy thiếu ghế không đủ điều kiện khai thác | VehicleTask10Test, HTTP T10 |
| T10-13 | Sơ đồ nhiều hơn sức chứa | Từ chối, giữ bản cũ | VehicleTask10Test, HTTP T10 |
| T10-14 | Hai ghế trùng ID/mã trong cùng xe | Từ chối | VehicleTask10Test, HTTP T10 |
| T10-15 | Hai xe đều có A1 | Cho phép với ID liên kết riêng | VehicleTask10Test, HTTP T10 |
| T10-16 | Hai ghế trùng vị trí trong cùng xe | Từ chối cả khi khác hoa/thường | VehicleTask10Test, HTTP T10, browser |
| T10-17 | Tầng ghế không hợp lệ | 400, không thay sơ đồ | VehicleTask10Test, HTTP T10 |
| T10-18 | Ghế chưa ràng buộc vào bảo trì | Cập nhật/đọc lại thành công | VehicleTask10Test, HTTP T10, browser |
| T10-19 | Giữ/bán ghế bảo trì | Helper từ chối; sơ đồ theo chuyến trả KHONG_BAN | VehicleTask10Test, HTTP T10 |
| T10-20 | Ghế bán ở C1, dùng cùng xe ở C2 | C1 DA_BAN, C2 TRONG; ghế vật lý không thành đã bán | VehicleTask10Test, HTTP T10 |
| T10-21 | Đổi mã/vị trí ghế đã có vé | 409; ID và liên kết giữ nguyên | VehicleTask10Test, HTTP T10 |
| T10-22 | Sơ đồ ảnh hưởng chuyến đã bán | 409, không đổi bản cũ | VehicleTask10Test, HTTP T10 |
| T10-23 | Đổi xe chuyến đã có vé/giữ chỗ | 409, chuyến vẫn trỏ xe cũ | VehicleTask10Test, HTTP T10 |
| T10-24 | Xóa xe đã có chuyến | 409 | VehicleTask10Test, HTTP T10 |
| T10-25 | NHA_XE A ghi xe/ghế A | Thành công; nhà xe lấy từ phiên | VehicleTask10Test, HTTP T10 |
| T10-26 | NHA_XE A ghi xe/ghế B, khai nhà xe B | 403, không ghi | VehicleTask10Test, HTTP T10 |
| T10-27 | Chưa đăng nhập gọi quản lý xe | 401 | VehicleTask10Test, HTTP T10 |
| T10-28 | Khách hàng gọi quản lý xe | 403 | VehicleTask10Test, HTTP T10 |
| T10-29 | Sơ đồ nhiều phần tử, phần cuối sai | Không lưu bất kỳ thay đổi nào | VehicleTask10Test, HTTP T10 |
| T10-30 | Đăng kiểm mới không bao phủ chuyến đã bán | Từ chối; không tự hủy vé/chuyến | VehicleTask10Test, HTTP T10 |

Kiểm tra thêm: kết thúc bảo trì giữ nguyên ID ghế; đăng kiểm mới sau chuyến hoàn tất giữ nguyên vé; hai yêu cầu tạo cùng biển số trên PostgreSQL chỉ lưu một xe với đủ ghế; CRUD/tìm loại xe; lọc xe theo biển số/loại/nhà xe/trạng thái; hai label hoán đổi trong cùng giao dịch; giữ chỗ chỉ tác động đúng chuyến và hết hiệu lực tại đúng hạn; ID dạng số/boolean không được coi là ID chuỗi; frontend báo validation tiếng Việt và xóa thông báo cũ khi đổi thao tác.

## Cách tìm bằng chứng

- Unit/transaction: `members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/VehicleTask10Test.java`, `VehicleServiceTest.java`, các test scope trong `AccessControlTest.java`.
- PostgreSQL/schema/khóa: `PostgresTask10Test`, `PostgresVehicleTest`, `PostgresAccessControlTest`, `PostgresT06AccessControlTest`, `OperatorServiceTest`; nạp toàn bộ fixture test trước chạy. Xem báo cáo `apps/backend/target/surefire-reports` sau Maven.
- HTTP T10: `tests/backend/vehicle_management_smoke.py`; sơ đồ, đăng kiểm, quyền và dữ liệu vé được kiểm tra với Tomcat/PostgreSQL thật.
- Hồi quy: `tests/backend/vehicle_api_smoke.py`, `api_smoke.py`, `account_auth_smoke.py`.
- Browser: `tests/frontend/vehicle_management_smoke.cjs`; frontend production build, Next proxy và backend thật.
- [Lệnh chạy trong VS Code](../../docs/huong-dan-demo.md#section-1), [API và helper](../../../../docs/api/t10-quan-ly-xe-va-ghe.md).

## Kết quả chạy

Kiểm tra ngày 12/10/2026 trên nhánh `feat/t10-vehicle-management`, với PostgreSQL riêng cho kiểm thử và Tomcat thật:

- Maven `verify`: **75 test đạt, 0 lỗi, 0 bỏ qua**, bao gồm các test PostgreSQL bật qua cấu hình.
- HTTP T10: **80 kiểm tra đạt**; hồi quy xe/ghế **28**, tuyến/chuyến **93**, tài khoản **36** — tổng **237 phản hồi được kiểm tra**.
- Browser T10 và tài khoản: đạt; kiểm tra tạo dữ liệu, đăng kiểm, sơ đồ hai tầng, bảo trì, rollback, validation tiếng Việt và phân quyền.
- Frontend production build: thành công. Lint: **0 lỗi**, còn 3 cảnh báo có sẵn trong `lib/api.ts` và `lib/mock-data.ts`.
- `git diff --check`: đạt. Entity, enum, persistence mapping, schema SQL và Docker Compose không thay đổi.
- Bộ kiểm tra cấu trúc ma trận chung: 23 use case, 82 test case hợp lệ về cấu trúc. Trạng thái cũ của ma trận chung vẫn là 20 Pass/62 Blocked; không xem kết quả kiểm tra cấu trúc là nghiệm thu các nghiệp vụ còn Blocked.

Bằng chứng khi chạy lại: báo cáo Maven ở `apps/backend/target/surefire-reports`, kết quả PASS của các script HTTP/browser nêu trên. Các luồng giữ/bán vé được đánh giá ở mức helper như phần phạm vi dưới đây.

## Lượt rà soát bổ sung

Đối chiếu lại toàn bộ 30 ca trong đề bài. Phát hiện và sửa lỗi tìm kiếm loại xe làm thay đổi danh mục dùng chung của form xe/sơ đồ ghế. Kết quả tìm kiếm hiện được lưu riêng, nên khi chuyển sang xe vẫn chọn đủ loại xe và hiển thị đúng số tầng. Browser test thực hiện tìm kiếm không có kết quả rồi chuyển tab, tạo xe và xác minh tầng 2.

Bổ sung 4 bài Java: hai bài PostgreSQL kiểm tra biển số trùng khi ghi đồng thời và đổi vị trí/rollback toàn bộ sơ đồ; hai bài nghiệp vụ kiểm tra kết thúc bảo trì và cập nhật đăng kiểm sau chuyến hoàn tất không đổi vé. Bộ HTTP bổ sung sức chứa âm/phân số, chặn đổi sức chứa loại xe đang dùng, kết thúc bảo trì ghế, chặn xóa ghế có vé và sửa xe khác nhà xe.

## Phạm vi tích hợp và nghiệm thu

T10 cung cấp ghế vật lý, sơ đồ theo chuyến và `VehicleRules.seatSellable` cho giữ/bán ghế. Chưa có endpoint đặt vé/giữ ghế trong nhánh để nghiệm thu toàn bộ luồng mua vé; kết quả T10-19 ở mức helper và API sơ đồ, không khẳng định module đặt vé đã tích hợp. Quy trình đổi xe/ghế sau bán vé không được triển khai.

Các lựa chọn dùng schema đã chốt: ID ghế database không đổi; mã/vị trí hiển thị dùng `viTri`; sức chứa từ loại xe; hiệu lực từ `ngayDangKiem`/`hanDangKiem`; không thêm trạng thái nháp hoặc hàng/cột. Loại xe dùng danh mục chung ADMIN quản lý, NHA_XE được đọc để chọn; vẫn đối chiếu ma trận T01 khi nhóm cung cấp bản chính thức.

Review/chấp nhận Pull Request của Minh Hiếu là bước bàn giao còn cần thực hiện; test tự động không thay thế review và không xác nhận PR đã được chấp nhận.
