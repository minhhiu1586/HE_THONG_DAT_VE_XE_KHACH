# T12 — Ma trận và kết quả kiểm thử

Nhánh `feat/t12-trip-creation-sale-validation`; nền `80ecf37`. Kiểm thử lại ngày 12/10/2026 trên database PostgreSQL cô lập `t12_recheck_20261012`, đủ sáu seed. Dữ liệu ứng dụng tại database chính không được dùng cho các smoke test thành công.

## Quy tắc được người thực hiện xác nhận

- Chỉ thêm enum NHAP/DANG_MO_BAN; giữ nguyên thuộc tính/cột/entity/schema.
- Cutoff mặc định dự án 60 phút, cấu hình được; không tuyên bố đây là mức chung của website thực tế.
- Tiếp tục T11: không kiểm tra hạng bằng, không đệm nghỉ nhân viên cố định; vai trò, hạn bằng, trùng lịch, nối bến, kế hoạch lái/nghỉ vẫn bắt buộc.
- Giá chuyến lấy từ giá tuyến do Entity không có giá riêng. CHUA_KHOI_HANH trong seed không tự trở thành chuyến mở bán.

## Đối chiếu đề bài

Các dòng có phạm vi giới hạn được ghi rõ; không coi helper là kiểm thử toàn bộ API mua vé chưa triển khai.

| Ca | Nội dung | Kết quả đã kiểm tra | Bằng chứng |
| --- | --- | --- | --- |
| 01 | Tạo nháp hợp lệ | Lưu NHAP; chưa tự mở bán | Java + HTTP + browser |
| 02 | Tuyến không tồn tại | 404, rollback | Java + HTTP |
| 03 | Xe không tồn tại | 404, rollback | Java + HTTP |
| 04 | Giá vé âm hoặc khác giá tuyến | 400; giá lấy từ tuyến | Java + HTTP |
| 05 | Giờ đến trước/bằng khởi hành | 400 | Java + HTTP |
| 06 | 22:00 đến 05:00 ngày sau | Đúng ngày giờ qua đêm | Java + HTTP + browser |
| 07 | Nháp chưa có nhân viên | Cho lưu nháp | Java + HTTP + browser |
| 08 | Mở bán thiếu nhân viên | 409; điều kiện NHAN_SU chưa đạt | Java + HTTP + browser |
| 09 | Tuyến ngừng khai thác | 409 | Java + HTTP |
| 10 | Bến không hoạt động | 409; đọc lại dữ liệu sau chờ khóa | Java + HTTP |
| 11 | Nhà xe bị khóa | 409 | Java + HTTP |
| 12 | Xe bảo trì | 409 | Java + HTTP |
| 13 | Xe hết đăng kiểm | 409 | Java + HTTP |
| 14 | Sơ đồ ghế không hợp lệ | 409 | Java + HTTP |
| 15 | Hai chuyến cùng xe trùng giờ | Chỉ một chuyến được mở bán | Java + PostgreSQL + HTTP |
| 16 | Bằng lái hết hạn | 409 | Java + HTTP |
| 17 | Tài xế không phù hợp vai trò | 409 theo T11; không kiểm tra hạng bằng | Java + HTTP |
| 18 | Nhân viên trùng lịch | 409 theo T11 | Java + HTTP |
| 19 | Điều kiện nối bến/lái-nghỉ | Dùng quy tắc T11; không đệm nghỉ cố định | Java + HTTP |
| 20 | Mở bán đầy đủ điều kiện | DANG_MO_BAN | Java + PostgreSQL + HTTP + browser |
| 21 | Mở bán lại | Không tạo dữ liệu trùng | Java + PostgreSQL + HTTP |
| 22 | Đúng/sau hạn nhận vé | 409, kể cả hết hạn khi đang kiểm tra | Java + HTTP |
| 23 | Tìm chuyến nháp/hết hạn/không đủ điều kiện | Không trả như chuyến được đặt | Java + HTTP |
| 24 | Nhận đặt vé trực tiếp hết hạn | Helper backend từ chối; API mua vé hoàn chỉnh chưa thuộc T12 | Java + HTTP kiểm tra trạng thái; API đặt vé chưa nghiệm thu |
| 25 | Đổi xe khi còn giữ chỗ | 409; giữ lịch sử | Java + HTTP |
| 26 | Đổi xe khi có vé | 409 | Java + HTTP |
| 27 | Thay sơ đồ ảnh hưởng vé | 409 theo T10 | Java + HTTP T10 |
| 28 | Sửa thời gian trùng xe | 409 và rollback | Java + HTTP |
| 29 | Sửa thời gian trùng nhân viên | 409 và rollback | Java + HTTP |
| 30 | Xóa chuyến có đơn/vé | 409 | Java + HTTP |
| 31 | Nhà xe A sửa chuyến B | 403; kiểm tra chủ sở hữu thực tế | Java + HTTP |
| 32 | Chưa đăng nhập | 401 | Java + HTTP |
| 33 | Không đủ quyền | 403 | Java + HTTP + browser |
| 34 | Hai yêu cầu mở bán đồng thời | Không mở bán cả hai chuyến xung đột | PostgreSQL + HTTP hai tài khoản ADMIN |
| 35 | Nháp → T11 phân công → T12 mở bán | Thành công qua API thật và giao diện | Java + PostgreSQL + HTTP + browser |

## Bộ kiểm thử và giới hạn

- Java: `TripServiceTest`, `PostgresTripTest` và regression T07/T08/T09/T10/T11 trong Maven verify. 103 test đạt, 0 lỗi, 0 bỏ qua; gồm PostgreSQL thật và kiểm tra refresh dữ liệu bến trong persistence context.
- HTTP: `tests/backend/trip_sale_smoke.py`: 117 kiểm tra đã đạt trong lần rà soát lại.
- Browser: `tests/frontend/trip_sale_smoke.cjs`: đạt validation tiếng Việt, qua đêm, API phân công T11 thật, điều kiện/mở bán, lọc, read-only sau mở bán, quyền nhà xe/khách, mobile.
- Hồi quy HTTP: api_smoke 95, vehicle_api_smoke 36, vehicle_management_smoke 81 kiểm tra đã đạt trong lần rà soát lại.
- Frontend build đạt; lint 0 lỗi, 3 warning có sẵn trong lib/api.ts và lib/mock-data.ts.
- Validator ma trận chung: 23 use case/82 ca hợp lệ về cấu trúc. Các trạng thái Blocked có sẵn của tài liệu chung không được coi là test T12 đã đạt.

Không triển khai tự chuyển ghế/hủy vé khách, toàn bộ API giữ ghế/đặt vé, giao diện riêng T11, hoặc bỏ qua review PR. PR chưa được review/chấp nhận trong phiên này. Giao diện tìm vé hiện có còn dữ liệu mock; public API đã áp dụng quy tắc T12.

[Hướng dẫn VS Code](../../docs/huong-dan-demo.md#section-2) · [Hợp đồng T12](../../../../docs/api/t12-quan-ly-chuyen-xe.md)
