package com.datvexe.repository;

import com.datvexe.security.AccountException;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** PostgreSQL request metadata. Uses exactly the business transaction, not a separate commit. */
public final class IdempotencyRepository {
    public record Scope(String userId, String method, String path, String key) {}
    public record Saved(String hash, int status, String body) {}
    private final EntityManager em;

    public IdempotencyRepository(EntityManager em) { this.em = em; }

    public void lock(Scope scope) {
        long lockId = ByteBuffer.wrap(digest(scope.userId() + "\n" + scope.method() + "\n"
                + scope.path() + "\n" + scope.key())).getLong();
        boolean acquired = (Boolean) em.createNativeQuery("select pg_try_advisory_xact_lock(:id)")
                .setParameter("id", lockId).getSingleResult();
        if (!acquired) throw new AccountException(409, "REQUEST_IN_PROGRESS", "Yêu cầu cùng khóa đang được xử lý.");
    }

    public Saved find(Scope scope, Instant now) {
        // An old key may be reused only after the retention period, under the same advisory lock.
        bind(em.createNativeQuery("delete from api_idempotency where user_id=:u and method=:m "
                        + "and path=:p and request_key=:k and expires_at <= :now"), scope)
                .setParameter("now", now).executeUpdate();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(em.createNativeQuery("select request_hash,status,response_body from api_idempotency "
                + "where user_id=:u and method=:m and path=:p and request_key=:k"), scope).getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = rows.getFirst();
        return new Saved((String) row[0], ((Number) row[1]).intValue(), (String) row[2]);
    }

    public void save(Scope scope, String hash, int status, String body, Instant now) {
        bind(em.createNativeQuery("insert into api_idempotency "
                        + "(user_id,method,path,request_key,request_hash,status,response_body,created_at,expires_at) "
                        + "values (:u,:m,:p,:k,:hash,:status,:body,:now,:expires)"), scope)
                .setParameter("hash", hash).setParameter("status", status).setParameter("body", body)
                .setParameter("now", now).setParameter("expires", now.plusSeconds(86_400)).executeUpdate();
    }

    public static String hash(String value) { return HexFormat.of().formatHex(digest(value)); }
    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private jakarta.persistence.Query bind(jakarta.persistence.Query query, Scope scope) {
        return query.setParameter("u", scope.userId()).setParameter("m", scope.method())
                .setParameter("p", scope.path()).setParameter("k", scope.key());
    }
}
