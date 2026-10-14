"""T19 ticket-refund HTTP/PostgreSQL smoke for a locally deployed backend.

Run from the repository root after applying the refund-policy/refund-tracking
migrations and deploying the backend:
    python tests/backend/ticket_refund_smoke.py

The script creates disposable customers and T19-prefixed database fixtures. It
uses a successful fixture payment only for preview checks; the refund execution
checks use zero-value MIEN_PHI tickets and never call a payment provider.
"""
import os
import subprocess
import uuid
from decimal import Decimal

os.environ.setdefault("DB_NAME", "datvexe")

import booking_order_smoke as t16


t16.PREFIX = "T19_" + uuid.uuid4().hex[:10] + "_"
t16.IDEMPOTENCY_PREFIX = "T19-" + uuid.uuid4().hex[:10] + "-"
BASE = t16.BASE
CLIENTS = t16.CLIENTS
CUSTOMER_A = t16.CUSTOMER_A
CUSTOMER_B = t16.CUSTOMER_B
RESULTS = []
OWNED_ORDERS = []
OWNED_PAYMENTS = []
HTTP_ASSERTIONS = 0
POLICY_ID = None


class SkipScenario(Exception):
    """A documented prerequisite is unavailable; no pass is claimed."""


def ident(suffix):
    return t16.ident(suffix)


def idem_key(suffix):
    return t16.idem_key(suffix)


def db(statement):
    t16.db(statement)


def db_value(statement):
    result = subprocess.run(
        ["docker", "exec", "-i", os.environ.get("DB_CONTAINER", "datvexe-postgres"),
         "psql", "-U", "datvexe_user", "-d", os.environ.get("DB_NAME", "datvexe"),
         "-v", "ON_ERROR_STOP=1", "-A", "-t", "-c", statement],
        check=True, capture_output=True, text=True)
    return result.stdout.strip()


def db_assert(condition, message):
    t16.db_assert(condition, message)


def exchange(actor, method, path, body=None, *, key=None, csrf=True, anonymous=False,
             expected=200):
    global HTTP_ASSERTIONS
    status, result, headers = t16.exchange(
        actor, method, path, body, key=key, csrf=csrf, anonymous=anonymous)
    HTTP_ASSERTIONS += 1
    allowed = expected if isinstance(expected, tuple) else (expected,)
    assert status in allowed, (method, path, allowed, status, result)
    return status, result, headers


def call(actor, method, path, body=None, *, key=None, csrf=True, anonymous=False,
         expected=200):
    return exchange(actor, method, path, body, key=key, csrf=csrf,
                    anonymous=anonymous, expected=expected)[1]


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


def prepare_stack_and_policy():
    t16.require_stack_migration_and_customers()
    t16.seed_owned_trip_fixtures()
    global POLICY_ID
    active = db_value(
        "SELECT ma_chinh_sach || '|' || so_gio_toi_thieu_truoc_khoi_hanh || '|' || "
        "ty_le_hoan || '|' || phi_hoan || '|' || cho_phep_hoan_ve_khuyen_mai "
        "FROM chinh_sach_hoan_ve WHERE trang_thai='HOAT_DONG'")
    fields = active.split("|")
    if len(fields) != 5:
        raise SkipScenario("the database must have one active refund policy")
    POLICY_ID, hours, rate, fee, allow_promotion = fields
    if (int(hours) != 24 or Decimal(rate) != 80 or Decimal(fee) != 0
            or allow_promotion not in ("t", "true")):
        raise SkipScenario("the active policy is not the documented 24-hour, 80%, zero-fee demo policy")
    db_assert("to_regclass('payment_gateway_metadata') IS NOT NULL AND "
              "to_regclass('refund_request') IS NOT NULL AND "
              "EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() "
              "AND table_name='ve_xe' AND column_name='ma_qr')",
              "Refund-tracking migration is missing")


def order_insert(order_id, user, amount):
    db("INSERT INTO don_dat_ve(ma_don,ma_nguoi_dung,ngay_dat,tong_tien,tien_giam,"
       "ma_chinh_sach_hoan_ve,khach_dat_ten,khach_dat_so_dien_thoai,kenh_dat,trang_thai) VALUES ("
       + ",".join((t16.sql_literal(order_id), t16.sql_literal(user.user_id), "LOCALTIMESTAMP",
                   str(amount), "0", t16.sql_literal(POLICY_ID), t16.sql_literal("T19 Customer"),
                   t16.sql_literal("0900000019"), "'WEB'", "'DA_THANH_TOAN'")) + ")")
    OWNED_ORDERS.append(order_id)


