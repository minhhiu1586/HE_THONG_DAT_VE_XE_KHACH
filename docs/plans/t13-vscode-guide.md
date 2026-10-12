# T13 — Hướng dẫn chạy và kiểm tra chi tiết trong VS Code

Hướng dẫn dành cho Windows, Terminal Git Bash trong VS Code. Các lệnh chạy từ thư mục gốc `HE_THONG_DAT_VE_XE_KHACH11`, trừ phần có yêu cầu chuyển vào `src/frontend`. Kết quả cần kiểm tra gồm tìm chuyến, xem chi tiết, chỗ còn và chọn ghế trên giao diện.

## 1. Mở đúng dự án và terminal

1. Mở VS Code → **File → Open Folder** → chọn thư mục `HE_THONG_DAT_VE_XE_KHACH11`.
2. Chọn **Terminal → New Terminal**.
3. Nhấn mũi tên cạnh dấu **+** trong terminal → chọn **Git Bash**.
4. Chạy:

```bash
pwd
git status
```

**Kết quả mong đợi:** đường dẫn kết thúc bằng thư mục dự án; Git hiển thị trạng thái repository, không báo “not a git repository”.

Nếu terminal đang ở `src/frontend`, quay về gốc:

```bash
cd ../..
```

Không chạy `cd src/frontend` lần nữa khi đã đứng trong thư mục frontend.

## 2. Kiểm tra công cụ

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

## 3. Chuyển sang nhánh T13

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

## 4. Chạy kiểm thử backend và tạo WAR

Tại thư mục gốc, chạy nhóm kiểm thử liên quan T13:

```bash
mvn -f src/backend/pom.xml -Dtest=RouteTripServiceTest,TripServiceTest,VehicleTask10Test,AccessControlTest,OperatorServiceTest,HealthPolicyTest test
```

**Kết quả mong đợi:** `BUILD SUCCESS`, 66 test, `Failures: 0`, `Errors: 0`. Nhóm này kiểm tra nghiệp vụ bằng H2; không cần PostgreSQL đang chạy.

Tiếp theo tạo WAR:

```bash
mvn -f src/backend/pom.xml -DskipTests package
```

**Kết quả mong đợi:** `BUILD SUCCESS` và file:

```text
src/backend/target/he-thong-dat-ve-xe-khach-0.1.0-SNAPSHOT.war
```

Ở lệnh thứ hai, test được bỏ qua vì vừa chạy nhóm test ở bước trước; nó không thay thế kiểm thử.

## 5. Khởi động PostgreSQL và backend

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

## 6. Kiểm tra dữ liệu thật trong database

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

## 7. Chuẩn bị ít nhất một chuyến mở bán hợp lệ

Nếu database đã có chuyến `DANG_MO_BAN` ở ngày tương lai và đủ điều kiện, dùng chuyến đó.

Nếu chưa có:

1. Sau khi frontend chạy ở bước 8, đăng nhập tài khoản ADMIN hoặc NHA_XE có quyền.
2. Mở **Quản lý → Chuyến xe và mở bán**.
3. Tạo chuyến nháp trên tuyến đang khai thác, chọn ngày tương lai, xe đúng nhà xe.
4. Mở phần chi tiết/điều kiện mở bán.
5. Phân công nhân viên bằng API T11 theo [hướng dẫn T12](t12-vscode-guide.md) và [API nhân viên T11](../api/t11-employees.md). T11 hiện chưa có giao diện phân công đầy đủ.
6. Kiểm tra từng nhóm điều kiện; sửa dữ liệu bị báo lỗi.
7. Chỉ nhấn mở bán khi đủ điều kiện.

**Kết quả mong đợi:** chuyến chuyển thành `DANG_MO_BAN` qua nghiệp vụ backend. Không đổi trạng thái trực tiếp bằng SQL để bỏ qua kiểm tra.

Không có tài khoản quản trị mặc định. Nếu cần tạo tài khoản kiểm thử quản trị trên database phát triển, làm theo [hướng dẫn T07](t07-vscode-guide.md).

