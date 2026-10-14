"""T20 owned-fixture HTTP smoke for ticket inspection and check-in.

Run from the repository root only after the T20 backend is deployed:
    python tests/backend/ticket_inspection_smoke.py

The script registers fresh local test accounts and creates only randomly
prefixed fixtures. Tickets and payments are seeded directly in PostgreSQL as
zero-value MIEN_PHI records; no payment gateway or receipt email is invoked.
"""
import json
import os
import uuid

os.environ.setdefault("DB_NAME", "datvexe")

import booking_order_smoke as t16
import ticket_refund_smoke as t19


RUN_ID = uuid.uuid4().hex[:10]
t16.PREFIX = "T20_" + RUN_ID + "_"
t16.IDEMPOTENCY_PREFIX = "T20-" + RUN_ID + "-"
RESULTS = []
OWNED_TICKET_IDS = []
STAFF_ACCOUNT_LINKED = False
PRIVATE_STAFF_ID = t16.ident("CHECKIN_STAFF")


class SkipScenario(Exception):
    """A required local schema or policy prerequisite is unavailable."""


def ident(suffix):
    return t16.ident(suffix)


def sql_literal(value):
    return t16.sql_literal(value)


def db(statement):
    t16.db(statement)


def db_value(statement):
    return t19.db_value(statement)


def call(actor, method, path, body=None, *, expected=200, key=None, csrf=True,
         anonymous=False):
    return t16.call(actor, method, path, body, expected=expected, key=key,
                    csrf=csrf, anonymous=anonymous)


def scenario(label, action):
    try:
        action()
        RESULTS.append(("PASS", label))
        print("PASS: " + label)
        return True
    except SkipScenario as skip:
        RESULTS.append(("SKIP", label))
        print("SKIP: " + label + " — " + str(skip))
        return False
    except Exception as error:
        RESULTS.append(("FAIL", label))
        print("FAIL: " + label + " — " + repr(error))
        raise


def prepare_stack_and_fixtures():
    # Check every table used by cleanup before registering disposable accounts.
    t16.db_assert(
        "to_regclass('nhat_ky_kiem_ve') IS NOT NULL AND "
        "to_regclass('refund_request') IS NOT NULL AND "
        "to_regclass('ticket_public_code_seq') IS NOT NULL AND "
        "EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() "
        "AND table_name='ve_xe' AND column_name='ma_qr')",
        "Ticket-code, check-in or refund-tracking migration is missing")
    active_policy = db_value(
        "SELECT ma_chinh_sach FROM chinh_sach_hoan_ve "
        "WHERE trang_thai='HOAT_DONG' ORDER BY ma_chinh_sach LIMIT 1")
    if not active_policy:
        raise SkipScenario("a local active refund policy is required for the synthetic guard fixture")
    t16.require_stack_migration_and_customers()
    t16.seed_owned_trip_fixtures()
    t19.POLICY_ID = active_policy
    seed_private_staff()
    fixtures = seed_ticket_fixtures()
    seed_pending_refund(fixtures["pending"], "CHO_XU_LY", "PENDING")
    seed_pending_refund(fixtures["reconcile"], "CAN_DOI_SOAT", "RECONCILE")
    return fixtures


def seed_private_staff():
    global STAFF_ACCOUNT_LINKED
    company = ident("OP")
    t16.STAFF_IDS.append(PRIVATE_STAFF_ID)
    db("INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,loai_nhan_vien,"
       "trang_thai,ma_nha_xe) VALUES ("
       + ",".join((sql_literal(PRIVATE_STAFF_ID), sql_literal("T20 fixture inspector"),
                   sql_literal(t16.staff_phone()),
                   sql_literal(PRIVATE_STAFF_ID.lower() + "@example.test"),
                   "'PHU_XE'", "'DANG_LAM_VIEC'", sql_literal(company))) + ")")
    db("INSERT INTO phan_cong_chuyen_xe(ma_phan_cong,ma_chuyen,ma_nhan_vien,vai_tro) VALUES "
       + ",".join("(" + ",".join((sql_literal(ident("CHECKIN_ASSIGN_" + str(index))),
                                    sql_literal(t16.TRIP_IDS[index - 1]),
                                    sql_literal(PRIVATE_STAFF_ID), "'PHU_XE'")) + ")"
                   for index in (1, 2)))
    db("UPDATE nguoi_dung SET loai_tai_khoan='NHAN_VIEN',ma_nha_xe=NULL,ma_nhan_vien="
       + sql_literal(PRIVATE_STAFF_ID) + " WHERE ma_nguoi_dung="
       + sql_literal(t16.CUSTOMER_C.user_id))
    STAFF_ACCOUNT_LINKED = True
    t16.db_assert("EXISTS (SELECT 1 FROM nguoi_dung WHERE ma_nguoi_dung="
                  + sql_literal(t16.CUSTOMER_C.user_id)
                  + " AND loai_tai_khoan='NHAN_VIEN' AND ma_nhan_vien="
                  + sql_literal(PRIVATE_STAFF_ID) + ")",
                  "Fresh T20 staff account was not linked to the private fixture employee")


