# T07 / T07.1 — Ma trận kiểm thử

Các fixture tạo tên/ID riêng mỗi lượt và được dọn trong finally. Không dùng tài khoản thật. H2 kiểm tra service và helper; PostgreSQL kiểm tra schema, khóa và tranh chấp; HTTP kiểm tra cookie/filter/controller; Chromium kiểm tra giao diện. Các mã dưới đây theo hợp đồng account-auth.md. Ma trận T01/T06 chính thức chưa có trong repository: cần nhóm đối chiếu khi review.

| Ca | Vai trò / điều kiện | Thao tác / tài nguyên | Kết quả / HTTP / mã | Bằng chứng tự động |
| --- | --- | --- | --- | --- |
| A01 | Chưa đăng nhập | Đăng ký dữ liệu hợp lệ | KHACH_HANG/HOAT_DONG, không lộ mật khẩu / 201 | AccountServiceTest, account_auth_smoke.py |
| A02 | Chưa đăng nhập | Trùng email/điện thoại/CCCD | Từ chối / 409 ACCOUNT_ALREADY_EXISTS | AccountServiceTest |
| A03 | Chưa đăng nhập | Thiếu/sai trường, mật khẩu yếu | 400 VALIDATION_ERROR | AccountServiceTest, HTTP |
| A04 | Chưa đăng nhập | Gửi role/loaiTaiKhoan khi đăng ký | Không nâng quyền / 400 VALIDATION_ERROR | HTTP |
| A05 | Tài khoản hoạt động | Đăng nhập đúng | Phiên cookie HttpOnly/SameSite / 200 | AccountServiceTest, HTTP, browser |
| A06 | Sai mật khẩu / khóa / chờ xác minh | Đăng nhập | Thông báo chung / 401 UNAUTHENTICATED | AccountServiceTest, HTTP |
| A07 | KH có phiên | Đăng xuất rồi dùng lại cookie cũ | 204 rồi 401 UNAUTHENTICATED | AccountServiceTest, HTTP |
| A08 | Phiên hết hạn | GET /tai-khoan/me | 401 UNAUTHENTICATED | AccountServiceTest clock, HTTP DB expiry |
| A09 | KH chủ hồ sơ | PUT /tai-khoan/me | Hồ sơ hợp lệ cập nhật / 200 | AccountServiceTest, HTTP, browser |
| A10 | KH | Đổi ID hồ sơ sang người khác | 403 FORBIDDEN | AccessControlTest, HTTP |
| A11 | KH | PUT hồ sơ kèm role/trạng thái | 400 VALIDATION_ERROR, không tăng quyền | HTTP |
| A12 | KH | Đổi mật khẩu đúng hiện tại | 204, mọi phiên cũ 401 | AccountServiceTest, HTTP |
| A13 | KH | Đổi mật khẩu sai hiện tại | 400 VALIDATION_ERROR | AccountServiceTest, HTTP |
| A14 | KH đang có phiên | ADMIN khóa rồi mở khóa | 200; phiên cũ 401; phải đăng nhập mới | AccountServiceTest, HTTP |
| A15 | KH/NX/NV | API quản trị tài khoản trực tiếp | 403 FORBIDDEN | AccountServiceTest, HTTP |
| A16 | ADMIN | Khóa chính mình | 409 INVALID_STATE_TRANSITION | AccountServiceTest |
| A17 | Chưa đăng nhập / hết phiên | API riêng /xe hoặc hồ sơ | 401 UNAUTHENTICATED | HTTP |
| A18 | KH có phiên | Mutation không có/sai CSRF | 403 FORBIDDEN | HTTP |
| P01 | KH1 chủ O1/V1 | Xem đơn/vé, helper trước hoàn tiền | Cho phép (helper không trả HTTP) | AccessControlTest |
| P02 | KH1; O2/V2 thuộc KH2 | Đổi orderId/ticketId | 403 FORBIDDEN | AccessControlTest |
| P03 | NX A | Tuyến/chuyến/xe thuộc A | Cho phép helper; list chỉ A | AccessControlTest, HTTP |
| P04 | NX A | URL chuyến/xe thuộc B / body xe thuộc B | 403 FORBIDDEN, không ghi dữ liệu | AccessControlTest, HTTP |
| P05 | NX A | POST tuyến khai maNhaXe B | 403 FORBIDDEN | AccessControlTest |
| P06 | NX đã chuyển A→B sau filter | Danh sách tuyến/xe | Chỉ trả B theo DB hiện tại | AccessControlTest, HTTP thay phạm vi phiên còn hiệu lực |
| P07 | NV đang làm, phân công chuyến A | Kiểm vé qua assigned/ticketAccess | Cho phép | AccessControlTest |
| P08 | NV, chuyến B không phân công | Kiểm vé qua helper | 403 FORBIDDEN | AccessControlTest |
| P09 | NV đã thu hồi phân công/tạm nghỉ | Dùng danh tính phiên cũ | 403 FORBIDDEN | AccessControlTest |
| P10 | Đã xác thực KH, principal giả ADMIN | role helper | 403 FORBIDDEN theo DB | AccessControlTest |
| P11 | Phiên vừa thu hồi | protected helper | 401 UNAUTHENTICATED | AccessControlTest |
| P12 | Thao tác đã giữ khóa tài khoản | Request khóa đồng thời | Chờ transaction cũ; request tiếp theo 401 | PostgresAccessControlTest |
| P13 | Thao tác đã giữ khóa phân công | Thu hồi phân công đồng thời | Chờ transaction cũ; request tiếp theo 403 | PostgresAccessControlTest |
| P15 | NHA_XE | ID tài nguyên JSON dạng số thay vì chuỗi | 400 VALIDATION_ERROR; ID chuỗi của nhà xe khác 403, không ghi dữ liệu | AccessControlTest, HTTP |
| P16 | NV có quyền trên chuyến cũ | Đổi phân công đồng thời sang chuyến khác | Thao tác sau thay đổi không được dùng phân công cũ / 403 | PostgresAccessControlTest |
| P14 | Bị từ chối | Lỗi HTTP | Đủ code/message/details/timestamp/traceId, không dữ liệu riêng | account_auth_smoke.py |
| F01 | Chưa đăng nhập | Mở trang hồ sơ | Chuyển đăng nhập có next | account_smoke.cjs |
| F02 | Đăng ký / đăng nhập | Sai xác nhận/sai mật khẩu | Hiện lỗi; nhập đúng thành công | account_smoke.cjs |
| F03 | KH | Lưu hồ sơ rồi tải lại | Hiển thị dữ liệu đã lưu | account_smoke.cjs |
| F04 | KH khóa khi còn cookie | Mở lại hồ sơ | 401, kết thúc trạng thái, chuyển đăng nhập và hiện thông báo | account_smoke.cjs |
| F06 | KH đã đăng nhập | Đổi mật khẩu qua giao diện | Xác nhận sai/hiện tại sai bị từ chối; đúng thành công và phiên cũ 401 | account_smoke.cjs |
| F07 | ADMIN | Khóa/mở khóa từ UI | Khóa thu hồi phiên đích; mở khóa cho đăng nhập mới; không tự khóa mình | account_smoke.cjs |
| F05 | KH / ADMIN | Trang quản trị | KH thông báo thiếu quyền; ADMIN xem được | account_smoke.cjs |
| R01 | ADMIN fixture | API tuyến/bến/chuyến hiện có | 75 phản hồi hợp lệ/validation/404/409 | api_smoke.py |
| R02 | ADMIN fixture | API loại xe/xe/ghế/phân công | 28 phản hồi CRUD/eligibility | vehicle_api_smoke.py |

