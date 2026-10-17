package com.datvexe.email;

import com.datvexe.domain.Entities.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptTemplateTest {
    static ReceiptTemplate.Content example(boolean sample, String passengerName) {
        BenXe from = new BenXe(); from.tenBenXe = "Bến xe Miền Tây"; from.diaChi = "395 Kinh Dương Vương, TP. Hồ Chí Minh";
        BenXe to = new BenXe(); to.tenBenXe = "Bến xe Trung tâm Cần Thơ"; to.diaChi = "Quốc lộ 1, Cần Thơ";
        TuyenXe route = new TuyenXe(); route.tramKhoiHanh = from; route.tramDen = to;
        NhaXe owner = new NhaXe(); owner.tenNhaXe = "Nhà xe mẫu";
        Xe vehicle = new Xe(); vehicle.nhaXe = owner; vehicle.bienSo = "DEMO-000.00";
        ChuyenXe trip = new ChuyenXe(); trip.tuyenXe = route; trip.xe = vehicle;
        trip.ngayKhoiHanh = LocalDate.of(2026, 10, 15); trip.gioKhoiHanh = LocalTime.of(8, 0); trip.gioDenDuKien = LocalDateTime.of(2026,10,15,11,30);
        DonDatVe order = new DonDatVe(); order.maDon = "VX-DEMO-EMAIL"; order.khachDatTen = "Khách kiểm thử"; order.khachDatSoDienThoai = "0900000000"; order.khachDatEmail = "testemaillstltweb@gmail.com";
        order.tongTien = new BigDecimal("360000"); order.tienGiam = new BigDecimal("20000");
        PhuongThucThanhToan method = new PhuongThucThanhToan(); method.tenPhuongThuc = "VNPAY sandbox (mẫu)";
        ThanhToan payment = new ThanhToan(); payment.maGiaoDich = "PAY-DEMO-NOT-REAL"; payment.phuongThucThanhToan = method; payment.soTien = new BigDecimal("340000"); payment.thoiGianThanhToan = LocalDateTime.of(2026,10,14,2,0);
        Ghe a = new Ghe(); a.viTri = "G01"; a.tang = 1; Ghe b = new Ghe(); b.viTri = "G02"; b.tang = 1;
        VeXe first = new VeXe(); first.maVe = "DEMO-TICKET-01"; first.chuyenXe = trip; first.ghe = a; first.hoTenKhachHang = passengerName; first.soDienThoaiKhachHang = "0900000000"; first.giaVe = new BigDecimal("170000");
        VeXe second = new VeXe(); second.maVe = "DEMO-TICKET-02"; second.chuyenXe = trip; second.ghe = b; second.hoTenKhachHang = "Hành khách mẫu 2"; second.soDienThoaiKhachHang = "0900000000"; second.giaVe = new BigDecimal("170000");
        return ReceiptTemplate.render(order, payment, List.of(first,second), sample);
    }
    @Test void receiptIncludesJourneySeatsTicketsAndFinancialTotalsAndEscapesNames() {
        var receipt = example(false, "<script>alert('x')</script>");
        assertTrue(receipt.html().contains("&lt;script&gt;")); assertFalse(receipt.html().contains("<script>"));
        for (String expected : List.of("G01", "G02", "DEMO-TICKET-01", "DEMO-TICKET-02", "Bến xe Miền Tây", "Cần Thơ", "340.000", "360.000", "20.000", "PAY-DEMO-NOT-REAL")) assertTrue(receipt.html().contains(expected), expected);
        assertTrue(receipt.text().contains("Mã vé:")); assertTrue(receipt.subject().contains("Thanh toán thành công"));
    }
    @Test void testPreviewClearlyLabelsSampleAndProducesStandalonePreview() throws Exception {
        var sample = example(true,"Hành khách mẫu 1");
        assertTrue(sample.subject().startsWith("[KIỂM THỬ]")); assertTrue(sample.html().contains("không phải vé hợp lệ"));
        Files.createDirectories(Path.of("target")); Files.writeString(Path.of("target/receipt-email-preview.html"), sample.html()); Files.writeString(Path.of("target/receipt-email-preview.txt"), sample.text());
    }
}
