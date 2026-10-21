# Luồng demo tài khoản, phân quyền và nhà xe

Chạy ứng dụng từ thư mục gốc theo [README](../../../README.md). Source chính thức và danh sách file nằm trong [README của phần này](../README.md). URL API, DTO gửi/nhận, session cookie, CSRF và cách lưu mật khẩu được giữ nguyên khi refactor.

<a id="section-1"></a>

## 1. Đăng ký, đăng nhập và phân quyền

### Thứ tự mở file

1. `frontend/src/account/components/AuthForm.tsx`: chọn giao diện đăng nhập hoặc đăng ký.
2. `frontend/src/account/components/LoginForm.tsx`: nhận email/mật khẩu, gửi yêu cầu, nhận session rồi chuyển trang.
3. `frontend/src/account/lib/auth-api.ts`: hàm `loginAccount()` và `registerAccount()`.
4. `frontend/src/account/lib/account-api.ts`: gửi HTTP JSON, header, cookie và chuyển lỗi thành `ApiError`.
5. `backend/src/main/java/com/datvexe/security/AuthFilter.java`: kiểm tra request, origin, phiên và CSRF trước khi vào Controller.
6. `backend/src/main/java/com/datvexe/controller/AuthController.java`: chọn handler đăng ký, đăng nhập hoặc đăng xuất.
7. `backend/src/main/java/com/datvexe/service/AuthService.java`: xử lý nghiệp vụ xác thực.
8. `backend/src/main/java/com/datvexe/repository/NguoiDungRepository.java`: tìm tài khoản theo email và khóa bản ghi trong transaction.
9. `backend/src/main/java/com/datvexe/security/Passwords.java`: đối chiếu mật khẩu với hash PBKDF2.
10. `backend/src/main/java/com/datvexe/repository/AuthSessionRepository.java`: lưu, đọc và thu hồi phiên.
11. `frontend/src/account/components/AuthProvider.tsx`: lưu hồ sơ/CSRF trong bộ nhớ và khôi phục phiên khi tải trang.

### Diễn giải đăng nhập

```text
LoginForm.submit
  → loginAccount
  → accountRequest(POST /auth/dang-nhap)
  → Next.js chuyển /api sang Backend
  → AuthFilter
  → AuthController.login
  → AuthService.login
  → NguoiDungRepository.byEmail / lock
  → Passwords.matches
  → SessionTokens.create / hash
  → AuthSessionRepository.save
  → AuthCookies.write + AccountResponses.Session
  → LoginForm nhận session
  → AuthProvider.accept
  → loginDestination
```

`await loginAccount(...)` tạm dừng phần xử lý trong hàm `submit` tới khi request hoàn tất. Giá trị trả về là session JSON gồm `nguoiDung`, `csrfToken`, `hetHan`. Cookie phiên do Backend đặt bằng `Set-Cookie`; token thô không nằm trong JSON trả về. Khi request bị từ chối, `accountRequest` ném lỗi và `LoginForm` hiển thị thông báo.

`AuthService.login()` kiểm tra tài khoản và mật khẩu, dọn phiên hết hạn, tạo token ngẫu nhiên, lưu hash token và CSRF vào phiên. `AuthCookies` đặt cookie HttpOnly. `AccountTransactions` bao transaction; lỗi làm rollback trước khi đóng EntityManager.

### Khôi phục phiên và phân quyền

Khi F5, `AuthProvider` gọi `GET /tai-khoan/me`. `AuthFilter` đọc cookie qua `AuthCookies.token()`, gọi `AuthService.authenticate()` và gắn `AccountResponses.Identity` vào request. Service đọc phiên, kiểm tra hạn, đọc lại trạng thái tài khoản sau khi khóa và xác nhận phiên chưa bị thu hồi.

Quyền tại endpoint do `shared/backend/src/main/java/com/datvexe/security/AccessPolicy.java` kiểm tra. Các helper tại `AccessControl.java` kiểm tra tiếp vai trò, tài nguyên và nhà xe trong transaction. Điều hướng Frontend không thay thế các kiểm tra Backend.

### Đăng ký

`RegisterForm.submit()` kiểm tra hai mật khẩu giống nhau, gọi `registerAccount()`, rồi hiển thị thành công. `AuthService.register()` dùng `AccountValidation`, kiểm tra trùng qua Repository, hash mật khẩu và chỉ tạo vai trò khách hàng. Client không được tự gán vai trò hoặc trạng thái.

