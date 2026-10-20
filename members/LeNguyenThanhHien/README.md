# Lê Nguyễn Thanh Hiền

Tỉnh/thành, bến, tuyến, phương tiện, ghế, tìm kiếm backend và vòng đời chuyến.

## Tài liệu và kiểm thử

- [Hướng dẫn demo](docs/huong-dan-demo.md)
- [Sơ đồ luồng nghiệp vụ, nhánh thành công và thất bại](docs/so-do-luong-nghiep-vu.md)
- [Bảng truy vết chức năng tới code, Entity, bảng DB và test](docs/bang-truy-vet-chuc-nang.md)
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

## Cách đọc backend sau khi viết lại

Giữ cấu trúc thư mục và hợp đồng API đang sử dụng. Source nằm trong
`backend/src/main/java/com/datvexe/` bên dưới thư mục thành viên này.

| Lớp | Vai trò |
|---|---|
| `controller/RouteTripController.java` | Chọn handler tỉnh, bến, tuyến, chuyến; đọc request và trả JSON |
| `controller/TripController.java` | Chọn thao tác tạo/sửa/xóa, mở bán, bắt đầu/hoàn tất/hủy chuyến |
| `controller/VehicleController.java` | Nhận yêu cầu loại xe, xe, ghế, sơ đồ và phân công |
| `service/RouteTripService.java` | Kiểm tra đầu vào, xử lý tỉnh/bến/tuyến và tổng hợp kết quả tìm chuyến |
| `repository/ChuyenXeRepository.java` | Truy vấn lọc/phân trang quản lý và `findForSearch` tìm chuyến đang mở bán |
| `service/TripSaleRules.java` | Kiểm tra lần lượt từng điều kiện mở bán và trả đầy đủ lý do chưa đạt |
| `service/TripService.java` | Tạo/cập nhật chuyến, chuyển trạng thái, ghi lịch sử và xử lý công việc hủy |
| `service/VehicleService.java` | Xử lý phương tiện, sơ đồ ghế và phân công bằng các bước/vòng lặp rõ ràng |
| `service/VehicleRules.java` | Kiểm tra đăng kiểm, sơ đồ, lịch sử sử dụng và khả năng bán ghế |
| `service/TripPassengerService.java` | Kiểm tra quyền rồi tạo danh sách hành khách và đếm trạng thái vé |

### Ví dụ luồng tìm chuyến

```text
RouteTripController.handleTrips
  → RouteTripService.searchChuyenXe
    → Kiểm tra điểm đi, điểm đến và ngày
    → Bắt đầu transaction, kiểm tra quyền
    → ChuyenXeRepository.findForSearch
    → Duyệt từng chuyến, gọi TripSaleRules.ensureSellable
    → searchView lấy thông tin và số ghế còn
    → Commit, trả danh sách
  → Ghi JSON vào response
```

`findForSearch` thêm điều kiện bằng `StringBuilder` và các nhánh `if/else`.
Vẫn nhận mã tỉnh (`CITY:`), mã bến (`STATION:`) và mã cũ không có tiền tố.
Các kiểm tra về xe, nhân sự, hạn đặt vé vẫn thuộc Service/quy tắc nghiệp vụ.

### Transaction và dữ liệu trả về

Trong từng thao tác Service, thứ tự được thể hiện trực tiếp:

```java
try (EntityManager em = factory.createEntityManager()) {
    EntityTransaction transaction = em.getTransaction();
    transaction.begin();
    try {
        AccessPolicy.guard(em);
        // Kiểm tra đầu vào/quyền, truy vấn và xử lý nghiệp vụ.
        // Tạo dữ liệu response trước khi commit.
        transaction.commit();
        // Trả kết quả.
    } catch (RuntimeException error) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
        throw error;
    }
}
```

Không còn callback transaction bằng `Function<EntityManager,T>` trong các Service
của thành viên. Repository dùng cùng EntityManager, không tự mở transaction riêng.
Khóa nhà xe/chuyến/bến/xe và thứ tự refresh được giữ trong luồng đang sử dụng.

Danh sách được tạo bằng `ArrayList` và vòng `for`. Response giữ tên trường cũ,
được dựng bằng DTO đang có hoặc từng lệnh `put`, không đổi sang phong bì JSON của Cinema.
Generic trong Repository dùng chung, DTO dạng record và các phép so sánh sắp xếp
ngắn vẫn được giữ để tương thích các phần khác của hệ thống.

Khi hủy chuyến, công việc hoàn và thông báo vẫn được lưu trong transaction hủy.
Gọi bộ xử lý hoàn tiền/gửi thông báo sau commit; lỗi của bộ xử lý không làm mất
bản ghi công việc để thử lại. Không đổi chính sách hoàn tiền hay tự đánh dấu vé đã lên xe.
