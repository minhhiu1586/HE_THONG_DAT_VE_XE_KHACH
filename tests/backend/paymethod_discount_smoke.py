"""T14 promotion/payment-method HTTP and PostgreSQL smoke checks.

Run from the repository root after building/deploying the backend:
    python tests/backend/paymethod_discount_smoke.py

The script uses only Python's standard library plus the existing AdminFixture and
Docker CLI. All fixture resource codes share a random prefix and are removed in
finally; it never deletes tables or rows outside that prefix.
"""
import concurrent.futures
import json
import os
import secrets
import threading
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

from auth_fixture import AdminFixture


BASE = os.environ.get("API_BASE_URL", "http://localhost:8080/api").rstrip("/")
PREFIX = "T14_" + uuid.uuid4().hex[:12] + "_"
ADMIN = AdminFixture(BASE)
CUSTOMER = AdminFixture(BASE)
ANONYMOUS = AdminFixture(BASE)
CLIENTS = [ADMIN, CUSTOMER]
HTTP_ASSERTIONS = 0
SCENARIOS = 0
STATUS_COUNTS = {}
COUNTS_LOCK = threading.Lock()


def ident(suffix):
    return PREFIX + suffix


def sql_literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def db(statement):
    ADMIN.sql(statement)


def db_assert(condition, message):
    db("DO $$ BEGIN IF NOT (" + condition + ") THEN RAISE EXCEPTION "
       + sql_literal(message) + "; END IF; END $$;")


def exchange(client, method, path, data=None, raw=None, csrf=True):
    global HTTP_ASSERTIONS
    headers = client.headers()
    if not csrf:
        headers.pop("X-CSRF-Token", None)
    payload = raw if raw is not None else (
        json.dumps(data, ensure_ascii=False).encode("utf-8") if data is not None else None
    )
    request = urllib.request.Request(
        BASE + path, data=payload, method=method, headers=headers
    )
    try:
        with client.opener.open(request, timeout=20) as response:
            status = response.status
            body = response.read()
            response_headers = response.headers
    except urllib.error.HTTPError as error:
        status = error.code
        body = error.read()
        response_headers = error.headers

    with COUNTS_LOCK:
        HTTP_ASSERTIONS += 1
        STATUS_COUNTS[status] = STATUS_COUNTS.get(status, 0) + 1

    if status == 204:
        assert body == b"", (method, path, "204 response had a body", body[:300])
        return status, None, response_headers

    content_type = response_headers.get("Content-Type", "")
    assert "application/json" in content_type, (method, path, status, content_type, body[:300])
    try:
        result = json.loads(body)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise AssertionError((method, path, status, body[:500])) from error
    if status >= 400:
        assert isinstance(result, dict) and isinstance(result.get("code"), str) and result["code"], result
        assert {"code", "message", "details", "timestamp", "traceId"} <= result.keys(), result
        assert response_headers.get("X-Trace-Id") == result["traceId"], result
    if status == 201:
        assert response_headers.get("Location"), (method, path, "Missing Location")
    return status, result, response_headers


def call(client, method, path, data=None, expected=200, raw=None, csrf=True):
    status, result, headers = exchange(client, method, path, data, raw, csrf)
    assert status == expected, (method, path, status, expected, result)
    return result


def pass_scenario(label):
    global SCENARIOS
    SCENARIOS += 1
    print("PASS: " + label)


def customer_start(client):
    profile = client.send("POST", "/auth/dang-ky", {
        "hoTen": "Khách hàng T14",
        "email": client.email,
        "soDienThoai": "09" + "".join(secrets.choice("0123456789") for _ in range(8)),
        "matKhau": client.password,
    })
    client.user_id = profile["maNguoiDung"]
    login = client.send("POST", "/auth/dang-nhap", {
        "email": client.email, "matKhau": client.password
    })
    client.csrf = login["csrfToken"]


def now_business_date():
    return datetime.now(timezone(timedelta(hours=7))).date()


