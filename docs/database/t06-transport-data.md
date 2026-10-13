# T06 — Dữ liệu dự án và kiểm thử

Theo schema/Class Diagram hiện tại; không sửa API, frontend hoặc schema. Dữ liệu mẫu phục vụ kiểm thử, tham khảo địa điểm thực tế. Nhà xe, biển số, nhân viên, giá và lịch chạy là dữ liệu test, không đại diện hoạt động của doanh nghiệp. Dùng lại tỉnh/thành và loại xe demo hiện có.

| Nhóm | Dữ liệu thêm |
| --- | --- |
| Nhà xe / nhân viên | 6 nhà xe tổng cộng (4 mã T06 + 2 mã nền); T06 có 96 nhân viên, 2.796 phân công cùng nhà xe |
| Địa điểm | 2 tỉnh/thành mới, 7 bến; sử dụng 6 tỉnh/thành tổng cộng |
| Tuyến | 16 tuyến hai chiều: HCM–Đà Lạt/Cần Thơ/Nha Trang/Đà Nẵng/Hà Nội và Đà Nẵng–Hà Nội; 24 điểm dừng |
| Xe / ghế | 50 xe, 1.507 ghế; mỗi tuyến có xe 22/29/40 chỗ; thêm xe bảo trì/hết đăng kiểm |
| Chuyến | 1.401 chuyến: lịch 31 ngày, giờ 08:00/14:00/22:00; chuyến hoàn tất/hủy/chưa gán xe |
| Nghiệp vụ mẫu | 25 tài khoản khóa, 66 đơn, 119 vé, 62 giao dịch, 4 giữ chỗ, 6 khuyến mãi, 3 chính sách tạm ngừng, 1 phương thức thanh toán, 1 đánh giá |

Xe 22 chỗ chia tầng 11/11, xe 29 chỗ một tầng, xe 40 chỗ chia 20/20. Ghế cuối T06_X_1 bảo trì, ghế cuối T06_X_2 ngừng sử dụng. Các ghế khác hoạt động. Tuyến dài 36 giờ chạy cách ngày để tránh trùng lịch xe/nhân viên.

## Nạp và kiểm tra

Nhà xe: Phương Trang, Thành Bưởi, Mai Linh Express, Kumho Samco, Hoàng Long, Hải Sơn. Dùng tên thương hiệu để trình diễn; không khẳng định xe/lịch/giá là dữ liệu hoạt động thật của hãng. Hoàng Long và Hải Sơn đã có xe, ghế, tuyến hai chiều, chuyến và phân công.

Docker PostgreSQL cần chạy và đã có schema 001/002, seed 001/002. Tại thư mục gốc:

```powershell
powershell -ExecutionPolicy Bypass -File tests/backend/load_t06_transport.ps1
# Thêm dữ liệu tình huống cho các module còn lại:
powershell -ExecutionPolicy Bypass -File tests/backend/load_t06_transport.ps1 -IncludeScenarios
# Backend cần chạy cho kiểm tra API:
powershell -ExecutionPolicy Bypass -File tests/backend/t06_transport_api.ps1
```

Script nạp seed nền 001/002 rồi thử nạp/nạp lại trong transaction rồi rollback; chỉ commit sau khi kiểm tra đạt. Không tạo container mới, không reset volume. Seed 005 chuẩn hóa trường hiển thị của các mã mẫu DEMO/T06 chỉ định; giữ nguyên ID, lịch, tiền và trạng thái nghiệp vụ. Docker nạp 001 → 002 → 003 → 005 trên database mới; 004 chỉ nạp với -IncludeScenarios. Ngày lấy tại lần nạp đầu, chạy lại không dời lịch; khi hết lịch cần bổ sung đợt mới.

## Mã dùng cho frontend

| API hiện có | Dữ liệu cần đọc |
| --- | --- |
| `/api/ben-xe` | `maBenXe`, `tenBenXe`, `maTinhThanh`; lọc mã T06_ |
| `/api/tuyen-xe` | T06_R_1…16; điểm đầu/cuối là mã bến, ghép tên từ danh sách bến |
| `/api/chuyen-xe/T06_C_1_1` | Chuyến ngày; lấy `maXe=T06_X_1` và ngày khởi hành |
| `/api/chuyen-xe?from=T06_BX_HCM_E&to=T06_BX_LD&date=YYYY-MM-DD` | Thay ngày bằng ngày thuộc lịch seed |
| `/api/xe/T06_X_1/ghe` | 22 ghế; T06_X_2: 29 ghế; T06_X_3: 40 ghế |
| `/api/chuyen-xe/T06_C_3_1` | Chuyến 22:00, đến ngày hôm sau |

