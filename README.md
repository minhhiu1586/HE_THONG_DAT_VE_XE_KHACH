# Vexexin — Hệ thống đặt vé xe khách trực tuyến

Ứng dụng web hỗ trợ tìm kiếm chuyến xe, chọn ghế, đặt vé, thanh toán trực tuyến và quản lý hoạt động nhà xe.

## Mục lục

- [Chức năng](#chuc-nang)
- [Công nghệ](#cong-nghe)
- [Kiến trúc và cấu trúc thư mục](#cau-truc-source)
- [Cài đặt và chạy](#cai-dat)
- [Cấu hình](#cau-hinh)
- [Dữ liệu mẫu](#du-lieu)
- [Kiểm thử](#kiem-thu)
- [Khắc phục lỗi thường gặp](#xu-ly-loi)
- [Tài liệu](#tai-lieu)

## Chức năng

### Khách hàng

- Đăng ký, đăng nhập, đăng xuất, cập nhật hồ sơ và đổi mật khẩu.
- Tìm chuyến theo điểm đi, điểm đến và ngày khởi hành.
- Xem chi tiết chuyến, giá vé và sơ đồ ghế.
- Chọn nhiều ghế, giữ chỗ trong 15 phút và nhập thông tin người đi.
- Xem báo giá, áp dụng khuyến mãi và thanh toán qua VNPay.
- Xem lịch sử đặt vé, vé điện tử có QR và nhận email xác nhận.
- Yêu cầu hoàn vé, theo dõi trạng thái hoàn tiền và đánh giá chuyến đi khi đủ điều kiện.

### Quản lý nhà xe

- Quản lý tuyến đường, bến xe và điểm dừng.
- Quản lý loại xe, phương tiện và sơ đồ ghế.
- Quản lý hồ sơ nhân viên, bằng lái, lịch làm việc và phân công chuyến.
- Tạo chuyến nháp, kiểm tra điều kiện mở bán, bắt đầu, hoàn thành và hủy chuyến.
- Quản lý dữ liệu và kiểm duyệt đánh giá trong phạm vi nhà xe.

### Quản trị hệ thống

- Quản lý tài khoản, trạng thái và quyền truy cập.
- Quản lý nhà xe và liên kết tài khoản quản lý.
- Quản lý các danh mục, dữ liệu vận tải theo chính sách truy cập.

### Luồng đặt vé

```text
Tìm chuyến → Xem chi tiết → Chọn ghế → Giữ chỗ
    → Nhập thông tin người đi → Chốt giá → Thanh toán
    → Backend xác minh giao dịch → Phát hành vé và gửi email
```

Giá, thời hạn giữ ghế và trạng thái thanh toán được Backend xác định. Vé chỉ được phát hành sau khi xác minh thanh toán; trạng thái gửi email được xử lý riêng. Hoàn vé áp dụng theo điều kiện và chính sách của đơn/vé.

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Frontend | Next.js 16, React 19, TypeScript, CSS Modules |
| Backend | Java 21, Jakarta Servlet 6, Maven, Jackson |
| ORM | JPA, Hibernate 6 |
| Database | PostgreSQL 16 |
| Máy chủ ứng dụng | Apache Tomcat 10.1 |
| Thanh toán | VNPay sandbox |
| Email | Brevo HTTP API |
| Mã QR | ZXing |
| Kiểm thử | JUnit 5, H2, PostgreSQL, script Python và kiểm thử trình duyệt |
| Môi trường chạy | Docker, Docker Compose |

## Kiến trúc và cấu trúc thư mục

```text
Trình duyệt → Next.js → /api/* → Filter
    → Servlet Controller → Service → Repository → JPA → PostgreSQL
```

Frontend gọi API qua Next.js; Backend quản lý xác thực, nghiệp vụ và truy cập dữ liệu. Backend được đóng gói thành một WAR; Frontend sử dụng một bộ dependency cài tại thư mục gốc.

```text
HE_THONG_DAT_VE_XE_KHACH/
├── apps/
│   ├── frontend/          # Next.js routes, layout, public và cấu hình
│   └── backend/           # Maven, resource và WAR tích hợp
├── members/               # Source Frontend, Backend và Java test
├── shared/                # Domain, JPA, bảo mật, UI và tiện ích dùng chung
├── database/
│   ├── migrations/        # Lịch sử thay đổi schema
│   ├── seed/              # Dữ liệu mẫu
│   ├── README.md
│   └── tai-khoan-mau.md
├── docs/                  # API, thiết kế và tài liệu kỹ thuật
├── tests/                 # Kiểm thử tích hợp, giao diện và use case
├── scripts/               # Migration và nạp dữ liệu
├── .github/               # CI và mẫu Pull Request
├── .env.example
├── docker-compose.yml
├── pom.xml
├── package.json
├── package-lock.json
├── tsconfig.json
└── README.md
```

Maven gom source từ `members/` và `shared/` để build Backend. Next.js tham chiếu source qua alias TypeScript. Các lệnh cài đặt, build và chạy đều thực hiện tại root repository.

## Cài đặt và chạy

### Yêu cầu

- Git, JDK 21, Maven 3.9 trở lên.
- Node.js 22 và npm.
- Docker Desktop chạy Linux containers, Docker Compose v2.
- Python 3 để nạp dữ liệu và chạy script kiểm thử.

Các lệnh sau dùng **PowerShell**, thực hiện tại thư mục gốc dự án.

### 1. Lấy source và tạo cấu hình

```powershell
git clone https://github.com/minhhiu1586/HE_THONG_DAT_VE_XE_KHACH.git
cd HE_THONG_DAT_VE_XE_KHACH
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Điền cấu hình trong `.env`. Đặt mật khẩu PostgreSQL trước lần tạo database đầu tiên. Với volume có sẵn, thông tin đăng nhập phải khớp database đang dùng.

### 2. Build và chạy Backend

```powershell
mvn -B clean verify
docker compose up -d postgres
docker compose run --rm migrate
docker compose up -d backend
```

WAR đầu ra: `apps/backend/target/he-thong-dat-ve-xe-khach-0.1.0-SNAPSHOT.war`.

Chờ Tomcat deploy, sau đó kiểm tra Backend trả HTTP `200`:

```powershell
curl.exe -i http://localhost:8080/api/health
```

### 3. Nạp dữ liệu và mở bán chuyến mẫu

Khi Backend đã sẵn sàng và đang dùng cấu hình database mặc định:

```powershell
python scripts/load_project_data.py
python tests/backend/project_data_check.py
```

Loader nạp migration/seed và mở bán chuyến qua API để kiểm tra điều kiện vận hành. Docker init chỉ tạo dữ liệu nền và chuyến nháp. Nếu đổi database/user, truyền tham số phù hợp theo `python scripts/load_project_data.py --help`.

Nếu admin mẫu đã đổi mật khẩu, đặt biến môi trường `PROJECT_ADMIN_PASSWORD` trước khi chạy loader. Nạp lại seed giữ nguyên mật khẩu và các bản ghi hiện có.

### 4. Chạy Frontend

Mở terminal khác tại root:

```powershell
npm ci
npm run dev
```

| Dịch vụ | Địa chỉ mặc định |
|---|---|
| Trang chủ | http://localhost:3000/home |
| Đăng nhập | http://localhost:3000/dang-nhap |
| Backend API | http://localhost:8080/api |
| Health check | http://localhost:8080/api/health |
| PostgreSQL | `localhost:5432` |

Frontend chạy riêng bằng npm; Compose chạy Backend và PostgreSQL.

### Build Frontend và dừng dịch vụ

```powershell
npm run build
npm run start
```

Dừng `npm run dev` trước khi chạy `start` trên cùng cổng. Dừng Backend và database bằng:

```powershell
docker compose down
```

Không dùng `down -v` nếu cần giữ dữ liệu PostgreSQL.

## Cấu hình

| Biến | Mục đích |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database và thông tin đăng nhập |
| `POSTGRES_PORT` | Cổng PostgreSQL trên máy host |
| `DATABASE_URL` | JDBC URL khi chạy Backend ngoài Compose |
| `FRONTEND_ORIGIN` | Origin Frontend được Backend chấp nhận |
| `AUTH_COOKIE_SECURE` | Thuộc tính Secure của cookie khi dùng HTTPS |
| `BACKEND_URL` | Đích rewrite API của Next.js, mặc định `http://127.0.0.1:8080` |
| `NEXT_PUBLIC_API_URL` | Ghi đè URL cho các hàm booking có đọc biến này; để trống khi dùng `/api` cùng origin |
| `VEHICLE_TURNAROUND_MINUTES` | Thời gian chuẩn bị xe giữa các chuyến |
| `TRIP_BOOKING_CUTOFF_MINUTES` | Khoảng ngừng nhận đặt vé trước giờ khởi hành |
| `VNP_TMN_CODE`, `VNP_HASH_SECRET`, `VNP_RETURN_URL` | Cấu hình merchant và URL trở về VNPay |
| `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME` | API key và sender đã xác minh để gửi email |
| `EMAIL_NOTIFICATIONS_ENABLED` | Bật/tắt gửi email |
| `TRIP_CANCELLATION_REFUND_APPROVED` | Cờ chính sách hoàn khi nhà xe hủy chuyến |

Xem đầy đủ tại [`.env.example`](.env.example). Giữ khóa VNPay/Brevo ở Backend và không commit `.env`.

Compose đọc `.env` ở root. Khi chạy Next.js riêng và cần đổi đích API, đặt biến trước khi chạy:

```powershell
$env:BACKEND_URL = "http://127.0.0.1:8080"
npm run dev
```

Sau khi đổi environment của Backend trong `.env`, áp dụng bằng `docker compose up -d --force-recreate backend`.

## Dữ liệu mẫu

Database có 18 migration theo phiên bản và ba file seed:

| File seed | Dữ liệu |
|---|---|
| `001_catalog.sql` | Danh mục nhà xe, địa điểm, loại xe và phương thức thanh toán |
| `002_accounts_staff_fleet.sql` | Tài khoản, nhân viên, phương tiện và ghế |
| `003_routes_trips.sql` | Tuyến, điểm đón/trả, lịch chuyến và nhân sự chuyến |

Hibernate dùng chế độ `validate`, không tự sửa schema. Khi có migration mới, chạy `docker compose run --rm migrate`; giữ nguyên migration đã áp dụng vì hệ thống kiểm tra checksum.

| Tài khoản demo | Email |
|---|---|
| Khách hàng | `khach1@datvexe.test` |
| Quản trị | `admin1@datvexe.test` |
| Quản lý nhà xe | `dieuhanh.futa@datvexe.test` |
| Tài xế | `tx1.futa@datvexe.test` |
| Phụ xe | `px1.futa@datvexe.test` |

Mật khẩu khởi tạo: `DatVeXe2026!`. Nếu tài khoản đã đổi mật khẩu, sử dụng mật khẩu mới.

Chọn ngày có chuyến mẫu đang mở bán khi thử tìm chuyến. Lịch và giá seed phục vụ demo, không đại diện hoạt động vận tải thực tế.

Chi tiết: [database](database/README.md), [danh sách tài khoản mẫu](database/tai-khoan-mau.md).

## Kiểm thử

Chạy tại root:

```powershell
mvn -B verify
npm run typecheck
npm run lint
npm run build
python tests/use-cases/check_matrix.py
node tests/frontend/management_options_check.cjs
node tests/frontend/trip_search_check.cjs
```

Java test nằm trong `members/*/backend/src/test/java`, fixture chung trong `shared/backend/src/test/java`. Báo cáo Maven sinh tại `apps/backend/target/surefire-reports/`.

Các script HTTP cần Backend/database đang chạy; bài kiểm thử PostgreSQL và trình duyệt có điều kiện môi trường riêng. `check_matrix.py` kiểm tra tính nhất quán của ma trận, không thực thi toàn bộ test case nghiệp vụ.

Hướng dẫn và kết quả: [kiểm thử Backend](tests/backend/README.md), [test case theo use case](tests/use-cases/README.md).

## Khắc phục lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
|---|---|
| PowerShell chặn `npm.ps1` | Dùng `npm.cmd ci` và `npm.cmd run dev` |
| Không nhận lệnh `next` | Chạy `npm ci` tại root repository |
| Không tải địa điểm hoặc `ECONNREFUSED` | Kiểm tra Backend health, log Tomcat và `BACKEND_URL` |
| Health trả 404 hoặc `Empty reply` | Chờ deploy; nếu lỗi tiếp tục, kiểm tra WAR và log khởi động ứng dụng |
| Hibernate báo thiếu bảng/cột | Chạy migration còn thiếu rồi khởi động lại Backend |
| Không tìm thấy chuyến | Kiểm tra loader đã mở bán, đúng ngày/chiều và chưa quá giờ nhận đặt vé |
| API trả 401/403 | Kiểm tra phiên đăng nhập, vai trò, phạm vi dữ liệu và CSRF |
| VNPay chưa được cấu hình | Cập nhật merchant sandbox và recreate Backend |
| Không nhận email | Kiểm tra địa chỉ nhận, sender Brevo đã xác minh, cấu hình và trạng thái/log gửi |

```powershell
docker compose ps
docker compose logs --tail=120 backend
curl.exe -i http://localhost:8080/api/health
```

## Tài liệu

- [Hợp đồng API và OpenAPI](docs/api/README.md).
- [Tài khoản và xác thực](docs/api/tai-khoan-va-xac-thuc.md).
- [Giữ chỗ và đơn đặt vé](docs/api/giu-cho-va-don-dat-ve.md).
- [Thanh toán VNPay](docs/api/thanh-toan-vnpay.md).
- [Email xác nhận](docs/api/email-xac-nhan-dat-ve.md).
- [Hủy vé và hoàn tiền](docs/api/huy-ve-va-hoan-tien.md).
- [Sơ đồ lớp](docs/diagrams/class-diagram/CLASS_DIAGRAM.drawio).
- [Hướng dẫn đóng góp](CONTRIBUTING.md).

Cấu hình hiện tại phục vụ phát triển và demo local. Thanh toán sandbox, email và các bài kiểm thử tích hợp cần môi trường phù hợp; chưa thay thế cấu hình triển khai production.
