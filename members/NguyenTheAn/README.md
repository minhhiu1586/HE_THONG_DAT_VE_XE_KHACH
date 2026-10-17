# Nguyễn Thế Ân

Giao diện tìm/chọn chuyến, giữ chỗ, đơn, người đi, lịch sử, hiển thị vé và yêu cầu hoàn.

## Tài liệu và kiểm thử

- [Kiểm thử và kết quả chạy](testing/README.md)
- [Phân công và phần phối hợp](../../docs/team/phan-cong-thanh-vien.md)
- [Hợp đồng API](../../docs/api/README.md)

<a id="source"></a>

## Source thuộc phạm vi bảo trì

Ứng dụng tích hợp dùng trực tiếp source bên dưới. Chạy từ thư mục gốc theo [README](../../README.md).

| File |
|---|
| [BookingController.java](backend/src/main/java/com/datvexe/controller/BookingController.java) |
| [BookingSession.java](backend/src/main/java/com/datvexe/controller/BookingSession.java) |
| [BookingDto.java](backend/src/main/java/com/datvexe/dto/BookingDto.java) |
| [HoldSeatRequest.java](backend/src/main/java/com/datvexe/dto/HoldSeatRequest.java) |
| [HoldSeatResponse.java](backend/src/main/java/com/datvexe/dto/HoldSeatResponse.java) |
| [DonDatVeRepository.java](backend/src/main/java/com/datvexe/repository/DonDatVeRepository.java) |
| [GiuChoRepository.java](backend/src/main/java/com/datvexe/repository/GiuChoRepository.java) |
| [BookingService.java](backend/src/main/java/com/datvexe/service/BookingService.java) |
| [OrderCode.java](backend/src/main/java/com/datvexe/service/OrderCode.java) |
| [GiuChoRepositoryTest.java](backend/src/test/java/com/datvexe/repository/GiuChoRepositoryTest.java) |
| [BookingServiceExpiryTest.java](backend/src/test/java/com/datvexe/service/BookingServiceExpiryTest.java) |
| [OrderCodeTest.java](backend/src/test/java/com/datvexe/service/OrderCodeTest.java) |
| [HomePage.tsx](frontend/src/trip-search/pages/HomePage.tsx) |
| [CheckoutPage.tsx](frontend/src/checkout/pages/CheckoutPage.tsx) |
| [TripPage.tsx](frontend/src/trip-details/pages/TripPage.tsx) |
| [SeatsPage.tsx](frontend/src/seat-selection/pages/SeatsPage.tsx) |
| [page.module.css](frontend/src/tickets/pages/page.module.css) |
| [TicketResultPage.tsx](frontend/src/tickets/pages/TicketResultPage.tsx) |
| [SearchResultsPage.tsx](frontend/src/trip-search/pages/SearchResultsPage.tsx) |
| [OrderHistoryPanel.module.css](frontend/src/orders/components/OrderHistoryPanel.module.css) |
| [OrderHistoryPanel.tsx](frontend/src/orders/components/OrderHistoryPanel.tsx) |
| [BusFront.module.css](frontend/src/booking/components/BusFront.module.css) |
| [BusFront.tsx](frontend/src/booking/components/BusFront.tsx) |
| [LocationPicker.tsx](frontend/src/booking/components/LocationPicker.tsx) |
| [OrderCode.module.css](frontend/src/booking/components/OrderCode.module.css) |
| [OrderCode.tsx](frontend/src/booking/components/OrderCode.tsx) |
| [PassengerForm.tsx](frontend/src/booking/components/PassengerForm.tsx) |
| [RefundPanel.module.css](frontend/src/refund-request/components/RefundPanel.module.css) |
| [RefundPanel.tsx](frontend/src/refund-request/components/RefundPanel.tsx) |
| [SearchDateField.tsx](frontend/src/booking/components/SearchDateField.tsx) |
| [SearchForm.tsx](frontend/src/booking/components/SearchForm.tsx) |
| [seat-layout.ts](frontend/src/booking/components/seat-layout.ts) |
| [SeatItem.tsx](frontend/src/booking/components/SeatItem.tsx) |
| [SeatLayout.tsx](frontend/src/booking/components/SeatLayout.tsx) |
| [TripCard.tsx](frontend/src/booking/components/TripCard.tsx) |
| [TripDetails.module.css](frontend/src/booking/components/TripDetails.module.css) |
| [TripDetails.tsx](frontend/src/booking/components/TripDetails.tsx) |
| [BusStations.tsx](frontend/src/trip-search/components/BusStations.tsx) |
| [PopularRoutes.tsx](frontend/src/trip-search/components/PopularRoutes.tsx) |
| [api.ts](frontend/src/booking/lib/api.ts) |
| [booking-api.ts](frontend/src/booking/lib/booking-api.ts) |
| [refund-api.ts](frontend/src/booking/lib/refund-api.ts) |
| [search-locations.ts](frontend/src/booking/lib/search-locations.ts) |
| [api.ts](frontend/src/booking/services/api.ts) |
| [booking.service.ts](frontend/src/booking/services/booking.service.ts) |
| [useBookingStore.ts](frontend/src/booking/store/useBookingStore.ts) |
| [t27-ra-soat-giao-dien.md](testing/existing/t27-ra-soat-giao-dien.md) |