def payment_insert(order_id, payment_id, amount, method):
    db("INSERT INTO thanh_toan(ma_giao_dich,ma_don,ma_phuong_thuc,so_tien,"
       "thoi_gian_thanh_toan,trang_thai) VALUES ("
       + ",".join((t16.sql_literal(payment_id), t16.sql_literal(order_id),
                   t16.sql_literal(method), str(amount), "LOCALTIMESTAMP", "'THANH_CONG'")) + ")")
    OWNED_PAYMENTS.append(payment_id)
    if method == "VNPAY":
        db("INSERT INTO payment_gateway_metadata(ma_giao_dich,merchant_code,request_created_at,"
           "provider_transaction_no) VALUES ("
           + ",".join((t16.sql_literal(payment_id), t16.sql_literal("T19_FIXTURE"),
                       "CURRENT_TIMESTAMP", t16.sql_literal(ident("PROVIDER_TXN")))) + ")")


def ticket_insert(order_id, ticket_id, trip_id, seat_id, amount):
    hold_id = ident("HOLD_" + ticket_id[-12:])
    db("INSERT INTO giu_cho(ma_giu_cho,ma_chuyen,ma_ghe,ma_don,ngay_dat,"
       "thoi_diem_bat_dau,thoi_diem_het_han,trang_thai) VALUES ("
       + ",".join((t16.sql_literal(hold_id), t16.sql_literal(trip_id), t16.sql_literal(seat_id),
                   t16.sql_literal(order_id), "LOCALTIMESTAMP", "LOCALTIMESTAMP - INTERVAL '1 hour'",
                   "LOCALTIMESTAMP + INTERVAL '1 day'", "'DA_XAC_NHAN'")) + ")")
    db("INSERT INTO ve_xe(ma_ve,ma_chuyen,ma_ghe,ma_don,ho_ten_khach_hang,"
       "so_dien_thoai_khach_hang,gia_ve,ngay_xuat_ve,trang_thai) VALUES ("
       + ",".join((t16.sql_literal(ticket_id), t16.sql_literal(trip_id), t16.sql_literal(seat_id),
                   t16.sql_literal(order_id), t16.sql_literal("T19 Passenger"),
                   t16.sql_literal("0900000019"), str(amount), "LOCALTIMESTAMP", "'DA_XUAT_VE'")) + ")")


def set_departure_offset(trip_id, interval):
    # The fixed interval literals are part of this script, not caller input.
    departure = "LOCALTIMESTAMP + INTERVAL " + t16.sql_literal(interval)
    db("UPDATE chuyen_xe SET ngay_khoi_hanh=(" + departure + ")::date, "
       "gio_khoi_hanh=(" + departure + ")::time, gio_den_du_kien=(" + departure
       + ") + INTERVAL '2 hours' WHERE ma_chuyen="
       + t16.sql_literal(trip_id))


