"""Check the stable project dataset and actual API contracts; does not create orders or payments."""
import argparse
from datetime import datetime, timedelta, timezone
import http.cookiejar
import json
import os
from pathlib import Path
import subprocess
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, build_opener, HTTPCookieProcessor

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', default='http://localhost:8080/api')
    parser.add_argument('--database', default='datvexe')
    parser.add_argument('--check-reload', action='store_true')
    parser.add_argument('--skip-logins', action='store_true', help='Repeat data/search checks after account logins have already passed')
    args = parser.parse_args()

    def sql(statement):
        result = subprocess.run(['docker', 'exec', '-e', 'PGTZ=Asia/Ho_Chi_Minh', '-i', 'datvexe-postgres', 'psql', '-U', 'datvexe_user', '-d', args.database, '-v', 'ON_ERROR_STOP=1', '-tA'],
                                input=statement, encoding='utf-8', text=True, capture_output=True, check=True)
        return result.stdout.strip()

    expected = {'nha_xe': 5, 'nhan_vien': 35, 'nguoi_dung': 47, 'xe': 10, 'ghe': 320, 'tuyen_xe': 20, 'diem_dung': 40}
    for table, count in expected.items():
        actual = int(sql(f'SELECT count(*) FROM {table}'))
        assert actual == count, (table, actual, count)
    assert sql("SELECT count(*) FROM nguoi_dung WHERE mat_khau NOT LIKE 'pbkdf2-sha256$600000$%'") == '0'
    assert sql("SELECT count(*) FROM phan_cong_chuyen_xe p JOIN nhan_vien n USING(ma_nhan_vien) JOIN chuyen_xe c USING(ma_chuyen) JOIN tuyen_xe t USING(ma_tuyen) WHERE n.ma_nha_xe<>t.ma_nha_xe") == '0'
    assert sql("SELECT count(*) FROM xe x WHERE (SELECT count(*) FROM ghe g WHERE g.ma_xe=x.ma_xe)<>x.so_cho_ngoi") == '0'
    assert int(sql("SELECT count(*) FROM chuyen_xe WHERE trang_thai='DANG_MO_BAN' AND ngay_khoi_hanh>CURRENT_DATE")) >= 210
    assert sql("SELECT count(*) FROM chuyen_xe a JOIN chuyen_xe b ON a.ma_xe=b.ma_xe AND a.ma_chuyen<b.ma_chuyen WHERE a.ngay_khoi_hanh+a.gio_khoi_hanh<b.gio_den_du_kien AND b.ngay_khoi_hanh+b.gio_khoi_hanh<a.gio_den_du_kien") == '0'
    assert sql("SELECT count(*) FROM phan_cong_chuyen_xe a JOIN phan_cong_chuyen_xe b ON a.ma_nhan_vien=b.ma_nhan_vien AND a.ma_phan_cong<b.ma_phan_cong JOIN chuyen_xe x ON x.ma_chuyen=a.ma_chuyen JOIN chuyen_xe y ON y.ma_chuyen=b.ma_chuyen WHERE x.ngay_khoi_hanh+x.gio_khoi_hanh<y.gio_den_du_kien AND y.ngay_khoi_hanh+y.gio_khoi_hanh<x.gio_den_du_kien") == '0'

    if args.check_reload:
        tables = tuple(expected) + ('chuyen_xe', 'phan_cong_chuyen_xe')
        def fingerprints():
            return [sql(f"SELECT md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text)::text,'[]')) FROM {table} r") for table in tables]
        before = fingerprints()
        subprocess.run(['python', str(ROOT / 'scripts/load_project_data.py'), '--database', args.database, '--load-only'], check=True, cwd=ROOT)
        assert before == fingerprints(), 'Reload changed existing data'

    opener = build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))
    csrf = None
    checks = 0
    def call(method, path, body=None):
        nonlocal checks
        headers = {'Content-Type': 'application/json', 'X-Requested-With': 'datvexe'}
        if csrf:
            headers['X-CSRF-Token'] = csrf
        req = Request(args.api.rstrip('/') + path, method=method, headers=headers,
                      data=None if body is None else json.dumps(body).encode('utf-8'))
        for attempt in range(3):
            try:
                with opener.open(req, timeout=30) as response:
                    raw = response.read()
                    checks += 1
                    return json.loads(raw) if raw else None
            except HTTPError as error:
                if error.code != 429 or attempt == 2:
                    raise
                print('Login throttle respected; waiting before continuing account checks.', flush=True)
                delay = min(120, max(1, int(error.headers.get('Retry-After', '60'))))
                time.sleep(delay)

    password = os.environ.get('PROJECT_ADMIN_PASSWORD', 'DatVeXe2026!')
    accounts = json.loads(sql("SELECT json_agg(json_build_object('email',email,'role',loai_tai_khoan,'employee',ma_nhan_vien,'owner',ma_nha_xe) ORDER BY email) FROM nguoi_dung"))
    for index, account in enumerate([] if args.skip_logins else accounts, 1):
        session = call('POST', '/auth/dang-nhap', {'email': account['email'], 'matKhau': password})
        csrf = session['csrfToken']
        assert session['nguoiDung']['loaiTaiKhoan'] == account['role']
        if account['role'] in ('ADMIN', 'NHA_XE', 'NHAN_VIEN'):
            staff = call('GET', '/nhan-vien')
            if account['role'] == 'NHA_XE':
                assert len(staff) == 7 and all(n['maNhaXe'] == account['owner'] for n in staff)
            elif account['role'] == 'NHAN_VIEN':
                assert len(staff) == 1 and staff[0]['maNhanVien'] == account['employee']
        call('POST', '/auth/dang-xuat')
        csrf = None
        if index % 10 == 0:
            print(f'Checked {index}/47 account logins.', flush=True)
    day = (datetime.now(timezone(timedelta(hours=7))).date()+timedelta(days=1)).isoformat()
    routes = json.loads(sql("SELECT json_agg(json_build_object('id',ma_tuyen,'from',tram_khoi_hanh,'to',tram_den)) FROM tuyen_xe"))
    available = 0
    for route in routes:
        journeys = call('GET', '/chuyen-xe?' + urlencode({'from': route['from'], 'to': route['to'], 'date': day}))
        for trip in journeys:
            assert trip['trangThai'] == 'DANG_MO_BAN'
            seat_map = call('GET', '/chuyen-xe/' + trip['maChuyen'] + '/ghe')
            assert len(seat_map['danhSachGhe']) in (22, 29, 40)
            assert any(seat['tinhTrangKhaDung'] == 'TRONG' for seat in seat_map['danhSachGhe'])
            available += 1
    assert available > 0
    login_result = 'logins skipped' if args.skip_logins else '47 logins'
    print(f'PASS: project counts, relationships, no schedule conflicts, {login_result} and {checks} API calls; search/seat maps available.')


if __name__ == '__main__':
    main()