def promotion(code, name, start, end, quantity=10, discount=15.25):
    return {
        "maKhuyenMai": code,
        "tenKhuyenMai": name,
        "moTa": "T14 smoke fixture",
        "loaiGiam": "PHAN_TRAM",
        "giaTriGiam": discount,
        "giamToiDa": "50000",
        "donToiThieu": "0",
        "soLuong": quantity,
        "ngayBatDau": start,
        "ngayKetThuc": end,
    }


def stamp(day, hour=0):
    return day.strftime("%Y-%m-%d") + "T" + f"{hour:02d}:00:00+07:00"


def create_promotion(code, name, start, end, quantity=10, discount=15.25):
    result = call(ADMIN, "POST", "/quan-tri/khuyen-mai",
                  promotion(code, name, start, end, quantity, discount), 201)
    assert result["maKhuyenMai"] == code and result["soLuongDaDung"] == 0
    return result


def auth_and_payment_methods():
    admin_path = "/quan-tri/phuong-thuc-thanh-toan"
    call(ANONYMOUS, "GET", "/quan-tri/khuyen-mai", expected=401)
    call(ANONYMOUS, "GET", admin_path, expected=401)
    call(CUSTOMER, "GET", "/quan-tri/khuyen-mai", expected=403)
    call(CUSTOMER, "GET", admin_path, expected=403)

    code = ident("PAY_CSRF")
    body = {"maPhuongThuc": code, "tenPhuongThuc": "T14 CSRF check"}
    call(ADMIN, "POST", admin_path, body, expected=403, csrf=False)
    db_assert("NOT EXISTS (SELECT 1 FROM phuong_thuc_thanh_toan WHERE ma_phuong_thuc="
              + sql_literal(code) + ")", "Missing-CSRF payment method was persisted")

    code = ident("PAY_CRUD")
    created = call(ADMIN, "POST", admin_path, {
        "maPhuongThuc": code, "tenPhuongThuc": "T14 chuyển khoản", "moTa": "Mô tả ban đầu"
    }, 201)
    assert created["trangThai"] == "HOAT_DONG"
    detail_path = admin_path + "/" + code
    assert call(ADMIN, "GET", detail_path)["maPhuongThuc"] == code
    call(ADMIN, "POST", admin_path, {
        "maPhuongThuc": code, "tenPhuongThuc": "Trùng mã"
    }, 409)
    updated = call(ADMIN, "PUT", detail_path, {
        "tenPhuongThuc": "T14 chuyển khoản đã sửa", "moTa": "Mô tả đã sửa"
    })
    assert updated["tenPhuongThuc"] == "T14 chuyển khoản đã sửa"
    assert call(ADMIN, "GET", detail_path)["moTa"] == "Mô tả đã sửa"
    db_assert("(SELECT count(*) FROM phuong_thuc_thanh_toan WHERE ma_phuong_thuc="
              + sql_literal(code) + " AND ten_phuong_thuc='T14 chuyển khoản đã sửa' "
              "AND mo_ta='Mô tả đã sửa' AND trang_thai='HOAT_DONG') = 1",
              "Payment method create/update was not persisted")

    public_path = "/phuong-thuc-thanh-toan"
    public_codes = {item["maPhuongThuc"] for item in call(ANONYMOUS, "GET", public_path)}
    assert code in public_codes
    for status in ("TAM_NGUNG", "NGUNG_HOAT_DONG"):
        changed = call(ADMIN, "PUT", detail_path + "/trang-thai", {"trangThai": status})
        assert changed["trangThai"] == status
        repeated = call(ADMIN, "PUT", detail_path + "/trang-thai", {"trangThai": status})
        assert repeated["trangThai"] == status
        admin_items = call(ADMIN, "GET", admin_path)
        assert any(item["maPhuongThuc"] == code and item["trangThai"] == status
                   for item in admin_items)
        public_codes = {item["maPhuongThuc"] for item in call(ANONYMOUS, "GET", public_path)}
        assert code not in public_codes
    call(ADMIN, "PUT", detail_path + "/trang-thai", {"trangThai": "HOAT_DONG"})
    assert code in {item["maPhuongThuc"] for item in call(ANONYMOUS, "GET", public_path)}

    call(ADMIN, "PUT", detail_path, {"tenPhuongThuc": "   "}, 400)
    call(ADMIN, "PUT", detail_path, {
        "maPhuongThuc": code, "tenPhuongThuc": "Không được đổi mã"
    }, 400)
    assert call(ADMIN, "GET", detail_path)["tenPhuongThuc"] == "T14 chuyển khoản đã sửa"
    call(ADMIN, "GET", admin_path + "/" + ident("PAY_MISSING"), expected=404)
    call(ADMIN, "GET", detail_path + "/unknown", expected=404)
    call(ADMIN, "GET", admin_path + "?unexpected=1", expected=400)
    status, _, headers = exchange(ADMIN, "DELETE", detail_path)
    assert status == 405 and headers.get("Allow") == "GET, HEAD, PUT, OPTIONS", headers
    call(ADMIN, "POST", public_path, {"maPhuongThuc": ident("PAY_BAD"),
                                       "tenPhuongThuc": "Sai nơi"}, expected=405)
    call(ADMIN, "POST", admin_path, {
        "maPhuongThuc": ident("PAY_BAD"), "tenPhuongThuc": "   "
    }, expected=400)
    db_assert("NOT EXISTS (SELECT 1 FROM phuong_thuc_thanh_toan WHERE ma_phuong_thuc="
              + sql_literal(ident("PAY_BAD")) + ")", "Invalid payment method was persisted")
    pass_scenario("payment-method access, CRUD/status, public availability, errors and persistence")


