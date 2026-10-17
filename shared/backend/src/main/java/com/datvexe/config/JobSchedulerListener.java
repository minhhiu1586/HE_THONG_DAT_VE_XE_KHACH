package com.datvexe.config;

import com.datvexe.service.BookingService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@WebListener
public class JobSchedulerListener implements ServletContextListener {

    private ScheduledExecutorService scheduler;
    private BookingService bookingService = new BookingService();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Tạo một luồng chạy ngầm
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Cấu hình: Trì hoãn 1 phút trước khi chạy lần đầu, sau đó lặp lại mỗi 1 phút
        scheduler.scheduleAtFixedRate(() -> {
            try {
                bookingService.releaseExpiredHolds();
            } catch (Exception e) {
                e.printStackTrace(); // Ghi log nếu lỗi để không chết luồng ngầm
            }
        }, 1, 1, TimeUnit.MINUTES);

        System.out.println("[Scheduler] Đã khởi động luồng tự động dọn dẹp ghế.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Khi bạn tắt Server (Tomcat), luồng này sẽ được dọn dẹp sạch sẽ
        if (scheduler != null) {
            scheduler.shutdownNow();
            System.out.println("[Scheduler] Đã tắt luồng dọn dẹp ghế.");
        }
    }
}