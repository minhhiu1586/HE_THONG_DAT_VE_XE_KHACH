package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.KiemVeDto.*;
import com.datvexe.repository.HoanVeRepository;
import com.datvexe.repository.IdempotencyRepository;
import com.datvexe.security.AccessControl;
import com.datvexe.security.AccountException;
import com.datvexe.security.RequestIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

public class KiemVeService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final EntityManagerFactory emf;

    public KiemVeService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    private NguoiDung caller(EntityManager em) {
        var context = RequestIdentity.get();
        if (context == null || context.user() == null) {
            throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
        }
        return AccessControl.authenticated(em, context.user(), Instant.now());
    }

    private ChuyenXe authorizeTrip(EntityManager em, NguoiDung user, String maChuyen) {
        return switch (user.loaiTaiKhoan) {
            case NHAN_VIEN -> authorizeAssignedEmployee(em, user, maChuyen);
            case NHA_XE -> {
                if (user.nhaXe == null) throw AccessControl.denied();
                NhaXe operator = AccessControl.resource(em, NhaXe.class, user.nhaXe.maNhaXe);
                ChuyenXe trip = AccessControl.resource(em, ChuyenXe.class, maChuyen);
                AccessControl.operator(user, trip.tuyenXe.nhaXe);
                if (trip.tuyenXe.nhaXe == null || !trip.tuyenXe.nhaXe.maNhaXe.equals(operator.maNhaXe))
                    throw AccessControl.denied();
                yield trip;
            }
            case ADMIN -> {
                ChuyenXe initial = em.find(ChuyenXe.class, maChuyen);
                if (initial == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy chuyến xe.");
                if (initial.tuyenXe.nhaXe != null)
                    AccessControl.resource(em, NhaXe.class, initial.tuyenXe.nhaXe.maNhaXe);
                yield AccessControl.resource(em, ChuyenXe.class, maChuyen);
            }
            default -> throw new AccountException(403, "FORBIDDEN", "Không có quyền thao tác trên chuyến này.");
        };
    }

    /** Lock company, trip, employee, assignment in a stable order before order/ticket locks. */
    private ChuyenXe authorizeAssignedEmployee(EntityManager em, NguoiDung user, String tripId) {
        if (user.loaiTaiKhoan != LoaiTaiKhoan.NHAN_VIEN || user.nhanVien == null)
            throw AccessControl.denied();

        String employeeId = user.nhanVien.maNhanVien;
        NhanVien initialEmployee = em.find(NhanVien.class, employeeId);
        if (initialEmployee == null || initialEmployee.nhaXe == null) throw AccessControl.denied();
        NhaXe company = AccessControl.resource(em, NhaXe.class, initialEmployee.nhaXe.maNhaXe);
        ChuyenXe trip = AccessControl.resource(em, ChuyenXe.class, tripId);
        NhanVien employee = AccessControl.resource(em, NhanVien.class, employeeId);

        if (employee.trangThai != TrangThaiNhanVien.DANG_LAM_VIEC
                || employee.nhaXe == null || company.trangThai != TrangThaiNhaXe.HOAT_DONG
                || trip.tuyenXe.nhaXe == null
                || !employee.nhaXe.maNhaXe.equals(company.maNhaXe)
                || !trip.tuyenXe.nhaXe.maNhaXe.equals(company.maNhaXe)) throw AccessControl.denied();

        List<PhanCongChuyenXe> assignments = em.createQuery(
                        "select p from PhanCongChuyenXe p where p.nhanVien = :employee and p.chuyenXe = :trip",
                        PhanCongChuyenXe.class)
                .setParameter("employee", employee)
                .setParameter("trip", trip)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        if (assignments.isEmpty()) throw AccessControl.denied();
        try {
            for (PhanCongChuyenXe assignment : assignments) {
                em.refresh(assignment, LockModeType.PESSIMISTIC_WRITE);
                if (!assignment.nhanVien.maNhanVien.equals(employee.maNhanVien)
                        || !assignment.chuyenXe.maChuyen.equals(trip.maChuyen)) throw AccessControl.denied();
            }
        } catch (EntityNotFoundException e) {
            throw AccessControl.denied();
        }
        return trip;
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,128}"))
            throw AccountException.invalid("Idempotency-Key", "Cần khóa idempotency hợp lệ.");
    }

    private static String checkInHash(String maVe, String maChuyen) {
        var request = JSON.createObjectNode().put("maVe", maVe).put("maChuyen", maChuyen);
        return IdempotencyRepository.hash(request.toString());
    }

    private static CheckInResponse replay(IdempotencyRepository.Saved saved, String hash) {
        if (!saved.hash().equals(hash))
            throw new AccountException(409, "IDEMPOTENCY_KEY_REUSED", "Khóa đã được dùng cho nội dung khác.");
        if (saved.status() != 200) throw new IllegalStateException("Invalid check-in idempotency status");
        try {
            return JSON.readValue(saved.body(), CheckInResponse.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid check-in idempotency response", e);
        }
    }

    private record TicketReference(String maVe, String maDon, String maChuyen) {}

    private TicketReference findTicketReference(EntityManager em, String code) {
        List<Object[]> rows = em.createQuery(
                        "select v.maVe, v.donDatVe.maDon, v.chuyenXe.maChuyen from VeXe v "
                                + "where v.maVe = :code or v.maTraCuu = :code", Object[].class)
                .setParameter("code", code)
                .getResultList();
        if (rows.isEmpty()) return null;
        if (rows.size() != 1)
            throw new AccountException(409, "TICKET_CODE_AMBIGUOUS", "Mã vé không xác định duy nhất.");
        Object[] row = rows.getFirst();
        return new TicketReference((String) row[0], (String) row[1], (String) row[2]);
    }

    private TicketReference findTicketReferenceByQr(EntityManager em, String qr) {
        List<Object[]> rows = em.createQuery(
                        "select v.maVe, v.donDatVe.maDon, v.chuyenXe.maChuyen from VeXe v where v.maQR = :qr",
                        Object[].class)
                .setParameter("qr", qr)
                .getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = rows.getFirst();
        return new TicketReference((String) row[0], (String) row[1], (String) row[2]);
    }

    private static String toIsoString(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.atZone(TripSaleRules.ZONE).toOffsetDateTime().toString();
    }

    private static String toIsoString(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return toIsoString(ldt);
        if (value instanceof java.sql.Timestamp timestamp) return toIsoString(timestamp.toLocalDateTime());
        if (value instanceof java.time.OffsetDateTime timestamp)
            return timestamp.atZoneSameInstant(TripSaleRules.ZONE).toOffsetDateTime().toString();
        if (value instanceof Instant timestamp)
            return timestamp.atZone(TripSaleRules.ZONE).toOffsetDateTime().toString();
        throw new IllegalStateException("Unsupported check-in timestamp type: " + value.getClass().getName());
    }

    private Map<String, String> latestCheckinTimes(EntityManager em, String tripId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select ma_ve, max(thoi_diem_kiem) from nhat_ky_kiem_ve "
                                + "where ma_chuyen = :trip and ket_qua = 'DA_LEN_XE' group by ma_ve")
                .setParameter("trip", tripId)
                .getResultList();
        Map<String, String> result = new HashMap<>();
        for (Object[] row : rows) result.put((String) row[0], toIsoString(row[1]));
        return result;
    }

    public VerifyResponse verify(VerifyRequest request) {
        if (request == null || request.maChuyen() == null || request.maChuyen().isBlank()) {
            throw new DomainException(400, "Mã chuyến là bắt buộc.");
        }
        String tripId = request.maChuyen().strip();
        if (!tripId.matches("[A-Za-z0-9_-]{1,40}"))
            throw AccountException.invalid("maChuyen", "Mã chuyến không hợp lệ.");
        boolean hasTicket = request.maVe() != null && !request.maVe().isBlank();
        boolean hasQr = request.qrToken() != null && !request.qrToken().isBlank();
        if ((hasTicket && hasQr) || (!hasTicket && !hasQr)) {
            throw new DomainException(400, "Cần gửi đúng một trong mã vé hoặc mã QR.");
        }
        String ticketCode = hasTicket ? request.maVe().strip() : null;
        String qrToken = hasQr ? request.qrToken().strip() : null;
        if (hasTicket && !ticketCode.matches("[A-Za-z0-9_-]{1,40}"))
            throw AccountException.invalid("maVe", "Mã vé không hợp lệ.");
        if (hasQr && !qrToken.matches("[A-Za-z0-9_-]{1,80}"))
            throw AccountException.invalid("qrToken", "Mã QR không hợp lệ.");

        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            NguoiDung user = caller(em);
            ChuyenXe trip = authorizeTrip(em, user, tripId);
            if (trip.trangThai == TrangThaiChuyen.DA_HUY || trip.trangThai == TrangThaiChuyen.HOAN_TAT) {
                tx.commit();
                return new VerifyResponse(false, null, tripId, null,
                        "TRIP_UNAVAILABLE", "Chuyến xe đã kết thúc hoặc bị hủy.",
                        null, null, null, null, null, null, null);
            }

            TicketReference reference = hasTicket
                    ? findTicketReference(em, ticketCode)
                    : findTicketReferenceByQr(em, qrToken);

            VerifyResponse resp;
            if (reference == null) {
                resp = new VerifyResponse(false, ticketCode, tripId, null,
                        "RESOURCE_NOT_FOUND", "Không tìm thấy vé hợp lệ cho chuyến này.",
                        null, null, null, null, null, null, null);
            } else if (!reference.maChuyen().equals(tripId)) {
                resp = new VerifyResponse(false, null, tripId, null,
                        "TRIP_MISMATCH", "Vé không thuộc chuyến xe này.",
                        null, null, null, null, null, null, null);
            } else {
                AccessControl.resource(em, DonDatVe.class, reference.maDon());
                VeXe ticket = AccessControl.resource(em, VeXe.class, reference.maVe());
                if (!ticket.chuyenXe.maChuyen.equals(tripId)
                        || !ticket.maDonDatVe.equals(reference.maDon())) {
                    throw new AccountException(409, "RESOURCE_CHANGED", "Thông tin vé vừa thay đổi.");
                }
                if (new HoanVeRepository(em).ticketBlocked(ticket.maVe)) {
                    resp = new VerifyResponse(false, ticket.maVe, tripId, ticket.maGhe,
                            "TICKET_REFUNDING", "Vé đang trong quá trình hoàn tiền.",
                            ticket.hoTenKhachHang, ticket.soDienThoaiKhachHang, ticket.cccdHanhKhach,
                            ticket.maTraCuu, ticket.giaVe, ticket.chuyenXe.tuyenXe.tenTuyen,
                            ticket.chuyenXe.xe != null ? ticket.chuyenXe.xe.bienSo : null);
                } else if (ticket.trangThai == TrangThaiVe.DA_HOAN) {
                    resp = new VerifyResponse(false, ticket.maVe, tripId, ticket.maGhe,
                            "TICKET_REFUNDED", "Vé đã được hoàn tiền.",
                            ticket.hoTenKhachHang, ticket.soDienThoaiKhachHang, ticket.cccdHanhKhach,
                            ticket.maTraCuu, ticket.giaVe, ticket.chuyenXe.tuyenXe.tenTuyen,
                            ticket.chuyenXe.xe != null ? ticket.chuyenXe.xe.bienSo : null);
                } else if (ticket.trangThai == TrangThaiVe.DA_HUY) {
                    resp = new VerifyResponse(false, ticket.maVe, tripId, ticket.maGhe,
                            "TICKET_CANCELLED", "Vé đã bị hủy.",
                            ticket.hoTenKhachHang, ticket.soDienThoaiKhachHang, ticket.cccdHanhKhach,
                            ticket.maTraCuu, ticket.giaVe, ticket.chuyenXe.tuyenXe.tenTuyen,
                            ticket.chuyenXe.xe != null ? ticket.chuyenXe.xe.bienSo : null);
                } else if (ticket.trangThai == TrangThaiVe.DA_LEN_XE) {
                    resp = new VerifyResponse(false, ticket.maVe, tripId, ticket.maGhe,
                            "TICKET_ALREADY_CHECKED_IN", "Vé đã được xác nhận lên xe.",
                            ticket.hoTenKhachHang, ticket.soDienThoaiKhachHang, ticket.cccdHanhKhach,
                            ticket.maTraCuu, ticket.giaVe, ticket.chuyenXe.tuyenXe.tenTuyen,
                            ticket.chuyenXe.xe != null ? ticket.chuyenXe.xe.bienSo : null);
                } else {
                    resp = new VerifyResponse(true, ticket.maVe, tripId, ticket.maGhe,
                            null, null,
                            ticket.hoTenKhachHang, ticket.soDienThoaiKhachHang, ticket.cccdHanhKhach,
                            ticket.maTraCuu, ticket.giaVe, ticket.chuyenXe.tuyenXe.tenTuyen,
                            ticket.chuyenXe.xe != null ? ticket.chuyenXe.xe.bienSo : null);
                }
            }
            tx.commit();
            return resp;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public CheckInResponse checkIn(String maVe, CheckInRequest request, String idempotencyKey) {
        if (request == null || request.maChuyen() == null || request.maChuyen().isBlank())
            throw new DomainException(400, "Mã chuyến là bắt buộc.");
        if (maVe == null || maVe.isBlank()) throw new DomainException(400, "Mã vé là bắt buộc.");
        String ticketCode = maVe.strip();
        String tripId = request.maChuyen().strip();
        if (!ticketCode.matches("[A-Za-z0-9_-]{1,40}"))
            throw AccountException.invalid("maVe", "Mã vé không hợp lệ.");
        if (!tripId.matches("[A-Za-z0-9_-]{1,40}"))
            throw AccountException.invalid("maChuyen", "Mã chuyến không hợp lệ.");
        validateIdempotencyKey(idempotencyKey);
        var context = RequestIdentity.get();
        if (context == null || context.user() == null || context.path() == null
                || !"POST".equals(context.method()))
            throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
        IdempotencyRepository.Scope scope = new IdempotencyRepository.Scope(
                context.user().maNguoiDung(), "POST", context.path(), idempotencyKey);
        String hash = checkInHash(ticketCode, tripId);

        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            IdempotencyRepository idempotency = new IdempotencyRepository(em);
            idempotency.lock(scope);
            NguoiDung user = caller(em);
            if (user.loaiTaiKhoan != LoaiTaiKhoan.NHAN_VIEN || user.nhanVien == null)
                throw AccessControl.denied();

            IdempotencyRepository.Saved saved = idempotency.find(scope, Instant.now());
            if (saved != null) {
                CheckInResponse response = replay(saved, hash);
                authorizeAssignedEmployee(em, user, tripId);
                tx.commit();
                return response;
            }

            ChuyenXe trip = authorizeAssignedEmployee(em, user, tripId);
            if (trip.trangThai == TrangThaiChuyen.DA_HUY || trip.trangThai == TrangThaiChuyen.HOAN_TAT)
                throw new AccountException(409, "INVALID_STATE_TRANSITION", "Chuyến xe đã kết thúc hoặc bị hủy.");
            TicketReference reference = findTicketReference(em, ticketCode);
            if (reference == null) throw new DomainException(404, "Không tìm thấy vé.");
            if (!reference.maChuyen().equals(tripId))
                throw new DomainException(409, "Vé không thuộc chuyến xe đã chỉ định.");

            AccessControl.resource(em, DonDatVe.class, reference.maDon());
            VeXe ticket = AccessControl.resource(em, VeXe.class, reference.maVe());
            if (!ticket.chuyenXe.maChuyen.equals(tripId) || !ticket.maDonDatVe.equals(reference.maDon()))
                throw new AccountException(409, "RESOURCE_CHANGED", "Thông tin vé vừa thay đổi.");

            AccessControl.ticketNotRefunding(em, ticket.maVe);

            if (ticket.trangThai == TrangThaiVe.DA_LEN_XE) {
                throw new AccountException(409, "TICKET_ALREADY_CHECKED_IN", "Vé đã được xác nhận lên xe.",
                        Map.of("maVe", ticket.maVe, "maChuyen", request.maChuyen()));
            }

            if (ticket.trangThai != TrangThaiVe.DA_XUAT_VE) {
                throw new AccountException(409, "INVALID_STATE_TRANSITION", "Trạng thái vé hiện tại không cho phép xác nhận lên xe.",
                        Map.of("maVe", ticket.maVe, "maChuyen", request.maChuyen()));
            }

            LocalDateTime now = LocalDateTime.now(TripSaleRules.ZONE);
            ticket.trangThai = TrangThaiVe.DA_LEN_XE;
            CheckInResponse response = new CheckInResponse(ticket.maVe, ticket.chuyenXe.maChuyen, ticket.maGhe,
                    "DA_LEN_XE", toIsoString(now));

            em.createNativeQuery("insert into nhat_ky_kiem_ve(ma_log, ma_ve, ma_chuyen, ma_ghe, ket_qua, thoi_diem_kiem, ma_nhan_vien) " +
                            "values (:log, :ve, :chuyen, :ghe, :ketQua, :thoiDiem, :nv)")
                    .setParameter("log", UUID.randomUUID().toString())
                    .setParameter("ve", ticket.maVe)
                    .setParameter("chuyen", ticket.chuyenXe.maChuyen)
                    .setParameter("ghe", ticket.maGhe)
                    .setParameter("ketQua", "DA_LEN_XE")
                    .setParameter("thoiDiem", now)
                    .setParameter("nv", user.nhanVien.maNhanVien)
                    .executeUpdate();

            em.flush();
            try {
                idempotency.save(scope, hash, 200, JSON.writeValueAsString(response), Instant.now());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot serialize check-in idempotency response", e);
            }
            tx.commit();
            return response;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public CheckLogPage checkLogs(String maChuyen, int page, int size, String maGhe, String sort) {
        if (maChuyen == null || maChuyen.isBlank()) throw new DomainException(400, "Mã chuyến là bắt buộc.");
        int p = Math.max(1, page);
        int s = Math.max(1, Math.min(100, size));

        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            NguoiDung user = caller(em);
            authorizeTrip(em, user, maChuyen);

            String countSql = "select count(*) from nhat_ky_kiem_ve where ma_chuyen = :chuyen" + (maGhe != null && !maGhe.isBlank() ? " and ma_ghe = :ghe" : "");
            var countQuery = em.createNativeQuery(countSql).setParameter("chuyen", maChuyen);
            if (maGhe != null && !maGhe.isBlank()) countQuery.setParameter("ghe", maGhe);
            long total = ((Number) countQuery.getSingleResult()).longValue();

            String querySql = "select ma_ve, ma_chuyen, ma_ghe, ket_qua, thoi_diem_kiem from nhat_ky_kiem_ve where ma_chuyen = :chuyen"
                    + (maGhe != null && !maGhe.isBlank() ? " and ma_ghe = :ghe" : "")
                    + " order by " + ("maVe,asc".equalsIgnoreCase(sort) ? "ma_ve asc" : "thoi_diem_kiem desc");

            var listQuery = em.createNativeQuery(querySql)
                    .setParameter("chuyen", maChuyen)
                    .setFirstResult((p - 1) * s)
                    .setMaxResults(s);
            if (maGhe != null && !maGhe.isBlank()) listQuery.setParameter("ghe", maGhe);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = listQuery.getResultList();
            List<CheckLog> items = new ArrayList<>();
            for (Object[] r : rows) {
                items.add(new CheckLog(
                        (String) r[0],
                        (String) r[1],
                        (String) r[2],
                        (String) r[3],
                        toIsoString(r[4])
                ));
            }

            int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / s);
            var pageResult = new CheckLogPage(items, p, s, total, totalPages);
            tx.commit();
            return pageResult;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public PassengerListResponse passengerList(String maChuyen) {
        if (maChuyen == null || maChuyen.isBlank()) throw new DomainException(400, "Mã chuyến là bắt buộc.");

        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            NguoiDung user = caller(em);
            authorizeTrip(em, user, maChuyen);

            ChuyenXe trip = em.find(ChuyenXe.class, maChuyen);
            if (trip == null) throw new DomainException(404, "Không tìm thấy chuyến xe.");

            List<VeXe> tickets = em.createQuery("select v from VeXe v where v.chuyenXe.maChuyen = :c order by v.ghe.maGhe", VeXe.class)
                    .setParameter("c", maChuyen).getResultList();

            HoanVeRepository hoanVeRepo = new HoanVeRepository(em);
            Map<String, String> checkinTimes = latestCheckinTimes(em, maChuyen);
            List<PassengerItem> passengers = new ArrayList<>();
            int tongVe = tickets.size();
            int daLenXe = 0;
            int chuaLenXe = 0;
            int daHoan = 0;
            int daHuy = 0;

            for (VeXe v : tickets) {
                boolean dangHoan = hoanVeRepo.ticketBlocked(v.maVe);
                if (v.trangThai == TrangThaiVe.DA_LEN_XE) daLenXe++;
                else if (v.trangThai == TrangThaiVe.DA_XUAT_VE) chuaLenXe++;
                else if (v.trangThai == TrangThaiVe.DA_HOAN) daHoan++;
                else if (v.trangThai == TrangThaiVe.DA_HUY) daHuy++;

                passengers.add(new PassengerItem(
                        v.maVe,
                        v.maTraCuu,
                        v.maGhe,
                        v.hoTenKhachHang,
                        v.soDienThoaiKhachHang,
                        v.cccdHanhKhach,
                        v.trangThai.name(),
                        v.giaVe,
                        checkinTimes.get(v.maVe),
                        dangHoan
                ));
            }

            var resp = new PassengerListResponse(
                    trip.maChuyen,
                    trip.tuyenXe.tenTuyen,
                    trip.tuyenXe.nhaXe != null ? trip.tuyenXe.nhaXe.tenNhaXe : "",
                    trip.xe != null ? trip.xe.bienSo : "Chưa gán xe",
                    trip.ngayKhoiHanh != null ? trip.ngayKhoiHanh.toString() : "",
                    trip.gioKhoiHanh != null ? trip.gioKhoiHanh.toString() : "",
                    tongVe,
                    daLenXe,
                    chuaLenXe,
                    daHoan,
                    daHuy,
                    passengers
            );
            tx.commit();
            return resp;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }
}
