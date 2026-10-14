# T19 — Chính sách hoàn vé và hoàn tiền

## Phạm vi

Backend cho khách yêu cầu hoàn một, nhiều hoặc toàn bộ vé đã phát hành. Chưa làm FE, email hoàn tiền, hoàn do nhà xe hủy chuyến hoặc tiền thu chưa phát hành vé. E02 hủy vé trực tiếp chưa triển khai.

Giữ nguyên class diagram và enum nghiệp vụ. Dùng chung `PriceCalculator`, không tạo `RefundCalculator` hoặc entity JPA `HoanTien`. Hai bảng kỹ thuật `payment_gateway_metadata` và `refund_request` lưu dữ liệu đối chiếu cổng, yêu cầu hoàn, chi tiết từng vé và lịch sử xử lý.

## API

[OpenAPI T19](ticket-refund-openapi.json) dùng để xem trực quan; [openapi.yaml](openapi.yaml) là hợp đồng chung. API dùng cookie `datvexe_session`. Thao tác ghi cần `X-CSRF-Token`; tạo yêu cầu và thử lại cần `Idempotency-Key`.

| Method | Đường dẫn | Quyền | Kết quả |
| --- | --- | --- | --- |
| GET / POST | `/api/quan-tri/chinh-sach-hoan-ve` | ADMIN | Danh sách phân trang / tạo (`201`) |
| GET / PUT | `/api/quan-tri/chinh-sach-hoan-ve/{ma}` | ADMIN | Chi tiết / sửa chính sách chưa dùng |
| PUT | `/api/quan-tri/chinh-sach-hoan-ve/{ma}/trang-thai` | ADMIN | Áp dụng / ngừng áp dụng |
| GET | `/api/ve-xe/{maVe}/chinh-sach-hoan` | Khách sở hữu vé | Điều kiện và tiền hoàn dự kiến |
| POST | `/api/booking/{maDon}/hoan-tien/du-kien` | Khách sở hữu đơn | Dự kiến nhiều vé |
| POST | `/api/booking/{maDon}/hoan-tien` | Khách sở hữu đơn | Tiếp nhận hoàn nhiều vé (`202`) |
| POST | `/api/ve-xe/{maVe}/hoan-tien` | Khách sở hữu vé | Tiếp nhận hoàn một vé (`202`) |
| GET | `/api/hoan-tien/{maHoanTien}` | Khách sở hữu, ADMIN, nhà xe quản lý chuyến | Trạng thái hiện tại |
| GET | `/api/ve-xe/{maVe}/hoan-tien` | Khách sở hữu, ADMIN, nhà xe quản lý chuyến | Lịch sử phân trang |
| POST | `/api/hoan-tien/{maHoanTien}/xac-minh` | Khách sở hữu, ADMIN, nhà xe quản lý chuyến | Xác minh kết quả cổng |
| POST | `/api/hoan-tien/{maHoanTien}/thu-lai` | ADMIN, nhà xe quản lý chuyến | Thử lại thất bại đã xác định (`202`) |

Các thao tác còn lại thành công trả `200`. Request nhiều vé: `{"danhSachMaVe":["VE01","VE02"],"lyDo":"Thay đổi kế hoạch"}`. Request một vé nhận `{}` hoặc `{"lyDo":"..."}`; xác minh và thử lại nhận `{}`. Gửi toàn bộ mã vé để hoàn cả đơn. Một vé không hợp lệ thì từ chối cả yêu cầu. Không nhận tiền từ FE.

Response có mã hoàn, đơn/giao dịch gốc, chính sách, tổng hoàn, `chiTiet` từng vé, trạng thái, thời gian, mã cổng và `lichSu`. Tiền VND dùng chuỗi số nguyên. `tyLeHoan` trong chính sách là số JSON từ 0–100, tối đa hai chữ số thập phân.

## Chính sách và cách tính

