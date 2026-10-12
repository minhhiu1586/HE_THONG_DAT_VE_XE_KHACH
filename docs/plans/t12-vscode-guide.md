# T12 — Chạy và kiểm tra trên VS Code

## Chuẩn bị

Cần JDK 21 (có javac), Maven, Node từ 20.9, Python 3 và Docker Desktop chạy Linux containers. Mở thư mục gốc dự án trong VS Code, dùng Git Bash. PostgreSQL chạy bằng Docker; không cần mở pgAdmin. Giữ dữ liệu hiện tại, không chạy `docker compose down -v`.

```bash
git switch feat/t12-trip-creation-sale-validation
mvn -f src/backend/pom.xml clean verify
docker compose up -d postgres backend
```

Đợi backend sẵn sàng tại `/api/health`. Nếu backend đang chạy trong lúc build WAR, restart riêng backend:

```bash
docker compose restart backend
```

Trong terminal khác, từ thư mục gốc:

```bash
cd src/frontend
npm ci
npm run build
npm run lint
npm run dev
```

Không chạy npm ở thư mục gốc vì package.json nằm trong `src/frontend`. Để thoát frontend, Ctrl+C.

## Kiểm tra giao diện

1. Đăng nhập ADMIN, chọn **Quản lý → Chuyến xe và mở bán**. NHA_XE chỉ thấy dữ liệu nhà xe mình; khách không có quyền.
2. Tạo mã chuyến mới, chọn tuyến và ngày tương lai. Chưa chọn xe/phân công vẫn lưu nháp được; chuyến không tự xuất hiện trong API tìm vé.
3. Chọn tuyến 7 giờ, nhập 22:00: giờ đến phải là 05:00 ngày sau. Giờ đến sớm hơn khởi hành bị từ chối.
4. Mở chi tiết, kiểm tra tám nhóm điều kiện. Thiếu xe/nhân viên không được mở bán.
5. Phân công qua T11 API, gồm nhân sự bắt buộc và xác nhận kế hoạch phù hợp. T11 hiện chưa có giao diện phân công. Không chỉ sửa trạng thái bằng SQL để giả lập mở bán.
6. Kiểm tra lại điều kiện, xác nhận mở bán. Chuyến đủ điều kiện mới chuyển trạng thái; sau mở bán không sửa xe/lịch bằng form nháp.
7. Lọc trạng thái, tuyến, xe, khoảng ngày; kiểm tra trên cửa sổ hẹp và tài khoản nhà xe khác.
8. Thông báo dùng tiếng Việt; chọn tab/thao tác khác phải xóa thông báo cũ. Nhập rỗng trường bắt buộc phải có hướng dẫn tiếng Việt.

Mặc định hạn nhận vé là 60 phút trước khởi hành, đổi qua `TRIP_BOOKING_CUTOFF_MINUTES` rồi tạo lại container backend. Tại đúng hạn và sau hạn đều bị chặn, kể cả gọi helper backend trực tiếp.

## Kiểm thử tự động

Từ thư mục gốc, backend chạy cổng8080 và Docker PostgreSQL hiện có:

```bash
py -3 tests/backend/trip_sale_smoke.py
```

Script tạo fixture có tiền tố riêng và dọn fixture khi hoàn tất. Khuyến nghị database kiểm thử riêng; có thể đặt `DB_NAME` và `API_BASE_URL` trỏ đến backend dùng đúng database đó. Không trộn backend database thật với DB_NAME kiểm thử.

Kiểm thử Java/PostgreSQL:

```bash
mvn -f src/backend/pom.xml -Dvehicle.pg.url=jdbc:postgresql://localhost:5432/datvexe -Dvehicle.pg.user=datvexe_user -Dvehicle.pg.password=datvexe_password -Dt07.t06.enabled=true verify
```

Các test PostgreSQL tạo và dọn fixture riêng; kiểm tra kết quả Skipped để biết đã thực sự chạy. Không có cấu hình PG thì các test này có thể bị bỏ qua.

Kiểm thử browser cần Playwright và Chromium. File `tests/frontend/trip_sale_smoke.cjs`, cấu hình `FRONTEND_URL`, `DB_NAME`, `CHROMIUM_PATH`. Test sử dụng API T11 thật để phân công rồi mở bán từ giao diện.

Đối chiếu [ma trận kiểm thử](../tasks/T12-test-matrix.md) và [API T12](../api/t12-trip-management.md). Việc PR được review/chấp nhận là bước riêng, không được suy ra từ test đạt.
