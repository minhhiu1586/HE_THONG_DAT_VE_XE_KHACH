# Kiểm thử tổng hợp chức năng hiện có

Nhánh: `feature/integration-crud-audit`. Nền code: `develop`, commit `5180226`. Database: `datvexe`. Tài khoản: [accounts.md](../database/seed/accounts.md). Dữ liệu kiểm thử tự động dùng mã riêng và được dọn sau lượt chạy, không sửa hồ sơ seed.

## Chạy ứng dụng và kiểm tra tự động

```powershell
docker compose --profile ops run --rm migrate
# Maven đã cài trên máy hiện tại qua NetBeans.
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' -f src/backend/pom.xml package
docker compose up -d postgres backend
docker compose restart backend
npm.cmd --prefix src/frontend ci
npm.cmd --prefix src/frontend run build
npm.cmd --prefix src/frontend run start
```

Frontend tại `src/frontend`: `npm.cmd ci`, `npm.cmd run build`, `npm.cmd run start`. Không chạy hai server cùng cổng 3000. Build không chứng minh mọi luồng nghiệp vụ đạt.

Tại thư mục gốc:

```powershell
python tests/backend/run_integration_audit.py
```

Runner chạy tuần tự tài khoản → nhân viên → tuyến/bến → xe → chuyến/mở bán → đặt vé → báo giá → khuyến mãi/phương thức → thanh toán → kiểm vé → hoàn vé. Log và kết quả JSON nằm trong `src/backend/target/integration-audit/`, không đưa lên Git. Nó đối chiếu dữ liệu lõi trước/sau để phát hiện fixture còn sót. Test nhà cung cấp email/VNPay thật cần cấu hình riêng; không tính test HTTP nội bộ là giao dịch bên ngoài đã đạt.

Chạy lại riêng một bộ sau khi sửa bài kiểm thử: `python tests/backend/run_integration_audit.py --only routes`. Kết quả riêng được lưu tại `results-selected.json`, giữ nguyên báo cáo toàn bộ trước đó.

## Kết quả xác minh ngày 14/10/2026

Backend Maven package trên `5180226` thành công: 267 test, 0 lỗi, 63 test bỏ qua theo điều kiện cấu hình (204 thực thi đạt). Frontend production build và TypeScript đạt. Ứng dụng chạy tại `http://localhost:3000`; backend `/api/health` trả 200. Migration 001–014 đã áp dụng.

T22 chạy riêng trên container PostgreSQL tạm: 36 test đạt, 0 lỗi, 0 bỏ qua; container được dọn sau kiểm thử. API đánh giá công khai trả 200. Frontend lint: 0 lỗi, 2 cảnh báo `<img>` có sẵn tại `BusStations.tsx` và `PopularRoutes.tsx`.

| Bộ kiểm thử HTTP | Kết quả cuối | Số kiểm tra |
| --- | --- | --- |
| Tài khoản, phiên, quyền | Đạt | 39 |
| Hồ sơ nhân viên, tài xế, phân công đồng thời | Đạt | 76 |
| Bến/tuyến/điểm dừng và vòng đời chuyến | Đạt | 96 |
| Xe, ghế, đăng kiểm, phạm vi | Đạt | 81 |
| Chuyến nháp, điều kiện, mở bán đồng thời và lọc nhóm tuyến | Đạt | 123 |
| Đơn đặt vé, giữ ghế, quyền chủ đơn | Đạt | 58 |
| Báo giá và giá cố định | Đạt | 116 |
| Khuyến mãi, phương thức thanh toán | Đạt | 101 |
| Thanh toán nội bộ / IPN có chữ ký | Đạt các ca thực thi; 9 ca chưa chạy | 20 |
| Kiểm vé, QR, chống xác nhận lặp | Đạt | 26 |
| Chính sách, preview, hoàn vé giá trị 0 | Đạt | 34 |

