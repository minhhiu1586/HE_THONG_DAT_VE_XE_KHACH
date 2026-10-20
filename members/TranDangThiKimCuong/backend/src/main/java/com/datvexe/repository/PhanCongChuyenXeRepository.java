package com.datvexe.repository;
import com.datvexe.domain.Entities.ChuyenXe;
import com.datvexe.domain.Entities.PhanCongChuyenXe;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Hibernate;
public final class PhanCongChuyenXeRepository extends JpaRepository<PhanCongChuyenXe> {
    public PhanCongChuyenXeRepository(EntityManager em) { super(em, PhanCongChuyenXe.class); }
    @Override
    public List<PhanCongChuyenXe> list() {
        String jpql = "select p from PhanCongChuyenXe p join fetch p.chuyenXe";
        return em.createQuery(jpql, PhanCongChuyenXe.class).getResultList();
    }
    @Override
    public PhanCongChuyenXe find(String id) {
        String jpql = "select p from PhanCongChuyenXe p join fetch p.chuyenXe where p.maPhanCong = :id";
        return em.createQuery(jpql, PhanCongChuyenXe.class)
            .setParameter("id", id).getResultStream().findFirst().orElse(null);
    }
    @Override
    public PhanCongChuyenXe lock(String id) {
        PhanCongChuyenXe assignment = super.lock(id);
        if (assignment != null) {
            assignment.chuyenXe = (ChuyenXe) Hibernate.unproxy(assignment.chuyenXe);
        }
        return assignment;
    }
    public List<PhanCongChuyenXe> byTrip(String id) {
        String jpql = "select p from PhanCongChuyenXe p join fetch p.chuyenXe where p.chuyenXe.maChuyen = :id";
        return em.createQuery(jpql, PhanCongChuyenXe.class)
            .setParameter("id", id).getResultList();
    }
}
