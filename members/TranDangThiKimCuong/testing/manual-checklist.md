# Checklist tự kiểm tra — Kim Cương

**86 ca; chạy theo thứ tự bên dưới.** Chỉ ghi Pass sau khi tự chạy; các ca tự động đã đạt xem [báo cáo](backend-refactor-2026-10-20.md).

## Chuẩn bị

- Web: http://localhost:3000. Quản lý: `/quan-tri/nhan-vien`; nhân viên: `/nhan-vien`; chuyến: `/quan-tri/chuyen-xe`.
- Lấy tài khoản tại [danh sách tài khoản](../../../database/tai-khoan-mau.md): Admin, quản lý A/B, tài xế, phụ xe, khách.
- Dùng hai cửa sổ có phiên riêng: quản lý và nhân viên. Chỉ thao tác bản ghi thử `QA_KC_`.
- Tạo D1/D2 (tài xế A), P1 (phụ xe A), O1 (điều hành A), DB (tài xế B); mỗi người có liên hệ riêng.
- Nhân viên muốn đăng nhập phải có tài khoản liên kết; phần cấp/liên kết phối hợp Hiếu.

| Chuyến thử | Chuẩn bị ở màn quản lý chuyến |
|---|---|
| X1 | Ngày tương lai T; S1 → S2, 08:00–12:00 |
| X2 | Cùng ngày; S1 → S2, 09:00–13:00; xe khác/không gán xe để thử trùng người |
| X3 | Cùng ngày; S2 → S1, 12:30–16:30 |
| XL | Chuyến 5 giờ; người/xe chưa trùng lịch |
| XN | T 22:00 → T+1 05:00; người/xe riêng |

Sau ca thành công: **tải lại và đối chiếu**. Sau ca lỗi: **kiểm dữ liệu cũ không đổi**. UI không cho nhập trường hợp cần thử thì dùng API với cookie/CSRF, không sửa frontend để vượt kiểm tra.

## 1. Thêm/sửa hồ sơ

| Mã | Thao tác | Kết quả cần thấy |
|---|---|---|
| HS01 | Thêm D1 đủ thông tin tài xế | 201; có hồ sơ, đúng nhà xe/loại/trạng thái; tải lại vẫn còn |
| HS02 | Thêm P1 và O1, không nhập bằng lái | Lưu được; vai trò hiển thị đúng |
| HS03 | Để trống từng trường bắt buộc: mã, tên, liên hệ, nhà xe, loại, trạng thái | UI chặn hoặc API 400; không phát sinh hồ sơ |
| HS04 | Tên chỉ khoảng trắng | API 400; không lưu tên rỗng |
| HS05 | Thử quá giới hạn: mã 40, tên 160, điện thoại 30, email 254, số bằng 80 ký tự | UI giới hạn nếu có; API từ chối giá trị quá dài. |
| HS06 | Nhập đúng giới hạn mã/tên/số bằng, các trường khác hợp lệ | Lưu được nếu không trùng; không mất ký tự. |
| HS07 | Điện thoại chữ, 9 số, 11 số, không bắt đầu 0 | 400; số đúng 10 chữ số bắt đầu 0 lưu được |
| HS08 | Email thiếu @ hoặc tên miền không hợp lệ | UI/API báo lỗi; ghi rõ chuỗi đã thử. |
| HS09 | Email hoa và khoảng trắng đầu/cuối | Lưu email đã trim, chuyển chữ thường |
| HS10 | Ngày sinh ngày mai / hôm nay / để trống | Tương ứng: 400 / được nhận theo quy tắc hiện có / được nhận |
| HS11 | Tài xế thiếu riêng số bằng hoặc riêng hạn bằng | 400; hồ sơ không được tạo |
| HS12 | Tài xế có bằng hết hạn, chưa có lịch | Hồ sơ có thể lưu; phân công chuyến phải bị chặn. |
| HS13 | Gửi nhà xe không tồn tại, loại/trạng thái không có trong enum, trường text là số | Tương ứng 404 hoặc 400; không lỗi 500 |
| HS14 | Nhập mã kiểm vé/loại KIEM_VE qua API | Loại KIEM_VE bị từ chối 400; không tạo lại chức năng đã gỡ |
| HS15 | Trùng mã nhân viên | 409; bản cũ không đổi |
| HS16 | Khác mã/điện thoại nhưng trùng email, kể cả email khác hoa/thường | 409 |
| HS17 | Khác mã/email nhưng trùng điện thoại | 409 |
| HS18 | Hai người trùng tên, mã và liên hệ khác nhau | Cả hai được lưu; họ tên không phải khóa duy nhất |
| HS19 | Sửa tên/liên hệ hợp lệ; lưu lại email/số điện thoại của chính hồ sơ đó | 200; không báo trùng với chính mình |
| HS20 | Sửa email/điện thoại sang của người khác | 409; tải lại giữ thông tin trước sửa |
| HS21 | Đổi mã hoặc chuyển nhà xe qua API | 400 khi đổi mã; 409 khi chuyển nhà xe |
| HS22 | Người chưa có lịch: đổi tạm nghỉ/nghỉ việc rồi khôi phục | Lưu trạng thái đúng; không tự có quyền nhận phân công khi nghỉ |
| HS23 | Người có phân công đang hoạt động: đổi loại/trạng thái nghỉ | 409; phân công và hồ sơ giữ nguyên |
| HS24 | Người đang có lịch: rút hạn bằng trước ngày đến | 409; hạn cũ giữ nguyên |

