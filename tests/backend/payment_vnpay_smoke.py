"""T18 VNPay/MIEN_PHI smoke for the local Docker backend.

Run from the repository root after deploying the T18 backend:
    python tests/backend/payment_vnpay_smoke.py

The script uses the T16 disposable-fixture helpers and exercises the real
backend at :8080 plus PostgreSQL at :5432. It does not claim a real VNPay
settlement: without VNP_TMN_CODE/VNP_HASH_SECRET it runs only local contract,
auth, signed-fixture and free-payment checks. A real sandbox return still needs
valid merchant credentials and a public HTTPS IPN URL.
"""
import hashlib
import hmac
import json
import os
import urllib.parse
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone, timedelta
from decimal import Decimal

os.environ.setdefault("DB_NAME", "datvexe")

import booking_order_smoke as t16


t16.PREFIX = "T18_" + uuid.uuid4().hex[:10] + "_"
t16.IDEMPOTENCY_PREFIX = "T18-" + uuid.uuid4().hex[:10] + "-"
BASE = t16.BASE
CLIENTS = t16.CLIENTS
PAYMENT_IDS = []
RESULTS = []
HTTP_ASSERTIONS = 0
PAYMENT_METHODS_CREATED = []
FREE_PROMOTION = None


class SkipScenario(Exception):
    """A documented local prerequisite is unavailable; no pass is claimed."""


def require_vnpay_fixture(label):
    if not os.environ.get("VNP_TMN_CODE") or not os.environ.get("VNP_HASH_SECRET"):
        raise SkipScenario(label + " requires local VNP_TMN_CODE/VNP_HASH_SECRET")


def ident(suffix):
    return t16.ident(suffix)


def idem_key(suffix):
    return t16.idem_key(suffix)


def payment_path(payment_id):
    return "/thanh-toan/" + payment_id


def order_path(order_id):
    return "/booking/" + order_id


def call(actor, method, path, body=None, expected=200, *, key=None, csrf=True,
         anonymous=False):
    global HTTP_ASSERTIONS
    status, result, headers = t16.exchange(actor, method, path, body, key=key,
                                            csrf=csrf, anonymous=anonymous)
    HTTP_ASSERTIONS += 1
    allowed = expected if isinstance(expected, tuple) else (expected,)
    assert status in allowed, (method, path, allowed, status, result)
    return status, result, headers


def scenario(label, action):
    try:
        action()
        RESULTS.append(("PASS", label))
        print("PASS: " + label)
    except SkipScenario as skip:
        RESULTS.append(("SKIP", label))
        print("SKIP: " + label + " — " + str(skip))
    except Exception as error:
        RESULTS.append(("FAIL", label))
        print("FAIL: " + label + " — " + repr(error))
        raise


def db(statement):
    t16.db(statement)


def seed_payment_methods():
    # Keep existing application methods. ON CONFLICT leaves a team's configured
    # rows untouched; the smoke never deletes these shared codes.
    for code, name, description in (
        ("VNPAY", "VNPay sandbox", "T18 sandbox method"),
        ("MIEN_PHI", "Miễn phí", "T18 zero-payment method"),
    ):
        db("INSERT INTO phuong_thuc_thanh_toan(ma_phuong_thuc,ten_phuong_thuc,mo_ta,trang_thai) "
           "VALUES (" + t16.sql_literal(code) + "," + t16.sql_literal(name) + "," +
           t16.sql_literal(description) + ",'HOAT_DONG') ON CONFLICT (ma_phuong_thuc) DO NOTHING")


def seed_free_promotion():
    global FREE_PROMOTION
    FREE_PROMOTION = ident("FREE_PROMO")
    db("INSERT INTO khuyen_mai(ma_khuyen_mai,ten_khuyen_mai,loai_giam,gia_tri_giam,"
       "don_toi_thieu,so_luong,so_luong_da_dung,ngay_bat_dau,ngay_ket_thuc,trang_thai) VALUES ("
       + ",".join((t16.sql_literal(FREE_PROMOTION), t16.sql_literal("T18 free payment"),
                   "'SO_TIEN_CO_DINH'", "100001", "0", "9", "0",
                   "LOCALTIMESTAMP - INTERVAL '1 day'", "LOCALTIMESTAMP + INTERVAL '5 days'",
                   "'HOAT_DONG'")) + ")")


