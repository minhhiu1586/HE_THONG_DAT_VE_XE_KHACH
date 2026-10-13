# Test giao diện T11

## Ghi chú riêng frontend T11

Copy thông tin đăng nhập tại [danh sách 47 tài khoản](../database/seed/accounts.md).

- Đã làm: danh sách/tìm/lọc/phân trang nhân viên; thêm/sửa/xóa hồ sơ; xem lịch, kiểm tra điều kiện, tạo/sửa/thu hồi phân công; trang nhân viên chỉ đọc lịch riêng.
- File UI chính: `src/frontend/components/Employees/`; hai trang `/quan-tri/nhan-vien` và `/nhan-vien`. Đăng nhập/menu dùng `AccountActions.tsx`, `AuthProvider` trong layout và `management-navigation.ts`.
- Đã kiểm: lint riêng T11, TypeScript, build production; 52 kiểm tra HTTP T11 và 43 lượt API dữ liệu qua proxy frontend. Kiểm tra HTTP không thay cho kiểm tra thao tác trên giao diện.
- Còn kiểm trực tiếp: tạo/sửa/thu hồi trên màn hình, bố cục 390px, thông báo lỗi và chuyển vai trò theo bảng bên dưới. Chưa nghiệm thu chung với người review.
- Lần chạy lint toàn frontend mới nhất: 2 lỗi `set-state-in-effect` trong `components/booking/SearchForm.tsx`, 5 cảnh báo ở các phần khác. Đây là phần ngoài T11, chưa sửa trong nhánh này; không coi lint toàn dự án đã đạt.
- Quy tắc backend: `t11-employees.md`. Dữ liệu: `docs/database/seed/README.md`. Danh sách có thẻ tên/vai trò/trạng thái; lịch quản lý chia 5 chuyến/trang, lịch cá nhân 8 chuyến/trang; ngày giờ hiển thị theo định dạng Việt Nam.

## Chạy và nạp dữ liệu

Tại thư mục gốc dự án, PowerShell:

```powershell
docker compose up -d postgres
python scripts/load_project_data.py --activate
python tests/backend/project_data_check.py --check-reload
```

Dùng tài khoản ổn định trong [danh sách đăng nhập](../database/seed/README.md): `admin1@datvexe.test`, `dieuhanh.futa@datvexe.test`, `tx1.futa@datvexe.test`. Mật khẩu khởi tạo `DatVeXe2026!`. Không cần file tài khoản tạm; không cleanup các tài khoản này.

Terminal khác:

```powershell
cd src/frontend
npm.cmd ci
npm.cmd run dev
```

Mở `http://localhost:3000`. Đăng nhập lần lượt các tài khoản test; đăng xuất trước khi đổi vai trò.

Nếu frontend đã chạy tại cổng 3000, dùng cửa sổ đó, không chạy thêm server. Lấy mã chuyến ngắn ngày mai bằng PowerShell:

```powershell
$ngayChuyen = (Get-Date).AddDays(1).ToString('yyyyMMdd')
"CX_FUTA_1_${ngayChuyen}_1"
```

## Dữ liệu dùng để kiểm tra

Seed dự án: `docs/database/seed/001_catalog.sql`, `002_accounts_staff_fleet.sql`, `003_routes_trips.sql`. Gồm 5 nhà xe, 35 nhân viên, 10 xe, 320 ghế, 20 tuyến và 210 chuyến mở bán trong 14 ngày. Database là `datvexe_project`. Các mã chuyến chứa ngày; chạy loader lại để bổ sung lịch mới, không dời lịch đã có.

| Mã | Dùng để test |
| --- | --- |
| NV_FUTA_01, NV_FUTA_05 | Tài xế/phụ xe tuyến ngắn của NX_FUTA |
| NV_FUTA_03, NV_FUTA_04, NV_FUTA_06 | Hai tài xế và phụ xe tuyến dài |
| CX_FUTA_1_YYYYMMDD_1 / CX_FUTA_1_YYYYMMDD_2 | Chuyến ngắn đi/về cùng ngày, thay YYYYMMDD bằng ngày mai |
| NV_TB_01 | Nhân viên nhà xe khác, dùng test phạm vi |
| KC_UI_01 / KC_UI_PC_01 | Mã gợi ý cho hồ sơ/phân công tự tạo; không sửa dữ liệu nhóm |

## Các bước kiểm tra

| Ca | Thao tác / kết quả mong đợi |
| --- | --- |
| Admin | Vào `/quan-tri/nhan-vien`, thấy dữ liệu trong toàn bộ phạm vi admin |
| Chưa đăng nhập | Trang quản lý hiện liên kết đăng nhập; trang lịch riêng chuyển tới đăng nhập |
| Quản lý nhà xe | Chỉ thấy 7 nhân viên NX_FUTA; không thấy NV_TB_01 |
| Tìm/lọc/phân trang | Tìm NV_FUTA_01; lọc vai trò/trạng thái; đổi trang khi có hơn 20 kết quả |
| Hồ sơ | Tạo KC_UI_01 với email/điện thoại riêng; sửa tên/trạng thái; thấy kết quả lưu |
| Dữ liệu sai | Điện thoại sai, email sai, thiếu số/hạn bằng tài xế: bị từ chối |
| Trùng mã/liên hệ | Bấm Thêm nhân viên rồi tạo mã/email/điện thoại đã tồn tại: báo lỗi backend |
| Lịch/nhân sự | Chọn NV_FUTA_01; nhập mã chuyến ngắn ngày mai: đúng tuyến/giờ và hai nhân sự; tuyến dài có ba người |
| Phân công | Chọn hồ sơ thử đủ điều kiện, nhập mã phân công riêng và mã chuyến; kiểm tra điều kiện rồi lưu |
| Chuyến dài | Chưa xác nhận kế hoạch lái/nghỉ: backend từ chối; xác nhận rồi lưu nếu đủ các điều kiện khác |
| Cập nhật/thu hồi | Sửa ghi chú hoặc thu hồi phân công thử; danh sách được cập nhật |
| Lịch sử | Thử xóa NV_FUTA_01: bị chặn vì có lịch sử; không thu hồi phân công seed để thử |
| Nhân viên | Vào `/nhan-vien`, chỉ thấy lịch của NV_FUTA_01; không có nút sửa/thu hồi |
| Màn hình nhỏ | Thu cửa sổ về khoảng 390px: bộ lọc/form xếp dọc, đọc và thao tác được |
| Mất kết nối | Tắt backend khi test, thao tác phải hiện lỗi; bật lại và tải trang để tiếp tục |

Tự động kiểm tra ca trùng lịch, bến nối chuyến, vai trò, hạn bằng, quyền, CSRF và cạnh tranh đồng thời:

```powershell
python tests/backend/employee_assignment_smoke.py
```

Sau test, chỉ xóa hồ sơ/phân công thử tự tạo khi được phép. Giữ tài khoản, nhân viên, xe và lịch khởi tạo để nhóm sử dụng tiếp.

Luồng mở bán và nghiệm thu chung với T12 thực hiện riêng; không sửa chuyến hoặc tài khoản của thành viên khác để test.
