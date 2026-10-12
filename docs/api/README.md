# Thiết kế hợp đồng API

**Dự án:** Hệ thống đặt vé xe khách trực tuyến.

**Phiên bản:** 0.1.0-draft — ngày 11/10/2026.

**Trạng thái:** Bản báo cáo để nhóm xem xét và thống nhất trước khi triển khai.

Mục tiêu: thống nhất cách frontend gửi yêu cầu, backend trả dữ liệu và xử lý lỗi. Phạm vi gồm 29 API thuộc nhóm A–F; không bổ sung chức năng quản trị khuyến mãi hoặc báo cáo kinh doanh. Tài liệu mô tả thiết kế mục tiêu, chưa xác nhận các API đã hoạt động.

Bộ tài liệu gồm:

- **[t10-vehicle-management.md](t10-vehicle-management.md)**: quản lý loại xe, xe, đăng kiểm, ghế vật lý và helper điều kiện khai thác T10.

- **[account-auth.md](account-auth.md)** và **[account-openapi.yaml](account-openapi.yaml)**: hợp đồng tài khoản/xác thực đã triển khai trong T07.
- **[T07.1-integration.md](../tasks/T07.1-integration.md)**: guard, helper và phạm vi quyền dùng chung.
- **[t08-route-management.md](t08-route-management.md)**: API quản lý tỉnh/thành, bến xe, tuyến và điểm dừng thuộc T08.
- **[t09-operator-management.md](t09-operator-management.md)**: API quản lý nhà xe, trạng thái và tài khoản quản lý thuộc T09.
- **[payment-discount.md](payment-discount.md)** và **[payment-discount-openapi.json](payment-discount-openapi.json)**: API khuyến mãi, phương thức thanh toán và báo cáo kiểm thử T14.
- **README.md**: quy ước chung, cách xử lý lỗi và các điểm cần thống nhất.
- **[api-contract.md](api-contract.md)**: danh sách API, request/response và dữ liệu mẫu ngay trong từng nhóm.
- **[openapi.yaml](openapi.yaml)**: kiểu dữ liệu, trường bắt buộc, giá trị null và ví dụ chi tiết để xem bằng công cụ hỗ trợ OpenAPI/Swagger. https://editor.swagger.io/

## 1. Danh sách API

| Nhóm | Phạm vi | Số API |
| --- | --- | --- |
| A | Tìm chuyến, chi tiết, ghế và giá tham khảo | 6 |
| B | Giữ chỗ và đơn đặt vé | 7 |
| C | Báo giá, chốt giá và phương thức thanh toán | 3 |
| D | Thanh toán và vé điện tử | 5 |
| E | Hủy vé và hoàn tiền | 5 |
| F | Xác minh vé và xác nhận lên xe | 3 |

Luồng chính: tìm chuyến → chọn ghế → giữ ghế và tạo đơn nháp → cập nhật liên hệ/hành khách → báo giá → chốt giá → thanh toán → xem vé.

## 2. Cấu trúc Request & Response

- API ứng dụng dùng JSON UTF-8, tên trường tiếng Việt `camelCase`, ví dụ `maChuyen`, `tongThanhToan`.
- Thành công trả object hoặc array trực tiếp; danh sách phân trang dùng cấu trúc tại mục 4. Không thêm lớp bọc `data`.
- Request dùng DTO riêng. Không nhận trực tiếp entity hoặc những trường do backend quyết định như chủ đơn, số tiền và hạn giữ.
- Trường bắt buộc, tùy chọn và nullable được ghi trong contract. Thiếu trường khác với `null`. Với PATCH, trường không gửi giữ nguyên; `null` chỉ xóa trường được phép nullable. PUT hành khách thay toàn bộ danh sách.
- Backend phải kiểm tra cả vai trò và quyền trên đơn/vé/chuyến; không chỉ kiểm tra đã đăng nhập.

Trong bản nháp, tên và số điện thoại người đặt trong `OrderResponse` vẫn là chuỗi bắt buộc, không nhận null. Cách khởi tạo hai giá trị này khi B01 tạo đơn còn chờ nhóm xác nhận: lấy thông tin hợp lệ từ hồ sơ hoặc điều chỉnh mô hình đơn nháp để cho phép thiếu. Ví dụ giả định đơn đã có liên hệ hợp lệ; đây là điểm phải thống nhất trước khi triển khai B01, không phải yêu cầu tự điền dữ liệu giả.

## 3. Chuẩn hóa dữ liệu

