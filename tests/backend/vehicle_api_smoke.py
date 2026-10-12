"""HTTP CRUD checks for vehicle/seat/assignment APIs; requires demo seed and Tomcat.

Creates uniquely named test records and deletes only those records in finally.
Run: py -3 tests/backend/vehicle_api_smoke.py
"""
import json
import os
import uuid
from auth_fixture import AdminFixture
from datetime import date, timedelta
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE = os.environ.get("VEHICLE_API_URL", "http://localhost:8080/api").rstrip("/")
AUTH = AdminFixture(BASE)
checks = 0


def call(method, path, body=None, expected=200):
    global checks
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = Request(BASE + path, data=data, method=method,
                      headers=AUTH.headers())
    try:
        response = AUTH.opener.open(request, timeout=30)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read().decode("utf-8")
        assert response.code == expected, (method, path, response.code, raw)
        checks += 1
        return json.loads(raw) if raw else None


def main():
    tag = "HTTP_" + uuid.uuid4().hex[:12]
    type_id, vehicle_id, assignment_id = tag + "_L", tag + "_X", tag + "_P"
    cleanup = []
    try:
        call("GET", "/xe/DEMO_XE")
        assert len(call("GET", "/xe/DEMO_XE2/ghe")) == 40
        kind = dict(maLoaiXe=type_id, tenLoaiXe="HTTP demo", soChoNgoi=29, soTang=2)
        call("POST", "/loai-xe", kind, 201); cleanup.append("/loai-xe/" + type_id)
        kind["tenLoaiXe"] = "HTTP updated"
        assert call("PUT", "/loai-xe/" + type_id, kind)["tenLoaiXe"] == "HTTP updated"
        today = date.today()
        vehicle = dict(maXe=vehicle_id, bienSo=tag.replace("_", "-"), maLoaiXe=type_id, maNhaXe="DEMO_NX",
                       namSanXuat=2024, ngayDangKiem=str(today - timedelta(days=30)),
                       hanDangKiem=str(today + timedelta(days=365)), trangThai="SAN_SANG",
                       tocDo=60, dungTichXang=100)
        call("POST", "/xe", vehicle, 201); cleanup.append("/xe/" + vehicle_id)
        vehicle["trangThai"] = "BAO_TRI"
        assert call("PUT", "/xe/" + vehicle_id, vehicle)["trangThai"] == "BAO_TRI"
        call("DELETE", "/loai-xe/" + type_id, expected=409)
        seats_path = "/xe/" + vehicle_id + "/ghe"
        seats = call("GET", seats_path); assert len(seats) == 29
        call("POST", seats_path, dict(viTri="EXTRA", tang=1), 409)
        seat_path = seats_path + "/" + seats[0]["maGhe"]
        call("GET", seat_path)
        call("PUT", seat_path, dict(viTri="A01", tang=1, trangThai="BAO_TRI"))
        call("DELETE", seat_path, expected=409)
        replacement = [dict(maGhe=s["maGhe"], viTri=s["viTri"], tang=s["tang"], trangThai=s["trangThai"]) for s in call("GET", seats_path)]
        replacement[0] = dict(maGhe=None, viTri="B01", tang=2, trangThai="HOAT_DONG")
        call("PUT", "/xe/" + vehicle_id + "/so-do-ghe", dict(danhSachGhe=replacement))
        call("GET", seat_path, expected=404)
        assert len(call("GET", seats_path)) == 29
        route_id=tag+"_R";trip_id=tag+"_C"
        original=call("GET","/tuyen-xe/DEMO_TUYEN")
        original_trip=call("GET","/chuyen-xe/DEMO_CHUYEN")
        call("POST","/tuyen-xe",dict(maTuyen=route_id,tenTuyen="Return fixture",tramKhoiHanh=original["tramDen"],tramDen=original["tramKhoiHanh"],thoiGianKhoiHanh="08:00",thoiGianDuKien=6,giaCoBan=250000,trangThai="DANG_KHAI_THAC",maNhaXe="DEMO_NX"),201)
        cleanup.append("/tuyen-xe/"+route_id)
        call("POST","/chuyen-xe",dict(maChuyen=trip_id,maTuyen=route_id,ngayKhoiHanh=str(date.fromisoformat(original_trip["ngayKhoiHanh"])+timedelta(days=1)),gioKhoiHanh="08:00"),201)
        cleanup.append("/quan-ly/chuyen-xe/"+trip_id)
        assignment = dict(maPhanCong=assignment_id, maChuyen=trip_id,
                          maNhanVien="DEMO_TX", vaiTro="TAI_XE_CHINH", ghiChu="HTTP test",xacNhanKeHoachLaiNghi=True)
        call("POST", "/phan-cong", assignment, 201); cleanup.append("/phan-cong/" + assignment_id)
        assignment["ghiChu"] = "HTTP update"
        call("PUT", "/phan-cong/" + assignment_id, assignment)
        assert call("GET", "/phan-cong/" + assignment_id)["ghiChu"].endswith("HTTP update")
        call("GET", "/xe/DEMO_HETHAN/dieu-kien/DEMO_SAU", expected=409)
        call("GET", "/xe/DEMO_BAOTRI/dieu-kien/DEMO_SAU", expected=409)
        call("GET", "/xe/DEMO_XE/dieu-kien/DEMO_TRUNG", expected=409)
        for path in list(reversed(cleanup)):
            call("DELETE", path, expected=204); cleanup.remove(path)
            call("GET", path, expected=404)
        print(f"PASS: {checks} HTTP checks; type, vehicle, seat, assignment CRUD and eligibility")
    finally:
        for path in reversed(cleanup):
            try:
                call("DELETE", path, expected=204)
            except Exception as error:
                print(f"Cleanup required for {path}: {error}")


if __name__ == "__main__":
    try:
        AUTH.start()
        main()
    finally:
        AUTH.close()