def prepare_frozen_order(actor, seats, *, promotion=None, trip=None):
    trip = trip or t16.TRIP_IDS[0]
    order = call(actor, "POST", "/booking/hold", {
        "maChuyen": trip, "danhSachMaGhe": seats
    }, expected=201, key=idem_key("hold-" + uuid.uuid4().hex))[1]["maDon"]
    call(actor, "PUT", order_path(order) + "/hanh-khach", {
        "hanhKhach": [
            {"maGhe": seat, "hoTen": "T18 Passenger " + str(index + 1),
             "soDienThoai": "090" + str(81000000 + index)}
            for index, seat in enumerate(seats)
        ]
    })
    preview = call(actor, "POST", order_path(order) + "/bao-gia",
                   {} if promotion is None else {"maKhuyenMai": promotion})[1]
    frozen = call(actor, "POST", order_path(order) + "/chot-gia",
                  {"maBaoGia": preview["maBaoGia"]},
                  key=idem_key("freeze-" + uuid.uuid4().hex))[1]
    assert frozen["trangThaiDon"] == "CHO_THANH_TOAN", frozen
    return order, frozen


def payment_contract_and_method_selection():
    order, frozen = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[0]])
    missing_key = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                       {"maPhuongThuc": "VNPAY"}, expected=400)[1]
    assert missing_key["code"] == "VALIDATION_ERROR", missing_key
    status, invalid, _ = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                              {"maPhuongThuc": "NOT_SUPPORTED"}, expected=409,
                              key=idem_key("unsupported"))
    assert invalid["code"] in {"PAYMENT_METHOD_UNSUPPORTED", "PAYMENT_METHOD_NOT_FOUND"}, invalid

    status, missing_money, _ = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                                    {"maPhuongThuc": "VNPAY", "soTien": "1"}, expected=400,
                                    key=idem_key("frontend-money"))
    assert missing_money["code"] == "VALIDATION_ERROR", missing_money

    return order


def create_vnpay_and_replay(order):
    require_vnpay_fixture("VNPay creation")
    key = idem_key("vnpay-init")
    status, result, _ = t16.exchange(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                                     {"maPhuongThuc": "VNPAY"}, key=key)
    if status == 503 and result.get("code") == "PAYMENT_NOT_CONFIGURED":
        raise SkipScenario("VNPay creation is not configured in the deployed backend")
    assert status == 201, (status, result)
    assert result["maPhuongThuc"] == "VNPAY", result
    payment_id = result["maGiaoDich"]
    PAYMENT_IDS.append(payment_id)
    assert result["soTien"] == "100001", result
    assert result["trangThai"] == "CHO_XU_LY", result
    replay = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                  {"maPhuongThuc": "VNPAY"}, expected=201, key=key)[1]
    assert replay["maGiaoDich"] == payment_id and replay["trangThai"] == "CHO_XU_LY", replay
    return payment_id


def payment_contract_and_pending_rules():
    require_vnpay_fixture("pending-payment rules")
    order, _ = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[0]], trip=t16.TRIP_IDS[1])
    key = idem_key("pending-rules")
    first = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                 {"maPhuongThuc": "VNPAY"}, expected=201, key=key)[1]
    PAYMENT_IDS.append(first["maGiaoDich"])

    reused_for_other_method = call(
        t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
        {"maPhuongThuc": "MIEN_PHI"}, expected=409, key=key)[1]
    assert reused_for_other_method["code"] == "IDEMPOTENCY_KEY_REUSED", reused_for_other_method

    second = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                  {"maPhuongThuc": "VNPAY"}, expected=202,
                  key=idem_key("pending-retry"))[1]
    assert second["maGiaoDich"] == first["maGiaoDich"]
    blocked = call(t16.CUSTOMER_A, "PATCH", order_path(order),
                   {"ghiChu": "cannot edit while payment is pending"}, expected=409,
                   key=idem_key("pending-edit"))[1]
    assert blocked["code"] == "PAYMENT_PROCESSING", blocked


def ownership_csrf_and_query_contract(payment_id):
    status, body, _ = call(None, "GET", payment_path(payment_id), expected=401,
                           anonymous=True)
    assert body["code"] == "UNAUTHENTICATED", body
    status, body, _ = call(t16.CUSTOMER_B, "GET", payment_path(payment_id), expected=403)
    assert body["code"] == "FORBIDDEN", body
    status, body, _ = call(t16.CUSTOMER_A, "POST", payment_path(payment_id) + "/xac-minh",
                           {}, expected=403, csrf=False)
    assert body["code"] == "FORBIDDEN", body
    status, body, _ = call(t16.CUSTOMER_A, "GET", payment_path(payment_id))
    assert body["maGiaoDich"] == payment_id and body["trangThai"] == "CHO_XU_LY", body
    status, history, _ = call(t16.CUSTOMER_A, "GET", order_path(body["maDon"]) +
                              "/thanh-toan?page=1&size=20&sort=maGiaoDich,desc")
    assert history["page"] == 1 and history["size"] == 20, history
    assert any(item["maGiaoDich"] == payment_id for item in history["items"]), history


