"""T17 real HTTP/PostgreSQL smoke; it never starts Docker or deploys the WAR.

Run from the repository root after the T17 backend is deployed:
    DB_NAME=datvexe python tests/backend/booking_pricing_smoke.py

The script reuses the T16 disposable account and transport fixture helpers,
but overrides their prefixes so that only resources created by this run are
removed. It exercises the real backend at :8080 and PostgreSQL at :5432.
"""
import concurrent.futures
import json
import os
import threading
import uuid
from decimal import Decimal

os.environ.setdefault("DB_NAME", "datvexe")

import booking_order_smoke as t16


# Reuse the T16 fixture functions with a T17-specific namespace.
t16.PREFIX = "T17_" + uuid.uuid4().hex[:10] + "_"
t16.IDEMPOTENCY_PREFIX = "T17-" + uuid.uuid4().hex[:10] + "-"
BASE = t16.BASE
CLIENTS = t16.CLIENTS
CUSTOMER_A = t16.CUSTOMER_A
CUSTOMER_B = t16.CUSTOMER_B
CUSTOMER_C = t16.CUSTOMER_C
HTTP_ASSERTIONS = 0
STATUS_COUNTS = {}
RESULTS = []
COUNTS_LOCK = threading.Lock()
EXTRA_PROMOTIONS = []
PAYMENT_IDS = []
PAYMENT_METHOD_ID = None


def ident(suffix):
    return t16.ident(suffix)


def idem_key(suffix):
    return t16.idem_key(suffix)


def order_path(order_id):
    return "/booking/" + order_id


def exchange(actor, method, path, body=None, *, key=None, csrf=True, anonymous=False):
    """Delegate the common HTTP/error-shape assertions to the T16 helper."""
    global HTTP_ASSERTIONS
    status, result, headers = t16.exchange(actor, method, path, body, key=key,
                                           csrf=csrf, anonymous=anonymous)
    with COUNTS_LOCK:
        HTTP_ASSERTIONS += 1
        STATUS_COUNTS[status] = STATUS_COUNTS.get(status, 0) + 1
    return status, result, headers


def call(actor, method, path, body=None, expected=200, *, key=None):
    status, result, _ = exchange(actor, method, path, body, key=key)
    allowed = expected if isinstance(expected, tuple) else (expected,)
    assert status in allowed, (method, path, allowed, status, result)
    if isinstance(result, dict) and isinstance(result.get("maDon"), str):
        t16.ORDER_IDS.add(result["maDon"])
    return result


def passed(label):
    RESULTS.append(("PASS", label))
    print("PASS: " + label)


def scenario(label, action):
    try:
        action()
        passed(label)
    except Exception as error:
        RESULTS.append(("FAIL", label))
        print("FAIL: " + label + " — " + repr(error))
        raise


def db(statement):
    t16.db(statement)


def db_assert(condition, message):
    t16.db_assert(condition, message)


def hold(actor, trip, seats):
    result = call(actor, "POST", "/booking/hold",
                  {"maChuyen": trip, "danhSachMaGhe": seats}, expected=201,
                  key=idem_key("hold-" + uuid.uuid4().hex))
    assert result["trangThaiDon"] == "CHO_XU_LY", result
    assert result["danhSachGheGiu"] == seats, result
    assert result["danhSachGiuCho"], result
    return result


def passengers(actor, order, seats, label="Passenger"):
    return call(actor, "PUT", order_path(order) + "/hanh-khach", {
        "hanhKhach": [
            {"maGhe": seat, "hoTen": f"{label} {index + 1}",
             "soDienThoai": "090" + str(70000000 + index)}
            for index, seat in enumerate(seats)
        ]
    })


def preview(actor, order, promotion=None, expected=200):
    body = {} if promotion is None else {"maKhuyenMai": promotion}
    return call(actor, "POST", order_path(order) + "/bao-gia", body, expected=expected)


def freeze(actor, order, quote_id, expected=200, key=None):
    status, result, _ = exchange(actor, "POST", order_path(order) + "/chot-gia",
                                 {"maBaoGia": quote_id}, key=key or idem_key("freeze-" + uuid.uuid4().hex))
    allowed = expected if isinstance(expected, tuple) else (expected,)
    assert status in allowed, ("POST", order_path(order) + "/chot-gia", allowed, status, result)
    if isinstance(result, dict) and isinstance(result.get("maDon"), str):
        t16.ORDER_IDS.add(result["maDon"])
    return status, result


