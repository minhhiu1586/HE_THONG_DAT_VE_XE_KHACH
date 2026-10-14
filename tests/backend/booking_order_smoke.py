"""T16 real HTTP/PostgreSQL integration smoke; it never starts a DB or deploys WAR.

Run from the repository root after applying migration 005 and deploying the backend:
    DB_NAME=datvexe python tests/backend/booking_order_smoke.py

Uses only Python's standard library, the Docker CLI, and auth_fixture.AdminFixture
for disposable cookie jars/SQL access. Accounts remain KHACH_HANG. All created
resource codes use a random T16 prefix, and cleanup scopes orders to these users.
"""
import concurrent.futures
import copy
import http.cookiejar
import json
import os
import secrets
import threading
import urllib.error
import urllib.request
import uuid
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

os.environ.setdefault("DB_NAME", "datvexe")

from auth_fixture import AdminFixture


BASE = os.environ.get("API_BASE_URL", "http://localhost:8080/api").rstrip("/")
PREFIX = "T16_" + uuid.uuid4().hex[:10] + "_"
IDEMPOTENCY_PREFIX = "T16-" + uuid.uuid4().hex[:10] + "-"
CUSTOMER_A = AdminFixture(BASE)
CUSTOMER_B = AdminFixture(BASE)
CUSTOMER_C = AdminFixture(BASE)
CLIENTS = [CUSTOMER_A, CUSTOMER_B, CUSTOMER_C]
ANONYMOUS = urllib.request.build_opener()
HTTP_ASSERTIONS = 0
STATUS_COUNTS = {}
RESULTS = []
COUNTS_LOCK = threading.Lock()
ORDER_IDS = set()
TRIP_IDS = []
SEAT_IDS = []
PROMO_ID = None
STAFF_IDS = []
STAFF_PHONES = set()
RACE_WINNER = None
RACE_LOSER = None
CUSTOMER_C_ORDER_ID = None
BUSINESS_TZ = timezone(timedelta(hours=7))
TRIP_DATES = [(datetime.now(BUSINESS_TZ).date() + timedelta(days=30 + i)).isoformat() for i in range(3)]


class SessionView:
    """An HTTP client identity with a selected JSESSIONID but the same auth cookie."""
    def __init__(self, source, session_source=None):
        self.user_id = source.user_id
        self.csrf = source.csrf
        self.jar = http.cookiejar.CookieJar()
        for cookie in source.jar:
            if cookie.name != "JSESSIONID":
                self.jar.set_cookie(copy.copy(cookie))
        if session_source is not None:
            for cookie in session_source.jar:
                if cookie.name == "JSESSIONID":
                    self.jar.set_cookie(copy.copy(cookie))
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def headers(self):
        return {"Content-Type": "application/json", "X-Requested-With": "datvexe",
                **({"X-CSRF-Token": self.csrf} if self.csrf else {})}


def ident(suffix):
    return PREFIX + suffix


def idem_key(suffix):
    return IDEMPOTENCY_PREFIX + suffix


def staff_phone():
    while True:
        phone = "09" + "".join(secrets.choice("0123456789") for _ in range(8))
        if phone not in STAFF_PHONES:
            STAFF_PHONES.add(phone)
            return phone


def sql_literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def db(statement):
    CUSTOMER_A.sql("SET TIME ZONE 'Asia/Ho_Chi_Minh'; " + statement)


def db_assert(condition, message):
    db("DO $$ BEGIN IF NOT (" + condition + ") THEN RAISE EXCEPTION "
       + sql_literal(message) + "; END IF; END $$;")


def customer_start(client):
    profile = client.send("POST", "/auth/dang-ky", {
        "hoTen": "Khách hàng kiểm thử T16",
        "email": client.email,
        "soDienThoai": "09" + "".join(secrets.choice("0123456789") for _ in range(8)),
        "matKhau": client.password,
    })
    client.user_id = profile["maNguoiDung"]
    login = client.send("POST", "/auth/dang-nhap", {"email": client.email, "matKhau": client.password})
    client.csrf = login["csrfToken"]


def exchange(actor, method, path, body=None, *, key=None, csrf=True, anonymous=False):
    global HTTP_ASSERTIONS
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json", "X-Requested-With": "datvexe"}
    if actor is not None:
        headers.update(actor.headers())
        if not csrf:
            headers.pop("X-CSRF-Token", None)
    if key is not None:
        headers["Idempotency-Key"] = key
    request = urllib.request.Request(BASE + path, data=payload, method=method, headers=headers)
    opener = ANONYMOUS if anonymous else actor.opener
    try:
        with opener.open(request, timeout=30) as response:
            status, raw, response_headers = response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        status, raw, response_headers = error.code, error.read(), error.headers

    with COUNTS_LOCK:
        HTTP_ASSERTIONS += 1
        STATUS_COUNTS[status] = STATUS_COUNTS.get(status, 0) + 1
    if status == 204:
        assert raw == b"", (method, path, "204 response had a body", raw[:300])
        return status, None, response_headers
    assert "application/json" in response_headers.get("Content-Type", ""), (
        method, path, status, response_headers.get("Content-Type"), raw[:300]
    )
    try:
        result = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise AssertionError((method, path, status, raw[:500])) from error
    if status >= 400:
        assert isinstance(result, dict) and isinstance(result.get("code"), str), result
        assert {"code", "message", "details", "timestamp", "traceId"} <= result.keys(), result
        assert response_headers.get("X-Trace-Id") == result["traceId"], result
    if status == 201:
        assert response_headers.get("Location"), (method, path, "Missing Location")
    return status, result, response_headers


