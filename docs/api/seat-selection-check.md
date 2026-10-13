# Kiểm tra chọn ghế

Trang chi tiết có nút chọn ghế; ghế trống mở trang `/tim-chuyen/{maChuyen}/seats`. Trang chọn ghế dùng `GET /api/chuyen-xe/{maChuyen}/ghe`, đọc `trangThaiSuDung` và `tinhTrangKhaDung`. Ghế bảo trì/ngừng sử dụng/đang giữ/đã bán không chọn được.

| Xe | Sơ đồ hiển thị | Chuyến đã kiểm ngày 14/10/2026 |
| --- | --- | --- |
| 22 chỗ | 2 tầng, 11 cabin/tầng, hai dãy 1+1 có lối đi | CX_CT_2_20261014_1 |
| 29 chỗ | 1 tầng, hai dãy 2+2 có lối đi | CX_CT_1_20261014_1 |
| 40 chỗ | 2 tầng, 20 giường/tầng, dãy 2+1 có lối đi | CX_FUTA_2_20261014_1 |

Sơ đồ hiển thị dựa trên số chỗ, tầng và vị trí hiện có trong database; đây là bố trí của dự án, chưa phải bản vẽ kỹ thuật của từng hãng xe. Không đổi mã ghế hoặc tổng số ghế trong database.

Đầu tầng 1 có vị trí tài xế màu đỏ, ghi rõ không bán vé/không chọn. Đây là mốc định hướng của sơ đồ, không có mã ghế hành khách, không thêm vào database và không tính trong 22/29/40 chỗ. Tiêu đề kết quả tìm chuyến hiển thị tên tỉnh/bến từ API; mã CITY:/STATION: chỉ dùng trong tham số tìm kiếm.

Đã kiểm trực tiếp G01/P01/A01 chọn được trên cả ba loại xe, giá và số ghế chọn cập nhật. Lint phần sửa, TypeScript và build đạt.

## Phần giữ ghế và checkout

Giữ ghế dùng POST `/api/booking/hold` qua phiên đăng nhập/CSRF hiện có. Backend trả `maDon` dạng HOLD-… là mã phiên giữ ghế; hiện không tạo bản ghi DonDatVe. Checkout đang gọi `/api/giu-cho` và các API `/api/don-dat-ve/...` khác với hợp đồng này. Cần module đặt vé thống nhất hợp đồng đơn/phiên giữ trước khi nghiệm thu đặt vé và thanh toán; kết quả kiểm chọn ghế không chứng minh checkout đã hoàn thành.

## Tự kiểm trên web

1. Đăng nhập tài khoản khách tại `/dang-nhap`.
2. Tìm tuyến/ngày có chuyến, mở chi tiết, bấm **Chọn ghế và đặt vé**.
3. Chọn và bỏ chọn ghế trống; kiểm tra màu, số ghế và giá.
4. Đổi sang chuyến khác: lựa chọn của chuyến trước được xóa.
5. Kiểm tra ghế không khả dụng không bấm được; kiểm từng sơ đồ 22/29/40 chỗ.
