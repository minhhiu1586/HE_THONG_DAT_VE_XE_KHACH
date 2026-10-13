# API giữ ghế và đơn đặt vé — T16

Tài liệu này là hợp đồng module cho T16 và bổ sung phần B trong [hợp đồng API chung](./api-contract.md). OpenAPI độc lập: [booking-order-openapi.json](./booking-order-openapi.json). Các phần C–F của tài liệu chung giữ hợp đồng mục tiêu cho báo giá, chốt giá, thanh toán và hoàn tiền; T16 chỉ cung cấp giá tạm tính trên đơn.

Các endpoint cần vai trò `KHACH_HANG`, phiên xác thực cookie `datvexe_session` còn hiệu lực, và `X-CSRF-Token` đúng cho mọi thao tác ghi. Chủ đơn được lấy từ phiên đăng nhập, không nhận `maNguoiDung` từ request. Danh sách hành khách tạm thời nằm trong HTTP session (`JSESSIONID`), không lưu vào PostgreSQL; dữ liệu phải được khóa theo tài khoản xác thực và mã đơn để không lẫn giữa hai tài khoản dùng chung một JSESSIONID. Header `Idempotency-Key` là bắt buộc cho tạo giữ chỗ, PATCH liên hệ và hủy đơn. Các lỗi JSON tuân theo [quy ước chung](./README.md#6-phản-hồi-lỗi-thống-nhất).

## Endpoint

| API | Phương thức và đường dẫn | Thành công | Ghi chú |
| --- | --- | --- | --- |
| B01 | `POST /api/booking/hold` | 201 `HoldSeatResponse` | Giữ toàn bộ ghế trong một giao dịch và tạo một đơn nháp. |
| B02/B05 | `GET /api/booking/{maDon}` | 200 `OrderResponse` | Xem đơn, các ghế giữ, hành khách trong HTTP session và giá tạm tính. Vé (`danhSachVe`) vẫn rỗng trước khi phát hành. |
| B03 | `PATCH /api/booking/{maDon}` | 200 `OrderResponse` | Chỉ `khachDatTen`, `khachDatSoDienThoai`, `ghiChu`; body cần ít nhất một trường. |
| B04 | `PUT /api/booking/{maDon}/hanh-khach` | 200 `OrderResponse` | Thay toàn bộ hành khách; tập ghế phải trùng với các ghế giữ hiện có. |
| B08a | `DELETE /api/booking/{maDon}/ghe/{maGhe}` | 200 `OrderResponse` | Bỏ một ghế; bỏ ghế cuối cùng kết thúc đơn ở `DA_HUY`. |
| B08b | `PUT /api/booking/{maDon}/doi-ghe` | 200 `OrderResponse` | Đổi ghế nguyên tử; xung đột để nguyên ghế cũ. |
| B08c | `PUT /api/booking/{maDon}/khuyen-mai` | 200 `OrderResponse` | Chọn mã bằng `maKhuyenMai` (chuỗi mã hợp lệ). |
| B08d | `DELETE /api/booking/{maDon}/khuyen-mai` | 200 `OrderResponse` | Bỏ mã đang chọn. |
| B07 | `POST /api/booking/{maDon}/huy` | 200 `OrderResponse` | Body tùy chọn; nếu gửi thì dùng `{}`. Gọi lại thao tác hủy cho cùng đơn là an toàn. |

Các endpoint không có khóa chống lặp riêng được bảo vệ bằng transaction, khóa chuyến và ràng buộc cơ sở dữ liệu. Với PATCH, PostgreSQL lưu bản idempotency đã khử `hanhKhach` (`[]`) và `coTheChotGia` (`false`) để không lưu dữ liệu hành khách tạm. Retry nhận đúng response đầy đủ chỉ khi cache response đã được ghi và vẫn còn trong HTTP session hiện tại. Cache có thể chưa được ghi nếu tiến trình dừng sau khi database commit nhưng trước khi session cập nhật; mất session cũng khiến cache này không dùng được. Trong các trường hợp đó, cùng key vẫn chặn thực thi lần hai nhưng chỉ trả trạng thái database đã khử dữ liệu. Client đọc lại đơn để xác nhận snapshot session còn dùng được hoặc nhập lại hành khách nếu session đã mất. Hold và cancel dùng replay từ bản idempotency đã lưu như thông thường. Lỗi validation là `400`; thiếu đăng nhập là `401`; sai vai trò hoặc không phải chủ đơn là `403`; không có tài nguyên là `404`; ghế, thời hạn hoặc trạng thái xung đột là `409`.

## Request mẫu

### Giữ hai ghế

```http
POST /api/booking/hold
Idempotency-Key: hold-unique-key-001
X-CSRF-Token: <csrf-token>
Content-Type: application/json
```

```json
{
  "maChuyen": "CX01",
  "danhSachMaGhe": ["G11", "G12"]
}
```

Danh sách phải khác rỗng, không trùng mã; ghế phải thuộc xe của chuyến và đang khả dụng. Cùng key, method, path và body trả kết quả gốc. Dùng lại key với body khác trả `409 IDEMPOTENCY_KEY_REUSED`.

### Cập nhật liên hệ

```json
{
  "khachDatTen": "Nguyen Van A",
  "khachDatSoDienThoai": "0900000001",
  "ghiChu": null
}
```

Tên người đặt là chuỗi 1–120 ký tự; số điện thoại là chuỗi 1–20 ký tự. `ghiChu` có thể là chuỗi tối đa 500 ký tự hoặc `null`; thiếu trường giữ nguyên giá trị. Trường ngoài allowlist bị từ chối.

### Thay hành khách

```json
{
  "hanhKhach": [
    {"maGhe": "G11", "hoTen": "Nguyen Van A", "soDienThoai": "0900000001"},
    {"maGhe": "G12", "hoTen": "Tran Thi B", "soDienThoai": "0900000002", "cccd": null}
  ]
}
```

`hanhKhach` cần ít nhất một phần tử. Mỗi phần tử cần `maGhe`, `hoTen` (1–120 ký tự) và `soDienThoai` (1–20 ký tự); `cccd` là tùy chọn, tối đa 20 ký tự hoặc `null`. Mã ghế phải duy nhất và khớp toàn bộ ghế giữ. Backend chuẩn hóa rồi lưu danh sách trong HTTP session; không có entity `HanhKhach` và không tạo vé trước khi phát hành. Gửi lại hành khách cho cùng ghế thay thế dữ liệu session của đơn. Nếu kiểm tra cả danh sách thất bại, danh sách session trước đó được giữ nguyên.

### Đổi ghế và khuyến mãi

```json
{"maGheCu": "G11", "maGheMoi": "G13"}
```

```json
{"maKhuyenMai": "KM10"}
```

Để bỏ mã đã chọn, dùng `DELETE /api/booking/{maDon}/khuyen-mai`. Ghế mới phải khả dụng trên cùng chuyến. Nếu thay đổi ghế thất bại, ghế giữ và hành khách trong session còn nguyên.

## Response

### Giữ ghế — 201

```json
{
  "maDon": "DDV01",
  "maChuyen": "CX01",
  "trangThaiDon": "CHO_XU_LY",
  "serverTime": "2026-10-13T09:30:00+07:00",
  "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
  "thoiDiemHetHan": "2026-10-13T09:45:00+07:00",
  "danhSachGheGiu": ["G11", "G12"],
  "danhSachGiuCho": [
    {"maGiuCho": "GC01", "maGhe": "G11", "trangThai": "DANG_GIU", "thoiDiemBatDau": "2026-10-13T09:30:00+07:00", "thoiDiemHetHan": "2026-10-13T09:45:00+07:00"},
    {"maGiuCho": "GC02", "maGhe": "G12", "trangThai": "DANG_GIU", "thoiDiemBatDau": "2026-10-13T09:30:00+07:00", "thoiDiemHetHan": "2026-10-13T09:45:00+07:00"}
  ]
}
```

### Đọc đơn hoặc cập nhật — 200

```json
{
  "maDon": "DDV01",
  "maChuyen": "CX01",
  "trangThaiDon": "CHO_XU_LY",
  "khachDatTen": "Nguyen Van A",
  "khachDatSoDienThoai": "0900000001",
  "ghiChu": null,
  "serverTime": "2026-10-13T09:32:00+07:00",
  "thoiDiemBatDau": "2026-10-13T09:30:00+07:00",
  "thoiDiemHetHan": "2026-10-13T09:45:00+07:00",
  "danhSachGiuCho": [
    {"maGiuCho": "GC01", "maGhe": "G11", "trangThai": "DANG_GIU", "thoiDiemBatDau": "2026-10-13T09:30:00+07:00", "thoiDiemHetHan": "2026-10-13T09:45:00+07:00"}
  ],
  "hanhKhach": [
    {"maGhe": "G11", "hoTen": "Nguyen Van A", "soDienThoai": "0900000001", "cccd": null}
  ],
  "danhSachVe": [],
  "giaTamTinh": {
    "tongTienGoc": "125000",
    "tienGiam": "0",
    "tongThanhToan": "125000",
    "maKhuyenMaiDaChon": null,
    "maKhuyenMaiApDung": null,
    "lyDoKhongApDung": null
  },
  "coTheChotGia": true,
  "giaDaChot": null,
  "thongBao": []
}
```

`hanhKhach` là snapshot ứng dụng tạm thời trong HTTP session; đây không phải dữ liệu vé hay bản ghi database. Khi đọc lại bằng cùng `JSESSIONID`, API trả danh sách đã lưu. Cùng cookie xác thực nhưng session HTTP mới đọc được đơn và ghế từ database, đồng thời trả `hanhKhach: []` và `coTheChotGia: false`; không tạo thêm đơn. Với đơn lịch sử không còn giữ chỗ, `thoiDiemBatDau` và `thoiDiemHetHan` có thể là `null`. T17 phải đọc và kiểm tra lại danh sách session hiện tại trước khi chốt giá. Nếu session mất, khách cần nhập lại hành khách. Với PATCH idempotency, retry chỉ nhận response đầy đủ khi cache session đã ghi và còn tồn tại; nếu cache chưa kịp ghi hoặc session mất, API trả snapshot database đã khử dữ liệu (`hanhKhach: []`, `coTheChotGia: false`). Client cần đọc lại đơn để kiểm tra snapshot session hoặc nhập lại hành khách.

`danhSachVe` luôn là `[]` trước khi thanh toán/phát hành; T18 chỉ tạo vé có `maVe`, trạng thái và thời điểm phát hành sau khi xác minh thanh toán. Không ghi hành khách, mã QR, ngày phát hành hoặc bản ghi vé nháp vào database trong T16. `coTheChotGia` cho biết session hiện tại có đủ hành khách hợp lệ cho các ghế đang giữ; giá chưa chốt nên `giaDaChot` vẫn `null`.

Các trường tiền trong `giaTamTinh` là chuỗi thập phân chính xác. Đây là preview: dùng `tuyen_xe.gia_co_ban × số ghế giữ`, không làm tròn và không reserve quota. T17 quyết định làm tròn và tạo snapshot đã chốt. Preview mã không hợp lệ trả lý do trong `lyDoKhongApDung`; GET không xóa lựa chọn đã lưu. Khi thao tác ghế, nếu mã đã mất hiệu lực thì bỏ lựa chọn đó và tính lại.

## Vòng đời và tính nhất quán

- Thời hạn giữ 15 phút theo thời gian máy chủ; không gia hạn khi GET, PATCH, lưu hành khách hoặc chọn mã. Hết hạn đúng tại `now >= thoiDiemHetHan`.
- Khi phát hiện hết hạn, cùng giao dịch chuyển giữ và đơn sang `HET_HAN`, xóa hành khách tạm của đơn khỏi HTTP session, rồi commit trước khi trả `409 HOLD_EXPIRED`. GET đơn đã hết hạn cũng trả lỗi này sau khi commit chuyển trạng thái; đơn không đổi sang `DA_HUY` chỉ vì giữ hết hạn.
- Hủy đơn là idempotent và giải phóng giữ chỗ một lần.
- Đơn đã thanh toán vẫn đọc được; mọi sửa liên hệ, hành khách, ghế, mã giảm hoặc hủy đơn đều bị chặn. Bỏ ghế hoặc hết hạn xóa hành khách session của ghế đó; vé đã phát hành được giữ nguyên lịch sử.
- Khóa theo thứ tự nhà xe → chuyến → đơn để đồng bộ với T12; khóa hàng chuyến tuần tự hóa việc giữ ghế. Migration 005 tạo unique index có điều kiện trên cặp chuyến/ghế chỉ cho vé `DA_XUAT_VE` và `DA_LEN_XE`. Trước khi áp dụng, migration dừng nếu còn dòng vé `NHAP` bất thường để nhóm kiểm tra, không tự xóa dữ liệu.
- Các bản ghi giữ cũ không có chủ sở hữu không được gán tự động cho khách; chúng hết hạn theo thời điểm lưu hiện tại.

## Smoke API và database

`tests/backend/booking_order_smoke.py` là một smoke tích hợp duy nhất dùng backend thật ở cổng 8080 và PostgreSQL thật ở cổng 5432. Nó đăng ký tài khoản `KHACH_HANG` riêng (không nâng quyền), tạo chuyến/ghế/khuyến mãi với tiền tố ngẫu nhiên, đối chiếu HTTP và SQL, rồi xóa đúng đơn/giữ và fixture thuộc lượt chạy. Test kiểm tra session cùng JSESSIONID, session mới, cách ly hai tài khoản, rollback validation, replay PATCH và phần response đã khử trong PostgreSQL, đồng thời xác nhận `ve_xe` không được tạo trước phát hành. Không chạy trên production. Test hết hạn sửa thời điểm của giữ do chính test tạo nên không phải đợi 15 phút.

Migration 004 là nền lịch sử cho idempotency. Trước khi triển khai WAR mới, áp dụng `docs/database/schema/005_booking_session.sql` trên database đã có 004. Migration giữ nguyên dữ liệu, dừng nếu gặp vé `NHAP` bất thường và tạo chỉ mục unique cho vé đã phát hành/đã lên xe. Giữ class diagram nguyên bản. PostgreSQL không có vé NHAP và T16 không tạo VeXe trước phát hành.

Từ root repository, sau khi migration và WAR mới đã sẵn sàng:

```powershell
# Đặt POSTGRES_DB/POSTGRES_USER/POSTGRES_PASSWORD theo volume hiện tại.
# Môi trường đã kiểm thử dùng DB datvexe; môi trường khác đặt DB_NAME tương ứng.
$env:POSTGRES_DB='datvexe'
$env:DB_NAME='datvexe'
docker compose stop backend
docker compose --profile ops run --rm --no-deps migrate
mvn -B -f src/backend/pom.xml clean verify
docker compose up -d --no-deps backend
Invoke-RestMethod http://localhost:8080/api/health
python tests/backend/booking_order_smoke.py
```

Health cần báo cả backend và database `UP`. Python 3 chuẩn, Docker CLI, container PostgreSQL phát triển (`DB_CONTAINER`, mặc định `datvexe-postgres`) và database `datvexe` (có thể đặt `DB_NAME`) là dependency của smoke. Có thể đổi `API_BASE_URL`, `DB_CONTAINER` và `DB_NAME`. Đăng ký/đăng nhập bị giới hạn tốc độ; nếu gặp 429, chờ qua cửa sổ giới hạn trước khi chạy lại. Script tự in số nhóm PASS/FAIL và số assertion HTTP; đây là metadata của lần chạy thực tế, không phải kết quả đã có sẵn.


## Kết quả kiểm thử ngày 2026-10-13

| Kiểm tra đã thực hiện | Kết quả |
| --- | --- |
| Maven package/test qua Docker, Java 21 | 94 test thành công; 14 test PostgreSQL chuyên biệt được skip theo cấu hình; không có failure/error. |
| Booking smoke trên backend :8080 và PostgreSQL :5432, DB datvexe | 11 nhóm PASS, 58 kiểm tra HTTP; không có FAIL hoặc HTTP 500. |
| Hồi quy T14 với paymethod_discount_smoke.py | 6 nhóm PASS, 101 kiểm tra HTTP; mã chỉ được chọn để preview không chặn sửa khuyến mãi, lịch sử đã thanh toán vẫn được bảo vệ. |
| OpenAPI chung và OpenAPI T16 | JSON/YAML đọc được, mọi local reference hợp lệ; ví dụ HoldSeatResponse/OrderResponse khớp schema. |
| Dọn dữ liệu kiểm thử | Fixture được xóa theo tài khoản/mã riêng của lượt chạy; không còn nhà xe T16 thử nghiệm hoặc vé NHAP. |

Smoke kiểm tra giữ nhiều ghế thành công cùng một đơn, rollback khi một ghế xung đột, hai khách tranh ghế, quyền/CSRF, session cùng tài khoản và khác tài khoản, mất session, validation cả danh sách, đổi/bỏ ghế, hủy ghế cuối, hết hạn rồi đặt lại và replay không tái giữ ghế. Khuyến mãi được tính chính xác bằng BigDecimal, không giữ/trừ lượt. Đơn đã thanh toán được kiểm tra bằng fixture database; đây không phải kiểm thử tích hợp cổng thanh toán.

Phạm vi bàn giao là backend T16. Chưa tích hợp FE, chưa chốt giá/phân bổ tiền T17 hoặc phát hành vé T18. Session có thể mất; khách phải nhập lại hành khách. Jpa.java và cấu hình Docker đã đồng bộ với develop; Compose chỉ bổ sung hai mount migration T16. Cần đặt biến môi trường đồng nhất giữa backend, PostgreSQL và migrate khi khởi động lại. Các kết quả Maven và smoke ở trên được ghi nhận trước lần đồng bộ cấu hình này; sau đồng bộ đã kiểm tra cấu hình Compose hợp lệ, chưa chạy lại smoke.