def seed_ticket(name, trip_index, seat_index, state="DA_XUAT_VE"):
    order_id = ident("ORDER_" + name)
    payment_id = ident("PAYMENT_" + name)
    ticket_id = ident("TICKET_" + name)
    t19.order_insert(order_id, t16.CUSTOMER_A, 0)
    t19.payment_insert(order_id, payment_id, 0, "MIEN_PHI")
    t19.ticket_insert(order_id, ticket_id, t16.TRIP_IDS[trip_index],
                       t16.SEAT_IDS[seat_index], 0)
    OWNED_TICKET_IDS.append(ticket_id)
    if state != "DA_XUAT_VE":
        db("UPDATE ve_xe SET trang_thai=" + sql_literal(state)
           + " WHERE ma_ve=" + sql_literal(ticket_id))
    code_qr = db_value("SELECT ma_tra_cuu || '|' || ma_qr FROM ve_xe WHERE ma_ve="
                       + sql_literal(ticket_id))
    fields = code_qr.split("|", 1)
    assert len(fields) == 2 and all(fields), (ticket_id, code_qr)
    return {"name": name, "order": order_id, "payment": payment_id,
            "ticket": ticket_id, "code": fields[0], "qr": fields[1],
            "trip": t16.TRIP_IDS[trip_index], "state": state}


def seed_ticket_fixtures():
    # Five seats on one private trip give the passenger-list assertions several states.
    fixtures = {
        "main": seed_ticket("MAIN", 0, 0),
        "pending": seed_ticket("PENDING", 0, 1),
        "reconcile": seed_ticket("RECONCILE", 0, 2),
        "refunded": seed_ticket("REFUNDED", 0, 3, "DA_HOAN"),
        "cancelled": seed_ticket("CANCELLED", 0, 4, "DA_HUY"),
    }
    return fixtures


def seed_pending_refund(fixture, state, suffix):
    line = {
        "maVe": fixture["ticket"],
        "giaThucTra": "0",
        "tyLeHoan": "80",
        "phiHoan": "0",
        "soTienHoan": "0",
    }
    details = json.dumps([line], separators=(",", ":"))
    db("INSERT INTO refund_request(ma_hoan_tien,ma_don,ma_giao_dich_goc,ma_nguoi_yeu_cau,"
       "ma_chinh_sach,ly_do,so_tien_hoan,trang_thai,chi_tiet,lich_su,ma_request_cong,"
       "loai_hoan_toan_phan,created_at) VALUES ("
       + ",".join((sql_literal(ident("REFUND_" + suffix)),
                   sql_literal(fixture["order"]), sql_literal(fixture["payment"]),
                   sql_literal(t16.CUSTOMER_A.user_id), sql_literal(t19.POLICY_ID),
                   sql_literal("T20 synthetic check-in guard"), "0",
                   sql_literal(state), "CAST(" + sql_literal(details) + " AS jsonb)",
                   "'[]'::jsonb", sql_literal(uuid.uuid4().hex), "TRUE", "CURRENT_TIMESTAMP"))
       + ")")


def verify(ticket, trip=None, *, lookup=None, qr=False, expected=200):
    body = {"maChuyen": trip or ticket["trip"]}
    if qr:
        body["qrToken"] = ticket["qr"]
    else:
        # The public five-character code is submitted in the maVe field too.
        body["maVe"] = lookup or ticket["ticket"]
    return call(t16.CUSTOMER_C, "POST", "/kiem-ve/xac-minh", body, expected=expected)


