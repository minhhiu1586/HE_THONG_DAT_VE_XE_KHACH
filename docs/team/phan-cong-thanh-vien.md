# Phân công sau tái cấu trúc

- **Nguyễn Minh Hiếu** (`members/NguyenMinhHieu`): Tài khoản, xác thực, phân quyền, nhà xe; bảo trì đánh giá hiện có theo T22.
- **Lê Nguyễn Thanh Hiền** (`members/LeNguyenThanhHien`): Tỉnh/thành, bến, tuyến, phương tiện, ghế, tìm kiếm backend và vòng đời chuyến.
- **Trần Đặng Thị Kim Cương** (`members/TranDangThiKimCuong`): Hồ sơ nhân viên, điều kiện phân công, lịch cá nhân.
- **Nguyễn Thế Ân** (`members/NguyenTheAn`): Giao diện tìm/chọn chuyến, giữ chỗ, đơn, người đi, lịch sử, hiển thị vé và yêu cầu hoàn.
- **Nguyễn Gia Bảo** (`members/NguyenGiaBao`): Giá, khuyến mãi, thanh toán, phát hành vé, QR, email và hoàn tiền.

## Ranh giới và phần phối hợp

- VehicleService/VehicleInputs tại Thanh Hiền chỉ còn quản lý phương tiện. API `/api/phan-cong`, AssignmentInputs và AssignmentService đã chuyển về Kim Cương; Hiền gọi AssignmentRules khi kiểm tra lịch/nhân sự và điều kiện vận hành. API HTTP và dữ liệu giữ nguyên; không tạo bản sao xử lý trong shared.
- BookingService/BookingDto: đặt tại Thế Ân; có phần báo/chốt giá và promotion phối hợp Gia Bảo. PriceCalculator/PricingDto và thanh toán/hoàn đặt tại Gia Bảo.
- RouteTripService và API tìm kiếm ở Thanh Hiền; frontend tìm chuyến ở Thế Ân.
- Hiển thị vé/yêu cầu hoàn ở Thế Ân; phát hành vé và nghiệp vụ tiền/hoàn ở Gia Bảo.
- Review hiện có đặt tại Hiếu theo T22; không xóa vì bảng mới chưa nhắc tới.
- Không triển khai thêm cấp tài khoản nhân viên, sửa payment context, thêm quota hay sửa các lỗi đã rà soát: đây chỉ là tái cấu trúc.

Folder thể hiện phạm vi bảo trì theo phân công mới, không thay thế tác giả/lịch sử commit thực tế. Không tự cập nhật trạng thái task Plane trong lần này.

<a id="kiem-ke-source"></a>

## Kiểm kê source

- [LeNguyenThanhHien](../../members/LeNguyenThanhHien/README.md#source)
- [NguyenGiaBao](../../members/NguyenGiaBao/README.md#source)
- [NguyenMinhHieu](../../members/NguyenMinhHieu/README.md#source)
- [NguyenTheAn](../../members/NguyenTheAn/README.md#source)
- [TranDangThiKimCuong](../../members/TranDangThiKimCuong/README.md#source)
