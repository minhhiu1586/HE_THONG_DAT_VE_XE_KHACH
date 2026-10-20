# Sơ đồ luồng nghiệp vụ — Lê Nguyễn Thanh Hiền

Đối chiếu nhánh `develop`, commit `0c2828b364366cd036e0a16ddc4a71a997f7cb16`.
Mở trên GitHub để xem sơ đồ Mermaid; trong VS Code cần trình xem Markdown hỗ trợ Mermaid.
[Bảng truy vết chức năng](bang-truy-vet-chuc-nang.md) dẫn tới Frontend, Controller, Service, dữ liệu và test của từng luồng.

## Quy ước chung

Các luồng ghi dữ liệu kiểm tra đăng nhập, quyền và CSRF tại tầng xác thực chung,
sau đó kiểm tra phạm vi tài nguyên ở Service. Lỗi xác thực/quyền trả 401/403;
CSRF sai hoặc thiếu bị từ chối theo bộ lọc chung. Không coi việc ẩn nút ở frontend là phân quyền.
Lỗi nghiệp vụ được trả qua `ApiErrors` với thông báo để giao diện hiển thị bằng `errorMessage`.
Các thông báo trong dấu ngoặc kép dưới đây được lấy từ code, là ví dụ đại diện,
không phải danh sách đầy đủ mọi lỗi. Lỗi trong transaction làm rollback; lỗi sau commit được xử lý theo từng luồng.

## 1. Quản lý tuyến và điểm dừng

Frontend: `RouteManagement.tsx`; Controller: `RouteTripController.handleRoutes`;
Service: `RouteTripService`. Endpoint gốc `/api/tuyen-xe`.

```mermaid
flowchart TD
    A[Người quản lý mở Quản lý tuyến] --> B[Tải tuyến, bến và nhà xe trong phạm vi quyền]
    B --> C{Thao tác}
    C -->|Tạo hoặc sửa| D[Nhập mã, tên, bến đầu cuối, thời gian, giá và nhà xe]
    D --> E{Đầu vào, bến và quyền hợp lệ?}
    E -->|Không| F[Trả 400, 403, 404 hoặc 409 và thông báo lỗi; không lưu]
    E -->|Có| G{Sửa cấu hình tuyến đã có chuyến?}
    G -->|Có| F
    G -->|Không| H[Lưu trong transaction; commit]
    H --> I[201 khi tạo hoặc 200 khi sửa; tải lại danh sách]
    C -->|Điểm dừng| J[Thêm, sửa, xóa hoặc sắp xếp điểm dừng]
    J --> K{Tuyến chưa có chuyến và danh sách hợp lệ?}
    K -->|Không| F
    K -->|Có| L[Lưu thứ tự điểm dừng; commit; trả kết quả]
    C -->|Xóa tuyến| M{Tuyến chưa có chuyến tham chiếu?}
    M -->|Không| F
    M -->|Có| N[Xóa trong transaction; trả 204]
    C -->|Đổi trạng thái| O[Kiểm tra quy tắc trạng thái riêng; lưu và trả 200]
    O -->|Bị từ chối| F
```

Ví dụ lỗi: 400 “Trạm khởi hành và trạm đến phải khác nhau”; 409
“Bến khởi hành và bến đến phải đang hoạt động để cấu hình tuyến”.
Đổi trạng thái là thao tác riêng; không suy ra tuyến đã có chuyến thì mọi thao tác đều bị cấm.
Kiểm thử: `RouteTripServiceTest.stationAndRouteValidationAndDeleteProtection`,
`routeAndStopsCannotBeChangedAfterTripsExistButStatusCanChange`,
`invalidStopOperationsDoNotChangeExistingOrder`.

## 2. Tạo xe và sơ đồ ghế

Frontend: `VehicleManagement.tsx`; Controller: `VehicleController`;
Service: `VehicleService`. Tạo xe dùng `POST /api/xe`, lưu sơ đồ dùng
`PUT /api/xe/{maXe}/so-do-ghe`.

