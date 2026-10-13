"""Real HTTP/PostgreSQL verification. Requires a running development Compose stack.

Run from the repository root: python3 tests/backend/api_smoke.py
Only fixtures with this run's random prefix are inserted/deleted.
"""
import json
import os
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.environ.get("API_BASE_URL", "http://localhost:8080/api").rstrip("/")
DB_CONTAINER = os.environ.get("DB_CONTAINER", "datvexe-postgres")
PREFIX = "qa_" + uuid.uuid4().hex[:10] + "_"
requests_checked = 0


def ident(name):
    return PREFIX + name


def sql(statement):
    subprocess.run([
        "docker", "exec", "-i", DB_CONTAINER, "psql", "-U", "datvexe_user",
        "-d", "datvexe", "-v", "ON_ERROR_STOP=1", "-c", statement,
    ], check=True, stdout=subprocess.DEVNULL)


def call(method, path, data=None, expected=200, raw=None):
    global requests_checked
    payload = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    request = urllib.request.Request(BASE + path, data=payload, method=method,
                                    headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status, body = response.status, response.read()
            content_type = response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        status, body = error.code, error.read()
        content_type = error.headers.get("Content-Type", "")
    assert status == expected, (method, path, status, expected, body[:800])
    if status == 204:
        assert body == b"", body
        result = None
    else:
        assert "application/json" in content_type, (path, content_type)
        result = json.loads(body)
        if status >= 400:
            assert isinstance(result.get("error"), str) and result["error"], result
    requests_checked += 1
    return result


def station(name, city="CITY_A"):
    return {"maBenXe": ident(name), "tenBenXe": name, "diaChi": "Địa chỉ thử",
            "maTinhThanh": ident(city)}


def route(name="R"):
    return {"maTuyen": ident(name), "tenTuyen": "Tuyến thử", "tramKhoiHanh": ident("A"),
            "tramDen": ident("B"), "thoiGianKhoiHanh": "08:00:00", "thoiGianDuKien": 6.5,
            "giaCoBan": 250000, "trangThai": "DANG_KHAI_THAC"}


def search(date="2030-01-01", origin="A", destination="B"):
    params = urllib.parse.urlencode({"from": ident(origin), "to": ident(destination), "date": date})
    return call("GET", "/chuyen-xe?" + params)


def setup():
    for name in ("CITY_A", "CITY_B"):
        call("POST", "/tinh-thanh", {"maTinhThanh": ident(name), "tenTinhThanh": name}, 201)
    call("POST", "/ben-xe", station("A"), 201)
    call("POST", "/ben-xe", station("B", "CITY_B"), 201)
    sql(f"INSERT INTO xe (ma_xe,bien_so,so_cho_ngoi,trang_thai) VALUES "
        f"('{ident('X')}','{ident('X')}',40,'SAN_SANG'),"
        f"('{ident('BROKEN')}','{ident('BROKEN')}',40,'BAO_TRI')")


def crud():
    call("POST", "/ben-xe", station("FREE"), 201)
    updated = station("FREE") | {"tenBenXe": "Đổi tên"}
    assert call("PUT", "/ben-xe/" + ident("FREE"), updated)["tenBenXe"] == "Đổi tên"
    assert any(x["maBenXe"] == ident("FREE") for x in call("GET", "/ben-xe"))
    call("DELETE", "/ben-xe/" + ident("FREE"), expected=204)
    call("GET", "/ben-xe/" + ident("FREE"), expected=404)
    call("POST", "/tuyen-xe", route(), 201)
    call("POST", "/tuyen-xe", route("FREE_R"), 201)
    call("DELETE", "/tuyen-xe/" + ident("FREE_R"), expected=204)
    call("GET", "/tuyen-xe/" + ident("FREE_R"), expected=404)
    update = route() | {"tenTuyen": "Tuyến mới", "giaCoBan": 260000}
    assert call("PUT", "/tuyen-xe/" + ident("R"), update)["giaCoBan"] == 260000
    assert any(x["maTuyen"] == ident("R") for x in call("GET", "/tuyen-xe"))
    endpoint = "/tuyen-xe/" + ident("R") + "/tram-dau-cuoi"
    call("PUT", endpoint, {"tramKhoiHanh": ident("B"), "tramDen": ident("A")})
    assert call("GET", "/tuyen-xe/" + ident("R"))["tramKhoiHanh"] == ident("B")
    call("PUT", endpoint, {"tramKhoiHanh": ident("A"), "tramDen": ident("B")})
    call("PUT", endpoint, {"tramKhoiHanh": ident("A"), "tramDen": ident("A")}, 400)
    call("DELETE", "/ben-xe/" + ident("A"), expected=409)
    print("PASS: station/route CRUD and endpoints")


def stops():
    path = "/tuyen-xe/" + ident("R") + "/diem-dung"
    for name, position in (("S1", 1), ("S3", 2), ("S2", 2)):
        call("POST", path, {"maDiemDung": ident(name), "tenDiemDung": name, "diaChi": "X",
                           "thuTu": position, "thoiGianDung": 5, "loaiDiemDung": "CA_HAI"}, 201)
    items = call("GET", "/tuyen-xe/" + ident("R"))["tramTrungGian"]
    assert [x["maDiemDung"] for x in items] == [ident(x) for x in ("S1", "S2", "S3")]
    result = call("PUT", path + "/sap-xep", {"maDiemDungTheoThuTu": [ident(x) for x in ("S3", "S2", "S1")]})
    assert [x["thuTu"] for x in result] == [1, 2, 3]
    call("PUT", path + "/sap-xep", {"maDiemDungTheoThuTu": [ident("S1")]}, 400)
    call("PUT", path + "/sap-xep", {"maDiemDungTheoThuTu": [ident("S1")] * 3}, 400)
    call("DELETE", path + "/" + ident("S2"), expected=204)
    items = call("GET", "/tuyen-xe/" + ident("R"))["tramTrungGian"]
    assert [x["maDiemDung"] for x in items] == [ident("S3"), ident("S1")]
    assert [x["thuTu"] for x in items] == [1, 2]
    call("POST", path, {"maDiemDung": ident("BAD"), "tenDiemDung": "Test", "diaChi": "X", "thuTu": 0}, 400)
    print("PASS: stop insert/reorder/delete and invalid lists")


def trips():
    assert search() == []
    for name, time in (("C1", "08:00:00"), ("C2", "09:00:00"), ("NIGHT", "22:00:00")):
        trip = call("POST", "/chuyen-xe", {"maChuyen": ident(name), "maTuyen": ident("R"),
                    "ngayKhoiHanh": "2030-01-01", "gioKhoiHanh": time}, 201)
        assert trip["ngayKhoiHanh"] == "2030-01-01" and trip["gioKhoiHanh"] == time
    assert call("GET", "/chuyen-xe/" + ident("NIGHT"))["gioDenDuKien"] == "2030-01-02T04:30:00"
    def assign(name, vehicle, status=200):
        return call("PUT", "/chuyen-xe/" + ident(name) + "/xe", {"maXe": ident(vehicle)}, status)
    assign("C1", "UNKNOWN", 404)
    assign("C1", "BROKEN", 409)
    assert assign("C1", "X")["maXe"] == ident("X")
    assign("C2", "X", 409)
    assert call("GET", "/chuyen-xe/" + ident("C2"))["maXe"] is None
    call("PUT", "/chuyen-xe/" + ident("C2"), {"ngayKhoiHanh": "2030-01-01", "gioKhoiHanh": "14:30:00"})
    assign("C2", "X")
    call("PUT", "/chuyen-xe/" + ident("C2"), {"ngayKhoiHanh": "2030-01-01", "gioKhoiHanh": "12:00:00"}, 409)
    assert call("GET", "/chuyen-xe/" + ident("C2"))["gioKhoiHanh"] == "14:30:00"
    call("PUT", "/chuyen-xe/" + ident("C2"), {"ngayKhoiHanh": "2030-01-02", "gioKhoiHanh": "08:00:00"})
    call("DELETE", "/tuyen-xe/" + ident("R"), expected=409)
    print("PASS: trip create, overnight, vehicle assignment/conflicts, reschedule rollback")


def search_and_status():
    assert [x["maChuyen"] for x in search()] == [ident("C1"), ident("NIGHT")]
    assert [x["maChuyen"] for x in search(origin="CITY_A", destination="CITY_B")] == [ident("C1"), ident("NIGHT")]
    assert search("2030-01-03") == [] and search(origin="B", destination="A") == []
    assert search(origin="UNKNOWN") == []
    call("POST", "/chuyen-xe/" + ident("NIGHT") + "/bat-dau", expected=409)
    call("POST", "/chuyen-xe/" + ident("NIGHT") + "/huy")
    assert call("GET", "/chuyen-xe/" + ident("NIGHT") + "/trang-thai")["trangThai"] == "DA_HUY"
    call("PUT", "/chuyen-xe/" + ident("NIGHT"), {"ngayKhoiHanh": "2030-01-01", "gioKhoiHanh": "12:00:00"}, 409)
    assert [x["maChuyen"] for x in search()] == [ident("C1")]
    call("POST", "/chuyen-xe/" + ident("C1") + "/bat-dau")
    call("POST", "/chuyen-xe/" + ident("C1") + "/huy", expected=409)
    call("POST", "/chuyen-xe/" + ident("C1") + "/hoan-thanh")
    assert search() == []
    assert call("GET", "/chuyen-xe/" + ident("C1") + "/trang-thai")["trangThai"] == "HOAN_TAT"
    call("POST", "/chuyen-xe/" + ident("C2") + "/huy")
    assert search("2030-01-02") == []
    print("PASS: search by station/city/date, no trips, cancelled/completed and lifecycle")


def validation():
    call("POST", "/ben-xe", station("A"), 409)
    call("POST", "/ben-xe", station("BAD") | {"tenBenXe": " "}, 400)
    call("POST", "/ben-xe", station("BAD") | {"tenBenXe": "X" * 161}, 400)
    call("POST", "/ben-xe", station("BAD") | {"maTinhThanh": ident("UNKNOWN")}, 404)
    call("POST", "/tuyen-xe", route("BAD") | {"giaCoBan": -1}, 400)
    call("POST", "/chuyen-xe", {"maChuyen": ident("BAD"), "maTuyen": ident("R")}, 400)
    call("GET", "/chuyen-xe?from=x&to=y&date=not-a-date", expected=400)
    call("GET", "/chuyen-xe?from=x&to=y", expected=400)
    call("GET", "/chuyen-xe/" + ident("UNKNOWN"), expected=404)
    call("POST", "/ben-xe", raw=b"null", expected=400)
    call("POST", "/ben-xe", raw=b"{broken", expected=400)
    call("POST", "/ben-xe", station("BAD") | {"trangThai": "INVALID"}, 400)
    print("PASS: validation/404/409 and JSON error responses")


def cleanup():
    for table, key in (("chuyen_xe", "ma_chuyen"), ("diem_dung", "ma_diem_dung"),
                       ("tuyen_xe", "ma_tuyen"), ("xe", "ma_xe"), ("ben_xe", "ma_ben_xe"),
                       ("tinh_thanh", "ma_tinh_thanh")):
        # Prefix contains only ASCII letters, digits and underscores, generated here.
        sql(f"DELETE FROM {table} WHERE left({key}, {len(PREFIX)}) = '{PREFIX}'")


if __name__ == "__main__":
    try:
        setup()
        for scenario in (crud, stops, trips, search_and_status, validation):
            scenario()
        print(f"PASS: 5 API scenarios, {requests_checked} HTTP responses checked")
    finally:
        cleanup()
