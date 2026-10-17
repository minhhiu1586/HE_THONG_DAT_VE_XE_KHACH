# T10 — Loại xe, phương tiện, ghế vật lý và đăng kiểm

Module sử dụng nguyên trạng entity/schema T03. Không thêm, bớt hoặc đổi thuộc tính database. Các trường bổ sung trong response là giá trị tính toán, không phải cột mới. Dùng cookie/CSRF T07, guard/phạm vi T07.1 và nhà xe T09; JSON thành công trả trực tiếp, lỗi `{code,message,details,timestamp,traceId}` theo T05.

## Quyền và endpoint

Tất cả đường dẫn dưới đây có tiền tố `/api`. ADMIN quản lý loại xe; NHA_XE được đọc loại xe để chọn khi tạo xe. Xe/ghế chỉ ADMIN hoặc NHA_XE thuộc phạm vi thực tế trong database quản lý. Nhà xe ngừng hoạt động vẫn được đọc lịch sử nhưng không được ghi. Người chưa đăng nhập nhận 401; sai quyền/phạm vi nhận 403.

| Method | Đường dẫn | Nội dung |
| --- | --- | --- |
| GET | `/loai-xe?q=` | Tìm loại xe theo mã/tên, trả array |
| POST | `/loai-xe` | Tạo loại xe, trả 201 |
| GET/PUT/DELETE | `/loai-xe/{maLoaiXe}` | Chi tiết/cập nhật/xóa; ghi chỉ ADMIN |
| GET | `/xe?q=&maLoaiXe=&maNhaXe=&trangThai=&maChuyen=` | Tìm xe; `maChuyen` giới hạn xe đủ điều kiện và không trùng lịch |
| GET | `/xe/pham-vi` | Nhà xe được phép chọn; chỉ mã/tên/trạng thái, NHA_XE chỉ nhận nhà xe mình |
| POST | `/xe` | Tạo xe và toàn bộ ghế cùng giao dịch, trả 201 |
| GET/PUT/DELETE | `/xe/{maXe}` | Chi tiết/cập nhật/xóa xe |
| GET | `/xe/{maXe}/ghe` | Danh sách ghế vật lý theo tầng/vị trí |
| PUT | `/xe/{maXe}/so-do-ghe` | Lưu nguyên tử toàn bộ sơ đồ đủ sức chứa |
| GET/PUT | `/xe/{maXe}/ghe/{maGhe}` | Xem/sửa một ghế; kiểm tra vị trí, tầng, trạng thái và liên kết |
| POST | `/xe/{maXe}/ghe` | Chỉ cho phép hoàn thiện đúng sức chứa xe legacy thiếu một ghế; dùng API toàn sơ đồ khi cần thay cấu hình |
| POST | `/xe/{maXe}/ghe/sinh` | Sinh mặc định khi danh sách rỗng; không sinh lại ghế đang có |
| DELETE | `/xe/{maXe}/ghe/{maGhe}` | Không cho xóa riêng làm thiếu sức chứa; dùng toàn sơ đồ để thay ghế chưa có ràng buộc |
| GET | `/xe/{maXe}/dieu-kien/{maChuyen}` | Kiểm tra trạng thái, đăng kiểm, sơ đồ, nhà xe và trùng lịch; không tự gán xe |
| GET | `/chuyen-xe/{maChuyen}/ghe` | Công khai, trạng thái khả dụng theo đúng chuyến; không lộ người mua/giữ |

`pham-vi` là mã dành riêng cho endpoint, không dùng làm mã xe mới. ID và enum phải là chuỗi; ID dạng số/boolean bị từ chối. Phân công nhân viên vẫn dùng API/service hiện có, T10 không tạo lại module phân công.

## Request và response quản lý

Loại xe: `{maLoaiXe,tenLoaiXe,soChoNgoi,soTang,moTa?}`. Mã dài tối đa 40, tên 120, mô tả 1000; sức chứa/số tầng là số nguyên dương, tầng không lớn hơn sức chứa. Entity không có trạng thái loại xe nên không bổ sung. Không đổi sức chứa/tầng của loại đã có xe; sửa tên/mô tả không tự thay sơ đồ.

Xe: `{maXe,bienSo,maLoaiXe,maNhaXe?,namSanXuat?,ngayDangKiem,hanDangKiem,trangThai?,tocDo?,dungTichXang?,soChoNgoi?}`. PUT gửi đủ thông tin chỉnh sửa; không đổi loại xe hoặc nhà xe sau tạo. NHA_XE được gán nhà xe từ danh tính đã xác thực, không thể khai báo nhà xe khác. ADMIN phải chọn nhà xe tồn tại. Sức chứa lấy từ loại xe; nếu gửi `soChoNgoi` thì phải dương và khớp loại.

Biển số được trim, bỏ khoảng trắng, đổi chữ hoa; duy nhất toàn hệ thống, ràng buộc unique hiện có bảo vệ khi ghi đồng thời. Chấp nhận chữ/số/dấu chấm/gạch ngang, 3–30 ký tự. Dung tích (lít) và tốc độ (km/giờ) tùy chọn, nếu có phải dương và tối đa hai chữ số thập phân. Năm sản xuất tùy chọn, từ 1 đến năm hiện tại + 1.

Response xe có dữ liệu entity và các cờ tính toán: `soGheThucTe`, `soDoHopLe`, `dangKiemConHieuLuc`, `coTheSuaSoDo`. Cờ đăng kiểm theo ngày hiện tại ở `Asia/Ho_Chi_Minh`; khi gán/bán phải đánh giá lại theo toàn thời gian chuyến. Không suy ra hiệu lực từ một ngày đăng kiểm.

## Mã và sơ đồ ghế

