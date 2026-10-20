package com.datvexe.controller;

import com.datvexe.domain.Entities.*;
import com.datvexe.service.AssignmentService;
import jakarta.servlet.annotation.WebServlet;
import com.datvexe.security.RequestIdentity;
import com.datvexe.service.EmployeeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private EntityManagerFactory factory;
    private EmployeeController controller;
    private AssignmentController assignmentController;
    private static final String PROFILE = """
        {"maNhanVien":"E1","hoTen":"Nguyễn Văn An","soDienThoai":"0912345678",
         "email":"an@example.test","maNhaXe":"NX","loaiNhanVien":"PHU_XE",
         "trangThai":"DANG_LAM_VIEC"}
        """;

    @BeforeEach
    void setup() throws Exception {
        RequestIdentity.clear();
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
            "jakarta.persistence.jdbc.driver", "org.h2.Driver",
            "jakarta.persistence.jdbc.url", "jdbc:h2:mem:employee_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "jakarta.persistence.jdbc.user", "sa",
            "jakarta.persistence.jdbc.password", "",
            "hibernate.hbm2ddl.auto", "create-drop"));
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            NhaXe operator = new NhaXe();
            operator.maNhaXe = "NX";
            operator.tenNhaXe = "Nhà xe";
            em.persist(operator);
            em.getTransaction().commit();
        }
        controller = new EmployeeController();
        var field = EmployeeController.class.getDeclaredField("service");
        field.setAccessible(true);
        field.set(controller, new EmployeeService(factory));
        assignmentController = new AssignmentController();
        var assignmentField = AssignmentController.class.getDeclaredField("service");
        assignmentField.setAccessible(true);
        assignmentField.set(assignmentController, new AssignmentService(factory));
    }

    @AfterEach
    void close() {
        RequestIdentity.clear();
        factory.close();
    }

    @Test
    void crudPreservesJsonFieldsAndHttpStatuses() throws Exception {
        Reply created = call("POST", null, PROFILE, Map.of());
        assertEquals(201, created.status);
        assertEquals("E1", created.body().path("maNhanVien").asText());
        assertTrue(created.body().has("ngaySinh"));
        assertTrue(created.body().path("ngaySinh").isNull());
        assertEquals(10, created.body().size());

        Reply updated = call("PUT", "/E1", PROFILE.replace("Nguyễn Văn An", "Nguyễn Văn Bình"), Map.of());
        assertEquals(200, updated.status);
        assertEquals("Nguyễn Văn Bình", call("GET", "/E1", "", Map.of()).body().path("hoTen").asText());
        assertEquals(1, call("GET", null, "", Map.of("q", "BÌNH", "maNhaXe", "NX")).body().size());
        assertEquals(0, call("GET", "/E1/phan-cong", "", Map.of()).body().size());

        Reply deleted = call("DELETE", "/E1", "", Map.of());
        assertEquals(204, deleted.status);
        assertEquals(0, deleted.bytes.size());
        assertEquals(404, call("GET", "/E1", "", Map.of()).status);
    }

    @Test
    void invalidJsonAndScalarCoercionAreRejectedWithoutSaving() throws Exception {
        assertEquals(400, call("POST", null, "{", Map.of()).status);
        assertEquals(400, call("POST", null, "null", Map.of()).status);
        assertEquals(400, call("POST", null, PROFILE.replace("\"PHU_XE\"", "1"), Map.of()).status);
        assertEquals(400, call("POST", null, PROFILE.replace("\"Nguyễn Văn An\"", "42"), Map.of()).status);
        assertEquals(0, call("GET", null, "", Map.of()).body().size());
    }

    @Test
    void invalidRoutesAndDuplicateEmployeeKeepExistingErrorContract() throws Exception {
        assertEquals(404, call("POST", "/E1", PROFILE, Map.of()).status);
        assertEquals(404, call("GET", "/E1/unknown", "", Map.of()).status);
        assertEquals(404, call("PATCH", null, "{}", Map.of()).status);
        assertEquals(201, call("POST", null, PROFILE, Map.of()).status);
        Reply duplicate = call("POST", null, PROFILE, Map.of());
        assertEquals(409, duplicate.status);
        assertEquals("INVALID_STATE_TRANSITION", duplicate.body().path("code").asText());
        assertEquals(1, call("GET", null, "", Map.of()).body().size());
        assertEquals(400, call("GET", "/E1/dieu-kien", "", Map.of("vaiTro", "INVALID")).status);
    }

    @Test
    void assignmentCrudKeepsExistingHttpAndJsonContract() throws Exception {
        assertEquals(201, call("POST", null, PROFILE, Map.of()).status);
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            TinhThanh city = new TinhThanh();
            city.maTinhThanh = "CITY"; city.tenTinhThanh = "City"; em.persist(city);
            BenXe first = new BenXe();
            first.maBenXe = "S1"; first.tenBenXe = "First"; first.diaChi = "First";
            first.tinhThanh = city; em.persist(first);
            BenXe last = new BenXe();
            last.maBenXe = "S2"; last.tenBenXe = "Last"; last.diaChi = "Last";
            last.tinhThanh = city; em.persist(last);
            TuyenXe route = new TuyenXe();
            route.maTuyen = "R"; route.tenTuyen = "Route";
            route.tramKhoiHanh = first; route.tramDen = last;
            route.nhaXe = em.find(NhaXe.class, "NX");
            route.giaCoBan = java.math.BigDecimal.TEN; em.persist(route);
            ChuyenXe trip = new ChuyenXe();
            trip.maChuyen = "C1"; trip.tuyenXe = route;
            trip.ngayKhoiHanh = java.time.LocalDate.of(2030, 1, 1);
            trip.gioKhoiHanh = java.time.LocalTime.of(8, 0);
            trip.gioDenDuKien = trip.ngayKhoiHanh.atTime(12, 0); em.persist(trip);
            em.getTransaction().commit();
        }
        String payload = """
            {"maPhanCong":"P1","maChuyen":"C1","maNhanVien":"E1","vaiTro":"PHU_XE"}
            """;
        Reply created = callAssignment("POST", null, payload);
        assertEquals(201, created.status);
        assertEquals(5, created.body().size());
        assertTrue(created.body().path("ghiChu").isNull());
        assertEquals("P1", created.body().path("maPhanCong").asText());
        assertEquals(1, callAssignment("GET", null, "").body().size());
        assertEquals("E1", callAssignment("GET", "/P1", "").body().path("maNhanVien").asText());
        assertEquals(409, callAssignment("POST", null, payload).status);
        Reply updated = callAssignment("PUT", "/P1", payload.replace("}", ",\"ghiChu\":\"Updated\"}"));
        assertEquals(200, updated.status);
        assertEquals("Updated", updated.body().path("ghiChu").asText());
        assertEquals(409, callAssignment("PUT", "/P1", payload.replace("PHU_XE", "TAI_XE_CHINH")).status);
        assertEquals("PHU_XE", callAssignment("GET", "/P1", "").body().path("vaiTro").asText());
        Reply deleted = callAssignment("DELETE", "/P1", "");
        assertEquals(204, deleted.status);
        assertEquals(0, deleted.bytes.size());
        assertEquals(404, callAssignment("GET", "/P1", "").status);
        assertEquals(0, callAssignment("GET", null, "").body().size());
    }

    @Test
    void assignmentRejectsInvalidBodyAndUnsupportedRoutes() throws Exception {
        assertEquals(400, callAssignment("POST", null, "null").status);
        assertEquals(400, callAssignment("POST", null, "{").status);
        assertEquals(400, callAssignment("POST", null, "{\"maPhanCong\":42}").status);
        assertEquals(400, callAssignment("POST", null, "{\"vaiTro\":1}").status);
        assertEquals(405, callAssignment("POST", "/P1", "{}").status);
        assertEquals(405, callAssignment("PUT", null, "{}").status);
        assertEquals(405, callAssignment("DELETE", null, "").status);
        assertEquals(405, callAssignment("PATCH", null, "{}").status);
        assertEquals(404, callAssignment("GET", "/P1/unknown", "").status);
    }

    @Test
    void assignmentHasExactlyOneServletOwner() {
        assertArrayEquals(new String[]{"/api/phan-cong", "/api/phan-cong/*"},
            AssignmentController.class.getAnnotation(WebServlet.class).urlPatterns());
        for (String path : VehicleController.class.getAnnotation(WebServlet.class).urlPatterns()) {
            assertFalse(path.startsWith("/api/phan-cong"));
        }
    }

    private Reply callAssignment(String method, String path, String body) throws Exception {
        return call(method, path, body, Map.of(), true);
    }

    private Reply call(String method, String path, String body, Map<String, String> query) throws Exception {
        return call(method, path, body, query, false);
    }

    private Reply call(String method, String path, String body, Map<String, String> query,
                       boolean assignment) throws Exception {
        var input = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        var stream = new ServletInputStream() {
            @Override public int read() { return input.read(); }
            @Override public boolean isFinished() { return input.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };
        var request = (HttpServletRequest) Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
            (proxy, operation, args) -> switch (operation.getName()) {
                case "getMethod" -> method;
                case "getPathInfo" -> path;
                case "getParameter" -> query.get((String) args[0]);
                case "getInputStream" -> stream;
                default -> throw new UnsupportedOperationException(operation.getName());
            });
        Reply reply = new Reply();
        var output = new ServletOutputStream() {
            @Override public void write(int value) { reply.bytes.write(value); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) {}
        };
        var response = (HttpServletResponse) Proxy.newProxyInstance(
            HttpServletResponse.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
            (proxy, operation, args) -> {
                switch (operation.getName()) {
                    case "setStatus" -> reply.status = (Integer) args[0];
                    case "setHeader" -> reply.headers.put((String) args[0], (String) args[1]);
                    case "getHeader" -> { return reply.headers.get((String) args[0]); }
                    case "getOutputStream" -> { return output; }
                    case "setCharacterEncoding", "setContentType" -> { }
                    default -> throw new UnsupportedOperationException(operation.getName());
                }
                return null;
            });
        if (assignment) {
            assignmentController.service(request, response);
        } else {
            controller.service(request, response);
        }
        return reply;
    }

    private class Reply {
        private int status = 200;
        private final Map<String, String> headers = new HashMap<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        JsonNode body() throws Exception {
            return json.readTree(bytes.toByteArray());
        }
    }
}