def signed_vnpay_query(payment_id, amount, response_code="00", transaction_status="00",
                       *, tamper=False):
    merchant = os.environ.get("VNP_TMN_CODE")
    secret = os.environ.get("VNP_HASH_SECRET")
    if not merchant or not secret:
        return None
    paid_at = datetime.now(timezone(timedelta(hours=7))).strftime("%Y%m%d%H%M%S")
    params = {
        "vnp_Amount": str(int(Decimal(amount) * 100)),
        "vnp_BankCode": "NCB",
        "vnp_Command": "pay",
        "vnp_OrderInfo": "Thanh toan don hang " + payment_id,
        "vnp_PayDate": paid_at,
        "vnp_ResponseCode": response_code,
        "vnp_TmnCode": merchant,
        "vnp_TransactionNo": ident("VN" + uuid.uuid4().hex[:10]),
        "vnp_TransactionStatus": transaction_status,
        "vnp_TxnRef": payment_id,
        "vnp_TransactionType": "01",
    }
    canonical = urllib.parse.urlencode(sorted(params.items()))
    digest = hmac.new(secret.encode(), canonical.encode(), hashlib.sha512).hexdigest()
    params["vnp_SecureHash"] = (("1" if digest[0] == "0" else "0") + digest[1:]) if tamper else digest
    return urllib.parse.urlencode(sorted(params.items()))


def callback(payment_id, amount, response_code="00", transaction_status="00", *, tamper=False):
    query = signed_vnpay_query(payment_id, amount, response_code, transaction_status,
                               tamper=tamper)
    assert query is not None, "VNP_TMN_CODE and VNP_HASH_SECRET are required for signed fixture tests"
    return call(None, "GET", "/thanh-toan/callback/vnpay?" + query,
                expected=200, anonymous=True)


def return_signature_fixture(payment_id, amount):
    query = signed_vnpay_query(payment_id, amount, "24", "02")
    if query is None:
        raise SkipScenario("ReturnURL fixture requires local VNP_TMN_CODE/VNP_HASH_SECRET")
    status, body, _ = call(None, "GET", "/thanh-toan/return/vnpay?" + query,
                           expected=200, anonymous=True)
    assert body["chuKyHopLe"] is True, body
    assert body["responseCode"] == "24" and body["transactionStatus"] == "02", body
    assert body["maGiaoDich"] == payment_id, body


def successful_ipn_issues_once():
    require_vnpay_fixture("successful IPN issuance")
    order, frozen = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[1]])
    payment = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                   {"maPhuongThuc": "VNPAY"}, expected=(201, 202),
                   key=idem_key("issue-init"))[1]
    PAYMENT_IDS.append(payment["maGiaoDich"])
    with ThreadPoolExecutor(max_workers=2) as pool:
        callbacks = list(pool.map(
            lambda _: callback(payment["maGiaoDich"], frozen["tongThanhToan"]), range(2)))
    codes = [body["RspCode"] for _, body, _ in callbacks]
    assert sorted(codes) == ["00", "02"], callbacks
    _, duplicate, _ = callback(payment["maGiaoDich"], frozen["tongThanhToan"])
    assert duplicate["RspCode"] == "02", duplicate
    current = call(t16.CUSTOMER_A, "GET", payment_path(payment["maGiaoDich"]))[1]
    assert current["trangThai"] == "THANH_CONG", current
    order_view = call(t16.CUSTOMER_A, "GET", order_path(order))[1]
    assert order_view["trangThaiDon"] == "DA_THANH_TOAN", order_view
    assert len(order_view["danhSachVe"]) == 1, order_view
    ticket = order_view["danhSachVe"][0]
    ticket_view = call(t16.CUSTOMER_A, "GET", "/ve-xe/" + ticket["maVe"])[1]
    assert ticket_view["qrToken"], ticket_view