Tổng 770 kiểm tra HTTP của 11 bộ, không bao gồm request dựng fixture. `results.json` và log từng bộ nằm trong `src/backend/target/integration-audit/`. Đối chiếu trước/sau: 5 nhà xe, 35 nhân viên (20 tài xế), 47 tài khoản, 10 xe, 320 ghế, 20 tuyến, 10 bến; dấu vân tay dữ liệu lõi phải khớp.

Đã kiểm trực tiếp trên web: đăng nhập quản trị; tìm NV_FUTA_01 và hiển thị lịch; điều hành FUTA thấy 7 nhân viên/4 tài xế và chỉ nhà xe FUTA; thêm tài xế `KC_AUD_UI_1014`, sửa tên, reload rồi tìm lại đúng tên; sửa email thành email đã có (khác hoa/thường) bị chặn; xóa hồ sơ thử chưa có phân công thành công, tìm lại không còn, tổng FUTA trở về 7. Hồ sơ thử này đã dọn.

Các ca nghiệp vụ còn lại trong bảng đã kiểm qua HTTP/PostgreSQL; các luồng web bên dưới là hướng dẫn để thành viên kiểm tiếp từng thao tác. Chưa xác minh gửi email thật do chưa cấu hình Brevo, chưa thực hiện thanh toán/QueryDR/hoàn tiền VNPay thật do thiếu cấu hình và giao dịch sandbox. Không kết luận các luồng bên ngoài này đã đạt.

Tài xế `tx1.futa@datvexe.test` đã đăng nhập trên web: vào `/quan-tri/nhan-vien` bị chặn quyền; `/nhan-vien` hiển thị Nguyễn Minh Đức, lịch 30 chuyến và hồ sơ NV_FUTA_01 chỉ đọc. Hồ sơ công việc do quản lý cập nhật; thông tin tài khoản có trang riêng.

Log nằm trong thư mục target và có thể mất khi Maven clean; hướng dẫn và bài kiểm thử được lưu trong Git.

## Luồng 1 — Đăng nhập và phạm vi quyền

Mở `http://localhost:3000/dang-nhap`. Mật khẩu khởi tạo `DatVeXe2026!` nếu chưa đổi; đăng xuất trước khi đổi tài khoản.

| Tài khoản | Trang / quyền cần kiểm |
| --- | --- |
| admin1@datvexe.test | `/quan-tri/nhan-vien`, toàn bộ 35 hồ sơ |
| dieuhanh.futa@datvexe.test | Cùng trang, chỉ 7 nhân viên FUTA |
| tx1.futa@datvexe.test | `/nhan-vien`, hồ sơ/lịch riêng NV_FUTA_01 |
| px1.futa@datvexe.test | `/nhan-vien`, hồ sơ/lịch riêng NV_FUTA_05 và kiểm vé theo quyền |
| khach1@datvexe.test | `/tai-khoan`, đặt vé/lịch sử đơn; không quản lý nhân viên |

Nhân viên không có nút quản lý hồ sơ/phân công. Gõ URL quản trị trực tiếp cũng không được vượt quyền. Quản lý nhà xe không đọc/sửa hồ sơ nhà xe khác.

## Luồng 2 — Thêm hồ sơ tài xế / phụ xe

Đăng nhập admin hoặc điều hành, mở **Nhân viên và phân công → Thêm nhân viên**. Dùng mã riêng `KC_CHECK_D01`, nhà xe FUTA, họ tên `Nguyễn Văn Kiểm`, điện thoại `0909900061`, email `kc.check.d01@example.test`; nếu liên hệ đã có thì dùng giá trị riêng khác. Chọn vai trò tài xế, số bằng `123456789061`, hạn `2035-12-31`. Phụ xe dùng mã `KC_CHECK_P01` và liên hệ khác, không bắt buộc bằng lái.

