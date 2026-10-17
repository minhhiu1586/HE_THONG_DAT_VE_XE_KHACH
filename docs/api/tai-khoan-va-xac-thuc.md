# T07 — Tài khoản và xác thực

Các API dưới đây đã triển khai trên nhánh `feat/t07-account-auth-rbac`. Hợp đồng A–F trong T05 vẫn mô tả các module nghiệp vụ riêng; tài liệu này bổ sung phần tài khoản. T07.1 cung cấp helper, không triển khai thay các API đặt vé, hoàn tiền, kiểm vé chưa có.

## Phiên và lỗi

Backend dùng phiên lưu PostgreSQL, cookie `datvexe_session` HttpOnly, SameSite=Lax, Path=/api, hết hạn tuyệt đối sau một giờ. Database chỉ lưu SHA-256 của token ngẫu nhiên 256 bit. Frontend không lưu token/mật khẩu vào localStorage. Đăng xuất thu hồi phiên hiện tại; đổi mật khẩu và khóa/mở khóa thu hồi tất cả phiên của tài khoản. Vai trò, trạng thái và phạm vi được đọc lại trong transaction của thao tác được bảo vệ.

POST đăng ký/đăng nhập gửi `X-Requested-With: datvexe`. Sau đăng nhập hoặc GET hồ sơ, lấy `csrfToken` và gửi `X-CSRF-Token` cho POST/PUT/PATCH/DELETE đã xác thực. Cookie gửi bằng credentials. Origin của mutation phải khớp `FRONTEND_ORIGIN` nếu request có Origin. Next.js chuyển tiếp `/api` tới `BACKEND_URL` (mặc định http://127.0.0.1:8080); trình duyệt gọi cùng origin.

Triển khai HTTPS đặt `AUTH_COOKIE_SECURE=true`, `FRONTEND_ORIGIN` đúng URL frontend, `BACKEND_URL` tới backend nội bộ. Giá trị Compose mặc định dành cho localhost. Không dùng chung backend này với frontend khác origin khi chưa cấu hình.

Lỗi JSON thống nhất `{code,message,details,timestamp,traceId}`, header `X-Trace-Id`. Chưa đăng nhập, hết hạn, thu hồi hoặc khóa tài khoản: `401 UNAUTHENTICATED`. Đăng nhập sai thông tin hoặc tài khoản không hoạt động: cùng `401 UNAUTHENTICATED`, tránh phân biệt sự tồn tại tài khoản. Thiếu quyền/CSRF: `403 FORBIDDEN`. Tài nguyên không tồn tại: `404 RESOURCE_NOT_FOUND`; tài nguyên tồn tại nhưng ngoài quyền: 403, không trả nội dung/định danh riêng trong thông báo. Không áp dụng 404 thay 403 tùy ý.

JSON chứa trường ngoài DTO trả `400 VALIDATION_ERROR`, kể cả `role`, `loaiTaiKhoan`, `trangThai`, `userId`, `maNhaXe` trong đăng ký/hồ sơ. Không cấp quyền từ request. ID tài nguyên dùng chuỗi; mã số/boolean trong các trường liên kết phạm vi bị từ chối 400, không bỏ qua kiểm tra scope khi DTO có thể tự chuyển kiểu. Nội dung JSON tối đa 64 KiB; sai media type 415; quá lớn 413. Đăng ký/đăng nhập giới hạn tổng 20 request/phút/IP/JVM, trả `429 TOO_MANY_REQUESTS` và `Retry-After: 60`. Đây là giới hạn một instance; triển khai nhiều instance cần giới hạn tập trung tại gateway.

## Endpoint

| Method / URL | Quyền | Request | Thành công |
| --- | --- | --- | --- |
| POST /api/auth/dang-ky | Công khai | hoTen, email, soDienThoai, matKhau; tùy chọn gioiTinh, ngaySinh, cccd | 201 Profile; không tự đăng nhập |
| POST /api/auth/dang-nhap | Công khai | email, matKhau | 200 `{nguoiDung,csrfToken,hetHan}`; Set-Cookie |
| POST /api/auth/dang-xuat | Đã đăng nhập + CSRF | Không có body | 204, xóa cookie và phiên |
| GET /api/tai-khoan/me | Đã đăng nhập | Không có | 200 `{nguoiDung,csrfToken,hetHan}` |
| PUT /api/tai-khoan/me | Chính mình + CSRF | hoTen, email, soDienThoai; gioiTinh, ngaySinh, cccd nullable | 200 Profile |
| PUT /api/tai-khoan/me/mat-khau | Chính mình + CSRF | matKhauHienTai, matKhauMoi | 204, thu hồi mọi phiên, cần đăng nhập lại |
| GET /api/quan-tri/tai-khoan | ADMIN | page mặc định 1; size mặc định 20, tối đa 100 | 200 `{items,page,size,totalItems,totalPages}` |
| PUT /api/quan-tri/tai-khoan/{maNguoiDung}/trang-thai | ADMIN + CSRF | trangThai: KHOA hoặc HOAT_DONG | 200 Profile, thu hồi mọi phiên của đích |

Profile gồm `maNguoiDung,hoTen,email,soDienThoai,gioiTinh,ngaySinh,cccd,loaiTaiKhoan,trangThai`; không có hash, token hay phạm vi quản trị nội bộ. PUT hồ sơ thay toàn bộ trường chỉnh sửa: gửi đủ ba trường bắt buộc; trường nullable bị bỏ qua được coi là null. Không có endpoint chỉnh sửa hồ sơ người khác. ADMIN không được khóa/mở khóa chính mình. CHO_XAC_THUC không đăng nhập được; chức năng xác minh email không thuộc task, đăng ký mới mặc định KHACH_HANG/HOAT_DONG.

Họ tên 1–160 ký tự sau trim; email hợp lệ tối đa 254 ký tự, lowercase trước kiểm tra unique; số điện thoại Việt Nam 10 chữ số bắt đầu 03/05/07/08/09, chấp nhận +84 rồi chuẩn hóa về 0. CCCD 12 số hoặc hộ chiếu một chữ và 7 số; ngày sinh từ 1900 đến ngày hiện tại. Mật khẩu 10–128 ký tự, có chữ và số; PBKDF2-HMAC-SHA256 600.000 vòng, salt ngẫu nhiên riêng từng tài khoản. Trùng định danh: `409 ACCOUNT_ALREADY_EXISTS`; sai mật khẩu hiện tại: `400 VALIDATION_ERROR`; chi tiết validation dùng `details.fieldErrors`.

## Database và tài khoản quản trị đầu tiên

Database hiện có: chạy migration `database/migrations/003_account_auth.sql` sau 001/002, trước chạy WAR mới. Migration dừng nếu email hiện có trùng sau chuẩn hóa; xử lý các bản ghi đó trước khi chạy lại. Compose volume mới tự chạy cả ba migration và seed. Không xóa volume để nâng cấp database đang có.

Không thêm mật khẩu quản trị mặc định hoặc nhận role từ API công khai. Người vận hành đăng ký tài khoản qua API, kiểm tra đúng danh tính và UUID vừa tạo, rồi dùng kết nối database có thẩm quyền:

```sql
-- Thay UUID_TAI_KHOAN_DA_XAC_MINH bằng đúng tài khoản đã kiểm tra.
BEGIN;
UPDATE nguoi_dung SET loai_tai_khoan = 'ADMIN'
WHERE ma_nguoi_dung = 'UUID_TAI_KHOAN_DA_XAC_MINH';
DELETE FROM phien_dang_nhap WHERE ma_nguoi_dung = 'UUID_TAI_KHOAN_DA_XAC_MINH';
COMMIT;
```

Gán NHA_XE cần `ma_nha_xe` đúng nhà xe; NHAN_VIEN cần `ma_nhan_vien` đúng nhân viên. Đây là quản trị dữ liệu tin cậy, không có API công khai tự gán quyền. Thu hồi phiên khi thay quan hệ/quyền; helper vẫn đối chiếu quan hệ hiện tại cho mỗi thao tác. Không lưu mật khẩu rõ vào seed. Khóa/mở khóa qua API ghi audit log actor/target/trạng thái/traceId, không ghi mật khẩu/token.

## Chạy giao diện tài khoản

Các trang `/dang-ky`, `/dang-nhap`, `/tai-khoan`, `/quan-tri/tai-khoan` gọi backend thật qua `/api`. Chạy backend và migration 003 trước; `BACKEND_URL` mặc định `http://127.0.0.1:8080`, đặt biến này trước `npm run build` nếu dùng host khác. Backend đặt `FRONTEND_ORIGIN` theo URL frontend. Production HTTPS đặt `AUTH_COOKIE_SECURE=true`.

AuthProvider giữ hồ sơ và CSRF trong bộ nhớ; cookie phiên HttpOnly do backend quản lý. 401 kết thúc trạng thái đăng nhập; 403 hiện thông báo thiếu quyền. Xem [hợp đồng](tai-khoan-va-xac-thuc.md), [hướng dẫn phân quyền](../../members/NguyenMinhHieu/testing/existing/t07-1-tich-hop-phan-quyen.md), [ma trận kiểm thử](../../members/NguyenMinhHieu/testing/existing/t07-ma-tran-kiem-thu-tai-khoan.md).