def promotion_search_and_status():
    today = now_business_date()
    filter_day = today + timedelta(days=20)
    end_day = today + timedelta(days=25)
    active_code = ident("KM_LITERAL")
    create_promotion(active_code, "Promo 100% reward T14", stamp(filter_day), stamp(end_day))
    create_promotion(ident("KM_DECOY"), "Promo 100X reward T14", stamp(filter_day), stamp(end_day))
    expired_code = ident("KM_EXPIRED_FULL")
    expired = create_promotion(expired_code, "T14 expired and full", stamp(today - timedelta(days=20)),
                               stamp(today - timedelta(days=1)), quantity=0)
    quota_code = ident("KM_FULL_FUTURE")
    quota = create_promotion(quota_code, "T14 quota full", stamp(filter_day), stamp(end_day), quantity=0)
    assert expired["trangThai"] == "HET_HAN", expired
    assert quota["trangThai"] == "HET_LUOT", quota

    query = urllib.parse.urlencode({"tuKhoa": "100% reward T14"})
    matching = call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + query)
    matching_codes = {item["maKhuyenMai"] for item in matching["items"]}
    assert active_code in matching_codes and ident("KM_DECOY") not in matching_codes
    case_match = call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + urllib.parse.urlencode({
        "tuKhoa": active_code.lower(), "trangThai": "HOAT_DONG"
    }))
    assert [item["maKhuyenMai"] for item in case_match["items"]] == [active_code]
    date_query = urllib.parse.urlencode({"tuNgay": filter_day.isoformat(), "denNgay": filter_day.isoformat()})
    date_items = call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + date_query)["items"]
    date_codes = {item["maKhuyenMai"] for item in date_items}
    assert active_code in date_codes and ident("KM_DECOY") in date_codes
    assert expired_code not in date_codes and quota_code in date_codes

    for status, code in (("HET_HAN", expired_code), ("HET_LUOT", quota_code)):
        filtered = call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + urllib.parse.urlencode({
            "tuKhoa": code, "trangThai": status
        }))
        assert code in {item["maKhuyenMai"] for item in filtered["items"]}

    page_codes = [ident("KM_PAGE_A"), ident("KM_PAGE_B"), ident("KM_PAGE_C")]
    for code in page_codes:
        create_promotion(code, "Page group " + PREFIX + code[-1], stamp(filter_day), stamp(end_day))
    page_one = call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + urllib.parse.urlencode({
        "tuKhoa": "Page group " + PREFIX, "page": 1, "size": 2
    }))
    page_two = call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + urllib.parse.urlencode({
        "tuKhoa": "Page group " + PREFIX, "page": 2, "size": 2
    }))
    assert (page_one["totalItems"], page_one["totalPages"], len(page_one["items"])) == (3, 2, 2), page_one
    assert (page_two["totalItems"], page_two["totalPages"], len(page_two["items"])) == (3, 2, 1), page_two
    paged_codes = [item["maKhuyenMai"] for item in page_one["items"] + page_two["items"]]
    assert paged_codes == sorted(page_codes), paged_codes
    page_beyond = call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + urllib.parse.urlencode({
        "tuKhoa": "Page group " + PREFIX, "page": 3, "size": 2
    }))
    assert page_beyond["items"] == [] and page_beyond["totalItems"] == 3, page_beyond
    pass_scenario("promotion literal keyword/date filters, pagination and expiry priority")


