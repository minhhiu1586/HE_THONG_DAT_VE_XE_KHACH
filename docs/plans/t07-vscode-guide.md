# Chạy và kiểm tra T07 / T07.1 trong VS Code

Hướng dẫn dùng Terminal **Git Bash trên Windows**, chạy từ thư mục gốc repository trừ khi có chỉ dẫn khác. Frontend T07 nằm ở `src/frontend`. Linux/macOS dùng `python3` thay `py -3`.

## 1. Chuẩn bị công cụ

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

## 2. Lấy đúng nhánh

```bash
git status
git switch feat/t07-account-auth-rbac
git pull --ff-only origin feat/t07-account-auth-rbac
```

Nếu có thay đổi local, giữ lại trước khi chuyển nhánh/pull. Không dùng reset để xóa chúng.

## 3. Build backend

```bash
mvn -f src/backend/pom.xml -B clean verify
```

Mong đợi `BUILD SUCCESS`. Khi chưa truyền cấu hình database test, 38 test thông thường chạy và 9 test PostgreSQL bị bỏ qua. Mục 13 hướng dẫn chạy 43 test với dữ liệu kiểm thử T07; 4 test bổ sung đối chiếu phân quyền trên dữ liệu mới chỉ chạy khi bật cấu hình riêng.

WAR tạo tại `src/backend/target/he-thong-dat-ve-xe-khach-0.1.0-SNAPSHOT.war`.

## 4. Chạy PostgreSQL và backend

```bash
docker compose up -d postgres
docker compose ps
```

Chờ PostgreSQL `healthy`. Database mới tự chạy schema và seed. Database cũ cần áp dụng migration theo thứ tự:

```bash
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/001_route_trip.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/002_vehicle_alignment.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/003_account_auth.sql
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

## 5. Chạy frontend

Mở terminal thứ hai:

```bash
cd src/frontend
npm ci
npm run dev
```

Mở http://localhost:3000, giữ terminal chạy. Next.js chuyển tiếp `/api` tới backend http://127.0.0.1:8080. Dùng thống nhất `localhost`; nếu đổi origin frontend, cập nhật `FRONTEND_ORIGIN` của backend. Nếu đổi backend, đặt `BACKEND_URL` trước chạy/build frontend.

Module này dùng phiên cookie/CSRF; không cần JWT_SECRET trong .env.example. Localhost HTTP dùng AUTH_COOKIE_SECURE=false; HTTPS production dùng true.

## 6. Đăng ký và đăng nhập

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

## 7. Hồ sơ cá nhân

Mở http://localhost:3000/tai-khoan sau đăng nhập:

1. Đổi họ tên/điện thoại và các trường tùy chọn, nhấn Lưu hồ sơ.
2. Có thông báo thành công; tải lại vẫn giữ dữ liệu.
3. Dùng email/điện thoại của tài khoản khác: bị từ chối.
4. Cửa sổ ẩn danh chưa đăng nhập mở hồ sơ: chuyển đăng nhập.
5. Không có trường sửa vai trò/trạng thái trong form hồ sơ; kiểm tra chống sửa request ở mục 10.

## 8. Đổi mật khẩu và đăng xuất

| Thao tác | Kết quả |
| --- | --- |
| Sai mật khẩu hiện tại | Từ chối |
| Mật khẩu mới yếu | Từ chối |
| Xác nhận mới không khớp | Báo lỗi |
| Hiện tại đúng, mật khẩu mới hợp lệ | Thành công, cần đăng nhập lại |
| Đăng nhập mật khẩu cũ | Thất bại |
| Đăng nhập mật khẩu mới | Thành công |

Đăng nhập cùng tài khoản ở cửa sổ thường và ẩn danh. Đổi mật khẩu ở cửa sổ thường, tải lại hồ sơ ở cửa sổ ẩn danh: phiên cũ phải không dùng được. Nhấn Đăng xuất rồi mở hồ sơ: yêu cầu đăng nhập. Kiểm tra replay cookie ở mục 10.

## 9. Tạo ADMIN để kiểm tra khóa/mở khóa

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

## 10. API trực tiếp bằng Postman

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

## 11. Phiên hết hạn

Đăng nhập tài khoản kiểm thử, lấy UUID rồi chạy SQL:

```sql
UPDATE phien_dang_nhap
SET tao_luc = now() - interval '2 hours',
    het_han = now() - interval '1 second'