```mermaid
flowchart TD
    A[Người quản lý chọn loại xe, nhà xe và nhập thông tin xe] --> B[Kiểm tra phiên, phạm vi và dữ liệu]
    B --> C{Mã, biển số, loại xe, sức chứa và đăng kiểm hợp lệ?}
    C -->|Không| D[Trả lỗi; không tạo xe hoặc ghế]
    C -->|Có| E[Tạo xe và tự sinh ghế vật lý cùng transaction]
    E --> F[Commit; 201 và dữ liệu xe]
    F --> G[Người dùng chỉnh vị trí, tầng, trạng thái ghế rồi lưu toàn sơ đồ]
    G --> H[Khóa xe; kiểm tra đủ số ghế, tầng, mã ghế và vị trí không trùng]
    H --> I{Sơ đồ hợp lệ?}
    I -->|Không| J[400 hoặc 409; rollback; giữ nguyên sơ đồ cũ]
    I -->|Có| K{Có thay đổi sơ đồ?}
    K -->|Không| N[200 và danh sách ghế]
    K -->|Có| L{Có lịch chuyến hoặc lịch sử vé, giữ chỗ?}
    L -->|Có| J
    L -->|Không| M[Cập nhật toàn bộ; flush; commit]
    M --> N
```

Ví dụ lỗi: 409 “Biển số đã tồn tại.”; 400 “Sức chứa phải khớp với loại xe.”;
400 “Tầng ghế không hợp lệ.”; 409 “Xe đang có lịch chuyến; không sửa sơ đồ ghế.”.
Ghế vật lý hoạt động/bảo trì khác với tình trạng trống/giữ/đã bán của một chuyến.
Kiểm thử: `VehicleTask10Test.typeAndVehicleValidationUniquePlatesAndFilters`,
`layoutRejectsWrongCountsDuplicatesForeignIdsAndInvalidFloorsWithoutPartialSave`;
`PostgresTask10Test.concurrentSamePlateAcrossTypesCreatesOnlyOneCompleteVehicle`.

## 3. Tạo chuyến nháp và mở bán

Frontend: `TripManagement.tsx`; Controller: `TripController`;
Service: `TripService`, `TripSaleRules`, `VehicleRules`, `AssignmentRules`.
Endpoint gốc `/api/quan-ly/chuyen-xe`.

```mermaid
flowchart TD
    A[Người quản lý nhập tuyến, ngày giờ và xe tùy chọn] --> B[POST tạo chuyến; kiểm tra quyền, mã, tuyến, giá và giờ đến]
    B --> C{Hợp lệ?}
    C -->|Không| D[400, 403, 404 hoặc 409; rollback; hiện thông báo]
    C -->|Có| E[Lưu NHAP; commit; 201; chưa xuất hiện để đặt vé]
    E --> F[Gán xe và phân công nhân viên qua module phân công]
    F --> G[GET dieu-kien-mo-ban]
    G --> H[Trả 200 với duDieuKien và danh sách điều kiện, lý do chưa đạt]
    H --> I[Người quản lý bấm Mở bán]
    I --> J[Khóa chuyến; kiểm tra lại trạng thái, hạn đặt, tuyến, bến, nhà xe, xe và nhân sự]
    J --> K{Đủ điều kiện và vẫn còn trong hạn?}
    K -->|Không| L[409 với lý do chưa đạt; rollback; tải lại bảng điều kiện]
    K -->|Có| M[Lưu DANG_MO_BAN nếu chưa mở; commit; 200]
    M --> N[Giao diện cập nhật trạng thái; tìm chuyến có thể thấy chuyến]
```

Tạo nháp cho phép thiếu xe và nhân sự; mở bán thì bắt buộc đủ điều kiện.
GET điều kiện không đạt vẫn có thể trả 200 với `duDieuKien=false`; POST mở bán thất bại trả 409.
Ví dụ lỗi: “Mã chuyến đã tồn tại.”; “Ngày giờ đến phải sau khởi hành.”;
“Chuyến chưa được gán xe.”; “Đã đến thời điểm ngừng nhận đặt vé trong khi kiểm tra điều kiện.”.
Mở bán lại trong hạn và đủ điều kiện không tạo chuyến mới. Hai chuyến xung đột lịch phải kiểm tra bằng khóa database.
Kiểm thử: `TripServiceTest.draftCanLackVehicleAndCrewButCannotBeSearchedOrSold`,
`actualT11AssignmentFlowAndIdempotentOpening`, `cutoffReachedDuringValidationStillRollsBackOpening`;
`PostgresTripTest.conflictingSimultaneousPublishSerializesUnderDatabaseLocks`.

