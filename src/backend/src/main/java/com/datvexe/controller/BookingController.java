package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.dto.BookingDto;
import com.datvexe.dto.BookingDto.OrderResponse;
import com.datvexe.dto.BookingDto.PassengerRequest;
import com.datvexe.dto.HoldSeatRequest;
import com.datvexe.dto.HoldSeatResponse;
import com.datvexe.dto.PricingDto;
import com.datvexe.security.AccountException;
import com.datvexe.security.RequestIdentity;
import com.datvexe.service.BookingService;
import com.datvexe.service.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@WebServlet(urlPatterns = {"/api/booking", "/api/booking/*"})
public class BookingController extends HttpServlet {
    private static final String ROOT = "/api/booking";
    private static final String SESSION_REQUEST_ATTRIBUTE = BookingController.class.getName() + ".session";
    private static final String USER_REQUEST_ATTRIBUTE = BookingController.class.getName() + ".userId";
    private static final Set<String> HOLD_FIELDS = Set.of("maChuyen", "danhSachMaGhe");
    private static final Set<String> PASSENGER_FIELDS = Set.of("maGhe", "hoTen", "soDienThoai", "cccd");
    private static final Set<String> PATCH_FIELDS = Set.of("khachDatTen", "khachDatSoDienThoai", "ghiChu");

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private BookingService service;

    @Override
    public void init() {
        service = new BookingService(Jpa.factory());
    }

    /** HttpServlet has no PATCH hook, so route and dispatch the supported verbs here. */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Route route = route(request);
        String method = request.getMethod();

        if (method.equals("OPTIONS")) {
            requireNoQuery(request);
            response.setHeader("Allow", route.allow());
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (!route.allows(method)) throw methodNotAllowed(response, route);
        requireNoQuery(request);

        // Holding seats has its own idempotency lock. Do not serialize these requests on the session.
        if (route.kind() == Kind.HOLD) {
            doPost(request, response);
            return;
        }

        String userId = authenticatedUserId();
        HttpSession session = request.getSession(true);
        synchronized (session) {
            request.setAttribute(SESSION_REQUEST_ATTRIBUTE, session);
            request.setAttribute(USER_REQUEST_ATTRIBUTE, userId);
            try {
                dispatch(request, response, method);
            } catch (AccountException error) {
                if (error.code().equals("HOLD_EXPIRED")) {
                    BookingSession.remove(session, userId, route.maDon());
                }
                throw error;
            }
        }
    }