P01/P02/P07/P08 xác minh helper trong transaction; chưa có API hoàn tiền/kiểm vé nghiệp vụ trong nhánh để nghiệm thu end-to-end các module đó. Thành viên phụ trách phải tích hợp helper và bổ sung test API khi triển khai. Không coi việc UI ẩn chức năng là kiểm tra phân quyền.

## Bằng chứng chạy tại môi trường cloud

Ngày 11/10/2026, Java 21 / PostgreSQL 16 / Tomcat 10.1 / Next.js 16.3.8:

- Maven verify: 43 test, 0 thất bại, 0 lỗi, 0 bỏ qua; gồm 5 test PostgreSQL (2 về xe/phân công, 3 tranh chấp khóa, thu hồi hoặc đổi phân công).
- HTTP tài khoản: 36 phản hồi; hồi quy tuyến/chuyến: 75; xe/phân công: 28.
- Frontend production build thành công; lint không có lỗi, còn một cảnh báo có sẵn ở `lib/mock-data.ts` về tham số `date` chưa dùng.
- Script Chromium `tests/frontend/account_smoke.cjs` kiểm tra giao diện trên frontend thật, cookie qua Next rewrite và backend thật. Đã đạt đăng ký, validation, đăng nhập, lưu/tải lại hồ sơ, chuyển hướng phiên bị khóa, quyền UI, trang quản trị và đăng xuất.

Các lệnh tái lập nằm trong `tests/backend/README.md`. Không đưa log chứa token/mật khẩu vào repository. API chuẩn và ma trận hướng dẫn chưa thay thế việc nhóm duyệt T01, review PR, hoặc xác nhận các module tương lai đã tích hợp.
