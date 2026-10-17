# Bàn giao T09 — nhà xe và phạm vi dữ liệu

## Quy tắc đã thống nhất

Nhóm dùng bốn trạng thái nhà xe `CHO_PHE_DUYET`, `HOAT_DONG`, `TAM_NGUNG` và `NGUNG_HOAT_DONG`. Nhà xe mới bắt đầu ở `CHO_PHE_DUYET`; chỉ Admin được chuyển trạng thái, cấp hoặc thu hồi tài khoản quản lý. Một tài khoản quản lý thuộc tối đa một nhà xe theo quan hệ `NguoiDung.nhaXe` trong T03.

`TAM_NGUNG` và `NGUNG_HOAT_DONG` chặn thao tác ghi của quản lý nhà xe và tạo chuyến mới. Chuyến của nhà xe ở hai trạng thái này không xuất hiện trong tìm kiếm chuyến công khai. API đọc tài nguyên lịch sử vẫn kiểm tra chủ sở hữu thực từ database. Khóa nhà xe không tự đổi trạng thái chuyến, giữ chỗ, đơn, vé, thanh toán hoặc hoàn tiền.

## Trước và sau khi khóa

1. Admin mở hồ sơ nhà xe và xem `GET /api/quan-tri/nha-xe/{maNhaXe}/anh-huong`.
2. Xem số chuyến sắp khởi hành, chuyến đang chạy, giữ chỗ còn hạn, vé đã phát hành và vé của đơn có thanh toán thành công. Kiểm tra chi tiết theo từng module trước khi quyết định cách hỗ trợ hành khách.
3. Admin xác nhận đổi trạng thái. `PUT /api/quan-tri/nha-xe/{maNhaXe}/trang-thai` khóa hàng nhà xe, tính lại số liệu trong giao dịch, cập nhật trạng thái và trả `{nhaXe,anhHuong}`. Backend ghi log actor, mã nhà xe, trạng thái mới, số chuyến/vé chính và trace ID.
4. Chuyển các chuyến đang chạy, chuyến sắp khởi hành và vé đã thanh toán cho người phụ trách vận hành để xử lý theo quy trình hủy chuyến/đổi lịch/hoàn vé riêng của nhóm. T09 chỉ cung cấp số liệu và chặn hoạt động mới; không tự thực hiện các nghiệp vụ này.
5. Nếu cần cho nhà xe hoạt động lại, Admin xác minh vấn đề đã được xử lý rồi chuyển sang `HOAT_DONG`.

Số liệu `anhHuong` là ảnh chụp tại thời điểm truy vấn. Frontend hiển thị trước khi xác nhận; phản hồi PUT chứa số liệu được tính lại. Không dùng các con số này làm căn cứ tự động hủy vé hay hoàn tiền.

## Hợp đồng cho module đặt vé

Khi có code giữ chỗ, xác nhận đơn hoặc phát hành vé, module đó phải gọi `AccessControl.saleAllowed(em, maChuyen)` **trong cùng giao dịch ghi**, ngay trước khi chấp nhận hoạt động bán mới. Helper khóa hàng chuyến và nhà xe; trả `409 OPERATOR_SUSPENDED` khi nhà xe không hoạt động, `409 OPERATOR_UNASSIGNED` nếu chuyến chưa xác định nhà xe vận hành. Không dùng kết quả tìm kiếm chuyến hoặc trạng thái đã tải trước để quyết định bán.

Sau khi tích hợp, kiểm thử đồng thời hai giao dịch: một giao dịch khóa nhà xe, một giao dịch giữ ghế/phát hành vé. Yêu cầu hoàn thành theo thứ tự khóa hàng; giao dịch bán bắt đầu sau khi khóa được commit phải bị từ chối. Kiểm tra vé đã thanh toán trước đó vẫn tra cứu được.

## Kiểm thử và điểm cần nghiệm thu

| Ca | Hiện trạng |
| --- | --- |
| Admin tạo/sửa/tìm/lọc nhà xe; mã trùng, dữ liệu sai và phân trang | Test service H2; cần chạy HTTP trên PostgreSQL |
| Cấp/thu hồi quyền, thu hồi phiên cũ, chặn tài khoản khác nhà xe | Test service và guard H2; cần chạy HTTP trên PostgreSQL |
| Nhà xe bị khóa: chặn tạo chuyến, ẩn khỏi tìm kiếm, giữ nguyên chuyến/đơn/vé/thanh toán | Test H2 |
| Bán vé trên chuyến chưa gắn nhà xe hoặc nhà xe bị khóa | Test helper H2; còn chờ luồng đặt vé thực tế |
| Tranh chấp hàng và dữ liệu mẫu trên PostgreSQL | Các test PostgreSQL có điều kiện; chưa chạy trên database thử nghiệm riêng |
| Giao diện Admin thao tác qua cookie, CSRF và API thật | TypeScript, lint, build; cần kiểm thử trình duyệt đầu cuối |

Test PostgreSQL hiện có chỉ được bật khi truyền `vehicle.pg.url`, `vehicle.pg.user`, `vehicle.pg.password`; nhóm test T06 còn cần `t07.t06.enabled=true`. Chỉ dùng database PostgreSQL thử nghiệm có thể xóa sau khi chạy, đã nạp schema và seed phù hợp. Không chạy trên database làm việc chung.

Từ thư mục gốc dự án, chạy bằng PowerShell khi Docker Desktop hoạt động:

```powershell
docker compose -f tests/backend/docker-compose.postgres.yml up -d --wait
mvn -B -f apps/backend/pom.xml verify "-Duser.timezone=Asia/Ho_Chi_Minh" "-Dvehicle.pg.url=jdbc:postgresql://localhost:55432/datvexe_test" "-Dvehicle.pg.user=datvexe_test" "-Dvehicle.pg.password=datvexe_test" "-Dt07.t06.enabled=true"
docker compose -f tests/backend/docker-compose.postgres.yml down
```

Compose này dùng database tạm trong bộ nhớ và chỉ mở cổng trên `127.0.0.1:55432`; dữ liệu bị xóa khi container dừng. Nếu cổng 55432 đang bận, đổi cổng trong compose và JDBC URL cùng lúc. Lệnh `down` vẫn cần chạy sau khi test thất bại.

`-Duser.timezone=Asia/Ho_Chi_Minh` đặt tên múi giờ chuẩn cho JVM chạy test. Trên một số máy Windows, JVM tự nhận `Asia/Saigon`; PostgreSQL 16 từ chối tên này ngay khi JDBC kết nối và cả 9 test PostgreSQL báo lỗi trước khi chạy logic kiểm thử.

Theo `tests/use-cases/test-cases.json`, **UC-15 là Quản lý nhà xe**, còn **UC-21 là Kiểm vé**. Phần đầu mô tả T09 trên Plane đang ghi UC-21; cần sửa tham chiếu đó thành UC-15 khi cập nhật task.

T09 có thể đưa vào PR để các thành viên dùng nền tảng nhà xe và tích hợp module của họ. Chỉ đánh dấu nghiệm thu toàn bộ sau khi kiểm thử PostgreSQL/HTTP, luồng đặt vé gọi helper, quy trình vận hành được nhóm chấp nhận và PR được reviewer duyệt.