    private void dispatch(HttpServletRequest request, HttpServletResponse response, String method)
            throws ServletException, IOException {
        switch (method) {
            case "GET", "HEAD" -> super.service(request, response);
            case "POST" -> doPost(request, response);
            case "PATCH" -> doPatch(request, response);
            case "PUT" -> doPut(request, response);
            case "DELETE" -> doDelete(request, response);
            default -> throw methodNotAllowed(response, route(request));
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Route route = route(request);
        List<PassengerRequest> passengers = passengers(request, route.maDon());
        writeOrder(request, response, route, service.getOrder(route.maDon(), passengers));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Route route = route(request);
        if (route.kind() == Kind.HOLD) {
            JsonNode body = readObject(request, HOLD_FIELDS);
            checkStrings(body, "maChuyen");
            JsonNode seats = body.get("danhSachMaGhe");
            if (seats != null && !seats.isNull()) {
                if (!seats.isArray()) throw invalid("danhSachMaGhe", "Danh sách ghế phải là một mảng.");
                for (JsonNode seat : seats) {
                    if (!seat.isTextual()) throw invalid("danhSachMaGhe", "Mỗi mã ghế phải là chuỗi.");
                }
            }
            HoldSeatRequest input = convert(body, HoldSeatRequest.class);
            BookingDto.Result result = service.holdSeats(input, request.getHeader("Idempotency-Key"));
            setHoldLocation(request, response, result);
            write(response, result);
            return;
        }
        if (route.kind() == Kind.QUOTE) {
            JsonNode body = readObject(request, Set.of("maKhuyenMai"));
            checkStrings(body, "maKhuyenMai");
            PricingDto.QuoteSnapshot snapshot = service.baoGia(
                    route.maDon(),
                    convert(body, PricingDto.QuoteRequest.class),
                    passengers(request, route.maDon()));
            BookingSession.saveQuote(session(request), userId(request), route.maDon(), snapshot);
            writeJson(response, HttpServletResponse.SC_OK, snapshot.response());
            return;
        }
        if (route.kind() == Kind.FREEZE) {
            JsonNode body = readObject(request, Set.of("maBaoGia"));
            checkStrings(body, "maBaoGia");
            BookingDto.Result result = service.chotGia(
                    route.maDon(),
                    convert(body, PricingDto.FreezeRequest.class),
                    request.getHeader("Idempotency-Key"),
                    passengers(request, route.maDon()),
                    BookingSession.quote(session(request), userId(request), route.maDon()));
            write(response, result);
            return;
        }
        requireEmptyBody(request, "Hủy đơn chỉ nhận body vắng hoặc object rỗng.");
        writeOrder(request, response, route,
                service.cancelOrder(route.maDon(), request.getHeader("Idempotency-Key")));
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Route route = route(request);
        JsonNode body = readObject(request, PATCH_FIELDS);
        checkStrings(body, "khachDatTen", "khachDatSoDienThoai", "ghiChu");
        for (String field : Set.of("khachDatTen", "khachDatSoDienThoai")) {
            if (body.has(field) && body.get(field).isNull()) {
                throw invalid(field, "Trường này không được để trống.");
            }
        }
        BookingDto.Result result = service.updateContact(
                route.maDon(), body, request.getHeader("Idempotency-Key"),
                passengers(request, route.maDon()));
        writePatchOrder(request, response, route, result);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Route route = route(request);
        switch (route.kind()) {
            case PASSENGERS -> {
                JsonNode body = readObject(request, Set.of("hanhKhach"));
                JsonNode passengers = body.get("hanhKhach");
                if (passengers != null && !passengers.isNull()) {
                    if (!passengers.isArray()) throw invalid("hanhKhach", "Danh sách hành khách phải là một mảng.");
                    for (JsonNode passenger : passengers) {
                        if (!passenger.isObject()) throw invalid("hanhKhach", "Mỗi hành khách phải là một object.");
                        requireAllowed(passenger, PASSENGER_FIELDS);
                        checkStrings(passenger, "maGhe", "hoTen", "soDienThoai", "cccd");
                    }
                }
                writeOrder(request, response, route, service.savePassengers(
                        route.maDon(), convert(body, BookingDto.PassengersRequest.class)));
            }
            case CHANGE_SEAT -> {
                JsonNode body = readObject(request, Set.of("maGheCu", "maGheMoi"));
                checkStrings(body, "maGheCu", "maGheMoi");
                writeOrder(request, response, route, service.changeSeat(
                        route.maDon(), convert(body, BookingDto.ChangeSeatRequest.class),
                        passengers(request, route.maDon())));
            }
            case PROMOTION -> {
                JsonNode body = readObject(request, Set.of("maKhuyenMai"));
                checkStrings(body, "maKhuyenMai");
                writeOrder(request, response, route, service.applyPromotion(
                        route.maDon(), convert(body, BookingDto.PromotionRequest.class),
                        passengers(request, route.maDon())));
            }
            default -> throw methodNotAllowed(response, route);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Route route = route(request);
        requireEmptyBody(request, "Đường dẫn này không nhận body.");
        switch (route.kind()) {
            case SEAT -> writeOrder(request, response, route,
                    service.removeSeat(route.maDon(), route.maGhe(), passengers(request, route.maDon())));
            case PROMOTION -> writeOrder(request, response, route,
                    service.removePromotion(route.maDon(), passengers(request, route.maDon())));
            default -> throw methodNotAllowed(response, route);
        }
    }

    private Route route(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!ROOT.equals(path)) throw notFound();
        String info = request.getPathInfo();
        if (info == null || info.isEmpty() || info.equals("/")) throw notFound();
        String[] part = info.substring(1).split("/", -1);

        if (part.length == 1 && part[0].equals("hold")) return new Route(Kind.HOLD, null, null);
        if (part.length == 1 && resourceCode(part[0])) return new Route(Kind.ORDER, part[0], null);
        if (part.length == 2 && resourceCode(part[0])) {
            Kind kind = switch (part[1]) {
                case "hanh-khach" -> Kind.PASSENGERS;
                case "doi-ghe" -> Kind.CHANGE_SEAT;
                case "khuyen-mai" -> Kind.PROMOTION;
                case "huy" -> Kind.CANCEL;
                case "bao-gia" -> Kind.QUOTE;
                case "chot-gia" -> Kind.FREEZE;
                default -> null;
            };
            if (kind != null) return new Route(kind, part[0], null);
        }
        if (part.length == 3 && resourceCode(part[0]) && part[1].equals("ghe") && resourceCode(part[2])) {
            return new Route(Kind.SEAT, part[0], part[2]);
        }
        throw notFound();
    }

    private boolean resourceCode(String value) {
        return value != null && value.length() <= 40 && value.matches("[A-Za-z0-9_-]{1,40}");
    }

    private void requireNoQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw invalid("query", "Đường dẫn này không hỗ trợ tham số query.");
    }

