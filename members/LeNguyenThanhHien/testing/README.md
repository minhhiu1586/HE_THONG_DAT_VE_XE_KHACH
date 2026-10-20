# Lê Nguyễn Thanh Hiền — Kiểm thử

<a id="section-1"></a>

## Chạy kiểm thử

Từ root: `mvn -B verify`. Chọn lớp: `mvn -B -f apps/backend/pom.xml -Dtest=TenLopTest test`.

Source Java test nằm ở `../backend/src/test/java`; gồm unit test và integration test.

- `PostgresTask10Test.java`
- `PostgresTripTest.java`
- `PostgresVehicleTest.java`
- `RouteTripServiceTest.java`
- `TripServiceTest.java`
- `VehicleServiceTest.java`
- `VehicleTask10Test.java`

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
| com.datvexe.service.PostgresTask10Test | 2 | 0 | 0 | 2 |
| com.datvexe.service.PostgresTripTest | 3 | 0 | 0 | 3 |
| com.datvexe.service.PostgresVehicleTest | 2 | 0 | 0 | 2 |
| com.datvexe.service.RouteTripServiceTest | 19 | 0 | 0 | 0 |
| com.datvexe.service.TripServiceTest | 21 | 0 | 0 | 0 |
| com.datvexe.service.VehicleServiceTest | 5 | 0 | 0 | 0 |
| com.datvexe.service.VehicleTask10Test | 12 | 0 | 0 | 0 |

PostgreSQL/HTTP/browser/provider thật chưa được chạy trong đợt này. Test bị bỏ qua không được tính đạt. Báo cáo chung: [ket-qua-kiem-thu-sau-tai-cau-truc.md](../../../docs/team/ket-qua-kiem-thu-sau-tai-cau-truc.md).

## Viết lại backend theo cách trình bày tuần tự — 18/10/2026

Phạm vi source thay đổi nằm trong thư mục `members/LeNguyenThanhHien`.
Giữ đường dẫn API, HTTP status, trường JSON, phân quyền, transaction/khóa và
quy tắc nghiệp vụ. Không thay giao diện, schema hay dữ liệu của CSDL đang dùng.

### Bằng chứng của lần kiểm tra này

| Kiểm tra | Kết quả |
|---|---|
| Biên dịch toàn bộ source và test Java 21 bằng ECJ | Đạt |
| `RouteTripServiceTest`, `TripServiceTest`, `VehicleServiceTest`, `VehicleTask10Test` | 58/58 đạt |
| `PostgresTripTest`, `PostgresTask10Test`, `PostgresVehicleTest` | 7/7 đạt, bật thật trên PostgreSQL 16 riêng |
| `tests/backend/api_smoke.py` trên Tomcat 10.1 | 96 phản hồi HTTP đạt |
| `tests/backend/trip_sale_smoke.py` trên Tomcat 10.1 | 123 phản hồi HTTP đạt |
| `tests/backend/vehicle_management_smoke.py` trên Tomcat 10.1 | 81 phản hồi HTTP đạt |
| Kịch bản hủy có đơn chưa trả tiền, đã trả và chưa rõ kết quả | Đạt; công việc phân loại đúng, báo cáo đủ trường, lịch sử một lần, thử lại không tạo hoàn trùng |

Thêm một test hồi quy trong `RouteTripServiceTest`: chín tổ hợp mã tỉnh/mã bến/mã cũ
trả cùng chuyến, giữ các trường hành trình và điều kiện bán; chuyến đang chạy và
hoàn tất không xuất hiện trong kết quả. Test này không bỏ qua các điều kiện vận hành.

Các test PostgreSQL được nạp đúng fixture demo mà các lớp test cũ yêu cầu.
CSDL cho JUnit PostgreSQL và CSDL cho HTTP tách riêng để không tranh chấp tên tỉnh
`T12` giữa hai bộ test. Container/CSDL kiểm thử được dọn sau khi chạy.
Kịch bản hủy dùng chính sách hoàn chưa phê duyệt để xác minh luồng đối soát;
không gửi giao dịch hoàn VNPAY hoặc email thật.

### Giới hạn đã ghi nhận

- Bộ toàn dự án có 209 test không phải PostgreSQL. Có lượt đạt 209/209; lượt cuối
  đạt 208/209 do `BookingServiceExpiryTest.schedulerAndGetAtExactDeadlineConvergeOnOneExpiredFrozenOrder`
  gặp deadlock H2. Lỗi này đã xuất hiện ở lượt mốc trước khi sửa (207/208 test lúc đó),
  nằm ngoài phần source sửa. Không đổi logic đặt vé hoặc bỏ kiểm tra đó để làm xanh bộ test.