WHERE ma_nguoi_dung = 'UUID_TAI_KHOAN_KIEM_THU';
```

Postman GET hồ sơ trả401; trình duyệt tải lại hồ sơ chuyển đăng nhập và hiện hướng dẫn. Lệnh này tác động tất cả phiên của UUID kiểm thử, không dùng với tài khoản thật.

## 12. HTTP test tự động

Backend đã trả200, chạy ở thư mục gốc:

```bash
py -3 tests/backend/account_auth_smoke.py
py -3 tests/backend/api_smoke.py
py -3 tests/backend/vehicle_api_smoke.py
```

Mong đợi lần lượt **36**, **75**, **28** kiểm tra đạt, tổng139. Script tài khoản kiểm tra phiên/CSRF/role/trangThai/userId giả/ID sai kiểu/khóa/mở khóa/truy cập chéo nhà xe. Các script tự tạo và dọn fixture, cần quyền SQL trên database phát triển.

Đăng ký/đăng nhập giới hạn20 request/phút/IP. Nhận429 sau chạy thủ công nhiều lần/lặp test liên tục: chờ hết cửa sổ một phút.

## 13. Đầy đủ test PostgreSQL, gồm quyền đồng thời

Tạo database test riêng; nếu đã tồn tại và đúng database test của bạn, dùng lại:

```bash
docker exec datvexe-postgres createdb -U datvexe_user t07_test
```

Nạp lần lượt, dừng khi lỗi:

```bash
for file in \
  docs/database/schema/001_route_trip.sql \
  docs/database/schema/002_vehicle_alignment.sql \
  docs/database/schema/003_account_auth.sql \
  tests/backend/fixtures/sql/001_base.sql \
  tests/backend/fixtures/sql/002_fleet.sql
do
  docker exec -i datvexe-postgres psql \
    -U datvexe_user -d t07_test -v ON_ERROR_STOP=1 < "$file" || break
done
```

Chỉ chạy Maven nếu tất cả file nạp thành công:

```bash
mvn -f src/backend/pom.xml -B \
  -Duser.timezone=Asia/Ho_Chi_Minh \
  -Dvehicle.pg.url=jdbc:postgresql://localhost:5432/t07_test \
  -Dvehicle.pg.user=datvexe_user \
  -Dvehicle.pg.password=datvexe_password \
  clean verify
```

Mong đợi `Tests run: 47, Failures: 0, Errors: 0, Skipped: 4` và BUILD SUCCESS: 43 test được chạy và đạt; 4 test bổ sung yêu cầu dữ liệu mới được bỏ qua trong cấu hình này. Có23 test tài khoản/phân quyền và20 test hồi quy tuyến/xe. PostgreSQL bao phủ khóa tài khoản/thu hồi phân công/đổi phân công đồng thời. Báo cáo: `src/backend/target/surefire-reports`.

## 14. Build frontend và đọc code

Tại src/frontend:

```bash
npm run build
npm run lint
```

Build thành công; lint0 lỗi, còn3 warning có sẵn ở lib/api.ts và lib/mock-data.ts. Nếu backend đang chạy trong lúc clean verify, WAR bị thay nên cần restart backend và chờ sẵn sàng trước HTTP test.

Java trong bảng nằm dưới `src/backend/src/main/java/com/datvexe/`:

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
| Giao diện và trạng thái đăng nhập | src/frontend/components/Account/ |
| Migration | docs/database/schema/003_account_auth.sql |

## Tài liệu đối chiếu

- [Báo cáo rà soát từng yêu cầu](../tasks/T07-review.md).
- [Ma trận kiểm thử](../tasks/T07-test-matrix.md).
- [API tài khoản](../api/account-auth.md).
- [Tích hợp helper T07.1](../tasks/T07.1-integration.md).

Chủ đơn/vé và phân công kiểm vé đã được test ở mức helper. API nghiệp vụ hoàn tiền/kiểm vé thuộc module triển khai sau; chưa tính là đã nghiệm thu end-to-end các API đó. T01 chính thức và việc PR được nhóm review/chấp nhận cần xác nhận riêng.