## 2. Xóa, tìm và lọc

| Mã | Thao tác | Kết quả cần thấy |
|---|---|---|
| DS01 | Xóa hồ sơ thử chưa có lịch/tài khoản; hủy hộp xác nhận trước một lần | Hủy thì giữ bản ghi; xác nhận thì 204, tải lại không còn |
| DS02 | Xóa người đang làm nhưng không có ràng buộc | Được phép; trạng thái đang làm một mình không cấm xóa |
| DS03 | Xóa người có bất kỳ lịch sử phân công hoặc tài khoản liên kết | 409; không xóa cứng, kể cả chuyến đã hoàn tất |
| DS04 | Xem/sửa/xóa mã không tồn tại | 404; UI có thông báo rõ |
| DS05 | Tìm theo mã, tên tiếng Việt, email, điện thoại; từ khóa không có | Đúng tập kết quả; trường hợp rỗng có thông báo |
| DS06 | Kết hợp lọc nhà xe/loại/trạng thái rồi bỏ lọc | Không trùng dòng, tổng và phân trang khớp kết quả |
| DS07 | >6 hồ sơ: trang trước/sau; đổi lọc khi ở trang cuối | Không bỏ sót/lặp dòng do phân trang; không rơi vào trang vượt giới hạn |
| DS08 | Chọn hồ sơ rồi bấm Thêm nhân viên | Form tạo mới, không ghi đè người vừa chọn |

## 3. Phân công

| Mã | Thao tác | Kết quả cần thấy |
|---|---|---|
| PC01 | D1 → X1 làm tài xế chính; P1 → X1 làm phụ xe | 201; đúng nhân sự chuyến và lịch từng người |
| PC02 | Bấm Kiểm tra điều kiện rồi xem danh sách trước khi Lưu | Chỉ có kết quả kiểm tra, chưa thêm phân công |
| PC03 | Chưa tải chuyến, thiếu mã phân công/chuyến | UI chưa cho lưu; API kiểm tra dữ liệu bắt buộc |
| PC04 | Mã nhân viên/chuyến không tồn tại | 404; không tạo bản ghi dở dang |
| PC05 | Phân công người nhà xe B vào chuyến A | Sai nghiệp vụ: 409; tài khoản sai phạm vi có thể bị chặn 403 trước. |
| PC06 | Phụ xe nhận vai trò tài xế, tài xế nhận vai trò phụ xe | UI vô hiệu vai trò sai; API từ chối 409 |
| PC07 | Điều hành nhận vai trò tổ lái; nhân viên tạm nghỉ/nghỉ việc | UI chặn và API từ chối |
| PC08 | Thiếu/hết hạn bằng lái trước ngày kết thúc chuyến | 409 |
| PC09 | Bằng lái hết hạn đúng ngày đến | Không bị chặn bởi điều kiện ngày; các điều kiện khác vẫn phải đạt. |
| PC10 | D1 đã ở X1, thêm D1 lần nữa với mã phân công khác | 409 |
| PC11 | Chuyến đã có tài xế chính, thêm D2 làm tài xế chính | 409; lái phụ đúng điều kiện vẫn có thể thêm |
| PC12 | D1 ở X1, phân tiếp D1 vào X2 chồng giờ | 409; X1 vẫn còn, X2 không có phân công mới |
| PC13 | X1 → X3 đúng bến, không chồng giờ | Lưu được; thời gian chuẩn bị xe kiểm riêng. |
| PC14 | Không chồng giờ nhưng bến trước đến khác bến sau đi | 409; khoảng cách thời gian dài không tự bỏ qua quy tắc nối bến |
| PC15 | Chuyến qua đêm XN và chuyến kế tiếp | So cả ngày lẫn giờ; không chỉ so chuỗi giờ |
| PC16 | Chuyến sau bắt đầu đúng lúc chuyến trước kết thúc, cùng bến | Không trùng lịch người; cùng xe còn phải đủ thời gian chuẩn bị. |
| PC17 | Sửa ghi chú/đổi vai trò hợp lệ/đổi chuyến hợp lệ | 200; tải lại lịch và danh sách nhân sự đều cập nhật |
| PC18 | Sửa sang chuyến chồng lịch/sai nhà xe | Báo lỗi; dữ liệu phân công cũ giữ nguyên |
| PC19 | Thu hồi phân công ở chuyến nháp; hủy hộp xác nhận trước | Hủy thì giữ; xác nhận thì xóa, lịch nhân viên tải lại không còn |
| PC20 | Sửa/xóa phân công khi chuyến đang chạy, hoàn tất hoặc hủy | Nút không cho thao tác; API từ chối theo trạng thái |
| PC21 | Sửa mã phân công; nhập ghi chú quá dài | UI khóa mã; API lấy ID từ URL. Ghi chú: UI tối đa 800, API 1000 kể cả dấu xác nhận. |
| PC22 | Hai phiên cùng phân một người vào hai chuyến chồng giờ | Một thành công, một 409; không có hai lịch xung đột. |

