# Chạy và kiểm tra T10 trong VS Code

Hướng dẫn dùng Windows/Git Bash. Các lệnh ở thư mục gốc `HE_THONG_DAT_VE_XE_KHACH11`, trừ phần frontend. Linux/macOS thay `py -3` bằng `python3`.

## 1. Lấy nhánh và kiểm tra công cụ

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

## 2. Backend — terminal 1 tại thư mục gốc

```bash
mvn -f src/backend/pom.xml -B clean verify
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

## 3. Frontend — terminal 2

Từ thư mục gốc:

```bash
cd src/frontend
npm ci
npm run dev
```

Giữ terminal chạy; mở Chrome/Edge tại **http://localhost:3000**. Nếu cổng 3000 đang bận, xử lý ứng dụng đang dùng cổng hoặc cập nhật `FRONTEND_ORIGIN` backend cho đúng cổng. Dùng `localhost` thống nhất. Next proxy `/api` đến `BACKEND_URL`, mặc định `http://127.0.0.1:8080`; khi đổi backend, đặt biến trước khi chạy/build.

## 4. ADMIN và nhà xe kiểm thử

Đăng nhập ADMIN đã có từ T07. Tại hồ sơ, chọn **Quản lý xe, đăng kiểm và sơ đồ ghế**, hoặc mở **http://localhost:3000/quan-tri/xe**.

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

## 5. Loại xe

ADMIN chọn tab **Loại xe**:

| Trường | Giá trị thử |
| --- | --- |
| Mã loại xe | `KT_T10_L20` |
| Tên loại xe | `Xe kiểm thử 20 chỗ` |
| Sức chứa | `20` |
| Số tầng | `2` |

Bấm **Thêm loại xe** → thành công. Thử tạo lại mã đó → lỗi trùng. Sức chứa 0/âm hoặc số tầng lớn hơn sức chứa không được lưu. Tìm theo mã/tên; **Sửa** tên/mô tả → thành công. Loại xe đã có xe thì không đổi cấu trúc sức chứa/tầng, không xóa cứng.

NHA_XE được đọc/chọn loại xe, không được thêm/sửa danh mục chung. Entity loại xe không có trạng thái nên không thêm trạng thái sử dụng.

## 6. Tạo, lọc và cập nhật xe

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

## 7. Xem và lưu sơ đồ ghế

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

## 8. Kiểm tra quyền và bảo vệ lịch sử

- Khách chưa đăng nhập mở `/quan-tri/xe` → chuyển đăng nhập; gọi `/api/xe` → 401.
- Khách hàng đã đăng nhập mở trang → thông báo không có quyền; API quản lý → 403.
- NHA_XE chỉ nhận xe của mình, chỉ chọn nhà xe mình và không có tab sửa loại xe.
- Dùng ADMIN để xem tất cả xe; T09 cung cấp liên kết tài khoản quản lý nhà xe.
- Xe đã có chuyến không xóa cứng; ghế có vé/giữ chỗ không đổi vị trí/tầng hoặc xóa.
- Đăng kiểm/trạng thái mới làm mất điều kiện của chuyến đang có sẽ bị từ chối; không tự hủy chuyến hay vé.

Các tình huống giả mạo URL/body, nhà xe khác, đăng kiểm qua đêm, vé theo từng chuyến và giữ/bán ghế bảo trì được kiểm tra bằng bộ test tự động sau đây. T10 không có giao diện bán vé; module đặt vé sẽ tích hợp helper khi triển khai.

## 9. Chạy test API — terminal 3 tại thư mục gốc

Nếu đang ở `src/frontend`, dùng `cd ../..`. Backend/PostgreSQL phải đang chạy:

```bash
py -3 tests/backend/vehicle_management_smoke.py
py -3 tests/backend/vehicle_api_smoke.py
py -3 tests/backend/api_smoke.py
```

Mong đợi các dòng **PASS** ở cuối mỗi script. Script tự tạo tài khoản ADMIN tạm, dữ liệu riêng và dọn trong finally; chỉ chạy trên database phát triển. Bộ T10 tạo cả dữ liệu vé để xác minh lịch sử và khả dụng theo chuyến, không thực hiện thanh toán thật.

Nếu có `AssertionError`, giữ đầy đủ log để kiểm tra. Lỗi `can't open file ...src/frontend/tests/...` là chạy sai thư mục. Nếu lặp bộ test liên tục bị 429, chờ hết cửa sổ một phút theo giới hạn đăng nhập T07.

## 10. PostgreSQL test riêng — tùy chọn

Repo có stack test dùng database tạm riêng tại cổng 55432; không dùng database ứng dụng `datvexe` cho Maven PostgreSQL tests:

```bash
docker compose -p t10-test -f tests/backend/docker-compose.postgres.yml up -d
docker compose -p t10-test -f tests/backend/docker-compose.postgres.yml ps
mvn -f src/backend/pom.xml -B -Duser.timezone=Asia/Ho_Chi_Minh -Dvehicle.pg.url=jdbc:postgresql://localhost:55432/datvexe_test -Dvehicle.pg.user=datvexe_test -Dvehicle.pg.password=datvexe_test -Dt07.t06.enabled=true verify
```

Chờ PostgreSQL test healthy trước Maven. Mong đợi BUILD SUCCESS, không failures/errors/skipped khi toàn bộ seed test có đủ. Dừng stack test sau dùng:

```bash
docker compose -p t10-test -f tests/backend/docker-compose.postgres.yml down
```

Stack test dùng tmpfs; dừng container mất dữ liệu test. Không ảnh hưởng volume database ứng dụng. Sau Maven tạo WAR mới, restart backend trước HTTP smoke.

## 11. Kiểm tra frontend và tài liệu

Tại terminal frontend, **Ctrl+C**, rồi:

```bash
npm run build
npm run lint
npm run dev
```

Build phải thành công; lint không có lỗi. Các cảnh báo unused variable có sẵn ở `lib/api.ts` và `lib/mock-data.ts` được ghi riêng trong kết quả kiểm thử.

- [Hợp đồng API/helper T10](../api/t10-vehicle-management.md).
- [Ma trận kiểm thử và kết quả T10](../tasks/T10-test-matrix.md).
- Browser smoke cho môi trường có Playwright/Chromium: `tests/frontend/vehicle_management_smoke.cjs`; có thể đặt `FRONTEND_URL`, `DB_NAME`, `DB_CONTAINER`, `CHROMIUM_PATH`. Trên Windows phải chọn đường dẫn trình duyệt và runner Playwright phù hợp, không dùng mặc định Linux `/usr/bin/chromium`.

Việc test đạt chưa thay thế review/chấp nhận PR của Minh Hiếu. API giữ/bán ghế tương lai phải tích hợp `VehicleRules.seatSellable` trong cùng transaction, rồi bổ sung test end-to-end module đặt vé.
