# T09 — Quản lý nhà xe và tài khoản quản lý

Triển khai trên nhánh `feat/t09-operator-management`. Các API dưới đây yêu cầu phiên `ADMIN`; POST/PUT/DELETE yêu cầu CSRF như [hợp đồng tài khoản](tai-khoan-va-xac-thuc.md). JSON dùng tên trường `camelCase`. Lỗi dùng `code`, `message`, `details`, `timestamp`, `traceId` và header `X-Trace-Id`.

## Hồ sơ nhà xe

| Method | Đường dẫn | Kết quả |
| --- | --- | --- |
| GET | `/api/quan-tri/nha-xe?q=&trangThai=&page=1&size=20` | 200, `{items,page,size,totalItems,totalPages}`; `size` tối đa 100 |
| POST | `/api/quan-tri/nha-xe` | 201, hồ sơ mới ở trạng thái `CHO_PHE_DUYET` |
| GET | `/api/quan-tri/nha-xe/{maNhaXe}` | 200, hồ sơ |
| PUT | `/api/quan-tri/nha-xe/{maNhaXe}` | 200, hồ sơ đã cập nhật; không đổi mã |
| DELETE | `/api/quan-tri/nha-xe/{maNhaXe}` | 204 nếu chưa có tài khoản, nhân viên, xe hoặc tuyến tham chiếu; ngược lại 409 `OPERATOR_IN_USE` |
| GET | `/api/quan-tri/nha-xe/{maNhaXe}/anh-huong` | 200, số chuyến, giữ chỗ và vé cần xem xét trước khi khóa |
| PUT | `/api/quan-tri/nha-xe/{maNhaXe}/trang-thai` | 200, `{nhaXe,anhHuong}` sau chuyển trạng thái |

POST nhận `maNhaXe` (bắt buộc, 1–40 ký tự ASCII gồm chữ, số, `_` hoặc `-`), `tenNhaXe` (bắt buộc, tối đa 160), `hotline` (30), `email` (254, hợp lệ), `diaChi` (255), `moTa` (1000), `logoUrl` (500), `ngayThanhLap` (ISO date, không ở tương lai). PUT nhận cùng trường trừ `maNhaXe` có thể bỏ qua; nếu gửi mã khác URL trả 400. Ví dụ:

```json
{"maNhaXe":"NX01","tenNhaXe":"Nhà xe Minh Hiếu","hotline":"0901234567","email":"contact@example.test","diaChi":"TP. Hồ Chí Minh","moTa":null,"logoUrl":null,"ngayThanhLap":null}
```

Trạng thái nhận một trong `CHO_PHE_DUYET`, `HOAT_DONG`, `TAM_NGUNG`, `NGUNG_HOAT_DONG`:

```json
{"trangThai":"TAM_NGUNG"}
```

Không xóa cứng nhà xe đã được tham chiếu; dùng trạng thái để ngừng hoạt động. Mã trùng trả `409 OPERATOR_ALREADY_EXISTS`. Mã không tồn tại trả 404. Nhóm đã xác nhận dùng đủ bốn trạng thái. `TAM_NGUNG` và `NGUNG_HOAT_DONG` ngăn thao tác ghi của quản lý nhà xe, tạo chuyến mới và hiển thị chuyến trong kết quả tìm kiếm công khai. Quyền đọc dữ liệu lịch sử của đúng nhà xe vẫn còn. Việc chuyển trạng thái không tự hủy chuyến, vé hoặc hoàn tiền. Admin có thể chuyển về `HOAT_DONG` sau khi xử lý vận hành.

`anhHuong` gồm `chuyenSapKhoiHanh`, `chuyenDangChay`, `giuChoDangGiu`, `veDaPhatHanh`, `veCoThanhToanThanhCong`. Đây là số liệu tại thời điểm truy vấn; số vé có thanh toán thành công được đếm theo giao dịch `THANH_CONG` của đơn. Frontend lấy số liệu trước khi xác nhận; API chuyển trạng thái tính lại trong giao dịch và trả số liệu sau thao tác. Admin phải chuyển các chuyến đang chạy, chuyến sắp khởi hành và vé đã thanh toán sang quy trình vận hành của nhóm. Không xem thao tác đổi trạng thái là thao tác hủy chuyến hoặc hoàn tiền.

## Tài khoản quản lý

