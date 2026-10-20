# Nguyễn Minh Hiếu — Kiểm thử

<a id="section-1"></a>

## Chạy kiểm thử

Từ root: `mvn -B verify`. Chọn lớp: `mvn -B -f apps/backend/pom.xml -Dtest=TenLopTest test`.

Source Java test nằm ở `../backend/src/test/java`; gồm unit test và integration test.

- `AccessControlTest.java`
- `HealthPolicyTest.java`
- `PaymentPolicyTest.java`
- `PostgresAccessControlTest.java`
- `PostgresT06AccessControlTest.java`
- `AccountServiceTest.java`
- `OperatorServiceTest.java`
- `ReviewServicePostgresTest.java`
- `ReviewServiceTest.java`

<a id="section-4"></a>

## Test case

- [Ma trận use case](../../../tests/use-cases/test-cases.json).
- Ma trận và review riêng: [existing/](existing/).
- [Các bài kiểm tra HTTP tích hợp](../../../tests/backend/README.md).

<a id="section-2"></a>
<a id="section-3"></a>

## Bug report và bằng chứng

Chưa có bug report nghiệp vụ mới trong đợt tái cấu trúc. Các review/ma trận và báo cáo được dẫn ở đây là bằng chứng đã có; không coi test bị bỏ qua là đạt.

<a id="section-5"></a>
<a id="section-6"></a>

## Kết quả backend sau tái cấu trúc — 18/10/2026

Nguồn: Surefire XML của lần Maven verify sau di chuyển. Giữ nguyên test gốc; có cả unit/integration test.

| Lớp | Tổng | Lỗi assertion | Lỗi thực thi | Bỏ qua |
|---|---:|---:|---:|---:|
| com.datvexe.security.AccessControlTest | 15 | 0 | 0 | 0 |
| com.datvexe.security.HealthPolicyTest | 1 | 0 | 0 | 0 |
| com.datvexe.security.PaymentPolicyTest | 2 | 0 | 0 | 0 |
| com.datvexe.security.PostgresAccessControlTest | 3 | 0 | 0 | 3 |
| com.datvexe.security.PostgresT06AccessControlTest | 4 | 0 | 0 | 4 |
| com.datvexe.service.AccountServiceTest | 10 | 0 | 0 | 0 |
| com.datvexe.service.OperatorServiceTest | 4 | 0 | 0 | 0 |
| com.datvexe.service.ReviewServicePostgresTest | 24 | 0 | 0 | 24 |
| com.datvexe.service.ReviewServiceTest | 36 | 0 | 0 | 0 |

PostgreSQL/HTTP/browser/provider thật chưa được chạy trong đợt này. Test bị bỏ qua không được tính đạt. Báo cáo chung: [ket-qua-kiem-thu-sau-tai-cau-truc.md](../../../docs/team/ket-qua-kiem-thu-sau-tai-cau-truc.md).

## Refactor ngày 20/10/2026

- Tham khảo cách tách Auth/User và handler của Cinema-main; giữ nguyên cơ chế session cookie, CSRF, PBKDF2 và các URL API.
- `mvn -B clean verify -DforkCount=0`: build WAR đạt; toàn bộ báo cáo có 275 test, 212 thực thi đạt, 63 bỏ qua, không có failure/error. Các test PostgreSQL có điều kiện chưa được chạy.
- Phần Nguyễn Minh Hiếu có 104 test: 73 thực thi đạt, 31 bỏ qua.
- `AuthFlowTest` bổ sung request xuyên Filter → Controller → Service → JPA: đăng ký, đăng nhập, cookie/CSRF, hồ sơ, đăng xuất, JSON và origin không hợp lệ.
- `LoginRateLimiterTest` kiểm tra giới hạn theo địa chỉ và thời điểm mở lại cửa sổ.
- Chạy lại hai lớp mới sau chỉnh lý cuối và đóng gói WAR: đạt.
- TypeScript, ESLint và Next.js production build đạt; ESLint còn hai cảnh báo img có sẵn ngoài phần này.
- Chưa chạy lại E2E trên Docker/trình duyệt hoặc dịch vụ VNPay/Brevo thật.
