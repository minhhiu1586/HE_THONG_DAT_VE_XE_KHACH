# T13 — Tìm chuyến và tình trạng ghế khả dụng

## Nguồn dữ liệu

Trang chủ và `/tim-chuyen` lấy danh mục tỉnh/thành, bến xe, tuyến đang khai thác, chuyến mở bán, nhà xe, loại xe, giá và khuyến mãi từ Backend/API và database. Frontend không có dữ liệu mẫu hoặc dự phòng bằng dữ liệu giả. Khi API lỗi, giao diện hiện lỗi hoặc trạng thái rỗng; cần khởi động PostgreSQL và backend trước khi dùng các màn hình này.

## API đang dùng

| Method | Endpoint | Dữ liệu |
| --- | --- | --- |
| GET | `/api/tinh-thanh` | Tỉnh/thành từ database |
| GET | `/api/ben-xe?trangThai=HOAT_DONG` | Bến xe đang hoạt động |
| GET | `/api/tuyen-xe?trangThai=DANG_KHAI_THAC` | Tuyến đang khai thác |
| GET | `/api/chuyen-xe?from=…&to=…&date=YYYY-MM-DD` | Chuyến có thể đặt trong ngày được chọn |
| GET | `/api/chuyen-xe/{maChuyen}` | Chi tiết công khai, điểm dừng và số ghế mới nhất |
| GET | `/api/chuyen-xe/{maChuyen}/ghe` | Trạng thái ghế theo chuyến |
| GET | `/api/khuyen-mai` | Khuyến mãi còn thời hạn và còn lượt |

Query `from` và `to` nhận `CITY:{maTinhThanh}` hoặc `STATION:{maBenXe}`. Mã không có tiền tố vẫn được chấp nhận như trước để tương thích với client cũ. Tìm theo thành phố khớp thành phố của bến đầu/cuối tuyến; tìm theo bến khớp mã bến chính xác. Mã địa điểm không tồn tại hoặc hai điểm thuộc cùng tỉnh/thành trả 400; ngày không đúng ISO trả 400.

Kết quả chuyến là array hiện tại, không phân trang. Backend chỉ trả chuyến `DANG_MO_BAN` đủ điều kiện mở bán, thuộc tuyến/nhà xe còn hoạt động và chưa quá giờ nhận đặt. Chuyến hết chỗ vẫn được hiển thị với `coTheDatVe=false`, `trangThaiNhanDatVe=HET_CHO`. Mỗi phần tử có thông tin tuyến, bến, tỉnh/thành, nhà xe, loại xe, giờ đi/đến, thời lượng, giá tuyến, tổng ghế, số ghế còn trống và trạng thái nhận đặt. Số ghế trống được tính riêng theo chuyến từ ghế vật lý đang hoạt động, vé đã phát hành và lượt giữ ghế chưa hết hạn; không ghi trạng thái bán vào bản ghi ghế vật lý.

Chi tiết công khai trả cùng các trường, thêm địa chỉ hai bến và `diemDung` theo `thuTu`. Chuyến không có điểm dừng trả mảng rỗng. Chuyến tồn tại nhưng không còn nhận đặt vẫn xem được chi tiết, với `coTheDatVe=false` và `trangThaiNhanDatVe=NGUNG_NHAN_DAT`; mã chuyến không tồn tại trả 404. Frontend chỉ cho xem và chọn ghế khả dụng khi chuyến còn nhận đặt và còn chỗ. Việc chọn chỉ lưu trong giao diện, không giữ chỗ; module giữ ghế phải kiểm tra lại trong giao dịch ghi. Nút cập nhật trong trang chi tiết truy vấn lại số ghế và xóa lựa chọn cũ.

Ngày lọc là `ngayKhoiHanh` của chuyến. `gioDenDuKien` lấy từ chuyến, gồm cả ngày đến hôm sau nếu chạy qua đêm. Frontend không thay thế giờ chuyến bằng giờ mặc định của tuyến.

## Hành vi khi backend không sẵn sàng

Frontend không chuyển sang fixture, seed hay mảng tĩnh khi request lỗi. Trang tìm chuyến hiển thị thông báo lỗi, còn trang chủ hiển thị lỗi tải danh mục hoặc nội dung rỗng. Để chạy local, khởi động database/backend theo hướng dẫn dự án rồi mở frontend; seed chỉ cung cấp dữ liệu minh họa trong database phát triển, không được frontend tự tạo hoặc ngầm sử dụng.