def signed_failure_and_tamper_are_not_issued():
    require_vnpay_fixture("signed failure/tamper fixtures")
    order, frozen = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[2]])
    payment = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                   {"maPhuongThuc": "VNPAY"}, expected=(201, 202),
                   key=idem_key("failure-init"))[1]
    PAYMENT_IDS.append(payment["maGiaoDich"])
    _, tampered, _ = callback(payment["maGiaoDich"], frozen["tongThanhToan"], tamper=True)
    assert tampered["RspCode"] == "97", tampered
    _, mismatch, _ = callback(payment["maGiaoDich"], str(Decimal(frozen["tongThanhToan"]) + 1))
    assert mismatch["RspCode"] == "04", mismatch
    _, failed, _ = callback(payment["maGiaoDich"], frozen["tongThanhToan"], "14", "02")
    assert failed["RspCode"] == "00", failed
    current = call(t16.CUSTOMER_A, "GET", payment_path(payment["maGiaoDich"]))[1]
    assert current["trangThai"] == "THAT_BAI", current
    order_view = call(t16.CUSTOMER_A, "GET", order_path(order))[1]
    assert order_view["danhSachVe"] == [], order_view

    # A failed attempt does not block a fresh attempt. The second pending row
    # also gives the history assertion two deterministic items to page/sort.
    retry = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                 {"maPhuongThuc": "VNPAY"}, expected=201,
                 key=idem_key("failure-retry"))[1]
    PAYMENT_IDS.append(retry["maGiaoDich"])
    history = call(t16.CUSTOMER_A, "GET", order_path(order) +
                   "/thanh-toan?page=1&size=20&sort=maGiaoDich,desc")[1]
    assert history["totalItems"] == 2 and len(history["items"]) == 2, history
    assert [item["maGiaoDich"] for item in history["items"]] == sorted(
        (item["maGiaoDich"] for item in history["items"]), reverse=True), history


def expired_success_is_reconciled_without_ticket():
    require_vnpay_fixture("expired-payment fixture")
    order, frozen = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[3]])
    payment = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                   {"maPhuongThuc": "VNPAY"}, expected=(201, 202),
                   key=idem_key("expired-init"))[1]
    PAYMENT_IDS.append(payment["maGiaoDich"])
    hold_id = call(t16.CUSTOMER_A, "GET", order_path(order))[1]["danhSachGiuCho"][0]["maGiuCho"]
    db("UPDATE giu_cho SET ngay_dat=LOCALTIMESTAMP-INTERVAL '16 minutes', "
       "thoi_diem_bat_dau=LOCALTIMESTAMP-INTERVAL '16 minutes', "
       "thoi_diem_het_han=LOCALTIMESTAMP-INTERVAL '1 second' "
       "WHERE ma_giu_cho=" + t16.sql_literal(hold_id))
    _, ack, _ = callback(payment["maGiaoDich"], frozen["tongThanhToan"])
    assert ack["RspCode"] == "00", ack
    current = call(t16.CUSTOMER_A, "GET", payment_path(payment["maGiaoDich"]))[1]
    assert current["trangThai"] == "THANH_CONG" and current["canDoiSoat"] is True, current
    order_view = call(t16.CUSTOMER_A, "GET", order_path(order), expected=409)[1]
    assert order_view["code"] == "HOLD_EXPIRED", order_view
    t16.db_assert("NOT EXISTS (SELECT 1 FROM ve_xe WHERE ma_don=" + t16.sql_literal(order) + ")",
                  "Expired paid order issued a ticket")


def free_payment_issues_without_vnpay():
    order, frozen = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[4]], promotion=FREE_PROMOTION)
    assert Decimal(frozen["tongThanhToan"]) == Decimal("0"), frozen
    payment = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                   {"maPhuongThuc": "MIEN_PHI"}, expected=201,
                   key=idem_key("free-init"))[1]
    PAYMENT_IDS.append(payment["maGiaoDich"])
    assert payment["trangThai"] == "THANH_CONG", payment
    order_view = call(t16.CUSTOMER_A, "GET", order_path(order))[1]
    assert order_view["trangThaiDon"] == "DA_THANH_TOAN" and len(order_view["danhSachVe"]) == 1, order_view


