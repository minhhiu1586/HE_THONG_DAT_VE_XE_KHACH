# API khuyến mãi và phương thức thanh toán

T14 triển khai API quản trị khuyến mãi và danh mục phương thức thanh toán trên backend hiện có. Hợp đồng request/response, mã lỗi và ví dụ JSON nằm trong [OpenAPI](./payment-discount-openapi.json).

## Phạm vi API

| Tài nguyên | Endpoint | Chức năng |
| --- | --- | --- |
| Khuyến mãi | `/api/quan-tri/khuyen-mai` | GET tìm kiếm/lọc/phân trang; POST tạo. |
| Khuyến mãi theo mã | `/api/quan-tri/khuyen-mai/{maKhuyenMai}` | GET chi tiết; PUT cập nhật; DELETE xóa khi chưa được dùng/tham chiếu. |
| Phương thức thanh toán | `/api/quan-tri/phuong-thuc-thanh-toan` | GET danh mục; POST tạo. |
| Phương thức theo mã | `/api/quan-tri/phuong-thuc-thanh-toan/{maPhuongThuc}` | GET chi tiết; PUT cập nhật thông tin. |
| Trạng thái phương thức | `/api/quan-tri/phuong-thuc-thanh-toan/{maPhuongThuc}/trang-thai` | PUT đặt trạng thái. |
| Danh mục công khai | `/api/phuong-thuc-thanh-toan` | GET các phương thức đang `HOAT_DONG`. |

API quản trị yêu cầu ADMIN; yêu cầu ghi cần CSRF. Luồng backend là HTTP → Servlet → Service → Repository → PostgreSQL; service quản lý validation và transaction, DTO là hợp đồng JSON và entity không được serialize trực tiếp.

Các lớp chính trong `src/backend/src/main/java/com/datvexe/`:

| Lớp | Trách nhiệm |
| --- | --- |
| `controller/KhuyenMaiController.java` | Endpoint tìm kiếm và CRUD khuyến mãi. |
| `controller/PhuongThucThanhToanController.java` | Endpoint danh mục, trạng thái và danh sách công khai. |
| `service/KhuyenMaiService.java` | Kiểm tra dữ liệu/điều kiện, transaction và tính giảm giá. |
| `service/PhuongThucThanhToanService.java` | Quản lý danh mục và kiểm tra khả dụng. |
| `repository/KhuyenMaiRepository.java` | Truy vấn khuyến mãi. |
| `repository/PhuongThucThanhToanRepository.java` | Truy vấn phương thức thanh toán. |
| `dto/KhuyenMaiDto.java`, `dto/PhuongThucThanhToanDto.java` | Request và response API. |

## Quy tắc nghiệp vụ

- Mã khuyến mãi và mã phương thức thanh toán chỉ gồm chữ ASCII, số, `_`, `-`, dài từ 1 đến 40 ký tự. Khoảng trắng, kể cả đầu/cuối, bị từ chối; tên hiển thị vẫn hỗ trợ tiếng Việt.
- Khuyến mãi áp dụng từ `ngayBatDau` (bao gồm) đến trước `ngayKetThuc`, khi còn lượt và đơn đạt `donToiThieu`. Trạng thái truy vấn ưu tiên `HET_HAN`, sau đó `HET_LUOT`, còn lại `HOAT_DONG`; trạng thái lưu không được cập nhật bởi GET. Khuyến mãi chưa tới ngày bắt đầu vẫn thuộc nhóm `HOAT_DONG` nhưng chưa được áp dụng. Ngày lọc gồm cả hai đầu theo `Asia/Ho_Chi_Minh`, kết quả có thứ tự ổn định.
- Phân trang bắt đầu từ trang 1, `size` từ 1 đến 100; mặc định lần lượt là 1 và 20. Trang vượt cuối trả `items` rỗng nhưng vẫn giữ tổng số bản ghi.
- Tiền VND truyền dưới dạng chuỗi số nguyên không âm, tối đa 12 chữ số theo cột `numeric(14,2)`; `giaTriGiam` là số phần trăm lớn hơn 0 đến 100 (tối đa hai chữ số thập phân) hoặc chuỗi VND dương. Tính toán dùng `BigDecimal`; giới hạn bởi `giamToiDa` và tổng đơn. Việc làm tròn thuộc bước chốt giá T17. Trường `giamToiDa` trong API được ánh xạ sang `giaTriToiDa` ở entity.
- `ngayBatDau`/`ngayKetThuc` nhận RFC3339 có offset. Service chuẩn hóa thời điểm về `Asia/Ho_Chi_Minh` khi lưu và trả offset tương ứng.
- Sửa/xóa khuyến mãi đã dùng hoặc được đơn tham chiếu trả `409` để giữ lịch sử. Tạo mới khởi tạo lượt đã dùng bằng 0; API quản trị không điều chỉnh lượt.
- Phương thức mới ở trạng thái `HOAT_DONG`; cập nhật thông tin giữ nguyên mã và trạng thái, bỏ qua `moTa` trong PUT sẽ lưu `null`. API trạng thái nhận `HOAT_DONG`, `TAM_NGUNG`, `NGUNG_HOAT_DONG`; gửi lại trạng thái hiện tại vẫn thành công.

