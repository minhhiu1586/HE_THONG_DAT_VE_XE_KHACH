package com.datvexe.repository;
import com.datvexe.domain.Entities.Xe;
import jakarta.persistence.EntityManager;
public final class XeRepository extends JpaRepository<Xe> {
    public XeRepository(EntityManager em) { super(em, Xe.class); }
}
