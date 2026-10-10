package com.datvexe.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;

/** Uses the service transaction; never opens or commits an independent transaction. */
public class JpaRepository<T> {
    protected final EntityManager em;
    private final Class<T> type;
    public JpaRepository(EntityManager em, Class<T> type) { this.em = em; this.type = type; }
    public T find(String id) { return em.find(type, id); }
    public T lock(String id) { return em.find(type, id, LockModeType.PESSIMISTIC_WRITE); }
    public List<T> list() {
        String name = em.getMetamodel().entity(type).getName();
        return em.createQuery("select x from " + name + " x", type).getResultList();
    }
    public void save(T entity) { em.persist(entity); }
    public void delete(T entity) { em.remove(entity); }
}