def assert_money_lines(response, expected_total=None):
    assert response["donViTienTe"] == "VND", response
    for field in ("tongTienGoc", "tongTienGiam", "tongThanhToan"):
        assert isinstance(response[field], str) and response[field].isdigit(), response
    lines = response["chiTietGia"]
    assert lines, response
    assert sum(Decimal(line["giaGoc"]) for line in lines) == Decimal(response["tongTienGoc"]), response
    assert sum(Decimal(line["tienGiam"]) for line in lines) == Decimal(response["tongTienGiam"]), response
    assert all(Decimal(line["thanhTien"]) == Decimal(line["giaGoc"]) - Decimal(line["tienGiam"])
               for line in lines), response
    assert sum(Decimal(line["thanhTien"]) for line in lines) == Decimal(response["tongThanhToan"]), response
    assert Decimal(response["tongTienGoc"]) - Decimal(response["tongTienGiam"]) == Decimal(response["tongThanhToan"]), response
    if expected_total is not None:
        assert Decimal(response["tongThanhToan"]) == Decimal(expected_total), response


def seed_extra_promotions():
    global PAYMENT_METHOD_ID
    cap = ident("CAP_PROMO")
    zero = ident("ZERO_PROMO")
    race = ident("RACE_PROMO")
    not_yet = ident("NOT_YET_PROMO")
    expired = ident("EXPIRED_PROMO")
    quota0 = ident("QUOTA0_PROMO")
    minimum = ident("MINIMUM_PROMO")
    EXTRA_PROMOTIONS.extend((cap, zero, race, not_yet, expired, quota0, minimum))
    values = [
        (cap, "T17 capped percent", "PHAN_TRAM", "50", "100001", "0", "9"),
        (zero, "T17 exact zero", "SO_TIEN_CO_DINH", "300003", "", "0", "9"),
        (race, "T17 last quota", "SO_TIEN_CO_DINH", "1", "", "0", "1"),
        (not_yet, "T17 not yet", "PHAN_TRAM", "10", "", "0", "9"),
        (expired, "T17 expired", "PHAN_TRAM", "10", "", "0", "9"),
        (quota0, "T17 quota zero", "PHAN_TRAM", "10", "", "0", "0"),
        (minimum, "T17 minimum", "PHAN_TRAM", "10", "", "999999999", "9"),
    ]
    for code, name, kind, value, cap_value, minimum, quantity in values:
        max_sql = "NULL" if not cap_value else cap_value
        if code == not_yet:
            start_sql, end_sql = "LOCALTIMESTAMP+INTERVAL '1 day'", "LOCALTIMESTAMP+INTERVAL '5 days'"
        elif code == expired:
            start_sql, end_sql = "LOCALTIMESTAMP-INTERVAL '5 days'", "LOCALTIMESTAMP-INTERVAL '1 day'"
        else:
            start_sql, end_sql = "LOCALTIMESTAMP-INTERVAL '1 day'", "LOCALTIMESTAMP+INTERVAL '5 days'"
        db("INSERT INTO khuyen_mai(ma_khuyen_mai,ten_khuyen_mai,loai_giam,gia_tri_giam,"
           "gia_tri_toi_da,don_toi_thieu,so_luong,so_luong_da_dung,ngay_bat_dau,"
           "ngay_ket_thuc,trang_thai) VALUES (" + t16.sql_literal(code) + ","
           + t16.sql_literal(name) + "," + t16.sql_literal(kind) + "," + value + ","
           + max_sql + "," + minimum + "," + quantity + ",0," + start_sql + ","
           + end_sql + ",'HOAT_DONG')")
    PAYMENT_METHOD_ID = ident("PAYMENT_METHOD")
    db("INSERT INTO phuong_thuc_thanh_toan(ma_phuong_thuc,ten_phuong_thuc,mo_ta,trang_thai) VALUES ("
       + ",".join((t16.sql_literal(PAYMENT_METHOD_ID), t16.sql_literal("T17 payment"),
                   t16.sql_literal("Disposable T17 fixture"), "'HOAT_DONG'")) + ")")


def quote_without_code_and_exact_allocation():
    order = hold(CUSTOMER_A, t16.TRIP_IDS[0], [t16.SEAT_IDS[0], t16.SEAT_IDS[1]])["maDon"]
    passengers(CUSTOMER_A, order, [t16.SEAT_IDS[0], t16.SEAT_IDS[1]])
    quote = preview(CUSTOMER_A, order)
    assert quote["maBaoGia"] and quote["maDon"] == order, quote
    assert quote["ketQuaKhuyenMai"] is None, quote
    assert_money_lines(quote, "200002")
    assert quote["tongTienGiam"] == "0" and quote["tongThanhToan"] == "200002", quote
    assert set(quote) == {"maBaoGia", "maDon", "donViTienTe", "thoiDiemBaoGia", "thoiDiemHetHan",
                          "tongTienGoc", "tongTienGiam", "tongThanhToan", "chiTietGia", "ketQuaKhuyenMai"}, quote
    call(CUSTOMER_A, "POST", order_path(order) + "/huy", {}, key=idem_key("cancel-quote"))


