# NguyenMinhHieu — Hướng dẫn demo trên VS Code

Các mục dưới đây được gộp từ tài liệu hiện có; kết quả kiểm thử lịch sử giữ nguyên thời điểm và giới hạn của lần chạy.

## Mục lục

- [Chạy và kiểm tra T07 / T07.1 trong VS Code](#section-1)
- [Hướng dẫn chạy và kiểm tra T08 trên VS Code](#section-2)

<a id="section-1"></a>

## Chạy và kiểm tra T07 / T07.1 trong VS Code

Hướng dẫn dùng Terminal **Git Bash trên Windows**, chạy từ thư mục gốc repository trừ khi có chỉ dẫn khác. Frontend T07 nằm ở `apps/frontend`. Linux/macOS dùng `python3` thay `py -3`.

### 1. Chuẩn bị công cụ

Cần Java 21, Maven, Node.js từ 20.9 trở lên, Docker Desktop, Git, Python 3. Mở repository trong VS Code → Terminal → New Terminal → chọn Git Bash.

```bash
java -version
mvn -version
node -v
npm -v
docker compose version
py -3 --version
```

Maven phải dùng Java 21; Docker Desktop đang chạy Linux containers.

### 2. Lấy đúng nhánh

```bash
git status
git switch feat/t07-account-auth-rbac
git pull --ff-only origin feat/t07-account-auth-rbac
```

Nếu có thay đổi local, giữ lại trước khi chuyển nhánh/pull. Không dùng reset để xóa chúng.

### 3. Build backend

```bash
mvn -f apps/backend/pom.xml -B clean verify
```

Mong đợi `BUILD SUCCESS`. Khi chưa truyền cấu hình database test, 38 test thông thường chạy và 9 test PostgreSQL bị bỏ qua. Mục 13 hướng dẫn chạy 43 test với dữ liệu kiểm thử T07; 4 test bổ sung đối chiếu phân quyền trên dữ liệu mới chỉ chạy khi bật cấu hình riêng.

WAR tạo tại `apps/backend/target/he-thong-dat-ve-xe-khach-0.1.0-SNAPSHOT.war`.

### 4. Chạy PostgreSQL và backend

```bash
docker compose up -d postgres
docker compose ps
```

Chờ PostgreSQL `healthy`. Database mới tự chạy schema và seed. Database cũ cần áp dụng migration theo thứ tự:

```bash
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/001_route_trip.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/002_vehicle_alignment.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/003_account_auth.sql
```

Dừng và xử lý lỗi nếu bất kỳ migration nào thất bại. Không xóa volume để nâng cấp database cũ. Database phát triển chưa có demo có thể nạp:

```bash
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < tests/backend/fixtures/sql/001_base.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < tests/backend/fixtures/sql/002_fleet.sql
```

Seed là dữ liệu kiểm thử, có thể điều chỉnh bản ghi demo cũ; chỉ nạp trên database phát triển.

```bash
docker compose up -d backend
curl -i http://localhost:8080/api/tinh-thanh
```

Mong đợi HTTP 200 và JSON. Tomcat cần thời gian khởi động; gọi lại khi sẵn sàng.

```bash
docker compose logs --tail=100 backend
docker compose logs --tail=100 postgres
```

Sau mỗi lần build WAR mới, chạy `docker compose restart backend` và chờ API sẵn sàng.

### 5. Chạy frontend

Mở terminal thứ hai:

```bash
cd apps/frontend
npm ci
npm run dev
```

Mở http://localhost:3000, giữ terminal chạy. Next.js chuyển tiếp `/api` tới backend http://127.0.0.1:8080. Dùng thống nhất `localhost`; nếu đổi origin frontend, cập nhật `FRONTEND_ORIGIN` của backend. Nếu đổi backend, đặt `BACKEND_URL` trước chạy/build frontend.

Module này dùng phiên cookie/CSRF; không cần JWT_SECRET trong .env.example. Localhost HTTP dùng AUTH_COOKIE_SECURE=false; HTTPS production dùng true.

### 6. Đăng ký và đăng nhập

Mở http://localhost:3000/dang-ky. Dữ liệu ví dụ dành cho kiểm thử:

| Trường | Giá trị |
| --- | --- |
| Họ tên | Khách hàng kiểm thử |
| Email | khach.t07@example.test |
| Số điện thoại | 0901234567 |
| Mật khẩu / xác nhận | TestTaiKhoan123 |

Nếu định danh đã có, dùng email/số điện thoại khác.

| Thao tác | Kết quả mong đợi |
| --- | --- |
| Bỏ trống trường bắt buộc | Không cho đăng ký |
| Email/điện thoại sai | Báo lỗi phù hợp |
| Hai mật khẩu khác nhau | Báo lỗi xác nhận |
| Mật khẩu không đạt policy 10–128 ký tự, có chữ và số | Backend từ chối |
| Dữ liệu hợp lệ | Đăng ký thành công |
| Đăng ký lại định danh đã dùng | 409, báo thông tin đã sử dụng |
| Đăng nhập sai mật khẩu | Từ chối, thông báo chung |
| Đăng nhập đúng | Chuyển hồ sơ, hiển thị tên |

Đăng nhập tại http://localhost:3000/dang-nhap. Kiểm tra database:

```bash
docker exec -it datvexe-postgres psql -U datvexe_user -d datvexe
```

Trong SQL:

```sql
SELECT ma_nguoi_dung, ho_ten, email, loai_tai_khoan, trang_thai,
       left(mat_khau, 20) AS dau_hash
FROM nguoi_dung
WHERE email = 'khach.t07@example.test';
```

Mong đợi KHACH_HANG, HOAT_DONG, hash bắt đầu `pbkdf2-sha256$`; không lưu mật khẩu rõ. Gõ `\q` để thoát psql.

### 7. Hồ sơ cá nhân

Mở http://localhost:3000/tai-khoan sau đăng nhập:

1. Đổi họ tên/điện thoại và các trường tùy chọn, nhấn Lưu hồ sơ.
2. Có thông báo thành công; tải lại vẫn giữ dữ liệu.
3. Dùng email/điện thoại của tài khoản khác: bị từ chối.
4. Cửa sổ ẩn danh chưa đăng nhập mở hồ sơ: chuyển đăng nhập.
5. Không có trường sửa vai trò/trạng thái trong form hồ sơ; kiểm tra chống sửa request ở mục 10.

### 8. Đổi mật khẩu và đăng xuất

| Thao tác | Kết quả |
| --- | --- |
| Sai mật khẩu hiện tại | Từ chối |
| Mật khẩu mới yếu | Từ chối |
| Xác nhận mới không khớp | Báo lỗi |
| Hiện tại đúng, mật khẩu mới hợp lệ | Thành công, cần đăng nhập lại |
| Đăng nhập mật khẩu cũ | Thất bại |
| Đăng nhập mật khẩu mới | Thành công |

Đăng nhập cùng tài khoản ở cửa sổ thường và ẩn danh. Đổi mật khẩu ở cửa sổ thường, tải lại hồ sơ ở cửa sổ ẩn danh: phiên cũ phải không dùng được. Nhấn Đăng xuất rồi mở hồ sơ: yêu cầu đăng nhập. Kiểm tra replay cookie ở mục 10.

### 9. Tạo ADMIN để kiểm tra khóa/mở khóa

Đăng ký tài khoản thứ hai: admin.t07@example.test / 0901234568 / TestQuanTri123. Không có tài khoản quản trị mặc định; API đăng ký không nhận role.

Mở psql như mục 6, xác minh đúng tài khoản:

```sql
SELECT ma_nguoi_dung, ho_ten, email
FROM nguoi_dung WHERE email = 'admin.t07@example.test';
```

Sao chép UUID, thay đúng vào cả hai câu lệnh:

```sql
BEGIN;
UPDATE nguoi_dung SET loai_tai_khoan = 'ADMIN'
WHERE ma_nguoi_dung = 'UUID_VUA_SAO_CHEP';
DELETE FROM phien_dang_nhap
WHERE ma_nguoi_dung = 'UUID_VUA_SAO_CHEP';
COMMIT;
```

Thoát psql, đăng nhập lại ADMIN, mở http://localhost:3000/quan-tri/tai-khoan.

1. Đăng nhập khách hàng ở cửa sổ ẩn danh.
2. ADMIN khóa khách hàng; tải lại hồ sơ khách → từ chối, chuyển đăng nhập.
3. Đăng nhập khách khi bị khóa → thất bại.
4. ADMIN mở khóa → khách đăng nhập mới được; phiên cũ không tự sống lại.
5. Nút khóa chính ADMIN hiện tại bị vô hiệu hóa.
6. Khách mở trang quản trị → thông báo thiếu quyền.

### 10. API trực tiếp bằng Postman

Biến `baseUrl = http://localhost:8080/api`. POST `{{baseUrl}}/auth/dang-nhap`, headers:

```text
Content-Type: application/json
X-Requested-With: datvexe
```

Body → raw → JSON:

```json
{"email":"khach.t07@example.test","matKhau":"MAT_KHAU_HIEN_TAI"}
```

200 trả nguoiDung/csrfToken/hetHan. Postman lưu cookie datvexe_session; lưu csrfToken vào biến `csrfToken`. Cookie Postman và cookie trình duyệt là hai phiên riêng. Request mutation đã đăng nhập gửi:

```text
Content-Type: application/json
X-CSRF-Token: {{csrfToken}}
```

| Request / điều kiện | Mong đợi |
| --- | --- |
| GET /tai-khoan/me không cookie | 401 |
| GET /tai-khoan/me phiên hợp lệ | 200 |
| GET /quan-tri/tai-khoan bằng khách | 403 |
| PUT /tai-khoan/me thiếu CSRF | 403 |
| GET /tai-khoan/ID_NGUOI_KHAC bằng khách | 403 |
| POST /auth/dang-xuat với cookie/CSRF | 204 |
| GET hồ sơ với cookie đã logout | 401 |

Trước logout, lưu giá trị cookie cũ. Sau logout, dùng cookie đó cho GET hồ sơ (tránh gửi thêm cookie khác trong cookie jar) để kiểm tra replay.

Chống nâng quyền: PUT /tai-khoan/me với body:

```json
{
  "hoTen":"Khách hàng kiểm thử",
  "email":"khach.t07@example.test",
  "soDienThoai":"0901234567",
  "gioiTinh":null,
  "ngaySinh":null,
  "cccd":null,
  "loaiTaiKhoan":"ADMIN"
}
```

Phải trả400, vai trò không đổi. Thử thay trường đặc quyền bằng role/trangThai/userId cũng phải bị từ chối. Bỏ trường đặc quyền thì dữ liệu hồ sơ hợp lệ cập nhật được.

Lỗi có code/message/details/timestamp/traceId; X-Trace-Id phải khớp traceId. Không trả mật khẩu/hash hoặc dữ liệu tài nguyên ngoài phạm vi.

### 11. Phiên hết hạn

Đăng nhập tài khoản kiểm thử, lấy UUID rồi chạy SQL:

```sql
UPDATE phien_dang_nhap
SET tao_luc = now() - interval '2 hours',
    het_han = now() - interval '1 second'
WHERE ma_nguoi_dung = 'UUID_TAI_KHOAN_KIEM_THU';
```

Postman GET hồ sơ trả401; trình duyệt tải lại hồ sơ chuyển đăng nhập và hiện hướng dẫn. Lệnh này tác động tất cả phiên của UUID kiểm thử, không dùng với tài khoản thật.

### 12. HTTP test tự động

Backend đã trả200, chạy ở thư mục gốc:

```bash
py -3 tests/backend/account_auth_smoke.py
py -3 tests/backend/api_smoke.py
py -3 tests/backend/vehicle_api_smoke.py
```

Mong đợi lần lượt **36**, **75**, **28** kiểm tra đạt, tổng139. Script tài khoản kiểm tra phiên/CSRF/role/trangThai/userId giả/ID sai kiểu/khóa/mở khóa/truy cập chéo nhà xe. Các script tự tạo và dọn fixture, cần quyền SQL trên database phát triển.

Đăng ký/đăng nhập giới hạn20 request/phút/IP. Nhận429 sau chạy thủ công nhiều lần/lặp test liên tục: chờ hết cửa sổ một phút.

### 13. Đầy đủ test PostgreSQL, gồm quyền đồng thời

Tạo database test riêng; nếu đã tồn tại và đúng database test của bạn, dùng lại:

```bash
docker exec datvexe-postgres createdb -U datvexe_user t07_test
```

Nạp lần lượt, dừng khi lỗi:

```bash
for file in \
  database/migrations/001_route_trip.sql \
  database/migrations/002_vehicle_alignment.sql \
  database/migrations/003_account_auth.sql \
  tests/backend/fixtures/sql/001_base.sql \
  tests/backend/fixtures/sql/002_fleet.sql
do
  docker exec -i datvexe-postgres psql \
    -U datvexe_user -d t07_test -v ON_ERROR_STOP=1 < "$file" || break
done
```

Chỉ chạy Maven nếu tất cả file nạp thành công:

```bash
mvn -f apps/backend/pom.xml -B \
  -Duser.timezone=Asia/Ho_Chi_Minh \
  -Dvehicle.pg.url=jdbc:postgresql://localhost:5432/t07_test \
  -Dvehicle.pg.user=datvexe_user \
  -Dvehicle.pg.password=datvexe_password \
  clean verify
```

Mong đợi `Tests run: 47, Failures: 0, Errors: 0, Skipped: 4` và BUILD SUCCESS: 43 test được chạy và đạt; 4 test bổ sung yêu cầu dữ liệu mới được bỏ qua trong cấu hình này. Có23 test tài khoản/phân quyền và20 test hồi quy tuyến/xe. PostgreSQL bao phủ khóa tài khoản/thu hồi phân công/đổi phân công đồng thời. Báo cáo: `apps/backend/target/surefire-reports`.

### 14. Build frontend và đọc code

Tại apps/frontend:

```bash
npm run build
npm run lint
```

Build thành công; lint0 lỗi, còn3 warning có sẵn ở lib/api.ts và lib/mock-data.ts. Nếu backend đang chạy trong lúc clean verify, WAR bị thay nên cần restart backend và chờ sẵn sàng trước HTTP test.

Java trong bảng nằm dưới `apps/backend/src/main/java/com/datvexe/`:

| Chức năng | File |
| --- | --- |
| Endpoint tài khoản | controller/AccountController.java |
| Nghiệp vụ tài khoản/phiên/password/trạng thái | service/AccountService.java |
| DTO được phép nhận | dto/AccountInputs.java |
| Băm mật khẩu | security/Passwords.java |
| Xác thực request/cookie/CSRF | security/AuthFilter.java |
| Vai trò/scope endpoint | security/AccessPolicy.java |
| Chủ đơn/vé/nhà xe/phân công | security/AccessControl.java |
| Phiên DB | domain/AuthSession.java |
| Giao diện và trạng thái đăng nhập | apps/frontend/components/Account/ |
| Migration | database/migrations/003_account_auth.sql |

### Tài liệu đối chiếu

- [Báo cáo rà soát từng yêu cầu](../testing/existing/t07-ra-soat-tai-khoan-va-phan-quyen.md).
- [Ma trận kiểm thử](../testing/existing/t07-ma-tran-kiem-thu-tai-khoan.md).
- [API tài khoản](../../../docs/api/tai-khoan-va-xac-thuc.md).
- [Tích hợp helper T07.1](../testing/existing/t07-1-tich-hop-phan-quyen.md).

Chủ đơn/vé và phân công kiểm vé đã được test ở mức helper. API nghiệp vụ hoàn tiền/kiểm vé thuộc module triển khai sau; chưa tính là đã nghiệm thu end-to-end các API đó. T01 chính thức và việc PR được nhóm review/chấp nhận cần xác nhận riêng.

<a id="section-2"></a>

## Hướng dẫn chạy và kiểm tra T08 trên VS Code

T08 quản lý tỉnh/thành phố, bến xe, tuyến xe và điểm dừng. Hướng dẫn dùng **Git Bash trên Windows**. Các lệnh chạy từ thư mục gốc `HE_THONG_DAT_VE_XE_KHACH11`, trừ phần frontend. Linux/macOS thay `py -3` bằng `python3`.

### 1. Chuẩn bị và lấy đúng nhánh

Mở Docker Desktop, chờ Docker chạy. Mở thư mục dự án trong VS Code, chọn **Terminal → New Terminal → Git Bash**.

```bash
java -version
mvn -version
node -v
npm -v
docker compose version
py -3 --version
```

Cần Java 21, Maven dùng Java 21, Node.js từ 20.9 trở lên, Python 3 và Docker Desktop chạy Linux containers.

```bash
git status
git fetch origin
git switch feat/t08-route-station-management
git pull --ff-only origin feat/t08-route-station-management
git branch --show-current
```

Nếu `git switch` báo không tìm thấy nhánh, chạy:

```bash
git switch --track origin/feat/t08-route-station-management
```

Kết quả tên nhánh phải là `feat/t08-route-station-management`. Nếu có thay đổi local cần giữ, lưu chúng trước khi đổi nhánh; không dùng reset để xóa.

### 2. Build backend và chạy test Java — terminal 1

Đứng ở thư mục gốc:

```bash
mvn -f apps/backend/pom.xml -B clean verify
```

Mong đợi `BUILD SUCCESS`, không có test thất bại hoặc lỗi. Khi chưa truyền database kiểm thử, các test PostgreSQL sẽ được bỏ qua; các test nghiệp vụ dùng H2 vẫn chạy. WAR được tạo tại `apps/backend/target/he-thong-dat-ve-xe-khach-0.1.0-SNAPSHOT.war`.

### 3. Chạy PostgreSQL và backend — terminal 1

```bash
docker compose up -d postgres
docker compose ps
```

Chờ PostgreSQL hiện `healthy`, rồi chạy:

```bash
docker compose up -d backend
docker compose restart backend
curl -i http://localhost:8080/api/tinh-thanh
```

Mong đợi **HTTP 200** và JSON danh sách tỉnh. Backend cần thời gian khởi động; gọi lại nếu chưa sẵn sàng. Sau mỗi lần build WAR mới, restart backend để sử dụng bản mới.

Nếu gặp lỗi, xem log:

```bash
docker compose logs --tail=100 backend
docker compose logs --tail=100 postgres
```

Database mới tự nạp schema/seed theo Compose. Nếu database cũ thiếu bảng hoặc cột, áp dụng schema theo thứ tự:

```bash
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/001_route_trip.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/002_vehicle_alignment.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < database/migrations/003_account_auth.sql
docker compose restart backend
```

Dừng lại nếu một lệnh schema thất bại. Không xóa volume để cập nhật database. Nếu báo trùng tên container `datvexe-postgres`, kiểm tra container đang có trước khi xử lý; không xóa dữ liệu theo suy đoán. Không cần mở pgAdmin để chạy hoặc test.

### 4. Chạy frontend — terminal 2

Bấm dấu **+** trong Terminal để mở terminal mới. Từ thư mục gốc:

```bash
cd apps/frontend
npm ci
npm run dev
```

Giữ terminal này chạy. Mở Chrome/Edge tại **http://localhost:3000**. Frontend nằm trong `apps/frontend`; Next.js chuyển tiếp `/api` đến backend `http://127.0.0.1:8080`.

Dùng thống nhất `localhost:3000`. Nếu Next tự chuyển sang cổng khác vì 3000 đang bận, xử lý ứng dụng đang chiếm cổng hoặc cập nhật `FRONTEND_ORIGIN` của backend cho đúng cổng. Nếu đổi backend, đặt `BACKEND_URL` trước khi chạy/build frontend.

### 5. Đăng nhập ADMIN

Nếu đã có ADMIN từ T07, đăng nhập tài khoản đó và mở **http://localhost:3000/quan-tri/tuyen-duong**. Phải thấy ba nút **Tuyến xe**, **Bến xe**, **Tỉnh/thành**.

Nếu chưa có ADMIN:

1. Mở **http://localhost:3000/dang-ky**.
2. Đăng ký email `admin.t08@example.test`, họ tên và số điện thoại hợp lệ; tự đặt mật khẩu và ghi nhớ.
3. Trong terminal tại thư mục gốc, mở psql:

```bash
docker exec -it datvexe-postgres psql -U datvexe_user -d datvexe
```

4. Khi thấy `datvexe=#`, chạy SQL sau trên database phát triển cục bộ:

```sql
BEGIN;
UPDATE nguoi_dung
SET loai_tai_khoan = 'ADMIN'
WHERE email = 'admin.t08@example.test'
RETURNING email, loai_tai_khoan;

DELETE FROM phien_dang_nhap
WHERE ma_nguoi_dung IN (
    SELECT ma_nguoi_dung FROM nguoi_dung
    WHERE email = 'admin.t08@example.test'
);
COMMIT;
```

Phải thấy email trên có vai trò `ADMIN`. `UPDATE 0` nghĩa là tài khoản chưa tồn tại hoặc email không đúng. Nếu dùng email khác, sửa email trong cả hai câu lệnh.

5. Thoát psql bằng `\q` (dấu gạch chéo ngược).
6. Trên trình duyệt, đăng xuất rồi đăng nhập lại bằng mật khẩu đã đăng ký.
7. Mở **http://localhost:3000/quan-tri/tuyen-duong**.

Tên hiển thị “admin” không quyết định quyền; tài khoản phải có vai trò `ADMIN` trong database.

### 6. Kiểm tra bằng giao diện

Dùng dữ liệu mới để tránh sửa các tuyến demo đã có chuyến. Nếu mã dưới đây đã tồn tại từ lần thử trước, dùng mã mới hoặc tiếp tục với bản ghi cũ.

#### 6.1. Tỉnh/thành phố

Chọn **Tỉnh/thành**:

- Tạo `KT_T08_A`, tên `Tỉnh kiểm thử A`, khu vực `Miền thử` → thành công.
- Tạo `KT_T08_B`, tên `Tỉnh kiểm thử B` → thành công.
- Tạo lại mã `KT_T08_A` → báo lỗi trùng mã.
- Bấm **Sửa** ở tỉnh A, đổi tên, bấm **Lưu thay đổi** → dữ liệu mới xuất hiện.
- Tìm theo mã hoặc tên → hiển thị tỉnh phù hợp.

#### 6.2. Bến xe

Chọn **Bến xe**:

- Tạo mã `KT_T08_BEN_A`, tên `Bến kiểm thử A`, địa chỉ `Địa chỉ A`, chọn tỉnh A và trạng thái `HOAT_DONG`.
- Tạo mã `KT_T08_BEN_B`, tên `Bến kiểm thử B`, địa chỉ `Địa chỉ B`, chọn tỉnh B và trạng thái `HOAT_DONG`.
- Cả hai lần phải thành công.
- Bấm **Sửa** bến A, đổi địa chỉ, lưu → hiển thị địa chỉ mới.
- Chọn tỉnh A ở bộ lọc và bấm **Lọc bến xe** → chỉ hiện bến thuộc tỉnh A. Chọn lại `Tất cả` để xem đủ bến.
- Tìm bằng mã/tên bến → hiển thị dữ liệu tương ứng.

#### 6.3. Tuyến xe

Chọn **Tuyến xe**, tạo:

| Trường | Giá trị |
| --- | --- |
| Mã tuyến | `KT_T08_TUYEN` |
| Tên tuyến | `Tuyến kiểm thử T08` |
| Bến khởi hành | Bến kiểm thử A |
| Bến đến | Bến kiểm thử B |
| Giờ mặc định | `08:00` |
| Thời lượng dự kiến | `2.5` giờ |
| Giá cơ bản | `150000` đồng |

Bấm **Tạo tuyến** → thành công. Tuyến chưa có điểm dừng trung gian vẫn hợp lệ.

- Cột trạng thái trong danh sách chỉ hiển thị. Bấm **Sửa** đúng dòng tuyến, chọn **Trạng thái khai thác** là `DANG_KHAI_THAC`, bấm **Lưu trạng thái** → thành công.
- Bấm **Sửa** đúng dòng tuyến, sửa mô tả/thời lượng, bấm **Lưu tuyến** → thành công khi chưa có chuyến.
- Thử tạo tuyến khác với cùng bến A ở cả hai đầu → bị từ chối.
- Thử thời lượng `0` hoặc âm → không cho lưu.
- Lọc theo bến đi, bến đến, trạng thái rồi bấm **Lọc tuyến** → kết quả đúng bộ lọc. Xóa bộ lọc khi muốn xem lại toàn bộ.

#### 6.4. Điểm dừng

Bấm **Điểm dừng** ở dòng `KT_T08_TUYEN`, kiểm tra tiêu đề đang chỉ đúng mã tuyến:

1. Thêm mã `KT_T08_D1`, tên `Điểm dừng 1`, địa chỉ `Địa chỉ điểm 1`, thời gian `5` phút.
2. Thêm mã `KT_T08_D2`, tên `Điểm dừng 2`, địa chỉ `Địa chỉ điểm 2`, thời gian `10` phút.
3. Danh sách phải có thứ tự 1, 2.
4. Bấm **Sửa**, đổi tên/thời gian, bấm **Lưu điểm dừng** → hiển thị dữ liệu mới.
5. Bấm **↑ / ↓** → đổi thứ tự đúng; tải lại trang vẫn giữ thứ tự.
6. Nhập thời gian dừng `-1` → không cho lưu.
7. Xóa một điểm → điểm còn lại được đánh lại thứ tự từ 1.

#### 6.5. Bảo vệ dữ liệu và quyền

- Khi tuyến vẫn dùng hai bến, thử xóa bến A → bị từ chối.
- Khi tỉnh A vẫn có bến, thử xóa tỉnh A → bị từ chối.
- Với tuyến đã có chuyến, sửa cấu hình/điểm dừng hoặc xóa tuyến → bị từ chối để giữ lịch sử. Đổi trạng thái khai thác vẫn được phép.
- Đăng nhập bằng khách hàng trong cửa sổ ẩn danh; mở `/quan-tri/tuyen-duong` → thông báo không có quyền.
- Mở trang đó khi chưa đăng nhập → chuyển đến đăng nhập.

Không tự nâng vai trò khách hàng thành ADMIN để kiểm tra trường hợp thiếu quyền. Các trường hợp tạo chuyến từ tuyến ngừng khai thác, tham chiếu tỉnh không tồn tại, lỗi giữa giao dịch và truy cập chéo nhà xe được kiểm tra bằng test tự động; giao diện có thể chặn nhập các dữ liệu đó ngay từ đầu.

#### 6.6. Xóa dữ liệu thử chưa có chuyến

Nếu muốn dọn dữ liệu vừa tạo, xóa theo thứ tự: **tuyến → hai bến → hai tỉnh**. Chỉ thao tác trên các mã kiểm thử của bạn. Nếu tuyến đã có chuyến, không xóa cứng tuyến đó.

### 7. Chạy test API — terminal 3

Mở terminal mới bằng dấu **+**. Phải đứng ở thư mục gốc:

```bash
pwd
```

Nếu đang ở `apps/frontend`, chạy `cd ../..`. Sau đó:

```bash
py -3 tests/backend/api_smoke.py
```

Backend, PostgreSQL và Docker Desktop phải đang chạy. Script tự đăng ký ADMIN kiểm thử tạm, chuẩn bị dữ liệu riêng và dọn các bản ghi của lượt chạy; chạy trên database phát triển.

Kết quả mong đợi:

```text
PASS: station/route CRUD and endpoints
PASS: stop insert/reorder/delete and invalid lists
PASS: trip create, overnight, vehicle assignment/conflicts, reschedule rollback
PASS: search by station/city/date, no trips, cancelled/completed and lifecycle
PASS: validation/404/409 and JSON error responses
PASS: 5 API scenarios, 93 HTTP responses checked
```

Nếu có `AssertionError`, test chưa đạt: giữ toàn bộ lỗi để kiểm tra. Lỗi `can't open file ...apps/frontend/tests/...` nghĩa là chạy sai thư mục; trở về gốc rồi chạy lại.

### 8. Kiểm tra frontend build và lint

Tại terminal 2 trong `apps/frontend`, nhấn **Ctrl+C** để dừng dev server:

```bash
npm run build
npm run lint
```

Mong đợi build thành công, lint **0 errors**. Hiện có 3 cảnh báo unused variable cũ trong `lib/api.ts` và `lib/mock-data.ts`. Chạy lại giao diện bằng `npm run dev`.

### 9. Test PostgreSQL riêng — tùy chọn

Các test PostgreSQL phải dùng database kiểm thử riêng, không dùng database ứng dụng `datvexe`. Tạo một database mới chưa tồn tại:

```bash
docker exec datvexe-postgres createdb -U datvexe_user t08_test
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < database/migrations/001_route_trip.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < database/migrations/002_vehicle_alignment.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < database/migrations/003_account_auth.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < tests/backend/fixtures/sql/001_base.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < tests/backend/fixtures/sql/002_fleet.sql
mvn -f apps/backend/pom.xml -B -Duser.timezone=Asia/Ho_Chi_Minh -Dvehicle.pg.url=jdbc:postgresql://localhost:5432/t08_test -Dvehicle.pg.user=datvexe_user -Dvehicle.pg.password=datvexe_password verify
```

Nếu database `t08_test` đã có, không chạy lại `createdb`; kiểm tra schema/seed hiện có trước. Dừng khi nạp schema/seed báo lỗi. Mong đợi **54 test: 50 thực thi đạt, 4 test dành riêng cho seed T06 bị bỏ qua**, `BUILD SUCCESS`. Không cần thay đổi hướng dẫn hoặc bật bộ seed T06 cho phần kiểm tra này.

Lệnh `verify` tạo lại WAR; nếu backend đang chạy, restart backend và chờ API sẵn sàng trước khi test HTTP tiếp.

### 10. Tài liệu đối chiếu

- [Bảng yêu cầu và kết quả T08](../testing/existing/t08-ra-soat-tuyen-va-ben-xe.md).
- [Hợp đồng API T08](../../../docs/api/t08-quan-ly-tuyen-va-ben-xe.md).

Các test đã đạt không thay thế bước review PR. Nhánh hiện chưa có ma trận T01 chính thức; quyền đang áp dụng ADMIN cho tỉnh/bến và ADMIN/NHA_XE theo phạm vi cho tuyến, cần đối chiếu khi nhóm cung cấp ma trận đã duyệt.
