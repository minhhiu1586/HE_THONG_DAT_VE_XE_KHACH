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
from auth_fixture import AdminFixture
from prepare_published_trip import publish_trip
AUTH = AdminFixture(os.environ.get("API_BASE_URL", "http://localhost:8080/api").rstrip("/"))

BASE = os.environ.get("API_BASE_URL", "http://localhost:8080/api").rstrip("/")
DB_CONTAINER = os.environ.get("DB_CONTAINER", "datvexe-postgres")
PREFIX = "qa_" + uuid.uuid4().hex[:10] + "_"
requests_checked = 0


def ident(name):
    return PREFIX + name


def sql(statement):
    subprocess.run([
        "docker", "exec", "-i", DB_CONTAINER, "psql", "-U", "datvexe_user",
        "-d", os.environ.get("DB_NAME", "datvexe"), "-v", "ON_ERROR_STOP=1", "-c", statement,
    ], check=True, stdout=subprocess.DEVNULL)


def call(method, path, data=None, expected=200, raw=None):
    global requests_checked
    payload = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    request = urllib.request.Request(BASE + path, data=payload, method=method,
                                    headers=AUTH.headers())
    try:
        with AUTH.opener.open(request, timeout=15) as response:
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
            assert isinstance(result.get("code"), str) and result["code"], result
    requests_checked += 1
    return result


def station(name, city="CITY_A"):
    return {"maBenXe": ident(name), "tenBenXe": name, "diaChi": "Địa chỉ thử",
            "maTinhThanh": ident(city)}


def route(name="R"):
    return {"maTuyen": ident(name), "tenTuyen": "Tuyến thử", "tramKhoiHanh": ident("A"),
            "tramDen": ident("B"), "thoiGianKhoiHanh": "08:00:00", "thoiGianDuKien": 6.5,
            "giaCoBan": 250000, "trangThai": "DANG_KHAI_THAC", "maNhaXe": ident("NX")}


def search(date="2030-01-01", origin="A", destination="B", expected=200):
    params = urllib.parse.urlencode({"from": ident(origin), "to": ident(destination), "date": date})
    return call("GET", "/chuyen-xe?" + params, expected=expected)


def setup():
    for name in ("CITY_A", "CITY_B"):
        call("POST", "/tinh-thanh", {"maTinhThanh": ident(name), "tenTinhThanh": name}, 201)
    call("POST", "/ben-xe", station("A"), 201)
    call("POST", "/ben-xe", station("B", "CITY_B"), 201)
    sql(f"INSERT INTO nha_xe (ma_nha_xe,ten_nha_xe,trang_thai) VALUES ('{ident('NX')}','Test','HOAT_DONG');"
        f"INSERT INTO loai_xe (ma_loai_xe,ten_loai_xe,so_cho_ngoi,so_tang) VALUES ('{ident('LX')}','Test',40,2);"
        f"INSERT INTO xe (ma_xe,bien_so,so_cho_ngoi,trang_thai,ma_loai_xe,ma_nha_xe,ngay_dang_kiem,han_dang_kiem) VALUES "
        f"('{ident('X')}','{ident('X')}',40,'SAN_SANG','{ident('LX')}','{ident('NX')}','2020-01-01','2100-01-01'),"
        f"('{ident('BROKEN')}','{ident('BROKEN')}',40,'BAO_TRI','{ident('LX')}','{ident('NX')}','2020-01-01','2100-01-01');"
        f"INSERT INTO ghe (ma_ghe,vi_tri,tang,trang_thai,ma_xe) SELECT '{ident('G')}'||i,'G'||i,1,'HOAT_DONG','{ident('X')}' FROM generate_series(1,40) i")