def invalid_promotions_auth_and_idempotency_guards():
    order = hold(CUSTOMER_A, t16.TRIP_IDS[0], [t16.SEAT_IDS[0], t16.SEAT_IDS[1]])["maDon"]
    status, incomplete, _ = exchange(CUSTOMER_A, "POST", order_path(order) + "/bao-gia", {})
    assert status == 400 and incomplete["code"] == "VALIDATION_ERROR", incomplete
    passengers(CUSTOMER_A, order, [t16.SEAT_IDS[0], t16.SEAT_IDS[1]], "Guard")

    for code in (ident("DOES_NOT_EXIST"), ident("NOT_YET_PROMO"), ident("EXPIRED_PROMO"),
                 ident("QUOTA0_PROMO"), ident("MINIMUM_PROMO")):
        status, invalid, _ = exchange(CUSTOMER_A, "POST", order_path(order) + "/bao-gia",
                                   {"maKhuyenMai": code})
        expected_code = "PROMOTION_EXHAUSTED" if code == ident("QUOTA0_PROMO") else "PROMOTION_NOT_APPLICABLE"
        assert status == 409 and invalid["code"] == expected_code, (code, invalid)
    status, invalid_type, _ = exchange(CUSTOMER_A, "POST", order_path(order) + "/bao-gia",
                                    {"maKhuyenMai": 10})
    assert status == 400 and invalid_type["code"] == "VALIDATION_ERROR", invalid_type
    status, money_field, _ = exchange(CUSTOMER_A, "POST", order_path(order) + "/bao-gia",
                                   {"maKhuyenMai": None, "tongTienGoc": "0"})
    assert status == 400 and money_field["code"] == "VALIDATION_ERROR", money_field

    status, anonymous, _ = exchange(None, "POST", order_path(order) + "/bao-gia", {}, anonymous=True)
    assert status == 401 and anonymous["code"] == "UNAUTHENTICATED", anonymous
    status, no_csrf, _ = exchange(CUSTOMER_A, "POST", order_path(order) + "/bao-gia", {}, csrf=False)
    assert status == 403 and no_csrf["code"] == "FORBIDDEN", no_csrf
    status, foreign, _ = exchange(CUSTOMER_B, "GET", order_path(order))
    assert status == 403 and foreign["code"] == "FORBIDDEN", foreign
    status, foreign_quote, _ = exchange(CUSTOMER_B, "POST", order_path(order) + "/bao-gia", {})
    assert status == 403 and foreign_quote["code"] == "FORBIDDEN", foreign_quote

    quote = preview(CUSTOMER_A, order)
    status, missing_key, _ = exchange(CUSTOMER_A, "POST", order_path(order) + "/chot-gia",
                                    {"maBaoGia": quote["maBaoGia"]})
    assert status == 400 and missing_key["code"] == "VALIDATION_ERROR", missing_key
    key = idem_key("body-reuse")
    _, frozen = freeze(CUSTOMER_A, order, quote["maBaoGia"], key=key)
    status, reused, _ = exchange(CUSTOMER_A, "POST", order_path(order) + "/chot-gia",
                              {"maBaoGia": ident("DIFFERENT_QUOTE")}, key=key)
    assert status == 409 and reused["code"] == "IDEMPOTENCY_KEY_REUSED", reused
    call(CUSTOMER_A, "POST", order_path(order) + "/huy", {}, key=idem_key("cancel-guards"))

    replaced = hold(CUSTOMER_A, t16.TRIP_IDS[1], [t16.SEAT_IDS[0]])["maDon"]
    passengers(CUSTOMER_A, replaced, [t16.SEAT_IDS[0]], "Replaced")
    first = preview(CUSTOMER_A, replaced)
    second = preview(CUSTOMER_A, replaced)
    assert first["maBaoGia"] != second["maBaoGia"], (first, second)
    status, stale = freeze(CUSTOMER_A, replaced, first["maBaoGia"], expected=409)
    assert status == 409 and stale["code"] in {"QUOTE_EXPIRED", "PRICE_CHANGED"}, stale
    call(CUSTOMER_A, "POST", order_path(replaced) + "/huy", {}, key=idem_key("cancel-replaced"))


