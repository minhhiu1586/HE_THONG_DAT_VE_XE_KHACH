# Test giao diện T11

## Không gian theo vai trò

Đăng nhập không có đường dẫn `next` sẽ mở khu vực phù hợp:

| Tài khoản | Trang mở | Nội dung |
| --- | --- | --- |
| Admin | `/quan-tri` | Tổng quan quản trị, quản lý toàn phạm vi, liên kết hồ sơ cá nhân |
| Quản lý nhà xe / điều hành | `/quan-tri/nhan-vien` | Hồ sơ, vai trò, trạng thái và phân công trong nhà xe |
| Tài xế | `/nhan-vien` | Tổng quan cá nhân, hồ sơ và số/hạn bằng, lịch riêng |
| Phụ xe | `/nhan-vien` | Tổng quan cá nhân, hồ sơ công việc và lịch riêng |
| Khách hàng | `/tai-khoan` | Hồ sơ tài khoản T07.1 hiện có |

Trang nhân viên lấy hồ sơ từ phiên đăng nhập; tên, lịch và vai trò là của người đang đăng nhập. Hồ sơ nghiệp vụ do admin/quản lý cập nhật; thông tin tài khoản và mật khẩu dùng trang `/tai-khoan`.

Kiểm tra bằng `tx1.futa@datvexe.test`, `px1.futa@datvexe.test`, `dieuhanh.futa@datvexe.test`, `admin1@datvexe.test`, `khach1@datvexe.test`, mật khẩu khởi tạo trong `tai-khoan-mau.md`. Đăng xuất trước mỗi lần đổi người. Tại trang nhân viên, thử cả ba mục Tổng quan/Hồ sơ nhân viên/Lịch phân công, lọc ngày có lịch và ngày không có lịch, kiểm tra trên màn hình nhỏ.

Đã kiểm trực tiếp đăng nhập cả 5 nhóm trên: tài xế nhận NV_FUTA_01 và 28 chuyến; phụ xe nhận NV_FUTA_05 và hồ sơ phụ xe; điều hành chỉ thấy 7 nhân viên FUTA; admin mở tổng quan quản trị; khách mở hồ sơ tài khoản. Lint riêng phần bổ sung, TypeScript và build production đạt. Việc nghiệm thu chung luồng chuyến nháp → phân công → mở bán vẫn cần reviewer theo T11.

Đã kiểm bộ lọc lịch: ngày 14/10/2026 hiển thị 2 chuyến của NV_FUTA_01; ngày 01/01/2026 hiển thị trạng thái không có chuyến. Hồ sơ trên khung 390px không tràn ngang. Các smoke test T10/T12/tuyến đường dùng `next=/tai-khoan` để tiếp tục kiểm luồng hồ sơ cũ; mặc định đăng nhập mới kiểm theo bảng vai trò bên trên.

## Ghi chú riêng frontend T11

Copy thông tin đăng nhập tại [danh sách 47 tài khoản](../../database/tai-khoan-mau.md).

- Đã làm: danh sách/tìm/lọc/phân trang nhân viên; thêm/sửa/xóa hồ sơ; xem lịch, kiểm tra điều kiện, tạo/sửa/thu hồi phân công; trang nhân viên chỉ đọc lịch riêng.
- File UI chính: `apps/frontend/components/Employees/`; hai trang `/quan-tri/nhan-vien` và `/nhan-vien`. Đăng nhập/menu dùng `AccountActions.tsx`, `AuthProvider` trong layout và `management-navigation.ts`.
- Đã kiểm: lint riêng T11, TypeScript, build production; 52 kiểm tra HTTP T11 và 43 lượt API dữ liệu qua proxy frontend. Kiểm tra HTTP không thay cho kiểm tra thao tác trên giao diện.
- Còn kiểm trực tiếp: tạo/sửa/thu hồi trên màn hình, bố cục 390px, thông báo lỗi và chuyển vai trò theo bảng bên dưới. Chưa nghiệm thu chung với người review.
- Lần chạy lint toàn frontend mới nhất: 2 lỗi `set-state-in-effect` trong `components/booking/SearchForm.tsx`, 5 cảnh báo ở các phần khác. Đây là phần ngoài T11, chưa sửa trong nhánh này; không coi lint toàn dự án đã đạt.
- Quy tắc backend: `t11-quan-ly-nhan-vien-va-phan-cong.md`. Dữ liệu: `database/README.md`. Danh sách có thẻ tên/vai trò/trạng thái; lịch quản lý chia 5 chuyến/trang, lịch cá nhân 8 chuyến/trang; ngày giờ hiển thị theo định dạng Việt Nam.

## Chạy và nạp dữ liệu

### Máy thành viên không đăng nhập được tài khoản seed

Pull code chỉ tải file SQL, không tự nạp vào database đang có. Backend và PostgreSQL cùng dùng `datvexe`; kiểm tra `POSTGRES_DB=datvexe` trong `.env`. Chạy loader `--activate` bên dưới để nạp và cấu hình đúng database. Nạp lại giữ nguyên tài khoản/mật khẩu hiện có.

Kiểm tra tài khoản đã có trên máy:

```powershell
docker exec datvexe-postgres psql -U datvexe_user -d datvexe -c "SELECT email, loai_tai_khoan, trang_thai FROM nguoi_dung WHERE email = 'tx1.futa@datvexe.test';"
```

Tài khoản cần có trạng thái `HOAT_DONG`. Nếu đã đổi mật khẩu thì dùng mật khẩu mới. Hồ sơ nhân viên và tài khoản đăng nhập là hai bản ghi khác nhau: tạo hồ sơ nhân viên không tự tạo tài khoản đăng nhập.

Tại thư mục gốc dự án, PowerShell:

```powershell
docker compose up -d postgres
python scripts/load_project_data.py --activate
python tests/backend/project_data_check.py --check-reload
```

Dùng tài khoản ổn định trong [danh sách đăng nhập](../../database/README.md#section-2): `admin1@datvexe.test`, `dieuhanh.futa@datvexe.test`, `tx1.futa@datvexe.test`. Mật khẩu khởi tạo `DatVeXe2026!`. Không cần file tài khoản tạm; không cleanup các tài khoản này.

Terminal khác:

```powershell
cd apps/frontend
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

Seed dự án: `database/seed/001_catalog.sql`, `002_accounts_staff_fleet.sql`, `003_routes_trips.sql`. Gồm 5 nhà xe, 35 nhân viên, 10 xe, 320 ghế, 20 tuyến và 210 chuyến mở bán trong 14 ngày. Database là `datvexe`. Các mã chuyến chứa ngày; chạy loader lại để bổ sung lịch mới, không dời lịch đã có.

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
