package com.datvexe.repository;

import com.datvexe.domain.Entities.VeXe;
import jakarta.persistence.EntityManager;
import java.util.List;

public final class VeXeRepository extends JpaRepository<VeXe> {
    public VeXeRepository(EntityManager em) { super(em, VeXe.class); }

    public List<VeXe> forOrder(String maDon) {
        return em.createQuery("select v from VeXe v where v.donDatVe.maDon = :order", VeXe.class)
                .setParameter("order", maDon)
                .getResultList();
    }
}