def cap_zero_and_largest_remainder():
    cap_order = hold(CUSTOMER_A, t16.TRIP_IDS[1], [t16.SEAT_IDS[0], t16.SEAT_IDS[1], t16.SEAT_IDS[2]])["maDon"]
    passengers(CUSTOMER_A, cap_order, [t16.SEAT_IDS[0], t16.SEAT_IDS[1], t16.SEAT_IDS[2]], "Cap")
    capped = preview(CUSTOMER_A, cap_order, ident("CAP_PROMO"))
    assert capped["tongTienGoc"] == "300003" and capped["tongTienGiam"] == "100001", capped
    assert capped["tongThanhToan"] == "200002", capped
    assert sum(int(line["tienGiam"]) for line in capped["chiTietGia"]) == 100001, capped
    assert [line["tienGiam"] for line in capped["chiTietGia"]] == ["33334", "33334", "33333"], capped
    assert_money_lines(capped)
    call(CUSTOMER_A, "POST", order_path(cap_order) + "/huy", {}, key=idem_key("cancel-cap"))

    zero_order = hold(CUSTOMER_B, t16.TRIP_IDS[1], [t16.SEAT_IDS[3], t16.SEAT_IDS[4], t16.SEAT_IDS[5]])["maDon"]
    passengers(CUSTOMER_B, zero_order, [t16.SEAT_IDS[3], t16.SEAT_IDS[4], t16.SEAT_IDS[5]], "Zero")
    zero = preview(CUSTOMER_B, zero_order, ident("ZERO_PROMO"))
    assert zero["tongTienGoc"] == "300003" and zero["tongTienGiam"] == "300003", zero
    assert zero["tongThanhToan"] == "0" and all(line["thanhTien"] == "0" for line in zero["chiTietGia"]), zero
    _, frozen_zero = freeze(CUSTOMER_B, zero_order, zero["maBaoGia"])
    assert frozen_zero["trangThaiDon"] == "CHO_THANH_TOAN" and frozen_zero["tongThanhToan"] == "0", frozen_zero
    db_assert("NOT EXISTS (SELECT 1 FROM ve_xe WHERE ma_don=" + t16.sql_literal(zero_order) + ")",
              "Zero-value freeze created a ticket before T18")
    db_assert("NOT EXISTS (SELECT 1 FROM thanh_toan WHERE ma_don=" + t16.sql_literal(zero_order) + ")",
              "Zero-value freeze created a payment before T18")
    call(CUSTOMER_B, "POST", order_path(zero_order) + "/huy", {}, key=idem_key("cancel-zero"))


def freeze_replay_get_and_thaw_after_edit():
    order = hold(CUSTOMER_A, t16.TRIP_IDS[2], [t16.SEAT_IDS[0], t16.SEAT_IDS[1]])["maDon"]
    passengers(CUSTOMER_A, order, [t16.SEAT_IDS[0], t16.SEAT_IDS[1]], "Freeze")
    quote = preview(CUSTOMER_A, order, ident("PROMO"))
    key = idem_key("freeze-replay")
    first_status, frozen = freeze(CUSTOMER_A, order, quote["maBaoGia"], key=key)
    assert first_status == 200
    assert frozen["trangThaiDon"] == "CHO_THANH_TOAN" and "thoiDiemBaoGia" not in frozen, frozen
    assert "phienBanGia" not in frozen and set(frozen) == {"maBaoGia", "maDon", "trangThaiDon", "donViTienTe",
                                                             "thoiDiemHetHan", "tongTienGoc", "tongTienGiam",
                                                             "tongThanhToan", "chiTietGia", "ketQuaKhuyenMai"}, frozen
    assert_money_lines(frozen)
    replay_status, replay = freeze(CUSTOMER_A, order, quote["maBaoGia"], key=key)
    assert replay_status == 200 and replay == frozen, (replay, frozen)
    current = call(CUSTOMER_A, "GET", order_path(order))
    assert current["trangThaiDon"] == "CHO_THANH_TOAN", current
    assert set(current["giaDaChot"]) == {"donViTienTe", "tongTienGoc", "tongTienGiam", "tongThanhToan", "chiTietGia"}, current
    blocked = hold(CUSTOMER_B, t16.TRIP_IDS[2], [t16.SEAT_IDS[2]])["maDon"]
    status, failed_swap, _ = exchange(CUSTOMER_A, "PUT", order_path(order) + "/doi-ghe",
                                   {"maGheCu": t16.SEAT_IDS[0], "maGheMoi": t16.SEAT_IDS[2]})
    assert status == 409 and failed_swap["code"] in {"SEAT_UNAVAILABLE", "INVALID_STATE_TRANSITION"}, failed_swap
    after_failed_swap = call(CUSTOMER_A, "GET", order_path(order))
    assert after_failed_swap["giaDaChot"] == current["giaDaChot"], after_failed_swap
    assert {item["maGhe"] for item in after_failed_swap["danhSachGiuCho"]} == {t16.SEAT_IDS[0], t16.SEAT_IDS[1]}, after_failed_swap
    call(CUSTOMER_B, "POST", order_path(blocked) + "/huy", {}, key=idem_key("cancel-blocked-swap"))
    frozen_snapshot = current["giaDaChot"]
    db("UPDATE tuyen_xe SET gia_co_ban=999999 WHERE ma_tuyen=" + t16.sql_literal(ident("ROUTE")))
    db("UPDATE khuyen_mai SET gia_tri_giam=99 WHERE ma_khuyen_mai=" + t16.sql_literal(ident("PROMO")))
    try:
        unchanged = call(CUSTOMER_A, "GET", order_path(order))
        assert unchanged["giaDaChot"] == frozen_snapshot, (unchanged, frozen_snapshot)
        assert unchanged["trangThaiDon"] == "CHO_THANH_TOAN", unchanged
    finally:
        db("UPDATE tuyen_xe SET gia_co_ban=100001 WHERE ma_tuyen=" + t16.sql_literal(ident("ROUTE")))
        db("UPDATE khuyen_mai SET gia_tri_giam=12.50 WHERE ma_khuyen_mai=" + t16.sql_literal(ident("PROMO")))
    # A successful T16 edit thaws the quote and requires C01/C02 again.
    edited = call(CUSTOMER_A, "PATCH", order_path(order), {"ghiChu": "after freeze"},
                  key=idem_key("thaw-edit"))
    assert edited["trangThaiDon"] == "CHO_XU_LY" and edited["giaDaChot"] is None, edited
    call(CUSTOMER_A, "POST", order_path(order) + "/huy", {}, key=idem_key("cancel-thaw"))


