# T12 — Quản lý chuyến và điều kiện mở bán

## Quy tắc đã chốt

Giữ nguyên thuộc tính Entity và cột database. Chỉ bổ sung `NHAP`, `DANG_MO_BAN` vào `TrangThaiChuyen`, theo chấp thuận của Thanh Hiền. `CHUA_KHOI_HANH` cũ được xử lý như chuyến chưa mở bán; seed không tự động được công bố bán.

Giá vé lấy từ `TuyenXe.giaCoBan`, không có giá riêng trên chuyến. Giá phải là VND nguyên, không âm, tối đa 12 chữ số; response giá là chuỗi decimal. Không thay đổi giá tuyến khi sửa chuyến.

Mặc định ngừng nhận vé **60 phút trước khởi hành**, cấu hình `TRIP_BOOKING_CUTOFF_MINUTES`. Đây là lựa chọn cấu hình của dự án, chưa xác minh một mức chung từ các website. Đồng hồ backend theo `Asia/Ho_Chi_Minh`; tại đúng hạn đã bị từ chối. Các thời điểm response có offset `+07:00`.

Dùng nguyên quy tắc T11: vai trò, hạn bằng, lịch, nối bến, xác nhận kế hoạch lái/nghỉ; không kiểm tra hạng bằng hoặc áp đệm nghỉ cố định cho nhân viên. Đệm chuẩn bị xe dùng cấu hình T11. Chuyến nháp không chiếm lịch khai thác xe; chuyến mở bán và đang chạy có chiếm lịch. Phân công nhân viên vẫn tuân thủ T11.

## API quản lý

Root `/api/quan-ly/chuyen-xe`, dùng session/CSRF và guard T07.1; ADMIN hoặc NHA_XE trong phạm vi sở hữu thực tế. Không đăng nhập 401, thiếu quyền hoặc khác nhà xe 403.

| Method | Path tương đối | Nội dung |
| --- | --- | --- |
| GET | `/` | Danh sách phân trang, lọc `maNhaXe`, `maTuyen`, `maXe`, `tuNgay`, `denNgay`, `trangThai`, `page`, `size` |
| POST | `/` | Tạo nháp |
| GET | `/{maChuyen}` | Chi tiết, nhân viên và quyền thao tác |
| PUT | `/{maChuyen}` | Sửa chuyến chưa mở bán |
| DELETE | `/{maChuyen}` | Xóa khi không có dữ liệu đặt vé tham chiếu |
| GET | `/{maChuyen}/dieu-kien-mo-ban` | Kiểm tra tám nhóm điều kiện |
| POST | `/{maChuyen}/mo-ban` | Kiểm tra lại và mở bán trong giao dịch |
| POST | `/{maChuyen}/huy` | Hủy khi không có giữ chỗ/vé cần xử lý |

Body tạo/sửa: `maChuyen`, `maTuyen`, `maXe` (có thể chưa chọn khi nháp), `ngayKhoiHanh` YYYY-MM-DD, `gioKhoiHanh` HH:mm, `gioDenDuKien` datetime đầy đủ, `giaVeDuKien` tùy chọn phải bằng giá tuyến. Khi bỏ giờ đến, backend tính từ thời lượng tuyến, hỗ trợ qua đêm. Body không nhận trạng thái tự mở bán hoặc thuộc tính ngoài DTO.

Chi tiết có `ngungNhanDatVeLuc`, `serverTime`, `phutDungNhanDatVe`, `daHetHanDatVe`, `coTheDatVe`, các cờ cho phép sửa/xóa/hủy. Không coi enum mở bán là đủ điều kiện nhận vé.

Report: `duDieuKien`, thời hạn/serverTime, `dieuKien` gồm `maDieuKien`, `tenDieuKien`, `dat`, `lyDo`. Nhóm: TRANG_THAI, HAN_DAT_VE, LICH_TRINH, TUYEN_BEN, NHA_XE, XE_DANG_KIEM, LICH_XE, NHAN_SU. Lỗi nghiệp vụ dùng cơ chế API lỗi chung; dữ liệu đầu vào sai 400, tài nguyên không tồn tại 404, xung đột nghiệp vụ 409.

## Tích hợp và bảo vệ dữ liệu

T12 khóa nhà xe/chuyến trong giao dịch, tái kiểm tra xe và phân công T11 khi mở bán. Hai yêu cầu mở bán xung đột cùng xe chỉ một yêu cầu thành công. Mở bán lại trong hạn không sinh bản ghi trùng. Kiểm tra thời hạn lại ngay trước ghi trạng thái.

Không đổi xe của chuyến đã mở bán; không đổi xe/sơ đồ khi ảnh hưởng giữ chỗ hoặc vé. Không sửa lịch gây xung đột, không xóa chuyến có đơn/vé tham chiếu. Không tự hủy vé hoặc chuyển ghế cho khách.

Public API tìm chuyến chỉ trả chuyến `DANG_MO_BAN` còn đủ điều kiện và chưa hết hạn. Chi tiết lịch sử vẫn có thể đọc, nhưng `coTheDatVe=false` khi không được đặt. Không công bố phân công nhân viên riêng tư trên public response.

Module giữ ghế/đặt vé phải gọi `AccessControl.saleAllowed` và `VehicleRules.seatSellable` trong giao dịch ghi, theo đồng hồ backend; không tin lựa chọn hoặc cờ frontend. T12 chưa triển khai toàn bộ giữ ghế, thanh toán hay mua vé; kiểm tra helper không thay thế nghiệm thu các API đó. Trang tìm vé hiện có còn dùng dữ liệu mock thuộc công việc tích hợp tiếp theo.

Giao diện quản lý: `/quan-tri/chuyen-xe`, menu Quản lý và thanh bên. T11 phân công được tích hợp qua API hiện có; không thêm module giao diện T11 ngoài phạm vi. Trang chủ và tìm chuyến đọc dữ liệu trực tiếp từ API/database, theo [T13](t13-trip-search-availability.md); frontend không dùng danh sách chuyến hoặc địa điểm mẫu.
