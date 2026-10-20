# Lê Nguyễn Thanh Hiền

Tỉnh/thành, bến, tuyến, phương tiện, ghế, tìm kiếm backend và vòng đời chuyến.

Phân công nhân viên được xử lý tại module Kim Cương qua AssignmentService; các test tích hợp chuyến gọi service này. Kiểm tra nhân sự trước mở bán/bắt đầu chuyến vẫn sử dụng AssignmentRules.

## Tài liệu và kiểm thử

- [Hướng dẫn demo](docs/huong-dan-demo.md)
- [Kiểm thử và kết quả chạy](testing/README.md)
- [Phân công và phần phối hợp](../../docs/team/phan-cong-thanh-vien.md)
- [Hợp đồng API](../../docs/api/README.md)

<a id="source"></a>

## Source thuộc phạm vi bảo trì

Ứng dụng tích hợp dùng trực tiếp source bên dưới. Chạy từ thư mục gốc theo [README](../../README.md).

| File |
|---|
| [TripPassengerService.java](backend/src/main/java/com/datvexe/service/TripPassengerService.java) |
| [TripPassengerDto.java](backend/src/main/java/com/datvexe/dto/TripPassengerDto.java) |
| [trip-passengers-api.ts](frontend/src/trips/lib/trip-passengers-api.ts) |
| [RouteTripController.java](backend/src/main/java/com/datvexe/controller/RouteTripController.java) |
| [TripController.java](backend/src/main/java/com/datvexe/controller/TripController.java) |
| [VehicleController.java](backend/src/main/java/com/datvexe/controller/VehicleController.java) |
| [TripInputs.java](backend/src/main/java/com/datvexe/dto/TripInputs.java) |
| [VehicleInputs.java](backend/src/main/java/com/datvexe/dto/VehicleInputs.java) |
| [ChuyenXeRepository.java](backend/src/main/java/com/datvexe/repository/ChuyenXeRepository.java) |
| [GheRepository.java](backend/src/main/java/com/datvexe/repository/GheRepository.java) |
| [LoaiXeRepository.java](backend/src/main/java/com/datvexe/repository/LoaiXeRepository.java) |
| [XeRepository.java](backend/src/main/java/com/datvexe/repository/XeRepository.java) |
| [RouteTripService.java](backend/src/main/java/com/datvexe/service/RouteTripService.java) |
| [TripSaleRules.java](backend/src/main/java/com/datvexe/service/TripSaleRules.java) |
| [TripService.java](backend/src/main/java/com/datvexe/service/TripService.java) |
| [VehicleRules.java](backend/src/main/java/com/datvexe/service/VehicleRules.java) |
| [VehicleService.java](backend/src/main/java/com/datvexe/service/VehicleService.java) |
| [PostgresTask10Test.java](backend/src/test/java/com/datvexe/service/PostgresTask10Test.java) |
| [PostgresTripTest.java](backend/src/test/java/com/datvexe/service/PostgresTripTest.java) |
| [PostgresVehicleTest.java](backend/src/test/java/com/datvexe/service/PostgresVehicleTest.java) |
| [RouteTripServiceTest.java](backend/src/test/java/com/datvexe/service/RouteTripServiceTest.java) |
| [TripServiceTest.java](backend/src/test/java/com/datvexe/service/TripServiceTest.java) |
| [VehicleServiceTest.java](backend/src/test/java/com/datvexe/service/VehicleServiceTest.java) |
| [VehicleTask10Test.java](backend/src/test/java/com/datvexe/service/VehicleTask10Test.java) |
| [RouteManagement.module.css](frontend/src/routes/components/RouteManagement.module.css) |
| [RouteManagement.tsx](frontend/src/routes/components/RouteManagement.tsx) |
| [TripManagement.module.css](frontend/src/trips/components/TripManagement.module.css) |
| [TripManagement.tsx](frontend/src/trips/components/TripManagement.tsx) |
| [TripPassengerModal.module.css](frontend/src/trips/components/TripPassengerModal.module.css) |
| [TripPassengerModal.tsx](frontend/src/trips/components/TripPassengerModal.tsx) |
| [VehicleManagement.module.css](frontend/src/vehicles/components/VehicleManagement.module.css) |
| [VehicleManagement.tsx](frontend/src/vehicles/components/VehicleManagement.tsx) |
| [t10-ma-tran-kiem-thu-xe-va-ghe.md](testing/existing/t10-ma-tran-kiem-thu-xe-va-ghe.md) |
| [t12-ma-tran-kiem-thu-chuyen-xe.md](testing/existing/t12-ma-tran-kiem-thu-chuyen-xe.md) |
| [t13-ma-tran-kiem-thu-tim-chuyen.md](testing/existing/t13-ma-tran-kiem-thu-tim-chuyen.md) |
| [t10-vscode-guide.md](docs/huong-dan-demo.md#section-1) |
| [t12-vscode-guide.md](docs/huong-dan-demo.md#section-2) |
| [t13-vscode-guide.md](docs/huong-dan-demo.md#section-3) |
