package com.datvexe.controller;

import com.datvexe.dto.HoldSeatRequest;
import com.datvexe.dto.HoldSeatResponse;
import com.datvexe.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/api/booking/hold")
public class BookingController extends HttpServlet { // Đã đổi tên class tại đây

    private BookingService bookingService = new BookingService();
    private ObjectMapper mapper;

    @Override
    public void init() throws ServletException {
        // Khởi tạo thư viện Jackson JSON và nạp module xử lý ngày giờ
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        try {
            // Parse JSON từ Frontend gửi lên thành Object
            HoldSeatRequest request = mapper.readValue(req.getInputStream(), HoldSeatRequest.class);

            if (request.getDanhSachMaGhe() == null || request.getDanhSachMaGhe().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"message\": \"Bạn chưa chọn ghế nào.\"}");
                return;
            }

            // Xử lý logic và trả về JSON
            HoldSeatResponse holdResponse = bookingService.holdSeats(request);
            mapper.writeValue(resp.getWriter(), holdResponse);

        } catch (RuntimeException e) {
            resp.setStatus(409); // Báo lỗi Conflict (Trùng ghế)
            resp.getWriter().write("{\"message\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"message\": \"Lỗi hệ thống khi giữ ghế.\"}");
        }
    }
}