Mặc định ngừng nhận đặt 60 phút trước khởi hành. Nên chọn chuyến ngày mai hoặc xa hơn để tránh hết hạn trong lúc kiểm tra. Xe/nhân viên cần có đăng kiểm, bằng lái và lịch vận hành hợp lệ cho ngày đó.

## 8. Khởi động frontend

Mở terminal thứ hai tại gốc dự án:

```bash
cd src/frontend
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

## 9. Kiểm tra danh mục và tìm kiếm trên giao diện

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

## 10. Kiểm tra từng trường hợp tìm kiếm

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

## 11. Kiểm tra trang chi tiết

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

## 12. Kiểm tra chuyến qua đêm và trạng thái nhận đặt

Chọn chuyến đã chuẩn bị có giờ đi 22:00, đến 05:00 hôm sau.

**Kết quả mong đợi:** ngày khởi hành là ngày tìm kiếm; ngày đến là hôm sau. Không lấy giờ mặc định của tuyến thay giờ đi thực tế. Tìm theo ngày đến không trả chuyến này chỉ vì nó đến vào ngày đó.

Kiểm tra trạng thái:

| Dữ liệu chuyến | Kết quả mong đợi |
| --- | --- |
| Nháp/chưa mở bán/hủy | Không xuất hiện như chuyến đặt được trong kết quả |
| Hết hạn nhận đặt | Không xuất hiện như chuyến đặt được; mở chi tiết hiện không còn nhận đặt |
| Đang mở bán nhưng hết ghế | Có thể vẫn xem chuyến với trạng thái **Hết chỗ**, không chọn ghế thêm |
| Đủ điều kiện và còn ghế | Hiện **Đang nhận đặt vé**, nút chọn ghế được bật |

## 13. Kiểm tra số ghế và chọn ghế

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

## 14. Kiểm tra API không cần đăng nhập

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

## 15. Kiểm tra lỗi tải dữ liệu

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

## 16. Những lỗi thường gặp

| Lỗi | Cách xử lý |
| --- | --- |
| `npm ENOENT package.json` | Chạy npm trong `src/frontend` |
| `cd: src/frontend: No such file` | Kiểm tra `pwd`; nếu đã ở frontend thì không cd thêm |
| `psql: command not found` | Dùng `docker compose exec postgres psql ...` |
| PostgreSQL trong container chạy nhưng Maven báo sai mật khẩu | Kiểm tra cấu hình JDBC của test trên máy; truy cập localhost có thể khác kết nối nội bộ container |
| Backend 404 hoặc chạy code cũ | Kiểm tra WAR mới, build lại rồi restart backend |
| Health HTTP 503 | Xem log backend/database và cấu hình kết nối |
| Danh mục địa điểm trống | Kiểm tra API tỉnh/bến và database đã có dữ liệu hay chưa |
| Có chuyến trong SQL nhưng tìm không thấy | Kiểm tra ngày khởi hành, tỉnh của bến, trạng thái mở bán, hạn đặt và toàn bộ điều kiện T12 |
| `release version 21 not supported` | Cài JDK 21 và kiểm tra Java mà Maven đang dùng |

## 17. Ghi kết quả nghiệm thu

Ghi từng trường hợp: ngày kiểm tra, mã chuyến, tỉnh đi/đến, ngày đi, kết quả thực tế, đạt/chưa đạt, ảnh hoặc log nếu lỗi. Đối chiếu [ma trận T13](../tasks/T13-test-matrix.md) và [API T13](../api/t13-trip-search-availability.md).

Test tự động đã chạy trong môi trường phát triển: 66 test backend liên quan đạt; frontend lint/build đạt. Việc kiểm tra PostgreSQL, trình duyệt trên máy bạn và reviewer nghiệm thu là các bước cần ghi kết quả riêng.

Để dừng frontend, nhấn **Ctrl+C** ở terminal đang chạy `npm run dev`. Nếu cần dừng dịch vụ Docker:

```bash
docker compose stop backend postgres
```

**Kết quả mong đợi:** dịch vụ dừng, dữ liệu database trong volume vẫn được giữ.