def track(value):
    if isinstance(value, dict) and isinstance(value.get("maDon"), str):
        ORDER_IDS.add(value["maDon"])


def call(actor, method, path, body=None, expected=200, *, key=None, csrf=True, anonymous=False):
    status, result, _ = exchange(actor, method, path, body, key=key, csrf=csrf, anonymous=anonymous)
    allowed = expected if isinstance(expected, tuple) else (expected,)
    assert status in allowed, (method, path, allowed, status, result)
    track(result)
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


def require_stack_migration_and_customers():
    request = urllib.request.Request(BASE + "/health", headers={"X-Requested-With": "datvexe"})
    with ANONYMOUS.open(request, timeout=10) as response:
        assert response.status == 200
        health = json.loads(response.read())
    assert health.get("status") == "UP" and health.get("database") == "UP", health
    db_assert("to_regclass('api_idempotency') IS NOT NULL", "Migration 004 table api_idempotency is missing")
    index_def = "pg_get_indexdef(to_regclass('uq_ve_xe_active_trip_seat'))"
    db_assert("to_regclass('uq_ve_xe_active_trip_seat') IS NOT NULL AND position('WHERE' in " + index_def + ") > 0 "
              "AND position('DA_XUAT_VE' in " + index_def + ") > 0 AND position('DA_LEN_XE' in " + index_def + ") > 0 "
              "AND position('NHAP' in " + index_def + ") = 0",
              "Migration 005 issued/boarded-only seat index is missing")
    for client in CLIENTS:
        customer_start(client)
    users = ",".join(sql_literal(client.user_id) for client in CLIENTS)
    db_assert("(SELECT count(*) FROM nguoi_dung WHERE ma_nguoi_dung IN (" + users
              + ") AND loai_tai_khoan='KHACH_HANG') = 3", "Smoke accounts must remain KHACH_HANG")


def seed_owned_trip_fixtures():
    city, station_a, station_b = ident("CITY"), ident("ST_A"), ident("ST_B")
    company, route, vehicle, vehicle_type = ident("OP"), ident("ROUTE"), ident("BUS"), ident("TYPE")
    db(
        f"INSERT INTO tinh_thanh(ma_tinh_thanh,ten_tinh_thanh) VALUES ({sql_literal(city)},'T16 QA');"
        f"INSERT INTO ben_xe(ma_ben_xe,ten_ben_xe,dia_chi,ma_tinh_thanh,trang_thai) VALUES "
        f"({sql_literal(station_a)},'T16 A','T16 A',{sql_literal(city)},'HOAT_DONG'),"
        f"({sql_literal(station_b)},'T16 B','T16 B',{sql_literal(city)},'HOAT_DONG');"
        f"INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,trang_thai) VALUES ({sql_literal(company)},'T16 QA','HOAT_DONG');"
        f"INSERT INTO loai_xe(ma_loai_xe,ten_loai_xe,so_cho_ngoi,so_tang) VALUES ({sql_literal(vehicle_type)},'T16 six seats',6,1);"
        f"INSERT INTO xe(ma_xe,bien_so,ma_loai_xe,so_cho_ngoi,so_do_ngoi,ngay_dang_kiem,han_dang_kiem,trang_thai,ma_nha_xe) VALUES "
        f"({sql_literal(vehicle)},'{PREFIX}PLATE',{sql_literal(vehicle_type)},6,1,DATE '2020-01-01',DATE '2100-01-01','SAN_SANG',{sql_literal(company)});"
        f"INSERT INTO tuyen_xe(ma_tuyen,ten_tuyen,tram_khoi_hanh,tram_den,thoi_gian_khoi_hanh,thoi_gian_du_kien,gia_co_ban,trang_thai,ma_nha_xe) VALUES "
        f"({sql_literal(route)},'T16 smoke route',{sql_literal(station_a)},{sql_literal(station_b)},TIME '08:00',2,100001,'DANG_KHAI_THAC',{sql_literal(company)});"
        f"INSERT INTO tuyen_xe(ma_tuyen,ten_tuyen,tram_khoi_hanh,tram_den,thoi_gian_khoi_hanh,thoi_gian_du_kien,gia_co_ban,trang_thai,ma_nha_xe) VALUES "
        f"({sql_literal(ident('RETURN_ROUTE'))},'T16 return route',{sql_literal(station_b)},{sql_literal(station_a)},TIME '08:00',2,100001,'DANG_KHAI_THAC',{sql_literal(company)});"
    )
    db("INSERT INTO ghe(ma_ghe,vi_tri,tang,trang_thai,ma_xe) VALUES "
       + ",".join(f"({sql_literal(ident('S' + str(i)))},'{i}',1,'HOAT_DONG',{sql_literal(vehicle)})" for i in range(1, 7)))
    for index, day in enumerate(TRIP_DATES, start=1):
        trip = ident("TRIP" + str(index))
        trip_route = route if index % 2 else ident("RETURN_ROUTE")
        TRIP_IDS.append(trip)
        db(f"INSERT INTO chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai) VALUES "
            f"({sql_literal(trip)},{sql_literal(trip_route)},{sql_literal(vehicle)},DATE {sql_literal(day)},TIME '08:00',"
            f"(DATE {sql_literal(day)} + TIME '10:00')::timestamp,'DANG_MO_BAN')")
        driver, assistant = ident(f"DRIVER{index}"), ident(f"ASSISTANT{index}")
        STAFF_IDS.extend((driver, assistant))
        driver_phone, assistant_phone = staff_phone(), staff_phone()
        db("INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,so_bang_lai,ngay_het_han_bang_lai,"
           "loai_nhan_vien,trang_thai,ma_nha_xe) VALUES "
           + f"({sql_literal(driver)},'T16 driver {index}',{sql_literal(driver_phone)},"
           + f"{sql_literal(driver.lower() + '@example.test')},'123456789012',DATE '2100-01-01','TAI_XE','DANG_LAM_VIEC',{sql_literal(company)}),"
           + f"({sql_literal(assistant)},'T16 assistant {index}',{sql_literal(assistant_phone)},"
           + f"{sql_literal(assistant.lower() + '@example.test')},NULL,NULL,'PHU_XE','DANG_LAM_VIEC',{sql_literal(company)})")
        db("INSERT INTO phan_cong_chuyen_xe(ma_phan_cong,ma_chuyen,ma_nhan_vien,vai_tro) VALUES "
           + f"({sql_literal(ident(f'ASSIGN{index}D'))},{sql_literal(trip)},{sql_literal(driver)},'TAI_XE_CHINH'),"
           + f"({sql_literal(ident(f'ASSIGN{index}A'))},{sql_literal(trip)},{sql_literal(assistant)},'PHU_XE')")
    global PROMO_ID
    PROMO_ID = ident("PROMO")
    db("INSERT INTO khuyen_mai(ma_khuyen_mai,ten_khuyen_mai,loai_giam,gia_tri_giam,don_toi_thieu,so_luong,"
       "so_luong_da_dung,ngay_bat_dau,ngay_ket_thuc,trang_thai) VALUES ("
       + ",".join((sql_literal(PROMO_ID), sql_literal("T16 exact preview"), "'PHAN_TRAM'", "12.50", "0", "9", "0",
                   "LOCALTIMESTAMP - INTERVAL '1 day'", "LOCALTIMESTAMP + INTERVAL '5 days'", "'HOAT_DONG'")) + ")")
    SEAT_IDS.extend(ident("S" + str(index)) for index in range(1, 7))


