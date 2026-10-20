package com.datvexe.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.function.Function;

final class AccountTransactions {
    private final EntityManagerFactory factory;

    AccountTransactions(EntityManagerFactory factory) {
        this.factory = factory;
    }

    <T> T execute(Function<EntityManager, T> action) {
        try (var entityManager = factory.createEntityManager()) {
            var transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                T result = action.apply(entityManager);
                transaction.commit();
                return result;
            } catch (RuntimeException error) {
                if (transaction.isActive()) transaction.rollback();
                throw error;
            }
        }
    }
}
