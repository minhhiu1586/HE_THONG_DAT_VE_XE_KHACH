# T11 — Nhân viên và phân công chuyến

Nguồn task: [DATVEXE-19](https://app.plane.so/he_thong_dat_ve_xe_khach/browse/DATVEXE-19/). Nhánh `feature/t11-employee-assignment`, từ develop `8bb4dec`. Không sửa Class Diagram, Entity hoặc schema; không kiểm tra hạng bằng theo thống nhất của nhóm.

## Quy tắc để review

- Nhân viên, xe và tuyến cùng nhà xe; admin vẫn phải tuân thủ điều kiện nghiệp vụ. Nhân viên chỉ đọc hồ sơ/lịch được giao cho mình.
- Một nhân viên một phân công trên một chuyến; tối đa một tài xế chính. Tài xế phải đang làm việc, đúng vai trò, có số/hạn bằng bao phủ đến cuối chuyến.
- Lịch xe tách khỏi lịch nhân viên. Cả hai không trùng thời gian; chuyến liền trước/sau phải nối đúng bến. Khác bến trả lỗi yêu cầu điều phối di chuyển hoặc đổi xe/người; chưa tính đường đi giữa bến.
- Bỏ đệm nghỉ 60 phút cho nhân viên; không áp mốc 2/8 giờ. Xe có thời gian chuẩn bị cấu hình `VEHICLE_TURNAROUND_MINUTES`, mặc định kỹ thuật 30 phút để review/điều chỉnh. Đúng ranh giới được phép. Thời gian chuẩn bị xe không chứng minh tài xế nghỉ đủ.
- Nhân sự tối thiểu: một tài xế chính, ít nhất một phụ xe; chuyến trên 4 giờ thêm tài xế phụ. Chuyến dài phải có người điều phối xác nhận kế hoạch lái/nghỉ trước khi lưu phân công. Hệ thống chưa lưu từng ca lái nên không tự xác nhận tuân thủ giờ lái/nghỉ.
- Body phân công thêm `xacNhanKeHoachLaiNghi: true` cho chuyến dài. Backend ghi người xác nhận/thời điểm/lịch/xe vào đầu `ghiChu` hiện có; không thêm cột. Sửa giờ/xe làm dấu xác nhận cũ không còn phù hợp; phải xác nhận lại trước mở bán. Dấu SEED chỉ dành cho kịch bản dữ liệu mẫu.
- Không sửa/thu hồi phân công chuyến đang chạy/hoàn tất. Chuyến đã có vé/giữ chỗ phải còn đủ nhân sự sau thay đổi; thiếu thì rollback. Giữ lịch sử chuyến hủy, không tính vào trùng lịch.
- Không chuyển nhà xe hồ sơ; chặn đổi vai trò/trạng thái làm mất điều kiện phân công hoạt động. Chỉ xóa hồ sơ chưa có phân công/tài khoản liên kết.

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

Nhánh này chỉ cung cấp backend, dữ liệu và test T11. Thành viên frontend xây giao diện theo API trên; dùng cookie/CSRF hiện có và hiển thị lỗi backend. Không bổ sung hoặc sửa frontend trong PR này.

## Dữ liệu và chạy kiểm tra

`006_t11_staff.sql`: 18 nhân viên, 6 xe 22/29/40 chỗ, 182 ghế, 12 tuyến/chuyến đi-về và 36 phân công. Lịch lấy ngày nạp đầu; nạp lại không dời lịch hoặc ghi đè hồ sơ mới. Chuẩn hóa tên tạo tự động còn hậu tố TX/PX của bộ T06; giữ ID và lịch sử. Tên hiển thị không có chữ demo; mã nền DEMO vẫn tồn tại để tương thích FK/test đã có, không hiển thị thành tên nhà xe.

Tham khảo thương hiệu/địa danh từ [FUTA](https://futabus.vn/) và [Thành Bưởi](https://thanhbuoi.com.vn/Lien-He). Nhân viên, điện thoại, biển số, lịch, giá và kế hoạch điều phối là dữ liệu mẫu phục vụ đồ án; không phải thông tin nhân viên/lịch vận hành của hãng. Email dùng miền `example.test`.

Docker mới nạp seed 006 sau seed 005. Database có volume sẵn cần schema xác thực 003 của T07 và dữ liệu vận tải nền; không xóa volume để nạp lại.

```powershell
powershell -ExecutionPolicy Bypass -File tests/backend/load_t11_staff.ps1
python tests/backend/employee_assignment_smoke.py
```

## Ảnh hưởng dùng chung / bàn giao T12

- Guard T07.1 bổ sung quyền/path nhân viên, phân biệt ID tạo mới với ID tài nguyên cũ; giữ kiểm tra nhà xe theo tài khoản thật.
- `VehicleService` dùng `AssignmentRules`; `RouteTripService` gọi lại kiểm tra khi đổi xe/giờ. Ba fixture test xe/chuyến đổi sang lịch về đúng bến, không bỏ kiểm tra hồi quy cũ.
- T12 gọi `AssignmentRules.validateTrip(em, trip)` khi cập nhật, `readyForOperation(em, trip)` trước mở bán trong transaction của T12. Kiểm tra xe dùng `validateVehicleSchedule`; không tạo bộ quy tắc thứ hai.
- Chưa thêm trạng thái chuyến nháp hoặc thực hiện luồng mở bán thay T12. Nghiệm thu tích hợp cuối cùng và review Thanh Hiền vẫn cần thực hiện khi T12 sẵn sàng.

## Kết quả kiểm tra — 12/10/2026

- Backend: 73 test đạt, 11 test PostgreSQL tùy chọn bỏ qua; không có test lỗi. Chạy lại 6 test hồ sơ/lịch sau sửa cuối: đạt.
- API với PostgreSQL thật: 42 kiểm tra đạt, gồm cookie/CSRF, phạm vi nhà xe, lịch nối chuyến và hai tài khoản phân công đồng thời.
- Seed 006: nạp và nạp lại đạt; số lượng/quan hệ/lịch đi-về đúng, lần nạp lại không thay đổi dữ liệu.
- Chưa nghiệm thu luồng mở bán T12 hoặc kiểm chứng giờ lái/nghỉ thực tế; không tính các phần này là đã hoàn tất.
