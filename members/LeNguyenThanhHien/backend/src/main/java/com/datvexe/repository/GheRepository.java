package com.datvexe.repository;

import com.datvexe.domain.Entities.Ghe;

import jakarta.persistence.EntityManager;

import java.util.List;

public final class GheRepository extends JpaRepository<Ghe> {
    public GheRepository(EntityManager em) {
        super(em, Ghe.class);
    }

    @Override
    public Ghe lock(String id) {
        Ghe g = super.lock(id);
        if (g != null) {
            // Lấy đối tượng xe thật từ proxy Hibernate trước khi đọc các thuộc tính của xe.
            g.xe = (com.datvexe.domain.Entities.Xe) org.hibernate.Hibernate.unproxy(g.xe);
        }
        return g;
    }

    public List<Ghe> byVehicle(String id) {
        return em.createQuery(
                        "select g from Ghe g join fetch g.xe where g.xe.maXe = :id order by g.tang,"
                                + " g.viTri",
                        Ghe.class)
                .setParameter("id", id)
                .getResultList();
    }
}
