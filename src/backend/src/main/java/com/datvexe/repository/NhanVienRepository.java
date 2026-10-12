package com.datvexe.repository;

import com.datvexe.domain.Entities.NhanVien;
import jakarta.persistence.EntityManager;
import java.util.List;

public final class NhanVienRepository extends JpaRepository<NhanVien> {
    public NhanVienRepository(EntityManager em) { super(em,NhanVien.class); }
    @Override public List<NhanVien> list() {
        return em.createQuery("select n from NhanVien n order by n.hoTen,n.maNhanVien",NhanVien.class).getResultList();
    }
}
