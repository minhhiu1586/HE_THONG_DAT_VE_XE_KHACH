# T11 — Nhân viên và phân công chuyến

Nguồn task: [DATVEXE-19](https://app.plane.so/he_thong_dat_ve_xe_khach/browse/DATVEXE-19/). Nhánh `feature/t11-employee-assignment`, từ develop `8bb4dec`. Không sửa Class Diagram, Entity hoặc schema; không kiểm tra hạng bằng theo thống nhất của nhóm.

## Quy tắc để review

- Nhân viên, xe và tuyến cùng nhà xe; admin vẫn phải tuân thủ điều kiện nghiệp vụ. Nhân viên chỉ đọc hồ sơ/lịch được giao cho mình.
- Một nhân viên một phân công trên một chuyến; tối đa một tài xế chính. Tài xế phải đang làm việc, đúng vai trò, có số/hạn bằng bao phủ đến cuối chuyến.
- Lịch xe tách khỏi lịch nhân viên. Cả hai không trùng thời gian; chuyến liền trước/sau phải nối đúng bến. Khác bến trả lỗi yêu cầu điều phối di chuyển hoặc đổi xe/người; chưa tính đường đi giữa bến.
- Xe có thời gian chuẩn bị cấu hình `VEHICLE_TURNAROUND_MINUTES`, mặc định 30 phút. Lịch nhân viên kiểm tra riêng theo thời gian chuyến và bến nối chuyến. Người điều phối xác nhận kế hoạch lái/nghỉ cho chuyến dài.
- Nhân sự tối thiểu: một tài xế chính, ít nhất một phụ xe; chuyến trên 4 giờ thêm tài xế phụ. Chuyến dài phải có người điều phối xác nhận kế hoạch lái/nghỉ trước khi lưu phân công. Hệ thống chưa lưu từng ca lái nên không tự xác nhận tuân thủ giờ lái/nghỉ.
- Body phân công dùng `xacNhanKeHoachLaiNghi: true` cho chuyến dài. Backend ghi người xác nhận/thời điểm/lịch/xe vào đầu `ghiChu`. Khi đổi giờ/xe, điều phối xác nhận lại kế hoạch trước mở bán.
- Không sửa/thu hồi phân công chuyến đang chạy/hoàn tất. Chuyến đã có vé/giữ chỗ phải còn đủ nhân sự sau thay đổi; thiếu thì rollback. Giữ lịch sử chuyến hủy, không tính vào trùng lịch.
- Không chuyển nhà xe hồ sơ; chặn đổi vai trò/trạng thái làm mất điều kiện phân công hoạt động. Chỉ xóa hồ sơ chưa có phân công/tài khoản liên kết.

## Khu vực cá nhân theo vai trò

Phần bổ sung `feature/t11-role-workspaces`: đăng nhập đưa admin tới tổng quan quản trị, quản lý tới nhân viên/phân công, nhân viên tới không gian cá nhân. Tài xế/phụ xe có tổng quan, hồ sơ nghiệp vụ và lịch riêng; tài xế thấy số/hạn bằng. Trang `/tai-khoan` T07.1 quản lý thông tin tài khoản và mật khẩu. Quyền cập nhật hồ sơ nghiệp vụ giữ tại admin/quản lý nhà xe theo T11 mục 2.9; nhân viên không tự đổi vai trò/trạng thái/phân công.

Mỗi người dùng chung cấu trúc giao diện theo vai trò và nhận dữ liệu riêng từ phiên đăng nhập. Hướng dẫn kiểm tra: [t11-kiem-thu-giao-dien-nhan-vien.md](t11-kiem-thu-giao-dien-nhan-vien.md).

## API bàn giao frontend

| API | Nội dung |
| --- | --- |
| `GET /api/nhan-vien?q=&maNhaXe=&loaiNhanVien=&trangThai=` | Danh sách theo phạm vi, tìm/lọc |
| `GET/PUT/DELETE /api/nhan-vien/{id}` | Chi tiết/cập nhật/xóa hồ sơ khi được phép |
| `POST /api/nhan-vien` | Tạo hồ sơ |
| `GET /api/nhan-vien/{id}/phan-cong` | Lịch theo nhân viên |
| `GET /api/nhan-vien/{id}/dieu-kien?maChuyen=...&vaiTro=...` | Điều kiện cơ bản và cảnh báo kế hoạch lái/nghỉ; không phải xác nhận đủ giờ nghỉ |
| `/api/phan-cong`, `/api/phan-cong/{id}` | CRUD hiện có, bổ sung quy tắc T11 |

Body hồ sơ: `maNhanVien`, `hoTen`, `soDienThoai`, `email`, `ngaySinh`, `maNhaXe`, `loaiNhanVien`, `trangThai`, `soBangLai`, `ngayHetHanBangLai`. PUT thay toàn bộ trường; giữ enum TAI_XE/PHU_XE/DIEU_HANH và DANG_LAM_VIEC/TAM_NGHI/DA_NGHI_VIEC. Dùng cookie/CSRF T07.1, lỗi theo `ApiErrors` T05.

Frontend bổ sung trên `feature/t11-employee-ui`, từ commit backend T11 `b04f120`: quản lý tại `/quan-tri/nhan-vien`, lịch riêng tại `/nhan-vien`. Dùng cookie/CSRF và proxy `/api` hiện có; không dùng mock. Thêm mục điều hướng quản lý và liên kết lịch riêng cho tài khoản nhân viên.

Quản lý: tìm/lọc/phân trang, thêm/sửa hồ sơ, trạng thái và số/hạn bằng; chọn nhân viên để xem lịch. Nhập mã chuyến rồi tải thông tin để xem nhân sự, kiểm tra điều kiện, thêm/sửa/thu hồi phân công. Backend vẫn quyết định quyền và điều kiện lưu. Không thêm hạng bằng theo thống nhất mới nhất của nhóm, dù mô tả Plane còn nhắc trường này.

Hướng dẫn dữ liệu, tài khoản và ca test giao diện: [t11-kiem-thu-giao-dien-nhan-vien.md](t11-kiem-thu-giao-dien-nhan-vien.md).

## Dữ liệu và chạy kiểm tra

Bộ dữ liệu dự án đã chốt: 5 nhà xe, 35 nhân viên, 47 tài khoản, 10 xe 22/29/40 chỗ, 320 ghế, 20 tuyến và lịch 14 ngày. Ba seed trong `database/seed` dùng mã rõ ràng; xem [danh sách tài khoản/cách nạp](../../database/README.md#section-2). Seed cũ chuyển sang `tests/backend/fixtures/sql` để giữ test hồi quy, không nạp vào database dự án.

Thương hiệu/địa danh tham khảo [FUTA](https://futabus.vn/) và [Thành Bưởi](https://thanhbuoi.com.vn/Lien-He), thông tin nhà xe/ảnh từ nhóm cung cấp. Hồ sơ nhân sự, lịch, giá và kế hoạch điều phối là dữ liệu nội bộ của đồ án. Tài khoản đăng nhập dùng miền `datvexe.test`.

Database dự án là `datvexe`, dùng container PostgreSQL hiện có; database cũ giữ nguyên. Docker tạo schema rồi ba seed, loader gọi API T12 để kiểm tra/mở bán; không đổi hàng loạt enum bằng SQL. Layout mới bọc lại AuthProvider và gắn AccountActions để tài khoản T11 đăng nhập được.

```powershell
python scripts/load_project_data.py --activate
python tests/backend/project_data_check.py --check-reload
```

## Ảnh hưởng dùng chung / bàn giao T12

- Guard T07.1 bổ sung quyền/path nhân viên, phân biệt ID tạo mới với ID tài nguyên cũ; giữ kiểm tra nhà xe theo tài khoản thật.
- API `/api/phan-cong` hiện do `AssignmentController` → `AssignmentService` xử lý trong module Kim Cương, dùng `AssignmentInputs.PhanCong` và `AssignmentRules`. `VehicleController`/`VehicleService` không còn xử lý phân công. `RouteTripService` vẫn gọi lại quy tắc khi đổi xe/giờ. Fixture test xe/chuyến tạo tổ lái qua `AssignmentService`.
- T12 gọi `AssignmentRules.validateTrip(em, trip)` khi cập nhật, `readyForOperation(em, trip)` trước mở bán trong transaction của T12. Kiểm tra xe dùng `validateVehicleSchedule`; không tạo bộ quy tắc thứ hai.
- Bộ dữ liệu gọi API mở bán T12 hiện có. API giữ ghế/đơn nháp sau merge thuộc T16; checkout cần dùng hợp đồng `/api/booking/...` trong `giu-cho-va-don-dat-ve.md`.

## Kiểm tra và hướng dẫn sử dụng

- 52 kiểm tra HTTP T11 đạt: hồ sơ, lịch, quyền, cookie/CSRF và phân công đồng thời.
- Bộ dữ liệu được kiểm tra số lượng, quan hệ, lịch và nạp lại. 47 tài khoản đăng nhập đúng quyền; API tìm chuyến và sơ đồ ghế đã kiểm qua proxy frontend.
- Giao diện T11 có tìm/lọc, thẻ nhân viên, trạng thái, form hồ sơ, lịch chia trang và nhân sự chuyến. Ngày giờ hiển thị theo lịch địa phương từ backend.
- Cách chạy, tài khoản và thao tác kiểm tra: [t11-kiem-thu-giao-dien-nhan-vien.md](t11-kiem-thu-giao-dien-nhan-vien.md).
- Phạm vi dữ liệu và số lượng từng nhóm: [seed/README.md](../../database/README.md#section-2).