def promotion_last_quota_race_on_different_trips():
    first = hold(CUSTOMER_A, t16.TRIP_IDS[0], [t16.SEAT_IDS[2]])["maDon"]
    second = hold(CUSTOMER_B, t16.TRIP_IDS[1], [t16.SEAT_IDS[2]])["maDon"]
    passengers(CUSTOMER_A, first, [t16.SEAT_IDS[2]], "Race A")
    passengers(CUSTOMER_B, second, [t16.SEAT_IDS[2]], "Race B")
    promo = ident("RACE_PROMO")
    quote_a = preview(CUSTOMER_A, first, promo)
    quote_b = preview(CUSTOMER_B, second, promo)
    barrier = threading.Barrier(2)

    def freeze_race(actor, order, quote_id):
        barrier.wait(timeout=10)
        return freeze(actor, order, quote_id, expected=(200, 409), key=idem_key("race-" + actor.user_id))

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda args: freeze_race(*args),
                                ((CUSTOMER_A, first, quote_a["maBaoGia"]),
                                 (CUSTOMER_B, second, quote_b["maBaoGia"]))))
    statuses = [status for status, _ in results]
    assert statuses.count(200) == 1 and statuses.count(409) == 1, results
    loser = next(result for status, result in results if status == 409)
    assert loser["code"] == "PROMOTION_EXHAUSTED", results
    db_assert("(SELECT so_luong_da_dung FROM khuyen_mai WHERE ma_khuyen_mai="
              + t16.sql_literal(promo) + ") = 0", "T17 freeze consumed promotion quota too early")
    for client, order in ((CUSTOMER_A, first), (CUSTOMER_B, second)):
        call(client, "POST", order_path(order) + "/huy", {}, key=idem_key("race-cancel-" + order))


def stale_freeze_replay_after_thaw_requires_new_quote():
    order = hold(CUSTOMER_B, t16.TRIP_IDS[0], [t16.SEAT_IDS[3]])["maDon"]
    passengers(CUSTOMER_B, order, [t16.SEAT_IDS[3]], "Replay")
    old_quote = preview(CUSTOMER_B, order)
    old_key = idem_key("stale-freeze")
    _, old_frozen = freeze(CUSTOMER_B, order, old_quote["maBaoGia"], key=old_key)
    thawed = call(CUSTOMER_B, "PATCH", order_path(order), {"ghiChu": "thaw before replay"},
                  key=idem_key("thaw-replay"))
    assert thawed["trangThaiDon"] == "CHO_XU_LY" and thawed["giaDaChot"] is None, thawed
    replay_status, replay = freeze(CUSTOMER_B, order, old_quote["maBaoGia"], key=old_key)
    assert replay_status == 200 and replay == old_frozen, (replay, old_frozen)
    current = call(CUSTOMER_B, "GET", order_path(order))
    assert current["trangThaiDon"] == "CHO_XU_LY" and current["giaDaChot"] is None, current
    new_quote = preview(CUSTOMER_B, order)
    assert new_quote["maBaoGia"] != old_quote["maBaoGia"], (old_quote, new_quote)
    _, new_frozen = freeze(CUSTOMER_B, order, new_quote["maBaoGia"], key=idem_key("new-freeze"))
    assert new_frozen["trangThaiDon"] == "CHO_THANH_TOAN", new_frozen
    call(CUSTOMER_B, "POST", order_path(order) + "/huy", {}, key=idem_key("cancel-replay"))


def frozen_get_survives_session_loss_and_reentry_thaws():
    order = hold(CUSTOMER_C, t16.TRIP_IDS[1], [t16.SEAT_IDS[0]])["maDon"]
    passenger_name = "Session frozen passenger"
    call(CUSTOMER_C, "PUT", order_path(order) + "/hanh-khach", {
        "hanhKhach": [{"maGhe": t16.SEAT_IDS[0], "hoTen": passenger_name, "soDienThoai": "0907123456"}]
    })
    quote = preview(CUSTOMER_C, order)
    _, frozen = freeze(CUSTOMER_C, order, quote["maBaoGia"])
    fresh = t16.SessionView(CUSTOMER_C)
    lost = call(fresh, "GET", order_path(order))
    assert lost["hanhKhach"] == [] and lost["coTheChotGia"] is False, lost
    assert lost["giaDaChot"] is not None and passenger_name not in json.dumps(lost, ensure_ascii=False), lost
    assert lost["giaDaChot"]["tongThanhToan"] == frozen["tongThanhToan"], lost
    reentered = call(fresh, "PUT", order_path(order) + "/hanh-khach", {
        "hanhKhach": [{"maGhe": t16.SEAT_IDS[0], "hoTen": passenger_name, "soDienThoai": "0907123456"}]
    })
    assert reentered["trangThaiDon"] == "CHO_XU_LY" and reentered["giaDaChot"] is None, reentered
    call(fresh, "POST", order_path(order) + "/huy", {}, key=idem_key("cancel-reentry"))