## 4. Tìm chuyến

Frontend thuộc Nguyễn Thế An: `SearchForm.tsx`, `SearchResultsPage.tsx`, `booking/lib/api.ts`.
Backend của Hiền: `RouteTripController.handleTrips` → `RouteTripService.searchChuyenXe`
→ `ChuyenXeRepository.findForSearch` → `TripSaleRules.ensureSellable` → `searchView`.

```mermaid
flowchart TD
    A[Khách chọn điểm đi, điểm đến và ngày] --> B[GET /api/chuyen-xe với from, to, date]
    B --> C{Ngày, mã địa điểm và hai điểm hợp lệ?}
    C -->|Không| D[400; hiển thị thông báo đầu vào không hợp lệ]
    C -->|Có| E[Truy vấn chuyến theo tỉnh hoặc bến và ngày]
    E --> F[Lọc chuyến theo điều kiện bán hiện tại; đếm ghế theo chuyến]
    F --> G{Có chuyến phù hợp?}
    G -->|Không| H[200 với mảng rỗng; giao diện báo không có chuyến phù hợp]
    G -->|Có| I[200 với danh sách giờ, giá, xe và số ghế còn]
    I --> J{Chuyến còn ghế?}
    J -->|Không| K[Vẫn hiển thị; coTheDatVe=false; HET_CHO]
    J -->|Có| L[Cho xem chi tiết và đi tiếp tới chọn ghế]
    B -->|Mạng hoặc backend lỗi| M[Hiển thị lỗi tải hoặc tìm dữ liệu; không dùng dữ liệu giả]
```

Ví dụ lỗi: “Ngày đi là bắt buộc”; “Tỉnh đi và tỉnh đến phải khác nhau.”.
Chuyến không còn đủ điều kiện bán bị bỏ khỏi kết quả; không trả lỗi toàn bộ chỉ vì một chuyến không bán được.
Kết quả rỗng là thành công HTTP, khác lỗi API. Chọn ghế ở frontend chưa giữ ghế trong database.
Kiểm thử: `RouteTripServiceTest.emptySearchWrongDirectionDateAndDepartureOrdering`,
`searchAcceptsCityStationAndLegacyCodesWithoutChangingSaleRules`,
`publicDetailAndAvailabilityUseCurrentTripSpecificSeats`.

## 5. Bắt đầu chuyến

`POST /api/quan-ly/chuyen-xe/{maChuyen}/bat-dau` → `TripService.transition`.

```mermaid
flowchart TD
    A[Người có quyền bấm Bắt đầu chuyến] --> B[Kiểm tra quyền; khóa chuyến]
    B --> C{ADMIN, nhà xe đúng phạm vi hoặc tài xế được phân công hợp lệ?}
    C -->|Không| D[401 hoặc 403; giữ trạng thái cũ]
    C -->|Có| E{Chuyến đang DANG_MO_BAN?}
    E -->|Không| F[409: Chỉ bắt đầu chuyến đang mở bán.]
    E -->|Có| G[Kiểm tra lại xe và nhân sự đủ điều kiện vận hành]
    G --> H{Đạt?}
    H -->|Không| I[Trả lỗi nghiệp vụ; rollback; hiển thị lý do]
    H -->|Có| J[Lưu DANG_CHAY và lịch sử thao tác trong transaction]
    J --> K[Commit; 200; cập nhật trạng thái trên giao diện]
```

Bấm bắt đầu lại khi đã chạy bị từ chối, không tạo thêm lịch sử thành công.
Kiểm thử: `TripServiceTest.lifecycleRejectsInvalidStatesAndDoesNotDuplicateHistory`,
`lifecycleRechecksVehicleAndRejectsCustomer`, `assignedDriverCanOperateButCannotCancelOrOperateAnotherTrip`.

## 6. Hoàn tất chuyến

`POST /api/quan-ly/chuyen-xe/{maChuyen}/hoan-thanh` → `TripService.transition`.

