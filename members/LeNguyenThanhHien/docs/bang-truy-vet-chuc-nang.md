# Bảng truy vết chức năng — Lê Nguyễn Thanh Hiền

Đối chiếu source nhánh `develop` tại commit `0c2828b364366cd036e0a16ddc4a71a997f7cb16`.
Tài liệu mô tả triển khai hiện có, không thay đổi API, Entity hoặc database.

## Cách đọc

Mỗi hàng đi từ thao tác giao diện đến Controller → Service → truy cập dữ liệu → Entity → bảng DB → test.
Tên phương thức trong ô Controller/Service thuộc file có liên kết ở danh mục cuối trang.
Các bảng liệt kê gồm bảng chính và bảng được đọc để kiểm tra quy tắc/quyền, không có nghĩa mọi bảng đều bị ghi.
Các bảng xác thực phiên dùng chung không được lặp lại ở từng hàng.

Entity nghiệp vụ dùng chung tại [Entities.java](../../../shared/backend/src/main/java/com/datvexe/domain/Entities.java).
Không sao chép Entity về thư mục thành viên. Với native SQL, ghi tên bảng thật và nêu rõ nếu không có Entity.
Test được dẫn là bằng chứng bao phủ trong source, **không phải kết quả chạy mới** hoặc cam kết mỗi test kiểm tra mọi nhánh của hàng.

## Truy vết từ giao diện tới dữ liệu và test