    private void requireAllowed(JsonNode body, Set<String> allowed) {
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid(field, "Trường này không được hỗ trợ.");
        });
    }

    private JsonNode readObject(HttpServletRequest request, Set<String> allowed) throws IOException {
        JsonNode body = readTree(request);
        if (body == null || !body.isObject()) throw invalid("body", "Body phải là một JSON object.");
        requireAllowed(body, allowed);
        return body;
    }

    private JsonNode readTree(HttpServletRequest request) throws IOException {
        try {
            JsonNode body = json.readTree(request.getInputStream());
            return body == null || body.isMissingNode() ? null : body;
        } catch (JsonProcessingException error) {
            throw invalid("body", "JSON sai định dạng, kiểu dữ liệu hoặc tên trường.");
        }
    }

    private void requireEmptyBody(HttpServletRequest request, String message) throws IOException {
        JsonNode body = readTree(request);
        if (body != null && (!body.isObject() || body.size() != 0)) throw invalid("body", message);
    }

    private void checkStrings(JsonNode body, String... fields) {
        for (String field : fields) {
            JsonNode value = body.get(field);
            if (value != null && !value.isNull() && !value.isTextual()) {
                throw invalid(field, "Trường này phải là chuỗi.");
            }
        }
    }

    private <T> T convert(JsonNode body, Class<T> type) {
        try {
            return json.treeToValue(body, type);
        } catch (JsonProcessingException error) {
            throw invalid("body", "JSON sai định dạng, kiểu dữ liệu hoặc tên trường.");
        }
    }

    private List<PassengerRequest> passengers(HttpServletRequest request, String orderId) {
        return BookingSession.passengers(session(request), userId(request), orderId);
    }

    private HttpSession session(HttpServletRequest request) {
        Object value = request.getAttribute(SESSION_REQUEST_ATTRIBUTE);
        if (value instanceof HttpSession session) return session;
        throw new IllegalStateException("Booking session was not initialized for this request.");
    }

    private String userId(HttpServletRequest request) {
        Object value = request.getAttribute(USER_REQUEST_ATTRIBUTE);
        return value instanceof String id ? id : authenticatedUserId();
    }

    private String authenticatedUserId() {
        RequestIdentity.Context identity = RequestIdentity.get();
        if (identity == null || identity.user() == null || identity.user().maNguoiDung() == null) {
            throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
        }
        return identity.user().maNguoiDung();
    }

    private void writeOrder(
            HttpServletRequest request,
            HttpServletResponse response,
            Route route,
            BookingDto.Result result) throws IOException {
        if (result.status() < 400 && result.body() instanceof OrderResponse order) {
            storePassengers(request, route, order);
            clearQuoteAfterMutation(request, route);
        }
        write(response, result);
    }

    private void writePatchOrder(
            HttpServletRequest request,
            HttpServletResponse response,
            Route route,
            BookingDto.Result result) throws IOException {
        String userId = userId(request);
        String key = request.getHeader("Idempotency-Key");
        String path = ROOT + "/" + route.maDon();
        HttpSession session = session(request);

        if (result.status() < 400 && result.body() instanceof OrderResponse order) {
            storePassengers(request, route, order);
            // PATCH is routed through the order resource, so invalidate explicitly.
            BookingSession.clearQuote(session(request), userId(request), route.maDon());
            String responseBody = json.writeValueAsString(order);
            BookingSession.cachePatchResponse(session, userId, path, key, responseBody);
            writeSerializedJson(response, result.status(), responseBody);
            return;
        }
        if (result.body() instanceof JsonNode) {
            String cached = BookingSession.cachedPatchResponse(session, userId, path, key);
            if (cached != null) {
                writeSerializedJson(response, result.status(), cached);
                return;
            }
        }
        write(response, result);
    }

    private void storePassengers(HttpServletRequest request, Route route, OrderResponse order) {
        List<PassengerRequest> passengers = Set.of(TrangThaiDon.CHO_XU_LY, TrangThaiDon.CHO_THANH_TOAN)
                .contains(order.trangThaiDon())
                ? order.hanhKhach() : List.of();
        BookingSession.update(session(request), userId(request), route.maDon(), passengers);
    }

    /** A successful real mutation invalidates the volatile quote after its transaction commits. */
    private void clearQuoteAfterMutation(HttpServletRequest request, Route route) {
        if (route.kind() == Kind.PASSENGERS
                || route.kind() == Kind.CHANGE_SEAT
                || route.kind() == Kind.SEAT
                || route.kind() == Kind.PROMOTION
                || route.kind() == Kind.CANCEL) {
            BookingSession.clearQuote(session(request), userId(request), route.maDon());
        }
    }

    private void setHoldLocation(HttpServletRequest request, HttpServletResponse response, BookingDto.Result result) {
        if (result.status() != HttpServletResponse.SC_CREATED) return;
        String orderId = null;
        if (result.body() instanceof HoldSeatResponse hold) {
            orderId = hold.maDon();
        } else if (result.body() instanceof JsonNode body && body.isObject()) {
            JsonNode value = body.get("maDon");
            if (value != null && value.isTextual()) orderId = value.asText();
        }
        if (resourceCode(orderId)) {
            response.setHeader("Location", request.getContextPath() + ROOT + "/" + orderId);
        }
    }

    private void write(HttpServletResponse response, BookingDto.Result result) throws IOException {
        response.setStatus(result.status());
        if (result.status() == HttpServletResponse.SC_NO_CONTENT || result.body() == null) return;
        writeJson(response, result.status(), result.body());
    }

    private void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        if (status == HttpServletResponse.SC_NO_CONTENT || body == null) return;
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), body);
    }

    private void writeSerializedJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        if (status == HttpServletResponse.SC_NO_CONTENT || body == null) return;
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(body);
    }

    private DomainException methodNotAllowed(HttpServletResponse response, Route route) {
        response.setHeader("Allow", route.allow());
        return new DomainException(405, "Phương thức không được hỗ trợ tại đường dẫn này.");
    }

    private DomainException notFound() {
        return new DomainException(404, "API không tồn tại.");
    }

    private AccountException invalid(String field, String message) {
        return AccountException.invalid(field, message);
    }

    private enum Kind { HOLD, ORDER, PASSENGERS, CHANGE_SEAT, SEAT, PROMOTION, CANCEL, QUOTE, FREEZE }

    private record Route(Kind kind, String maDon, String maGhe) {
        boolean allows(String method) {
            return switch (kind) {
                case HOLD -> method.equals("POST");
                case ORDER -> Set.of("GET", "HEAD", "PATCH").contains(method);
                case PASSENGERS, CHANGE_SEAT -> method.equals("PUT");
                case SEAT -> method.equals("DELETE");
                case PROMOTION -> Set.of("PUT", "DELETE").contains(method);
                case CANCEL, QUOTE, FREEZE -> method.equals("POST");
            };
        }

        String allow() {
            return switch (kind) {
                case HOLD -> "POST, OPTIONS";
                case ORDER -> "GET, HEAD, PATCH, OPTIONS";
                case PASSENGERS, CHANGE_SEAT -> "PUT, OPTIONS";
                case SEAT -> "DELETE, OPTIONS";
                case PROMOTION -> "PUT, DELETE, OPTIONS";
                case CANCEL, QUOTE, FREEZE -> "POST, OPTIONS";
            };
        }
    }
}
