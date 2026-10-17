package com.datvexe.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class AppLifecycle implements ServletContextListener {
    @Override public void contextDestroyed(ServletContextEvent event) { Jpa.close(); }
}