def expired_frozen_coupon_releases_before_explicit_get():
    promo = ident("RACE_PROMO")
    first = hold(CUSTOMER_A, t16.TRIP_IDS[0], [t16.SEAT_IDS[5]])["maDon"]
    passengers(CUSTOMER_A, first, [t16.SEAT_IDS[5]], "Expired coupon")
    first_quote = preview(CUSTOMER_A, first, promo)
    _, first_frozen = freeze(CUSTOMER_A, first, first_quote["maBaoGia"])
    first_hold = call(CUSTOMER_A, "GET", order_path(first))["danhSachGiuCho"][0]["maGiuCho"]
    second = hold(CUSTOMER_B, t16.TRIP_IDS[1], [t16.SEAT_IDS[5]])["maDon"]
    passengers(CUSTOMER_B, second, [t16.SEAT_IDS[5]], "Replacement coupon")
    db("UPDATE giu_cho SET ngay_dat=LOCALTIMESTAMP-INTERVAL '15 minutes 1 second', "
       "thoi_diem_bat_dau=LOCALTIMESTAMP-INTERVAL '15 minutes 1 second', "
       "thoi_diem_het_han=LOCALTIMESTAMP-INTERVAL '1 second' WHERE ma_giu_cho=" + t16.sql_literal(first_hold))
    # The second C02 can reclaim the logical quota before anyone GETs first.
    second_quote = preview(CUSTOMER_B, second, promo)
    _, second_frozen = freeze(CUSTOMER_B, second, second_quote["maBaoGia"])
    assert second_frozen["trangThaiDon"] == "CHO_THANH_TOAN", second_frozen
    db_assert("(SELECT count(*) FROM don_dat_ve WHERE ma_khuyen_mai=" + t16.sql_literal(promo)
              + " AND trang_thai='CHO_THANH_TOAN' AND ma_don <> " + t16.sql_literal(first)
              + " AND ma_don <> " + t16.sql_literal(second) + ") = 0",
              "Expired frozen order incorrectly retained a promotion hold")
    status, expired, _ = exchange(CUSTOMER_A, "GET", order_path(first))
    assert status == 409 and expired["code"] == "HOLD_EXPIRED", expired
    db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + t16.sql_literal(first)
              + " AND trang_thai='HET_HAN' AND tong_tien=" + first_frozen["tongTienGoc"]
              + " AND tien_giam=" + first_frozen["tongTienGiam"]
              + " AND ma_khuyen_mai=" + t16.sql_literal(promo) + ")",
              "Frozen expiry did not preserve gross, discount and promotion code")
    call(CUSTOMER_B, "POST", order_path(second) + "/huy", {}, key=idem_key("cancel-replacement"))


def changed_quote_session_loss_and_expiry():
    order = hold(CUSTOMER_C, t16.TRIP_IDS[2], [t16.SEAT_IDS[2]])["maDon"]
    passengers(CUSTOMER_C, order, [t16.SEAT_IDS[2]], "Stale")
    quote = preview(CUSTOMER_C, order)
    db("UPDATE tuyen_xe SET gia_co_ban=100002 WHERE ma_tuyen=" + t16.sql_literal(ident("ROUTE")))
    status, stale = freeze(CUSTOMER_C, order, quote["maBaoGia"], expected=409)
    assert status == 409 and stale["code"] == "PRICE_CHANGED", stale
    db("UPDATE tuyen_xe SET gia_co_ban=100001 WHERE ma_tuyen=" + t16.sql_literal(ident("ROUTE")))
    call(CUSTOMER_C, "POST", order_path(order) + "/huy", {}, key=idem_key("cancel-stale"))

    session_order = hold(CUSTOMER_C, t16.TRIP_IDS[2], [t16.SEAT_IDS[3]])["maDon"]
    passengers(CUSTOMER_C, session_order, [t16.SEAT_IDS[3]], "Session")
    session_quote = preview(CUSTOMER_C, session_order)
    fresh_session = t16.SessionView(CUSTOMER_C)
    status, missing, _ = exchange(fresh_session, "POST", order_path(session_order) + "/chot-gia",
                               {"maBaoGia": session_quote["maBaoGia"]}, key=idem_key("lost-session"))
    assert status == 409 and missing["code"] == "QUOTE_EXPIRED", missing
    call(CUSTOMER_C, "POST", order_path(session_order) + "/huy", {}, key=idem_key("cancel-session"))

    expiry_order = hold(CUSTOMER_C, t16.TRIP_IDS[2], [t16.SEAT_IDS[4]])["maDon"]
    passengers(CUSTOMER_C, expiry_order, [t16.SEAT_IDS[4]], "Expiry")
    expiry_quote = preview(CUSTOMER_C, expiry_order)
    hold_id = call(CUSTOMER_C, "GET", order_path(expiry_order))["danhSachGiuCho"][0]["maGiuCho"]
    db("UPDATE giu_cho SET ngay_dat=LOCALTIMESTAMP-INTERVAL '15 minutes 1 second', "
       "thoi_diem_bat_dau=LOCALTIMESTAMP-INTERVAL '15 minutes 1 second', "
       "thoi_diem_het_han=LOCALTIMESTAMP-INTERVAL '1 second' WHERE ma_giu_cho=" + t16.sql_literal(hold_id))
    status, expired = freeze(CUSTOMER_C, expiry_order, expiry_quote["maBaoGia"], expected=409)
    assert status == 409 and expired["code"] == "HOLD_EXPIRED", expired
    db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + t16.sql_literal(expiry_order)
              + " AND trang_thai='HET_HAN')", "T17 did not commit order expiry before returning error")


