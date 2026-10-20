package com.datvexe.repository;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.NguoiDung;

import jakarta.persistence.EntityManager;

import java.time.Instant;

public final class AuthSessionRepository {
    private final EntityManager entityManager;

    public AuthSessionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public AuthSession find(String tokenHash) {
        return entityManager.find(AuthSession.class, tokenHash);
    }

    public void save(AuthSession session) {
        entityManager.persist(session);
    }

    public void refresh(AuthSession session) {
        entityManager.refresh(session);
    }

    public void deleteExpired(Instant now) {
        entityManager
                .createQuery("delete from AuthSession s where s.hetHan <= :now")
                .setParameter("now", now)
                .executeUpdate();
    }

    public void delete(String tokenHash, String userId) {
        entityManager
                .createQuery(
                        "delete from AuthSession s where s.tokenHash = :token and"
                            + " s.nguoiDung.maNguoiDung = :id")
                .setParameter("token", tokenHash)
                .setParameter("id", userId)
                .executeUpdate();
    }

    public void revokeAll(NguoiDung user) {
        entityManager
                .createQuery("delete from AuthSession s where s.nguoiDung = :user")
                .setParameter("user", user)
                .executeUpdate();
    }
}