def f01_lookup_scope_and_privacy(fixtures):
    main = fixtures["main"]
    anonymous = call(None, "POST", "/kiem-ve/xac-minh",
                     {"maChuyen": main["trip"], "maVe": main["ticket"]},
                     expected=401, anonymous=True)
    assert anonymous["code"] == "UNAUTHENTICATED", anonymous

    csrf_denied = call(t16.CUSTOMER_C, "POST", "/kiem-ve/xac-minh",
                       {"maChuyen": main["trip"], "maVe": main["ticket"]},
                       expected=403, csrf=False)
    assert csrf_denied["code"] == "FORBIDDEN", csrf_denied

    full = verify(main)
    short = verify(main, lookup=main["code"])
    qr = verify(main, qr=True)
    for result in (full, short, qr):
        assert result["hopLe"] is True and result["maVe"] == main["ticket"], result
        assert result["maChuyen"] == main["trip"] and result["maGhe"], result
    assert full["maTraCuu"] == main["code"] and short["maTraCuu"] == main["code"], (full, short)

    # Both trips are assigned to this fixture employee, so this reaches the mismatch branch.
    mismatch = verify(main, trip=t16.TRIP_IDS[1])
    assert mismatch["hopLe"] is False and mismatch["maLyDo"] == "TRIP_MISMATCH", mismatch
    for field in ("hoTenHanhKhach", "soDienThoaiHanhKhach", "cccdHanhKhach",
                  "maTraCuu", "giaVe", "tenTuyen", "bienSoXe"):
        assert mismatch.get(field) is None, (field, mismatch)
    encoded_mismatch = json.dumps(mismatch, ensure_ascii=False)
    assert "T19 Passenger" not in encoded_mismatch and "0900000019" not in encoded_mismatch, mismatch

    # Trip 3 has the same operator but no assignment for the fresh staff account.
    unassigned = call(t16.CUSTOMER_C, "POST", "/kiem-ve/xac-minh",
                      {"maChuyen": t16.TRIP_IDS[2], "maVe": main["ticket"]}, expected=403)
    assert unassigned["code"] == "FORBIDDEN", unassigned
    customer = call(t16.CUSTOMER_B, "POST", "/ve-xe/" + main["ticket"] + "/len-xe",
                    {"maChuyen": main["trip"]}, expected=403,
                    key=t16.idem_key("customer-cannot-check-in"))
    assert customer["code"] == "FORBIDDEN", customer


def assert_no_log(ticket_id):
    count = int(db_value("SELECT count(*) FROM nhat_ky_kiem_ve WHERE ma_ve="
                         + sql_literal(ticket_id)))
    assert count == 0, (ticket_id, count)


def check_in(ticket, *, key, trip=None, expected=200):
    status, result, _ = t16.exchange(
        t16.CUSTOMER_C, "POST", "/ve-xe/" + ticket["ticket"] + "/len-xe",
        {"maChuyen": trip or ticket["trip"]}, key=key)
    assert status == expected, (ticket["ticket"], expected, status, result)
    return result


def f02_idempotent_check_in_and_state_guards(fixtures):
    main = fixtures["main"]
    assert_no_log(main["ticket"])

    missing_key = call(t16.CUSTOMER_C, "POST", "/ve-xe/" + main["ticket"] + "/len-xe",
                       {"maChuyen": main["trip"]}, expected=400)
    assert missing_key["code"] == "VALIDATION_ERROR", missing_key
    assert_no_log(main["ticket"])

    key = t16.idem_key("check-in-" + uuid.uuid4().hex)
    first = check_in(main, key=key)
    assert first["maVe"] == main["ticket"] and first["maChuyen"] == main["trip"], first
    assert first["trangThai"] == "DA_LEN_XE" and first["thoiDiemLenXe"], first

    replay = check_in(main, key=key)
    assert replay == first, (first, replay)
    assert int(db_value("SELECT count(*) FROM nhat_ky_kiem_ve WHERE ma_ve="
                        + sql_literal(main["ticket"]) + " AND ket_qua='DA_LEN_XE'")) == 1

    changed = call(t16.CUSTOMER_C, "POST", "/ve-xe/" + main["ticket"] + "/len-xe",
                   {"maChuyen": t16.TRIP_IDS[1]}, expected=409, key=key)
    assert changed["code"] == "IDEMPOTENCY_KEY_REUSED", changed

    already_boarded = call(t16.CUSTOMER_C, "POST", "/ve-xe/" + main["ticket"] + "/len-xe",
                           {"maChuyen": main["trip"]}, expected=409,
                           key=t16.idem_key("check-in-second-" + uuid.uuid4().hex))
    assert already_boarded["code"] == "TICKET_ALREADY_CHECKED_IN", already_boarded
    assert int(db_value("SELECT count(*) FROM nhat_ky_kiem_ve WHERE ma_ve="
                        + sql_literal(main["ticket"]) + " AND ket_qua='DA_LEN_XE'")) == 1

    checked = verify(main)
    assert checked["hopLe"] is False and checked["maLyDo"] == "TICKET_ALREADY_CHECKED_IN", checked

    for name in ("pending", "reconcile"):
        ticket = fixtures[name]
        blocked = verify(ticket)
        assert blocked["hopLe"] is False and blocked["maLyDo"] == "TICKET_REFUNDING", blocked
        response = call(t16.CUSTOMER_C, "POST", "/ve-xe/" + ticket["ticket"] + "/len-xe",
                        {"maChuyen": ticket["trip"]}, expected=409,
                        key=t16.idem_key("blocked-" + name + "-" + uuid.uuid4().hex))
        assert response["code"] == "REFUND_IN_PROGRESS", response
        assert_no_log(ticket["ticket"])

    for name, reason in (("refunded", "TICKET_REFUNDED"),
                         ("cancelled", "TICKET_CANCELLED")):
        ticket = fixtures[name]
        rejected = verify(ticket)
        assert rejected["hopLe"] is False and rejected["maLyDo"] == reason, rejected
        response = call(t16.CUSTOMER_C, "POST", "/ve-xe/" + ticket["ticket"] + "/len-xe",
                        {"maChuyen": ticket["trip"]}, expected=409,
                        key=t16.idem_key("terminal-" + name + "-" + uuid.uuid4().hex))
        assert response["code"] == "INVALID_STATE_TRANSITION", response
        assert_no_log(ticket["ticket"])


