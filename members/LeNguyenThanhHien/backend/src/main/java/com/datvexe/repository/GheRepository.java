package com.datvexe.repository;

import com.datvexe.domain.Entities.Ghe;
import com.datvexe.domain.Entities.Xe;

import jakarta.persistence.EntityManager;

import java.util.List;

public final class GheRepository extends JpaRepository<Ghe> {
    public GheRepository(EntityManager em) {
        super(em, Ghe.class);
    }

    // Khóa bản ghi ghế và lấy đối tượng xe thật để đọc thông tin xe liên quan.
    @Override
    public Ghe lock(String id) {
        Ghe seat = super.lock(id);
        if (seat != null) {
            // Lấy đối tượng xe thật từ proxy Hibernate trước khi đọc các thuộc tính của xe.
            seat.xe = (Xe) org.hibernate.Hibernate.unproxy(seat.xe);
        }
        return seat;
    }

    public List<Ghe> byVehicle(String id) {
        return em.createQuery("select g from Ghe g join fetch g.xe where g.xe.maXe = :id order by g.tang," + " g.viTri", Ghe.class)
            .setParameter("id", id)
            .getResultList();
    }
}
