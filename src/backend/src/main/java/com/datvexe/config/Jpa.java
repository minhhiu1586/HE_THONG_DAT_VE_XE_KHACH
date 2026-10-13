package com.datvexe.config;

import jakarta.persistence.*;
import java.util.Map;
import java.util.function.Function;

public final class Jpa {
    private Jpa() {}

    private static final EntityManagerFactory FACTORY = Persistence.createEntityManagerFactory("datvexe", Map.of(
        "jakarta.persistence.jdbc.driver", System.getProperty("db.driver", "org.postgresql.Driver"),
        "jakarta.persistence.jdbc.url", System.getProperty("db.url", System.getenv().getOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/datvexe")),
        "jakarta.persistence.jdbc.user", System.getProperty("db.user", System.getenv().getOrDefault("POSTGRES_USER", "datvexe_user")),
        "jakarta.persistence.jdbc.password", System.getProperty("db.password", System.getenv().getOrDefault("POSTGRES_PASSWORD", "datvexe_password")),
        "hibernate.hbm2ddl.auto", System.getProperty("db.ddl", "validate")
    ));

    public static EntityManagerFactory factory() { return FACTORY; }

    public static <T> T read(Function<EntityManager, T> action) {
        try (EntityManager em = FACTORY.createEntityManager()) { return action.apply(em); }
    }

    public static <T> T write(Function<EntityManager, T> action) {
        try (EntityManager em = FACTORY.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                T result = action.apply(em);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public static void close() { FACTORY.close(); }
}