| Dữ liệu | Quy ước đề xuất |
| --- | --- |
| ID | Chuỗi, ví dụ `"DDV01"`; phân biệt mã đơn, vé, giao dịch nội bộ và tham chiếu cổng. Không suy đoán nghiệp vụ từ ID. |
| Tiền | Chuỗi số nguyên VND không âm, ví dụ `"250000"`; backend tính bằng `BigDecimal`, không dùng `float`/`double`. |
| Làm tròn | Đề xuất `HALF_UP` đến 1 VND khi chốt; phân bổ phần dư ổn định để tổng từng vé bằng tổng đơn. Cần nhóm duyệt. |
| Ngày / giờ lịch | `YYYY-MM-DD` / `HH:mm:ss`. |
| Thời điểm | RFC3339 có offset, ví dụ `2026-10-11T09:30:00+07:00`; múi giờ nghiệp vụ `Asia/Ho_Chi_Minh`. |
| Trạng thái | Theo quy tắc được nhóm thống nhất. Giá trị trong ví dụ chưa thay thế bảng chuyển trạng thái chính thức. |

Trạng thái vật lý của ghế khác khả dụng theo chuyến. `DANG_CHON` chỉ thuộc giao diện, chưa tạo quyền giữ ghế.

## 4. Phân trang, lọc và sắp xếp

Đề xuất `page` bắt đầu từ 1, mặc định 1; `size` mặc định 20, từ 1 đến 100. Query sai trả 400. Tỉnh/thành, bến xe và phương thức thanh toán trả array; các danh sách còn lại được chỉ rõ trong contract.

```json
{
  "items": [],
  "page": 3,
  "size": 20,
  "totalItems": 21,
  "totalPages": 2
}
```

`totalItems` tính toàn bộ kết quả sau lọc; `totalPages = ceil(totalItems / size)`. Trang vượt phạm vi trả mảng rỗng và giữ nguyên tổng; chỉ khi không có kết quả nào thì hai tổng bằng 0.

Chỉ nhận bộ lọc và trường sort ghi tại từng API. Cú pháp `sort=field,asc|desc`, thêm ID làm khóa phụ ổn định; không nhận tên cột tùy ý. Bộ lọc `tuNgay`–`denNgay` của đơn tính theo ngày tạo, bao gồm cả hai ngày.

## 5. Mã HTTP và lỗi nghiệp vụ

| HTTP | Khi dùng / mã lỗi |
| --- | --- |
| 200 | Đọc, cập nhật, báo giá thành công; tra cứu giao dịch đang chờ cũng trả 200. |
| 201 | Tạo được tài nguyên; trả `Location` nếu có đường dẫn tra cứu. |
| 202 | Đã nhận yêu cầu bất đồng bộ, chưa hoàn tất; trả ID để tra cứu. |
| 400 | `VALIDATION_ERROR`: đầu vào thiếu hoặc sai. |
| 401 / 403 | `UNAUTHENTICATED` / `FORBIDDEN`. |
| 404 | `RESOURCE_NOT_FOUND`. |
| 409 | Xung đột ghế, hết hạn giữ, sai trạng thái, báo giá thay đổi hoặc yêu cầu lặp không hợp lệ. Mã cụ thể ghi tại từng API. |
| 500 | `INTERNAL_ERROR`: lỗi hệ thống. |
| 502 / 503 | `PAYMENT_PROVIDER_ERROR` / `SERVICE_UNAVAILABLE`. |

Timeout hoặc 5xx không đủ để kết luận thanh toán thất bại. Nếu đã cấp mã giao dịch, giữ mã đó để tra cứu trước khi tạo lần khác.

## 6. Phản hồi lỗi thống nhất

```json
{
  "code": "SEAT_UNAVAILABLE",
  "message": "Một hoặc nhiều ghế không còn khả dụng.",
  "details": {"maGheKhongKhaDung": ["G12"]},
  "timestamp": "2026-10-11T09:30:00+07:00",
  "traceId": "req-example-001"
}
```

`code`, `message`, `timestamp`, `traceId` là chuỗi; `details` là object, có thể rỗng. Lỗi validation dùng `details.fieldErrors`, ví dụ `[{"field":"maChuyen","code":"REQUIRED","message":"Vui lòng chọn chuyến."}]`. Không trả stack trace, SQL hoặc thông tin người đang giữ ghế.

Header chung: `Content-Type: application/json`; các API liệt kê tại mục 8 cần `Idempotency-Key`; server trả `X-Trace-Id` để tra log. Cấu hình CORS theo frontend thực tế. T07 dùng phiên cookie `datvexe_session` và CSRF theo [account-auth.md](account-auth.md).

## 7. Backend quyết định dữ liệu nghiệp vụ

- Giữ nhiều ghế và tạo **một đơn nháp** phải thành công hoặc thất bại cùng nhau. B03/B04 cập nhật đơn đó, không tạo đơn mới.
- Hạn giữ là **15 phút do backend tính**. Tải lại, gửi lại request, báo giá hoặc khởi tạo thanh toán không gia hạn. Hết hiệu lực khi thời gian server đạt hạn giữ.
- Giá, giảm giá, tổng thu/hoàn, quyền sở hữu và trạng thái được kiểm tra ở backend; frontend gửi mã và ý định thao tác.
- C01 xem thử giá; C02 lưu giá đã chốt. Đề xuất chỉ C02 giữ lượt khuyến mãi, thất bại phải rollback cùng snapshot. Hết hạn/hủy trả lượt đúng một lần.
- B05 trả `giaDaChot: null` trước khi chốt, snapshot có `phienBanGia` sau khi chốt. D01 dùng phiên bản này và số tiền từ backend; GET đơn không tự chốt lại.
- Tổng tiền phân bổ theo vé phải khớp đơn. Hoàn một vé không làm mất hiệu lực các vé khác.