def order_path(order):
    return "/booking/" + order


def hold(actor, trip, seats, key=None, expected=201):
    status, result, _ = exchange(actor, "POST", "/booking/hold", {"maChuyen": trip, "danhSachMaGhe": seats},
                                 key=key or (idem_key("hold-") + uuid.uuid4().hex))
    allowed = expected if isinstance(expected, tuple) else (expected,)
    assert status in allowed, ("POST", "/booking/hold", allowed, status, result)
    if status == 201:
        assert set(result) == {"maDon", "maChuyen", "trangThaiDon", "serverTime", "thoiDiemBatDau",
                              "thoiDiemHetHan", "danhSachGheGiu", "danhSachGiuCho"}, result
    track(result)
    return result


def auth_csrf_and_required_idempotency():
    call(None, "GET", "/booking/" + ident("MISSING"), expected=401, anonymous=True)
    call(None, "POST", "/booking/hold", {"maChuyen": TRIP_IDS[0], "danhSachMaGhe": [SEAT_IDS[0]]},
         expected=401, key=idem_key("anonymous"), anonymous=True)
    missing_csrf = call(CUSTOMER_A, "POST", "/booking/hold",
                        {"maChuyen": TRIP_IDS[0], "danhSachMaGhe": [SEAT_IDS[0]]},
                        expected=403, key=idem_key("no-csrf"), csrf=False)
    assert missing_csrf["code"] == "FORBIDDEN", missing_csrf
    call(CUSTOMER_A, "POST", "/booking/hold",
         {"maChuyen": TRIP_IDS[0], "danhSachMaGhe": [SEAT_IDS[0]]}, expected=400)
    db_assert("NOT EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_nguoi_dung="
              + sql_literal(CUSTOMER_A.user_id) + ")", "Unauthorized/CSRF/missing-key hold created an order")


def rejects_non_customer_role():
    db("UPDATE nguoi_dung SET loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung="
       + sql_literal(CUSTOMER_C.user_id))
    try:
        rejected = call(CUSTOMER_C, "GET", order_path(ident("ROLE_CHECK_MISSING")), expected=403)
        assert rejected["code"] == "FORBIDDEN", rejected
    finally:
        db("UPDATE nguoi_dung SET loai_tai_khoan='KHACH_HANG' WHERE ma_nguoi_dung="
           + sql_literal(CUSTOMER_C.user_id))


