# Nguyễn Gia Bảo

Giá, khuyến mãi, thanh toán, phát hành vé, QR, email và hoàn tiền.

## Tài liệu và kiểm thử

- [Kiểm thử và kết quả chạy](testing/README.md)
- [Phân công và phần phối hợp](../../docs/team/phan-cong-thanh-vien.md)
- [Hợp đồng API](../../docs/api/README.md)

<a id="source"></a>

## Source thuộc phạm vi bảo trì

Ứng dụng tích hợp dùng trực tiếp source bên dưới. Chạy từ thư mục gốc theo [README](../../README.md).

| File |
|---|
| [ChinhSachHoanVeController.java](backend/src/main/java/com/datvexe/controller/ChinhSachHoanVeController.java) |
| [HoanTienController.java](backend/src/main/java/com/datvexe/controller/HoanTienController.java) |
| [KhuyenMaiController.java](backend/src/main/java/com/datvexe/controller/KhuyenMaiController.java) |
| [PhuongThucThanhToanController.java](backend/src/main/java/com/datvexe/controller/PhuongThucThanhToanController.java) |
| [PublicPromotionController.java](backend/src/main/java/com/datvexe/controller/PublicPromotionController.java) |
| [RefundHttp.java](backend/src/main/java/com/datvexe/controller/RefundHttp.java) |
| [ThanhToanController.java](backend/src/main/java/com/datvexe/controller/ThanhToanController.java) |
| [VeXeController.java](backend/src/main/java/com/datvexe/controller/VeXeController.java) |
| [EmailOutbox.java](backend/src/main/java/com/datvexe/domain/EmailOutbox.java) |
| [ChinhSachHoanVeDto.java](backend/src/main/java/com/datvexe/dto/ChinhSachHoanVeDto.java) |
| [HoanVeDto.java](backend/src/main/java/com/datvexe/dto/HoanVeDto.java) |
| [KhuyenMaiDto.java](backend/src/main/java/com/datvexe/dto/KhuyenMaiDto.java) |
| [PhuongThucThanhToanDto.java](backend/src/main/java/com/datvexe/dto/PhuongThucThanhToanDto.java) |
| [PricingDto.java](backend/src/main/java/com/datvexe/dto/PricingDto.java) |
| [ThanhToanDto.java](backend/src/main/java/com/datvexe/dto/ThanhToanDto.java) |
| [BrevoClient.java](backend/src/main/java/com/datvexe/email/BrevoClient.java) |
| [EmailDispatcher.java](backend/src/main/java/com/datvexe/email/EmailDispatcher.java) |
| [EmailWorker.java](backend/src/main/java/com/datvexe/email/EmailWorker.java) |
| [ReceiptQueue.java](backend/src/main/java/com/datvexe/email/ReceiptQueue.java) |
| [ReceiptTemplate.java](backend/src/main/java/com/datvexe/email/ReceiptTemplate.java) |
| [TripCancellationEmailDispatcher.java](backend/src/main/java/com/datvexe/email/TripCancellationEmailDispatcher.java) |
| [PaymentContextStore.java](backend/src/main/java/com/datvexe/payment/PaymentContextStore.java) |
| [VNPayClient.java](backend/src/main/java/com/datvexe/payment/VNPayClient.java) |
| [VNPayConfig.java](backend/src/main/java/com/datvexe/payment/VNPayConfig.java) |
| [ChinhSachHoanVeRepository.java](backend/src/main/java/com/datvexe/repository/ChinhSachHoanVeRepository.java) |
| [HoanVeRepository.java](backend/src/main/java/com/datvexe/repository/HoanVeRepository.java) |
| [KhuyenMaiRepository.java](backend/src/main/java/com/datvexe/repository/KhuyenMaiRepository.java) |
| [PaymentGatewayMetadataRepository.java](backend/src/main/java/com/datvexe/repository/PaymentGatewayMetadataRepository.java) |
| [PhuongThucThanhToanRepository.java](backend/src/main/java/com/datvexe/repository/PhuongThucThanhToanRepository.java) |
| [ThanhToanRepository.java](backend/src/main/java/com/datvexe/repository/ThanhToanRepository.java) |
| [VeXeRepository.java](backend/src/main/java/com/datvexe/repository/VeXeRepository.java) |
| [ChinhSachHoanVeService.java](backend/src/main/java/com/datvexe/service/ChinhSachHoanVeService.java) |
| [HoanVeService.java](backend/src/main/java/com/datvexe/service/HoanVeService.java) |
| [KhuyenMaiService.java](backend/src/main/java/com/datvexe/service/KhuyenMaiService.java) |
| [PhuongThucThanhToanService.java](backend/src/main/java/com/datvexe/service/PhuongThucThanhToanService.java) |
| [PriceCalculator.java](backend/src/main/java/com/datvexe/service/PriceCalculator.java) |
| [ThanhToanService.java](backend/src/main/java/com/datvexe/service/ThanhToanService.java) |
| [TicketCode.java](backend/src/main/java/com/datvexe/service/TicketCode.java) |
| [TicketQr.java](backend/src/main/java/com/datvexe/ticket/TicketQr.java) |
| [BrevoClientTest.java](backend/src/test/java/com/datvexe/email/BrevoClientTest.java) |
| [EmailDispatcherTest.java](backend/src/test/java/com/datvexe/email/EmailDispatcherTest.java) |
| [ReceiptTemplateTest.java](backend/src/test/java/com/datvexe/email/ReceiptTemplateTest.java) |
| [VNPayClientTest.java](backend/src/test/java/com/datvexe/payment/VNPayClientTest.java) |
| [VNPayRefundClientTest.java](backend/src/test/java/com/datvexe/payment/VNPayRefundClientTest.java) |
| [ChinhSachHoanVeServiceTest.java](backend/src/test/java/com/datvexe/service/ChinhSachHoanVeServiceTest.java) |
| [HoanVeServicePostgresTest.java](backend/src/test/java/com/datvexe/service/HoanVeServicePostgresTest.java) |
| [PriceCalculatorTest.java](backend/src/test/java/com/datvexe/service/PriceCalculatorTest.java) |
| [ThanhToanServiceTest.java](backend/src/test/java/com/datvexe/service/ThanhToanServiceTest.java) |
| [TicketCodeTest.java](backend/src/test/java/com/datvexe/service/TicketCodeTest.java) |
| [TicketQrTest.java](backend/src/test/java/com/datvexe/ticket/TicketQrTest.java) |
| [PaymentReturnPage.tsx](frontend/src/payment/pages/PaymentReturnPage.tsx) |