| Ca kiểm | Kỳ vọng theo code hiện có |
| --- | --- |
| Hồ sơ đủ thông tin, nhà xe hợp lệ | Thêm được; đọc lại bằng tìm mã, reload trang vẫn còn |
| Hai người trùng họ tên, mã/liên hệ khác | Được phép; tên không phải khóa định danh |
| Trùng mã nhân viên | Từ chối 409 |
| Trùng email, kể cả khác hoa/thường | Từ chối 409 |
| Trùng điện thoại | Từ chối 409 |
| Email/điện thoại sai định dạng | Từ chối 400 / validation trên form |
| Thiếu số/hạn bằng với tài xế | Từ chối 400 |
| Ngày sinh trong tương lai | Từ chối 400 |
| Nhà xe không tồn tại | Từ chối 404 |

Thêm hồ sơ nhân viên không tự tạo tài khoản đăng nhập. `NhanVien` là hồ sơ nghiệp vụ, `NguoiDung` là tài khoản; tài khoản nhân viên cần liên kết `ma_nhan_vien` và quyền đúng. Màn hình quản trị tài khoản hiện quản lý trạng thái tài khoản, không coi đăng ký khách hàng là cấp quyền nhân viên.

## Luồng 3 — Sửa hồ sơ và kiểm tra rollback

Chọn hồ sơ thử trong danh sách, sửa họ tên/liên hệ rồi **Lưu hồ sơ**. Tìm lại/reload và đối chiếu PostgreSQL. Thử đổi email thành email người khác: phải báo lỗi, email cũ vẫn còn.

| Thao tác | Kỳ vọng |
| --- | --- |
| Đổi họ tên/liên hệ thành giá trị hợp lệ | Lưu được |
| Đổi mã nhân viên | Không cho phép; form khóa mã, API từ chối |
| Chuyển nhà xe | Không cho phép; form khóa nhà xe, API từ chối |
| Đổi vai trò/trạng thái khi chưa có phân công hoạt động | Cho phép theo validation |
| Cho tài xế/phụ xe đang có chuyến hoạt động nghỉ hoặc đổi vai trò | Chặn 409; cần xử lý phân công trước |
| Rút hạn bằng khiến không bao phủ chuyến đã giao | Chặn 409 |

Hồ sơ tài khoản tại `/tai-khoan` và hồ sơ công việc là hai bảng riêng; cập nhật thông tin tài khoản không có nghĩa hồ sơ công việc được đồng bộ tự động.

## Luồng 4 — Phân công và xung đột lịch

Tại `/quan-tri/chuyen-xe`, tạo chuyến nháp cho nhà xe đang kiểm. Tại nhân viên, nhập mã chuyến vào form phân công → **Tải thông tin chuyến → Kiểm tra điều kiện → Lưu phân công**. Tạo mã phân công riêng. Kiểm lại cả lịch nhân viên và nhân sự chuyến; dữ liệu lưu trong `phan_cong_chuyen_xe`.

| Ca kiểm | Kỳ vọng |
| --- | --- |
| Đúng vai trò, nhà xe, trạng thái, hạn bằng, không trùng lịch | Lưu được |
| Cùng nhân viên lặp trên cùng chuyến | Chặn 409 |
| Tài xế chính thứ hai trên cùng chuyến | Chặn 409 |
| Phụ xe gán vai trò tài xế / tài xế gán vai trò phụ xe | Chặn 409 |
| Khác nhà xe / nhân viên đang nghỉ / bằng hết hạn | Chặn 409 |
| Chuyến giao nhau, kể cả qua đêm | Chặn 409 |
| Nối chuyến nhưng bến đến trước khác bến đi sau | Chặn 409 |
| Nối đúng bến và không trùng thời gian | Có thể hợp lệ; lịch xe còn phải đủ đệm chuẩn bị |
| Chuyến trên 4 giờ chưa xác nhận kế hoạch lái/nghỉ | Chặn 409 |
| Hai tài khoản đồng thời phân công một nhân viên vào hai chuyến giao nhau | Chỉ một yêu cầu thành công |
| Sửa/thu hồi chuyến đang chạy hoặc hoàn tất | Không được làm sai lịch sử |

