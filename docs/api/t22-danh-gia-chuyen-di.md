# T22 — Đánh giá chuyến và kiểm duyệt

## Quy tắc triển khai

Dùng `DanhGia`, trạng thái `CHO_DUYET`, `DA_DUYET`, `AN` và ràng buộc `UNIQUE(ma_nguoi_dung,ma_chuyen)` hiện có. Thêm cờ `da_xoa` để xóa mềm; DTO hiển thị `DA_XOA` khi cờ bật, không tạo thêm enum trùng nghĩa. Cặp tài khoản–chuyến vẫn được giữ sau xóa và không cho tạo lại hoặc khôi phục qua API thường.

Quyền đánh giá thuộc chủ tài khoản của `DonDatVe`, không dựa vào tên hoặc số điện thoại hành khách. Chuyến phải `HOAN_TAT`; có ít nhất một vé `DA_XUAT_VE` hoặc `DA_LEN_XE` thuộc đơn `DA_THANH_TOAN` và có giao dịch `THANH_CONG` hoặc `HOAN_TIEN_MOT_PHAN`. Giữ ghế, đơn nháp, vé `DA_HOAN`/`DA_HUY` và giao dịch chưa xác nhận không cấp quyền. Nhiều vé hoặc nhiều đơn cùng chuyến vẫn chỉ có một đánh giá.

Áp dụng phương án đề xuất của đặc tả: không bắt buộc ghi nhận lên xe; sao là số nguyên 1–5; nhận xét không bắt buộc, tối đa 2000 ký tự, loại khoảng trắng đầu/cuối. Từ chối ký tự điều khiển không hợp lệ và Unicode lỗi trước khi ghi database. Backend từ chối các trường sở hữu, chuyến và trạng thái được gửi trong DTO sửa. Nội dung hiển thị bằng văn bản React, không dùng HTML trực tiếp.

Tạo hoặc sửa có thay đổi điểm/nội dung luôn chuyển về `CHO_DUYET`; sửa không thay đổi dữ liệu thì giữ trạng thái. Sửa vẫn kiểm tra lại quyền sở hữu và vé đủ điều kiện. Admin kiểm duyệt toàn hệ thống; nhà xe chỉ kiểm duyệt chuyến của mình và phải còn hoạt động. Khách chỉ được xóa mềm đánh giá của mình. Nhân viên không có quyền kiểm duyệt. Ẩn yêu cầu lý do tối đa 1000 ký tự.

Rating tính trực tiếp từ bản ghi `DA_DUYET` và `da_xoa=false`, không lưu bộ đếm tăng/giảm. Chưa có đánh giá được duyệt: `rating=null`, `totalItems=0`. Mọi thay đổi, lịch sử và quyền sở hữu được xử lý trong transaction, khóa chuyến trước đánh giá. Yêu cầu tạo lặp trả 409; duyệt/ẩn/xóa lặp không ghi lịch sử hay tác động rating lần hai.

Phạm vi rating là từng chuyến, không giả định đó là rating chung của nhà xe. API công khai chỉ trả mã đánh giá, mã chuyến, sao, nhận xét, thời điểm và tên hiển thị chung “Khách đã đặt vé”; không trả mã tài khoản, email, điện thoại hoặc lịch sử kiểm duyệt.

Các lựa chọn triển khai theo đề xuất cần được nhóm đối chiếu T01/T05 khi nghiệm thu: thang sao, nhận xét không bắt buộc, điều kiện không bắt buộc lên xe, quyền kiểm duyệt Admin/nhà xe và xóa không khôi phục.

## API

| API | Quyền / kết quả |
| --- | --- |
| `GET /api/danh-gia/dieu-kien?maChuyen=C` | Khách: `coTheDanhGia`, `coTheSua`, `lyDo`, đánh giá của chính tài khoản nếu đã tồn tại |
| `POST /api/danh-gia` | Khách đủ điều kiện, 201; JSON `{"maChuyen":"C","soSao":5,"binhLuan":"Chuyến đi tốt"}` |
| `PUT /api/danh-gia/{id}` | Chủ đánh giá; JSON `{"soSao":4,"binhLuan":"Nội dung sửa"}` |
| `DELETE /api/danh-gia/{id}` | Chủ đánh giá hoặc người quản lý trong phạm vi; xóa mềm |
| `GET /api/danh-gia/cong-khai?maChuyen=C&page=1&size=10` | Công khai: `items`, `page`, `totalItems`, `totalPages`, `rating` |
| `GET /api/quan-ly/danh-gia?trangThai=CHO_DUYET&maChuyen=C&page=1&size=20` | Admin/nhà xe: lọc, phân trang; có thể lọc `DA_XOA` |
| `POST /api/danh-gia/{id}/duyet` | Admin/nhà xe trong phạm vi; JSON `{}` |
| `POST /api/danh-gia/{id}/an` | Admin/nhà xe trong phạm vi; JSON `{"lyDo":"Nội dung không phù hợp"}` |
| `GET /api/danh-gia/{id}/lich-su` | Admin/nhà xe trong phạm vi: người thao tác, trạng thái, lý do và thời điểm |

