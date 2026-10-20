package com.datvexe.service;

import com.datvexe.domain.Entities.ChuyenXe;
import com.datvexe.domain.Entities.NhanVien;
import com.datvexe.domain.Entities.PhanCongChuyenXe;
import com.datvexe.domain.States.TrangThaiChuyen;
import com.datvexe.domain.States.VaiTroChuyen;
import com.datvexe.dto.AssignmentInputs;
import com.datvexe.repository.PhanCongChuyenXeRepository;
import com.datvexe.security.AccessPolicy;
import com.datvexe.security.RequestIdentity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.hibernate.Hibernate;

public final class AssignmentService {
    private final EntityManagerFactory factory;

    public AssignmentService(EntityManagerFactory factory) {
        this.factory = factory;
    }

    private <T> T tx(Function<EntityManager, T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            var transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                T result = action.apply(em);
                transaction.commit();
                return result;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    public List<Map<String, Object>> list() {
        return tx(em -> new PhanCongChuyenXeRepository(em).list().stream()
            .filter(assignment -> assignment.vaiTro != VaiTroChuyen.KIEM_VE)
            .filter(AssignmentService::inOperatorScope)
            .map(AssignmentService::view)
            .toList());
    }

    // Keep the existing HTTP list contract: q is length-checked, not used as a filter.
    public List<Map<String, Object>> filtered(String search) {
        return tx(em -> {
            String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
            if (term.length() > 160) {
                throw new DomainException(400, "Từ khóa quá dài.");
            }
            return new PhanCongChuyenXeRepository(em).list().stream()
                .filter(AssignmentService::inOperatorScope)
                .map(AssignmentService::view)
                .toList();
        });
    }

    public Map<String, Object> get(String id) {
        return tx(em -> view(found(new PhanCongChuyenXeRepository(em).find(text(id, 40)))));
    }

    public Map<String, Object> save(String id, AssignmentInputs.PhanCong input) {
        return tx(em -> {
            PhanCongChuyenXeRepository assignments = new PhanCongChuyenXeRepository(em);
            PhanCongChuyenXe assignment = id == null ? new PhanCongChuyenXe()
                : found(assignments.lock(text(id, 40)));
            ChuyenXe previousTrip = id == null ? null
                : em.find(ChuyenXe.class, assignment.chuyenXe.maChuyen, LockModeType.PESSIMISTIC_WRITE);
            if (previousTrip != null) {
                previousTrip = (ChuyenXe) Hibernate.unproxy(previousTrip);
            }
            if (id == null) {
                assignment.maPhanCong = text(input.maPhanCong(), 40);
                check(assignments.find(assignment.maPhanCong) == null, "Mã phân công đã tồn tại");
            }
            if (id != null) {
                check(TripSaleRules.scheduled(assignment.chuyenXe), "Không sửa phân công chuyến đã khởi hành");
            }
            ChuyenXe trip = found(em.find(ChuyenXe.class, text(input.maChuyen(), 40),
                LockModeType.PESSIMISTIC_WRITE));
            trip = (ChuyenXe) Hibernate.unproxy(trip);
            trip.tuyenXe = (com.datvexe.domain.Entities.TuyenXe) Hibernate.unproxy(trip.tuyenXe);
            check(TripSaleRules.scheduled(trip), "Chỉ phân công chuyến chưa khởi hành");
            NhanVien employee = found(em.find(NhanVien.class, text(input.maNhanVien(), 40),
                LockModeType.PESSIMISTIC_WRITE));
            employee = (NhanVien) Hibernate.unproxy(employee);
            AssignmentRules.validate(em, trip, employee, input.vaiTro(), assignment.maPhanCong);

            assignment.ghiChu = prepareNote(trip, input);
            assignment.chuyenXe = trip;
            assignment.nhanVien = employee;
            assignment.vaiTro = input.vaiTro();
            if (id == null) {
                assignments.save(assignment);
            }
            em.flush();
            validateProtectedCrew(em, trip);
            if (previousTrip != null && !previousTrip.maChuyen.equals(trip.maChuyen)) {
                validateProtectedCrew(em, previousTrip);
            }
            return view(assignment);
        });
    }

    public void delete(String id) {
        tx(em -> {
            PhanCongChuyenXeRepository assignments = new PhanCongChuyenXeRepository(em);
            PhanCongChuyenXe assignment = found(assignments.lock(text(id, 40)));
            check(TripSaleRules.scheduled(assignment.chuyenXe), "Chuyến đã khởi hành");
            ChuyenXe trip = em.find(ChuyenXe.class, assignment.chuyenXe.maChuyen,
                LockModeType.PESSIMISTIC_WRITE);
            boolean protectCrew = trip.trangThai == TrangThaiChuyen.DANG_MO_BAN
                || VehicleRules.hasTicketsOrHolds(em, trip);
            assignments.delete(assignment);
            em.flush();
            if (protectCrew) {
                AssignmentRules.readyForOperation(em, trip);
            }
            return null;
        });
    }

    private static void validateProtectedCrew(EntityManager em, ChuyenXe trip) {
        if (trip.trangThai == TrangThaiChuyen.DANG_MO_BAN || VehicleRules.hasTicketsOrHolds(em, trip)) {
            AssignmentRules.readyForOperation(em, trip);
        }
    }

    private static String prepareNote(ChuyenXe trip, AssignmentInputs.PhanCong input) {
        if (input.ghiChu() != null && input.ghiChu().length() > 1000) {
            throw new DomainException(400, "Ghi chú quá dài");
        }
        String note = input.ghiChu() == null ? ""
            : input.ghiChu().replaceFirst("^\\[T11_PLAN:[^\\]]*\\]\\s*", "");
        if (AssignmentRules.needsDrivingPlan(trip)) {
            check(Boolean.TRUE.equals(input.xacNhanKeHoachLaiNghi()),
                "Chuyến dài cần xác nhận kế hoạch lái/nghỉ; hệ thống chưa tự kiểm chứng giờ nghỉ.");
            var identity = RequestIdentity.get();
            String reviewer = identity == null ? "INTERNAL" : identity.user().maNguoiDung();
            note = AssignmentRules.reviewPrefix(trip) + reviewer + "/" + Instant.now() + "] " + note;
        }
        check(note.length() <= 1000, "Ghi chú quá dài sau khi ghi nhận xác nhận.");
        return note.isEmpty() ? null : note;
    }

    private static boolean inOperatorScope(PhanCongChuyenXe assignment) {
        String owner = RequestIdentity.operatorId();
        return owner == null || assignment.chuyenXe.tuyenXe.nhaXe != null
            && owner.equals(assignment.chuyenXe.tuyenXe.nhaXe.maNhaXe)
            && assignment.nhanVien.nhaXe != null && owner.equals(assignment.nhanVien.nhaXe.maNhaXe);
    }

    private static Map<String, Object> view(PhanCongChuyenXe assignment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maPhanCong", assignment.maPhanCong);
        result.put("maChuyen", assignment.chuyenXe.maChuyen);
        result.put("maNhanVien", assignment.nhanVien.maNhanVien);
        result.put("vaiTro", assignment.vaiTro);
        result.put("ghiChu", assignment.ghiChu);
        return result;
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new DomainException(400, "Chuỗi bắt buộc bị thiếu hoặc quá dài");
        }
        return value.trim();
    }

    private static <T> T found(T value) {
        if (value == null) {
            throw new DomainException(404, "Không tìm thấy dữ liệu");
        }
        return value;
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new DomainException(409, message);
        }
    }
}
