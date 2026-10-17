# Nguyễn Gia Bảo — Kiểm thử

<a id="section-1"></a>

## Chạy kiểm thử

Từ root: `mvn -B verify`. Chọn lớp: `mvn -B -f apps/backend/pom.xml -Dtest=TenLopTest test`.

Source Java test nằm ở `../backend/src/test/java`; gồm unit test và integration test.

- `BrevoClientTest.java`
- `EmailDispatcherTest.java`
- `ReceiptTemplateTest.java`
- `VNPayClientTest.java`
- `VNPayRefundClientTest.java`
- `ChinhSachHoanVeServiceTest.java`
- `HoanVeServicePostgresTest.java`
- `PriceCalculatorTest.java`
- `ThanhToanServiceTest.java`
- `TicketCodeTest.java`
- `TicketQrTest.java`

<a id="section-4"></a>

## Test case

- [Ma trận use case](../../../tests/use-cases/test-cases.json).
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
| com.datvexe.email.BrevoClientTest | 2 | 0 | 0 | 0 |
| com.datvexe.email.EmailDispatcherTest | 3 | 0 | 0 | 0 |
| com.datvexe.email.ReceiptTemplateTest | 2 | 0 | 0 | 0 |
| com.datvexe.payment.VNPayClientTest | 7 | 0 | 0 | 0 |
| com.datvexe.payment.VNPayRefundClientTest | 9 | 0 | 0 | 0 |
| com.datvexe.service.ChinhSachHoanVeServiceTest | 3 | 0 | 0 | 0 |
| com.datvexe.service.HoanVeServicePostgresTest | 25 | 0 | 0 | 25 |
| com.datvexe.service.PriceCalculatorTest | 11 | 0 | 0 | 0 |
| com.datvexe.service.ThanhToanServiceTest | 18 | 0 | 0 | 0 |
| com.datvexe.service.TicketCodeTest | 1 | 0 | 0 | 0 |
| com.datvexe.ticket.TicketQrTest | 1 | 0 | 0 | 0 |

PostgreSQL/HTTP/browser/provider thật chưa được chạy trong đợt này. Test bị bỏ qua không được tính đạt. Báo cáo chung: [ket-qua-kiem-thu-sau-tai-cau-truc.md](../../../docs/team/ket-qua-kiem-thu-sau-tai-cau-truc.md).