def seed_refund_fixtures():
    paid_order = ident("PAID_ORDER")
    paid_payment = ident("PAID_PAYMENT")
    eligible_ticket = ident("ELIGIBLE_TICKET")
    ineligible_order = ident("LATE_ORDER")
    ineligible_payment = ident("LATE_PAYMENT")
    ineligible_ticket = ident("LATE_TICKET")
    zero_order = ident("ZERO_ORDER")
    zero_payment = ident("ZERO_PAYMENT")
    zero_ticket_a = ident("ZERO_TICKET_A")
    zero_ticket_b = ident("ZERO_TICKET_B")
    zero_ticket_c = ident("ZERO_TICKET_C")

    order_insert(paid_order, CUSTOMER_A, 180000)
    payment_insert(paid_order, paid_payment, 180000, "VNPAY")
    # Keep the outbound/return sequence used by the later zero-payment trip.
    set_departure_offset(t16.TRIP_IDS[1], "24 hours 5 minutes")
    ticket_insert(paid_order, eligible_ticket, t16.TRIP_IDS[1], t16.SEAT_IDS[0], 180000)

    order_insert(ineligible_order, CUSTOMER_A, 180000)
    payment_insert(ineligible_order, ineligible_payment, 180000, "VNPAY")
    set_departure_offset(t16.TRIP_IDS[0], "23 hours 55 minutes")
    ticket_insert(ineligible_order, ineligible_ticket, t16.TRIP_IDS[0], t16.SEAT_IDS[1], 180000)

    order_insert(zero_order, CUSTOMER_A, 0)
    payment_insert(zero_order, zero_payment, 0, "MIEN_PHI")
    ticket_insert(zero_order, zero_ticket_a, t16.TRIP_IDS[2], t16.SEAT_IDS[2], 0)
    ticket_insert(zero_order, zero_ticket_b, t16.TRIP_IDS[2], t16.SEAT_IDS[3], 0)
    ticket_insert(zero_order, zero_ticket_c, t16.TRIP_IDS[2], t16.SEAT_IDS[4], 0)

    return {
        "paid_order": paid_order,
        "ineligible_order": ineligible_order,
        "eligible_ticket": eligible_ticket,
        "ineligible_ticket": ineligible_ticket,
        "zero_order": zero_order,
        "zero_tickets": [zero_ticket_a, zero_ticket_b, zero_ticket_c],
    }


def my_order_history(fixtures):
    path = "/booking/me"
    anonymous = call(None, "GET", path, anonymous=True, expected=401)
    assert anonymous["code"] == "UNAUTHENTICATED", anonymous

    customer_orders = call(CUSTOMER_A, "GET", path)
    other_customer_orders = call(CUSTOMER_B, "GET", path)
    assert isinstance(customer_orders, list), customer_orders
    assert other_customer_orders == [], other_customer_orders

    expected = {
        fixtures["paid_order"]: ("DA_THANH_TOAN", "180000", t16.TRIP_IDS[1]),
        fixtures["ineligible_order"]: ("DA_THANH_TOAN", "180000", t16.TRIP_IDS[0]),
        fixtures["zero_order"]: ("DA_THANH_TOAN", "0", t16.TRIP_IDS[2]),
    }
    actual = {order["maDon"]: order for order in customer_orders}
    assert set(actual) == set(expected), actual
    for order_id, (status, amount, trip_id) in expected.items():
        order = actual[order_id]
        assert order["trangThai"] == status, order
        assert order["tongTien"] == amount, order
        assert order["maChuyen"] == trip_id, order

    # AuthFilter answers preflight before dispatching to the servlet.
    exchange(CUSTOMER_A, "OPTIONS", path, expected=204)
    _, method_error, method_headers = exchange(CUSTOMER_A, "POST", path, {}, expected=405)
    assert method_error["code"] == "METHOD_NOT_ALLOWED", method_error
    assert method_headers.get("Allow") == "GET, OPTIONS", method_headers
    query_error = call(CUSTOMER_A, "GET", path + "?page=1", expected=400)
    assert query_error["code"] == "VALIDATION_ERROR", query_error


def auth_csrf_and_policy_preview(fixtures):
    policy_path = "/ve-xe/" + fixtures["eligible_ticket"] + "/chinh-sach-hoan"
    call(None, "GET", policy_path, anonymous=True, expected=401)
    denied = call(CUSTOMER_B, "GET", policy_path, expected=403)
    assert denied["code"] == "FORBIDDEN", denied

    path = "/booking/" + fixtures["paid_order"] + "/hoan-tien/du-kien"
    denied = call(CUSTOMER_A, "POST", path,
                  {"danhSachMaVe": [fixtures["eligible_ticket"]]},
                  key=idem_key("preview-no-csrf"), csrf=False, expected=403)
    assert denied["code"] == "FORBIDDEN", denied

    eligible = call(CUSTOMER_A, "GET", policy_path)
    assert eligible["maVe"] == fixtures["eligible_ticket"] and eligible["duocYeuCauHoan"] is True, eligible
    assert eligible["soTienDuKienHoan"] == "144000" and eligible["donViTienTe"] == "VND", eligible

    late = call(CUSTOMER_A, "GET", "/ve-xe/" + fixtures["ineligible_ticket"] + "/chinh-sach-hoan")
    assert late["maVe"] == fixtures["ineligible_ticket"] and late["duocYeuCauHoan"] is False, late
    preview = call(CUSTOMER_A, "POST", path,
                   {"danhSachMaVe": [fixtures["eligible_ticket"]]},
                   key=idem_key("eligible-preview"))
    assert preview["maDon"] == fixtures["paid_order"] and preview["soTienHoan"] == "144000", preview
    assert preview["donViTienTe"] == "VND" and len(preview["chiTiet"]) == 1, preview
    assert preview["chiTiet"][0]["maVe"] == fixtures["eligible_ticket"], preview