## Kế hoạch kiểm thử

| Nhóm | Nội dung và điều kiện đạt |
| --- | --- |
| Khởi động | Compose chạy PostgreSQL/backend; health và kết nối database đều UP. |
| Phân quyền | Khách ẩn danh nhận `401`, khách hàng nhận `403`; ADMIN thiếu CSRF nhận `403`; danh mục công khai đọc được. |
| CRUD và validation | Tạo/đọc/sửa/xóa đúng HTTP code; dữ liệu sai bị từ chối và lỗi cập nhật không làm đổi dữ liệu cũ. |
| Tìm kiếm/phân trang | Lọc mã, tên, ngày; ký tự `%`, `_`, `!` được coi là ký tự thường; trang vượt cuối vẫn giữ tổng. |
| Trạng thái khuyến mãi | Kiểm tra ưu tiên hết hạn/hết lượt và khuyến mãi chưa tới ngày bắt đầu. |
| Bảo vệ lịch sử | Khuyến mãi đã dùng hoặc được đơn tham chiếu trả `409`, dữ liệu được giữ nguyên. |
| Phương thức thanh toán | Kiểm tra CRUD, ba trạng thái, gọi lặp trạng thái và danh sách công khai; không hỗ trợ xóa. |
| Tranh chấp/HTTP | Mã trùng đồng thời chỉ tạo một bản ghi; đường dẫn, method, query và JSON sai trả lỗi có cấu trúc. |
| Hồi quy mã tài nguyên | Mã chứa khoảng trắng, Unicode, `/` hoặc vượt 40 ký tự bị từ chối; mã hợp lệ 40 ký tự tạo được và đọc lại qua Location. |
| Công thức giảm | Service kiểm tra khoảng hiệu lực, đơn tối thiểu, mức giảm tối đa và độ chính xác `BigDecimal`; 15% của `100001` cho `15000.15`, chưa làm tròn trước T17. |

## Chạy smoke test trên Docker

Từ thư mục gốc repository, build WAR mới và khởi động backend/PostgreSQL bằng Compose. Tạo lại container backend để nạp đúng WAR vừa build; dữ liệu PostgreSQL nằm trong volume hiện có.

```powershell
mvn -B -f src/backend/pom.xml clean verify
docker compose up -d --force-recreate backend
Invoke-RestMethod http://localhost:8080/api/health
python tests/backend/paymethod_discount_smoke.py
```

