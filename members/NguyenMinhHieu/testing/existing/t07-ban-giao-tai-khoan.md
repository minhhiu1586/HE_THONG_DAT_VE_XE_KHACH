# Bàn giao T07 và T07.1

Đăng ký, đăng nhập, đăng xuất, hồ sơ, đổi mật khẩu và quản trị khóa/mở khóa đã có API và giao diện dùng backend thật. Phiên lưu PostgreSQL, cookie HttpOnly/CSRF; đổi mật khẩu và trạng thái thu hồi phiên. Các service hiện có đã tích hợp guard vai trò và phạm vi nhà xe. Helper dùng chung kiểm tra chủ đơn/vé, nhà xe và phân công hiện tại trong transaction.

Tài liệu:

- [API tài khoản và cách chạy migration/bootstrap ADMIN](../../../../docs/api/tai-khoan-va-xac-thuc.md).
- [OpenAPI tài khoản](../../../../docs/api/account-openapi.yaml).
- [Hướng dẫn tích hợp T07.1 và chính sách vai trò](t07-1-tich-hop-phan-quyen.md).
- [Ma trận, bằng chứng kiểm thử và giới hạn nghiệm thu](t07-ma-tran-kiem-thu-tai-khoan.md).

Kiểm tra: 43 JUnit đạt, không skip; 139 phản hồi HTTP đạt (36 tài khoản + 75 tuyến/chuyến + 28 xe/phân công); Chromium đạt đăng ký/validation/đăng nhập/hồ sơ/phiên khóa/đổi mật khẩu/thông báo phiên khóa/quyền UI/khóa và mở khóa quản trị/đăng xuất. Frontend production build đạt; lint không có lỗi, một cảnh báo có sẵn trong mock-data.ts. Các script tự dọn fixture; database PostgreSQL test tách biệt.

Không có ma trận T01/T06 được phê duyệt trong repository để xác nhận chính sách cuối của nhóm. Các module đặt vé, hoàn tiền và kiểm vé phải tích hợp helper và kiểm thử API riêng; không triển khai thay API nghiệp vụ của các thành viên khác. Chưa tạo, review hoặc chấp nhận PR trong phiên này. Mã nguồn được bàn giao trên nhánh `feat/t07-account-auth-rbac`.

Gợi ý commit đơn giản:

```bash
git commit -m "Thêm tài khoản, đăng nhập, hồ sơ và phân quyền"
```

## Nội dung PR chuẩn bị sẵn

**Tiêu đề:** Thêm chức năng tài khoản và phân quyền T07

Người dùng có thể đăng ký tài khoản khách hàng, đăng nhập bằng phiên cookie, cập nhật hồ sơ và đổi mật khẩu. Quản trị viên có thể khóa/mở khóa tài khoản; khóa hoặc đổi mật khẩu khiến các phiên cũ không dùng được nữa.

Bổ sung guard dùng chung và helper kiểm tra chủ đơn/vé, phạm vi nhà xe, phân công nhân viên theo dữ liệu DB hiện tại. Tích hợp vào API tuyến/chuyến/xe/phân công, chuẩn hóa lỗi 401/403, thêm migration 003, giao diện tài khoản và tài liệu tích hợp. Không thêm API nghiệp vụ hoàn tiền/kiểm vé của module khác.

Validation: 43 JUnit, 139 phản hồi HTTP và kịch bản Chromium đạt; frontend build/lint đạt (một warning cũ). Cần chạy migration 003 trước WAR mới, cấu hình cookie Secure khi dùng HTTPS, và đối chiếu vai trò T01 trước merge. Helper phạm vi phải được gọi trong transaction của API nghiệp vụ khi các thành viên tích hợp.