def promotion_crud_and_validation():
    today = now_business_date()
    start, end = stamp(today - timedelta(days=1)), stamp(today + timedelta(days=40))
    code = ident("KM_CRUD")
    create_promotion(code, "T14 promotion before update", start, end)
    detail_path = "/quan-tri/khuyen-mai/" + code
    assert call(ADMIN, "GET", detail_path)["maKhuyenMai"] == code
    listed = call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + urllib.parse.urlencode({"tuKhoa": "KM_CRUD"}))
    assert any(item["maKhuyenMai"] == code for item in listed["items"])

    changed = promotion(code, "T14 promotion updated", start, end, quantity=12, discount=20.5)
    changed.pop("maKhuyenMai")
    changed["moTa"] = "Updated by T14 smoke"
    updated = call(ADMIN, "PUT", detail_path, changed)
    assert updated["tenKhuyenMai"] == changed["tenKhuyenMai"]
    assert updated["giaTriGiam"] == changed["giaTriGiam"] and updated["soLuong"] == 12
    assert call(ADMIN, "GET", detail_path)["moTa"] == "Updated by T14 smoke"
    db_assert("(SELECT count(*) FROM khuyen_mai WHERE ma_khuyen_mai=" + sql_literal(code)
              + " AND ten_khuyen_mai='T14 promotion updated' AND so_luong=12 "
              "AND gia_tri_giam=20.5 AND mo_ta='Updated by T14 smoke') = 1",
              "Promotion create/update was not persisted")

    call(ADMIN, "POST", "/quan-tri/khuyen-mai", promotion(code, "Duplicate", start, end), 409)
    invalid_update = changed | {"tenKhuyenMai": "Must roll back", "giaTriGiam": 20.555}
    call(ADMIN, "PUT", detail_path, invalid_update, 400)
    assert call(ADMIN, "GET", detail_path)["tenKhuyenMai"] == "T14 promotion updated"
    db_assert("(SELECT count(*) FROM khuyen_mai WHERE ma_khuyen_mai=" + sql_literal(code)
              + " AND ten_khuyen_mai='T14 promotion updated' AND gia_tri_giam=20.5) = 1",
              "Rejected promotion update was not rolled back")
    call(ADMIN, "POST", "/quan-tri/khuyen-mai", promotion(ident("KM_BAD_PRECISION"),
        "T14 too precise", start, end, discount=3.333), 400)
    call(ADMIN, "POST", "/quan-tri/khuyen-mai", raw=raw_invalid_body(), expected=400)
    invalid = promotion(ident("KM_INVALID"), "T14 invalid payload", start, end)
    for fields in (
            {"giaTriGiam": "15"},
            {"loaiGiam": "SO_TIEN_CO_DINH", "giaTriGiam": 10000},
            {"donToiThieu": "100.5"},
            {"donToiThieu": "1000000000000"},
            {"tenKhuyenMai": None},
            {"ngayKetThuc": start},
            {"soLuong": -1},
            {"unexpected": True}):
        call(ADMIN, "POST", "/quan-tri/khuyen-mai", invalid | fields, expected=400)
    exact_raw = json.dumps(invalid | {"giaTriGiam": "EXACT_NUMBER"}).replace(
        '"EXACT_NUMBER"', '15.120000000000000001').encode("utf-8")
    call(ADMIN, "POST", "/quan-tri/khuyen-mai", raw=exact_raw, expected=400)
    db_assert("NOT EXISTS (SELECT 1 FROM khuyen_mai WHERE ma_khuyen_mai="
              + sql_literal(ident("KM_INVALID")) + ")", "Invalid promotion was persisted")
    fixed = promotion(ident("KM_FIXED"), "T14 fixed discount", start, end) | {
        "loaiGiam": "SO_TIEN_CO_DINH", "giaTriGiam": "10000", "giamToiDa": None}
    assert call(ADMIN, "POST", "/quan-tri/khuyen-mai", fixed, expected=201)["giaTriGiam"] == "10000"
    db_assert("NOT EXISTS (SELECT 1 FROM khuyen_mai WHERE ma_khuyen_mai="
              + sql_literal(ident("KM_BAD_PRECISION")) + ")", "Invalid precision promotion was persisted")
    call(ADMIN, "PUT", detail_path, changed | {"maKhuyenMai": code}, 400)

    preview_order = ident("ORDER_PREVIEW")
    db("INSERT INTO don_dat_ve (ma_don, ma_nguoi_dung, ngay_dat, tong_tien, tien_giam, "
       "ma_khuyen_mai, khach_dat_ten, khach_dat_so_dien_thoai, kenh_dat, trang_thai) VALUES ("
       + ", ".join((sql_literal(preview_order), sql_literal(CUSTOMER.user_id), "now()", "0", "0",
                    sql_literal(code), sql_literal("Khách hàng T14"), sql_literal("0900000000"),
                    sql_literal("WEB"), sql_literal("CHO_XU_LY"))) + ")")

    preview_update = changed | {"tenKhuyenMai": "T14 promotion with pending preview"}
    allowed_update = call(ADMIN, "PUT", detail_path, preview_update, 200)
    assert allowed_update["tenKhuyenMai"] == preview_update["tenKhuyenMai"]
    db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + sql_literal(preview_order)
              + " AND ma_khuyen_mai=" + sql_literal(code) + " AND trang_thai='CHO_XU_LY')",
              "Pending preview order fixture missing")
    db("DELETE FROM don_dat_ve WHERE ma_don=" + sql_literal(preview_order))
    db_assert("NOT EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + sql_literal(preview_order) + ")",
              "Pending preview order fixture was not removed")
    pass_scenario("promotion update is allowed while only a pending preview order selects the code")

    # Restore the baseline before verifying that committed purchase history protects the promotion.
    call(ADMIN, "PUT", detail_path, changed, 200)
    history_order = ident("ORDER_HISTORY")
    db("INSERT INTO don_dat_ve (ma_don, ma_nguoi_dung, ngay_dat, tong_tien, tien_giam, "
       "ma_khuyen_mai, khach_dat_ten, khach_dat_so_dien_thoai, kenh_dat, trang_thai) VALUES ("
       + ", ".join((sql_literal(history_order), sql_literal(CUSTOMER.user_id), "now()", "0", "0",
                    sql_literal(code), sql_literal("Khách hàng T14"), sql_literal("0900000000"),
                    sql_literal("WEB"), sql_literal("DA_THANH_TOAN"))) + ")")
    call(ADMIN, "PUT", detail_path, changed, 409)
    assert call(ADMIN, "GET", detail_path)["tenKhuyenMai"] == "T14 promotion updated"
    call(ADMIN, "DELETE", detail_path, expected=409)
    db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + sql_literal(history_order)
              + " AND ma_khuyen_mai=" + sql_literal(code) + ")", "Owned promotion history fixture missing")

    call(ADMIN, "GET", "/quan-tri/khuyen-mai/" + ident("KM_MISSING"), expected=404)
    delete_code = ident("KM_DELETE")
    create_promotion(delete_code, "T14 safe delete", start, end)
    call(ADMIN, "DELETE", "/quan-tri/khuyen-mai/" + delete_code, expected=204)
    call(ADMIN, "GET", "/quan-tri/khuyen-mai/" + delete_code, expected=404)
    call(ADMIN, "DELETE", "/quan-tri/khuyen-mai/" + ident("KM_DELETE_MISSING"), expected=404)
    call(ADMIN, "GET", detail_path + "/extra", expected=404)
    call(ADMIN, "GET", "/quan-tri/khuyen-mai?unknown=1", expected=400)
    call(ADMIN, "GET", "/quan-tri/khuyen-mai?" + urllib.parse.urlencode({"page": 0}), expected=400)
    call(ADMIN, "POST", detail_path, changed, 405)
    call(ADMIN, "PUT", "/quan-tri/khuyen-mai", changed, 405)
    call(ADMIN, "DELETE", "/quan-tri/khuyen-mai", expected=405)
    pass_scenario("promotion CRUD, validation, duplicate handling, rollback and history protection")


