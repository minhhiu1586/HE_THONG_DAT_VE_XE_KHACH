# Rà soát yêu cầu T07 và T07.1 — 11/10/2026

Đối chiếu trực tiếp nội dung T07 người dùng cung cấp, T07.1 trong chat, mã nguồn, schema, DTO và các bài kiểm thử. Lần rà soát này bổ sung kiểm tra ID dạng số, trạng thái đặc quyền trong hồ sơ, phân công thay đổi đồng thời, thông báo phiên không hợp lệ, đổi mật khẩu và khóa/mở khóa qua giao diện.

## T07

| Mục yêu cầu | Triển khai và bằng chứng |
| --- | --- |
| 2.1 Đăng ký | DTO riêng; required/format/unique; PBKDF2 salt riêng; chỉ KHACH_HANG; unit, HTTP, browser |
| 2.2 Đăng nhập | Kiểm tra mật khẩu/trạng thái, thông báo chung; phiên DB/cookie HttpOnly; không trả mật khẩu/hash/token cookie trong JSON; unit, HTTP, browser |
| 2.3 Đăng xuất | Xóa phiên/cookie, chống replay; HTTP và browser |
| 2.4 Hồ sơ | Danh tính lấy từ phiên; PUT chính mình; trùng định danh bị từ chối; role/trangThai/userId bị từ chối; unit, HTTP, lưu/tải lại UI |
| 2.5 Đổi mật khẩu | Mật khẩu hiện tại + policy mới + xác nhận UI; băm mới; thu hồi tất cả phiên; unit kiểm tra hai phiên, HTTP, UI |
| 2.6 Khóa/mở khóa | ADMIN thật từ DB; không tự khóa; thu hồi tất cả phiên đích; audit actor/target/status/trace; unit, HTTP, thao tác UI thật |
| 2.7 Phiên hết hạn | Chưa đăng nhập/token giả/hết hạn/logout/khóa đều từ chối 401; có thông báo/điều hướng frontend; unit, HTTP, browser |
| 2.8 Tích hợp T07.1 | Dùng chung AccessControl/RequestIdentity/ApiErrors, đọc lại role và trạng thái trong transaction; test giả role/scope/session |
| 2.9 Kiến trúc backend/T05 | Entity, Repository, Service, Servlet, DTO; lỗi JSON 401/403 và traceId; OpenAPI bổ sung tài khoản/CSRF; WAR/PostgreSQL validate đạt |
| 2.10 Giao diện | Đăng ký/đăng nhập/hồ sơ/password/quản trị; required, lỗi/success, busy/ref chặn submit trùng; Chromium thao tác các luồng chính |
| 2.11 Thành phần dùng chung | Context xác thực, helper scope, AuthProvider/accountRequest, cookie/CSRF, hướng dẫn 401/403 và bảo vệ trang; tài liệu tích hợp |

## T07.1

| Mục yêu cầu | Triển khai và bằng chứng |
| --- | --- |
| 2.1 Ma trận quyền | Bảng chính sách endpoint trong hướng dẫn; dựa vai trò/schema/contract hiện có. T01 đã duyệt không có trong repo nên chưa thể xác nhận khớp bản chính thức |
| 2.2 Người dùng hiện tại | Filter xác thực cookie qua AccountService; backend DB làm nguồn danh tính/role/trạng thái/phạm vi; không tin userId/role/maNhaXe từ request |
| 2.3 Guard vai trò | Filter + guard trong transaction service; endpoint công khai khai báo rõ; API riêng yêu cầu phiên; unit/HTTP gọi trực tiếp |
| 2.4 Chủ sở hữu | profileOwner/orderOwner/ticketOwner theo quan hệ DB; test KH1 đối với đơn/vé KH1 và KH2 |
| 2.5 Nhà xe | route/trip/vehicle/employee/operator, kiểm tra cả URL và quan hệ body; list lọc scope hiện tại; unit và HTTP hai nhà xe |
| 2.6 Phân công | assigned kiểm tra role/nhân viên đang làm/nhà xe/chuyến/phân công DB; test không được phân công, thu hồi, tạm nghỉ, đổi phân công |
| 2.7 Phản hồi | 401 chưa xác thực, 403 thiếu quyền; cấu trúc T05 + header trace; lỗi không trả dữ liệu tài nguyên ngoài phạm vi; missing resource 404 theo tài liệu |
| 2.8 Helper chung | AccessControl, AccessPolicy, RequestIdentity, ApiErrors; hướng dẫn cùng transaction, không cache quyền |
| 2.9 Tích hợp tài khoản | Hồ sơ/password/quản trị đều xác thực lại session/role/state trong transaction; phiên bị khóa/thu hồi bị từ chối |
| 2.10 Hỗ trợ module | Có hướng dẫn và ví dụ cho tuyến/chuyến/giao dịch/đơn/vé/nhân viên/kiểm vé; việc các thành viên đã tích hợp module tương lai chưa được xác nhận |
| 2.11 Ma trận kiểm thử | t07-ma-tran-kiem-thu-tai-khoan.md có vai trò, tài nguyên, điều kiện, kết quả/HTTP/code, nơi tìm bằng chứng; dùng seed hai nhà xe và nhân viên của repo |
| 2.12 Vượt quyền/đồng thời | ID/body/role giả, scope đổi sau filter, phiên cũ, khóa tài khoản, thu hồi và đổi phân công; PostgreSQL kiểm tra khóa transaction và từ chối thao tác sau thu hồi |

