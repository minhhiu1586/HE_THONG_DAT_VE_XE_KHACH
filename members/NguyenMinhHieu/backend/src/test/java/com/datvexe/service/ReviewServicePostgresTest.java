package com.datvexe.service;

import jakarta.persistence.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.*;
import java.util.*;

@EnabledIfEnvironmentVariable(named = "REVIEW_TEST_JDBC_URL", matches = ".+")
class ReviewServicePostgresTest extends ReviewServiceTest {
    private String schema;

    private String url() {
        return System.getenv("REVIEW_TEST_JDBC_URL");
    }

    private String user() {
        return System.getenv().getOrDefault("REVIEW_TEST_JDBC_USER", "postgres");
    }

    private String password() {
        return System.getenv().getOrDefault("REVIEW_TEST_JDBC_PASSWORD", "");
    }

    @Override
    EntityManagerFactory createFactory() {
        schema = "t22_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(url(), user(), password());
                var st = c.createStatement()) {
            st.execute("create schema " + schema);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        String scoped = url() + (url().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        return Persistence.createEntityManagerFactory(
                "datvexe",
                Map.of(
                        "jakarta.persistence.jdbc.driver",
                        "org.postgresql.Driver",
                        "jakarta.persistence.jdbc.url",
                        scoped,
                        "jakarta.persistence.jdbc.user",
                        user(),
                        "jakarta.persistence.jdbc.password",
                        password(),
                        "hibernate.default_schema",
                        schema,
                        "hibernate.hbm2ddl.auto",
                        "create-drop"));
    }

    @Override
    @AfterEach
    void close() {
        try {
            super.close();
        } finally {
            if (schema != null)
                try (var c = DriverManager.getConnection(url(), user(), password());
                        var st = c.createStatement()) {
                    st.execute("drop schema " + schema + " cascade");
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
        }
    }
}
