# Nguyễn Minh Hiếu

## Phạm vi

Tài khoản, xác thực, phân quyền, nhà xe và đánh giá chuyến đi.

## Luồng chính

| Chức năng | Giao diện | Controller | Service | Repository |
|---|---|---|---|---|
| Đăng ký / đăng nhập / đăng xuất | `AuthForm` → `RegisterForm` / `LoginForm` | `AuthController` | `AuthService` | `NguoiDungRepository`, `AuthSessionRepository` |
| Hồ sơ / đổi mật khẩu | `ProfilePanel` → `ProfileDetailsForm` / `ChangePasswordForm` | `AccountController` | `AccountService` | `NguoiDungRepository`, `AuthSessionRepository` |
| Quản trị tài khoản | `AdminAccounts` | `AdminAccountController` | `AdminAccountService` | `NguoiDungRepository`, `AuthSessionRepository` |
| Nhà xe và tài khoản quản lý | `OperatorManagement`, `OperatorForm` | `OperatorController` | `OperatorService` | `NhaXeRepository`, `AuthSessionRepository` |
| Đánh giá | `PublicReviews`, `ReviewEditor`, `ReviewModeration` | `ReviewController` | `ReviewService` | `ReviewRepository` |

`AuthFilter` kiểm tra request, phiên, CSRF và quyền truy cập. `AuthCookies` quản lý cookie, `CachedBodyRequest` giữ body để Controller đọc lại, `Passwords` xử lý mật khẩu. DTO nằm trong `dto`, kiểm tra dữ liệu trong `validation`.

- [Luồng demo và thứ tự mở code](docs/huong-dan-demo.md)
- [Kiểm thử và kết quả](testing/README.md)
- [Chạy ứng dụng](../../README.md)

<a id="source"></a>

## Source

| File |
|---|
| [AccountController.java](backend/src/main/java/com/datvexe/controller/AccountController.java) |
| [AccountHttp.java](backend/src/main/java/com/datvexe/controller/AccountHttp.java) |
| [AdminAccountController.java](backend/src/main/java/com/datvexe/controller/AdminAccountController.java) |
| [AuthController.java](backend/src/main/java/com/datvexe/controller/AuthController.java) |
| [OperatorController.java](backend/src/main/java/com/datvexe/controller/OperatorController.java) |
| [ReviewController.java](backend/src/main/java/com/datvexe/controller/ReviewController.java) |
| [AuthSession.java](backend/src/main/java/com/datvexe/domain/AuthSession.java) |
| [AccountInputs.java](backend/src/main/java/com/datvexe/dto/AccountInputs.java) |
| [AccountResponses.java](backend/src/main/java/com/datvexe/dto/AccountResponses.java) |
| [OperatorInputs.java](backend/src/main/java/com/datvexe/dto/OperatorInputs.java) |
| [ReviewInputs.java](backend/src/main/java/com/datvexe/dto/ReviewInputs.java) |
| [AuthSessionRepository.java](backend/src/main/java/com/datvexe/repository/AuthSessionRepository.java) |
| [NguoiDungRepository.java](backend/src/main/java/com/datvexe/repository/NguoiDungRepository.java) |
| [NhaXeRepository.java](backend/src/main/java/com/datvexe/repository/NhaXeRepository.java) |
| [ReviewRepository.java](backend/src/main/java/com/datvexe/repository/ReviewRepository.java) |
| [AuthCookies.java](backend/src/main/java/com/datvexe/security/AuthCookies.java) |
| [AuthFilter.java](backend/src/main/java/com/datvexe/security/AuthFilter.java) |
| [CachedBodyRequest.java](backend/src/main/java/com/datvexe/security/CachedBodyRequest.java) |
| [LoginRateLimiter.java](backend/src/main/java/com/datvexe/security/LoginRateLimiter.java) |
| [Passwords.java](backend/src/main/java/com/datvexe/security/Passwords.java) |
| [SessionTokens.java](backend/src/main/java/com/datvexe/security/SessionTokens.java) |
| [AccountErrors.java](backend/src/main/java/com/datvexe/service/AccountErrors.java) |
| [AccountMapper.java](backend/src/main/java/com/datvexe/service/AccountMapper.java) |
| [AccountService.java](backend/src/main/java/com/datvexe/service/AccountService.java) |
| [AccountTransactions.java](backend/src/main/java/com/datvexe/service/AccountTransactions.java) |
| [AdminAccountService.java](backend/src/main/java/com/datvexe/service/AdminAccountService.java) |
| [AuthService.java](backend/src/main/java/com/datvexe/service/AuthService.java) |
| [OperatorService.java](backend/src/main/java/com/datvexe/service/OperatorService.java) |
| [ReviewMapper.java](backend/src/main/java/com/datvexe/service/ReviewMapper.java) |
| [ReviewService.java](backend/src/main/java/com/datvexe/service/ReviewService.java) |
| [AccountValidation.java](backend/src/main/java/com/datvexe/validation/AccountValidation.java) |
| [OperatorValidation.java](backend/src/main/java/com/datvexe/validation/OperatorValidation.java) |
| [ReviewValidation.java](backend/src/main/java/com/datvexe/validation/ReviewValidation.java) |
| [AccountActions.tsx](frontend/src/account/components/AccountActions.tsx) |
| [AdminAccounts.tsx](frontend/src/account/components/AdminAccounts.tsx) |
| [AuthForm.tsx](frontend/src/account/components/AuthForm.tsx) |
| [AuthFormLayout.tsx](frontend/src/account/components/AuthFormLayout.tsx) |
| [AuthProvider.tsx](frontend/src/account/components/AuthProvider.tsx) |
| [ChangePasswordForm.tsx](frontend/src/account/components/ChangePasswordForm.tsx) |
| [LoginForm.tsx](frontend/src/account/components/LoginForm.tsx) |
| [ProfileDetailsForm.tsx](frontend/src/account/components/ProfileDetailsForm.tsx) |
| [ProfilePanel.tsx](frontend/src/account/components/ProfilePanel.tsx) |
| [RegisterForm.tsx](frontend/src/account/components/RegisterForm.tsx) |
| [account-api.ts](frontend/src/account/lib/account-api.ts) |
| [account-types.ts](frontend/src/account/lib/account-types.ts) |
| [auth-api.ts](frontend/src/account/lib/auth-api.ts) |
| [role-home.ts](frontend/src/account/lib/role-home.ts) |
| [OperatorForm.tsx](frontend/src/operators/components/OperatorForm.tsx) |
| [OperatorManagement.tsx](frontend/src/operators/components/OperatorManagement.tsx) |
| [operator-types.ts](frontend/src/operators/lib/operator-types.ts) |
| [PublicReviews.tsx](frontend/src/reviews/components/PublicReviews.tsx) |
| [review-types.ts](frontend/src/reviews/components/review-types.ts) |
| [ReviewEditor.tsx](frontend/src/reviews/components/ReviewEditor.tsx) |
| [ReviewModeration.tsx](frontend/src/reviews/components/ReviewModeration.tsx) |
