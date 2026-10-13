package com.datvexe.controller;

import com.datvexe.dto.BookingDto.PassengerRequest;
import com.datvexe.security.AuthFilter;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSession;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Base64;
import java.util.Enumeration;

/** Per-user passenger draft state stored in the authenticated HTTP session. */
@WebListener
public final class BookingSession implements ServletContextListener {
    private static final String ATTRIBUTE_PREFIX = "datvexe.booking.passengers.";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        SessionCookieConfig cookie = event.getServletContext().getSessionCookieConfig();
        cookie.setHttpOnly(true);
        cookie.setSecure(AuthFilter.secureCookies());
        cookie.setAttribute("SameSite", "Lax");
    }

    public static List<PassengerRequest> passengers(HttpSession session, String userId, String orderId) {
        Object value = session.getAttribute(attribute(userId, orderId));
        if (!(value instanceof StoredPassengers stored) || !stored.userId().equals(userId)) {
            if (value != null) session.removeAttribute(attribute(userId, orderId));
            return List.of();
        }
        return stored.passengers();
    }

    public static void update(HttpSession session, String userId, String orderId, List<PassengerRequest> passengers) {
        String attribute = attribute(userId, orderId);
        if (passengers == null || passengers.isEmpty()) {
            session.removeAttribute(attribute);
            return;
        }
        session.setAttribute(attribute, new StoredPassengers(userId, passengers));
    }

    public static void remove(HttpSession session, String userId, String orderId) {
        session.removeAttribute(attribute(userId, orderId));
        String prefix = responsePrefix(userId, orderPath(orderId));
        Enumeration<String> names = session.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith(prefix)) session.removeAttribute(name);
        }
    }

    public static void cachePatchResponse(
            HttpSession session, String userId, String path, String key, String response) {
        if (key != null && response != null) {
            session.setAttribute(responseAttribute(userId, path, key), response);
        }
    }

    public static String cachedPatchResponse(HttpSession session, String userId, String path, String key) {
        if (key == null) return null;
        Object value = session.getAttribute(responseAttribute(userId, path, key));
        return value instanceof String response ? response : null;
    }

    private static String attribute(String userId, String orderId) {
        return ATTRIBUTE_PREFIX + userId + "." + orderId;
    }

    private static String responseAttribute(String userId, String path, String key) {
        return responsePrefix(userId, path) + encode(key);
    }

    private static String responsePrefix(String userId, String path) {
        return ATTRIBUTE_PREFIX + "patch-response." + userId + "." + encode(path) + ".";
    }

    private static String orderPath(String orderId) {
        return "/api/booking/" + orderId;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record StoredPassengers(String userId, List<PassengerRequest> passengers) implements Serializable {
        private StoredPassengers {
            passengers = List.copyOf(passengers);
        }
    }
}