def concurrent_seat_winner_and_owner_scope():
    barrier = threading.Barrier(2)

    def request(client):
        barrier.wait(timeout=10)
        return exchange(client, "POST", "/booking/hold",
                        {"maChuyen": TRIP_IDS[0], "danhSachMaGhe": [SEAT_IDS[0]]},
                        key=idem_key("race-" + client.user_id))[0:2]

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(request, (CUSTOMER_A, CUSTOMER_B)))
    winners = [(client, body) for client, (status, body) in zip((CUSTOMER_A, CUSTOMER_B), responses) if status == 201]
    losers = [client for client, (status, _) in zip((CUSTOMER_A, CUSTOMER_B), responses) if status == 409]
    assert len(winners) == len(losers) == 1, [(status, body) for status, body in responses]
    global RACE_WINNER, RACE_LOSER
    RACE_WINNER, result = winners[0]
    RACE_LOSER = losers[0]
    track(result)
    assert result["maChuyen"] == TRIP_IDS[0] and result["danhSachGheGiu"] == [SEAT_IDS[0]]
    db_assert("(SELECT count(*) FROM giu_cho WHERE ma_chuyen=" + sql_literal(TRIP_IDS[0])
              + " AND ma_ghe=" + sql_literal(SEAT_IDS[0])
              + " AND trang_thai='DANG_GIU' AND thoi_diem_het_han > LOCALTIMESTAMP) = 1",
              "Concurrent hold did not leave exactly one active owner")
    call(RACE_LOSER, "GET", order_path(result["maDon"]), expected=403)
    call(RACE_WINNER, "GET", order_path(result["maDon"]), expected=200)
    conflict = hold(RACE_LOSER, TRIP_IDS[0], [SEAT_IDS[0], SEAT_IDS[2]], expected=409)
    assert conflict["code"] == "SEAT_UNAVAILABLE", conflict
    db_assert("NOT EXISTS (SELECT 1 FROM giu_cho WHERE ma_chuyen=" + sql_literal(TRIP_IDS[0])
              + " AND ma_ghe=" + sql_literal(SEAT_IDS[2]) + " AND trang_thai='DANG_GIU')",
              "Failed multi-seat hold left a partial reservation")


def concurrent_idempotent_hold_and_key_reuse():
    global CUSTOMER_C_ORDER_ID
    key = idem_key("same-key-" + uuid.uuid4().hex)
    barrier = threading.Barrier(2)

    def request():
        barrier.wait(timeout=10)
        return exchange(CUSTOMER_C, "POST", "/booking/hold",
                        {"maChuyen": TRIP_IDS[1], "danhSachMaGhe": [SEAT_IDS[0]]}, key=key)

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda _: request(), range(2)))
    successes = [result for status, result, _ in responses if status == 201]
    in_progress = [result for status, result, _ in responses if status == 409]
    assert len(successes) >= 1 and len(successes) + len(in_progress) == 2, [(s, b) for s, b, _ in responses]
    assert all(result.get("code") == "REQUEST_IN_PROGRESS" for result in in_progress), in_progress
    first = successes[0]
    track(first)
    CUSTOMER_C_ORDER_ID = first["maDon"]
    replay = call(CUSTOMER_C, "POST", "/booking/hold",
                 {"maChuyen": TRIP_IDS[1], "danhSachMaGhe": [SEAT_IDS[0]]}, expected=201, key=key)
    assert replay["maDon"] == CUSTOMER_C_ORDER_ID
    assert all(result["maDon"] == CUSTOMER_C_ORDER_ID for result in successes)
    reused = call(CUSTOMER_C, "POST", "/booking/hold",
                  {"maChuyen": TRIP_IDS[1], "danhSachMaGhe": [SEAT_IDS[1]]}, expected=409, key=key)
    assert reused["code"] == "IDEMPOTENCY_KEY_REUSED", reused
    db_assert("(SELECT count(*) FROM don_dat_ve d JOIN giu_cho h ON h.ma_don=d.ma_don WHERE d.ma_nguoi_dung="
              + sql_literal(CUSTOMER_C.user_id) + " AND h.ma_chuyen=" + sql_literal(TRIP_IDS[1])
              + " AND h.ma_ghe=" + sql_literal(SEAT_IDS[0]) + " AND d.trang_thai <> 'DA_HUY') = 1",
              "Concurrent idempotent hold created more than one active order/hold")
    db_assert("NOT EXISTS (SELECT 1 FROM giu_cho WHERE ma_chuyen=" + sql_literal(TRIP_IDS[1])
              + " AND ma_ghe=" + sql_literal(SEAT_IDS[1]) + " AND trang_thai='DANG_GIU')",
              "Changed body under a reused key created another hold")