Nếu máy chưa cài Maven, thay lệnh build ở trên bằng:

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path)\src\backend,target=/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -B -ntp clean verify
```

Health cần trả `status=UP` và `database=UP` trước khi chạy smoke. Script dùng tài khoản và tiền tố dữ liệu riêng, đối chiếu HTTP/PostgreSQL rồi dọn fixture của lượt chạy trong `finally`; không reset volume. Chạy trên database phát triển. Đăng ký/đăng nhập giới hạn 20 lần/phút/IP, nên đợi hết một phút nếu gặp `429`. Có thể đặt `API_BASE_URL` và `DB_CONTAINER` để trỏ tới stack khác.

## Kết quả kiểm tra ngày 12/10/2026

| Bộ kiểm tra | Kết quả |
| --- | --- |
| Docker backend hiện hành | Đã kiểm tra Tomcat 10.1.60 / Java 21 với PostgreSQL 16.15. `/api/health` trả `200`, backend và database đều UP; kết nối JDBC và Hibernate validate schema thành công. |
| API smoke sau sửa lỗi | **99/99 phản hồi HTTP đạt**, 5 nhóm kịch bản; bao gồm hồi quy mã tài nguyên, đối chiếu SQL về lưu dữ liệu, rollback và bảo vệ lịch sử. Script hiện giữ lại là `tests/backend/paymethod_discount_smoke.py`. |
| Build sau đồng bộ develop | `clean verify` thành công trên nền `8bb4dec`: 64 đạt, 11 test PostgreSQL tùy chọn bỏ qua, 0 lỗi. Các JUnit này thuộc module khác; T14 chỉ giữ API smoke. Smoke 99/99 cũng được chạy lại trên WAR sau đồng bộ. |
| Tester khuyến mãi trước sửa | 29/29 nhóm kiểm tra đạt; đây là số nhóm, không phải số request. |
| Tester HTTP/bảo mật trước sửa | 52 request gồm chuẩn bị tài khoản và probe Origin; không xác nhận lỗi ứng dụng trong bộ này. |
| Tester PostgreSQL trước sửa | 9/9 tình huống đạt; kiểm tra trùng mã đồng thời, cập nhật/xóa, rollback và tham chiếu lịch sử. |
| Maven trước khi dọn test | Lượt `verify` ngày đó có 68 đạt, 9 bỏ qua, 0 lỗi; trong 68 có 16 test T14. Đây là số liệu lịch sử trước khi xóa hai file JUnit T14 theo đợt dọn test, không mô tả bộ test hiện còn. |
| Dọn fixture | Sau các lượt kiểm tra, dữ liệu thử thuộc các tester đã được xác nhận dọn sạch; health cuối lượt vẫn UP. |

Các con số trên thuộc các bộ kiểm tra và cách đếm riêng, không cộng thành một tổng. Bộ smoke ban đầu có 83 phản hồi đạt; sau bổ sung hồi quy mã tài nguyên đã tăng lên 99. Các lỗi bộ đếm/kỳ vọng của harness độc lập đã được kiểm tra lại, không tính là lỗi ứng dụng.

## Lỗi đã sửa: mã có khoảng trắng (P2)

Trước sửa, cả hai module chấp nhận tạo mã chứa khoảng trắng, trả `201` và `Location` đã percent-encode, nhưng GET theo Location trả `400 VALIDATION_ERROR` do lớp bảo vệ URI từ chối `%`.

Đã thêm validation mã tại hai service và pattern `^[A-Za-z0-9_-]{1,40}$` vào OpenAPI cho request, response và tham số đường dẫn. Mã sai trả `400` với fieldErrors tương ứng; không tự cắt khoảng trắng khỏi mã. Tên hiển thị vẫn có thể chứa tiếng Việt và khoảng trắng.

Đã xác minh trên WAR mới: cả hai module từ chối mã có khoảng trắng đầu/cuối hoặc ở giữa; mã hợp lệ 40 ký tự trả `201` và đọc lại qua Location trả `200`. OpenAPI và 21 ví dụ hợp lệ. Không còn lỗi P2 được xác nhận đang mở trong phạm vi lượt kiểm tra này.

## Giới hạn

Đây là kiểm thử chức năng và một số tình huống đồng thời, không phải kiểm thử tải. Frontend T14 chưa tích hợp, đang chờ phối hợp. T14 chưa tích hợp giữ/xác nhận/giải phóng lượt, tranh chấp lượt hoặc ghế, quy tắc làm tròn tại bước chốt giá, cổng thanh toán sandbox hay callback; các nội dung tích hợp thuộc T17/T18.