```mermaid
flowchart TD
    A[Người có quyền bấm Hoàn tất chuyến] --> B[Kiểm tra quyền và khóa chuyến]
    B --> C{Quyền hợp lệ?}
    C -->|Không| D[401 hoặc 403; không thay đổi dữ liệu]
    C -->|Có| E{Chuyến đang DANG_CHAY?}
    E -->|Không| F[409: Chỉ hoàn tất chuyến đang chạy.]
    E -->|Có| G[Lưu HOAN_TAT và lịch sử thao tác]
    G --> H{Giao dịch thành công?}
    H -->|Không| I[Rollback; trả lỗi; giao diện không báo thành công]
    H -->|Có| J[Commit; 200; cập nhật giao diện]
```

Hoàn tất chuyến không tự đánh dấu toàn bộ vé là đã lên xe.
Kiểm thử: `TripServiceTest.completingDoesNotMarkUninspectedPassengersAsBoarded`,
`lifecycleRejectsInvalidStatesAndDoesNotDuplicateHistory`.

## 7. Hủy chuyến và theo dõi xử lý

`POST /api/quan-ly/chuyen-xe/{maChuyen}/huy` với `{lyDo}` → `TripService.cancel/transition`.
Báo cáo: GET hậu tố `/vong-doi`; thử lại công việc: POST hậu tố `/xu-ly-hoan`.

```mermaid
flowchart TD
    A[ADMIN hoặc nhà xe nhập lý do và xác nhận hủy] --> B[Kiểm tra quyền; khóa chuyến]
    B --> C{Đúng phạm vi và lý do 1 đến 1000 ký tự sau trim?}
    C -->|Không| D[401, 403 hoặc 400; không hủy]
    C -->|Có| E{Trạng thái NHAP, CHUA_KHOI_HANH hoặc DANG_MO_BAN?}
    E -->|Không| F[409: Không hủy chuyến đang chạy, đã hoàn tất hoặc đã hủy.]
    E -->|Có| G[Khóa đơn liên quan; giải phóng giữ chỗ; phân loại công việc hoàn và thông báo]
    G --> H[Lưu DA_HUY cùng lịch sử và công việc trong một transaction]
    H --> I{Commit thành công?}
    I -->|Không| J[Rollback; trả lỗi; chuyến chưa hủy thành công]
    I -->|Có| K[Sau commit: thử xử lý hoàn và gửi thông báo]
    K --> L{Công việc hoàn hoặc thông báo còn lỗi, chưa rõ?}
    L -->|Có| M[Giữ công việc để đối soát hoặc thử lại; không đảo trạng thái hủy]
    L -->|Không| N[Ghi nhận kết quả xử lý]
    M --> O[200 cho thao tác hủy; tải báo cáo vòng đời để xem kết quả từng công việc]
    N --> O
```

Ví dụ lỗi: 400 “Vui lòng nhập lý do hủy chuyến.”; 400 “Lý do hủy không được vượt quá 1000 ký tự.”.
Tài xế được phân công có thể bắt đầu/hoàn tất nhưng không có quyền hủy.
Đơn chưa thu tiền không cần hoàn; đơn đã thu có thể chờ xử lý; giao dịch chưa rõ hoặc đơn chứa chuyến khác cần đối soát.
Cấu hình phê duyệt chính sách hoàn và khả năng của nhà cung cấp ảnh hưởng bước hoàn thực tế.
**Hủy thành công không đồng nghĩa đã hoàn tiền hoặc đã gửi email thành công.**
Kiểm thử: `TripServiceTest.cancellingReleasesHoldsWithoutAnOrderAndRequiresReason`,
`assignedDriverCanOperateButCannotCancelOrOperateAnotherTrip`, `lifecycleRejectsInvalidStatesAndDoesNotDuplicateHistory`.
Các test này không thay thế kiểm thử nhà cung cấp hoàn tiền/email thật.

## Cách dùng khi trình bày

Chọn một sơ đồ, đi từ thao tác người dùng tới endpoint, mở Controller rồi Service tương ứng,
chỉ ra một nhánh thành công và một nhánh thất bại, sau đó mở test dẫn ở cuối luồng.
Dùng bảng truy vết để chỉ đúng Entity/bảng và phân biệt JPA với native SQL.
Các test được dẫn là test hiện có trong source, chưa phải báo cáo chạy mới của lần bổ sung tài liệu này.