| Chức năng | Frontend | Controller | Service | Repository hoặc JPA/SQL trực tiếp | Entity | Bảng database | Test liên quan và trường hợp |
|---|---|---|---|---|---|---|---|
| Tỉnh/thành, bến xe | [FE tuyến](../../../members/LeNguyenThanhHien/frontend/src/routes/components/RouteManagement.tsx) | `RouteTripController.handleCities/handleStations` | `RouteTripService: create/update/get/list/deleteTinhThanh; create/update/get/list/deleteBenXe` | JPA trực tiếp: find, persist, remove, JPQL kiểm tra tham chiếu | TinhThanh, BenXe; TuyenXe, ChuyenXe để kiểm tra ràng buộc | `tinh_thanh, ben_xe, tuyen_xe, chuyen_xe` | [RouteTripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/RouteTripServiceTest.java)<br>citiesCanBeSearchedUpdatedAndCannotBeDeletedWhileReferenced; stationsCanBeFilteredByProvinceNameAndStatus |
| Tạo/sửa/xem/lọc/xóa tuyến | [FE tuyến](../../../members/LeNguyenThanhHien/frontend/src/routes/components/RouteManagement.tsx) | `RouteTripController.handleRoutes` | `RouteTripService.createTuyenXe/updateTuyenXe/getTuyenXe/listTuyenXe/deleteTuyenXe/setTramDauCuoi/setTrangThaiTuyenXe` | JPA trực tiếp: find, persist, remove, createQuery; hasTrips kiểm tra chuyến tham chiếu | TuyenXe, BenXe, TinhThanh, NhaXe, ChuyenXe, DiemDung | `tuyen_xe, ben_xe, tinh_thanh, nha_xe, chuyen_xe, diem_dung` | [RouteTripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/RouteTripServiceTest.java)<br>stationAndRouteValidationAndDeleteProtection; routeAndStopsCannotBeChangedAfterTripsExistButStatusCanChange |
| Thêm/sửa/xóa/sắp xếp điểm dừng | [FE tuyến](../../../members/LeNguyenThanhHien/frontend/src/routes/components/RouteManagement.tsx) | `RouteTripController.handleRoutes` | `RouteTripService.addDiemDung/updateDiemDung/deleteDiemDung/reorderDiemDung` | JPA trực tiếp: find/persist/remove; stops, renumber, park; kiểm tra tuyến đã có chuyến | DiemDung, TuyenXe, ChuyenXe | `diem_dung, tuyen_xe, chuyen_xe` | [RouteTripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/RouteTripServiceTest.java)<br>stopsCanBeInsertedReorderedAndRemovedWithoutDuplicatePositions; invalidStopOperationsDoNotChangeExistingOrder |
| Danh mục loại xe | [FE xe](../../../members/LeNguyenThanhHien/frontend/src/vehicles/components/VehicleManagement.tsx) | `VehicleController.handleResource` | `VehicleService.saveType/get/filtered/delete` | LoaiXeRepository kế thừa JpaRepository; JPA/quan hệ danhSachXe kiểm tra loại đã sử dụng | LoaiXe, Xe | `loai_xe, xe` | [VehicleTask10Test](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/VehicleTask10Test.java)<br>typeAndVehicleValidationUniquePlatesAndFilters |
| Tạo/cập nhật/lọc xe, đăng kiểm | [FE xe](../../../members/LeNguyenThanhHien/frontend/src/vehicles/components/VehicleManagement.tsx) | `VehicleController.handleResource` | `VehicleService.saveVehicle/get/filtered; VehicleRules.inspection` | XeRepository, LoaiXeRepository; JPA trực tiếp tìm NhaXe, kiểm tra trùng biển; generate tạo ghế cùng giao dịch | Xe, LoaiXe, NhaXe, Ghe; ChuyenXe để bảo vệ lịch vận hành | `xe, loai_xe, nha_xe, ghe, chuyen_xe` | [VehicleTask10Test](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/VehicleTask10Test.java)<br>typeAndVehicleValidationUniquePlatesAndFilters; registrationUpdateAfterCompletedTripPreservesTicketsAndSeatReferences |
| Sinh ghế, CRUD ghế, lưu toàn sơ đồ | [FE xe](../../../members/LeNguyenThanhHien/frontend/src/vehicles/components/VehicleManagement.tsx) | `VehicleController.handleSeats; nhánh so-do-ghe trong service` | `VehicleService.seats/createSeat/updateSeat/deleteSeat/saveLayout` | XeRepository.lock, GheRepository; JPA persist/remove và JPQL kiểm tra lịch sử vé/giữ chỗ | Xe, LoaiXe, Ghe, ChuyenXe, VeXe, GiuCho | `xe, loai_xe, ghe, chuyen_xe, ve_xe, giu_cho` | [VehicleTask10Test](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/VehicleTask10Test.java)<br>layoutRejectsWrongCountsDuplicatesForeignIdsAndInvalidFloorsWithoutPartialSave; twoVehiclesMayUseSameLocalCodeAndLabelSwapIsAtomic |
| Tạo/sửa/xóa chuyến nháp | [FE chuyến](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripManagement.tsx) | `TripController.handleCollection/handleTrip` | `TripService.create/update/delete; apply; VehicleRules và AssignmentRules khi có xe/lịch` | JPA find/persist/remove; ChuyenXeRepository.lock khi sửa/xóa; JPA kiểm tra lịch xe/nhân viên | ChuyenXe, TuyenXe, BenXe, NhaXe, Xe; PhanCongChuyenXe, NhanVien, VeXe, GiuCho khi kiểm tra lịch/lịch sử | `chuyen_xe, tuyen_xe, ben_xe, nha_xe, xe, phan_cong_chuyen_xe, nhan_vien, ve_xe, giu_cho` | [TripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/TripServiceTest.java)<br>draftCanLackVehicleAndCrewButCannotBeSearchedOrSold; invalidReferencesPricesAndArrivalRollbackWithoutChangingRoute; draftTimeUpdatesRecheckVehicleAndEmployeeSchedulesAndRollback |
| Danh sách quản lý chuyến, phân trang | [FE chuyến](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripManagement.tsx) | `TripController.handleCollection` | `TripService.list` | ChuyenXeRepository.search/count với Filters; phân quyền/phạm vi trong Service | ChuyenXe, TuyenXe, NhaXe, Xe; dữ liệu phân công được đưa vào view | `chuyen_xe, tuyen_xe, nha_xe, xe, phan_cong_chuyen_xe, nhan_vien` | [TripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/TripServiceTest.java)<br>authenticatedScopeIsEnforcedForManagementNotClientIds |
| Kiểm tra điều kiện và mở bán | [FE chuyến](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripManagement.tsx) | `TripController.handleAction` | `TripService.eligibility/open; TripSaleRules.inspect/ensureOpenable; VehicleRules.ready; AssignmentRules.readyForOperation/validateVehicleSchedule` | ChuyenXeRepository.lock; JPA khóa/refresh tuyến, bến, xe và truy vấn lịch/phân công | ChuyenXe, TuyenXe, BenXe, NhaXe, Xe, LoaiXe, Ghe, PhanCongChuyenXe, NhanVien | `chuyen_xe, tuyen_xe, ben_xe, nha_xe, xe, loai_xe, ghe, phan_cong_chuyen_xe, nhan_vien` | [TripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/TripServiceTest.java)<br>actualT11AssignmentFlowAndIdempotentOpening; vehicleMaintenanceInspectionAndInvalidLayoutBlockOpening; cutoffReachedDuringValidationStillRollsBackOpening |
| Tìm chuyến, số ghế còn | [FE tìm](../../../members/NguyenTheAn/frontend/src/trip-search/pages/SearchResultsPage.tsx) | `RouteTripController.handleTrips` | `RouteTripService.searchChuyenXe/searchView; TripSaleRules.ensureSellable` | ChuyenXeRepository.findForSearch; JPA cityOf và JPQL đếm ghế/vé/giữ chỗ theo chuyến | ChuyenXe, TuyenXe, BenXe, TinhThanh, NhaXe, Xe, LoaiXe, Ghe, VeXe, GiuCho; nhân sự qua TripSaleRules | `chuyen_xe, tuyen_xe, ben_xe, tinh_thanh, nha_xe, xe, loai_xe, ghe, ve_xe, giu_cho, phan_cong_chuyen_xe, nhan_vien` | [RouteTripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/RouteTripServiceTest.java)<br>emptySearchWrongDirectionDateAndDepartureOrdering; searchAcceptsCityStationAndLegacyCodesWithoutChangingSaleRules |
| Chi tiết chuyến, ghế theo chuyến | [FE chi tiết](../../../members/NguyenTheAn/frontend/src/booking/components/TripDetails.tsx) | `RouteTripController.handleTrips` | `RouteTripService.getChuyenXe; VehicleService.tripSeats; VehicleRules.availability` | JPA find/query; GheRepository.byVehicle; quy tắc bán kiểm tra theo chuyến | ChuyenXe, TuyenXe, DiemDung, BenXe, TinhThanh, NhaXe, Xe, LoaiXe, Ghe, VeXe, GiuCho; nhân sự qua quy tắc bán | `chuyen_xe, tuyen_xe, diem_dung, ben_xe, tinh_thanh, nha_xe, xe, loai_xe, ghe, ve_xe, giu_cho, phan_cong_chuyen_xe, nhan_vien` | [RouteTripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/RouteTripServiceTest.java)<br>publicDetailAndAvailabilityUseCurrentTripSpecificSeats |
| Bắt đầu chuyến | [FE chuyến](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripManagement.tsx) | `TripController.handleAction: bat-dau` | `TripService.transition/authorizeLifecycle/history; VehicleRules.ready; AssignmentRules.readyForOperation` | ChuyenXeRepository.lock; JPA kiểm tra xe/phân công; native SQL ghi lịch sử | ChuyenXe và entity vận hành; lịch sử không có Entity riêng | `chuyen_xe, tuyen_xe, nha_xe, xe, loai_xe, ghe, phan_cong_chuyen_xe, nhan_vien`; `trip_lifecycle_history` | [TripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/TripServiceTest.java)<br>lifecycleRejectsInvalidStatesAndDoesNotDuplicateHistory; lifecycleRechecksVehicleAndRejectsCustomer; assignedDriverCanOperateButCannotCancelOrOperateAnotherTrip |
| Hoàn tất chuyến | [FE chuyến](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripManagement.tsx) | `TripController.handleAction: hoan-thanh` | `TripService.transition/authorizeLifecycle/history` | ChuyenXeRepository.lock; kiểm tra quyền JPA; native SQL ghi lịch sử; không tự đánh dấu hành khách đã lên xe | ChuyenXe, PhanCongChuyenXe, NhanVien, NhaXe/TuyenXe cho quyền; lịch sử không có Entity riêng | `chuyen_xe, phan_cong_chuyen_xe, nhan_vien, nha_xe, tuyen_xe`; `trip_lifecycle_history` | [TripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/TripServiceTest.java)<br>lifecycleRejectsInvalidStatesAndDoesNotDuplicateHistory; completingDoesNotMarkUninspectedPassengersAsBoarded |
| Hủy chuyến, công việc hoàn/thông báo, báo cáo/thử lại | [FE chuyến](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripManagement.tsx) | `TripController.handleAction: huy/vong-doi/xu-ly-hoan` | `TripService.cancel/transition/cancelAffectedOrders/lifecycleReport/retryCancellationWork; HoanVeService và TripCancellationEmailDispatcher sau commit` | ChuyenXeRepository.lock; JPA khóa đơn/vé/giữ chỗ/thanh toán; native SQL ghi lịch sử, công việc, thông báo và đọc yêu cầu hoàn | ChuyenXe, DonDatVe, VeXe, GiuCho, ThanhToan; quyền qua TuyenXe/NhaXe. Các bảng công việc/lịch sử không có Entity riêng | `chuyen_xe, tuyen_xe, nha_xe, don_dat_ve, ve_xe, giu_cho, thanh_toan`; `trip_lifecycle_history, trip_cancellation_work, trip_cancellation_notification, refund_request` | [TripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/TripServiceTest.java)<br>cancellingReleasesHoldsWithoutAnOrderAndRequiresReason; assignedDriverCanOperateButCannotCancelOrOperateAnotherTrip |
| Danh sách hành khách và vé | [FE hành khách](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripPassengerModal.tsx) | `RouteTripController.handleTrips: hanh-khach` | `TripPassengerService.list` | JPA trực tiếp find/JPQL lấy VeXe và kiểm tra phân công/quyền; HoanVeRepository.ticketBlocked truy vấn trạng thái hoàn bằng SQL | ChuyenXe, TuyenXe, NhaXe, Xe, PhanCongChuyenXe, NhanVien, VeXe; bảng hoàn qua SQL | `chuyen_xe, tuyen_xe, nha_xe, xe, phan_cong_chuyen_xe, nhan_vien, ve_xe, refund_request` | [TripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/TripServiceTest.java)<br>Chưa có test chuyên biệt TripPassengerService trong 7 lớp test của thành viên; các test vòng đời không thay thế kiểm thử danh sách hành khách. |