def f03_staff_history_and_passenger_list(fixtures):
    trip = t16.TRIP_IDS[0]
    logs = call(t16.CUSTOMER_C, "GET", "/chuyen-xe/" + trip + "/kiem-ve?page=1&size=100")
    assert logs["totalItems"] == 1 and logs["totalPages"] == 1, logs
    assert len(logs["items"]) == 1, logs
    assert logs["items"][0]["maVe"] == fixtures["main"]["ticket"]
    assert logs["items"][0]["ketQua"] == "DA_LEN_XE"

    direct_count = int(db_value("SELECT count(*) FROM nhat_ky_kiem_ve WHERE ma_ve="
                                + sql_literal(fixtures["main"]["ticket"])))
    assert direct_count == 1, direct_count

    passengers = call(t16.CUSTOMER_C, "GET", "/chuyen-xe/" + trip + "/hanh-khach")
    assert passengers["maChuyen"] == trip and passengers["tongVe"] == 5, passengers
    assert (passengers["daLenXe"], passengers["chuaLenXe"],
            passengers["daHoan"], passengers["daHuy"]) == (1, 2, 1, 1), passengers
    by_ticket = {item["maVe"]: item for item in passengers["danhSachHanhKhach"]}
    assert set(by_ticket) == set(OWNED_TICKET_IDS), by_ticket
    assert by_ticket[fixtures["main"]["ticket"]]["trangThai"] == "DA_LEN_XE"
    for name in ("pending", "reconcile"):
        row = by_ticket[fixtures[name]["ticket"]]
        assert row["trangThai"] == "DA_XUAT_VE" and row["dangHoan"] is True, row
    assert by_ticket[fixtures["refunded"]["ticket"]]["trangThai"] == "DA_HOAN"
    assert by_ticket[fixtures["cancelled"]["ticket"]]["trangThai"] == "DA_HUY"

    for path in ("/chuyen-xe/" + t16.TRIP_IDS[2] + "/kiem-ve",
                 "/chuyen-xe/" + t16.TRIP_IDS[2] + "/hanh-khach"):
        denied = call(t16.CUSTOMER_C, "GET", path, expected=403)
        assert denied["code"] == "FORBIDDEN", denied