## 8. Chống xử lý lặp

Các API B01, B03, B07, C02, D01, E02, E03 và F02 dùng `Idempotency-Key`: cùng người gọi, method, path, key và nội dung thì trả lại kết quả; cùng key nhưng đổi nội dung trả `409 IDEMPOTENCY_KEY_REUSED`. Nếu yêu cầu cũ còn xử lý, trả `409 REQUEST_IN_PROGRESS`. Đây là lỗi chung của các API trên, ngoài các lỗi nghiệp vụ được ghi tại từng endpoint.

Khóa này không thay cho transaction hoặc ràng buộc chống đặt trùng ghế trong DB. Callback trùng được nhận diện theo mã giao dịch/thông báo đã xác minh của cổng, tránh thu/hoàn hoặc phát hành vé hai lần. Thời gian lưu khóa cần thống nhất trước triển khai; đề xuất hiện tại là tối thiểu 24 giờ.

## 9. Xác minh callback thanh toán

Backend kiểm tra chữ ký, tham chiếu giao dịch, số tiền/tiền tệ, kết quả cổng, trạng thái đơn và hạn giữ. Có URL thanh toán hoặc trình duyệt quay về trang thành công chưa chứng minh đã thu tiền hay được phát hành vé.

D04 chờ chọn cổng sandbox, vì vậy chưa chốt method, payload, chữ ký hoặc ACK. Callback muộn vẫn phải ghi nhận kết quả tài chính. Phương án không phát hành vé, chuyển tra soát/hoàn tiền là **đề xuất chưa duyệt**; không tự tái giữ ghế.

## 10. Dữ liệu mẫu và những điểm cần thống nhất

Các JSON trong [api-contract.md](api-contract.md) là dữ liệu giả để frontend tham khảo, chưa phải mock server có trạng thái. Dùng mẫu lỗi chung tại mục 6 và lỗi nghiệp vụ tại từng nhóm, tránh lặp cùng một JSON cho mọi API.

Các điểm cần nhóm xác nhận:

| Nội dung | Điểm chưa thống nhất |
| --- | --- |
| Trạng thái | Bảng chuyển trạng thái đơn, giữ, vé, thanh toán và hoàn; cách biểu diễn đơn hết hạn. |
| Đăng nhập và quyền | T07 đã triển khai phiên cookie và helper phạm vi; nhóm đối chiếu T01, xác nhận quyền nghiệp vụ và việc hỗ trợ khách không tài khoản. |
| Đơn nháp | Cách khởi tạo tên/số điện thoại người đặt tại B01, như mục 2. |
| Giá và khuyến mãi | Quy tắc làm tròn, giữ/trả lượt mã; điều kiện sửa đơn hoặc chốt lại giá. |
| Chuyến chưa gán xe | Đề xuất chi tiết cho phép null, từ chối giữ ghế; chưa có xe thì từ chối trả sơ đồ ghế. |
| Thanh toán và hoàn | Cổng sandbox, callback muộn, chính sách/phí hoàn và thời điểm bán lại ghế. |

Các điểm này cần nhóm thống nhất trước triển khai phần liên quan; không tự bổ sung enum hay thay sơ đồ lớp theo ví dụ.

Khi triển khai backend, cần bổ sung cách chống tranh ghế tại DB và nơi lưu hành khách của đơn nháp, snapshot giá theo ghế, yêu cầu hoàn, thời điểm kiểm vé. Tổng các lần hoàn không được vượt số tiền thực thu của vé; đổi khóa chống lặp không được bỏ qua giới hạn này. Đây là công việc thiết kế/triển khai tiếp theo, chưa được thực hiện trong T05.

## 11. Thay đổi hợp đồng

Ghi lý do → trao đổi với frontend/backend liên quan → cập nhật contract, OpenAPI và ví dụ tương ứng → review PR trước tích hợp. Khi đổi endpoint, tên trường, kiểu dữ liệu hoặc trạng thái phải thông báo các bên sử dụng.

Kết quả bàn giao là danh sách API, cấu trúc dữ liệu, quy tắc xử lý lỗi và ví dụ trong ba file trên. Không kèm mock server, bộ kiểm thử hoặc mã triển khai nghiệp vụ.

Backend hiện có còn trả tiền dạng JSON number, một số danh sách dạng array và lỗi `{"error":"..."}`. Việc áp dụng hợp đồng cần được phối hợp với frontend/backend khi triển khai; báo cáo này chưa thay đổi ứng dụng.

- [T12 — Quản lý chuyến và mở bán](t12-trip-management.md).
