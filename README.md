# 🚌 HỆ THỐNG ĐẶT VÉ XE KHÁCH TRỰC TUYẾN

### Online Bus Ticket Booking & Management System

**Đồ án cuối kỳ môn Lập trình Web**

**Trường Đại học Công nghệ Kỹ thuật TP. Hồ Chí Minh**

[GitHub Repository](https://github.com/minhhiu1586/HE_THONG_DAT_VE_XE_KHACH)

---

## 📋 Mục Lục

1. [Giới Thiệu](#-giới-thiệu)
2. [Tính Năng](#-tính-năng)
3. [Công Nghệ Sử Dụng](#-công-nghệ-sử-dụng)
4. [Kiến Trúc Hệ Thống](#-kiến-trúc-hệ-thống)
5. [Cài Đặt và Chạy Dự Án](#-cài-đặt-và-chạy-dự-án)
6. [Cấu Hình](#-cấu-hình)
7. [Database và API](#-database-và-api)
8. [Deployment](#-deployment)
9. [Screenshots](#-screenshots)
10. [Contributing](#-contributing)
11. [Team](#-team)

---

## 🎯 Giới Thiệu

**Hệ thống đặt vé xe khách trực tuyến** là ứng dụng web hỗ trợ khách hàng tìm kiếm chuyến xe, lựa chọn ghế, đặt vé, thanh toán và quản lý vé trực tuyến.

Bên cạnh đó, hệ thống cung cấp các chức năng quản lý nhà xe, phương tiện, tuyến đường, lịch trình, nhân viên, kiểm vé và báo cáo hoạt động.

### Mục tiêu

- Số hóa quy trình tìm kiếm và đặt vé xe khách.
- Hỗ trợ đặt nhiều vé và mua hộ hành khách.
- Giữ ghế trong 15 phút, hạn chế tranh chấp và bán trùng ghế.
- Tích hợp thanh toán trực tuyến và vé điện tử.
- Hỗ trợ hoàn vé, kiểm vé và quản lý chuyến xe.
- Cung cấp công cụ quản trị và thống kê cho nhà xe.

> **Trạng thái dự án:** Đang phát triển. Một số API đặt vé, báo giá, thanh toán VNPay và yêu cầu hoàn tiền đã có trong mã nguồn Backend; việc tích hợp đầu cuối và môi trường triển khai cần được đánh giá theo kết quả kiểm thử. Một số màn hình Frontend vẫn dùng dữ liệu mẫu. Tài liệu mô tả cả API đã triển khai trong mã nguồn và phần còn đề xuất.

---

## ✨ Tính Năng

### 🔍 Khách vãng lai

- Tìm kiếm chuyến theo điểm đi, điểm đến và ngày khởi hành.
- Xem thông tin chuyến, nhà xe, giá vé và sơ đồ ghế.
- Đăng ký và đăng nhập tài khoản.

### 👤 Khách hàng

- Tìm kiếm, lựa chọn và giữ ghế trong 15 phút.
- Đặt nhiều vé trong cùng một đơn, hỗ trợ mua hộ.
- Áp dụng mã khuyến mãi và thanh toán trực tuyến.
- Xem lịch sử đặt vé và vé điện tử có mã QR.
- Yêu cầu hoàn vé và theo dõi trạng thái hoàn.
- Đánh giá chuyến xe khi đủ điều kiện.

### 🏢 Người quản lý nhà xe

- Quản lý xe, loại xe và sơ đồ ghế.
- Quản lý tuyến đường, lịch trình và chuyến xe.
- Quản lý nhân viên và phân công chuyến.
- Mở bán, theo dõi và hủy chuyến theo quy định.
- Xem danh sách hành khách và báo cáo hoạt động.

### 🎫 Nhân viên nhà xe

- Xem các chuyến được phân công.
- Xem danh sách hành khách.
- Kiểm tra vé bằng mã QR hoặc mã vé.
- Xác nhận hành khách lên xe.

### 🛡️ Quản trị viên

- Quản lý tài khoản và phân quyền.
- Quản lý nhà xe và danh mục hệ thống.
- Quản lý khuyến mãi và phương thức thanh toán.
- Theo dõi giao dịch, thống kê và báo cáo.
- Kiểm duyệt đánh giá.

### 🔐 Quy tắc nghiệp vụ chính

- Ghế được giữ tối đa **15 phút**, do Backend xác định.
- Một ghế trên một chuyến không được bán cho hai vé còn hiệu lực.
- Một đơn có thể chứa nhiều vé cho cùng chuyến.
- Người đi không bắt buộc phải có tài khoản.
- Vé chỉ được phát hành sau khi thanh toán được Backend xác minh.
- Hỗ trợ hoàn từng vé đủ điều kiện trong đơn nhiều vé.
- Nhân viên chỉ được kiểm vé trong phạm vi được phân công.

---

## 🛠️ Công Nghệ Sử Dụng

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Jakarta Servlet, Maven |
| ORM | JPA, Hibernate |
| Frontend | Next.js, React, TypeScript |
| Database | PostgreSQL |
| Application Server | Apache Tomcat |
| Containerization | Docker, Docker Compose |
| Version Control | Git, GitHub |
| Project Management | Plane |
| Modeling | PlantUML |

---

## 🏗️ Kiến Trúc Hệ Thống

Hệ thống được xây dựng theo kiến trúc **Frontend–Backend tách biệt**, với Backend tổ chức theo các tầng xử lý.

```text
┌─────────────────────────────────┐
│           FRONTEND              │
│     Next.js / React / TS        │
└────────────────┬────────────────┘
                 │ HTTP / REST API
┌────────────────▼────────────────┐
│           CONTROLLER            │
│         Jakarta Servlet         │
└────────────────┬────────────────┘
                 │
┌────────────────▼────────────────┐
│             SERVICE             │
│          Business Logic         │
└────────────────┬────────────────┘
                 │
┌────────────────▼────────────────┐
│           REPOSITORY            │
│          JPA / Hibernate        │
└────────────────┬────────────────┘
                 │
┌────────────────▼────────────────┐
│            DATABASE             │
│           PostgreSQL            │
└─────────────────────────────────┘
```

### Project Structure

```text
HE_THONG_DAT_VE_XE_KHACH/
│
├── .github/                # CI và Pull Request
├── docs/
│   ├── requirements/       # Yêu cầu và Use Case
│   ├── diagrams/           # Sơ đồ hệ thống
│   ├── database/           # Schema và migration
│   ├── api/                # API contracts
│   ├── plans/              # Kế hoạch
│   └── tasks/              # Tài liệu công việc
│
├── src/
│   ├── backend/            # Java Servlet, JPA
│   └── frontend/           # Next.js, React
│
├── tests/                  # Kiểm thử
├── scripts/                # Công cụ migration
├── docker-compose.yml
├── .env.example
├── CONTRIBUTING.md
└── README.md
```

---

## 🚀 Cài Đặt và Chạy Dự Án

### Yêu cầu

- Git
- Java JDK 21
- Apache Maven 3.9+
- Node.js 22
- Docker Desktop và Docker Compose v2

Các lệnh dưới đây dành cho **PowerShell trên Windows**.

### 1. Clone Repository

```powershell
git clone https://github.com/minhhiu1586/HE_THONG_DAT_VE_XE_KHACH.git

cd HE_THONG_DAT_VE_XE_KHACH
```

### 2. Cấu hình môi trường

```powershell
Copy-Item .env.example .env
```

Cập nhật các giá trị cần thiết trong `.env`, đặc biệt là thông tin PostgreSQL.

**Không commit file `.env` lên GitHub.**
File `.env` ở thư mục gốc được Docker Compose đọc; nếu chạy Next.js riêng và cần đổi API URL, đặt `NEXT_PUBLIC_API_URL` trong môi trường frontend trước khi chạy `npm run dev`.

### 3. Build Backend và chạy Database

```powershell
mvn -B -f src/backend/pom.xml clean verify

docker compose up -d postgres

docker compose run --rm migrate

docker compose up -d backend
```

Kiểm tra Backend:

```powershell
curl.exe -i http://localhost:8080/api/health
```

### 4. Chạy Frontend

Mở terminal mới từ thư mục gốc dự án:

```powershell
cd src/frontend

npm ci

npm run dev
```

### Địa chỉ truy cập

| Dịch vụ | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api |
| Health Check | http://localhost:8080/api/health |

### 5. Kiểm thử

Từ thư mục gốc dự án:

```powershell
mvn -B -f src/backend/pom.xml verify

python tests/use-cases/check_matrix.py
```

Kiểm tra Frontend:

```powershell
cd src/frontend

npm run lint

npm run build
```

### 6. Dừng môi trường

```powershell
docker compose down
```

---

## ⚙️ Cấu Hình

Các biến môi trường được khai báo trong [`.env.example`](.env.example).

| Biến | Mục đích |
|---|---|
| `POSTGRES_DB` | Tên database |
| `POSTGRES_USER` | Tài khoản PostgreSQL |
| `POSTGRES_PASSWORD` | Mật khẩu PostgreSQL |
| `POSTGRES_PORT` | Cổng PostgreSQL |
| `DATABASE_URL` | JDBC URL khi chạy Backend ngoài Docker |
| `FRONTEND_ORIGIN` | Origin được Backend chấp nhận |
| `AUTH_COOKIE_SECURE` | Bật Secure Cookie khi sử dụng HTTPS |
| `NEXT_PUBLIC_API_URL` | Địa chỉ API cho Frontend |
| `BACKEND_URL` | Đích API rewrite của Next.js |

Hệ thống hiện sử dụng **Session Cookie** để quản lý phiên đăng nhập.

**Thanh toán trực tuyến:** VNPay sandbox và cấu hình Backend được mô tả trong [payment-vnpay.md](docs/api/payment-vnpay.md). Kết quả hoàn tiền cần được tra cứu/đối soát; HTTP `202` chưa xác nhận tiền đã hoàn.

---

## 🗄️ Database và API

### Database

Hệ thống sử dụng PostgreSQL với các nhóm dữ liệu chính:

- **Tài khoản:** Người dùng, nhà xe, nhân viên và phân công.
- **Vận tải:** Tỉnh/thành, bến xe, tuyến, điểm dừng, xe, ghế và chuyến.
- **Đặt vé:** Giữ chỗ, đơn đặt vé và vé xe.
- **Thanh toán:** Giao dịch, phương thức thanh toán, khuyến mãi và chính sách hoàn.
- **Đánh giá:** Đánh giá chuyến xe.

**Tài liệu chi tiết:**

- [Database Schema & Migration](docs/database/README.md)
- [Diagrams](docs/diagrams/)

### API Documentation

Backend cung cấp các nhóm API:

- Authentication & Authorization
- Locations & Routes
- Bus Operators & Vehicles
- Trips & Seat Reservation
- Booking & Tickets
- Promotion & Payment
- Refund & Check-in
- Reviews & Reports

**Tài liệu chi tiết:**

- [API Documentation](docs/api/README.md)
- [OpenAPI Specification](docs/api/openapi.yaml)
- [Ticket refund API](docs/api/ticket-refund.md)
- [Ticket refund OpenAPI subset](docs/api/ticket-refund-openapi.json)

> Tài liệu API có thể bao gồm cả endpoint đang thiết kế. Cần đối chiếu với Servlet mapping để xác định các API đã triển khai.

---

## 🌐 Deployment

**Trạng thái:** [Chưa cập nhật môi trường triển khai chính thức.]

Hiện dự án có cấu hình phát triển với Docker Compose cho PostgreSQL và Backend Tomcat; Frontend chạy bằng Next.js.

Thông tin sẽ bổ sung sau:

- **Frontend URL:** [Bổ sung sau]
- **Backend API URL:** [Bổ sung sau]
- **Hosting:** [Bổ sung sau]
- **Hướng dẫn triển khai:** [Bổ sung sau]
- **Backup & Restore:** [Bổ sung sau]

---

## 📸 Screenshots

**Trạng thái:** [Bổ sung ảnh khi các chức năng đã được triển khai và kiểm thử.]

Dự kiến bổ sung:

- Trang chủ và tìm kiếm chuyến.
- Chi tiết chuyến và sơ đồ ghế.
- Đặt vé và thanh toán.
- Vé điện tử và mã QR.
- Quản lý nhà xe.
- Kiểm vé.
- Dashboard và báo cáo.

---

## 🤝 Contributing

Dự án được phát triển theo nhóm, quản lý công việc bằng Plane và mã nguồn bằng GitHub.

Quy trình chung:

1. Nhận task trên Plane.
2. Tạo branch từ nhánh phát triển.
3. Triển khai và tự kiểm thử.
4. Commit, push và tạo Pull Request.
5. Reviewer kiểm tra trước khi merge.
6. Cập nhật trạng thái công việc.

Xem hướng dẫn chi tiết tại [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 👥 Team

### Development Team

| Thành viên | Phạm vi phụ trách chính |
|---|---|
| **Minh Hiếu** | Kiến trúc, Backend nền tảng, xác thực, phân quyền, tài khoản và nhà xe |
| **Thanh Hiền** | Danh mục, tuyến xe, xe, ghế, lịch trình và vận hành |
| **Thế Ân** | Giữ ghế, đặt vé, đơn nhiều vé và nghiệp vụ đặt chỗ |
| **Gia Bảo** | API contract, khuyến mãi, thanh toán, giao dịch và báo cáo |
| **Kim Cương** | Nhân viên, phân công, hoàn vé, kiểm vé và kiểm thử |

---

### 📌 Thông tin dự án

- **Tên dự án:** Hệ thống đặt vé xe khách trực tuyến
- **Loại dự án:** Web Application
- **Môn học:** Lập trình Web
- **Đơn vị:** Trường Đại học Công nghệ Kỹ thuật TP. Hồ Chí Minh
- **Số thành viên:** 5
- **Repository:** [HE_THONG_DAT_VE_XE_KHACH](https://github.com/minhhiu1586/HE_THONG_DAT_VE_XE_KHACH)
- **Giấy phép:** [Chưa được nhóm quyết định; bổ sung file `LICENSE` sau]

---

**🚌 Hệ thống đặt vé xe khách trực tuyến — Giải pháp quản lý và đặt vé hiện đại.**
