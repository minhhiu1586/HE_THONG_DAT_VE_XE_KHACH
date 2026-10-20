# Bàn giao backend Kim Cương — 20/10/2026

Đợt tách module: nhánh `fix/backend-tran-dang-thi-kim-cuong`, so với `de53e13`; đã lưu trong commit `792b86f`.

## Đợt làm rõ code tiếp theo

Nhánh `fix/backend-kim-cuong-readability`, so với `792b86f`. Chưa commit/push.

- `EmployeeService`: lọc nhân viên và sắp lịch theo từng bước bằng vòng lặp.
- `AssignmentService`: gom xử lý danh sách vào `assignmentViews()`, giữ cách lọc cũ của từng hàm.
- `AssignmentRules`: đặt tên điều kiện vai trò/trùng lịch/chuyến liền kề; kiểm tra tổ lái bằng vòng lặp; đặt hằng mốc 4 giờ và làm rõ dấu xác nhận kế hoạch.
- Hai Repository: tách biến JPQL, trình bày tham số rõ ràng; không thay đổi nội dung truy vấn.
- Thêm test đúng 4 giờ và vượt 1 giây. Giữ API, nghiệp vụ, transaction, khóa dữ liệu; không đổi frontend, shared, database, .env.

Phạm vi đợt này: sửa 5 file backend, 1 file test và tài liệu này; không thêm/xóa file. Đây là cải tiến khả năng đọc và bảo trì, chưa đo tăng tốc độ.

Kiểm chứng: toàn backend 217 đạt/63 bỏ qua (280 tổng); sau chỉnh cuối chạy lại 44 test nhân viên/phân công/xe/chuyến đạt, WAR build thành công. Log: `apps/backend/target/readability-verify.log`, `readability-final-tests.log`.

Chưa chạy lại HTTP/UI đợt này: Docker daemon không hoạt động khi thử khởi động backend. Kết quả bên dưới thuộc đợt tách module trước.

## Đợt tách module trước

## Cải tiến

| Trước | Hiện tại | Lợi ích |
|---|---|---|
| API phân công nằm chung quản lý xe | AssignmentController/Service/Inputs trong folder Kim Cương | Rõ người phụ trách, dễ tìm và giải thích luồng |
| Controller/service nhân viên viết dồn nhiều bước | Chia nhận request, kiểm quyền, hồ sơ, kiểm trùng và lưu | Dễ đọc, khoanh vùng lỗi |
| Truy vấn liên hệ/tài khoản nằm trong service | NhanVienRepository xử lý | Tập trung truy vấn, giữ chung transaction |
| Test phân công nằm lẫn test xe | AssignmentServiceTest và test controller | Dễ kiểm riêng; vẫn kiểm tích hợp xe/chuyến |

Luồng: **AssignmentPanel → /api/phan-cong → AssignmentController → AssignmentInputs → AssignmentService → AssignmentRules/Repository**.

Giữ nguyên API, JSON, mã lỗi và nghiệp vụ. Hiền tiếp tục dùng AssignmentRules để kiểm nhân sự khi mở bán/bắt đầu chuyến. Không đổi frontend, shared, database hoặc .env. Đây là cải tiến khả năng bảo trì, chưa đo tăng tốc độ.

## File thay đổi

**24 file: sửa 17, thêm 7, xóa 0.** Không sao chép xử lý phân công sang hai nơi.

| Phạm vi | File |
|---|---|
| Thêm code Kim Cương | AssignmentController, AssignmentInputs, AssignmentService |
| Sửa code Kim Cương | EmployeeController, EmployeeInputs, EmployeeService, NhanVienRepository |
| Test Kim Cương | Thêm EmployeeControllerTest, AssignmentServiceTest; sửa EmployeeServiceTest |
| Code Hiền | Sửa VehicleController, VehicleService, VehicleInputs: bỏ phần phân công đã chuyển |
| Test Hiền | Cập nhật VehicleServiceTest, TripServiceTest, PostgresVehicleTest, PostgresTripTest sang service mới |
| Tài liệu | README hai thành viên; testing/README; báo cáo này; manual-checklist; phân công nhóm; hợp đồng API T11 |

AssignmentRules và PhanCongChuyenXeRepository giữ nguyên. Phân trang backend mới và DTO đầu ra toàn bộ chưa triển khai; danh sách vẫn giữ cách lọc/Map hiện có.

## Kết quả kiểm thử

| Kiểm tra | Kết quả |
|---|---|
| Bộ tập trung nhân viên/xe/chuyến | 43 đạt, không lỗi/bỏ qua |
| Rà soát cuối hồ sơ/phân công | Chạy lại 18 test: đạt toàn bộ (`employee-final-review.log`) |
| Toàn backend và WAR | BUILD SUCCESS; 216 đạt, 63 bỏ qua, không lỗi — tổng 279 |
| HTTP trên WAR mới | 76 nhân viên + 81 xe + 123 mở bán = 280 kiểm tra đạt |
| Dữ liệu lõi | Snapshot 7 bảng trước/sau khớp; fixture tự dọn |
| Frontend cùng source hiện tại | Build/TypeScript đạt; lint 0 lỗi, 2 cảnh báo img có sẵn |
| UI đã xem | Admin đăng nhập, danh sách 35 nhân viên/20 tài xế, tìm mã và mở hồ sơ/lịch |

Bằng chứng trong `apps/backend/target`: `assignment-split-tests.log`, `assignment-split-verify.log`, `integration-audit/results-selected.json`, `employee-frontend-build.log`, `employee-frontend-lint.log`. WAR có lớp phân công mới, không còn VehicleInputs.PhanCong; URL chỉ đăng ký tại AssignmentController.

[Checklist kiểm tay](manual-checklist.md). Chưa nghiệm thu toàn bộ UI, PostgreSQL JUnit có điều kiện, tải lớn và dịch vụ bên ngoài. Test bỏ qua không tính đạt; không kết luận toàn dự án đã sẵn sàng nộp chỉ từ phạm vi này.
