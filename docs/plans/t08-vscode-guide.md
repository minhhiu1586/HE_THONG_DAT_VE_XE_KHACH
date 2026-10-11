# Hướng dẫn chạy và kiểm tra T08 trên VS Code

T08 quản lý tỉnh/thành phố, bến xe, tuyến xe và điểm dừng. Hướng dẫn dùng **Git Bash trên Windows**. Các lệnh chạy từ thư mục gốc `HE_THONG_DAT_VE_XE_KHACH11`, trừ phần frontend. Linux/macOS thay `py -3` bằng `python3`.

## 1. Chuẩn bị và lấy đúng nhánh

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

## 2. Build backend và chạy test Java — terminal 1

Đứng ở thư mục gốc:

```bash
mvn -f src/backend/pom.xml -B clean verify
```

Mong đợi `BUILD SUCCESS`, không có test thất bại hoặc lỗi. Khi chưa truyền database kiểm thử, các test PostgreSQL sẽ được bỏ qua; các test nghiệp vụ dùng H2 vẫn chạy. WAR được tạo tại `src/backend/target/he-thong-dat-ve-xe-khach-0.1.0-SNAPSHOT.war`.

## 3. Chạy PostgreSQL và backend — terminal 1

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
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/001_route_trip.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/002_vehicle_alignment.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/003_account_auth.sql
docker compose restart backend
```

Dừng lại nếu một lệnh schema thất bại. Không xóa volume để cập nhật database. Nếu báo trùng tên container `datvexe-postgres`, kiểm tra container đang có trước khi xử lý; không xóa dữ liệu theo suy đoán. Không cần mở pgAdmin để chạy hoặc test.

## 4. Chạy frontend — terminal 2

Bấm dấu **+** trong Terminal để mở terminal mới. Từ thư mục gốc:

```bash
cd src/frontend
npm ci
npm run dev
```

Giữ terminal này chạy. Mở Chrome/Edge tại **http://localhost:3000**. Frontend nằm trong `src/frontend`; Next.js chuyển tiếp `/api` đến backend `http://127.0.0.1:8080`.

Dùng thống nhất `localhost:3000`. Nếu Next tự chuyển sang cổng khác vì 3000 đang bận, xử lý ứng dụng đang chiếm cổng hoặc cập nhật `FRONTEND_ORIGIN` của backend cho đúng cổng. Nếu đổi backend, đặt `BACKEND_URL` trước khi chạy/build frontend.

## 5. Đăng nhập ADMIN

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

## 6. Kiểm tra bằng giao diện

Dùng dữ liệu mới để tránh sửa các tuyến demo đã có chuyến. Nếu mã dưới đây đã tồn tại từ lần thử trước, dùng mã mới hoặc tiếp tục với bản ghi cũ.

### 6.1. Tỉnh/thành phố

Chọn **Tỉnh/thành**:

- Tạo `KT_T08_A`, tên `Tỉnh kiểm thử A`, khu vực `Miền thử` → thành công.
- Tạo `KT_T08_B`, tên `Tỉnh kiểm thử B` → thành công.
- Tạo lại mã `KT_T08_A` → báo lỗi trùng mã.
- Bấm **Sửa** ở tỉnh A, đổi tên, bấm **Lưu thay đổi** → dữ liệu mới xuất hiện.
- Tìm theo mã hoặc tên → hiển thị tỉnh phù hợp.

### 6.2. Bến xe

Chọn **Bến xe**:

- Tạo mã `KT_T08_BEN_A`, tên `Bến kiểm thử A`, địa chỉ `Địa chỉ A`, chọn tỉnh A và trạng thái `HOAT_DONG`.
- Tạo mã `KT_T08_BEN_B`, tên `Bến kiểm thử B`, địa chỉ `Địa chỉ B`, chọn tỉnh B và trạng thái `HOAT_DONG`.
- Cả hai lần phải thành công.
- Bấm **Sửa** bến A, đổi địa chỉ, lưu → hiển thị địa chỉ mới.
- Chọn tỉnh A ở bộ lọc và bấm **Lọc bến xe** → chỉ hiện bến thuộc tỉnh A. Chọn lại `Tất cả` để xem đủ bến.
- Tìm bằng mã/tên bến → hiển thị dữ liệu tương ứng.

### 6.3. Tuyến xe

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

- Đổi trạng thái tuyến sang `DANG_KHAI_THAC` → thành công.
- Bấm **Sửa** đúng dòng tuyến, sửa mô tả/thời lượng, bấm **Lưu tuyến** → thành công khi chưa có chuyến.
- Thử tạo tuyến khác với cùng bến A ở cả hai đầu → bị từ chối.
- Thử thời lượng `0` hoặc âm → không cho lưu.
- Lọc theo bến đi, bến đến, trạng thái rồi bấm **Lọc tuyến** → kết quả đúng bộ lọc. Xóa bộ lọc khi muốn xem lại toàn bộ.

### 6.4. Điểm dừng

Bấm **Điểm dừng** ở dòng `KT_T08_TUYEN`, kiểm tra tiêu đề đang chỉ đúng mã tuyến:

1. Thêm mã `KT_T08_D1`, tên `Điểm dừng 1`, địa chỉ `Địa chỉ điểm 1`, thời gian `5` phút.
2. Thêm mã `KT_T08_D2`, tên `Điểm dừng 2`, địa chỉ `Địa chỉ điểm 2`, thời gian `10` phút.
3. Danh sách phải có thứ tự 1, 2.
4. Bấm **Sửa**, đổi tên/thời gian, bấm **Lưu điểm dừng** → hiển thị dữ liệu mới.
5. Bấm **↑ / ↓** → đổi thứ tự đúng; tải lại trang vẫn giữ thứ tự.
6. Nhập thời gian dừng `-1` → không cho lưu.
7. Xóa một điểm → điểm còn lại được đánh lại thứ tự từ 1.

### 6.5. Bảo vệ dữ liệu và quyền

- Khi tuyến vẫn dùng hai bến, thử xóa bến A → bị từ chối.
- Khi tỉnh A vẫn có bến, thử xóa tỉnh A → bị từ chối.
- Với tuyến đã có chuyến, sửa cấu hình/điểm dừng hoặc xóa tuyến → bị từ chối để giữ lịch sử. Đổi trạng thái khai thác vẫn được phép.
- Đăng nhập bằng khách hàng trong cửa sổ ẩn danh; mở `/quan-tri/tuyen-duong` → thông báo không có quyền.
- Mở trang đó khi chưa đăng nhập → chuyển đến đăng nhập.

Không tự nâng vai trò khách hàng thành ADMIN để kiểm tra trường hợp thiếu quyền. Các trường hợp tạo chuyến từ tuyến ngừng khai thác, tham chiếu tỉnh không tồn tại, lỗi giữa giao dịch và truy cập chéo nhà xe được kiểm tra bằng test tự động; giao diện có thể chặn nhập các dữ liệu đó ngay từ đầu.

### 6.6. Xóa dữ liệu thử chưa có chuyến

Nếu muốn dọn dữ liệu vừa tạo, xóa theo thứ tự: **tuyến → hai bến → hai tỉnh**. Chỉ thao tác trên các mã kiểm thử của bạn. Nếu tuyến đã có chuyến, không xóa cứng tuyến đó.

## 7. Chạy test API — terminal 3

Mở terminal mới bằng dấu **+**. Phải đứng ở thư mục gốc:

```bash
pwd
```

Nếu đang ở `src/frontend`, chạy `cd ../..`. Sau đó:

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

Nếu có `AssertionError`, test chưa đạt: giữ toàn bộ lỗi để kiểm tra. Lỗi `can't open file ...src/frontend/tests/...` nghĩa là chạy sai thư mục; trở về gốc rồi chạy lại.

## 8. Kiểm tra frontend build và lint

Tại terminal 2 trong `src/frontend`, nhấn **Ctrl+C** để dừng dev server:

```bash
npm run build
npm run lint
```

Mong đợi build thành công, lint **0 errors**. Hiện có 3 cảnh báo unused variable cũ trong `lib/api.ts` và `lib/mock-data.ts`. Chạy lại giao diện bằng `npm run dev`.

## 9. Test PostgreSQL riêng — tùy chọn

Các test PostgreSQL phải dùng database kiểm thử riêng, không dùng database ứng dụng `datvexe`. Tạo một database mới chưa tồn tại:

```bash
docker exec datvexe-postgres createdb -U datvexe_user t08_test
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < docs/database/schema/001_route_trip.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < docs/database/schema/002_vehicle_alignment.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < docs/database/schema/003_account_auth.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < docs/database/seed/001_demo.sql
docker exec -i datvexe-postgres psql -U datvexe_user -d t08_test -v ON_ERROR_STOP=1 < docs/database/seed/002_fleet_demo.sql
mvn -f src/backend/pom.xml -B -Duser.timezone=Asia/Ho_Chi_Minh -Dvehicle.pg.url=jdbc:postgresql://localhost:5432/t08_test -Dvehicle.pg.user=datvexe_user -Dvehicle.pg.password=datvexe_password verify
```

Nếu database `t08_test` đã có, không chạy lại `createdb`; kiểm tra schema/seed hiện có trước. Dừng khi nạp schema/seed báo lỗi. Mong đợi **54 test: 50 thực thi đạt, 4 test dành riêng cho seed T06 bị bỏ qua**, `BUILD SUCCESS`. Không cần thay đổi hướng dẫn hoặc bật bộ seed T06 cho phần kiểm tra này.

Lệnh `verify` tạo lại WAR; nếu backend đang chạy, restart backend và chờ API sẵn sàng trước khi test HTTP tiếp.

## 10. Tài liệu đối chiếu

- [Bảng yêu cầu và kết quả T08](../tasks/T08-review.md).
- [Hợp đồng API T08](../api/t08-route-management.md).

Các test đã đạt không thay thế bước review PR. Nhánh hiện chưa có ma trận T01 chính thức; quyền đang áp dụng ADMIN cho tỉnh/bến và ADMIN/NHA_XE theo phạm vi cho tuyến, cần đối chiếu khi nhóm cung cấp ma trận đã duyệt.