def multiseseat_promotion_issues_once():
    require_vnpay_fixture("multi-seat promotion issuance")
    order, frozen = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[1], t16.SEAT_IDS[2]],
                                         promotion=t16.PROMO_ID, trip=t16.TRIP_IDS[1])
    assert frozen["tongTienGoc"] == "200002", frozen
    assert frozen["tongTienGiam"] == "25000", frozen
    assert frozen["tongThanhToan"] == "175002", frozen
    assert sum(Decimal(line["tienGiam"]) for line in frozen["chiTietGia"]) == Decimal("25000"), frozen

    payment = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                   {"maPhuongThuc": "VNPAY"}, expected=201,
                   key=idem_key("multi-promotion-init"))[1]
    PAYMENT_IDS.append(payment["maGiaoDich"])
    _, ack, _ = callback(payment["maGiaoDich"], frozen["tongThanhToan"])
    assert ack["RspCode"] == "00", ack
    order_view = call(t16.CUSTOMER_A, "GET", order_path(order))[1]
    assert order_view["trangThaiDon"] == "DA_THANH_TOAN", order_view
    ticket_views = [call(t16.CUSTOMER_A, "GET", "/ve-xe/" + ticket["maVe"])[1]
                    for ticket in order_view["danhSachVe"]]
    assert len(ticket_views) == 2, ticket_views
    assert sum(Decimal(ticket["tienGiam"]) for ticket in ticket_views) == Decimal("25000"), ticket_views
    assert sum(Decimal(ticket["thanhTien"]) for ticket in ticket_views) == Decimal("175002"), ticket_views
    assert all(ticket["qrToken"] for ticket in ticket_views), ticket_views
    t16.db_assert("(SELECT so_luong_da_dung FROM khuyen_mai WHERE ma_khuyen_mai="
              + t16.sql_literal(t16.PROMO_ID) + ") = 1",
              "A multi-seat paid order consumed more than one promotion use")


def frozen_price_survives_route_fare_change():
    require_vnpay_fixture("frozen-price route-fare check")
    order, frozen = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[3]], trip=t16.TRIP_IDS[1])
    trip = t16.TRIP_IDS[1]
    db("UPDATE tuyen_xe SET gia_co_ban=200001 WHERE ma_tuyen=(SELECT ma_tuyen FROM chuyen_xe WHERE ma_chuyen="
       + t16.sql_literal(trip) + ")")
    try:
        payment = call(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                       {"maPhuongThuc": "VNPAY"}, expected=201,
                       key=idem_key("frozen-fare-init"))[1]
        PAYMENT_IDS.append(payment["maGiaoDich"])
        assert payment["soTien"] == frozen["tongThanhToan"] == "100001", payment
        _, ack, _ = callback(payment["maGiaoDich"], frozen["tongThanhToan"])
        assert ack["RspCode"] == "00", ack
        order_view = call(t16.CUSTOMER_A, "GET", order_path(order))[1]
        ticket = order_view["danhSachVe"][0]
        issued = call(t16.CUSTOMER_A, "GET", "/ve-xe/" + ticket["maVe"])[1]
        assert issued["thanhTien"] == "100001" and issued["tienGiam"] == "0", issued
    finally:
        db("UPDATE tuyen_xe SET gia_co_ban=100001 WHERE ma_tuyen=(SELECT ma_tuyen FROM chuyen_xe WHERE ma_chuyen="
           + t16.sql_literal(trip) + ")")


def expired_payment_attempt_commits_state():
    # This checks D01 before any payment row exists: the service must commit
    # HET_HAN transitions before returning 409, without scheduler assistance.
    order, _ = prepare_frozen_order(t16.CUSTOMER_A, [t16.SEAT_IDS[4]], trip=t16.TRIP_IDS[1])
    view = call(t16.CUSTOMER_A, "GET", order_path(order))[1]
    hold_id = view["danhSachGiuCho"][0]["maGiuCho"]
    db("UPDATE giu_cho SET ngay_dat=LOCALTIMESTAMP-INTERVAL '16 minutes', "
       "thoi_diem_bat_dau=LOCALTIMESTAMP-INTERVAL '16 minutes', "
       "thoi_diem_het_han=LOCALTIMESTAMP-INTERVAL '1 second' WHERE ma_giu_cho="
       + t16.sql_literal(hold_id))
    status, expired, _ = t16.exchange(t16.CUSTOMER_A, "POST", order_path(order) + "/thanh-toan",
                                      {"maPhuongThuc": "VNPAY"}, key=idem_key("expired-before-payment"))
    assert status == 409 and expired["code"] == "HOLD_EXPIRED", expired
    t16.db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + t16.sql_literal(order)
              + " AND trang_thai='HET_HAN') AND EXISTS (SELECT 1 FROM giu_cho WHERE ma_giu_cho="
              + t16.sql_literal(hold_id) + " AND trang_thai='HET_HAN')",
              "Expired D01 attempt did not commit terminal order/hold state")
    status, expired_get, _ = t16.exchange(t16.CUSTOMER_A, "GET", order_path(order))
    assert status == 409 and expired_get["code"] == "HOLD_EXPIRED", expired_get


