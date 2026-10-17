package com.datvexe.repository;

import com.datvexe.domain.Entities.NguoiDung;
import jakarta.persistence.EntityManager;

public final class NguoiDungRepository extends JpaRepository<NguoiDung> {
    public NguoiDungRepository(EntityManager em) { super(em, NguoiDung.class); }
    @Override public NguoiDung lock(String id) {
        NguoiDung user = super.lock(id);

        if (user != null) em.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
    public NguoiDung byEmail(String email) {
        return em.createQuery("select n from NguoiDung n where lower(n.email) = :email", NguoiDung.class)
            .setParameter("email", email).getResultStream().findFirst().orElse(null);
    }
    public boolean duplicate(String id, String email, String phone, String cccd) {
        return em.createQuery("select count(n) from NguoiDung n where n.maNguoiDung <> :id and "
            + "(lower(n.email) = :email or n.soDienThoai = :phone or (:cccd is not null and n.cccd = :cccd))", Long.class)
            .setParameter("id", id).setParameter("email", email).setParameter("phone", phone)
            .setParameter("cccd", cccd).getSingleResult() > 0;
    }
}