| Method | Đường dẫn | Kết quả |
| --- | --- | --- |
| GET | `/api/quan-tri/nha-xe/{maNhaXe}/quan-ly` | 200, danh sách tài khoản `NHA_XE` thuộc nhà xe |
| POST | `/api/quan-tri/nha-xe/{maNhaXe}/quan-ly` | 201, cấp quyền cho tài khoản đang là `KHACH_HANG`, hoạt động và chưa gắn nhà xe/nhân viên |
| DELETE | `/api/quan-tri/nha-xe/{maNhaXe}/quan-ly/{maNguoiDung}` | 204, thu hồi quyền; tài khoản trở lại `KHACH_HANG` |

POST body: `{"maNguoiDung":"<id tài khoản>"}`. Mỗi tài khoản quản lý thuộc tối đa một nhà xe, một nhà xe có thể có nhiều tài khoản quản lý. Cấp/thu hồi quyền là giao dịch nguyên tử, thu hồi các phiên hiện có; người dùng đăng nhập lại để nhận quyền mới. API không nhận quyền từ đăng ký công khai. Xung đột vai trò hoặc trạng thái trả 409; thu hồi tài khoản không thuộc nhà xe trong URL trả 403. Hồ sơ và lịch sử đơn/vé của tài khoản không bị xóa khi thu hồi.

## Phạm vi và tích hợp

- `AccessControl` đối chiếu chủ sở hữu từ quan hệ trong database, không tin `maNhaXe` do client gửi. URL/body trỏ tuyến, xe, chuyến hoặc nhân viên nhà xe khác phải bị từ chối.
- Nhà xe tạm ngưng vẫn có thể đọc tài nguyên lịch sử của chính mình. Request ghi của vai trò `NHA_XE` bị từ chối. Tạo chuyến mới bị chặn cả khi Admin gọi API trên tuyến của nhà xe chưa hoạt động.
- Module bán vé/chốt đơn chưa có luồng hoàn chỉnh trong repository ở thời điểm T09. Chủ task đặt vé cần gọi kiểm tra trạng thái nhà xe **trong cùng giao dịch trước khi giữ chỗ/xuất vé**; không suy luận từ giao diện hoặc từ trạng thái tuyến đã tải trước đó.
- Tìm kiếm chuyến công khai loại chuyến của nhà xe không `HOAT_DONG`; tuyến danh mục chưa gắn nhà xe vẫn giữ hành vi tìm kiếm hiện có của T08. API đọc chi tiết chuyến vẫn phục vụ tra cứu lịch sử theo quyền; việc ẩn kết quả tìm kiếm không thay thế kiểm tra trong giao dịch đặt vé.
- `saleAllowed` trả `409 OPERATOR_UNASSIGNED` nếu chuyến chưa xác định nhà xe vận hành. Module đặt vé không được mở bán chuyến này dù nó còn xuất hiện trong kết quả tìm kiếm theo hợp đồng T08 hiện tại.
- Helper dùng chung `AccessControl.saleAllowed(em, maChuyen)` đã có cho bước tích hợp này; trạng thái bị chặn trả `409 OPERATOR_SUSPENDED`.
- Trang Admin: `/quan-tri/nha-xe`. Trang tài khoản có liên kết đến trang này. Frontend dùng phiên cookie và CSRF hiện có, không gửi `role`/`maNhaXe` để tự cấp quyền.
- Các thay đổi trạng thái, cấp và thu hồi quyền ghi audit log với actor, mã nhà xe, mã tài khoản (nếu có) và trace ID; không ghi token/mật khẩu.

## Kiểm thử nghiệm thu

1. Admin tạo/sửa/tìm/lọc nhà xe; mã trùng và dữ liệu không hợp lệ bị từ chối.
2. Khách hàng không gọi được API Admin.
3. Cấp quyền cho tài khoản hợp lệ, thu hồi phiên cũ; từ chối tài khoản đã gắn nhà xe khác hoặc vai trò khác.
4. Thu hồi quyền, phiên cũ hết hiệu lực và lịch sử tài khoản còn nguyên.
5. Nhà xe A không đọc/sửa được tài nguyên nhà xe B bằng cách đổi ID trong URL/body.
6. Tạm ngưng nhà xe: không mở chuyến mới hoặc ghi tài nguyên; vẫn đọc được lịch sử có quyền; vé/chuyến cũ không tự đổi trạng thái.
7. Sau khi module bán vé tích hợp, thử giữ chỗ/thanh toán/xuất vé ở ranh giới vừa khóa nhà xe để kiểm chứng không bán mới.