def payment_in_progress_blocks_price_changes_and_closed_snapshot():
    order = hold(CUSTOMER_A, t16.TRIP_IDS[0], [t16.SEAT_IDS[4]])["maDon"]
    passengers(CUSTOMER_A, order, [t16.SEAT_IDS[4]], "Payment")
    quote = preview(CUSTOMER_A, order)
    _, frozen = freeze(CUSTOMER_A, order, quote["maBaoGia"])
    payment_id = ident("PENDING_PAYMENT")
    PAYMENT_IDS.append(payment_id)
    db("INSERT INTO thanh_toan(ma_giao_dich,ma_don,ma_phuong_thuc,so_tien,trang_thai) VALUES ("
       + ",".join((t16.sql_literal(payment_id), t16.sql_literal(order), t16.sql_literal(PAYMENT_METHOD_ID),
                   frozen["tongThanhToan"], "'CHO_XU_LY'")) + ")")
    rejected = call(CUSTOMER_A, "PATCH", order_path(order), {"ghiChu": "blocked"}, expected=409,
                    key=idem_key("pending-edit"))
    assert rejected["code"] in {"PAYMENT_PROCESSING", "INVALID_STATE_TRANSITION"}, rejected
    db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + t16.sql_literal(order)
              + " AND trang_thai='CHO_THANH_TOAN')", "Pending payment edit changed frozen order")
    db("DELETE FROM thanh_toan WHERE ma_giao_dich=" + t16.sql_literal(payment_id))
    call(CUSTOMER_A, "POST", order_path(order) + "/huy", {}, key=idem_key("cancel-pending"))


def issued_tickets_and_partial_refund_keep_frozen_totals():
    order = hold(CUSTOMER_A, t16.TRIP_IDS[2], [t16.SEAT_IDS[0], t16.SEAT_IDS[1], t16.SEAT_IDS[2]])["maDon"]
    passengers(CUSTOMER_A, order, [t16.SEAT_IDS[0], t16.SEAT_IDS[1], t16.SEAT_IDS[2]], "Issued")
    quote = preview(CUSTOMER_A, order, ident("CAP_PROMO"))
    _, frozen = freeze(CUSTOMER_A, order, quote["maBaoGia"])
    ticket_ids = [ident("TICKET_" + str(index)) for index in range(len(frozen["chiTietGia"]))]
    PAYMENT_IDS.append(ident("SUCCESS_PAYMENT"))
    db("UPDATE don_dat_ve SET trang_thai='DA_THANH_TOAN' WHERE ma_don=" + t16.sql_literal(order))
    db("UPDATE giu_cho SET trang_thai='DA_XAC_NHAN' WHERE ma_don=" + t16.sql_literal(order))
    for ticket_id, line, seat in zip(ticket_ids, frozen["chiTietGia"],
                                     [t16.SEAT_IDS[0], t16.SEAT_IDS[1], t16.SEAT_IDS[2]]):
        db("INSERT INTO ve_xe(ma_ve,ma_chuyen,ma_ghe,ma_don,ho_ten_khach_hang,"
           "so_dien_thoai_khach_hang,gia_ve,ngay_xuat_ve,trang_thai) VALUES ("
           + ",".join((t16.sql_literal(ticket_id), t16.sql_literal(t16.TRIP_IDS[2]),
                       t16.sql_literal(seat), t16.sql_literal(order),
                       t16.sql_literal("Issued " + seat), t16.sql_literal("0907999000"),
                       line["thanhTien"], "LOCALTIMESTAMP", "'DA_XUAT_VE'")) + ")")
    db("INSERT INTO thanh_toan(ma_giao_dich,ma_don,ma_phuong_thuc,so_tien,trang_thai) VALUES ("
       + ",".join((t16.sql_literal(PAYMENT_IDS[-1]), t16.sql_literal(order),
                   t16.sql_literal(PAYMENT_METHOD_ID), frozen["tongThanhToan"], "'THANH_CONG'")) + ")")
    issued = call(CUSTOMER_A, "GET", order_path(order))
    assert issued["trangThaiDon"] == "DA_THANH_TOAN" and len(issued["danhSachVe"]) == 3, issued
    assert issued["giaDaChot"] is not None, issued
    assert issued["giaDaChot"]["tongTienGoc"] == frozen["tongTienGoc"], issued
    assert issued["giaDaChot"]["tongTienGiam"] == frozen["tongTienGiam"], issued
    assert sum(Decimal(item["thanhTien"]) for item in issued["giaDaChot"]["chiTietGia"]) == Decimal(frozen["tongThanhToan"]), issued
    db("UPDATE ve_xe SET trang_thai='DA_HOAN' WHERE ma_ve=" + t16.sql_literal(ticket_ids[0]))
    refunded = call(CUSTOMER_A, "GET", order_path(order))
    assert refunded["trangThaiDon"] == "DA_THANH_TOAN" and refunded["giaDaChot"] is not None, refunded
    assert refunded["giaDaChot"]["tongTienGoc"] == frozen["tongTienGoc"], refunded
    assert refunded["giaDaChot"]["tongTienGiam"] == frozen["tongTienGiam"], refunded
    assert any(ticket["maVe"] == ticket_ids[0] and ticket["trangThai"] == "DA_HOAN"
               for ticket in refunded["danhSachVe"]), refunded
    db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + t16.sql_literal(order)
              + " AND tong_tien=" + frozen["tongTienGoc"] + " AND tien_giam=" + frozen["tongTienGiam"] + ")",
              "Partial refund changed frozen gross or discount totals")


