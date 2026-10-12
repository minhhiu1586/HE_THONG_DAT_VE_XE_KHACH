package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.domain.States.TrangThaiChuyen;
import com.datvexe.dto.TripInputs;
import com.datvexe.security.ApiErrors;
import com.datvexe.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.LocalDate;

@WebServlet(urlPatterns={"/api/quan-ly/chuyen-xe","/api/quan-ly/chuyen-xe/*"})
public class TripController extends HttpServlet {
    private TripService service;
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    public TripController() {
        json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    }
    @Override public void init(){service=new TripService(Jpa.factory());}
    private TripInputs.Draft body(HttpServletRequest request)throws IOException {
        var value=json.readValue(request.getInputStream(),TripInputs.Draft.class);
        if(value==null)throw new DomainException(400,"Thiếu nội dung yêu cầu.");return value;
    }
    private static LocalDate date(String value){return value==null || value.isBlank()?null:LocalDate.parse(value);}
    private static int number(String value,int fallback){return value==null?fallback:Integer.parseInt(value);}
    @Override protected void service(HttpServletRequest req,HttpServletResponse res)throws IOException {
        res.setCharacterEncoding("UTF-8");res.setContentType("application/json; charset=UTF-8");
        try {
            String path=req.getPathInfo();String[] p=path==null || path.equals("/")?new String[0]:path.substring(1).split("/");
            String method=req.getMethod();Object result;int status=200;
            if(p.length==0 && method.equals("GET"))result=service.list(req.getParameter("maNhaXe"),req.getParameter("maTuyen"),req.getParameter("maXe"),date(req.getParameter("tuNgay")),date(req.getParameter("denNgay")),
                req.getParameter("trangThai")==null || req.getParameter("trangThai").isBlank()?null:TrangThaiChuyen.valueOf(req.getParameter("trangThai")),number(req.getParameter("page"),1),number(req.getParameter("size"),20));
            else if(p.length==0 && method.equals("POST")){result=service.create(body(req));status=201;}
            else if(p.length==1 && method.equals("GET"))result=service.get(p[0]);
            else if(p.length==1 && method.equals("PUT"))result=service.update(p[0],body(req));
            else if(p.length==1 && method.equals("DELETE")){service.delete(p[0]);result=null;status=204;}
            else if(p.length==2 && p[1].equals("dieu-kien-mo-ban") && method.equals("GET"))result=service.eligibility(p[0]);
            else if(p.length==2 && p[1].equals("mo-ban") && method.equals("POST"))result=service.open(p[0]);
            else if(p.length==2 && p[1].equals("huy") && method.equals("POST"))result=service.cancel(p[0]);
            else throw new DomainException(404,"API không tồn tại.");
            res.setStatus(status);if(result!=null)json.writeValue(res.getOutputStream(),result);
        } catch(DomainException error){ApiErrors.write(res,error);}
        catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException | java.time.format.DateTimeParseException error){ApiErrors.write(res,new DomainException(400,"Dữ liệu yêu cầu không hợp lệ."));}
        catch(jakarta.persistence.PersistenceException error){
            getServletContext().log("Trip database error",error);boolean constraint=false;
            for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof org.hibernate.exception.ConstraintViolationException)constraint=true;
            ApiErrors.write(res,new DomainException(constraint?409:500,constraint?"Dữ liệu chuyến bị trùng hoặc vi phạm ràng buộc.":"Không thể xử lý chuyến lúc này."));
        }
    }
}
