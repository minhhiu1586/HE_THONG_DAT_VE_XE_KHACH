package com.datvexe.repository;

import com.datvexe.domain.Entities.ChuyenXe;
import com.datvexe.domain.Entities.DonDatVe;
import com.datvexe.domain.Entities.VeXe;
import com.datvexe.domain.States.TrangThaiVe;

import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VeXeRepository extends JpaRepository<VeXe> {

    public VeXeRepository(EntityManager em) {
        super(em, VeXe.class);
    }

    public VeXe findWithOrder(String id) {
        VeXe ve = em.find(VeXe.class, id);
        if (ve != null) {
            ve.donDatVe = (DonDatVe) org.hibernate.Hibernate.unproxy(ve.donDatVe);
        }
        return ve;
    }

    public long countForOrder(String orderId) {
        return em.createQuery("""
                        select count(v) from VeXe v
                        where v.donDatVe.maDon = :order
                        """, Long.class)
                .setParameter("order", orderId)
                .getSingleResult();
    }

    public long countSoldForTripSeats(String tripId, Set<String> seatIds) {
        return em.createQuery("""
                        select count(v) from VeXe v
                        where v.chuyenXe.maChuyen = :trip and v.ghe.maGhe in :seats
                          and v.trangThai in :states
                        """, Long.class)
                .setParameter("trip", tripId)
                .setParameter("seats", seatIds)
                .setParameter("states", List.of(TrangThaiVe.DA_XUAT_VE, TrangThaiVe.DA_LEN_XE))
                .getSingleResult();
    }

    public List<String> tripIdsForOrder(String orderId) {
        return em.createQuery("""
                        select distinct v.chuyenXe.maChuyen from VeXe v
                        where v.donDatVe.maDon = :order
                        """, String.class)
                .setParameter("order", orderId)
                .getResultList();
    }

    public Set<String> tripIdsForOrderIncludingHolds(String orderId) {
        List<String> maChuyenGiuCho = em.createQuery("""
                        select distinct g.chuyenXe.maChuyen from GiuCho g
                        where g.donDatVe.maDon = :order
                        """, String.class)
                .setParameter("order", orderId)
                .getResultList();

        Set<String> maChuyen = new HashSet<>(maChuyenGiuCho);
        maChuyen.addAll(tripIdsForOrder(orderId));
        return maChuyen;
    }

    public ChuyenXe findTrip(String tripId) {
        return em.find(ChuyenXe.class, tripId);
    }

    public List<VeXe> forOrder(String maDon) {
        return em.createQuery("""
                        select v from VeXe v
                        where v.donDatVe.maDon = :order
                        """, VeXe.class)
                .setParameter("order", maDon)
                .getResultList();
    }
}
