package com.datvexe.repository;

import com.datvexe.domain.Entities.NguoiDung;
import jakarta.persistence.EntityManager;

// Tầng truy vấn JPA cho tài khoản; nhận EntityManager và dùng transaction do AccountService mở.
public final class NguoiDungRepository extends JpaRepository<NguoiDung> {
    public NguoiDungRepository(EntityManager em) { super(em, NguoiDung.class); }
    // Khóa bản ghi và đọc lại dữ liệu để xử lý trạng thái tài khoản nhất quán trong transaction.
    @Override public NguoiDung lock(String id) {
        NguoiDung user = super.lock(id);
        // Hibernate may lock after its initial SELECT; refresh under the acquired lock.
        if (user != null) em.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
    // LOGIN gọi hàm này. JPQL dùng tên entity/thuộc tính Java; :email được bind bằng setParameter, không nối input vào query.
    public NguoiDung byEmail(String email) {
        return em.createQuery("select n from NguoiDung n where lower(n.email) = :email", NguoiDung.class)
            .setParameter("email", email).getResultStream().findFirst().orElse(null);
    }
    // Đăng ký/sửa hồ sơ kiểm tra email, điện thoại, CCCD đã được dùng chưa; id giúp loại trừ chính mình khi sửa.
    public boolean duplicate(String id, String email, String phone, String cccd) {
        return em.createQuery("select count(n) from NguoiDung n where n.maNguoiDung <> :id and "
            + "(lower(n.email) = :email or n.soDienThoai = :phone or (:cccd is not null and n.cccd = :cccd))", Long.class)
            .setParameter("id", id).setParameter("email", email).setParameter("phone", phone)
            .setParameter("cccd", cccd).getSingleResult() > 0;
    }
}
