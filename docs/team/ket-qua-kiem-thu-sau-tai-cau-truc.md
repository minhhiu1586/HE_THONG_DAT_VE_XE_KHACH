# Xác minh tái cấu trúc theo thành viên — 18/10/2026

## Phạm vi và cách tổ chức

- Source chính thức di chuyển về members/<HoTen>/frontend/src và members/<HoTen>/backend/src; không tạo bản sao source trong apps.
- apps/frontend giữ entry route/layout/config/public; bảy trang có phần triển khai chuyển sang thành viên, route re-export default.
- apps/backend đóng gói một WAR; build-helper khai báo source/test roots. Giữ package Java và persistence mapping, không tạo dependency vòng bằng cách tách JAR khi service còn gọi chéo.
- Một bộ dependency npm và lockfile ở root; TypeScript/Next.js dùng alias trực tiếp đến thành viên/shared. Không yêu cầu symlink workspace.
- Kiểm thử HTTP/browser xuyên module và fixture giữ ở tests để không phá luồng chạy chung. Test Java thực sự đã chuyển theo người phụ trách.
- Hai thay đổi chưa commit có sẵn ở NguoiDungRepository.java và account-api.ts được giữ trong snapshot và source mới.

## Kết quả đã chạy

| Kiểm tra | Kết quả |
|---|---|
| Backend Maven verify, đóng gói WAR | PASS; 268 test: 205 thực thi đạt, 63 bỏ qua, 0 failure, 0 error |
| Frontend Next.js production build | PASS; compile, TypeScript và sinh các route thành công |
| TypeScript noEmit | PASS |
| ESLint toàn app, members và shared | PASS, 0 error; còn 2 cảnh báo img có sẵn tại BusStations và PopularRoutes |
| management_options_check.cjs | PASS |
| trip_search_check.cjs | PASS |
| check_matrix.py | PASS về tính nhất quán: 23 use case, 82 case; giữ trạng thái lịch sử 20 Pass và 62 Blocked |
| npm install offline, ignore-scripts | PASS sau khi dùng dependency chung root; không đổi version package bên thứ ba |
| git diff --check | PASS; có cảnh báo LF/CRLF của cấu hình Git, không có whitespace error |

## Đối chiếu nội dung

- 135 file Java (source và test) khớp byte với snapshot trước khi chuyển.
- 79 file TypeScript giữ nguyên sau khi bỏ qua nội dung module specifier của import.
- 17 file CSS khớp byte.
- 20 file SQL schema/seed khớp byte; .env.example không đổi.
- Các file cấu hình, path trong script và link tài liệu được cập nhật. Không sửa nghiệp vụ, UI, API hoặc dữ liệu.

Chi tiết: [structure-integrity.json](structure-integrity.json), [backend-test-summary.json](backend-test-summary.json), [file-moves.json](file-moves.json).

## Giới hạn

- Chưa chạy lại HTTP/browser E2E, PostgreSQL test có điều kiện, camera/POS, VNPay hoặc Brevo thật; không tuyên bố các mục này đã đạt.
- Không sửa thiếu chức năng cấp tài khoản nhân viên, payment context RAM, quota giữ chỗ hoặc các vấn đề nghiệp vụ đã rà trước đó.
- Kiểm vé hiện có giữ nguyên theo yêu cầu chỉ tái cấu trúc; chưa gỡ UI/menu hay thêm thiết bị.
- Folder test-case/bug-report chứa hướng dẫn và liên kết bằng chứng thật; không tạo báo cáo lỗi hoặc kết quả nghiệm thu giả.
- Source hỗn hợp VehicleService và BookingService chưa bị tách hàm; trách nhiệm phối hợp ghi ở phan-cong-thanh-vien.md.
- Chưa commit, push hoặc sửa task Plane.

## Lệnh chuẩn từ root

```powershell
npm ci
npm run typecheck
npm run lint
npm run build
npm run dev
# Terminal khác khi cần backend:
mvn -B verify
docker compose up -d postgres
docker compose run --rm migrate
docker compose up -d --force-recreate backend
```

WAR mới: apps/backend/target/he-thong-dat-ve-xe-khach-0.1.0-SNAPSHOT.war. Docker compose đã đổi mount theo đường dẫn mới. Không cần xóa volume database.

Môi trường xác minh: JDK 24 chạy Maven với compile release 21; CI tiếp tục dùng JDK 21. Các CLI Node được chạy bằng runtime Node có sẵn trong phiên công cụ do node.exe hệ thống bị hạn chế thực thi trong sandbox.
