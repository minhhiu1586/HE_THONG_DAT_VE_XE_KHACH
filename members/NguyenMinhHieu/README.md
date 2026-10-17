# Nguyễn Minh Hiếu

Tài khoản, xác thực, phân quyền, nhà xe; bảo trì đánh giá hiện có theo T22.

## Tài liệu và kiểm thử

- [Hướng dẫn demo](docs/huong-dan-demo.md)
- [Kiểm thử và kết quả chạy](testing/README.md)
- [Phân công và phần phối hợp](../../docs/team/phan-cong-thanh-vien.md)
- [Hợp đồng API](../../docs/api/README.md)

<a id="source"></a>

## Source thuộc phạm vi bảo trì

Ứng dụng tích hợp dùng trực tiếp source bên dưới. Chạy từ thư mục gốc theo [README](../../README.md).

| File |
|---|
| [AccountController.java](backend/src/main/java/com/datvexe/controller/AccountController.java) |
| [OperatorController.java](backend/src/main/java/com/datvexe/controller/OperatorController.java) |
| [ReviewController.java](backend/src/main/java/com/datvexe/controller/ReviewController.java) |
| [AuthSession.java](backend/src/main/java/com/datvexe/domain/AuthSession.java) |
| [AccountInputs.java](backend/src/main/java/com/datvexe/dto/AccountInputs.java) |
| [OperatorInputs.java](backend/src/main/java/com/datvexe/dto/OperatorInputs.java) |
| [ReviewInputs.java](backend/src/main/java/com/datvexe/dto/ReviewInputs.java) |
| [NguoiDungRepository.java](backend/src/main/java/com/datvexe/repository/NguoiDungRepository.java) |
| [ReviewRepository.java](backend/src/main/java/com/datvexe/repository/ReviewRepository.java) |
| [AuthFilter.java](backend/src/main/java/com/datvexe/security/AuthFilter.java) |
| [Passwords.java](backend/src/main/java/com/datvexe/security/Passwords.java) |
| [AccountService.java](backend/src/main/java/com/datvexe/service/AccountService.java) |
| [OperatorService.java](backend/src/main/java/com/datvexe/service/OperatorService.java) |
| [ReviewService.java](backend/src/main/java/com/datvexe/service/ReviewService.java) |
| [AccessControlTest.java](backend/src/test/java/com/datvexe/security/AccessControlTest.java) |
| [HealthPolicyTest.java](backend/src/test/java/com/datvexe/security/HealthPolicyTest.java) |
| [PaymentPolicyTest.java](backend/src/test/java/com/datvexe/security/PaymentPolicyTest.java) |
| [PostgresAccessControlTest.java](backend/src/test/java/com/datvexe/security/PostgresAccessControlTest.java) |
| [PostgresT06AccessControlTest.java](backend/src/test/java/com/datvexe/security/PostgresT06AccessControlTest.java) |
| [AccountServiceTest.java](backend/src/test/java/com/datvexe/service/AccountServiceTest.java) |
| [OperatorServiceTest.java](backend/src/test/java/com/datvexe/service/OperatorServiceTest.java) |
| [ReviewServicePostgresTest.java](backend/src/test/java/com/datvexe/service/ReviewServicePostgresTest.java) |
| [ReviewServiceTest.java](backend/src/test/java/com/datvexe/service/ReviewServiceTest.java) |
| [Account.module.css](frontend/src/account/components/Account.module.css) |
| [AccountActions.tsx](frontend/src/account/components/AccountActions.tsx) |
| [AdminAccounts.tsx](frontend/src/account/components/AdminAccounts.tsx) |
| [AuthForm.tsx](frontend/src/account/components/AuthForm.tsx) |
| [AuthProvider.tsx](frontend/src/account/components/AuthProvider.tsx) |
| [ProfilePanel.tsx](frontend/src/account/components/ProfilePanel.tsx) |
| [OperatorManagement.module.css](frontend/src/operators/components/OperatorManagement.module.css) |
| [OperatorManagement.tsx](frontend/src/operators/components/OperatorManagement.tsx) |
| [PublicReviews.tsx](frontend/src/reviews/components/PublicReviews.tsx) |
| [review-types.ts](frontend/src/reviews/components/review-types.ts) |
| [ReviewEditor.tsx](frontend/src/reviews/components/ReviewEditor.tsx) |
| [ReviewModeration.tsx](frontend/src/reviews/components/ReviewModeration.tsx) |
| [Reviews.module.css](frontend/src/reviews/components/Reviews.module.css) |
| [account-api.ts](frontend/src/account/lib/account-api.ts) |
| [role-home.ts](frontend/src/account/lib/role-home.ts) |
| [t07-ban-giao-tai-khoan.md](testing/existing/t07-ban-giao-tai-khoan.md) |
| [t07-ra-soat-tai-khoan-va-phan-quyen.md](testing/existing/t07-ra-soat-tai-khoan-va-phan-quyen.md) |
| [t07-ma-tran-kiem-thu-tai-khoan.md](testing/existing/t07-ma-tran-kiem-thu-tai-khoan.md) |
| [t07-1-tich-hop-phan-quyen.md](testing/existing/t07-1-tich-hop-phan-quyen.md) |
| [t08-ra-soat-tuyen-va-ben-xe.md](testing/existing/t08-ra-soat-tuyen-va-ben-xe.md) |
| [t09-ban-giao-quan-ly-nha-xe.md](testing/existing/t09-ban-giao-quan-ly-nha-xe.md) |
| [t07-vscode-guide.md](docs/huong-dan-demo.md#section-1) |
| [t08-vscode-guide.md](docs/huong-dan-demo.md#section-2) |
