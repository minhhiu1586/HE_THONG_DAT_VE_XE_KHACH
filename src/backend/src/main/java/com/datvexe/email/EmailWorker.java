package com.datvexe.email;

import com.datvexe.config.Jpa;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import java.time.Clock;
import java.util.concurrent.*;

@WebListener
public final class EmailWorker implements ServletContextListener {
    private ScheduledExecutorService executor;
    @Override public void contextInitialized(ServletContextEvent event) {
        BrevoClient client = new BrevoClient(BrevoClient.Config.fromEnvironment());
        if (!client.ready()) { event.getServletContext().log("Receipt email delivery is disabled or not configured."); return; }
        EmailDispatcher dispatcher = new EmailDispatcher(Jpa.factory(), client::send, Clock.systemUTC());
        executor = Executors.newSingleThreadScheduledExecutor(task -> { Thread thread = new Thread(task, "booking-receipt-email"); thread.setDaemon(true); return thread; });
        executor.scheduleWithFixedDelay(() -> {
            try { dispatcher.runOnce(); }
            catch (RuntimeException e) { event.getServletContext().log("Receipt email worker failed: " + e.getClass().getSimpleName()); }
        }, 3, 5, TimeUnit.SECONDS);
    }
    @Override public void contextDestroyed(ServletContextEvent event) {
        if (executor != null) {
            executor.shutdownNow();
            try { executor.awaitTermination(20, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