`maGhe` là khóa liên kết database đã chốt, toàn hệ thống dùng ID sinh bởi backend. Không đổi ID của ghế đang tồn tại. `viTri` là mã/vị trí hiển thị như A1, G001; hai xe khác nhau có thể cùng A1. Trong cùng xe không được trùng `viTri` sau chuẩn hóa chữ hoa. Tầng phải từ 1 đến số tầng loại xe. Không thêm thuộc tính hàng/cột; giao diện sắp xếp tự nhiên theo tầng và `viTri`, bố trí các ô để xem, không coi thứ tự mảng là thuộc tính database mới.

Ví dụ lưu sơ đồ của xe bốn chỗ:

```json
{
  "danhSachGhe": [
    {"maGhe":"ID_GHE_1","viTri":"A1","tang":1,"trangThai":"HOAT_DONG"},
    {"maGhe":"ID_GHE_2","viTri":"A2","tang":1,"trangThai":"HOAT_DONG"},
    {"maGhe":"ID_GHE_3","viTri":"B1","tang":2,"trangThai":"BAO_TRI"},
    {"maGhe":null,"viTri":"B2","tang":2,"trangThai":"HOAT_DONG"}
  ]
}
```

Thay các ID mẫu bằng ID thật từ GET ghế. `maGhe:null` tạo ghế mới trong giao dịch; các ID cũ không có trong danh sách bị loại bỏ chỉ khi được phép. Không gửi `maXe` vào từng phần tử request. Danh sách phải đủ đúng sức chứa, không trùng ID/vị trí và không dùng ID ghế xe khác. Toàn bộ validation chạy trước ghi; lỗi bất kỳ phần tử nào rollback. Đổi chỗ hai mã/vị trí được lưu trong một giao dịch, không vi phạm unique hiện có. Form thêm/bỏ vị trí chỉ là thay đổi chưa lưu, không phải trạng thái nháp của database.

## Đăng kiểm, bảo trì và lịch sử

- Ngày bắt đầu và hạn đăng kiểm là bắt buộc; hạn không trước ngày bắt đầu. Điều kiện xe phải bao phủ cả ngày khởi hành và ngày đến, kể cả chuyến qua đêm. Ngày đến đúng hạn vẫn hợp lệ.
- Chỉ xe SAN_SANG của nhà xe hoạt động, đủ sơ đồ và đăng kiểm mới được gán cho chuyến. Có thể lưu xe hết đăng kiểm để theo dõi nhưng không đưa vào mở bán.
- Ghế BAO_TRI/NGUNG_SU_DUNG vẫn là ghế vật lý trong sức chứa; không được giữ/bán. Khi sửa sơ đồ, chặn nếu xe còn chuyến chưa kết thúc hoặc bất kỳ ghế nào có lịch sử vé/giữ chỗ. Đây là chính sách hạn chế của phiên bản đầu.
- Không xóa xe có lịch sử chuyến; không xóa/sửa ghế có vé/giữ chỗ. Chặn đổi xe của chuyến có vé hoặc giữ chỗ. Không tự đổi ghế hoặc chuyển khách.
- Cập nhật đăng kiểm/trạng thái xe không được làm mất điều kiện của chuyến chưa kết thúc. API từ chối thay đổi đó, giữ nguyên chuyến/vé; người vận hành phải xử lý lịch theo quy trình riêng. Không tự hủy vé.

## Tích hợp bán vé

`VehicleRules.ready(em,maXe,trip)` kiểm tra xe dưới khóa trong transaction hiện tại. Luồng tạo/gán/đổi lịch chuyến đã tích hợp helper này. `AccessControl.saleAllowed(em,maChuyen)` của T09 được nối thêm kiểm tra xe và trạng thái chuyến/tuyến.

Module giữ/bán ghế gọi `VehicleRules.seatSellable(em,maChuyen,maGhe,serverNow)` **trong cùng transaction trước khi lưu giữ chỗ/vé**. `serverNow` do backend tính theo múi giờ nghiệp vụ; vẫn phải kiểm tra vai trò, quyền đơn, idempotency và ràng buộc chống bán trùng của module đặt vé. Helper khóa xe/ghế và kiểm tra ghế thuộc xe, bảo trì, vé hiệu lực và giữ chỗ chưa hết hạn. Ghế có vé ở chuyến C1 không làm ghế đó ở C2 đã bán.

GET ghế theo chuyến trả `{maChuyen,maXe,serverTime,danhSachGhe}`. Mỗi ghế có `maGhe,viTri,tang,trangThaiSuDung,tinhTrangKhaDung`; khả dụng gồm TRONG, DANG_GIU, DA_BAN, KHONG_BAN. Không ghi các giá trị bán/giữ vào enum ghế vật lý. API này giữ `viTri` dạng chuỗi theo entity hiện có; không thêm hàng/cột hoặc tự thay schema theo ví dụ OpenAPI mục tiêu.

Không triển khai endpoint giữ ghế/bán vé trong T10. Bộ test kiểm tra helper, dữ liệu vé/giữ chỗ và sơ đồ theo chuyến; thành viên module đặt vé phải dùng helper trong endpoint thực tế và kiểm thử luồng đó khi triển khai.

## Lỗi

400 VALIDATION_ERROR: thiếu/sai trường, sơ đồ thiếu/thừa/trùng hoặc tầng sai. 404 RESOURCE_NOT_FOUND: loại xe/xe/nhà xe không tồn tại. 409 INVALID_STATE_TRANSITION: trùng biển số/mã, dữ liệu đang được dùng, xe thiếu điều kiện, không thể thay đổi lịch sử. 401/403 theo T07. Không trả SQL, stack trace hay người mua/giữ ghế trong phản hồi lỗi.