def raw_invalid_body():
    return b"{broken"


def resource_code_contract():
    start = stamp(now_business_date())
    end = stamp(now_business_date() + timedelta(days=30))
    invalid_codes = (
        ident("BAD CODE"), " " + ident("BAD"), ident("BAD") + " ",
        ident("Mã"), ident("BAD/CODE"), ident("TOO_LONG").ljust(41, "X")
    )
    for path, field in (
            ("/quan-tri/khuyen-mai", "maKhuyenMai"),
            ("/quan-tri/phuong-thuc-thanh-toan", "maPhuongThuc")):
        def body_for(code):
            if field == "maKhuyenMai":
                return promotion(code, "T14 code validation", start, end)
            return {field: code, "tenPhuongThuc": "T14 code validation"}

        for code in invalid_codes:
            error = call(ADMIN, "POST", path, body_for(code), expected=400)
            assert error["code"] == "VALIDATION_ERROR", error
            assert any(item["field"] == field
                       for item in error["details"]["fieldErrors"]), error

        code = ident("A1-_").ljust(40, "X")
        status, result, headers = exchange(ADMIN, "POST", path, body_for(code))
        assert status == 201 and result[field] == code, (status, result)
        location = headers["Location"]
        assert location == "/api" + path + "/" + code, location
        assert call(ADMIN, "GET", location.removeprefix("/api"))[field] == code
    pass_scenario("resource code validation and readable Location for both modules")


