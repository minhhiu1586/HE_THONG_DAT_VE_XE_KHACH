package com.datvexe.email;

import com.datvexe.domain.Entities.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class ReceiptTemplate {
    private ReceiptTemplate() {}
    public record Content(String subject, String html, String text) {}
    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String value(String text) { return text == null || text.isBlank() ? "Chưa cung cấp" : text; }
    private static String money(BigDecimal amount) { return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount == null ? BigDecimal.ZERO : amount) + " đ"; }
    private static String row(String label, String text) {
        return "<tr><td style=\"padding:9px 0;color:#64748b;width:32%;vertical-align:top;overflow-wrap:anywhere;word-break:break-word\">" + escape(label) + "</td><td style=\"padding:9px 0;color:#0f172a;font-weight:600;overflow-wrap:anywhere\">" + escape(value(text)) + "</td></tr>";
    }
    public static Content render(DonDatVe order, ThanhToan payment, List<VeXe> tickets, boolean sample) {
        if (tickets.isEmpty()) throw new IllegalArgumentException("Issued tickets are required.");
        ChuyenXe trip = tickets.getFirst().chuyenXe;
        TuyenXe route = trip.tuyenXe;
        String from = route.tramKhoiHanh.tenBenXe, to = route.tramDen.tenBenXe;
        String departure = trip.ngayKhoiHanh.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " · " + trip.gioKhoiHanh.format(DateTimeFormatter.ofPattern("HH:mm"));
        String arrival = trip.gioDenDuKien.format(DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy"));
        String operator = trip.xe != null && trip.xe.nhaXe != null ? trip.xe.nhaXe.tenNhaXe : null;
        String vehicle = trip.xe == null ? null : trip.xe.bienSo;
        String paidAt = payment.thoiGianThanhToan == null ? null : payment.thoiGianThanhToan.format(DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy"));
        String method = payment.phuongThucThanhToan.tenPhuongThuc;
        StringBuilder ticketRows = new StringBuilder(), plain = new StringBuilder();
        for (VeXe ticket : tickets) {
            String seat = value(ticket.ghe.viTri) + " · Tầng " + ticket.ghe.tang;
            ticketRows.append("<tr><td style=\"padding:12px 8px;border-bottom:1px solid #e2e8f0;vertical-align:top;overflow-wrap:anywhere;word-break:break-word\"><strong>").append(escape(ticket.hoTenKhachHang)).append("</strong><br><span style=\"font-size:12px;color:#64748b\">").append(escape(ticket.soDienThoaiKhachHang)).append("</span></td><td style=\"padding:12px 8px;border-bottom:1px solid #e2e8f0;vertical-align:top;overflow-wrap:anywhere;word-break:break-word\">").append(escape(seat)).append("</td><td style=\"padding:12px 8px;border-bottom:1px solid #e2e8f0;vertical-align:top;overflow-wrap:anywhere;word-break:break-word;word-break:break-all;font-size:12px\">").append(escape(ticket.maTraCuu == null ? ticket.maVe : ticket.maTraCuu)).append("<br><span style=\"color:#15803d\">Đã phát hành</span></td><td style=\"padding:12px 8px;border-bottom:1px solid #e2e8f0;text-align:right;vertical-align:top;overflow-wrap:anywhere;word-break:break-word\">").append(escape(money(ticket.giaVe))).append("</td></tr>");
            plain.append("\nHành khách: ").append(ticket.hoTenKhachHang).append(" · ").append(seat).append(" · Mã vé: ").append(ticket.maTraCuu == null ? ticket.maVe : ticket.maTraCuu).append(" · Giá: ").append(money(ticket.giaVe));
        }
        String title = sample ? "Email kiểm thử — hóa đơn và vé mẫu" : "Thanh toán thành công";
        String intro = sample ? "Đây là dữ liệu mẫu để kiểm tra gửi email, không phải vé hợp lệ và không ghi nhận giao dịch thật." : "Cảm ơn bạn đã đặt vé tại Vexexin. Thanh toán đã được xác minh và các vé dưới đây đã được phát hành.";
        String summary = row("Mã đơn", order.maDon) + row("Người đặt", order.khachDatTen) + row("Điện thoại", order.khachDatSoDienThoai) + row("Email nhận vé", order.khachDatEmail) + row("Nhà xe", operator) + row("Biển số", vehicle) + row("Khởi hành", departure) + row("Đến dự kiến", arrival) + row("Điểm đón", from + " · " + value(route.tramKhoiHanh.diaChi)) + row("Điểm trả", to + " · " + value(route.tramDen.diaChi));
        String html = "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>@media(max-width:480px){.mail-header{padding:22px 16px!important}.mail-content{padding:18px 14px!important}.mail-ticket th,.mail-ticket td{font-size:11px!important;padding:9px 4px!important}.mail-heading{font-size:20px!important}}</style></head><body style=\"margin:0;background:#f1f5f9;color:#0f172a;font-family:Arial,Helvetica,sans-serif\"><table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr><td align=\"center\" style=\"padding:24px 12px\"><table role=\"presentation\" width=\"680\" cellspacing=\"0\" cellpadding=\"0\" style=\"width:100%;max-width:680px;table-layout:fixed;background:#fff;border-radius:16px;overflow:hidden\"><tr><td class=\"mail-header\" style=\"padding:28px;background:#1d4ed8;color:#fff\"><div style=\"font-size:25px;font-weight:700\">Vexexin.</div><h1 class=\"mail-heading\" style=\"font-size:23px;margin:16px 0 8px\">" + escape(title) + "</h1><p style=\"margin:0;font-size:14px;line-height:1.7\">" + escape(intro) + "</p></td></tr><tr><td class=\"mail-content\" style=\"padding:24px\"><h2 style=\"font-size:18px;margin:0 0 12px\">Thông tin hành trình &amp; đơn đặt vé</h2><table role=\"presentation\" width=\"100%\" style=\"font-size:14px;line-height:1.5;border-collapse:collapse;table-layout:fixed\">" + summary + "</table><h2 style=\"font-size:18px;margin:24px 0 12px\">Vé và hành khách</h2><table class=\"mail-ticket\" width=\"100%\" cellspacing=\"0\" style=\"border-collapse:collapse;table-layout:fixed;font-size:13px;line-height:1.5\"><thead><tr style=\"background:#eff6ff;color:#1e3a8a\"><th style=\"padding:10px 8px;text-align:left\">Hành khách</th><th style=\"padding:10px 8px;text-align:left\">Ghế</th><th style=\"padding:10px 8px;text-align:left\">Mã vé</th><th style=\"padding:10px 8px;text-align:right\">Thành tiền</th></tr></thead><tbody>" + ticketRows + "</tbody></table><h2 style=\"font-size:18px;margin:24px 0 12px\">Biên nhận thanh toán</h2><table role=\"presentation\" width=\"100%\" style=\"font-size:14px;border-collapse:collapse;table-layout:fixed\">" + row("Mã giao dịch", payment.maGiaoDich) + row("Phương thức", method) + row("Thời điểm", paidAt) + row("Tổng tiền vé", money(order.tongTien)) + row("Giảm giá", money(order.tienGiam)) + "</table><div style=\"margin-top:12px;padding:18px;border-radius:12px;background:#eff6ff;color:#1d4ed8;font-weight:700\">Đã thanh toán <span style=\"display:block;text-align:right;margin-top:6px;font-size:22px\">" + escape(money(payment.soTien)) + "</span></div><p style=\"font-size:13px;line-height:1.7;color:#475569;margin:22px 0 0\">Vui lòng lưu email này và mã vé để đối chiếu khi lên xe. Mỗi vé có ảnh QR đính kèm tên Ve-&lt;mã vé&gt;.png; mở ảnh tương ứng khi cần xuất trình vé. Có mặt tại điểm đón trước giờ khởi hành theo hướng dẫn của nhà xe.</p><p style=\"font-size:12px;line-height:1.6;color:#64748b\">Đây là biên nhận đặt vé, không thay thế hóa đơn điện tử VAT.</p></td></tr><tr><td style=\"padding:20px 24px;background:#f8fafc;border-top:1px solid #e2e8f0;font-size:12px;color:#64748b\">Vexexin — Hỗ trợ khách hàng · Chúc bạn một hành trình an toàn.</td></tr></table></td></tr></table></body></html>";
        String text = "VEXEXIN — " + title + "\n" + intro + "\nMã đơn: " + order.maDon + "\nNgười đặt: " + order.khachDatTen + "\nĐiện thoại: " + order.khachDatSoDienThoai + "\nHành trình: " + from + " → " + to + "\nKhởi hành: " + departure + "\nĐến dự kiến: " + arrival + "\nNhà xe: " + value(operator) + "\nBiển số: " + value(vehicle) + plain + "\nTổng tiền vé: " + money(order.tongTien) + "\nGiảm giá: " + money(order.tienGiam) + "\nĐã thanh toán: " + money(payment.soTien) + "\nPhương thức: " + method + "\nMã giao dịch: " + payment.maGiaoDich + "\nThanh toán lúc: " + value(paidAt) + "\nẢnh QR của từng vé được đính kèm trong email, tên Ve-<mã vé>.png.\nĐây là biên nhận đặt vé, không thay thế hóa đơn VAT.";
        return new Content((sample ? "[KIỂM THỬ] " : "") + "Vexexin | " + title + " | " + order.maDon, html, text);
    }
}