Nhãn ghế: xe 22 chỗ P01–P22, xe 29 chỗ 01–29, xe 40 chỗ A01–A20/B01–B20. Mã ghế giữ nguyên; frontend đọc `viTri` và `tang` từ API từng xe. Schema không có tọa độ hàng/cột; cách bố trí này là quy ước dữ liệu test, không khẳng định sơ đồ của hãng cụ thể. Trạng thái vật lý không phải trống/giữ/đặt theo chuyến; trạng thái đặt/giữ cần API nghiệp vụ.

Seed 004 có đơn một/nhiều vé, vé đã hoàn/hoàn một phần, thanh toán thành công/thất bại/đang xử lý, giữ chỗ hết hạn/hủy và khuyến mãi còn/hết lượt. Đây là tình huống test theo schema hiện có: không thêm enum hoặc bảng. Giá trị khuyến mãi và chính sách mẫu chưa phải quy định chính thức; chính sách để tạm ngừng. Tài khoản khóa, mật khẩu đánh dấu không dùng đăng nhập; giao dịch mô phỏng không dùng thanh toán thật. Giữ chỗ/khuyến mãi có mốc thời gian cố định khi nạp đầu; kiểm tra biên cần đồng hồ test phù hợp, không tự dời mốc khi nạp lại.

## Kết quả 11/10/2026

Đã nạp vào database dự án; lịch mới 11/10–10/11/2026. SQL và nạp lại 20 bảng đạt; API đạt 16 kiểm tra đọc. Backend build đạt, 18 test đạt và 2 test PostgreSQL cũ bỏ qua. [Ma trận và giới hạn nghiệm thu](../../tests/use-cases/README.md): 82 ca, 20 Pass, 0 Not Run, 62 Blocked; chưa nghiệm thu toàn bộ T06/giao diện.

## File bàn giao

Seed nền `001_demo.sql` và `002_fleet_demo.sql` giữ mã DEMO để test/FK tương thích, nhưng tên hiển thị đã chuẩn hóa. Tên file/mã là nội bộ. Docker nạp dữ liệu vận tải mặc định; `004_t06_scenarios.sql` là bộ tình huống test riêng. Không xóa bản ghi hoặc reset volume để dọn dự án.

Giữ riêng theo trách nhiệm: 003 tạo dữ liệu vận tải; 004 tạo tình huống nghiệp vụ; 005 cập nhật dữ liệu T06 đã nạp. Hai SQL assertion kiểm tra hai seed tương ứng; script nạp điều phối thứ tự 001 → 002 → 003 → 004 (tùy chọn) → 005 → kiểm tra. API test chỉ đọc HTTP. Ma trận là kế hoạch kiểm thử; kết quả chạy lưu riêng trong `tests/use-cases/results.md`.

Khi cập nhật: giữ nguyên ID đang được vé/đơn tham chiếu; sửa assertion khi thay đổi số lượng hoặc quy ước dữ liệu; chạy script nạp và API test trước khi commit. Không thay đổi schema, mã tham chiếu hoặc logic backend/frontend.

- Dữ liệu: `tests/backend/fixtures/sql/003_transport.sql`, `004_t06_scenarios.sql`, `005_t06_display_alignment.sql`.
- Hướng dẫn: file này.
- Nạp/kiểm tra: `tests/backend/load_t06_transport.ps1`, `t06_transport_assertions.sql`, `t06_scenario_assertions.sql`, `t06_transport_api.ps1`.
- Kiểm tra chín ca vận tải còn lại: `python tests/backend/t06_remaining_api.py`; tạo mã test riêng và tự dọn. Database trống đã được kiểm tra schema/seed trong container hiện có; xem kết quả ở ma trận.
- Ma trận: `tests/use-cases/README.md`, `test-cases.json`, `check_matrix.py`, `results.md`.

Seed 001/002 và Docker Compose được chỉnh sửa; các file T06 là file mới trên nhánh `feature/t06-seed-test-matrix`. Không sửa code backend/frontend hoặc schema. Docker Compose đã thêm seed vận tải 003 và cập nhật 005 vào khởi tạo database mới. Thành viên dùng cùng database sẽ thấy thêm mã T06_; seed khởi tạo chỉ chạy khi volume PostgreSQL trống; volume hiện có dùng script nạp, không xóa volume.

Địa điểm tham khảo: [Miền Đông mới](https://benxemiendongmoi.com.vn/), [Đà Lạt](https://benxehue.vn/tuyen-xe/129), [Cần Thơ](https://cand.vn/Giao-thong/Chuyen-toan-bo-hoat-dong-ben-xe-91B-ve-ben-xe-trung-tam-Can-Tho-i503995/), [Đà Nẵng](https://blog.vexere.com/ben-xe-trung-tam-da-nang/), [Nước Ngầm](https://benxenuocngam.com/ben-xe-nuoc-ngam-o-dau.html). Địa chỉ lưu đường/địa danh để tránh phụ thuộc tên quận/phường cũ. Điểm nghỉ trung gian và thời gian dừng là dữ liệu test, không phải danh sách trạm được cấp phép của hãng.
