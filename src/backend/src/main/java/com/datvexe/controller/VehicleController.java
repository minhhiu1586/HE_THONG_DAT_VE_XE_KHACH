package com.datvexe.controller;

import com.datvexe.config.Jpa;
import com.datvexe.dto.VehicleInputs;
import com.datvexe.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

@WebServlet(urlPatterns={"/api/loai-xe", "/api/loai-xe/*", "/api/xe", "/api/xe/*", "/api/phan-cong", "/api/phan-cong/*"})
public class VehicleController extends HttpServlet {
    private VehicleService service;
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    @Override public void init() { service=new VehicleService(Jpa.factory()); }
    private <T> T body(HttpServletRequest req,Class<T> type) throws IOException {
        T value=json.readValue(req.getInputStream(),type);
        if(value==null) throw new DomainException(400,"Thiếu nội dung yêu cầu"); return value;
    }
    @Override protected void service(HttpServletRequest req,HttpServletResponse res) throws IOException {
        res.setCharacterEncoding("UTF-8"); res.setContentType("application/json; charset=UTF-8");
        if("http://localhost:3000".equals(req.getHeader("Origin"))) {
            res.setHeader("Access-Control-Allow-Origin","http://localhost:3000");
            res.setHeader("Access-Control-Allow-Methods","GET, POST, PUT, DELETE, OPTIONS");
            res.setHeader("Access-Control-Allow-Headers","Content-Type");
        }
        if(req.getMethod().equals("OPTIONS")) { res.setStatus(204); return; }
        try {
            String kind=req.getServletPath().substring("/api/".length());
            String path=req.getPathInfo();
            String[] p=path==null || path.equals("/")?new String[0]:path.substring(1).split("/");
            String method=req.getMethod(); Object result; int status=200;
            if(kind.equals("xe") && p.length==3 && p[1].equals("dieu-kien") && method.equals("GET")) {
                result=service.vehicleEligibility(p[0],p[2]);
            } else if(kind.equals("xe") && p.length>=2 && p[1].equals("ghe")) {
                if(p.length==2 && method.equals("GET")) result=service.seats(p[0],false);
                else if(p.length==2 && method.equals("POST")) { result=service.createSeat(p[0],body(req,VehicleInputs.Ghe.class)); status=201; }
                else if(p.length==3 && p[2].equals("sinh") && method.equals("POST")) { result=service.seats(p[0],true); status=201; }
                else if(p.length==3 && method.equals("GET")) result=service.getSeat(p[0],p[2]);
                else if(p.length==3 && method.equals("DELETE")) { service.deleteSeat(p[0],p[2]); result=null; status=204; }
                else if(p.length==3 && method.equals("PUT")) result=service.updateSeat(p[0],p[2],body(req,VehicleInputs.Ghe.class));
                else throw new DomainException(404,"API không tồn tại");
            } else if(p.length<=1) {
                String id=p.length==0?null:p[0];
                if(method.equals("GET")) result=id==null?service.list(kind):service.get(kind,id);
                else if((method.equals("POST") && id==null) || (method.equals("PUT") && id!=null)) {
                    result=switch(kind) {
                        case "loai-xe" -> service.saveType(id,body(req,VehicleInputs.LoaiXe.class));
                        case "xe" -> service.saveVehicle(id,body(req,VehicleInputs.Xe.class));
                        case "phan-cong" -> service.saveAssignment(id,body(req,VehicleInputs.PhanCong.class));
                        default -> throw new DomainException(404,"API không tồn tại");
                    }; if(id==null) status=201;
                } else if(method.equals("DELETE") && id!=null) { service.delete(kind,id); result=null; status=204; }
                else throw new DomainException(405,"Phương thức không được hỗ trợ");
            } else throw new DomainException(404,"API không tồn tại");
            res.setStatus(status); if(result!=null) json.writeValue(res.getOutputStream(),result);
        } catch(DomainException e) { error(res,e.status(),e.getMessage()); }
        catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) { error(res,400,"Dữ liệu yêu cầu không hợp lệ"); }
        catch(jakarta.persistence.PersistenceException e) {
            getServletContext().log("Vehicle database failure",e);
            boolean constraint=false;
            for(Throwable c=e;c!=null;c=c.getCause()) if(c instanceof org.hibernate.exception.ConstraintViolationException) constraint=true;
            error(res,constraint?409:500,constraint?"Dữ liệu vi phạm ràng buộc database":"Không thể xử lý dữ liệu lúc này");
        }
    }
    private void error(HttpServletResponse res,int status,String message) throws IOException {
        res.setStatus(status); json.writeValue(res.getOutputStream(),Map.of("error",message));
    }
}