def session_passengers_contact_and_seat_mutations():
    order = hold(CUSTOMER_A, TRIP_IDS[1], [SEAT_IDS[1], SEAT_IDS[2]])
    order_id = order["maDon"]
    other = hold(CUSTOMER_B, TRIP_IDS[1], [SEAT_IDS[3]])
    other_id = other["maDon"]
    initial = call(CUSTOMER_A, "GET", order_path(order_id))
    assert initial["danhSachVe"] == [] and initial["hanhKhach"] == []

    invalid = call(CUSTOMER_A, "PUT", order_path(order_id) + "/hanh-khach", {"hanhKhach": [
        {"maGhe": SEAT_IDS[1], "hoTen": "Valid name", "soDienThoai": "0900000001"},
        {"maGhe": SEAT_IDS[2], "hoTen": "Missing phone"},
    ]}, expected=400)
    assert invalid["code"] == "VALIDATION_ERROR", invalid
    assert call(CUSTOMER_A, "GET", order_path(order_id))["hanhKhach"] == []

    passengers = {"hanhKhach": [
        {"maGhe": SEAT_IDS[1], "hoTen": "Passenger A", "soDienThoai": "0900000001"},
        {"maGhe": SEAT_IDS[2], "hoTen": "Passenger B", "soDienThoai": "0900000002", "cccd": None},
    ]}
    saved = call(CUSTOMER_A, "PUT", order_path(order_id) + "/hanh-khach", passengers)
    normalized = saved["hanhKhach"]
    assert {item["maGhe"] for item in normalized} == {SEAT_IDS[1], SEAT_IDS[2]}
    assert saved["danhSachVe"] == [] and saved["coTheChotGia"] is True, saved

    invalid_replacement = call(CUSTOMER_A, "PUT", order_path(order_id) + "/hanh-khach", {"hanhKhach": [
        {"maGhe": SEAT_IDS[1], "hoTen": "Only one seat", "soDienThoai": "0900000001"}
    ]}, expected=400)
    assert invalid_replacement["code"] == "VALIDATION_ERROR", invalid_replacement
    assert call(CUSTOMER_A, "GET", order_path(order_id))["hanhKhach"] == normalized

    # A different authenticated account reuses the same JSESSIONID; its snapshot is isolated.
    customer_b_shared_session = SessionView(CUSTOMER_B, session_source=CUSTOMER_A)
    call(customer_b_shared_session, "GET", order_path(order_id), expected=403)
    b_saved = call(customer_b_shared_session, "PUT", order_path(other_id) + "/hanh-khach", {"hanhKhach": [
        {"maGhe": SEAT_IDS[3], "hoTen": "Customer B", "soDienThoai": "0900000003"}
    ]})
    assert b_saved["hanhKhach"][0]["hoTen"] == "Customer B" and b_saved["danhSachVe"] == []
    assert call(CUSTOMER_A, "GET", order_path(order_id))["hanhKhach"] == normalized
    assert call(customer_b_shared_session, "GET", order_path(other_id))["hanhKhach"] == b_saved["hanhKhach"]

    # New JSESSIONID, same auth cookie: order/holds load from PostgreSQL, passenger data does not.
    fresh_a_session = SessionView(CUSTOMER_A)
    assert not any(cookie.name == "JSESSIONID" for cookie in fresh_a_session.jar)
    before_count = len(ORDER_IDS)
    fresh = call(fresh_a_session, "GET", order_path(order_id))
    assert fresh["maDon"] == order_id and fresh["danhSachGiuCho"] and fresh["hanhKhach"] == []
    assert fresh["coTheChotGia"] is False and fresh["danhSachVe"] == []
    assert len(ORDER_IDS) == before_count

    # PATCH idempotency stores a redacted DB snapshot. The session cache may not exist after a crash.
    patch_key = idem_key("contact-" + uuid.uuid4().hex)
    patch_body = {"khachDatTen": "Contact T16", "khachDatSoDienThoai": "0901234567", "ghiChu": "smoke"}
    first = call(CUSTOMER_A, "PATCH", order_path(order_id), patch_body, key=patch_key)
    same_session_replay = call(CUSTOMER_A, "PATCH", order_path(order_id), patch_body, key=patch_key)
    assert same_session_replay["khachDatTen"] == first["khachDatTen"]
    assert same_session_replay["khachDatSoDienThoai"] == first["khachDatSoDienThoai"]
    assert same_session_replay["hanhKhach"] in (normalized, [])
    if same_session_replay["hanhKhach"] == []:
        assert same_session_replay["coTheChotGia"] is False
    reused = call(CUSTOMER_A, "PATCH", order_path(order_id), patch_body | {"ghiChu": "changed"},
                  expected=409, key=patch_key)
    assert reused["code"] == "IDEMPOTENCY_KEY_REUSED", reused
    db_assert("EXISTS (SELECT 1 FROM api_idempotency WHERE user_id=" + sql_literal(CUSTOMER_A.user_id)
              + " AND method='PATCH' AND path=" + sql_literal("/api/booking/" + order_id)
              + " AND request_key=" + sql_literal(patch_key)
              + " AND (response_body::jsonb->'hanhKhach') = '[]'::jsonb"
              + " AND (response_body::jsonb->'coTheChotGia') = 'false'::jsonb"
              + " AND position('Passenger A' in response_body) = 0)",
              "Idempotency response persisted temporary passenger data")
    lost_session_replay = call(fresh_a_session, "PATCH", order_path(order_id), patch_body, key=patch_key)
    assert lost_session_replay["hanhKhach"] == [] and lost_session_replay["coTheChotGia"] is False
    assert call(CUSTOMER_A, "GET", order_path(order_id))["hanhKhach"] == normalized

    # Failed seat swap is atomic; successful swap moves the matching passenger without changing details.
    original_seats = {item["maGhe"] for item in call(CUSTOMER_A, "GET", order_path(order_id))["danhSachGiuCho"]}
    blocked_swap = call(CUSTOMER_A, "PUT", order_path(order_id) + "/doi-ghe",
                        {"maGheCu": SEAT_IDS[1], "maGheMoi": SEAT_IDS[3]}, expected=409)
    assert blocked_swap["code"] in {"SEAT_UNAVAILABLE", "INVALID_STATE_TRANSITION"}, blocked_swap
    unchanged = call(CUSTOMER_A, "GET", order_path(order_id))
    assert {item["maGhe"] for item in unchanged["danhSachGiuCho"]} == original_seats
    assert unchanged["hanhKhach"] == normalized
    moved = call(CUSTOMER_A, "PUT", order_path(order_id) + "/doi-ghe",
                 {"maGheCu": SEAT_IDS[1], "maGheMoi": SEAT_IDS[4]})
    moved_passenger = next(item for item in moved["hanhKhach"] if item["maGhe"] == SEAT_IDS[4])
    assert moved_passenger["hoTen"] == "Passenger A" and moved_passenger["soDienThoai"] == "0900000001"
    assert moved["danhSachVe"] == []
    dropped = call(CUSTOMER_A, "DELETE", order_path(order_id) + "/ghe/" + SEAT_IDS[2])
    assert all(item["maGhe"] != SEAT_IDS[2] for item in dropped["hanhKhach"])
    last = call(CUSTOMER_A, "DELETE", order_path(order_id) + "/ghe/" + SEAT_IDS[4])
    assert last["trangThaiDon"] == "DA_HUY" and last["hanhKhach"] == []
    db_assert("(SELECT count(*) FROM ve_xe WHERE ma_don IN (" + sql_literal(order_id) + "," + sql_literal(other_id) + ")) = 0",
              "Passenger flow created a ve_xe row before issuance")
    rebook = hold(CUSTOMER_A, TRIP_IDS[1], [SEAT_IDS[1]])
    assert rebook["maDon"] != order_id
    rebook_view = call(CUSTOMER_A, "GET", order_path(rebook["maDon"]))
    assert rebook_view["hanhKhach"] == [] and rebook_view["danhSachVe"] == []