Số/hạn bằng được kiểm tra theo mô hình hiện có; hạng bằng không nằm trong thiết kế đã chốt. Xác nhận kế hoạch không phải chứng nhận hệ thống đã tính đủ giờ nghỉ thực tế.

## Luồng 5 — Xóa hồ sơ

Chỉ dùng hồ sơ thử tự tạo để kiểm xóa. Bấm **Xóa hồ sơ**, xác nhận, tìm lại mã và reload trang.

| Hồ sơ | Kỳ vọng hiện tại |
| --- | --- |
| Tài xế đang làm việc nhưng chưa có phân công/tài khoản | Xóa được |
| Phụ xe/điều hành đang làm việc nhưng chưa có phân công/tài khoản | Xóa được |
| Có bất kỳ lịch sử phân công | Chặn xóa cứng 409 |
| Có tài khoản liên kết | Chặn xóa cứng 409 |
| Hồ sơ không tồn tại | 404 |

Trạng thái “đang làm việc” không tự chặn xóa. Điều kiện chặn là quan hệ đã phát sinh; không thay đổi quy tắc này chỉ để test. Nếu muốn nhân viên rời công ty mà vẫn giữ lịch sử, cập nhật trạng thái sau khi xử lý các phân công hoạt động.

## Các luồng tiếp theo trên web

| Thứ tự | Trang | Thao tác kiểm |
| --- | --- | --- |
| 6 | `/quan-tri/tuyen-duong` | Thêm/sửa/tìm bến, tuyến và điểm dừng; trùng mã; chặn xóa tài nguyên đang được tham chiếu |
| 7 | `/quan-tri/xe` | Loại xe, xe, ghế và đăng kiểm; trùng biển số; ghế 22/29/40 đúng số lượng; chặn xóa xe có lịch sử chuyến |
| 8 | `/quan-tri/chuyen-xe` | Chuyến nháp → phân công → kiểm điều kiện → mở bán; sửa lịch/xe phải kiểm lại; hành khách và vòng đời chuyến theo quyền |
| 9 | `/trips`, `/tim-chuyen/{id}` | Tìm đúng tuyến/ngày, đọc chi tiết/ghế; không có dữ liệu trả trạng thái rỗng |
| 10 | `/tim-chuyen/{id}/seats`, `/checkout` | Chọn/bỏ chọn/giữ ghế; hai khách tranh cùng ghế; liên hệ, hành khách, báo giá; quyền chủ đơn và hết hạn |
| 11 | `/tai-khoan`, trang xác nhận đơn | Lịch sử đơn/vé, mã vé và thanh toán; giao dịch sandbox có điều kiện cấu hình riêng |
| 12 | `/nhan-vien`, mục kiểm vé | Đọc vé/QR theo chuyến được giao; xác nhận lên xe, chống lặp, sai chuyến/ngoài phạm vi |
| 13 | Hồ sơ/lịch sử đơn, mục hoàn vé | Điều kiện hoàn, preview, chống lặp; không hoàn sai chủ đơn hoặc sai trạng thái |
| 14 | Lịch sử đơn, chi tiết chuyến, `/quan-tri/danh-gia` | Khách có vé hợp lệ trên chuyến hoàn tất tạo/sửa/xóa mềm đánh giá; admin/nhà xe duyệt hoặc ẩn theo quyền; công khai chỉ thấy đánh giá đã duyệt |

## Dữ liệu hiển thị và lưu ở đâu

### Kiểm tra danh sách lựa chọn tuyến

Trang `/quan-tri/chuyen-xe`: **Lọc tuyến** chỉ hiển thị tên tuyến, mỗi tên một lựa chọn, sắp xếp A–Z theo tiếng Việt. Lựa chọn gộp các mã tuyến cùng tên để trả chuyến của mọi nhà xe phù hợp; tên nhà xe được xem trong bảng kết quả. Không xóa/gộp bản ghi PostgreSQL. **Tuyến xe** khi tạo chuyến vẫn chọn tuyến cụ thể thuộc nhà xe để gán đúng phương tiện và nhân sự.