### Hồ sơ, đổi mật khẩu và quản trị

| Luồng | File và hàm chính |
|---|---|
| Xem/sửa hồ sơ | `ProfilePanel.saveProfile` → `AccountController.update` → `AccountService.update` |
| Đổi mật khẩu | `ChangePasswordForm` → `ProfilePanel.changePassword` → `AccountController.changePassword` → `AccountService.changePassword` |
| Khóa/mở tài khoản | `AdminAccounts` → `AdminAccountController.status` → `AdminAccountService.status` |
| Đăng xuất | `AuthController.logout` → `AuthService.logout` → `AuthSessionRepository.delete` |

Đổi mật khẩu và đổi trạng thái tài khoản thu hồi các phiên liên quan. Đăng xuất chỉ thu hồi phiên hiện tại. Kiểm tra số lần thử đăng ký/đăng nhập được tách vào `LoginRateLimiter`.

### Kịch bản demo

1. Đăng nhập khách thành công, mở hồ sơ và nhấn F5 để chứng minh khôi phục phiên.
2. Đăng nhập sai mật khẩu để xem lỗi mà không tiết lộ tài khoản có tồn tại hay không.
3. Thử truy cập quản trị bằng khách để chứng minh kiểm tra quyền ở Backend.
4. Đổi mật khẩu, xác nhận phiên cũ hết hiệu lực và đăng nhập bằng mật khẩu mới.
5. Đăng nhập admin, khóa một tài khoản khác và chứng minh phiên của tài khoản đó bị thu hồi.

<a id="section-2"></a>

## 2. Nhà xe và tài khoản quản lý

```text
OperatorManagement / OperatorForm
  → AuthProvider.request
  → AuthFilter
  → OperatorController
  → OperatorService
  → NhaXeRepository
  → PostgreSQL
```

- `OperatorForm` chỉ hiển thị và nhận dữ liệu form; `OperatorManagement` điều phối tải, lưu, đổi trạng thái và cấp/thu hồi quản lý.
- `OperatorValidation` kiểm tra dữ liệu và áp dụng các trường được phép sửa.
- `OperatorService` kiểm tra admin, điều kiện thay đổi, dữ liệu tham chiếu và thu hồi phiên khi đổi quyền.
- `NhaXeRepository` chứa truy vấn tìm kiếm, phân trang, dữ liệu ảnh hưởng và danh sách tài khoản quản lý.
- DTO input nằm trong `OperatorInputs`; các response hiện có tiếp tục được khai báo trong `OperatorService`.

Demo tạo nhà xe, sửa hồ sơ, xem dữ liệu ảnh hưởng khi đổi trạng thái; thử xóa nhà xe có dữ liệu để xem lỗi bảo vệ. Khi cấp quyền quản lý, tài khoản phải đáp ứng điều kiện và phiên cũ bị thu hồi.

## 3. Đánh giá chuyến đi

`ReviewController` → `ReviewService` → `ReviewRepository`. Kiểm tra sao/nội dung/phân trang nằm trong `ReviewValidation`; ánh xạ response nằm trong `ReviewMapper`.

Service giữ các quy tắc: khách có vé hợp lệ trên chuyến đã hoàn tất; mỗi tài khoản đánh giá một lần trên chuyến; chỉnh sửa phải kiểm duyệt lại; quản lý chỉ kiểm duyệt trong phạm vi được phép. Lịch sử thay đổi tiếp tục được lưu qua Repository trong transaction.

## 4. Kiểm thử

```powershell
mvn -B verify
npm run typecheck
npm run lint
npm run build
```

- `AccountServiceTest`: đăng ký, đăng nhập, hết hạn, hồ sơ, đổi mật khẩu, trạng thái và thu hồi phiên qua ba Service.
- `AuthFlowTest`: chạy request xuyên Filter, Controller và JPA; kiểm tra cookie, CSRF, JSON và thu hồi phiên.
- `LoginRateLimiterTest`: giới hạn theo địa chỉ và thời điểm mở lại cửa sổ.
- `AccessControlTest`: quyền trên tài nguyên và phạm vi nhà xe.
- `OperatorServiceTest`, `ReviewServiceTest`: nghiệp vụ nhà xe và đánh giá.

Các bài PostgreSQL/trình duyệt có điều kiện môi trường riêng; test bị bỏ qua không được tính là đạt. Báo cáo kiểm thử nằm trong `apps/backend/target/surefire-reports` sau khi chạy Maven.