def concurrent_duplicate_code():
    code = ident("KM_RACE")
    body = promotion(code, "T14 concurrent insert", stamp(now_business_date()),
                     stamp(now_business_date() + timedelta(days=30)))
    count = 3
    workers = []
    for _ in range(count):
        worker = AdminFixture(BASE)
        CLIENTS.append(worker)
        worker.start()
        workers.append(worker)
    barrier = threading.Barrier(count)
    def create_same_code(worker):
        barrier.wait(timeout=10)
        return exchange(worker, "POST", "/quan-tri/khuyen-mai", body)[0]
    with concurrent.futures.ThreadPoolExecutor(max_workers=count) as pool:
        results = list(pool.map(create_same_code, workers))
    assert results.count(201) == 1 and results.count(409) == count - 1, results
    db_assert("(SELECT count(*) FROM khuyen_mai WHERE ma_khuyen_mai=" + sql_literal(code) + ") = 1",
              "Concurrent promotion insert did not leave exactly one row")
    pass_scenario("same-code parallel promotion POST conflict")


def cleanup():
    # Every ID below is generated by ident(); delete child references first.
    prefix_len = len(PREFIX)
    for table, key in (("don_dat_ve", "ma_don"), ("khuyen_mai", "ma_khuyen_mai"),
                       ("phuong_thuc_thanh_toan", "ma_phuong_thuc")):
        db(f"DELETE FROM {table} WHERE left({key}, {prefix_len}) = {sql_literal(PREFIX)}")


if __name__ == "__main__":
    try:
        ADMIN.start()
        customer_start(CUSTOMER)
        auth_and_payment_methods()
        promotion_search_and_status()
        promotion_crud_and_validation()
        resource_code_contract()
        concurrent_duplicate_code()
        status_text = ", ".join(f"HTTP {status}: {count}" for status, count in sorted(STATUS_COUNTS.items()))
        print(f"PASS: {SCENARIOS} T14 scenarios, {HTTP_ASSERTIONS} HTTP assertions ({status_text})")
    finally:
        try:
            cleanup()
        finally:
            for client in reversed(CLIENTS):
                client.close()