Chọn TP. Hồ Chí Minh – Cần Thơ rồi lọc: admin thấy cả FUTA và Thành Bưởi, tổng và phân trang tính trên toàn bộ nhóm; điều hành chỉ thấy chuyến thuộc nhà xe mình. Tạo chuyến chọn tuyến nào thì nhà xe vận hành và danh sách xe dự kiến phải theo tuyến đó. Trang quản lý tuyến sắp xếp tỉnh/thành, bến và tuyến theo tên cả lần tải đầu và sau khi lọc/lưu.

Kiểm tra tự động phần lựa chọn: `node tests/frontend/management_options_check.cjs` (đã đạt); frontend production build và TypeScript đã đạt sau thay đổi.

API danh sách chuyến nhận `maTuyen` là một mã hoặc nhiều mã phân cách bằng dấu phẩy (tối đa 100 mã). Backend kiểm từng mã, lọc trước khi đếm/phân trang và giữ điều kiện phạm vi nhà xe. Bộ HTTP chuyến kiểm nhóm tuyến, phân trang, mã không hợp lệ và quyền điều hành.

Đã kiểm bản cập nhật trên web: bộ lọc có 14 tên tuyến duy nhất theo A–Z; chọn TP. Hồ Chí Minh – Cần Thơ trả 30 chuyến của FUTA và Thành Bưởi trong bảng kết quả. Bộ HTTP chuyến đạt 123 kiểm tra và đối chiếu dữ liệu trước/sau khớp.

| Dữ liệu | PostgreSQL | Giao diện đọc |
| --- | --- | --- |
| Hồ sơ nhân viên / bằng lái / trạng thái | `nhan_vien` | Quản lý nhân viên, hồ sơ nhân viên riêng |
| Tài khoản / quyền / mật khẩu hash | `nguoi_dung`, `phien_dang_nhap` | Đăng nhập, tài khoản, quản trị tài khoản |
| Lịch phân công | `phan_cong_chuyen_xe` liên kết `nhan_vien`, `chuyen_xe` | Lịch nhân viên, nhân sự chuyến |
| Bến/tuyến/điểm dừng | `ben_xe`, `tuyen_xe`, `diem_dung`, `tinh_thanh` | Quản lý tuyến, tìm chuyến, chi tiết chuyến |
| Xe/loại/ghế | `xe`, `loai_xe`, `ghe` | Quản lý xe, sơ đồ ghế của chuyến |
| Đơn/giữ ghế/vé/thanh toán | `don_dat_ve`, `giu_cho`, `ve_xe`, `thanh_toan` | Checkout, lịch sử đơn/vé; qua API theo quyền |

Form → API backend → validation/quyền/transaction → PostgreSQL `datvexe`. Docker lưu dữ liệu trong volume `postgres_data`; dừng/chạy container không tự xóa nó. File seed chỉ là dữ liệu khởi tạo, thao tác trên web không sửa file SQL trong Git. Máy thành viên có database riêng; push code không tự đồng bộ bản ghi mới trên các máy. Nhân viên tạo được bằng UI sẽ thấy ngay ở danh sách theo quyền, và vẫn còn sau reload nếu API lưu thành công.

Máy hiện tại có PostgreSQL Windows ở cổng 5432. `.env` local đặt `POSTGRES_PORT=5433` để PostgreSQL Docker không trùng cổng. Kết nối từ pgAdmin/DBeaver dùng `localhost:5433`, database `datvexe`; backend trong Docker vẫn kết nối `postgres:5432`. `.env` không được commit. Khi máy thành viên có PostgreSQL Windows, áp dụng cùng cấu hình cổng để tránh đọc nhầm database.

Kiểm thử PostgreSQL T22 dùng database/container tạm riêng. Trên Windows, đặt JVM `-Duser.timezone=Asia/Ho_Chi_Minh`; alias `Asia/Saigon` có thể bị PostgreSQL từ chối khi JDBC mở kết nối.