## Endpoint để lần theo Controller

Mọi đường dẫn dưới đây có tiền tố `/api`. Request ghi chịu kiểm tra phiên, CSRF và phạm vi nhà xe theo cấu hình xác thực chung.

| Nhóm | Endpoint chính |
|---|---|
| Tỉnh/bến/tuyến | `/tinh-thanh`, `/ben-xe`, `/tuyen-xe`; chi tiết `/{id}`; tuyến có `/tram-dau-cuoi`, `/trang-thai`, `/diem-dung` và `/diem-dung/sap-xep` |
| Loại xe/xe/ghế | `/loai-xe`, `/xe`, `/xe/{id}/ghe`, `/xe/{id}/ghe/sinh`, `/xe/{id}/so-do-ghe` |
| Quản lý chuyến | `/quan-ly/chuyen-xe`, `/quan-ly/chuyen-xe/{id}` |
| Mở bán/vòng đời | `/quan-ly/chuyen-xe/{id}/dieu-kien-mo-ban`, `/mo-ban`, `/bat-dau`, `/hoan-thanh`, `/huy`, `/vong-doi`, `/xu-ly-hoan` (các hậu tố cùng thuộc chuyến quản lý) |
| Công khai/tìm chuyến | `/chuyen-xe?from=…&to=…&date=YYYY-MM-DD`, `/chuyen-xe/{id}`, `/chuyen-xe/{id}/ghe` |
| Hành khách có phân quyền | `/chuyen-xe/{id}/hanh-khach` |