## 4. Đủ tổ lái và mở bán

| Mã | Thao tác | Kết quả cần thấy |
|---|---|---|
| MB01 | X1 thiếu lái chính hoặc thiếu phụ xe → kiểm tra/mở bán | Không đủ điều kiện; chưa mở bán |
| MB02 | Chuyến đúng 4 giờ, có lái chính và phụ xe | Không bắt buộc lái phụ do thời lượng; vẫn kiểm các điều kiện xe/tuyến. |
| MB03 | Chuyến >4 giờ, chưa xác nhận kế hoạch khi phân công | Bị chặn theo yêu cầu xác nhận kế hoạch |
| MB04 | Chuyến >4 giờ, đã xác nhận nhưng thiếu lái phụ → mở bán | Bị chặn; thêm lái phụ hợp lệ và đủ điều kiện còn lại mới mở bán được |
| MB05 | Đổi lịch/xe chuyến dài sau xác nhận kế hoạch | Kiểm lại kế hoạch cho lịch/xe mới, không dùng dấu xác nhận cũ vô điều kiện |
| MB06 | Chuyến mở bán: gỡ lái chính/phụ xe làm thiếu người bắt buộc | 409 và rollback; tổ lái cũ còn nguyên |
| MB07 | Bắt đầu chuyến thiếu người/xe không sẵn sàng | Bị chặn; không chuyển DANG_CHAY |

## 5. Tài khoản và lịch cá nhân

| Mã | Thao tác | Kết quả cần thấy |
|---|---|---|
| CN01 | A phân công cho tài xế đã liên kết; B đăng nhập tài xế, mở `/nhan-vien` | Đúng tên, mã, nhà xe, loại; đúng chuyến/ngày/giờ/vai trò/ghi chú |
| CN02 | Làm tương tự với phụ xe | Chỉ lịch của phụ xe đó, không lẫn lịch tài xế/người khác |
| CN03 | Quản lý sửa/thu hồi lịch; nhân viên tải lại | Hai phía khớp nhau; không yêu cầu realtime. |
| CN04 | Nhân viên chưa có lịch | Thông báo chưa phân công, không báo lỗi kỹ thuật |
| CN05 | Tài khoản NHAN_VIEN chưa liên kết hồ sơ | Thông báo chưa liên kết; không tự lấy người khác làm hồ sơ |
| CN06 | Đăng nhập tài khoản điều hành | Đối chiếu vai trò tài khoản thật; seed quản lý có thể mang quyền NHA_XE. |
| CN07 | Lọc ngày, chuyển trang lịch, chuyển Tổng quan/Hồ sơ/Lịch | Ngày/giờ giữ đúng múi giờ hiển thị; số và dữ liệu nhất quán |
| CN08 | Tài xế được giao chuyến đủ điều kiện: bắt đầu → hoàn tất | DANG_MO_BAN → DANG_CHAY → HOAN_TAT; quản lý thấy cùng trạng thái. |
| CN09 | Phụ xe hoặc tài xế không được giao chuyến gọi API bắt đầu/hoàn tất | Bị chặn; không chỉ dựa vào việc ẩn nút |
| CN10 | Tài xế thử hủy chuyến/tự tạo phân công | Không có quyền; tài xế không tự nhận chuyến |
| CN11 | Hoàn tất chuyến chưa chạy, bắt đầu lại chuyến đã hoàn tất | Bị chặn, không đổi trạng thái sai |

