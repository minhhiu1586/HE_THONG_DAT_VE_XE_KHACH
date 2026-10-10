# Dữ liệu demo và kết quả test

Dữ liệu mô phỏng cho Xe/Ghế/Phân công. Địa danh dùng tên thực tế; thông tin doanh nghiệp, nhân viên, liên hệ, biển số, bằng lái, giá vé và lịch chạy là giả lập.

## Dữ liệu trên database mới

| Nhà xe | Loại xe | Xe | Ghế/giường | Nhân viên | Tuyến | Chuyến | Phân công |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2 | 3: 22/29/40 chỗ | 10 | 309 | 17 | 4 | 46 | 87 |

Có xe sẵn sàng/bảo trì/hết đăng kiểm; tài xế hợp lệ/hết bằng/tạm nghỉ; tài xế chính/phụ và phụ xe; chuyến trùng giờ để test từ chối. Sáu xe bổ sung có lịch 7 ngày, cùng nhà xe và không trùng lịch nhân viên.

Nạp: schema 001 → schema 002 → seed 001 → seed 002. Seed 001 chuyển riêng bộ demo 16 chỗ cũ sang 29 chỗ và bổ sung ghế; chặn nếu có vé/giữ chỗ hoặc xe ngoài demo dùng loại đó. Các bản ghi khác không bị ghi đè, lịch không tự dời ngày.

## Kết quả kiểm tra ngày 09/10/2026

| Kiểm tra | Kết quả |
| --- | --- |
| Maven/H2/PostgreSQL 16 | 20 test đạt, không bỏ qua; build WAR thành công |
| HTTP trên Tomcat 10.1, WAR và seed mới | 28 kiểm tra đạt: CRUD loại xe/xe/ghế/phân công và điều kiện xe |
| Hai yêu cầu phân công cùng lúc | Chỉ một yêu cầu thành công, yêu cầu trùng bị từ chối |
| Nạp lại seed | Không tạo trùng; số lượng giữ nguyên |
| Ghế từng xe; nhà xe của phân công | Không có bản ghi sai |
| Dọn dữ liệu test | Không còn bản ghi HTTP_/TEST_ của lượt kiểm tra |

Maven kiểm tra trên database test riêng; HTTP kiểm tra backend tại cổng 8080 với bộ seed trên. Test đồng thời bao phủ một tình huống phân công, chưa bao phủ tải toàn hệ thống.

## Chạy lại

HTTP trên backend đã có seed: `py -3 tests/backend/vehicle_api_smoke.py`. Đổi địa chỉ bằng biến môi trường `VEHICLE_API_URL` nếu cần.

PostgreSQL: tạo database test riêng và nạp đủ schema/seed trước khi chạy. Thay các giá trị `<...>` bằng kết nối của database đó; không dùng database đang sử dụng chung:

```powershell
mvn -f src/backend/pom.xml -B "-Duser.timezone=Asia/Ho_Chi_Minh" "-Dvehicle.pg.url=jdbc:postgresql://<host>:<port>/<database_test>" "-Dvehicle.pg.user=<user>" "-Dvehicle.pg.password=<password>" verify
```

Maven không có `vehicle.pg.url` sẽ bỏ qua 2 test PostgreSQL. Dùng múi giờ trên để tránh lỗi tên cũ `Asia/Saigon` ở một số image PostgreSQL.

Test PostgreSQL tạo/xóa phân công `TEST_OK`, `TEST_CONCURRENT_1/2`. Script HTTP tạo mã `HTTP_...` riêng cho từng lượt và tự dọn dữ liệu; nếu dọn thất bại sẽ in mã cần kiểm tra. Báo cáo Maven: `src/backend/target/surefire-reports`.

Phần Xe/Ghế/Phân công đã có dữ liệu và test các luồng chính. Frontend đang dùng mock; đặt vé/thanh toán và kết nối giao diện thuộc module của thành viên phụ trách. Cách chạy và điểm nhóm cần review: [vehicle-seat-database.md](../plans/vehicle-seat-database.md).