def policy_admin_contract():
    path = "/quan-tri/chinh-sach-hoan-ve"
    policy_id = ident("POLICY")
    values = {"tenChinhSach": "T19 inactive fixture", "soGioToiThieuTruocKhoiHanh": 24,
              "tyLeHoan": 80, "phiHoan": "0", "choPhepHoanVeKhuyenMai": True,
              "trangThai": "TAM_NGUNG"}
    call(CUSTOMER_A, "GET", path, expected=403)
    db("UPDATE nguoi_dung SET loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung="
       + t16.sql_literal(CUSTOMER_B.user_id))
    try:
        page = call(CUSTOMER_B, "GET", path + "?page=1&size=10")
        assert page["page"] == 1 and page["size"] == 10, page
        call(CUSTOMER_B, "POST", path, dict(values, maChinhSach=policy_id),
             csrf=False, expected=403)
        created = call(CUSTOMER_B, "POST", path, dict(values, maChinhSach=policy_id), expected=201)
        assert created["maChinhSach"] == policy_id, created
        values["tyLeHoan"] = 50
        updated = call(CUSTOMER_B, "PUT", path + "/" + policy_id, values)
        assert updated["tyLeHoan"] == 50, updated
        detail = call(CUSTOMER_B, "GET", path + "/" + policy_id)
        assert detail["tyLeHoan"] == 50, detail
        call(CUSTOMER_B, "PUT", path + "/" + policy_id + "/trang-thai", {"trangThai": "TAM_NGUNG"})
        # Seeded paid orders already reference the active policy; no policy is switched.
        call(CUSTOMER_B, "PUT", path + "/" + POLICY_ID, values, expected=409)
    finally:
        db("DELETE FROM chinh_sach_hoan_ve WHERE ma_chinh_sach=" + t16.sql_literal(policy_id))
        db("UPDATE nguoi_dung SET loai_tai_khoan='KHACH_HANG' WHERE ma_nguoi_dung="
           + t16.sql_literal(CUSTOMER_B.user_id))


def response_items(result):
    if isinstance(result, list):
        return result
    if isinstance(result, dict) and isinstance(result.get("items"), list):
        return result["items"]
    return [result]


