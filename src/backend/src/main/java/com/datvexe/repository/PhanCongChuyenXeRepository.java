package com.datvexe.repository;
import com.datvexe.domain.Entities.PhanCongChuyenXe;
import jakarta.persistence.EntityManager;
import java.util.List;
public final class PhanCongChuyenXeRepository extends JpaRepository<PhanCongChuyenXe> {
    public PhanCongChuyenXeRepository(EntityManager em) { super(em, PhanCongChuyenXe.class); }
    @Override public List<PhanCongChuyenXe> list() {
        return em.createQuery("select p from PhanCongChuyenXe p join fetch p.chuyenXe", PhanCongChuyenXe.class).getResultList();
    }
    @Override public PhanCongChuyenXe find(String id) {
        return em.createQuery("select p from PhanCongChuyenXe p join fetch p.chuyenXe where p.maPhanCong = :id", PhanCongChuyenXe.class)
            .setParameter("id", id).getResultStream().findFirst().orElse(null);
    }
    @Override public PhanCongChuyenXe lock(String id) {
        PhanCongChuyenXe p = super.lock(id);
        if (p != null) p.chuyenXe = (com.datvexe.domain.Entities.ChuyenXe) org.hibernate.Hibernate.unproxy(p.chuyenXe);
        return p;
    }
    public List<PhanCongChuyenXe> byTrip(String id) {
        return em.createQuery("select p from PhanCongChuyenXe p join fetch p.chuyenXe where p.chuyenXe.maChuyen = :id", PhanCongChuyenXe.class)
            .setParameter("id", id).getResultList();
    }
}