def crud():
    city = call("GET", "/tinh-thanh/" + ident("CITY_A"))
    assert city["maTinhThanh"] == ident("CITY_A")
    assert any(x["maTinhThanh"] == ident("CITY_A") for x in call("GET", "/tinh-thanh?q=CITY_A"))
    call("POST", "/tinh-thanh", {"maTinhThanh": ident("FREE_CITY"), "tenTinhThanh": "Tỉnh thử"}, 201)
    assert call("PUT", "/tinh-thanh/" + ident("FREE_CITY"), {"tenTinhThanh": "Tỉnh đã sửa", "khuVuc": "Miền thử"})["tenTinhThanh"] == "Tỉnh đã sửa"
    call("DELETE", "/tinh-thanh/" + ident("FREE_CITY"), expected=204)
    call("POST", "/ben-xe", station("FREE"), 201)
    updated = station("FREE") | {"tenBenXe": "Đổi tên"}
    assert call("PUT", "/ben-xe/" + ident("FREE"), updated)["tenBenXe"] == "Đổi tên"
    assert any(x["maBenXe"] == ident("FREE") for x in call("GET", "/ben-xe?maTinhThanh=" + ident("CITY_A") + "&q=FREE"))
    call("DELETE", "/ben-xe/" + ident("FREE"), expected=204)
    call("GET", "/ben-xe/" + ident("FREE"), expected=404)
    call("POST", "/tuyen-xe", route(), 201)
    call("POST", "/tuyen-xe", route("FREE_R"), 201)
    assert any(x["maTuyen"] == ident("R") for x in call("GET", "/tuyen-xe?tramKhoiHanh=" + ident("A") + "&tramDen=" + ident("B") + "&trangThai=DANG_KHAI_THAC"))
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
    updated = call("PUT", path + "/" + ident("S2"), {"tenDiemDung": "S2 đã sửa", "diaChi": "Địa chỉ mới",
        "thoiGianDung": 10, "loaiDiemDung": "DIEM_TRA"})
    assert updated["tenDiemDung"] == "S2 đã sửa" and updated["thoiGianDung"] == 10
    call("PUT", path + "/" + ident("S2"), {"tenDiemDung": "Sai", "diaChi": "X", "thoiGianDung": -1}, 400)
    result = call("PUT", path + "/sap-xep", {"maDiemDungTheoThuTu": [ident(x) for x in ("S3", "S2", "S1")]})
    assert [x["thuTu"] for x in result] == [1, 2, 3]
    call("PUT", path + "/sap-xep", {"maDiemDungTheoThuTu": [ident("S1")]}, 400)
    call("PUT", path + "/sap-xep", {"maDiemDungTheoThuTu": [ident("S1")] * 3}, 400)
    call("DELETE", path + "/" + ident("S2"), expected=204)
    items = call("GET", "/tuyen-xe/" + ident("R"))["tramTrungGian"]
    assert [x["maDiemDung"] for x in items] == [ident("S3"), ident("S1")]
    assert [x["thuTu"] for x in items] == [1, 2]
    call("POST", path, {"maDiemDung": ident("BAD"), "tenDiemDung": "Test", "diaChi": "X", "thuTu": 0}, 400)
    call("POST", path, {"maDiemDung": ident("BAD_TIME"), "tenDiemDung": "Test", "diaChi": "X", "thoiGianDung": -1}, 400)
    print("PASS: stop insert/reorder/delete and invalid lists")