def expire_without_wait_and_rebook():
    original_key = idem_key("expiring-hold")
    order = hold(RACE_LOSER, TRIP_IDS[0], [SEAT_IDS[1]], key=original_key)
    order_id = order["maDon"]
    saved = call(RACE_LOSER, "PUT", order_path(order_id) + "/hanh-khach", {"hanhKhach": [
        {"maGhe": SEAT_IDS[1], "hoTen": "Expiring passenger", "soDienThoai": "0905550001"}
    ]})
    hold_id = saved["danhSachGiuCho"][0]["maGiuCho"]
    db("UPDATE giu_cho SET ngay_dat=LOCALTIMESTAMP-INTERVAL '15 minutes 1 second', "
       "thoi_diem_bat_dau=LOCALTIMESTAMP-INTERVAL '15 minutes 1 second', "
       "thoi_diem_het_han=LOCALTIMESTAMP-INTERVAL '1 second' WHERE ma_giu_cho=" + sql_literal(hold_id))
    status, expired, _ = exchange(RACE_LOSER, "GET", order_path(order_id))
    assert status == 409 and expired["code"] == "HOLD_EXPIRED", expired
    replay = hold(RACE_LOSER, TRIP_IDS[0], [SEAT_IDS[1]], key=original_key)
    assert replay["maDon"] == order_id and replay["thoiDiemHetHan"] == order["thoiDiemHetHan"]
    db_assert("EXISTS (SELECT 1 FROM giu_cho WHERE ma_giu_cho=" + sql_literal(hold_id)
              + " AND trang_thai='HET_HAN') AND EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don="
              + sql_literal(order_id) + " AND trang_thai='HET_HAN')"
              + " AND NOT EXISTS (SELECT 1 FROM ve_xe WHERE ma_don=" + sql_literal(order_id) + ")",
              "Expiry was not committed or created a ticket draft")
    rebook = hold(CUSTOMER_C, TRIP_IDS[0], [SEAT_IDS[1]])
    assert rebook["maDon"] != order_id
    db_assert("EXISTS (SELECT 1 FROM giu_cho WHERE ma_don=" + sql_literal(rebook["maDon"])
              + " AND ma_ghe=" + sql_literal(SEAT_IDS[1]) + " AND trang_thai='DANG_GIU')",
              "Seat from an expired booking was not available for rebooking")


def cancel_is_idempotent_and_clears_session():
    order = hold(CUSTOMER_B, TRIP_IDS[2], [SEAT_IDS[0]])
    order_id = order["maDon"]
    saved = call(CUSTOMER_B, "PUT", order_path(order_id) + "/hanh-khach", {"hanhKhach": [
        {"maGhe": SEAT_IDS[0], "hoTen": "Cancel passenger", "soDienThoai": "0903330001"}
    ]})
    assert saved["hanhKhach"] and saved["danhSachVe"] == []
    key = idem_key("cancel-" + uuid.uuid4().hex)
    first = call(CUSTOMER_B, "POST", order_path(order_id) + "/huy", {}, key=key)
    second = call(CUSTOMER_B, "POST", order_path(order_id) + "/huy", {}, key=key)
    no_body = call(CUSTOMER_B, "POST", order_path(order_id) + "/huy", key=idem_key("cancel-no-body"))
    assert no_body["trangThaiDon"] == "DA_HUY" and no_body["hanhKhach"] == []
    assert first["trangThaiDon"] == second["trangThaiDon"] == "DA_HUY"
    assert second["hanhKhach"] == [] and second["danhSachVe"] == [] and second["coTheChotGia"] is False
    db_assert("(SELECT count(*) FROM don_dat_ve WHERE ma_don=" + sql_literal(order_id)
              + " AND trang_thai='DA_HUY') = 1 AND NOT EXISTS (SELECT 1 FROM giu_cho WHERE ma_don="
              + sql_literal(order_id) + " AND trang_thai='DANG_GIU') AND NOT EXISTS (SELECT 1 FROM ve_xe WHERE ma_don="
              + sql_literal(order_id) + ")", "Repeated cancellation failed to clear/release only this draft")