API công khai `/chuyen-xe` và API quản lý `/quan-ly/chuyen-xe` là hai luồng khác nhau.
`RouteTripService` còn các thao tác tương thích cũ; giao diện quản lý hiện gọi `TripController`/`TripService`.
API hủy công khai cũ bị từ chối và hướng sang API quản lý có lý do hủy.

## Ánh xạ Entity → bảng

Tất cả Entity ở bảng này nằm trong `Entities.java` dùng chung.

| Entity | Bảng |
|---|---|
| TinhThanh / BenXe | `tinh_thanh` / `ben_xe` |
| TuyenXe / DiemDung / ChuyenXe | `tuyen_xe` / `diem_dung` / `chuyen_xe` |
| NhaXe / NhanVien / PhanCongChuyenXe | `nha_xe` / `nhan_vien` / `phan_cong_chuyen_xe` |
| LoaiXe / Xe / Ghe | `loai_xe` / `xe` / `ghe` |
| GiuCho / DonDatVe / VeXe / ThanhToan | `giu_cho` / `don_dat_ve` / `ve_xe` / `thanh_toan` |

`trip_lifecycle_history`, `trip_cancellation_work`, `trip_cancellation_notification` được tạo trong
[013_trip_lifecycle.sql](../../../database/migrations/013_trip_lifecycle.sql), thao tác bằng native SQL trong `TripService`.
`refund_request` thuộc phần hoàn vé, được đọc khi lập báo cáo; không suy ra có Entity tương ứng trong `Entities.java`.