def cleanup():
    for payment_id in PAYMENT_IDS:
        db("DELETE FROM thanh_toan WHERE ma_giao_dich=" + t16.sql_literal(payment_id))
    t16.cleanup_owned_data()
    if EXTRA_PROMOTIONS:
        codes = ",".join(t16.sql_literal(code) for code in EXTRA_PROMOTIONS)
        db("DELETE FROM khuyen_mai WHERE ma_khuyen_mai IN (" + codes + ")")
    if PAYMENT_METHOD_ID:
        db("DELETE FROM phuong_thuc_thanh_toan WHERE ma_phuong_thuc=" + t16.sql_literal(PAYMENT_METHOD_ID))


def main():
    error = None
    try:
        scenario("health, migrations, isolated KH accounts and T17 fixtures",
                 lambda: (t16.require_stack_migration_and_customers(), t16.seed_owned_trip_fixtures(), seed_extra_promotions()))
        scenario("preview without code returns exact integer VND allocation", quote_without_code_and_exact_allocation)
        scenario("invalid promotion, auth, CSRF, ownership and request guards", invalid_promotions_auth_and_idempotency_guards)
        scenario("promotion cap, zero total and largest-remainder allocation", cap_zero_and_largest_remainder)
        scenario("freeze replay, immutable GET snapshot and successful T16 thaw", freeze_replay_get_and_thaw_after_edit)
        scenario("different-trip concurrent freeze has one winner for last quota", promotion_last_quota_race_on_different_trips)
        scenario("stale freeze replay after thaw requires a new quote and key", stale_freeze_replay_after_thaw_requires_new_quote)
        scenario("frozen GET survives session loss and re-entry thaws order", frozen_get_survives_session_loss_and_reentry_thaws)
        scenario("price fingerprint, lost session and boundary expiry", changed_quote_session_loss_and_expiry)
        scenario("expired frozen coupon releases before explicit GET cleanup", expired_frozen_coupon_releases_before_explicit_get)
        scenario("pending payment blocks edit and preserves frozen order", payment_in_progress_blocks_price_changes_and_closed_snapshot)
        scenario("issued tickets and partial refund keep frozen totals", issued_tickets_and_partial_refund_keep_frozen_totals)
    except Exception as error_value:
        error = error_value
    finally:
        try:
            cleanup()
        except Exception as cleanup_error:
            RESULTS.append(("FAIL", "cleanup owned T17 fixtures"))
            print("FAIL: cleanup owned T17 fixtures — " + repr(cleanup_error))
            error = error or cleanup_error
        for client in reversed(CLIENTS):
            try:
                client.close()
            except Exception as close_error:
                RESULTS.append(("FAIL", "close fixture account"))
                print("FAIL: close fixture account — " + repr(close_error))
                error = error or close_error
    pass_count = sum(result == "PASS" for result, _ in RESULTS)
    fail_count = sum(result == "FAIL" for result, _ in RESULTS)
    statuses = ", ".join("HTTP %s: %s" % item for item in sorted(STATUS_COUNTS.items()))
    print("RESULT: %d PASS, %d FAIL, %d HTTP assertions (%s)" %
          (pass_count, fail_count, HTTP_ASSERTIONS, statuses))
    return 1 if error or fail_count else 0


if __name__ == "__main__":
    raise SystemExit(main())