def promotion_preview_and_revalidation():
    order = hold(CUSTOMER_A, TRIP_IDS[2], [SEAT_IDS[4], SEAT_IDS[5]])
    order_id = order["maDon"]
    selected = call(CUSTOMER_A, "PUT", order_path(order_id) + "/khuyen-mai", {"maKhuyenMai": PROMO_ID})
    preview = selected["giaTamTinh"]
    assert preview["tongTienGoc"] == "200002" and Decimal(preview["tongTienGoc"]) == Decimal("200002"), preview
    assert preview["tienGiam"] == "25000.25" and Decimal(preview["tienGiam"]) == Decimal("25000.25"), preview
    assert preview["tongThanhToan"] == "175001.75" and Decimal(preview["tongThanhToan"]) == Decimal("175001.75"), preview
    assert preview["maKhuyenMaiDaChon"] == PROMO_ID and preview["maKhuyenMaiApDung"] == PROMO_ID
    db_assert("(SELECT so_luong_da_dung FROM khuyen_mai WHERE ma_khuyen_mai="
              + sql_literal(PROMO_ID) + ") = 0", "Preview reserved promotion quota")
    db(f"UPDATE khuyen_mai SET so_luong_da_dung=so_luong WHERE ma_khuyen_mai={sql_literal(PROMO_ID)}")
    invalid_preview = call(CUSTOMER_A, "GET", order_path(order_id))["giaTamTinh"]
    assert invalid_preview["maKhuyenMaiDaChon"] == PROMO_ID
    assert invalid_preview["maKhuyenMaiApDung"] is None and invalid_preview["lyDoKhongApDung"], invalid_preview
    db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + sql_literal(order_id)
              + " AND ma_khuyen_mai=" + sql_literal(PROMO_ID) + ")",
              "GET cleared an invalid selected promotion from stored order state")
    changed = call(CUSTOMER_A, "DELETE", order_path(order_id) + "/ghe/" + SEAT_IDS[4])
    after_change = changed["giaTamTinh"]
    assert after_change["maKhuyenMaiDaChon"] is None and Decimal(after_change["tienGiam"]) == 0
    assert Decimal(after_change["tongTienGoc"]) == Decimal("100001")
    db_assert("(SELECT ma_khuyen_mai IS NULL FROM don_dat_ve WHERE ma_don="
              + sql_literal(order_id) + ")", "Seat mutation did not drop an invalid selected promotion")


def assert_no_ticket_rows_before_issuance():
    users = ",".join(sql_literal(client.user_id) for client in CLIENTS)
    db_assert("NOT EXISTS (SELECT 1 FROM ve_xe v JOIN don_dat_ve d ON d.ma_don=v.ma_don WHERE d.ma_nguoi_dung IN ("
              + users + "))", "T16 created ve_xe rows before ticket issuance")


def paid_order_is_readable_but_immutable():
    order_id, ticket_id = ident("PAID_ORDER"), ident("PAID_TICKET")
    hold_id = ident("PAID_HOLD")
    db("INSERT INTO don_dat_ve(ma_don,ma_nguoi_dung,ngay_dat,tong_tien,tien_giam,khach_dat_ten,"
       "khach_dat_so_dien_thoai,kenh_dat,trang_thai,ghi_chu) VALUES ("
       + ",".join((sql_literal(order_id), sql_literal(CUSTOMER_C.user_id), "LOCALTIMESTAMP", "100001", "0",
                   "'Paid T16'", "'0900000003'", "'WEB'", "'DA_THANH_TOAN'", "NULL")) + ")")
    db("INSERT INTO giu_cho(ma_giu_cho,ma_chuyen,ma_ghe,ma_don,ngay_dat,thoi_diem_bat_dau,"
       "thoi_diem_het_han,trang_thai) VALUES ("
       + ",".join((sql_literal(hold_id), sql_literal(TRIP_IDS[0]), sql_literal(SEAT_IDS[5]), sql_literal(order_id),
                   "LOCALTIMESTAMP-INTERVAL '2 hours'", "LOCALTIMESTAMP-INTERVAL '2 hours'",
                   "LOCALTIMESTAMP-INTERVAL '1 hour'", "'DA_XAC_NHAN'")) + ")")
    db("INSERT INTO ve_xe(ma_ve,ma_chuyen,ma_ghe,ma_don,ho_ten_khach_hang,so_dien_thoai_khach_hang,"
       "gia_ve,ngay_xuat_ve,trang_thai) VALUES ("
       + ",".join((sql_literal(ticket_id), sql_literal(TRIP_IDS[0]), sql_literal(SEAT_IDS[5]), sql_literal(order_id),
                   "'Paid T16'", "'0900000003'", "100001", "LOCALTIMESTAMP", "'DA_XUAT_VE'")) + ")")
    ORDER_IDS.add(order_id)
    view = call(CUSTOMER_C, "GET", order_path(order_id))
    assert view["trangThaiDon"] == "DA_THANH_TOAN" and view["hanhKhach"] == []
    assert any(item["maVe"] == ticket_id and item["ngayXuatVe"] for item in view["danhSachVe"]), view
    for method, path, body in (
        ("PATCH", order_path(order_id), {"ghiChu": "must not change"}),
        ("DELETE", order_path(order_id) + "/ghe/" + SEAT_IDS[5], None),
        ("POST", order_path(order_id) + "/huy", {}),
    ):
        call(CUSTOMER_C, method, path, body, expected=409,
             key=idem_key("paid-" + uuid.uuid4().hex) if method in {"PATCH", "POST"} else None)
    db_assert("EXISTS (SELECT 1 FROM don_dat_ve WHERE ma_don=" + sql_literal(order_id)
              + " AND trang_thai='DA_THANH_TOAN' AND ghi_chu IS NULL) AND EXISTS (SELECT 1 FROM ve_xe WHERE ma_ve="
              + sql_literal(ticket_id) + " AND trang_thai='DA_XUAT_VE')",
              "Rejected paid-order mutation changed the paid order or issued ticket")


