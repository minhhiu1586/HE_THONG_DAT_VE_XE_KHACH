package com.datvexe.repository;
import com.datvexe.domain.Entities.LoaiXe;
import jakarta.persistence.EntityManager;
public final class LoaiXeRepository extends JpaRepository<LoaiXe> {
    public LoaiXeRepository(EntityManager em) { super(em, LoaiXe.class); }
}
