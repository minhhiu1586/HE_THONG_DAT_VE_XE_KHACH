# Chuẩn hóa tỉnh và tìm kiếm chuyến

Migration `017_canonical_cities.sql` gộp tỉnh trùng tên, cập nhật mã tỉnh của bến xe,
giữ nguyên mã bến, tuyến, chuyến, đơn và vé. Mã chuẩn của danh mục seed:
`CT`, `HCM`, `LD`, `KH`, `DN`, `HN`, `NB`.
Tỉnh ngoài danh mục chọn một mã hiện có, ưu tiên mã không có tiền tố `DEMO_`, `T06_`.
Tên được so sánh sau khi bỏ dấu, chuẩn hóa hoa/thường và khoảng trắng.
Nếu mã chuẩn thuộc tỉnh khác, migration dừng và transaction rollback để kiểm tra thủ công.
Unique index ngăn tạo cùng tên tỉnh dưới mã khác, kể cả hai request đồng thời.

Frontend chỉ tải `/api/tinh-thanh`. `searchTrips` gửi một GET `/api/chuyen-xe`
với `from`, `to`, `date`; backend giữ nguyên quy tắc đang mở bán, thời hạn đặt vé,
điều kiện xe, nhân sự và số ghế còn. Bộ lọc giờ và sắp xếp vẫn ở frontend.
Giao diện không thay đổi. URL cũ chứa mã tỉnh bị gộp cần chọn lại địa điểm trên form.

## Áp dụng trên máy đang có dữ liệu

Chạy từ thư mục gốc sau khi lấy bản sửa. Dừng backend để tránh ghi dữ liệu trong lúc gộp:

```bash
docker compose stop backend
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > backup-before-city-normalization.sql
docker compose run --rm migrate
```

Chỉ tiếp tục nếu backup và migration đều thành công. Migration không xóa volume,
không nạp lại tài khoản và không đổi mật khẩu. Không commit file backup vào Git.

```bash
docker compose up -d backend
cd src/frontend
npm ci
npm run dev
```

Nếu frontend đang chạy, dừng bằng Ctrl+C trước khi chạy lại. Chọn lại điểm đi/đến.
Kiểm tra PostgreSQL từ thư mục gốc:

```bash
docker compose exec postgres psql -U datvexe_user -d datvexe -c "SELECT ma_tinh_thanh,ten_tinh_thanh FROM tinh_thanh ORDER BY ten_tinh_thanh;"
docker compose exec postgres psql -U datvexe_user -d datvexe -c "SELECT city_name_key(ten_tinh_thanh),count(*) FROM tinh_thanh GROUP BY city_name_key(ten_tinh_thanh) HAVING count(*)>1;"
```

Truy vấn thứ hai phải trả `(0 rows)`. Trong Network của trình duyệt, mỗi lần tìm
chỉ có một request `/api/chuyen-xe`, không gọi `/api/ben-xe` để tải địa điểm.

## Kiểm tra tự động

```bash
node tests/frontend/trip_search_check.cjs
python3 tests/backend/canonical_cities_check.py
```

Kiểm tra PostgreSQL tạo rồi xóa CSDL thử riêng; không chỉnh CSDL đang sử dụng.
Có thể cấu hình `DB_CONTAINER`, `DB_USER` nếu khác mặc định.
Bao phủ sáu nhóm tỉnh trùng, chỉ có mã demo, nạp lại seed, chạy migration lại,
chặn tên trùng và giữ nguyên dữ liệu chuyến khi chuyển liên kết bến.