Một chính sách đang áp dụng cho đơn mới. Demo `CS_DEMO_24H_80`: trước ít nhất 24 giờ, hoàn 80%, phí 0, cho phép vé khuyến mãi. Migration chỉ thêm demo nếu chưa có chính sách đang áp dụng; không ghi đè cấu hình hiện có.

T17 đưa chính sách vào báo giá và lưu tham chiếu khi chốt. Đổi chính sách giữa hai bước trả `PRICE_CHANGED`. Chính sách đã được đơn tham chiếu không sửa điều kiện; thay điều kiện bằng mã mới. Đơn cũ thiếu chính sách không gán hồi tố.

Tính riêng từng vé trên `VeXe.giaVe`:

```text
min(tiền thực trả, max(0, HALF_UP(tiền thực trả × tỷ lệ / 100 − phí)))
```

Làm tròn đến 1 VND, không thay đổi giá/lịch sử vé. Đúng mốc 24 giờ vẫn được hoàn. Không hoàn lại lượt khuyến mãi.

## Luồng xử lý và tính nhất quán

1. Khóa nhà xe → chuyến → đơn → thanh toán → yêu cầu hoàn; kiểm tra quyền, chính sách, thời hạn, trạng thái vé và hạn mức đã thu.
2. Lưu yêu cầu, khóa hoàn và kết quả tiếp nhận theo key trong cùng transaction, rồi commit.
3. Lưu dấu bắt đầu gửi; gọi VNPay ngoài transaction DB.
4. Lưu bằng chứng phản hồi đã xác minh, sau đó cập nhật vé và thanh toán nguyên tử trong transaction mới.

Mỗi giao dịch thu chỉ có một yêu cầu đang chờ/cần đối soát. Cùng key/nội dung trả đúng snapshot tiếp nhận ban đầu `202 / CHO_XU_LY`, không gửi tiền lần nữa; khác nội dung trả `IDEMPOTENCY_KEY_REUSED`. Dùng GET để lấy trạng thái mới nhất, kể cả khoản 0 đồng đã hoàn nội bộ ngay.

| Trạng thái kỹ thuật | Ý nghĩa |
| --- | --- |
| `CHO_XU_LY` | Đã tiếp nhận, giữ khóa hoàn |
| `THANH_CONG` | Đã xác minh hoàn; vé được chọn chuyển `DA_HOAN` |
| `THAT_BAI` | Đã xác định thất bại; vé vẫn có hiệu lực, được thử lại có kiểm tra |
| `CAN_DOI_SOAT` | Chưa chắc chắn; giữ khóa/hạn mức, không tự gửi lại |

Trong lúc chờ/cần đối soát, vé vẫn `DA_XUAT_VE` nhưng response có `dangHoan: true`, QR là `null`. Vé đã hoàn cũng không trả QR. Guard `AccessControl.ticketNotRefunding` được chuẩn bị cho module kiểm vé gọi dưới khóa; chưa có luồng kiểm vé hoàn chỉnh để nghiệm thu tích hợp phần này.

Đơn giữ `DA_THANH_TOAN`. Thanh toán gốc chuyển theo số tiền thực hoàn: hoàn hết vé ở mức 80% vẫn là `HOAN_TIEN_MOT_PHAN`. Sau hoàn thành công, vé mới ngừng chiếm ghế; đặt lại phải qua điều kiện chuyến đang bán. Guard booking/thanh toán chặn sửa đơn, thu lại hoặc phát hành lại khi đã thanh toán/hoàn.

## VNPay và đối soát

