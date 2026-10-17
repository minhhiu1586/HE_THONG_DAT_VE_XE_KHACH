# LeNguyenThanhHien — Hướng dẫn demo trên VS Code

Các mục dưới đây được gộp từ tài liệu hiện có; kết quả kiểm thử lịch sử giữ nguyên thời điểm và giới hạn của lần chạy.

## Mục lục

- [Chạy và kiểm tra T10 trong VS Code](#section-1)
- [T12 — Chạy và kiểm tra trên VS Code](#section-2)
- [T13 — Hướng dẫn chạy và kiểm tra chi tiết trong VS Code](#section-3)

<a id="section-1"></a>

## Chạy và kiểm tra T10 trong VS Code

Hướng dẫn dùng Windows/Git Bash. Các lệnh ở thư mục gốc `HE_THONG_DAT_VE_XE_KHACH11`, trừ phần frontend. Linux/macOS thay `py -3` bằng `python3`.

### 1. Lấy nhánh và kiểm tra công cụ

```bash
git fetch origin
git switch feat/t10-vehicle-management
git pull --ff-only origin feat/t10-vehicle-management
java -version
javac -version
mvn -version
node -v
docker compose version
py -3 --version
```

Nếu chưa có nhánh local, dùng `git switch --track origin/feat/t10-vehicle-management`. Giữ lại thay đổi local trước khi đổi nhánh. Cần Java 21/JDK có javac, Maven dùng JDK 21, Node từ 20.9, Python 3 và Docker Desktop đang chạy Linux containers.

### 2. Backend — terminal 1 tại thư mục gốc

```bash
mvn -f apps/backend/pom.xml -B clean verify
docker compose up -d postgres
docker compose ps
```

Mong đợi `BUILD SUCCESS`; PostgreSQL `healthy`. Maven mặc định bỏ qua những test cần PostgreSQL. T10 không thay entity/schema, không có migration mới. Database cũ cần có đủ schema 001/002/003 của T07/T09. Không xóa volume để nâng cấp.

```bash
docker compose up -d backend
docker compose restart backend
curl -i http://localhost:8080/api/health
```

Chờ HTTP 200. Sau mỗi build WAR mới, restart backend. Xem lỗi bằng `docker compose logs --tail=100 backend`. Không cần mở pgAdmin.

### 3. Frontend — terminal 2

Từ thư mục gốc:

```bash
cd apps/frontend
npm ci
npm run dev
```

Giữ terminal chạy; mở Chrome/Edge tại **http://localhost:3000**. Nếu cổng 3000 đang bận, xử lý ứng dụng đang dùng cổng hoặc cập nhật `FRONTEND_ORIGIN` backend cho đúng cổng. Dùng `localhost` thống nhất. Next proxy `/api` đến `BACKEND_URL`, mặc định `http://127.0.0.1:8080`; khi đổi backend, đặt biến trước khi chạy/build.

### 4. ADMIN và nhà xe kiểm thử

Đăng nhập ADMIN đã có từ T07. Bấm **Quản trị** trên thanh đầu trang, rồi chọn **Xe và sơ đồ ghế** trong menu bên trái; hoặc mở **http://localhost:3000/quan-tri/xe**. Tài khoản NHA_XE dùng nút **Quản lý nhà xe**. Trên điện thoại, mở menu ☰ ở đầu trang để vào khu vực quản lý; menu bên của khu vực này có nút ☰ riêng để mở các chức năng.

Nếu chưa có ADMIN, đăng ký `admin.t10@example.test` tại `/dang-ky`, tự đặt mật khẩu và ghi nhớ. Tại terminal gốc:

```bash
docker exec -it datvexe-postgres psql -U datvexe_user -d datvexe
```

Trong psql:

```sql
BEGIN;
UPDATE nguoi_dung SET loai_tai_khoan='ADMIN'
WHERE email='admin.t10@example.test'
RETURNING email,loai_tai_khoan;
DELETE FROM phien_dang_nhap WHERE ma_nguoi_dung IN (
  SELECT ma_nguoi_dung FROM nguoi_dung WHERE email='admin.t10@example.test'
);
COMMIT;
```

Phải thấy đúng email với ADMIN, không phải `UPDATE 0`. Thoát bằng `\q`, đăng xuất/đăng nhập lại. Không nhập placeholder UUID vào SQL. Chỉ thao tác này trên database phát triển cục bộ.

Chọn nhà xe **HOAT_DONG** có sẵn trong danh sách khi tạo xe. Nếu chưa có nhà xe, ADMIN dùng T09 tại `/quan-tri/nha-xe` tạo và duyệt nhà xe trước. Để kiểm thử vai trò NHA_XE, dùng T09 gán tài khoản quản lý cho đúng nhà xe, rồi đăng nhập tài khoản đó ở cửa sổ ẩn danh.

### 5. Loại xe

ADMIN chọn tab **Loại xe**:

| Trường | Giá trị thử |
| --- | --- |
| Mã loại xe | `KT_T10_L20` |
| Tên loại xe | `Xe kiểm thử 20 chỗ` |
| Sức chứa | `20` |
| Số tầng | `2` |

Bấm **Thêm loại xe** → thành công. Thử tạo lại mã đó → lỗi trùng. Sức chứa 0/âm hoặc số tầng lớn hơn sức chứa không được lưu. Tìm theo mã/tên; **Sửa** tên/mô tả → thành công. Loại xe đã có xe thì không đổi cấu trúc sức chứa/tầng, không xóa cứng.

NHA_XE được đọc/chọn loại xe, không được thêm/sửa danh mục chung. Entity loại xe không có trạng thái nên không thêm trạng thái sử dụng.

### 6. Tạo, lọc và cập nhật xe

Chọn **Xe và ghế**:

| Trường | Giá trị thử |
| --- | --- |
| Mã xe | `KT_T10_X1` |
| Biển số | `51B-123.45` (chọn biển khác nếu đã có) |
| Loại xe | Loại 20 chỗ vừa tạo |
| Nhà xe | Nhà xe đang hoạt động của bạn |
| Ngày đăng kiểm | `2026-01-01` |
| Hạn đăng kiểm | `2028-12-31` |
| Dung tích nhiên liệu | `100` lít |
| Tốc độ | `60` km/giờ |
| Trạng thái | Sẵn sàng |

Bấm **Thêm xe** → xe và 20 ghế được tạo cùng lúc. Số ghế lấy từ loại xe, không tự nhập sức chứa khác. Giữ ngày đăng kiểm/hạn phù hợp ngày hiện tại và ngày chuyến khi thử trong tương lai.

- Thử biển số đã có → từ chối, không tạo xe/ghế dở dang.
- Dung tích âm, hạn trước ngày đăng kiểm hoặc thiếu trường bắt buộc → lỗi tiếng Việt.
- Lọc theo biển số/mã xe, loại, nhà xe, trạng thái → danh sách phù hợp.
- Bấm **Sửa** xe chưa có chuyến; đổi đăng kiểm, thông tin kỹ thuật hoặc trạng thái Bảo trì → thành công.
- Loại xe/nhà xe của xe đã tạo được cố định để giữ lịch sử ghế.
- Xe hết đăng kiểm vẫn lưu để theo dõi; danh sách/chi tiết cảnh báo **Chưa đủ điều kiện**. Backend không cho gán xe đó vào chuyến mở bán mới.

### 7. Xem và lưu sơ đồ ghế

Bấm **Chi tiết / ghế** đúng dòng `KT_T10_X1`:

1. Kiểm tra tiêu đề đúng biển số, thông tin đăng kiểm và sơ đồ đủ **20/20 chỗ**.
2. Hai tầng được hiển thị riêng; màu xanh là hoạt động, màu bảo trì là không bán. Đây là trạng thái vật lý, không phải ghế đã bán theo chuyến.
3. Trong bảng, đổi **Mã/vị trí** một ghế thành `A1`, chọn tầng trong giới hạn loại xe.
4. Chọn trạng thái ghế **Bảo trì**, bấm **Lưu sơ đồ ghế** → thành công nếu chưa có ràng buộc.
5. Tải lại trang, chọn lại chi tiết xe → dữ liệu vẫn được lưu.
6. Đổi hai ghế cùng vị trí `A1` → backend từ chối; **Tải lại sơ đồ** để xem bản đã lưu không bị thay đổi.
7. Bấm **Bỏ vị trí** → chỉ thay đổi trên form; khi còn 19/20 thì nút lưu bị khóa. Thêm vị trí thay thế, điền mã hợp lệ để đủ 20 mới lưu được.
8. Thêm vượt 20 → không được lưu. Backend cũng kiểm tra đủ đúng sức chứa khi gọi trực tiếp API.

`maGhe` là ID liên kết do backend sinh, hiển thị cố định. Mã/vị trí A1 dùng `viTri`; hai xe có thể cùng A1. Không thêm hàng/cột vào entity đã chốt. Bố cục các ô trên màn hình là cách hiển thị danh sách theo tầng/vị trí.

Nếu chi tiết báo xe có lịch chuyến hoặc ghế có lịch sử vé/giữ chỗ, bảng sửa sơ đồ bị khóa. Không xóa dữ liệu lịch sử để vượt kiểm tra. Quy trình đổi xe/ghế sau bán vé không nằm trong T10.

### 8. Kiểm tra quyền và bảo vệ lịch sử

- Khách chưa đăng nhập mở `/quan-tri/xe` → chuyển đăng nhập; gọi `/api/xe` → 401.
- Khách hàng đã đăng nhập mở trang → thông báo không có quyền; API quản lý → 403.
- NHA_XE chỉ nhận xe của mình, chỉ chọn nhà xe mình và không có tab sửa loại xe.
- Dùng ADMIN để xem tất cả xe; T09 cung cấp liên kết tài khoản quản lý nhà xe.
- Xe đã có chuyến không xóa cứng; ghế có vé/giữ chỗ không đổi vị trí/tầng hoặc xóa.
- Đăng kiểm/trạng thái mới làm mất điều kiện của chuyến đang có sẽ bị từ chối; không tự hủy chuyến hay vé.

Các tình huống giả mạo URL/body, nhà xe khác, đăng kiểm qua đêm, vé theo từng chuyến và giữ/bán ghế bảo trì được kiểm tra bằng bộ test tự động sau đây. T10 không có giao diện bán vé; module đặt vé sẽ tích hợp helper khi triển khai.

### 9. Chạy test API — terminal 3 tại thư mục gốc

Nếu đang ở `apps/frontend`, dùng `cd ../..`. Backend/PostgreSQL phải đang chạy:

```bash
py -3 tests/backend/vehicle_management_smoke.py
py -3 tests/backend/vehicle_api_smoke.py
py -3 tests/backend/api_smoke.py
```

Mong đợi các dòng **PASS** ở cuối mỗi script. Script tự tạo tài khoản ADMIN tạm, dữ liệu riêng và dọn trong finally; chỉ chạy trên database phát triển. Bộ T10 tạo cả dữ liệu vé để xác minh lịch sử và khả dụng theo chuyến, không thực hiện thanh toán thật.

Nếu có `AssertionError`, giữ đầy đủ log để kiểm tra. Lỗi `can't open file ...apps/frontend/tests/...` là chạy sai thư mục. Nếu lặp bộ test liên tục bị 429, chờ hết cửa sổ một phút theo giới hạn đăng nhập T07.

### 10. PostgreSQL test riêng — tùy chọn

Repo có stack test dùng database tạm riêng tại cổng 55432; không dùng database ứng dụng `datvexe` cho Maven PostgreSQL tests:

```bash
docker compose -p t10-test -f tests/backend/docker-compose.postgres.yml up -d
docker compose -p t10-test -f tests/backend/docker-compose.postgres.yml ps
mvn -f apps/backend/pom.xml -B -Duser.timezone=Asia/Ho_Chi_Minh -Dvehicle.pg.url=jdbc:postgresql://localhost:55432/datvexe_test -Dvehicle.pg.user=datvexe_test -Dvehicle.pg.password=datvexe_test -Dt07.t06.enabled=true verify
```

Chờ PostgreSQL test healthy trước Maven. Mong đợi BUILD SUCCESS, không failures/errors/skipped khi toàn bộ seed test có đủ. Dừng stack test sau dùng:

```bash
docker compose -p t10-test -f tests/backend/docker-compose.postgres.yml down
```

Stack test dùng tmpfs; dừng container mất dữ liệu test. Không ảnh hưởng volume database ứng dụng. Sau Maven tạo WAR mới, restart backend trước HTTP smoke.

### 11. Kiểm tra frontend và tài liệu

Tại terminal frontend, **Ctrl+C**, rồi:

```bash
npm run build
npm run lint
npm run dev
```

Build phải thành công; lint không có lỗi. Các cảnh báo unused variable có sẵn ở `lib/api.ts` và `lib/mock-data.ts` được ghi riêng trong kết quả kiểm thử.

- [Hợp đồng API/helper T10](../../../docs/api/t10-quan-ly-xe-va-ghe.md).
- [Ma trận kiểm thử và kết quả T10](../testing/existing/t10-ma-tran-kiem-thu-xe-va-ghe.md).
- Browser smoke cho môi trường có Playwright/Chromium: `tests/frontend/vehicle_management_smoke.cjs`; có thể đặt `FRONTEND_URL`, `DB_NAME`, `DB_CONTAINER`, `CHROMIUM_PATH`. Trên Windows phải chọn đường dẫn trình duyệt và runner Playwright phù hợp, không dùng mặc định Linux `/usr/bin/chromium`.

Việc test đạt chưa thay thế review/chấp nhận PR của Minh Hiếu. API giữ/bán ghế tương lai phải tích hợp `VehicleRules.seatSellable` trong cùng transaction, rồi bổ sung test end-to-end module đặt vé.

<a id="section-2"></a>

## T12 — Chạy và kiểm tra trên VS Code

### Chuẩn bị

Cần JDK 21 (có javac), Maven, Node từ 20.9, Python 3 và Docker Desktop chạy Linux containers. Mở thư mục gốc dự án trong VS Code, dùng Git Bash. PostgreSQL chạy bằng Docker; không cần mở pgAdmin. Giữ dữ liệu hiện tại, không chạy `docker compose down -v`.

```bash
git switch feat/t12-trip-creation-sale-validation
mvn -f apps/backend/pom.xml clean verify
docker compose up -d postgres backend
```

Đợi backend sẵn sàng tại `/api/health`. Nếu backend đang chạy trong lúc build WAR, restart riêng backend:

```bash
docker compose restart backend
```

Trong terminal khác, từ thư mục gốc:

```bash
cd apps/frontend
npm ci
npm run build
npm run lint
npm run dev
```

Không chạy npm ở thư mục gốc vì package.json nằm trong `apps/frontend`. Để thoát frontend, Ctrl+C.

### Kiểm tra giao diện

1. Đăng nhập ADMIN, chọn **Quản lý → Chuyến xe và mở bán**. NHA_XE chỉ thấy dữ liệu nhà xe mình; khách không có quyền.
2. Tạo mã chuyến mới, chọn tuyến và ngày tương lai. Chưa chọn xe/phân công vẫn lưu nháp được; chuyến không tự xuất hiện trong API tìm vé.
3. Chọn tuyến 7 giờ, nhập 22:00: giờ đến phải là 05:00 ngày sau. Giờ đến sớm hơn khởi hành bị từ chối.
4. Mở chi tiết, kiểm tra tám nhóm điều kiện. Thiếu xe/nhân viên không được mở bán.
5. Phân công qua T11 API, gồm nhân sự bắt buộc và xác nhận kế hoạch phù hợp. T11 hiện chưa có giao diện phân công. Không chỉ sửa trạng thái bằng SQL để giả lập mở bán.
6. Kiểm tra lại điều kiện, xác nhận mở bán. Chuyến đủ điều kiện mới chuyển trạng thái; sau mở bán không sửa xe/lịch bằng form nháp.
7. Lọc trạng thái, tuyến, xe, khoảng ngày; kiểm tra trên cửa sổ hẹp và tài khoản nhà xe khác.
8. Thông báo dùng tiếng Việt; chọn tab/thao tác khác phải xóa thông báo cũ. Nhập rỗng trường bắt buộc phải có hướng dẫn tiếng Việt.

Mặc định hạn nhận vé là 60 phút trước khởi hành, đổi qua `TRIP_BOOKING_CUTOFF_MINUTES` rồi tạo lại container backend. Tại đúng hạn và sau hạn đều bị chặn, kể cả gọi helper backend trực tiếp.

### Kiểm thử tự động

Từ thư mục gốc, backend chạy cổng8080 và Docker PostgreSQL hiện có:

```bash
py -3 tests/backend/trip_sale_smoke.py
```

Script tạo fixture có tiền tố riêng và dọn fixture khi hoàn tất. Khuyến nghị database kiểm thử riêng; có thể đặt `DB_NAME` và `API_BASE_URL` trỏ đến backend dùng đúng database đó. Không trộn backend database thật với DB_NAME kiểm thử.

Kiểm thử Java/PostgreSQL:

```bash
mvn -f apps/backend/pom.xml -Dvehicle.pg.url=jdbc:postgresql://localhost:5432/datvexe -Dvehicle.pg.user=datvexe_user -Dvehicle.pg.password=datvexe_password -Dt07.t06.enabled=true verify
```

Các test PostgreSQL tạo và dọn fixture riêng; kiểm tra kết quả Skipped để biết đã thực sự chạy. Không có cấu hình PG thì các test này có thể bị bỏ qua.

Kiểm thử browser cần Playwright và Chromium. File `tests/frontend/trip_sale_smoke.cjs`, cấu hình `FRONTEND_URL`, `DB_NAME`, `CHROMIUM_PATH`. Test sử dụng API T11 thật để phân công rồi mở bán từ giao diện.

Đối chiếu [ma trận kiểm thử](../testing/existing/t12-ma-tran-kiem-thu-chuyen-xe.md) và [API T12](../../../docs/api/t12-quan-ly-chuyen-xe.md). Việc PR được review/chấp nhận là bước riêng, không được suy ra từ test đạt.

<a id="section-3"></a>

## T13 — Hướng dẫn chạy và kiểm tra chi tiết trong VS Code

Hướng dẫn dành cho Windows, Terminal Git Bash trong VS Code. Các lệnh chạy từ thư mục gốc `HE_THONG_DAT_VE_XE_KHACH11`, trừ phần có yêu cầu chuyển vào `apps/frontend`. Kết quả cần kiểm tra gồm tìm chuyến, xem chi tiết, chỗ còn và chọn ghế trên giao diện.

### 1. Mở đúng dự án và terminal

1. Mở VS Code → **File → Open Folder** → chọn thư mục `HE_THONG_DAT_VE_XE_KHACH11`.
2. Chọn **Terminal → New Terminal**.
3. Nhấn mũi tên cạnh dấu **+** trong terminal → chọn **Git Bash**.
4. Chạy:

```bash
pwd
git status
```

**Kết quả mong đợi:** đường dẫn kết thúc bằng thư mục dự án; Git hiển thị trạng thái repository, không báo “not a git repository”.

Nếu terminal đang ở `apps/frontend`, quay về gốc:

```bash
cd ../..
```

Không chạy `cd apps/frontend` lần nữa khi đã đứng trong thư mục frontend.

### 2. Kiểm tra công cụ

Mở Docker Desktop và đợi Docker sẵn sàng, sau đó chạy:

```bash
java -version
javac -version
mvn -version
node -v
npm -v
docker compose version
```

**Kết quả mong đợi:**

| Lệnh | Kết quả cần có |
| --- | --- |
| `java -version`, `javac -version` | Java/JDK 21 |
| `mvn -version` | Maven hoạt động và dùng Java 21 |
| `node -v` | Node từ 20.9 trở lên |
| `npm -v` | Có số phiên bản |
| `docker compose version` | Có phiên bản Compose |

Nếu có “command not found”, cài công cụ tương ứng và mở lại terminal. Chỉ có `java` nhưng thiếu `javac` nghĩa là chưa có đủ JDK để build backend.

### 3. Chuyển sang nhánh T13

```bash
git fetch origin
git switch feat/t13-trip-search-availability
git pull --ff-only origin feat/t13-trip-search-availability
git branch --show-current
```

**Kết quả mong đợi:** dòng cuối là `feat/t13-trip-search-availability`; pull thành công hoặc báo `Already up to date`.

Nếu máy chưa có nhánh local, dùng:

```bash
git switch --track origin/feat/t13-trip-search-availability
```

Nếu Git báo có thay đổi local cản chuyển nhánh/pull, giữ lại thay đổi đó trước khi tiếp tục. Không dùng reset để xóa công việc đang làm.

### 4. Chạy kiểm thử backend và tạo WAR

Tại thư mục gốc, chạy nhóm kiểm thử liên quan T13:

```bash
mvn -f apps/backend/pom.xml -Dtest=RouteTripServiceTest,TripServiceTest,VehicleTask10Test,AccessControlTest,OperatorServiceTest,HealthPolicyTest test
```

**Kết quả mong đợi:** `BUILD SUCCESS`, 66 test, `Failures: 0`, `Errors: 0`. Nhóm này kiểm tra nghiệp vụ bằng H2; không cần PostgreSQL đang chạy.

Tiếp theo tạo WAR:

```bash
mvn -f apps/backend/pom.xml -DskipTests package
```

**Kết quả mong đợi:** `BUILD SUCCESS` và file:

```text
apps/backend/target/he-thong-dat-ve-xe-khach-0.1.0-SNAPSHOT.war
```

Ở lệnh thứ hai, test được bỏ qua vì vừa chạy nhóm test ở bước trước; nó không thay thế kiểm thử.

### 5. Khởi động PostgreSQL và backend

```bash
docker compose up -d postgres backend
docker compose ps
```

**Kết quả mong đợi:** PostgreSQL trạng thái `healthy`, backend đang chạy; cổng database thường là 5432 và backend là 8080.

Nếu backend đã chạy trước khi build WAR mới:

```bash
docker compose restart backend
```

Đợi Tomcat triển khai xong rồi kiểm tra:

```bash
curl -i http://localhost:8080/api/health
```

**Kết quả mong đợi:** HTTP 200 và JSON có `"status":"UP"`, `"database":"UP"`; thứ tự hai trường có thể khác.

Nếu chưa đạt, xem log:

```bash
docker compose logs --tail=100 backend
docker compose logs --tail=50 postgres
```

Giữ volume database hiện tại; không chạy `docker compose down -v`. SQL seed chỉ tự chạy khi PostgreSQL khởi tạo volume mới. Pull code không tự chạy lại seed trên volume cũ.

### 6. Kiểm tra dữ liệu thật trong database

Không cần cài `psql` trên Windows. Chạy qua container:

```bash
docker compose exec postgres psql -U datvexe_user -d datvexe -c "SELECT 1;"
```

**Kết quả mong đợi:** một hàng có giá trị `1`. Nếu bạn đã đổi tên database/user trong cấu hình, thay hai giá trị trên cho đúng.

Xem danh mục tỉnh/thành:

```bash
docker compose exec postgres psql -U datvexe_user -d datvexe -c "SELECT ma_tinh_thanh, ten_tinh_thanh FROM tinh_thanh ORDER BY ma_tinh_thanh;"
```

Xem các chuyến và tỉnh tương ứng:

```bash
docker compose exec postgres psql -U datvexe_user -d datvexe -c "SELECT c.ma_chuyen, bd.ma_tinh_thanh AS tinh_di, be.ma_tinh_thanh AS tinh_den, c.ngay_khoi_hanh, c.gio_khoi_hanh, c.gio_den_du_kien, c.trang_thai FROM chuyen_xe c JOIN tuyen_xe t ON t.ma_tuyen=c.ma_tuyen JOIN ben_xe bd ON bd.ma_ben_xe=t.tram_khoi_hanh JOIN ben_xe be ON be.ma_ben_xe=t.tram_den ORDER BY c.ngay_khoi_hanh,c.gio_khoi_hanh;"
```

**Kết quả mong đợi:** mã tỉnh đi/đến được lấy từ hai bến của tuyến; có ngày giờ riêng của mỗi chuyến. Ghi lại mã chuyến, mã tỉnh và ngày để dùng ở các bước sau.

Các bản ghi `NHAP` hoặc `CHUA_KHOI_HANH` không tự xuất hiện như chuyến được đặt. T13 đọc PostgreSQL qua backend; dữ liệu SQL seed đã nạp và dữ liệu nhập bằng giao diện đều là bản ghi trong database.

### 7. Chuẩn bị ít nhất một chuyến mở bán hợp lệ

Nếu database đã có chuyến `DANG_MO_BAN` ở ngày tương lai và đủ điều kiện, dùng chuyến đó. Seed T11 cũng cung cấp sẵn một bộ chuyến đi-về để thử mở bán; `CX_6_1` được cấu hình làm chuyến TP. Hồ Chí Minh → Đà Lạt, xe `XE_006` và nhân viên cùng nhà xe. Chuyến này có một tài xế chính, một tài xế phụ, một phụ xe và tuyến về `CX_6_2` nối đúng bến. Trong database được seed mới, đây là dữ liệu kiểm thử có thể đủ điều kiện mở bán sau khi backend kiểm tra.

#### Dữ liệu chạy dự án hiện tại

Database chính là `datvexe`. Dùng ba seed khởi tạo và API T12 mở bán:

```powershell
docker compose up -d postgres
python scripts/load_project_data.py --activate
python tests/backend/project_data_check.py --check-reload
```

Xem [dữ liệu và tài khoản](../../../database/README.md#section-2). Mã chuyến hiện tại có dạng `CX_FUTA_1_YYYYMMDD_1`, ngày trong 14 ngày tới. Không dùng các mã CX_6_1/DEMO trong phần hồi quy dưới đây để kiểm tra database dự án mới.

#### Seed hồi quy cũ — chỉ dùng database kiểm thử

Docker chỉ tự chạy các file seed khi khởi tạo volume PostgreSQL lần đầu. Vì vậy pull code hoặc khởi động lại container không tự tạo `CX_6_1`. Các seed có phụ thuộc; không chạy riêng `006_t11_staff.sql` trước seed vận tải T06.

Tại thư mục gốc repository, nếu các seed nền chưa có, nạp theo thứ tự:

```bash
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U datvexe_user -d datvexe < tests/backend/fixtures/sql/001_base.sql
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U datvexe_user -d datvexe < tests/backend/fixtures/sql/002_fleet.sql
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U datvexe_user -d datvexe < tests/backend/fixtures/sql/003_transport.sql
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U datvexe_user -d datvexe < tests/backend/fixtures/sql/005_alignment.sql
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U datvexe_user -d datvexe < tests/backend/fixtures/sql/006_staff.sql
```

Nếu đã xác nhận `001_demo.sql`, `002_fleet_demo.sql` và `005_t06_display_alignment.sql` có đủ dữ liệu, có thể chỉ cần chạy `003_t06_transport.sql` rồi `006_t11_staff.sql`. Ví dụ đang ở terminal `apps/frontend`, đường dẫn file lần lượt bắt đầu bằng `../../docs/...`.

Các script seed dùng mã cố định và phần lớn bỏ qua bản ghi đã tồn tại; không xóa volume bằng `docker compose down -v`. Nếu một script báo lỗi, dừng tại đó, đọc lỗi và bổ sung đúng seed phụ thuộc trước khi chạy bước tiếp theo. `006_t11_staff.sql` nằm trong transaction; khi gặp lỗi giữa chừng, các thay đổi của lần chạy lỗi được rollback.

Kiểm tra chuyến và ngày giờ thực tế trong database:

```bash
docker compose exec postgres psql -U datvexe_user -d datvexe -c "SELECT ma_chuyen, ma_tuyen, ma_xe, ngay_khoi_hanh, gio_khoi_hanh, gio_den_du_kien, trang_thai FROM chuyen_xe WHERE ma_chuyen='CX_6_1';"
```

**Kết quả mong đợi:** một dòng `CX_6_1`; ngày khởi hành là ngày nạp seed cộng một ngày trong lần nạp thành công đầu tiên, giờ đi `08:00`. Chạy lại seed không dời ngày của chuyến đã tồn tại. Nếu ngày này đã qua, chuyến không thể dùng để mở bán; hãy dùng chuyến tương lai khác hoặc tạo bộ dữ liệu kiểm thử có ngày tương lai.

#### Mở bán `CX_6_1`

1. Đảm bảo backend và frontend đang chạy theo các bước 5 và 8; đăng nhập tài khoản ADMIN hoặc NHA_XE có quyền quản lý.
2. Mở **Quản lý → Chuyến xe và mở bán**. Bộ lọc trạng thái ban đầu có thể đang chọn **Đang mở bán**; đổi thành **Tất cả trạng thái** hoặc **Chưa mở bán (dữ liệu cũ)** rồi bấm **Lọc chuyến**.
3. Tìm `CX_6_1`, chọn **Chi tiết / mở bán** và xem từng ô trong phần **Điều kiện mở bán**.
4. Nếu tất cả điều kiện xanh, bấm **Mở bán chuyến**. Nếu có điều kiện đỏ, xử lý đúng nguyên nhân hiển thị. Không ép trạng thái bằng SQL.
5. Nạp lại chi tiết hoặc danh sách và xác nhận trạng thái là **Đang mở bán**. Nếu chuyến bị từ chối, ghi lại lý do; dữ liệu hiện có trong database như lịch xe/nhân viên vẫn được Backend kiểm tra nên không thể khẳng định chuyến luôn hợp lệ ở mọi máy.

**Kết quả mong đợi:** trong bộ seed T11 nguyên vẹn, lịch xe và nhân viên của `CX_6_1` được bố trí để nối đúng với chuyến về `CX_6_2`, và đã có đủ vai trò. Khi các dữ liệu liên quan còn hợp lệ và chuyến chưa quá hạn, quy tắc T12 cho phép mở bán. Chuyến seed ban đầu có trạng thái `CHUA_KHOI_HANH`, nên T13 chưa tìm thấy cho đến khi mở bán thành công.

Nếu không có tài khoản quản trị, không có giao diện phân công nhân viên đầy đủ trong T11; xem [hướng dẫn T12](huong-dan-demo.md#section-2) và [API nhân viên T11](../../../docs/api/t11-quan-ly-nhan-vien-va-phan-cong.md) để chuẩn bị dữ liệu qua API. Không có tài khoản quản trị mặc định. Nếu cần tạo tài khoản kiểm thử quản trị trên database phát triển, làm theo [hướng dẫn T07](../../NguyenMinhHieu/docs/huong-dan-demo.md#section-1).

Mặc định ngừng nhận đặt 60 phút trước khởi hành. Nên chọn chuyến ngày mai hoặc xa hơn để tránh hết hạn trong lúc kiểm tra. Xe/nhân viên cần có đăng kiểm, bằng lái và lịch vận hành hợp lệ cho ngày đó.

### 8. Khởi động frontend

Mở terminal thứ hai tại gốc dự án:

```bash
cd apps/frontend
npm ci
npm run lint
npm run build
npm run dev
```

**Kết quả mong đợi:**

- `npm ci`: cài dependency thành công.
- `npm run lint`: kết thúc không có lỗi.
- `npm run build`: thành công, có đường dẫn `/tim-chuyen` và `/tim-chuyen/[maChuyen]`.
- `npm run dev`: thông báo địa chỉ frontend, mặc định `http://localhost:3000`.

Giữ terminal này chạy. Nếu Node tự chọn cổng 3001 vì 3000 đã bị chiếm, dừng phiên frontend cũ và chạy lại trên 3000 để phù hợp cấu hình origin hiện tại.

### 9. Kiểm tra danh mục và tìm kiếm trên giao diện

1. Mở `http://localhost:3000/tim-chuyen` bằng trình duyệt.
2. Không đăng nhập.
3. Chọn tỉnh đi, tỉnh đến khác nhau theo dữ liệu đã xem ở bước 6.
4. Chọn đúng **ngày khởi hành** của chuyến đã mở bán.
5. Nhấn **Tìm kiếm**.

**Kết quả mong đợi:** danh sách địa điểm lấy từ backend; trong lúc tải có thông báo tải; kết quả có nhà xe, bến đi/đến, giờ riêng của chuyến, loại xe, giá và số chỗ còn.

Đối chiếu:

| Thông tin | Nguồn cần khớp |
| --- | --- |
| Tỉnh đi/đến | Tỉnh của bến đầu/cuối tuyến |
| Giờ khởi hành | `ChuyenXe.gioKhoiHanh` |
| Ngày đến và giờ đến | `ChuyenXe.gioDenDuKien` |
| Giá | Giá tuyến đang gán cho chuyến |
| Chỗ còn | Ghế hoạt động, chưa bán và chưa có giữ hợp lệ của chuyến |

### 10. Kiểm tra từng trường hợp tìm kiếm

| Bước thực hiện | Kết quả mong đợi |
| --- | --- |
| Chọn cùng tỉnh đi và tỉnh đến rồi tìm | Thông báo dữ liệu không hợp lệ; không hiện như kết quả thành công |
| Bỏ trống địa điểm/ngày bắt buộc | Form yêu cầu nhập/chọn trước khi gửi |
| Chọn ngày không có chuyến mở bán | Thông báo không có chuyến phù hợp; không hiện lại chuyến của lần tìm trước |
| Đổi cặp tỉnh hoặc ngày rồi tìm lại | URL và kết quả cập nhật theo lựa chọn mới |
| Có nhiều chuyến cùng tuyến | Hiện đầy đủ các chuyến phù hợp, đúng giờ từng chuyến |
| Dùng bộ lọc loại xe/nhà xe | Chỉ còn kết quả phù hợp bộ lọc |
| Chọn sắp xếp giờ/giá/chỗ còn | Thứ tự thay đổi đúng tiêu chí |
| Nhấn xóa bộ lọc | Checkbox và kết quả trở về trạng thái không lọc |

Để kiểm tra mã tỉnh không tồn tại, dùng API ở bước 14 vì giao diện chỉ cho chọn danh mục hợp lệ.

### 11. Kiểm tra trang chi tiết

1. Tại một thẻ chuyến, nhấn **Xem chi tiết**.
2. Kiểm tra URL dạng `/tim-chuyen/MÃ_CHUYẾN`.
3. Đối chiếu mã chuyến, tuyến, nhà xe, loại xe, biển số, giá và chỗ còn với chuyến vừa chọn.
4. Kiểm tra bến đầu/cuối và địa chỉ.
5. Kiểm tra điểm dừng trung gian.

**Kết quả mong đợi:** chuyển sang trang chi tiết ngay, không phải kéo xuống cuối danh sách tìm kiếm. Nếu không có điểm dừng, hiện thông báo không có điểm dừng trung gian; nếu có nhiều điểm, hiển thị theo thứ tự đã cấu hình.

Thử mở URL với mã không tồn tại:

```text
http://localhost:3000/tim-chuyen/MA_KHONG_TON_TAI_T13
```

**Kết quả mong đợi:** thông báo không thể xem chi tiết và lý do từ backend; có liên kết quay lại tìm chuyến. API tương ứng trả 404.

### 12. Kiểm tra chuyến qua đêm và trạng thái nhận đặt

Chọn chuyến đã chuẩn bị có giờ đi 22:00, đến 05:00 hôm sau.

**Kết quả mong đợi:** ngày khởi hành là ngày tìm kiếm; ngày đến là hôm sau. Không lấy giờ mặc định của tuyến thay giờ đi thực tế. Tìm theo ngày đến không trả chuyến này chỉ vì nó đến vào ngày đó.

Kiểm tra trạng thái:

| Dữ liệu chuyến | Kết quả mong đợi |
| --- | --- |
| Nháp/chưa mở bán/hủy | Không xuất hiện như chuyến đặt được trong kết quả |
| Hết hạn nhận đặt | Không xuất hiện như chuyến đặt được; mở chi tiết hiện không còn nhận đặt |
| Đang mở bán nhưng hết ghế | Có thể vẫn xem chuyến với trạng thái **Hết chỗ**, không chọn ghế thêm |
| Đủ điều kiện và còn ghế | Hiện **Đang nhận đặt vé**, nút chọn ghế được bật |

### 13. Kiểm tra số ghế và chọn ghế

1. Mở chi tiết chuyến còn chỗ.
2. Nhấn **Cập nhật chỗ còn**.
3. Nhấn **Chọn ghế**.
4. Chọn một ghế còn trống, rồi nhấn lại ghế đó để bỏ chọn.
5. Thử ghế đã bán, đang giữ hoặc bảo trì nếu database có dữ liệu tương ứng.

**Kết quả mong đợi:** cập nhật lấy dữ liệu mới; ghế trống chọn được và có hiệu ứng đang chọn; ghế không khả dụng bị vô hiệu hóa. Chọn trong T13 chưa tạo giữ chỗ. Khi cập nhật lại, lựa chọn cũ được xóa.

Ca nghiệp vụ cần đối chiếu bằng dữ liệu kiểm thử:

| Dữ liệu | Kết quả mong đợi |
| --- | --- |
| 20 ghế, 5 ghế bán, 3 ghế giữ hợp lệ, 2 ghế bảo trì; các nhóm không trùng | Còn 10 ghế |
| A1 bán ở chuyến A, chưa bán/giữ ở chuyến B cùng xe | Không trừ A1 của chuyến B |
| Lượt giữ vừa hết hạn | Chỗ còn tăng sau lần truy vấn mới |
| Chỗ thay đổi sau khi tải kết quả | Mở chi tiết/cập nhật lấy trạng thái mới |
| Hết toàn bộ chỗ | Hiện hết chỗ, không cho chọn thêm |

Nếu module giữ ghế/bán vé chưa sẵn sàng, kiểm tra các ca động bằng test tự động. Không sửa Entity/schema hoặc tạo vé giả trên database đang dùng để thay nghiệm thu tích hợp.

### 14. Kiểm tra API không cần đăng nhập

Từ kết quả SQL ở bước 6, thay các giá trị trong dấu `<...>` bằng mã/ngày thật rồi bỏ dấu ngoặc:

```bash
curl -i "http://localhost:8080/api/chuyen-xe?from=CITY:<MA_TINH_DI>&to=CITY:<MA_TINH_DEN>&date=<YYYY-MM-DD>"
curl -i "http://localhost:8080/api/chuyen-xe/<MA_CHUYEN>"
```

**Kết quả mong đợi:** HTTP 200, không yêu cầu cookie đăng nhập. Tìm kiếm trả array, chi tiết trả object. Danh sách rỗng là `[]`, không phải lỗi 500.

Các lệnh sau chạy nguyên văn:

```bash
curl -i "http://localhost:8080/api/chuyen-xe/MA_KHONG_TON_TAI_T13"
curl -i "http://localhost:8080/api/chuyen-xe?from=CITY:MA_TINH_KHONG_TON_TAI&to=CITY:MA_TINH_KHONG_TON_TAI&date=2030-01-01"
curl -i "http://localhost:8080/api/chuyen-xe?from=CITY:ANY&to=CITY:OTHER&date=khong-phai-ngay"
```

**Kết quả mong đợi:** lần lượt 404, 400, 400; JSON có thông báo lỗi. Các mã không tồn tại chỉ là tham số để kiểm tra lỗi, không tạo dữ liệu vào database.

### 15. Kiểm tra lỗi tải dữ liệu

Trong terminal gốc:

```bash
docker compose stop backend
```

Quay lại trình duyệt, tải lại trang tìm chuyến hoặc gửi tìm kiếm mới.

**Kết quả mong đợi:** thông báo không tải/tìm được dữ liệu; không hiện chuyến giả, không dùng kết quả cũ thay lần tìm mới, không coi lỗi API là kết quả không có chuyến.

Khởi động lại:

```bash
docker compose start backend
curl -i http://localhost:8080/api/health
```

Khi health đạt HTTP 200, thử tìm kiếm lại; dữ liệu phải tải bình thường.

### 16. Những lỗi thường gặp

| Lỗi | Cách xử lý |
| --- | --- |
| `npm ENOENT package.json` | Chạy npm trong `apps/frontend` |
| `cd: apps/frontend: No such file` | Kiểm tra `pwd`; nếu đã ở frontend thì không cd thêm |
| `psql: command not found` | Dùng `docker compose exec postgres psql ...` |
| PostgreSQL trong container chạy nhưng Maven báo sai mật khẩu | Kiểm tra cấu hình JDBC của test trên máy; truy cập localhost có thể khác kết nối nội bộ container |
| Backend 404 hoặc chạy code cũ | Kiểm tra WAR mới, build lại rồi restart backend |
| Health HTTP 503 | Xem log backend/database và cấu hình kết nối |
| Danh mục địa điểm trống | Kiểm tra API tỉnh/bến và database đã có dữ liệu hay chưa |
| Có chuyến trong SQL nhưng tìm không thấy | Kiểm tra ngày khởi hành, tỉnh của bến, trạng thái mở bán, hạn đặt và toàn bộ điều kiện T12 |
| `release version 21 not supported` | Cài JDK 21 và kiểm tra Java mà Maven đang dùng |

### 17. Ghi kết quả nghiệm thu

Ghi từng trường hợp: ngày kiểm tra, mã chuyến, tỉnh đi/đến, ngày đi, kết quả thực tế, đạt/chưa đạt, ảnh hoặc log nếu lỗi. Đối chiếu [ma trận T13](../testing/existing/t13-ma-tran-kiem-thu-tim-chuyen.md) và [API T13](../../../docs/api/t13-tim-chuyen-va-cho-con.md).

Test tự động đã chạy trong môi trường phát triển: 66 test backend liên quan đạt; frontend lint/build đạt. Việc kiểm tra PostgreSQL, trình duyệt trên máy bạn và reviewer nghiệm thu là các bước cần ghi kết quả riêng.

Để dừng frontend, nhấn **Ctrl+C** ở terminal đang chạy `npm run dev`. Nếu cần dừng dịch vụ Docker:

```bash
docker compose stop backend postgres
```

**Kết quả mong đợi:** dịch vụ dừng, dữ liệu database trong volume vẫn được giữ.
