package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.EmployeeInputs;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import jakarta.persistence.PersistenceException;
import java.io.IOException;

@WebServlet(urlPatterns={"/api/nhan-vien","/api/nhan-vien/*"})
public class EmployeeController extends HttpServlet {
    private EmployeeService service;
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    public EmployeeController() {
        json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    }
    @Override public void init() { service=new EmployeeService(Jpa.factory()); }
    @Override protected void service(HttpServletRequest req,HttpServletResponse res) throws IOException {
        res.setCharacterEncoding("UTF-8"); res.setContentType("application/json; charset=UTF-8");
        try {
            String path=req.getPathInfo(); String[] parts=path==null || path.equals("/")?new String[0]:path.substring(1).split("/");
            String method=req.getMethod(); Object result; int status=200;
            if(method.equals("GET") && parts.length==0) result=service.list(req.getParameter("q"),req.getParameter("maNhaXe"),req.getParameter("loaiNhanVien"),req.getParameter("trangThai"));
            else if(method.equals("GET") && parts.length==1) result=service.get(parts[0]);
            else if(method.equals("GET") && parts.length==2 && parts[1].equals("phan-cong")) result=service.assignments(parts[0]);
            else if(method.equals("GET") && parts.length==2 && parts[1].equals("dieu-kien")) result=service.eligibility(parts[0],req.getParameter("maChuyen"),
                req.getParameter("vaiTro")==null?null:com.datvexe.domain.States.VaiTroChuyen.valueOf(req.getParameter("vaiTro")));
            else if(method.equals("POST") && parts.length==0) { result=service.save(null,json.readValue(req.getInputStream(),EmployeeInputs.Employee.class)); status=201; }
            else if(method.equals("PUT") && parts.length==1) result=service.save(parts[0],json.readValue(req.getInputStream(),EmployeeInputs.Employee.class));
            else if(method.equals("DELETE") && parts.length==1) { service.delete(parts[0]); result=null; status=204; }
            else throw new DomainException(404,"API không tồn tại.");
            res.setStatus(status); if(result!=null) json.writeValue(res.getOutputStream(),result);
        } catch(DomainException error) { ApiErrors.write(res,error); }
        catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) { ApiErrors.write(res,new DomainException(400,"Dữ liệu yêu cầu không hợp lệ.")); }
        catch(PersistenceException error) {
            getServletContext().log("Employee database failure",error);
            boolean constraint=false;
            for(Throwable cause=error;cause!=null;cause=cause.getCause())
                if(cause instanceof org.hibernate.exception.ConstraintViolationException) constraint=true;
            ApiErrors.write(res,new DomainException(constraint?409:500,constraint?
                "Dữ liệu xung đột; vui lòng kiểm tra lại hồ sơ.":"Không thể xử lý dữ liệu lúc này."));
        }
    }
}