Gửi hoàn bằng đúng merchant, mã và thời điểm tạo giao dịch gốc đã lưu; thiếu metadata không được suy đoán. Xác minh chữ ký, tham chiếu, tiền, loại giao dịch và mã hoàn theo [đặc tả VNPay](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

Giao dịch thu thành công không chứng minh đã hoàn. Timeout, phản hồi không xác minh được hoặc crash sau lúc gửi giữ `CAN_DOI_SOAT`. Nếu đã lưu bằng chứng hoàn, xác minh có thể hoàn tất cập nhật vé mà không gửi lại. Nếu mất phản hồi trước khi lưu mã hoàn, giữ khóa và bàn giao T23 đối chiếu yêu cầu, giao dịch, tiền và lịch sử với cổng. T19 chưa có API chốt đối soát thủ công hoặc scheduler hoàn tiền.

Khoản hoàn 0 đồng xử lý nội bộ, không gọi cổng. Kiểm thử tự động dùng gateway giả lập; fixture không phải giao dịch sandbox thật.

## Triển khai và kiểm thử

Áp dụng migration `010_refund_policy.sql`, `011_refund_tracking.sql`, sau đó build và triển khai WAR. Không reset PostgreSQL. Smoke: `python tests/backend/ticket_refund_smoke.py` trên backend `:8080` và PostgreSQL `:5432`; dữ liệu thử được tạo riêng và dọn sau khi chạy.

Kết quả thực chạy ngày 14/10/2026:

| Kiểm tra | Kết quả |
| --- | --- |
| Maven `verify`, đóng gói WAR | 195 test được báo cáo: 181 đạt, 14 bỏ qua, 0 lỗi |
| Hoàn tiền trên PostgreSQL với gateway giả lập | 18 test đạt, nằm trong số 181 test trên |
| Smoke T19 trên Docker | 6 ca đạt, 34 kiểm tra HTTP |
| Hồi quy T16 | 11 ca đạt, 58 kiểm tra HTTP |
| Hồi quy T17 | 12 ca đạt, 116 kiểm tra HTTP |
| Hồi quy T18 | 13 ca đạt, 86 kiểm tra HTTP bằng callback ký cục bộ |
| OpenAPI | YAML/JSON đọc được; tham chiếu nội bộ hợp lệ; bản riêng T19 khớp hợp đồng chung |

18 test PostgreSQL bao gồm hoàn một/nhiều/toàn bộ vé, tỷ lệ 80%/100%/0%, sai chủ đơn, quá hạn, vượt tiền thu, request đồng thời, replay, timeout, thử lại có kiểm soát và phục hồi sau lỗi cập nhật vé/restart. Có test callback thu tiền muộn không đảo trạng thái hoàn hoặc phát hành lại. 14 test bỏ qua là các bộ PostgreSQL cũ ngoài T19 chưa bật cấu hình chạy riêng; không tính chúng là đạt.

Lần chạy Maven đã bật `REFUND_TEST_JDBC_URL`, `REFUND_TEST_JDBC_USER`, `REFUND_TEST_JDBC_PASSWORD` trỏ đến PostgreSQL Docker. Trên Windows/JDK 25 ở máy này cần thêm `-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp` để test HTTP email dùng được socket cục bộ; không thay cấu hình ứng dụng vì lỗi này.

Log trong `src/backend/target/`: `t19-sync-verify.log`, `t19-sync-smoke.log`, `t19-sync-t16.log`, `t19-sync-t17.log`, `t19-sync-t18.log`. Schema test và fixture được dọn sau khi chạy. Migration 010/011 đã áp dụng; giữ dữ liệu hiện có. Khi smoke, email worker được tắt và dùng khóa VNPay giả để ký fixture cục bộ; sau đó khôi phục cấu hình Docker ban đầu.

**Chưa thực hiện hoàn tiền sandbox thật.** Smoke HTTP chỉ thực thi khoản hoàn 0 đồng; hoàn có tiền được kiểm tra bằng gateway giả lập trên PostgreSQL. Chưa nghiệm thu FE và module kiểm vé.

Đã đồng bộ develop `a2f02d1` (T30) trước khi commit T19: giữ API lịch sử `GET /api/booking/me` cùng các route hoàn tiền, snapshot chính sách và cơ chế ẩn QR của T19. Lịch sử chỉ trả đơn của khách đang đăng nhập; đơn cũ không có giữ chỗ vẫn tra cứu chuyến từ vé. Smoke và test dịch vụ đã kiểm tra các trường hợp này.