- Maven `clean verify` chưa hoàn tất do DNS khi tải plugin từ Maven Central.
  Vì thế các số trên là kết quả biên dịch Java 21/JUnit trực tiếp và chạy WAR thử
  trên Tomcat/PostgreSQL, không phải tuyên bố Maven BUILD SUCCESS.

### Lệnh kiểm tra trên máy đã có Maven/JDK 21

Chạy từ thư mục gốc:

```bash
mvn -B -f apps/backend/pom.xml -Dtest=RouteTripServiceTest,TripServiceTest,VehicleServiceTest,VehicleTask10Test test
mvn -B -f apps/backend/pom.xml clean verify
```

Các bài HTTP phải trỏ vào backend và CSDL thử riêng, cùng cấu hình `API_BASE_URL`,
`DB_CONTAINER`, `DB_NAME`. Không chạy các fixture trên CSDL đang chứa dữ liệu cần giữ.

## Kiểm tra lại toàn dự án sau bổ sung chú thích — 18/10/2026

Đợt này bổ sung 53 dòng chú thích tiếng Việt trong 12 file Java, không sửa mã
thực thi. Các kết quả dưới đây là lần chạy mới, bổ sung cho giới hạn của lần trước.

| Kiểm tra | Kết quả |
|---|---|
| Maven/JDK 21 `clean verify` trên toàn backend | BUILD SUCCESS; 209 test thực thi đạt, 63 test PostgreSQL tạm bỏ qua ở lượt mặc định |
| Bật riêng toàn bộ nhóm PostgreSQL trên CSDL fixture độc lập | 75/75 đạt, không bỏ qua |
| 10 bộ HTTP tích hợp: tài khoản, nhân viên, tuyến, xe, mở bán, đặt vé, giá, khuyến mãi/phương thức thanh toán, thanh toán, hoàn vé | 10/10 bộ đạt; ảnh chụp dữ liệu nền trước/sau trùng nhau |
| Thanh toán chạy bổ sung với cấu hình ký callback thử cục bộ | 13 ca đạt, 86 xác nhận HTTP, không bỏ qua |
| Frontend lint và TypeScript sau build | Đạt |
| Build production Next.js bằng Webpack | Đạt |
| Kiểm tra Node cho tìm chuyến và danh mục quản lý | Đạt |
| Trình duyệt: tìm kiếm/bộ lọc, quản lý tuyến, đánh giá T22 | Đạt |
| Trình duyệt: quản lý xe T10 và mở bán T12 | Đạt sau điều chỉnh bộ chọn menu trong bản chạy tạm |

Google Fonts bị chặn trong môi trường kiểm thử nên build Turbopack mặc định
không hoàn tất. Lượt Webpack dùng bộ nhớ đệm Inter thật lấy từ gói
`@fontsource/inter`, không đổi cấu hình hay mã frontend trong Git.

Các bài trình duyệt cũ có bộ chọn không khớp giao diện hiện tại: liên kết
“Quản trị”, thông báo có thêm liên kết, vùng menu di động và nhãn nút menu.
Các điều chỉnh phục vụ chạy lại chỉ nằm trong `/tmp`, không sửa ứng dụng hoặc
làm yếu kiểm tra nghiệp vụ. Bài quản lý xe dùng nhà xe `NX_FUTA` có trong
seed hiện tại thay cho `DEMO_NX`; chờ menu cập nhật sau điều hướng/đổi kích thước.
Bài tài khoản đi qua đăng ký, đăng nhập, hồ sơ,
đổi mật khẩu, phân quyền và khóa/mở khóa; chưa đạt bước cuối do đăng xuất
chuyển về `/dang-nhap?next=...` thay vì `/` như bài kiểm tra mong đợi.
Đây là hành vi của frontend dùng chung, không thay đổi trong đợt bổ sung chú thích.

CSDL PostgreSQL và Tomcat thử chạy riêng, không đặt lại dữ liệu đang dùng.
Callback VNPay được ký và gửi cục bộ; không xác nhận giao dịch sandbox,
QueryDR, hoàn tiền qua ngân hàng hoặc gửi email thật. Các kết quả này không
phải cam kết không còn lỗi ở mọi tình huống.
