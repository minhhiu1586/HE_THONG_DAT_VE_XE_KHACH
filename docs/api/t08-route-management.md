# API quản lý tỉnh thành, bến xe, tuyến và điểm dừng (T08)

Các endpoint dùng tiền tố `/api`, JSON UTF-8 và cấu trúc lỗi chung T05 (kèm `X-Trace-Id`). Backend kiểm tra vai trò, phiên, CSRF và phạm vi nhà xe theo T07/T07.1. ID trong request phải là chuỗi.

## Tỉnh/thành phố

| Method | Đường dẫn | Quyền | Nội dung |
| --- | --- | --- | --- |
| GET | `/tinh-thanh?q=` | Công khai | Danh sách; tìm theo mã hoặc tên |
| GET | `/tinh-thanh/{maTinhThanh}` | Công khai | Chi tiết |
| POST | `/tinh-thanh` | ADMIN | `{maTinhThanh, tenTinhThanh, khuVuc}`; trả 201 |
| PUT | `/tinh-thanh/{maTinhThanh}` | ADMIN | `{tenTinhThanh, khuVuc}` |
| DELETE | `/tinh-thanh/{maTinhThanh}` | ADMIN | Chỉ xóa khi chưa có bến tham chiếu; nếu không trả 409 |

Mô hình hiện có không lưu trạng thái tỉnh/thành phố; không tự thêm trạng thái vào dữ liệu T03.

## Bến xe

| Method | Đường dẫn | Quyền | Nội dung |
| --- | --- | --- | --- |
| GET | `/ben-xe?maTinhThanh=&q=&trangThai=` | Công khai | Lọc theo tỉnh, mã/tên và trạng thái |
| GET | `/ben-xe/{maBenXe}` | Công khai | Chi tiết |
| POST | `/ben-xe` | ADMIN | `{maBenXe, tenBenXe, diaChi, soDienThoai?, maTinhThanh, trangThai?}`; trả 201 |
| PUT | `/ben-xe/{maBenXe}` | ADMIN | Cập nhật thông tin/trạng thái |
| DELETE | `/ben-xe/{maBenXe}` | ADMIN | Xóa khi chưa được tuyến tham chiếu; nếu không trả 409 |

Bến mới phải tham chiếu tỉnh tồn tại. Tên, địa chỉ và tỉnh của bến đã được chuyến sử dụng không thể đổi để giữ lịch sử; trạng thái và số điện thoại vẫn có thể cập nhật.

## Tuyến xe và điểm dừng

| Method | Đường dẫn | Quyền | Nội dung |
| --- | --- | --- | --- |
| GET | `/tuyen-xe?tramKhoiHanh=&tramDen=&maNhaXe=&trangThai=` | ADMIN/NHA_XE | Lọc danh sách; nhà xe luôn bị giới hạn vào phạm vi của chính mình |
| GET | `/tuyen-xe/{maTuyen}` | ADMIN/NHA_XE | Chi tiết tuyến kèm điểm dừng đúng thứ tự |
| POST | `/tuyen-xe` | ADMIN/NHA_XE | `{maTuyen, tenTuyen, tramKhoiHanh, tramDen, thoiGianKhoiHanh?, khoangCachKM?, thoiGianDuKien, giaCoBan, moTa?, trangThai?, maNhaXe?}`; trả 201 |
| PUT | `/tuyen-xe/{maTuyen}` | ADMIN/NHA_XE | Cập nhật cấu hình trước khi tuyến có chuyến |
| PUT | `/tuyen-xe/{maTuyen}/trang-thai` | ADMIN/NHA_XE | `{trangThai}`; bật khai thác yêu cầu thời lượng hợp lệ và hai bến đang hoạt động |
| DELETE | `/tuyen-xe/{maTuyen}` | ADMIN/NHA_XE | Chỉ xóa khi chưa có chuyến; nếu không trả 409 |
| POST | `/tuyen-xe/{maTuyen}/diem-dung` | ADMIN/NHA_XE | `{maDiemDung, tenDiemDung, diaChi, thuTu?, thoiGianDung, loaiDiemDung?}` |
| PUT | `/tuyen-xe/{maTuyen}/diem-dung/{maDiemDung}` | ADMIN/NHA_XE | `{tenDiemDung, diaChi, thoiGianDung, loaiDiemDung?}` |
| PUT | `/tuyen-xe/{maTuyen}/diem-dung/sap-xep` | ADMIN/NHA_XE | `{maDiemDungTheoThuTu: [...]}` phải chứa đủ ID duy nhất của tuyến |
| DELETE | `/tuyen-xe/{maTuyen}/diem-dung/{maDiemDung}` | ADMIN/NHA_XE | Xóa điểm rồi đánh lại thứ tự trong cùng giao dịch |

Bến đi/đến phải khác nhau, tồn tại và đang hoạt động khi tạo/cấu hình tuyến. Tuyến cần thời lượng dự kiến dương; giờ mặc định trên tuyến tách khỏi giờ khởi hành từng chuyến. Tuyến không có điểm dừng vẫn hợp lệ. Thời gian dừng không âm, thứ tự là 1..n. Thêm điểm dừng tại thứ tự đã dùng sẽ chèn điểm và dời thứ tự hiện hữu; endpoint sắp xếp yêu cầu danh sách đầy đủ, không trùng. Khi tuyến đã có chuyến, không cho sửa cấu hình, điểm dừng hay xóa tuyến; vẫn có thể đổi trạng thái. Chuyến hiện hữu được giữ nguyên khi tuyến ngừng khai thác; không thể mở chuyến mới nếu tuyến hoặc bến không hoạt động.

Nhà xe tạo tuyến được gán phạm vi từ phiên đã xác thực trong backend; `maNhaXe` do client gửi không thể đổi quyền sở hữu. Các thao tác nhắm tới tuyến ngoài phạm vi trả 403. Người chưa đăng nhập nhận 401.

## Giao diện

Đăng nhập bằng ADMIN hoặc NHA_XE, mở `/quan-tri/tuyen-duong`. ADMIN có thể quản lý tỉnh, bến và tuyến. NHA_XE chỉ có phần tuyến/điểm dừng thuộc nhà xe của mình.
