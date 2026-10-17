# Đối chiếu nghiệm vụ và kiểm thử T22

Lần rà soát cuối: 14/10/2026, nhánh `feature/t22-trip-reviews-moderation`.

## Kết quả

- Biên dịch toàn bộ nguồn chính và kiểm thử bằng Java 21: đạt.
- 111 ca trong bộ T22 H2/PostgreSQL, TripServiceTest và ThanhToanServiceTest: đạt, 0 thất bại, 0 bỏ qua. Trong đó T22 có 36 ca trên mỗi database.
- Bộ hồi quy không thuộc nhóm PostgreSQL ở lần triển khai trước: 199 ca đạt. Các ca trùng giữa hai bộ không được cộng hai lần.
- Kiểm thử trình duyệt với backend thật và PostgreSQL riêng: đạt.
- Frontend lint và TypeScript: đạt; còn hai cảnh báo `<img>` đã có ở màn trang chủ.
- Kiểm tra Docker Compose và diff: đạt. Database đang phục vụ ứng dụng không được dùng làm database kiểm thử.

## Đối chiếu yêu cầu

| Nhóm yêu cầu | Cách thực hiện và bằng chứng |
| --- | --- |
| Đúng chủ tài khoản, có vé đã thanh toán | Truy vấn nối vé → đơn → người đặt và kiểm tra giao dịch đã thu; từ chối khách không có vé, tài khoản chưa đăng nhập, đơn nháp/chưa thanh toán hoặc chỉ có dữ liệu giữ chỗ |
| Đúng trạng thái chuyến | Chỉ `HOAN_TAT`; kiểm thử tất cả trạng thái còn lại, cùng luồng hoàn tất T21 → đánh giá T22 |
| Vé hoàn/hủy không cấp quyền | Loại `DA_HOAN`, `DA_HUY`; ít nhất một vé hợp lệ vẫn cho đánh giá khi có vé khác đã hoàn |
| Một tài khoản/chuyến | Kiểm tra tại service, khóa chuyến, unique constraint hiện có; nhiều vé, nhiều đơn và hai request đồng thời đều chỉ tạo một bản ghi |
| Sao và nhận xét | Sao nguyên 1–5; từ chối null, 0, 6, số thập phân, chuỗi số hoặc boolean; tối đa 2000 ký tự; kiểm tra giới hạn, ký tự lưu trữ lỗi và nội dung script |
| Sửa đúng quyền, không chuyển chủ/chuyến | Chỉ chủ đánh giá; DTO sửa chỉ nhận sao và nhận xét; kiểm thử từ chối các trường chủ, chuyến và trạng thái tự gửi |
| Sửa đã duyệt → chờ duyệt | Bỏ khỏi rating ngay khi điểm/nội dung thay đổi; lưu không thay đổi giữ trạng thái đã duyệt, frontend không báo sai trạng thái |
| Kiểm duyệt theo RBAC | Admin hoặc nhà xe còn hoạt động trong đúng phạm vi; kiểm thử nhà xe đúng phạm vi và khác phạm vi; khách/nhân viên không được duyệt |
| Duyệt/ẩn lặp | Không ghi thêm lịch sử hoặc thay đổi rating lần hai; lý do ẩn bắt buộc |
| Xóa mềm | Giữ dữ liệu và cặp tài khoản/chuyến; không hiển thị/tính rating, không tạo lại, không sửa/duyệt bản ghi đã xóa; chủ khác không được xóa |
| Rating chính xác | AVG chỉ từ `DA_DUYET` và chưa xóa; kiểm thử 5 và 4 = 4,5; ẩn 5 còn 4,0; không có đánh giá → null |
| Công khai và bảo mật | Chỉ hiện đã duyệt; không trả mã người dùng, email, điện thoại hoặc lịch sử kiểm duyệt; React hiển thị văn bản và kiểm thử browser xác nhận script không chạy |
| Transaction và lịch sử | Mọi thay đổi cùng audit trong transaction; lỗi ghi audit rollback trạng thái, rating vẫn đúng; lưu người thao tác, thời điểm và nội dung cũ/mới |
| Giao diện đồng bộ | Dùng stylesheet gốc; kiểm thử tạo/sửa/duyệt/ẩn/xóa thật, rating, không hiện nút tạo lần hai và không tràn ngang tại 390px |

## Những điểm đã sửa trong lần rà cuối

1. Chặn NUL, ký tự điều khiển không hợp lệ và Unicode lỗi trước khi lưu PostgreSQL.
2. Trả `coTheSua` từ backend và chỉ hiện nút sửa khi còn đủ điều kiện vé.
3. Thông báo lưu dựa trên trạng thái backend; không báo chuyển chờ duyệt khi nội dung không thay đổi.

Kiểm thử trình duyệt được bổ sung: lưu đánh giá đã duyệt không đổi nội dung vẫn giữ rating; vé chuyển sang đã hoàn thì không còn nút sửa nhưng chủ đánh giá vẫn được xóa mềm.

## Phạm vi nghiệm thu

Thực hiện theo phương án đề xuất trong đặc tả: nhận xét không bắt buộc, sao 1–5, không bắt buộc có ghi nhận lên xe, Admin/nhà xe kiểm duyệt theo phạm vi, không cho tạo lại sau xóa. Các lựa chọn này được ghi trong [hợp đồng T22](t22-danh-gia-chuyen-di.md) để nhóm đối chiếu T01/T05.

Không coi kết quả tự kiểm thử là việc thành viên review đã chấp nhận PR; chưa thực hiện gửi PR hoặc yêu cầu review thay người dùng. Chưa có kết quả `mvn clean verify` trong môi trường cloud này; việc xác minh Java dùng trình biên dịch và JUnit trực tiếp, kèm khởi động Tomcat thực tế.