def refunds_are_local_and_persisted(fixtures):
    single_ticket, batch_ticket_a, batch_ticket_b = fixtures["zero_tickets"]
    seat_request = {"maChuyen": t16.TRIP_IDS[2], "danhSachMaGhe": [t16.SEAT_IDS[2]]}
    occupied = call(CUSTOMER_B, "POST", "/booking/hold", seat_request,
                    key=idem_key("occupied-before-refund"), expected=409)
    assert occupied["code"] == "SEAT_UNAVAILABLE", occupied
    denied = call(CUSTOMER_A, "POST", "/ve-xe/" + single_ticket + "/hoan-tien", {},
                  key=idem_key("single-no-csrf"), csrf=False, expected=403)
    assert denied["code"] == "FORBIDDEN", denied

    single = call(CUSTOMER_A, "POST", "/ve-xe/" + single_ticket + "/hoan-tien", {},
                  key=idem_key("single-zero"), expected=202)
    assert single["maHoanTien"] and single["soTienHoan"] == "0", single
    assert single["trangThai"] == "CHO_XU_LY", single
    replay = call(CUSTOMER_A, "POST", "/ve-xe/" + single_ticket + "/hoan-tien", {},
                  key=idem_key("single-zero"), expected=202)
    assert replay == single, replay
    # Replay is the accepted historical result; GET is the current state.
    assert replay["trangThai"] == "CHO_XU_LY", replay
    conflict = call(CUSTOMER_A, "POST", "/ve-xe/" + single_ticket + "/hoan-tien",
                    {"lyDo": "changed"}, key=idem_key("single-zero"), expected=409)
    assert conflict["code"] == "IDEMPOTENCY_KEY_REUSED", conflict
    remaining = call(CUSTOMER_A, "GET", "/ve-xe/" + batch_ticket_a)
    assert remaining["trangThai"] == "DA_XUAT_VE" and remaining["qrToken"], remaining

    batch = call(CUSTOMER_A, "POST", "/booking/" + fixtures["zero_order"] + "/hoan-tien",
                 {"danhSachMaVe": [batch_ticket_a, batch_ticket_b]},
                 key=idem_key("batch-zero"), expected=202)
    items = response_items(batch)
    assert len(items) == 1, batch
    batch_refund = items[0]
    assert batch_refund["maHoanTien"] and batch_refund["soTienHoan"] == "0", batch_refund
    assert batch_refund["trangThai"] == "CHO_XU_LY", batch_refund
    assert {line["maVe"] for line in batch_refund["chiTiet"]} == {batch_ticket_a, batch_ticket_b}, batch_refund

    for result in (single, batch_refund):
        stored = call(CUSTOMER_A, "GET", "/hoan-tien/" + result["maHoanTien"])
        assert stored["maHoanTien"] == result["maHoanTien"]
        assert stored["trangThai"] == "THANH_CONG" and stored["soTienHoan"] == "0", stored

    history = call(CUSTOMER_A, "GET", "/ve-xe/" + single_ticket + "/hoan-tien?page=1&size=10")
    assert any(item["maHoanTien"] == single["maHoanTien"] for item in history["items"]), history

    for ticket_id in (single_ticket, batch_ticket_a, batch_ticket_b):
        ticket = call(CUSTOMER_A, "GET", "/ve-xe/" + ticket_id)
        assert ticket["trangThai"] == "DA_HOAN" and ticket.get("qrToken") is None, ticket
    rebooked = call(CUSTOMER_B, "POST", "/booking/hold", seat_request,
                    key=idem_key("available-after-refund"), expected=201)
    assert rebooked["maDon"] != fixtures["zero_order"], rebooked


def cleanup():
    users = ",".join(t16.sql_literal(client.user_id) for client in CLIENTS if client.user_id)
    if users:
        db("DELETE FROM refund_request WHERE ma_don IN (SELECT ma_don FROM don_dat_ve "
           "WHERE ma_nguoi_dung IN (" + users + "))")
        if OWNED_PAYMENTS:
            payments = ",".join(t16.sql_literal(payment) for payment in OWNED_PAYMENTS)
            db("DELETE FROM payment_gateway_metadata WHERE ma_giao_dich IN (" + payments + ")")
            db("DELETE FROM thanh_toan WHERE ma_giao_dich IN (" + payments + ")")
        t16.cleanup_owned_data()


def main():
    error = None
    try:
        if scenario("local backend, T19 accounts, refund migrations and 24h demo policy",
                    prepare_stack_and_policy):
            fixture_result = {}

            def seed():
                fixture_result.update(seed_refund_fixtures())

            if scenario("scoped paid-ticket and zero-payment fixtures", seed):
                fixtures = fixture_result
                scenario("authenticated, owner-scoped booking history GET contract",
                         lambda: my_order_history(fixtures))
                scenario("admin policy HTTP contract, CSRF and referenced-policy immutability", policy_admin_contract)
                if scenario("auth, CSRF and preview around the 24-hour threshold",
                            lambda: auth_csrf_and_policy_preview(fixtures)):
                    scenario("zero-value single and batch refunds persist without provider calls",
                             lambda: refunds_are_local_and_persisted(fixtures))
    except Exception as error_value:
        error = error_value
    finally:
        try:
            cleanup()
        except Exception as cleanup_error:
            print("FAIL: cleanup T19-owned fixtures — " + repr(cleanup_error))
            error = error or cleanup_error
        for client in reversed(CLIENTS):
            try:
                client.close()
            except Exception as close_error:
                print("FAIL: close T19 fixture account — " + repr(close_error))
                error = error or close_error
    passed = sum(result == "PASS" for result, _ in RESULTS)
    failed = sum(result == "FAIL" for result, _ in RESULTS)
    skipped = sum(result == "SKIP" for result, _ in RESULTS)
    print("RESULT: %d PASS, %d SKIP, %d FAIL, %d HTTP assertions" %
          (passed, skipped, failed, HTTP_ASSERTIONS))
    return 1 if error or failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
