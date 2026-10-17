"""Execute remaining transport matrix cases; delete only uniquely named test records."""
import json
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = 'http://localhost:8080/api'


def call(method, path, body=None, expected=200):
    req = Request(BASE + path, method=method,
                  data=None if body is None else json.dumps(body).encode('utf-8'),
                  headers={'Content-Type': 'application/json'})
    try:
        response = urlopen(req, timeout=30)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read()
        assert response.code == expected, (method, path, response.code, raw)
        return json.loads(raw) if raw else None


def main():
    tag = 'T06_TEST_' + uuid.uuid4().hex[:8]
    station, route = tag + '_B', tag + '_R'
    cleanup = []
    results = []
    try:
        call('DELETE', '/ben-xe/T06_BX_HCM_E', expected=409)
        call('GET', '/ben-xe/T06_BX_HCM_E')
        results.append('TC-UC16-02')
        body = dict(maBenXe=station, tenBenXe='B' * 160, diaChi='Test',
                    maTinhThanh='DEMO_HCM', trangThai='HOAT_DONG')
        call('POST', '/ben-xe', body, 201)
        cleanup.append('/ben-xe/' + station)
        assert len(call('GET', '/ben-xe/' + station)['tenBenXe']) == 160
        results.append('TC-UC16-03')
        body = dict(maTuyen=route, tenTuyen='Test', tramKhoiHanh=station, tramDen=station,
                    thoiGianKhoiHanh='08:00', thoiGianDuKien=4, giaCoBan=180000,
                    trangThai='DANG_KHAI_THAC', maNhaXe='T06_NX_A')
        call('POST', '/tuyen-xe', body, 400)
        results.append('TC-UC17-02')
        body['tramDen'] = 'T06_BX_LD'
        call('POST', '/tuyen-xe', body, 201)
        cleanup.append('/tuyen-xe/' + route)
        call('GET', '/tuyen-xe/' + route)
        stops = [tag + '_D1', tag + '_D2']
        for n, stop in enumerate(stops, 1):
            path = '/tuyen-xe/' + route + '/diem-dung'
            call('POST', path, dict(maDiemDung=stop, tenDiemDung='Test', diaChi='Test',
                                   thuTu=n, thoiGianDung=15, loaiDiemDung='CA_HAI'), 201)
            cleanup.append(path + '/' + stop)
        reordered = call('PUT', path + '/sap-xep', dict(maDiemDungTheoThuTu=stops[::-1]))
        assert [s['maDiemDung'] for s in reordered] == stops[::-1]
        results.append('TC-UC17-03')
        call('PUT', '/xe/T06_X_1/ghe/T06_X_1_G001',
             dict(viTri='CHANGED', tang=1, trangThai='HOAT_DONG'), 409)
        assert call('GET', '/xe/T06_X_1/ghe/T06_X_1_G001')['viTri'] == 'P01'
        results.append('TC-UC18-02')
        call('PUT', '/chuyen-xe/T06_C_1_1/xe', dict(maXe='T06_X_7'), 409)
        assert call('GET', '/chuyen-xe/T06_C_1_1')['maXe'] == 'T06_X_1'
        results.append('TC-UC19-02')
        assignment = dict(maPhanCong=tag + '_P', maChuyen='T06_C_NO_VEHICLE',
                          maNhanVien='T06_TX_7', vaiTro='TAI_XE_CHINH')
        call('POST', '/phan-cong', assignment, 409)
        results.append('TC-UC20-02')
        # Reuse a baseline employee available on DEMO_SAU, preserving seeded assignments.
        assignment['maNhanVien'] = 'DEMO_TX2'
        assignment['maChuyen'] = 'DEMO_SAU'
        call('POST', '/phan-cong', assignment, 201)
        cleanup.append('/phan-cong/' + assignment['maPhanCong'])
        assignment['ghiChu'] = 'Updated'
        call('PUT', cleanup[-1], assignment)
        assert call('GET', cleanup[-1])['ghiChu'] == 'Updated'
        call('DELETE', cleanup.pop(), expected=204)
        results.append('TC-UC20-01')
        barrier_path = '/phan-cong'
        def attempt(n):
            candidate = dict(assignment, maPhanCong=tag + '_RACE' + str(n))
            cleanup.append('/phan-cong/' + candidate['maPhanCong'])
            try:
                call('POST', barrier_path, candidate, 201)
                return 201
            except AssertionError as error:
                if error.args[0][2] == 409:
                    return 409
                raise
        with ThreadPoolExecutor(max_workers=2) as pool:
            assert sorted(pool.map(attempt, range(2))) == [201, 409]
        results.append('TC-UC20-03')
    finally:
        for path in reversed(cleanup):
            try:
                call('DELETE', path, expected=204)
            except AssertionError as error:
                if error.args[0][2] != 404:
                    raise
    output = Path(__file__).resolve().parents[2] / 'apps/backend/target/t06_remaining_api.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(results, indent=2), encoding='utf-8')
    print(f'PASS: {len(results)} remaining transport matrix cases; test records cleaned.')


if __name__ == '__main__':
    main()