def f04_system_inspector_without_operator_assignment():
    db("UPDATE nhan_vien SET ma_nha_xe=NULL,loai_nhan_vien='KIEM_VE' WHERE ma_nhan_vien=" + sql_literal(PRIVATE_STAFF_ID))
    db("DELETE FROM phan_cong_chuyen_xe WHERE ma_nhan_vien=" + sql_literal(PRIVATE_STAFF_ID))
    trips = call(t16.CUSTOMER_C, "GET", "/kiem-ve/chuyen-xe")
    assert any(item["maChuyen"] == t16.TRIP_IDS[0] for item in trips), trips
    passengers = call(t16.CUSTOMER_C, "GET", "/chuyen-xe/" + t16.TRIP_IDS[2] + "/hanh-khach")
    assert passengers["maChuyen"] == t16.TRIP_IDS[2], passengers
    ticket = seed_ticket("SYSTEM", 2, 0)
    verified = verify(ticket)
    assert verified["hopLe"] is True, verified
    boarded = check_in(ticket, key=t16.idem_key("system-check-in"))
    assert boarded["trangThai"] == "DA_LEN_XE", boarded
    assert int(db_value("SELECT count(*) FROM nhat_ky_kiem_ve WHERE ma_ve=" + sql_literal(ticket["ticket"])
                        + " AND ma_nhan_vien=" + sql_literal(PRIVATE_STAFF_ID))) == 1
    call(t16.CUSTOMER_A, "GET", "/kiem-ve/chuyen-xe", expected=403)
    db("UPDATE nhan_vien SET trang_thai='TAM_NGHI' WHERE ma_nhan_vien=" + sql_literal(PRIVATE_STAFF_ID))
    call(t16.CUSTOMER_C, "GET", "/kiem-ve/chuyen-xe", expected=403)
    call(t16.CUSTOMER_C, "GET", "/chuyen-xe/" + t16.TRIP_IDS[0] + "/hanh-khach", expected=403)


def cleanup():
    global STAFF_ACCOUNT_LINKED
    errors = []
    if STAFF_ACCOUNT_LINKED and t16.CUSTOMER_C.user_id:
        try:
            db("UPDATE nguoi_dung SET loai_tai_khoan='KHACH_HANG',ma_nhan_vien=NULL,ma_nha_xe=NULL "
               "WHERE ma_nguoi_dung=" + sql_literal(t16.CUSTOMER_C.user_id))
            STAFF_ACCOUNT_LINKED = False
        except Exception as error:
            errors.append(("restore fresh staff account", error))
    if OWNED_TICKET_IDS:
        try:
            # Check-in logs have no cascade; delete only rows for this run's tickets first.
            owned = ",".join(sql_literal(ticket_id) for ticket_id in OWNED_TICKET_IDS)
            db("DELETE FROM nhat_ky_kiem_ve WHERE ma_ve IN (" + owned + ")")
        except Exception as error:
            errors.append(("delete T20-owned check-in logs", error))
    try:
        # This removes only orders belonging to the fresh accounts and the T16-prefixed transport fixture.
        t19.cleanup()
    except Exception as error:
        errors.append(("clean T20-owned fixture data", error))
    for client in reversed(t16.CLIENTS):
        try:
            client.close()
        except Exception as error:
            errors.append(("close T20 fixture account", error))
    if errors:
        raise RuntimeError("T20 cleanup failures: " + "; ".join(name + ": " + repr(error)
                                                                   for name, error in errors))


def main():
    error = None
    fixture_holder = {}
    try:
        def prepare():
            fixture_holder.update(prepare_stack_and_fixtures())

        if scenario("local backend, fresh accounts, migrations and owned transport fixture", prepare):
            # Setup has completed before HTTP assertions; seed tickets are all local zero-value rows.
            fixtures = fixture_holder
            scenario("F01 full/public ticket codes, QR, authorization and mismatch privacy",
                     lambda: f01_lookup_scope_and_privacy(fixtures))
            scenario("F02 required idempotency, replay, single log and terminal guards",
                     lambda: f02_idempotent_check_in_and_state_guards(fixtures))
            scenario("F03 assigned-staff logs, passenger state/history and scope",
                     lambda: f03_staff_history_and_passenger_list(fixtures))
            scenario("F04 system inspector without operator assignment and inactive-role guards",
                     f04_system_inspector_without_operator_assignment)
    except Exception as error_value:
        error = error_value
    finally:
        try:
            cleanup()
        except Exception as cleanup_error:
            print("FAIL: cleanup T20-owned fixtures — " + repr(cleanup_error))
            error = error or cleanup_error

    passed = sum(result == "PASS" for result, _ in RESULTS)
    failed = sum(result == "FAIL" for result, _ in RESULTS)
    skipped = sum(result == "SKIP" for result, _ in RESULTS)
    print("RESULT: %d PASS, %d SKIP, %d FAIL, %d HTTP assertions" %
          (passed, skipped, failed, t16.HTTP_ASSERTIONS))
    return 1 if error or failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
