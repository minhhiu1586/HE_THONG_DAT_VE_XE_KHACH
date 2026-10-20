package com.datvexe.repository;

import com.datvexe.domain.Entities.NhanVien;
import jakarta.persistence.EntityManager;
import java.util.List;

public final class NhanVienRepository extends JpaRepository<NhanVien> {
    public NhanVienRepository(EntityManager entityManager) {
        super(entityManager, NhanVien.class);
    }

    @Override
    public List<NhanVien> list() {
        return em.createQuery(
            "select n from NhanVien n order by n.hoTen,n.maNhanVien", NhanVien.class
        ).getResultList();
    }

    public boolean contactInUse(String employeeId, String email, String phone) {
        return em.createQuery("""
            select count(n) from NhanVien n
            where n.maNhanVien <> :id
              and (lower(n.email) = :email or n.soDienThoai = :phone)
            """, Long.class)
            .setParameter("id", employeeId)
            .setParameter("email", email)
            .setParameter("phone", phone)
            .getSingleResult() > 0;
    }

    public boolean hasLinkedAccount(NhanVien employee) {
        return em.createQuery(
            "select count(u) from NguoiDung u where u.nhanVien = :employee", Long.class
        ).setParameter("employee", employee).getSingleResult() > 0;
    }
}
