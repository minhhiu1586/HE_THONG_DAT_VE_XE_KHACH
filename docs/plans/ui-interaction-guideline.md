# Quy tắc tương tác cho các chức năng quản lý

Áp dụng cho mọi màn hình quản lý hiện tại và chức năng mới.

- Nút phải có trạng thái hover, nhấn và focus bằng bàn phím rõ ràng; nút disabled giữ nguyên trạng thái không tương tác.
- Khi chọn **Sửa**, đưa biểu mẫu của bản ghi vào vùng nhìn thấy và focus trường đầu tiên có thể nhập.
- Khi chọn **Chi tiết**, đưa phần chi tiết tương ứng vào vùng nhìn thấy và focus tiêu đề phần đó.
- Biểu mẫu có thể đặt ở đầu trang, cạnh danh sách hoặc phía dưới tùy bố cục. Không yêu cầu người dùng tự cuộn tìm phần vừa mở.
- Dùng `useManagementFocus` từ `src/frontend/lib/use-management-focus.ts` cho các đích trong màn hình quản lý. Hook cuộn tới đích và tôn trọng tùy chọn giảm chuyển động của thiết bị.
- Nút xóa cần xác nhận trước thao tác và thông báo kết quả. Vì xóa không mở biểu mẫu, không chuyển người dùng tới một form không liên quan.
- Sau khi thêm/sửa mà giao diện mở chi tiết hoặc kết quả mới, đưa nội dung vừa mở vào vùng nhìn thấy.
- Không tự cuộn khi người dùng chỉ tải lại dữ liệu tại chỗ hoặc đang thao tác bên trong phần chi tiết hiện có.

Kiểm tra hành vi ở màn hình rộng, màn hình hẹp, bàn phím và thiết bị bật giảm chuyển động.