def cleanup_owned_data():
    user_ids = [client.user_id for client in CLIENTS if client.user_id]
    if not user_ids:
        return
    users = ",".join(sql_literal(user_id) for user_id in user_ids)
    # Customers are newly registered for this run. Their generated order IDs are UUIDs, not PREFIX IDs.
    owned_orders = "SELECT ma_don FROM don_dat_ve WHERE ma_nguoi_dung IN (" + users + ")"
    db(f"DELETE FROM refund_request WHERE ma_don IN ({owned_orders})")
    db(f"DELETE FROM payment_gateway_metadata WHERE ma_giao_dich IN (SELECT ma_giao_dich FROM thanh_toan WHERE ma_don IN ({owned_orders}))")
    db(f"DELETE FROM api_idempotency WHERE user_id IN ({users})")
    db(f"DELETE FROM ve_xe WHERE ma_don IN ({owned_orders})")
    db(f"DELETE FROM giu_cho WHERE ma_don IN ({owned_orders})")
    db(f"DELETE FROM thanh_toan WHERE ma_don IN ({owned_orders})")
    db(f"DELETE FROM don_dat_ve WHERE ma_don IN ({owned_orders})")
    if PROMO_ID:
        db(f"DELETE FROM khuyen_mai WHERE ma_khuyen_mai={sql_literal(PROMO_ID)}")
    if TRIP_IDS:
        trips = ",".join(sql_literal(trip) for trip in TRIP_IDS)
        db(f"DELETE FROM phan_cong_chuyen_xe WHERE ma_chuyen IN ({trips})")
        db(f"DELETE FROM chuyen_xe WHERE ma_chuyen IN ({trips})")
    if STAFF_IDS:
        staff = ",".join(sql_literal(staff_id) for staff_id in STAFF_IDS)
        db(f"DELETE FROM nhan_vien WHERE ma_nhan_vien IN ({staff})")
    db(f"DELETE FROM tuyen_xe WHERE ma_tuyen={sql_literal(ident('ROUTE'))}")
    db(f"DELETE FROM tuyen_xe WHERE ma_tuyen={sql_literal(ident('RETURN_ROUTE'))}")
    db(f"DELETE FROM ghe WHERE ma_xe={sql_literal(ident('BUS'))}")
    db(f"DELETE FROM xe WHERE ma_xe={sql_literal(ident('BUS'))}")
    db(f"DELETE FROM loai_xe WHERE ma_loai_xe={sql_literal(ident('TYPE'))}")
    db(f"DELETE FROM nha_xe WHERE ma_nha_xe={sql_literal(ident('OP'))}")
    db(f"DELETE FROM ben_xe WHERE ma_ben_xe IN ({sql_literal(ident('ST_A'))},{sql_literal(ident('ST_B'))})")
    db(f"DELETE FROM tinh_thanh WHERE ma_tinh_thanh={sql_literal(ident('CITY'))}")


def main():
    error = None
    try:
        scenario("real backend/PostgreSQL health, migrations 004/005, KH accounts and fixtures",
                 lambda: (require_stack_migration_and_customers(), seed_owned_trip_fixtures()))
        scenario("anonymous access, KH role, CSRF and required Idempotency-Key", auth_csrf_and_required_idempotency)
        scenario("two customers race for one seat; owner check and one winner", concurrent_seat_winner_and_owner_scope)
        scenario("ADMIN role is rejected by booking APIs and test account is restored", rejects_non_customer_role)
        scenario("concurrent same-key retry, body replay and changed-body rejection", concurrent_idempotent_hold_and_key_reuse)
        scenario("session passenger validation, isolation, idempotency privacy, swap/drop and rebook",
                 session_passengers_contact_and_seat_mutations)
        scenario("expiry clears session, commits terminal state, and permits rebooking", expire_without_wait_and_rebook)
        scenario("cancel replay is idempotent and clears session passenger data", cancel_is_idempotent_and_clears_session)
        scenario("exact preview, no quota use, stale GET and seat-change revalidation", promotion_preview_and_revalidation)
        scenario("database contains no ticket rows before issuance", assert_no_ticket_rows_before_issuance)
        scenario("paid order remains readable and all booking mutations are blocked", paid_order_is_readable_but_immutable)
    except Exception as error_value:
        error = error_value
    finally:
        try:
            cleanup_owned_data()
        except Exception as cleanup_error:
            RESULTS.append(("FAIL", "cleanup owned fixtures"))
            print("FAIL: cleanup owned fixtures — " + repr(cleanup_error))
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
    statuses = ", ".join(f"HTTP {status}: {count}" for status, count in sorted(STATUS_COUNTS.items()))
    print(f"RESULT: {pass_count} PASS, {fail_count} FAIL, {HTTP_ASSERTIONS} HTTP assertions ({statuses})")
    return 1 if error or fail_count else 0


if __name__ == "__main__":
    raise SystemExit(main())
