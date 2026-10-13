package com.datvexe.controller;

import com.datvexe.dto.BookingDto.PassengerRequest;
import com.datvexe.dto.PricingDto;
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
    private static final String QUOTE_ATTRIBUTE_PREFIX = "datvexe.booking.quote.";

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
        clearQuote(session, userId, orderId);
        String prefix = responsePrefix(userId, orderPath(orderId));
        Enumeration<String> names = session.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith(prefix)) session.removeAttribute(name);
        }
    }

    /** Returns the latest quote for this authenticated user and order, if it is still in the session. */
    public static PricingDto.QuoteSnapshot quote(HttpSession session, String userId, String orderId) {
        String attribute = quoteAttribute(userId, orderId);
        Object value = session.getAttribute(attribute);
        if (!(value instanceof PricingDto.QuoteSnapshot snapshot)
                || !java.util.Objects.equals(userId, snapshot.userId())
                || !java.util.Objects.equals(orderId, snapshot.maDon())) {
            if (value != null) session.removeAttribute(attribute);
            return null;
        }
        return snapshot;
    }

    /** Saves only the latest quote for this user/order pair. */
    public static void saveQuote(
            HttpSession session,
            String userId,
            String orderId,
            PricingDto.QuoteSnapshot snapshot) {
        if (snapshot == null) {
            clearQuote(session, userId, orderId);
            return;
        }
        if (!java.util.Objects.equals(userId, snapshot.userId())
                || !java.util.Objects.equals(orderId, snapshot.maDon())) {
            throw new IllegalArgumentException("Quote does not belong to the requested booking.");
        }
        session.setAttribute(quoteAttribute(userId, orderId), snapshot);
    }

    public static void clearQuote(HttpSession session, String userId, String orderId) {
        session.removeAttribute(quoteAttribute(userId, orderId));
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

    private static String quoteAttribute(String userId, String orderId) {
        return QUOTE_ATTRIBUTE_PREFIX + encode(userId) + "." + encode(orderId);
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
