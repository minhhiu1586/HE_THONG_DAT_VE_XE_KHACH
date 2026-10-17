package com.datvexe.controller;

import com.datvexe.service.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemovedInspectionTest {
    @Test void boardingEndpointIsNotAvailable() {
        var request=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
            new Class<?>[]{HttpServletRequest.class},(proxy,method,args)->switch(method.getName()) {
                case "getPathInfo" -> "/V1/len-xe";
                case "getMethod" -> "POST";
                default -> null;
            });
        assertEquals(404,assertThrows(DomainException.class,()->new VeXeController().service(request,null)).status());
    }
    @Test void inspectionServletAndServiceAreNotPackaged() {
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.datvexe.controller.KiemVeController"));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.datvexe.service.KiemVeService"));
    }
}