def cleanup():
    # Cleanup must also cover a payment whose response assertion failed before tracking its ID.
    users = ",".join(t16.sql_literal(client.user_id) for client in CLIENTS if client.user_id)
    if users:
        db("DELETE FROM payment_gateway_metadata WHERE ma_giao_dich IN (SELECT ma_giao_dich FROM thanh_toan WHERE ma_don IN (SELECT ma_don FROM don_dat_ve WHERE ma_nguoi_dung IN (" + users + ")))")
        db("DELETE FROM thanh_toan WHERE ma_don IN (SELECT ma_don FROM don_dat_ve WHERE ma_nguoi_dung IN (" + users + "))")
    t16.cleanup_owned_data()
    if PAYMENT_METHODS_CREATED:
        ids = ",".join(t16.sql_literal(code) for code in PAYMENT_METHODS_CREATED)
        db("DELETE FROM phuong_thuc_thanh_toan WHERE ma_phuong_thuc IN (" + ids + ")")
    if FREE_PROMOTION:
        db("DELETE FROM khuyen_mai WHERE ma_khuyen_mai=" + t16.sql_literal(FREE_PROMOTION))


def main():
    error = None
    try:
        scenario("health, migration and isolated T18 fixtures",
                 lambda: (t16.require_stack_migration_and_customers(),
                           t16.seed_owned_trip_fixtures(), seed_payment_methods(), seed_free_promotion()))
        contract = {}
        scenario("method selection and server-side amount", lambda: contract.update(
            order=payment_contract_and_method_selection()))
        payment_id = None
        if contract.get("order"):
            creation = {}
            scenario("idempotent VNPay creation", lambda: creation.update(
                payment_id=create_vnpay_and_replay(contract["order"])))
            payment_id = creation.get("payment_id")
        if payment_id:
            scenario("ownership, CSRF and payment status query", lambda: ownership_csrf_and_query_contract(payment_id))
        else:
            scenario("ownership, CSRF and payment status query",
                     lambda: (_ for _ in ()).throw(SkipScenario("no configured VNPay payment attempt")))
        if payment_id:
            amount = call(t16.CUSTOMER_A, "GET", payment_path(payment_id))[1]["soTien"]
            scenario("ReturnURL remains read-only", lambda: return_signature_fixture(payment_id, amount))
        else:
            scenario("ReturnURL remains read-only",
                     lambda: (_ for _ in ()).throw(SkipScenario("no configured VNPay payment attempt")))
        scenario("same-key method reuse, pending retry and T16 edit lock", payment_contract_and_pending_rules)
        scenario("MIEN_PHI issues a zero-value order", free_payment_issues_without_vnpay)
        scenario("successful signed IPN issues one ticket and duplicate ACK is safe", successful_ipn_issues_once)
        scenario("signed tamper, amount mismatch and failure issue no ticket", signed_failure_and_tamper_are_not_issued)
        scenario("expired successful payment is marked for reconciliation without issuance", expired_success_is_reconciled_without_ticket)
        scenario("multi-seat promotion allocation, one quota use and ticket net sum", multiseseat_promotion_issues_once)
        scenario("frozen price survives a route-fare change before payment", frozen_price_survives_route_fare_change)
        scenario("expired D01 attempt commits terminal states before returning 409", expired_payment_attempt_commits_state)
        print("INFO: D06 QueryDR requires an actual sandbox transaction; this smoke uses signed local IPN fixtures")
    except Exception as error_value:
        error = error_value
    finally:
        try:
            cleanup()
        except Exception as cleanup_error:
            print("FAIL: cleanup owned T18 fixtures — " + repr(cleanup_error))
            error = error or cleanup_error
        for client in reversed(CLIENTS):
            try:
                client.close()
            except Exception as close_error:
                print("FAIL: close fixture account — " + repr(close_error))
                error = error or close_error
    passed = sum(result == "PASS" for result, _ in RESULTS)
    failed = sum(result == "FAIL" for result, _ in RESULTS)
    skipped = sum(result == "SKIP" for result, _ in RESULTS)
    print("RESULT: %d PASS, %d SKIP, %d FAIL, %d HTTP assertions" %
          (passed, skipped, failed, t16.HTTP_ASSERTIONS))
    return 1 if error or failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
