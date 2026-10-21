package com.datvexe.controller;

import com.datvexe.service.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemovedInspectionTest {
    @Test
    void boardingEndpointIsNotAvailable() {
        var request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPathInfo" -> "/V1/len-xe";
                    case "getMethod" -> "POST";
                    default -> null;
                }
        );

        var response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> null
        );

        DomainException error = assertThrows(
                DomainException.class,
                () -> new VeXeController().service(request, response)
        );

        assertEquals(404, error.status());
    }
    @Test void inspectionServletAndServiceAreNotPackaged() {
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.datvexe.controller.KiemVeController"));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.datvexe.service.KiemVeService"));
    }
}
