# Lê Nguyễn Thanh Hiền

## Phạm vi

Tỉnh/thành, bến xe, tuyến và điểm dừng; loại xe, phương tiện và sơ đồ ghế;
backend tìm chuyến; tạo chuyến nháp, mở bán, vòng đời chuyến và danh sách hành khách.

## Luồng chính

| Chức năng | Giao diện | Controller | Service | Repository / truy cập dữ liệu |
|---|---|---|---|---|
| Tỉnh, bến, tuyến và điểm dừng | `RouteManagement` → `CityPanel`, `StationPanel`, `RouteForm`, `RouteList`, `RouteStopsPanel` | `RouteTripController` | `RouteTripService` | JPA trực tiếp: `find`, `persist`, `remove`, JPQL |
| Loại xe, xe và sơ đồ ghế | `VehicleManagement` → `VehicleTypePanel`, `VehicleForm`, `VehicleList`, `VehicleSeatLayout` | `VehicleController` | `VehicleService`, `VehicleRules` | `LoaiXeRepository`, `XeRepository`, `GheRepository`; JPA kiểm tra lịch sử |
| Chuyến nháp và mở bán | `TripManagement` → `TripForm`, `TripList`, `TripDetails`, `TripSaleConditions` | `TripController` | `TripService`, `TripSaleRules` | `ChuyenXeRepository`; JPA kiểm tra điều kiện |
| Tìm chuyến và ghế theo chuyến | Frontend Thế An: `SearchForm`, `SearchResultsPage`, `TripDetails` | `RouteTripController` | `RouteTripService`, `VehicleService`, `VehicleRules` | `ChuyenXeRepository.findForSearch`, `GheRepository`; JPQL đếm ghế/vé/giữ chỗ |
| Bắt đầu, hoàn tất, hủy và theo dõi xử lý | `TripDetails`, `TripLifecyclePanel` | `TripController` | `TripService` | `ChuyenXeRepository`; JPA và native SQL lịch sử/công việc |
| Danh sách hành khách và vé | `TripPassengerModal`, `trip-passengers-api` | `RouteTripController` | `TripPassengerService` | JPA đọc vé; `HoanVeRepository` đọc tình trạng hoàn |

Component quản lý giữ state, gọi API và điều phối; component con hiển thị form/bảng và gọi callback.
Type, nhãn trạng thái, form trống và hàm chuyển dữ liệu sang form nằm trong `frontend/src/*/lib`.
FE dùng chung `useAuth().request`, `errorMessage` và validation tiếng Việt; Backend kiểm tra quyền, phạm vi và nghiệp vụ.

Entity nằm trong `shared/backend/src/main/java/com/datvexe/domain/Entities.java`, không sao chép vào thư mục cá nhân.
Phân công do Kim Cương xử lý qua `AssignmentService`/`AssignmentRules`; giao diện tìm chuyến thuộc Thế An;
hoàn tiền và email khi hủy gọi module Gia Bảo. Hủy chuyến thành công không đồng nghĩa đã hoàn tiền/gửi email thành công.

- [Luồng demo và thứ tự mở code](docs/huong-dan-demo.md)
- [Sơ đồ nghiệp vụ](docs/huong-dan-demo.md#so-do-nghiep-vu)
- [Bảng truy vết tới Entity, bảng DB và test](docs/huong-dan-demo.md#bang-truy-vet)
- [Kiểm thử và kết quả](testing/README.md)
- [Chạy ứng dụng](../../README.md)

<a id="source"></a>

## Source

Các đường dẫn dưới đây là source đang được ứng dụng tích hợp sử dụng.

| File |
|---|
| [RouteTripController.java](backend/src/main/java/com/datvexe/controller/RouteTripController.java) |
| [TripController.java](backend/src/main/java/com/datvexe/controller/TripController.java) |
| [VehicleController.java](backend/src/main/java/com/datvexe/controller/VehicleController.java) |
| [TripInputs.java](backend/src/main/java/com/datvexe/dto/TripInputs.java) |
| [TripPassengerDto.java](backend/src/main/java/com/datvexe/dto/TripPassengerDto.java) |
| [VehicleInputs.java](backend/src/main/java/com/datvexe/dto/VehicleInputs.java) |
| [ChuyenXeRepository.java](backend/src/main/java/com/datvexe/repository/ChuyenXeRepository.java) |
| [GheRepository.java](backend/src/main/java/com/datvexe/repository/GheRepository.java) |
| [LoaiXeRepository.java](backend/src/main/java/com/datvexe/repository/LoaiXeRepository.java) |
| [XeRepository.java](backend/src/main/java/com/datvexe/repository/XeRepository.java) |
| [RouteTripService.java](backend/src/main/java/com/datvexe/service/RouteTripService.java) |
| [TripPassengerService.java](backend/src/main/java/com/datvexe/service/TripPassengerService.java) |
| [TripSaleRules.java](backend/src/main/java/com/datvexe/service/TripSaleRules.java) |
| [TripService.java](backend/src/main/java/com/datvexe/service/TripService.java) |
| [VehicleRules.java](backend/src/main/java/com/datvexe/service/VehicleRules.java) |
| [VehicleService.java](backend/src/main/java/com/datvexe/service/VehicleService.java) |
| [CityPanel.tsx](frontend/src/routes/components/CityPanel.tsx) |
| [RouteForm.tsx](frontend/src/routes/components/RouteForm.tsx) |
| [RouteList.tsx](frontend/src/routes/components/RouteList.tsx) |
| [RouteManagement.tsx](frontend/src/routes/components/RouteManagement.tsx) |
| [RouteStopsPanel.tsx](frontend/src/routes/components/RouteStopsPanel.tsx) |
| [StationPanel.tsx](frontend/src/routes/components/StationPanel.tsx) |
| [route-types.ts](frontend/src/routes/lib/route-types.ts) |
| [TripDetails.tsx](frontend/src/trips/components/TripDetails.tsx) |
| [TripForm.tsx](frontend/src/trips/components/TripForm.tsx) |
| [TripLifecyclePanel.tsx](frontend/src/trips/components/TripLifecyclePanel.tsx) |
| [TripList.tsx](frontend/src/trips/components/TripList.tsx) |
| [TripManagement.tsx](frontend/src/trips/components/TripManagement.tsx) |
| [TripPassengerModal.tsx](frontend/src/trips/components/TripPassengerModal.tsx) |
| [TripSaleConditions.tsx](frontend/src/trips/components/TripSaleConditions.tsx) |
| [trip-passengers-api.ts](frontend/src/trips/lib/trip-passengers-api.ts) |
| [trip-types.ts](frontend/src/trips/lib/trip-types.ts) |
| [VehicleField.tsx](frontend/src/vehicles/components/VehicleField.tsx) |
| [VehicleForm.tsx](frontend/src/vehicles/components/VehicleForm.tsx) |
| [VehicleList.tsx](frontend/src/vehicles/components/VehicleList.tsx) |
| [VehicleManagement.tsx](frontend/src/vehicles/components/VehicleManagement.tsx) |
| [VehicleSeatLayout.tsx](frontend/src/vehicles/components/VehicleSeatLayout.tsx) |
| [VehicleTypePanel.tsx](frontend/src/vehicles/components/VehicleTypePanel.tsx) |
| [vehicle-types.ts](frontend/src/vehicles/lib/vehicle-types.ts) |