def trips():
    assert search() == []
    call("POST", "/tuyen-xe", route("RETURN") | {"tramKhoiHanh":ident("B"),"tramDen":ident("A")},201)
    for name, time in (("C1", "08:00:00"), ("C2", "09:00:00"), ("NIGHT", "22:00:00")):
        trip = call("POST", "/chuyen-xe", {"maChuyen": ident(name), "maTuyen": ident("RETURN" if name == "C2" else "R"),
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
    call("PUT", "/chuyen-xe/" + ident("C2"), {"ngayKhoiHanh": "2030-01-01", "gioKhoiHanh": "15:00:00"})
    assign("C2", "X")
    call("PUT", "/chuyen-xe/" + ident("C2"), {"ngayKhoiHanh": "2030-01-01", "gioKhoiHanh": "12:00:00"}, 409)
    assert call("GET", "/chuyen-xe/" + ident("C2"))["gioKhoiHanh"] == "15:00:00"
    call("PUT", "/chuyen-xe/" + ident("C2"), {"ngayKhoiHanh": "2030-01-02", "gioKhoiHanh": "08:00:00"})
    call("DELETE", "/tuyen-xe/" + ident("R"), expected=409)
    call("PUT", "/tuyen-xe/" + ident("R"), route() | {"tenTuyen": "Đổi sau chuyến"}, expected=409)
    call("PUT", "/tuyen-xe/" + ident("R") + "/tram-dau-cuoi", {"tramKhoiHanh": ident("B"), "tramDen": ident("A")}, expected=409)
    call("PUT", "/tuyen-xe/" + ident("R") + "/diem-dung/sap-xep",
         {"maDiemDungTheoThuTu": [ident(x) for x in ("S1", "S2", "S3")]}, expected=409)
    call("PUT", "/tuyen-xe/" + ident("R") + "/diem-dung/" + ident("S1"),
         {"tenDiemDung": "Không sửa lịch sử", "diaChi": "X", "thoiGianDung": 10}, expected=409)
    call("PUT", "/tuyen-xe/" + ident("R") + "/trang-thai", {"trangThai": "TAM_NGUNG"})
    call("POST", "/chuyen-xe", {"maChuyen": ident("CLOSED_ROUTE"), "maTuyen": ident("R"), "ngayKhoiHanh": "2030-01-03"}, expected=409)
    call("PUT", "/tuyen-xe/" + ident("R") + "/trang-thai", {"trangThai": "DANG_KHAI_THAC"})
    print("PASS: trip create, overnight, vehicle assignment/conflicts, reschedule rollback")


def search_and_status():
    assert search() == []  # Legacy scheduled rows are not automatically opened.
    publish_trip(AUTH,ident("C1"))
    assert [x["maChuyen"] for x in search()] == [ident("C1")]
    assert [x["maChuyen"] for x in search(origin="CITY_A", destination="CITY_B")] == [ident("C1")]
    assert search("2030-01-03") == [] and search(origin="B", destination="A") == []
    assert search(origin="UNKNOWN", expected=400)["code"]
    call("POST", "/chuyen-xe/" + ident("NIGHT") + "/bat-dau", expected=409)
    call("POST", "/chuyen-xe/" + ident("NIGHT") + "/huy", expected=400)
    call("POST", "/quan-ly/chuyen-xe/" + ident("NIGHT") + "/huy", {"lyDo": "Kiểm thử hủy chuyến"})
    assert call("GET", "/chuyen-xe/" + ident("NIGHT") + "/trang-thai")["trangThai"] == "DA_HUY"
    call("PUT", "/chuyen-xe/" + ident("NIGHT"), {"ngayKhoiHanh": "2030-01-01", "gioKhoiHanh": "12:00:00"}, 409)
    assert [x["maChuyen"] for x in search()] == [ident("C1")]
    call("POST", "/chuyen-xe/" + ident("C1") + "/bat-dau")
    call("POST", "/quan-ly/chuyen-xe/" + ident("C1") + "/huy", {"lyDo": "Kiểm thử trạng thái"}, expected=409)
    call("POST", "/chuyen-xe/" + ident("C1") + "/hoan-thanh")
    assert search() == []
    assert call("GET", "/chuyen-xe/" + ident("C1") + "/trang-thai")["trangThai"] == "HOAN_TAT"
    call("POST", "/quan-ly/chuyen-xe/" + ident("C2") + "/huy", {"lyDo": "Kiểm thử hủy chuyến"})
    assert search("2030-01-02") == []
    print("PASS: search by station/city/date, no trips, cancelled/completed and lifecycle")


def validation():
    call("POST", "/tinh-thanh", {"maTinhThanh": ident("CITY_A"), "tenTinhThanh": "Trùng tỉnh"}, 409)
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
    call("DELETE", "/tinh-thanh/" + ident("CITY_A"), expected=409)
    print("PASS: validation/404/409 and JSON error responses")


def cleanup():
    sql(f"DELETE FROM trip_lifecycle_history WHERE left(ma_chuyen, {len(PREFIX)}) = '{PREFIX}'")
    for table, key in (("phan_cong_chuyen_xe","ma_phan_cong"), ("chuyen_xe", "ma_chuyen"), ("diem_dung", "ma_diem_dung"),
                       ("tuyen_xe", "ma_tuyen"), ("ghe", "ma_ghe"), ("xe", "ma_xe"), ("loai_xe", "ma_loai_xe"), ("nhan_vien","ma_nhan_vien"), ("nha_xe", "ma_nha_xe"), ("ben_xe", "ma_ben_xe"),
                       ("tinh_thanh", "ma_tinh_thanh")):
        # Prefix contains only ASCII letters, digits and underscores, generated here.
        sql(f"DELETE FROM {table} WHERE left({key}, {len(PREFIX)}) = '{PREFIX}'")


if __name__ == "__main__":
    try:
        AUTH.start()
        setup()
        for scenario in (crud, stops, trips, search_and_status, validation):
            scenario()
        print(f"PASS: 5 API scenarios, {requests_checked} HTTP responses checked")
    finally:
        try:
            cleanup()
        finally:
            AUTH.close()
