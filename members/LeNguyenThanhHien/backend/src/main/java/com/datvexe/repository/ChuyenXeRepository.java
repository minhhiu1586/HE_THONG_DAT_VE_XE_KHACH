package com.datvexe.repository;

import com.datvexe.domain.Entities.ChuyenXe;
import com.datvexe.domain.States.TrangThaiChuyen;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;

/** Queries use the transaction and authoritative operator scope supplied by the service. */
public final class ChuyenXeRepository extends JpaRepository<ChuyenXe> {
    public ChuyenXeRepository(EntityManager em) { super(em, ChuyenXe.class); }
    @Override public ChuyenXe lock(String id) {
        ChuyenXe trip=super.lock(id);
        if(trip!=null) {
            em.refresh(trip,LockModeType.PESSIMISTIC_WRITE);
            trip=(ChuyenXe)org.hibernate.Hibernate.unproxy(trip);
        }
        return trip;
    }
    /** tuyen accepts comma-separated route IDs grouped by the UI; owner scope remains independent. */
    public record Filters(String nhaXe, String tuyen, String xe, LocalDate tuNgay,
        LocalDate denNgay, TrangThaiChuyen trangThai) {}
    public List<ChuyenXe> search(Filters filters, int offset, int size) {
        var query=em.createQuery("select c from ChuyenXe c"+where(filters)
            +" order by c.ngayKhoiHanh desc,c.gioKhoiHanh desc,c.maChuyen",ChuyenXe.class);
        parameters(filters).forEach(query::setParameter);
        return query.setFirstResult(offset).setMaxResults(size).getResultList();
    }
    public long count(Filters filters) {
        var query=em.createQuery("select count(c) from ChuyenXe c"+where(filters),Long.class);
        parameters(filters).forEach(query::setParameter);return query.getSingleResult();
    }
    private static Map<String,Object> parameters(Filters f) {
        Map<String,Object> result=new LinkedHashMap<>();
        if(f.nhaXe()!=null) result.put("owner",f.nhaXe());
        if(f.tuyen()!=null) result.put("routes",Arrays.asList(f.tuyen().split(",")));
        if(f.xe()!=null) result.put("vehicle",f.xe());
        if(f.tuNgay()!=null) result.put("fromDate",f.tuNgay());
        if(f.denNgay()!=null) result.put("toDate",f.denNgay());
        if(f.trangThai()!=null) result.put("state",f.trangThai());
        return result;
    }
    private static String where(Filters f) {
        List<String> clauses=new ArrayList<>();
        if(f.nhaXe()!=null) clauses.add("c.tuyenXe.nhaXe.maNhaXe=:owner");
        if(f.tuyen()!=null) clauses.add("c.tuyenXe.maTuyen in :routes");
        if(f.xe()!=null) clauses.add("c.xe.maXe=:vehicle");
        if(f.tuNgay()!=null) clauses.add("c.ngayKhoiHanh>=:fromDate");
        if(f.denNgay()!=null) clauses.add("c.ngayKhoiHanh<=:toDate");
        if(f.trangThai()!=null) clauses.add("c.trangThai=:state");
        return clauses.isEmpty()?"":" where "+String.join(" and ",clauses);
    }
}