## Test bổ sung và giới hạn

- PostgreSQL thật: `PostgresTask10Test.fullLayoutSwapsLabelsAndRejectsBadLastEntryAtomically`,
  `concurrentSamePlateAcrossTypesCreatesOnlyOneCompleteVehicle`;
  `PostgresTripTest.realT11AssignmentCanPublishDraftAndDuplicateOpeningIsStable`,
  `conflictingSimultaneousPublishSerializesUnderDatabaseLocks`, `failedPublishAndUpdateRollbackOnPostgres`;
  `PostgresVehicleTest.concurrentAssignmentsCannotDoubleBookDriver`.
- Các lớp PostgreSQL chỉ chạy khi có system property `vehicle.pg.url`; bỏ qua không được tính đạt.
- HTTP tích hợp: `api_smoke.py`, `vehicle_management_smoke.py`, `trip_sale_smoke.py` ở danh mục dưới.
  Dùng database kiểm thử riêng; không chạy fixture lên dữ liệu cần giữ.
- Luồng hoàn tiền và email khi hủy phụ thuộc module của thành viên khác; test vòng đời không chứng minh
  đã hoàn tiền qua ngân hàng hoặc gửi email thật.
- Chưa tìm thấy test chuyên biệt cho danh sách hành khách trong phạm vi test của thành viên.
- Xem [báo cáo kiểm thử](../testing/README.md) để biết kết quả lịch sử;
  [sơ đồ nghiệp vụ](so-do-luong-nghiep-vu.md) để trình bày các nhánh thành công/thất bại.

## Danh mục file để mở code