## Những lỗi/chỗ thiếu đã sửa trong lần rà soát

1. Guard bỏ qua ID JSON không phải chuỗi trong khi DTO có thể tự đổi số thành String. Nay trả 400 trước xử lý, ID chuỗi của tài nguyên ngoài scope vẫn trả403; có unit và HTTP hồi quy.
2. Sau refresh bản ghi phân công được khóa, đối chiếu lại nhân viên/chuyến để không dùng quan hệ đã bị đổi giữa lúc đọc và khóa. Bổ sung test PostgreSQL đổi phân công đồng thời.
3. Khi tải lại trang riêng hoặc refresh một phiên đang dùng bị thu hồi, frontend nay có thông báo hướng dẫn đăng nhập; bổ sung assertion trên trình duyệt.
4. Script browser cũ chỉ kiểm tra trang quản trị hiển thị, chưa thao tác khóa/mở khóa và đổi mật khẩu. Nay kiểm tra đúng thao tác UI và phản hồi API/phiên phía backend.

## Kết quả và giới hạn

- Maven verify: **47 test đạt, 0 failures, 0 errors, 0 skipped**. Trong đó 27 test thuộc tài khoản/phân quyền; 20 test hồi quy module tuyến/xe. Có 9 test PostgreSQL thực, gồm 3 tình huống cạnh tranh quyền/phân công.
- HTTP tài khoản/phân quyền: **36** phản hồi; hồi quy tuyến/chuyến: **75**; xe/phân công: **28**. Tổng **139**.
- Chromium: đăng ký/validation/đăng nhập, hồ sơ lưu và tải lại, password sai/xác nhận sai/đổi thành công, phiên cũ 401, khóa khi còn phiên có notice/redirect, thiếu quyền UI, ADMIN khóa/mở khóa, cấm tự khóa và logout.
- Frontend production build đạt; lint **0 errors**, 3 warning có sẵn ở api.ts và mock-data.ts. Migration003 chạy lại trên database test thành công. Các tham chiếu hai file OpenAPI được kiểm tra và resolve thành công.

Đây là bằng chứng cho module đã có và helper dùng chung. API hoàn tiền/kiểm vé nghiệp vụ chưa có trong nhánh: test ở mức helper, không khẳng định end-to-end module tương lai. SRS/use-case hiện là README khung, chưa có T01 được duyệt; đã đối chiếu quyền tài khoản và quan hệ sở hữu/phạm vi trên dữ liệu mới trong repo. PR chưa được review/chấp nhận; các điểm này không thể xác nhận chỉ bằng test tự động.

## Kiểm tra lại T07/T07.1 sau cập nhật database

Kiểm tra trên nhánh `feat/t07-account-auth-rbac`, nền `develop` tại `4072cc1`, ngày 11/10/2026, dùng database riêng để bảo vệ dữ liệu ứng dụng.

- Maven verify đạt47/47, không skip. Bổ sung4 test phân quyền trên dữ liệu mới: tài khoản bị khóa dù có phiên; quyền sở hữu đơn/vé; phạm vi nhà xe của tuyến/chuyến/xe/nhân viên; phân công và thu hồi phân công. Các cập nhật trong nhóm này đều rollback.
- API tài khoản/xác thực đạt36 kiểm tra. Kiểm tra hồi quy các API đã tích hợp guard đạt103 kiểm tra, tổng139.
- Chromium kiểm tra thành công đăng ký, đăng nhập, hồ sơ, đổi mật khẩu, phiên bị khóa, thiếu quyền và ADMIN khóa/mở khóa. Script đã hỗ trợ tìm tài khoản qua nhiều trang.
- Script HTTP và browser hỗ trợ `DB_NAME` để fixture SQL sử dụng đúng database của backend, mặc định `datvexe`. Biến này không tự thay đổi cấu hình database của backend.
- Build frontend đạt; lint0 lỗi/3 warning có sẵn. Không phát hiện lỗi ứng dụng do thay đổi database lần này.

Hướng dẫn VS Code chỉ bao gồm thiết lập và kiểm tra T07/T07.1, không bổ sung quy trình chạy nhiệm vụ khác. Kiểm tra hoàn tiền/kiểm vé hiện ở mức helper phân quyền vì API nghiệp vụ tương ứng chưa triển khai.
