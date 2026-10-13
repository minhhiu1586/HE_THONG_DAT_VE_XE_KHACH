# Chạy và kiểm tra backend trên Windows với VS Code

Hướng dẫn này ghi lại quy trình đã chạy thử trên Windows 11, VS Code và terminal **Git Bash**, tại thư mục gốc `HE_THONG_DAT_VE_XE_KHACH`, nhánh `feat/route-trip-management`. Backend dùng Java 21, Maven, PostgreSQL và Tomcat 10.1 chạy bằng Docker.

## 1. Công cụ và kiểm tra môi trường

Đã kiểm tra thành công với:

- Eclipse Temurin JDK `21.0.12.1`, giải nén tại `C:\Tools\jdk-21.0.12.1+1`.
- Apache Maven `3.10.0`, cài tại `C:\Tools\apache-maven-3.10.0`.
- Docker Desktop đang chạy Linux containers.
- Python 3 có launcher Windows `py`.

Đặt `JAVA_HOME` tới thư mục gốc JDK (thư mục chứa `bin`), rồi thêm `%JAVA_HOME%\bin` vào `Path`. Đóng và mở lại terminal sau khi lưu biến môi trường. Kiểm tra trong PowerShell:

```powershell
java -version
mvn -version
```

Maven phải hiển thị `Java version: 21.0.12.1`. Cũng có thể kiểm tra Docker và Python trong Git Bash:

```bash
docker version
docker compose version
py -3 --version
```

Không cần cài Tomcat riêng nếu chạy theo hướng dẫn này; container dùng Tomcat `10.1-jdk21`.

## 2. Cập nhật mã nguồn

Mở terminal Git Bash tại thư mục gốc repository. Giữ nguyên các thay đổi local trước khi pull; không dùng lệnh reset để xóa chúng.

```bash
git status
git switch feat/route-trip-management
git pull --ff-only origin feat/route-trip-management
```

## 3. Build WAR và chạy test nghiệp vụ

Chạy ở thư mục gốc:

```bash
mvn -f src/backend/pom.xml -B clean verify
```

Kết quả cần có `BUILD SUCCESS`; hiện có 13 test nghiệp vụ và kiểm tra mapping 20 entity. Trong lần chạy ban đầu, quá trình test compile báo thiếu một số file `.class` trong `target/classes`. Xóa thư mục build cũ rồi chạy `clean verify` đã khắc phục:

```bash
rm -rf src/backend/target
mvn -f src/backend/pom.xml -B clean verify
```

Lệnh trên là cú pháp Git Bash. Nếu dùng PowerShell, lệnh xóa tương đương là `Remove-Item -Recurse -Force src/backend/target`.

## 4. Khởi chạy PostgreSQL và Tomcat

Đảm bảo Docker Desktop đang chạy, sau đó:

```bash
docker compose up -d backend
docker compose ps
```

Chờ PostgreSQL hiện `healthy` và backend hiện `Up`. Compose chạy PostgreSQL 16 tại cổng `5432`, Tomcat 10.1 tại cổng `8080`, và triển khai WAR vào context gốc.

Kiểm tra endpoint tỉnh/thành:

```bash
curl -i http://localhost:8080/api/tinh-thanh
```

Kết quả đúng khi nhận `HTTP/1.1 200` và JSON. Database mới, chưa có dữ liệu, sẽ trả `[]`. Mở URL này trên trình duyệt cũng kiểm tra được backend trực tiếp; việc đó **không** có nghĩa giao diện frontend đã tích hợp API.

### Nếu báo thiếu bảng trong database

Docker chỉ tự chạy script trong `/docker-entrypoint-initdb.d` khi tạo volume PostgreSQL lần đầu. Nếu volume đã có sẵn từ bản trước, áp dụng file schema duy nhất từ thư mục gốc:

```bash
docker exec -i datvexe-postgres psql -U datvexe_user -d datvexe -v ON_ERROR_STOP=1 < docs/database/schema/001_route_trip.sql
docker compose restart backend
```

Schema dùng `CREATE TABLE IF NOT EXISTS`, không xóa dữ liệu hiện có. Sau restart, chờ Tomcat khởi động rồi mới gọi `curl`; nếu gọi quá sớm có thể nhận `Empty reply from server`. Nếu lỗi vẫn còn, xem log:

```bash
docker compose logs --tail=100 backend
```

Nếu vừa sửa mã Java, build lại WAR rồi khởi động lại container:

```bash
mvn -f src/backend/pom.xml -B clean verify
docker compose restart backend
```

## 5. Chạy smoke test API

Sau khi `curl -i http://localhost:8080/api/tinh-thanh` trả `HTTP/1.1 200`, chạy bộ kiểm tra API từ thư mục gốc trong Git Bash:

```bash
py -3 tests/backend/api_smoke.py
```

Kết quả thành công có 5 dòng `PASS`, kết thúc bằng:

```text
PASS: 5 API scenarios, 75 HTTP responses checked
```

Smoke test tự tạo dữ liệu thử rồi dọn dữ liệu của lượt chạy; vì vậy gọi lại danh sách tỉnh/thành sau test vẫn có thể trả `[]`.

## 6. Dừng dịch vụ

```bash
docker compose stop
```

Lệnh này dừng container nhưng giữ volume database để dùng lại ở lần chạy sau.