- [FE tuyến](../../../members/LeNguyenThanhHien/frontend/src/routes/components/RouteManagement.tsx)
- [FE xe](../../../members/LeNguyenThanhHien/frontend/src/vehicles/components/VehicleManagement.tsx)
- [FE chuyến](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripManagement.tsx)
- [FE hành khách](../../../members/LeNguyenThanhHien/frontend/src/trips/components/TripPassengerModal.tsx)
- [FE tìm](../../../members/NguyenTheAn/frontend/src/trip-search/pages/SearchResultsPage.tsx)
- [Form tìm](../../../members/NguyenTheAn/frontend/src/booking/components/SearchForm.tsx)
- [API tìm](../../../members/NguyenTheAn/frontend/src/booking/lib/api.ts)
- [FE chi tiết](../../../members/NguyenTheAn/frontend/src/booking/components/TripDetails.tsx)
- [RouteTripController](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/controller/RouteTripController.java)
- [VehicleController](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/controller/VehicleController.java)
- [TripController](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/controller/TripController.java)
- [RouteTripService](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/service/RouteTripService.java)
- [VehicleService](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/service/VehicleService.java)
- [VehicleRules](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/service/VehicleRules.java)
- [TripService](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/service/TripService.java)
- [TripSaleRules](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/service/TripSaleRules.java)
- [TripPassengerService](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/service/TripPassengerService.java)
- [ChuyenXeRepository](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/repository/ChuyenXeRepository.java)
- [XeRepository](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/repository/XeRepository.java)
- [LoaiXeRepository](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/repository/LoaiXeRepository.java)
- [GheRepository](../../../members/LeNguyenThanhHien/backend/src/main/java/com/datvexe/repository/GheRepository.java)
- [AssignmentRules](../../../members/TranDangThiKimCuong/backend/src/main/java/com/datvexe/service/AssignmentRules.java)
- [Entity dùng chung](../../../shared/backend/src/main/java/com/datvexe/domain/Entities.java)
- [SQL vòng đời](../../../database/migrations/013_trip_lifecycle.sql)
- [RouteTripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/RouteTripServiceTest.java)
- [VehicleServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/VehicleServiceTest.java)
- [VehicleTask10Test](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/VehicleTask10Test.java)
- [TripServiceTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/TripServiceTest.java)
- [PostgresTask10Test](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/PostgresTask10Test.java)
- [PostgresTripTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/PostgresTripTest.java)
- [PostgresVehicleTest](../../../members/LeNguyenThanhHien/backend/src/test/java/com/datvexe/service/PostgresVehicleTest.java)
- [api_smoke](../../../tests/backend/api_smoke.py)
- [vehicle_management_smoke](../../../tests/backend/vehicle_management_smoke.py)
- [trip_sale_smoke](../../../tests/backend/trip_sale_smoke.py)

Giao diện quản trị được gắn tại `apps/frontend/app/quan-tri/tuyen-duong/page.tsx`,
`apps/frontend/app/quan-tri/xe/page.tsx`, `apps/frontend/app/quan-tri/chuyen-xe/page.tsx`.
Frontend tìm chuyến thuộc Nguyễn Thế An; `AssignmentRules` thuộc Trần Đặng Thị Kim Cương.
Các phụ thuộc này được dẫn để giải thích tích hợp, không nhận là source riêng của Hiền.

- [HoanVeRepository](../../../members/NguyenGiaBao/backend/src/main/java/com/datvexe/repository/HoanVeRepository.java) — phụ thuộc module Nguyễn Gia Bảo.

- [HoanVeService](../../../members/NguyenGiaBao/backend/src/main/java/com/datvexe/service/HoanVeService.java) — phụ thuộc module Nguyễn Gia Bảo.

- [TripCancellationEmailDispatcher](../../../members/NguyenGiaBao/backend/src/main/java/com/datvexe/email/TripCancellationEmailDispatcher.java) — phụ thuộc module Nguyễn Gia Bảo.