Dùng cookie phiên và CSRF hiện có. Các API ghi dữ liệu gửi `X-Requested-With: datvexe` và `X-CSRF-Token`. Lỗi theo `ApiErrors` T05, có `code`, `message`, `details`, `timestamp`, `traceId`.

Mã lỗi gồm `UNAUTHENTICATED`, `FORBIDDEN`, `TRIP_NOT_COMPLETED`, `REVIEW_NOT_ELIGIBLE`, `REVIEW_ALREADY_EXISTS`, `INVALID_REVIEW_STARS`, `INVALID_REVIEW_CONTENT`, `REVIEW_NOT_FOUND`, `REVIEW_DELETED`, `REVIEW_REASON_REQUIRED`, `INVALID_PAGINATION`, `VALIDATION_ERROR`.

## Chạy và kiểm tra giao diện

Từ gốc repository, với `.env` riêng đã cấu hình database:

```bash
docker compose stop backend
docker compose up -d postgres
docker compose run --rm migrate
mvn -B -f apps/backend/pom.xml clean package
docker compose up -d --force-recreate backend
cd apps/frontend
npm ci
npm run dev
```

Migration `014_trip_reviews.sql` bổ sung cờ xóa mềm và bảng lịch sử, giữ đánh giá cũ. Không xóa volume hoặc nạp lại seed để chạy T22.

- Khách: `/tai-khoan` → lịch sử đơn → đánh giá chuyến đã hoàn tất. Mỗi chuyến chỉ hiện một form dù có nhiều đơn.
- Người kiểm duyệt: `/quan-tri/danh-gia` hoặc mục “Kiểm duyệt đánh giá” trong menu quản lý; lọc trạng thái/mã chuyến, xem chi tiết và lịch sử, duyệt, ẩn hoặc xóa mềm.
- Công khai: `/tim-chuyen/{maChuyen}` → đánh giá chuyến và rating.

Các màn cũ giữ stylesheet và bố cục. T22 dùng lại `Account.module.css`, `TripManagement.module.css`, `TripDetails.module.css`; stylesheet mới chỉ dành cho ô nhận xét và từng đánh giá công khai.

## Kiểm thử

```bash
mvn -B -f apps/backend/pom.xml -Dtest=ReviewServiceTest,TripServiceTest,ThanhToanServiceTest test
```

Để kiểm tra cùng bộ ca trên PostgreSQL, đặt `REVIEW_TEST_JDBC_URL`, `REVIEW_TEST_JDBC_USER`, `REVIEW_TEST_JDBC_PASSWORD` cho database kiểm thử riêng rồi chạy thêm `ReviewServicePostgresTest`. Mỗi ca tạo schema `t22_*` riêng và xóa đúng schema đó sau khi chạy.

Kiểm thử trình duyệt dùng backend thật, database dùng một lần đã có migration 001–014; không trỏ vào database đang phục vụ người dùng:

```bash
FRONTEND_URL=http://localhost:3022 DB_CONTAINER=t22-review-postgres DB_NAME=t22_review DB_USER=postgres node tests/frontend/review_smoke.cjs
```

Script yêu cầu Playwright và Chromium; có thể đặt `CHROMIUM_PATH`. Tạo dữ liệu có tiền tố ngẫu nhiên trên database dùng một lần. Kiểm tra tạo/sửa/duyệt/ẩn/xóa, rating, chống thực thi script, bảo vệ thông tin và chiều rộng 390px.

Kết quả tự kiểm thử ngày 14/10/2026: 111 kiểm thử đạt trong lần rà soát cuối của bộ chọn T22 trên H2/PostgreSQL cùng hồi quy chuyến, thanh toán. Bộ hồi quy toàn bộ không thuộc nhóm PostgreSQL có 199 kiểm thử đạt. Hai bộ có ca trùng nhau, không cộng thành tổng 300; không có ca bỏ qua hoặc thất bại trong các bộ đã chạy. Mã nguồn chính và kiểm thử được biên dịch bằng Java 21, chạy JUnit trực tiếp do Maven trong môi trường gặp lỗi truy cập kho plugin. Backend Tomcat đã khởi động và xác nhận `/api/health`; đủ migration 001–014 được áp dụng trên PostgreSQL mới. Browser smoke test đã đạt. Lint/TypeScript được kiểm tra riêng.

Chưa có PR hoặc nghiệm thu của thành viên review; các kết quả tự kiểm thử không thay thế việc nhóm chốt quy tắc và review theo đặc tả.