## 6. Phân quyền và HTTP

| Mã | Thao tác | Kết quả cần thấy |
|---|---|---|
| Q01 | Chưa đăng nhập gọi API nhân viên | 401; UI yêu cầu đăng nhập |
| Q02 | Khách hàng mở quản lý/gọi API nhân viên | Bị chặn; không lộ dữ liệu nhân viên |
| Q03 | Quản lý A xem/tạo/sửa/xóa hồ sơ B | Bị chặn 403; danh sách chỉ trong phạm vi |
| Q04 | Nhân viên gọi chi tiết/lịch của người khác | 403; không lộ hồ sơ/lịch |
| Q05 | Nhân viên tự sửa/xóa hồ sơ qua API | 403; quyền đọc không đồng nghĩa quyền ghi |
| Q06 | POST/PUT/DELETE thiếu hoặc sai X-CSRF-Token | 403; giữ session/cookie đúng để cô lập CSRF |
| Q07 | Đăng xuất hoặc tài khoản khóa rồi tải lại trang/API | Không tiếp tục dùng quyền cũ; phối hợp module tài khoản |
| Q08 | API JSON hỏng, null, số thay trường text, enum không hợp lệ | 400; có lỗi có cấu trúc, không stack trace |
| Q09 | Gọi đường dẫn/method chưa hỗ trợ | Nhân viên: nhánh lạ 404. Phân công: method sai 405, đường dẫn con lạ 404. |

## 7. Giao diện

| Mã | Thao tác | Kết quả cần thấy |
|---|---|---|
| UI01 | Bấm lưu nhanh nhiều lần; mạng chậm | Nút bận/phản hồi rõ, không tự lặp tạo hồ sơ |
| UI02 | Tắt mạng thử tải/lưu rồi bật lại | Có lỗi dễ hiểu, không giả báo lưu thành công; tải lại xác định trạng thái thật |
| UI03 | Desktop 1366px và mobile 390px/320px; mở menu, cuộn form và lịch | Đọc đủ nhãn/nút, không bị che, không mất dữ liệu; ghi bug nếu tràn ngang |
| UI04 | Dùng Tab/Enter, kiểm focus, trường bắt buộc, nút xác nhận/hủy | Thao tác được, thông báo gắn đúng trường/luồng |
| UI05 | Rời trang hoặc tải lại khi chưa lưu | Không tự xuất hiện dữ liệu chưa lưu; ghi hạn chế nếu thiếu cảnh báo rời trang. |

## Ghi kết quả và dọn test

Ghi từng ca: **mã ca → dữ liệu dùng → kết quả thực tế → Pass/Fail/Blocked → ảnh hoặc mã bản ghi**. Thiếu tài khoản/dữ liệu thì ghi Blocked và lý do.

Dữ liệu lưu trong PostgreSQL theo `.env`: `nhan_vien`, `phan_cong_chuyen_xe`, `nguoi_dung`; lịch nối với `chuyen_xe`. Xem bằng pgAdmin/DBeaver, lọc đúng mã `QA_KC_`; bấm lưu không cập nhật seed SQL.

Dọn qua giao diện: thu hồi lịch được phép → xử lý liên kết tài khoản → xóa hồ sơ thử. Lịch sử được bảo vệ thì giữ lại và ghi mã; không ép xóa SQL, không xóa volume.

**Lưu ý:** tài xế được giao chuyến có quyền bắt đầu/hoàn tất; không tự phân công/hủy chuyến. Hệ thống chưa chứng nhận giờ nghỉ hay hạng bằng. Checklist không thay thế kiểm tải lớn hoặc nghiệm thu mọi module.
