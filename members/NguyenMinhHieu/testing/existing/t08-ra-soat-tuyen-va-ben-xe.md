# T08 — Quản lý tỉnh thành, bến xe, tuyến và điểm dừng

Triển khai dựa trên schema và entity T03 hiện có; tích hợp bảo vệ API của T07/T07.1. Hợp đồng endpoint và quyền: [t08-quan-ly-tuyen-va-ben-xe.md](../../../../docs/api/t08-quan-ly-tuyen-va-ben-xe.md).

## Phạm vi đã làm

- API tỉnh/thành: danh sách/tìm theo mã hoặc tên, chi tiết, tạo, sửa; chỉ ADMIN được thay đổi. Xóa bị từ chối khi còn bến tham chiếu. Entity hiện không có cột trạng thái tỉnh/thành nên không bổ sung trạng thái.
- API bến: danh sách theo tỉnh/trạng thái/từ khóa, chi tiết, tạo, sửa trạng thái; tỉnh tham chiếu phải tồn tại. Xóa bị từ chối nếu tuyến đang tham chiếu. Không sửa tên/địa chỉ/tỉnh của bến đã được chuyến sử dụng.
- API tuyến: lọc theo bến đi/đến, nhà xe, trạng thái; CRUD; quản lý trạng thái; thêm, sửa, xóa, sắp xếp điểm dừng theo giao dịch.
- Backend xác nhận phạm vi nhà xe từ phiên, không tin `maNhaXe` do client gửi. Nhà xe chỉ thấy và sửa tuyến thuộc phạm vi; danh mục tỉnh/bến chỉ ADMIN quản lý.
- Tuyến cần bến khác nhau, bến đang hoạt động, thời lượng dương; điểm dừng tùy chọn, thứ tự liền mạch, thời gian dừng không âm. Chuyến mới không được tạo từ tuyến hoặc bến ngừng hoạt động.
- Tuyến đã có chuyến không bị xóa cứng và cấu hình/điểm dừng không đổi; được đổi trạng thái khai thác, giữ nguyên chuyến cũ.
- UI tại `/quan-tri/tuyen-duong`; ADMIN quản lý tỉnh/bến/tuyến, NHA_XE quản lý tuyến trong phạm vi mình và sắp xếp điểm dừng.

## Kết quả kiểm thử

Chạy lại ngày 11/10/2026 trên nhánh `feat/t08-route-station-management`, dùng WAR vừa được Maven tạo và frontend production build trỏ đúng backend kiểm thử.

- Maven `verify`: `BUILD SUCCESS`, 54 test, 0 lỗi, 0 thất bại; 50 test thực thi, 4 test dành riêng cho bộ seed T06 được bỏ qua theo cấu hình mặc định. Các test PostgreSQL được bật với database kiểm thử riêng `t07_t06_review`. Dùng Maven/thư viện đã lưu trong môi trường nên lần này không bị chặn bởi DNS Maven Central.
- API smoke: 5 nhóm kịch bản, 93 phản hồi HTTP đã kiểm tra; đạt. Bao gồm CRUD tuyến/bến, điểm dừng, tạo chuyến, bộ lọc, trạng thái và lỗi nghiệp vụ.
- Browser smoke: đạt trên Next.js + Tomcat + PostgreSQL; tạo tỉnh, hai bến, tuyến không có điểm dừng ban đầu, đổi trạng thái, thêm/sửa điểm dừng và sắp xếp.
- Frontend production build: đạt; TypeScript và static generation thành công.
- Frontend lint: 0 lỗi, còn 3 cảnh báo unused variable có sẵn trong `lib/api.ts` và `lib/mock-data.ts`.
- Kiểm thử sửa giao diện: đạt kiểm tra tiếng Việt cho các trường bắt buộc trên bốn biểu mẫu, trường tùy chọn được bỏ trống, thông báo thành công/lỗi biến mất khi chuyển tab hoặc bắt đầu sửa, trạng thái trong danh sách chỉ hiển thị và cập nhật trạng thái qua biểu mẫu sửa tuyến. Frontend build và lint đạt sau thay đổi.
- `git diff --check`, kiểm tra cú pháp Python và Node: đạt.

## Đối chiếu các trường hợp của đề bài

