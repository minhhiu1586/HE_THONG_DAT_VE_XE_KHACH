# Rà soát giao diện xanh dương

## Phạm vi

Áp dụng bảng màu xanh dương và font hệ thống Segoe UI cho các trang hiện có: trang chủ, tìm chuyến, tài khoản, đặt vé, quản trị và nhân viên. Giữ nguyên endpoint, payload, xác thực cookie, CSRF và phân quyền Backend.

- Primary: #2474E5; hover: #1D63C7; dark: #154B96.
- Nội dung: #172B4D; mô tả: #475569; nền: #F5F7FB.
- Bổ sung biến màu mà các CSS module cũ đang tham chiếu.
- Header co giãn; tên tài khoản xuống dòng; menu giới hạn theo viewport.
- Grid dùng minmax(0, 1fr); bảng rộng cuộn trong khung; form và nút không cắt chữ.
- Menu quản trị đóng khi đổi đường dẫn, không cập nhật state đồng bộ trong effect.

## Kiểm tra đã chạy

- TypeScript: tsc --noEmit đạt.
- ESLint: 0 lỗi; 2 cảnh báo img có sẵn trong PopularRoutes và BusStations.
- git diff --check: đạt.
- Desktop 1280px: trang chủ, bảng xe tải dữ liệu Backend (76 dòng tại thời điểm kiểm tra).
- Mobile 390px: đăng nhập Admin, hồ sơ; tài khoản, nhà xe, xe, tuyến, chuyến, nhân viên; không tràn ngang sau khi tải nội dung.
- Mobile 320px: trang chủ, tìm chuyến, đăng ký, hồ sơ, quản lý xe; menu quản trị mở được; không tràn ngang.
- Đăng nhập tài khoản nhân viên tx1.futa@datvexe.test thành công; trang lịch chuyến và Header vừa chiều rộng 390px.
- Chưa nghiệm thu các luồng ghi dữ liệu, thanh toán hoặc giữ ghế: thay đổi này tập trung giao diện, không bổ sung Backend cho các nghiệp vụ chưa triển khai.

## Tự kiểm tra

1. Chạy Backend và npm.cmd run dev trong src/frontend.
2. Mở /home, /trips, /dang-nhap, /dang-ky; kiểm tra nhãn, nút, lỗi và các card.
3. Đăng nhập Admin; mở từng mục quản trị, mở form sửa/chi tiết, cuộn ngang bảng trong khung trên điện thoại.
4. Mở menu tài khoản và menu quản trị; đổi trang; kiểm tra tên dài hiển thị đầy đủ.
5. Đăng nhập nhân viên; mở /nhan-vien; kiểm tra lịch chuyến và Header.
6. Dùng DevTools ở 320px, 390px, 768px và desktop; kiểm tra khi zoom 200%, nhập nội dung dài và dùng phím Tab.

Các thay đổi chưa được commit hoặc push. Những file đang sửa từ trước phiên rà soát được giữ lại.
