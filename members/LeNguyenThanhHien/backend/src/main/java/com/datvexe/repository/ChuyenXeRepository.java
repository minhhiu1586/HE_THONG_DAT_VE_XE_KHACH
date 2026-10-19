package com.datvexe.repository;

import com.datvexe.domain.Entities.ChuyenXe;
import com.datvexe.domain.States.TrangThaiChuyen;
import com.datvexe.domain.States.TrangThaiNhaXe;
import com.datvexe.domain.States.TrangThaiTuyenXe;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.util.*;

/** Truy vấn dùng EntityManager và phạm vi nhà xe do Service cung cấp. */
public final class ChuyenXeRepository extends JpaRepository<ChuyenXe> {
    public ChuyenXeRepository(EntityManager em) {
        super(em, ChuyenXe.class);
    }

    // Khóa và đọc lại chuyến để sử dụng trạng thái mới nhất sau khi chờ giao dịch khác.
    @Override
    public ChuyenXe lock(String id) {
        ChuyenXe trip = super.lock(id);
        if (trip != null) {
            // Đọc lại trạng thái mới nhất sau khi chờ khóa, tránh dùng chuyến đã nạp trước đó.
            em.refresh(trip, LockModeType.PESSIMISTIC_WRITE);
            trip = (ChuyenXe) org.hibernate.Hibernate.unproxy(trip);
        }
        return trip;
    }

    /** Lọc các mã tuyến cùng nhóm; phạm vi nhà xe được kiểm tra độc lập. */
    public record Filters(String nhaXe, String tuyen, String xe, LocalDate tuNgay, LocalDate denNgay, TrangThaiChuyen trangThai) {}

    public List<ChuyenXe> search(Filters filters, int offset, int size) {
        TypedQuery<ChuyenXe> query = em.createQuery("select c from ChuyenXe c" + where(filters)
            + " order by c.ngayKhoiHanh desc,c.gioKhoiHanh desc,c.maChuyen", ChuyenXe.class);
        for (Map.Entry<String, Object> parameter : parameters(filters).entrySet()) {
            query.setParameter(parameter.getKey(), parameter.getValue());
        }
        return query.setFirstResult(offset).setMaxResults(size).getResultList();
    }

    // Dùng cùng điều kiện với truy vấn danh sách để tổng số bản ghi phân trang không bị lệch.
    public long count(Filters filters) {
        TypedQuery<Long> query = em.createQuery("select count(c) from ChuyenXe c" + where(filters), Long.class);
        for (Map.Entry<String, Object> parameter : parameters(filters).entrySet()) {
            query.setParameter(parameter.getKey(), parameter.getValue());
        }
        return query.getSingleResult();
    }

    /** Tìm chuyến đang mở bán; điều kiện xe, nhân sự và hạn đặt vé được Service kiểm tra tiếp. */
    public List<ChuyenXe> findForSearch(String fromKind, String fromCode, String toKind, String toCode, LocalDate date) {
        StringBuilder jpql = new StringBuilder("select c from ChuyenXe c join c.tuyenXe.nhaXe n where c.ngayKhoiHanh ="
            + " :date and c.trangThai = :scheduled and c.tuyenXe.trangThai =" + " :active and n.trangThai = :activeOperator ");

        if (fromKind.equals("CITY")) {
            jpql.append("and c.tuyenXe.tramKhoiHanh.tinhThanh.maTinhThanh = :from ");
        } else if (fromKind.equals("STATION")) {
            jpql.append("and c.tuyenXe.tramKhoiHanh.maBenXe = :from ");
        } else {
            jpql.append("and (c.tuyenXe.tramKhoiHanh.maBenXe = :from " + "or c.tuyenXe.tramKhoiHanh.tinhThanh.maTinhThanh = :from) ");
        }

        if (toKind.equals("CITY")) {
            jpql.append("and c.tuyenXe.tramDen.tinhThanh.maTinhThanh = :to ");
        } else if (toKind.equals("STATION")) {
            jpql.append("and c.tuyenXe.tramDen.maBenXe = :to ");
        } else {
            jpql.append("and (c.tuyenXe.tramDen.maBenXe = :to " + "or c.tuyenXe.tramDen.tinhThanh.maTinhThanh = :to) ");
        }
        jpql.append("order by c.gioKhoiHanh");

        return em.createQuery(jpql.toString(), ChuyenXe.class)
            .setParameter("date", date)
            .setParameter("scheduled", TrangThaiChuyen.DANG_MO_BAN)
            .setParameter("active", TrangThaiTuyenXe.DANG_KHAI_THAC)
            .setParameter("activeOperator", TrangThaiNhaXe.HOAT_DONG)
            .setParameter("from", fromCode)
            .setParameter("to", toCode)
            .getResultList();
    }

    // Ghép giá trị với tên tham số JPQL, không nối trực tiếp dữ liệu người dùng vào câu truy vấn.
    private static Map<String, Object> parameters(Filters f) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (f.nhaXe() != null) {
            result.put("owner", f.nhaXe());
        }
        if (f.tuyen() != null) {
            result.put("routes", Arrays.asList(f.tuyen().split(",")));
        }
        if (f.xe() != null) {
            result.put("vehicle", f.xe());
        }
        if (f.tuNgay() != null) {
            result.put("fromDate", f.tuNgay());
        }
        if (f.denNgay() != null) {
            result.put("toDate", f.denNgay());
        }
        if (f.trangThai() != null) {
            result.put("state", f.trangThai());
        }
        return result;
    }

    // Chỉ thêm điều kiện có giá trị; nhiều điều kiện được kết hợp bằng AND.
    private static String where(Filters f) {
        List<String> clauses = new ArrayList<>();
        if (f.nhaXe() != null) {
            clauses.add("c.tuyenXe.nhaXe.maNhaXe=:owner");
        }
        if (f.tuyen() != null) {
            clauses.add("c.tuyenXe.maTuyen in :routes");
        }
        if (f.xe() != null) {
            clauses.add("c.xe.maXe=:vehicle");
        }
        if (f.tuNgay() != null) {
            clauses.add("c.ngayKhoiHanh>=:fromDate");
        }
        if (f.denNgay() != null) {
            clauses.add("c.ngayKhoiHanh<=:toDate");
        }
        if (f.trangThai() != null) {
            clauses.add("c.trangThai=:state");
        }
        if (clauses.isEmpty()) {
            return "";
        } else {
            return " where " + String.join(" and ", clauses);
        }
    }
}