| Yêu cầu | Bằng chứng kiểm thử | Kết quả |
| --- | --- | --- |
| Tạo tỉnh hợp lệ; từ chối mã trùng | API smoke: `setup`, `crud`, `validation` | Đạt |
| CRUD/tìm tỉnh; không xóa tỉnh có bến | `citiesCanBeSearchedUpdatedAndCannotBeDeletedWhileReferenced`, API smoke | Đạt |
| Tạo bến thuộc tỉnh hợp lệ; từ chối tỉnh không tồn tại | `invalidStationRouteAndTripDataAreRejectedBeforePersistence`, API smoke | Đạt |
| CRUD/lọc bến theo tỉnh, trạng thái, tên | `stationAndRouteCanBeUpdatedAndDeletedWhenUnused`, `stationsCanBeFilteredByProvinceNameAndStatus`, API smoke | Đạt |
| Bến đi và đến khác nhau; từ chối bến giống nhau | `stationAndRouteValidationAndDeleteProtection`, API smoke | Đạt |
| Tuyến không có điểm dừng; tuyến nhiều điểm đúng thứ tự | `stopsCanBeInsertedReorderedAndRemovedWithoutDuplicatePositions`, browser smoke | Đạt |
| Sửa/xóa điểm dừng và sắp xếp nhất quán | API smoke: `stops`, browser smoke | Đạt |
| Từ chối thứ tự sai, danh sách trùng/thiếu ID; không lưu dở dang | `invalidStopOperationsDoNotChangeExistingOrder`, API smoke: `stops` | Đạt |
| Từ chối thời gian dừng âm | `invalidStopOperationsDoNotChangeExistingOrder`, API smoke: `stops` | Đạt |
| Thời lượng tuyến hợp lệ, tách khỏi ngày/giờ chuyến | `routeNeedsDurationAndDepartureTimeAndFailedCreationRollsBack`, `tripRescheduleRechecksVehicleAndRollsBackFailedChanges`, API smoke | Đạt |
| Tỉnh đi/đến lấy từ bến tương ứng | API smoke: `search_and_status`, `emptySearchWrongDirectionDateAndDepartureOrdering` | Đạt |
| Tuyến/bến ngừng hoạt động không tạo được chuyến mới | `inactiveRoutesRejectCreationAndHideExistingTrips`, `inactiveTerminalCannotBeUsedToCreateANewTrip`, API smoke | Đạt |
| Không xóa tuyến có chuyến; không xóa bến có tuyến | `routeAndStopsCannotBeChangedAfterTripsExistButStatusCanChange`, `stationAndRouteValidationAndDeleteProtection`, API smoke | Đạt |
| Không sửa hành trình làm sai lịch sử chuyến/vé | `routeAndStopsCannotBeChangedAfterTripsExistButStatusCanChange`, API smoke: `trips`; chặn ngay từ khi tuyến có chuyến, kể cả trước khi phát hành vé | Đạt |
| Nhà xe A không sửa tuyến nhà xe B; không giả mạo nhà xe trong body | `operatorScopeUsesActualDatabaseRelationships`, `serviceGuardRejectsCrossOperatorUrlAndBodyBeforeWriting`, `operatorCanCreateRouteOnlyInsideItsAuthenticatedDatabaseScope` | Đạt |
| Chưa xác thực hoặc không đủ quyền bị từ chối | `roleAndAuthenticationCannotBeForged`, `onlyAdminCanChangeSharedGeographicCatalogs` | Đạt |
| Backend/frontend tích hợp; hiển thị thành công và dữ liệu đúng | Browser smoke, frontend production build và lint | Đạt |

Thứ tự đã có khi thêm điểm dừng là thao tác chèn: hệ thống dời các điểm cũ để vẫn duy nhất và liền mạch. Danh sách sắp xếp có ID trùng hoặc không đủ điểm bị từ chối. Chi tiết quy tắc nằm trong tài liệu API.

Để chạy lại browser smoke, PostgreSQL cần có schema/seed T07 và Tomcat cần chạy ở cổng 8081. Trong `apps/frontend`, build và chạy Next với `BACKEND_URL=http://127.0.0.1:8081`; sau đó từ thư mục gốc chạy `FRONTEND_URL=http://localhost:3003 DB_NAME=datvexe node tests/frontend/route_management_smoke.cjs`. Script tạo tài khoản quản trị kiểm thử bằng đăng ký rồi nâng vai trò trong database cục bộ, và tự dọn các bản ghi đã tạo. T01 chưa có ma trận quyền chính thức trong nhánh. Quyền hiện dùng ADMIN cho danh mục địa lý và ADMIN/NHA_XE theo phạm vi cho tuyến; cần đối chiếu ma trận T01 khi nhóm cung cấp bản đã duyệt.

T01 chưa có ma trận quyền chính thức trong nhánh. Quyền hiện dùng ADMIN cho danh mục địa lý và ADMIN/NHA_XE theo phạm vi cho tuyến; cần đối chiếu ma trận T01 khi nhóm cung cấp bản đã duyệt.
