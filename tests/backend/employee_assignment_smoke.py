"""T11 real cookie/CSRF/PostgreSQL checks. Development database only; own records cleaned."""
import json
import os
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from auth_fixture import AdminFixture

BASE = os.environ.get('API_BASE_URL', 'http://localhost:8080/api').rstrip('/')
PREFIX = 'EMP_' + uuid.uuid4().hex[:8] + '_'
auth = AdminFixture(BASE)
other_auth = AdminFixture(BASE)
checks = 0


def ident(value):
    return PREFIX + value


def call(method, path, body=None, expected=200, actor=auth, anonymous=False):
    global checks
    req = Request(BASE + path, method=method, headers=actor.headers(),
                  data=None if body is None else json.dumps(body).encode('utf-8'))
    try:
        response = (urlopen if anonymous else actor.opener.open)(req, timeout=30)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read()
        assert response.status == expected, (path, expected, response.status, raw[:500])
        result = json.loads(raw) if raw else None
        if expected >= 400:
            assert all(key in result for key in ('code', 'message', 'details', 'timestamp', 'traceId'))
        checks += 1
        return result


def employee(name, owner='A', role='TAI_XE'):
    number = {'D': 1, 'P': 2, 'D2': 3, 'FOREIGN': 4, 'TEMP': 5}[name]
    return dict(maNhanVien=ident(name), hoTen='Nguyen Thanh ' + name,
                soDienThoai='09' + str(int(PREFIX[4:12], 16) % 1000000).zfill(6) + str(number).zfill(2),
                email=ident(name).lower() + '@example.test', maNhaXe=ident(owner),
                loaiNhanVien=role, trangThai='DANG_LAM_VIEC', soBangLai='123456789012' if role == 'TAI_XE' else None,
                ngayHetHanBangLai='2040-01-01' if role == 'TAI_XE' else None)


def profile_audit():
    """Actual profile rules: duplicate names are allowed; contact details must be unique."""
    def profile(code, slot, role='TAI_XE', **changes):
        base = employee('TEMP', role=role)
        return base | dict(maNhanVien=ident(code), hoTen='Nguyen Thanh D',
                           soDienThoai=base['soDienThoai'][:-2] + str(slot),
                           email=ident(code).lower() + '@example.test') | changes

    driver = profile('AUD_D', 61)
    assistant = profile('AUD_P', 62, 'PHU_XE')
    assert call('POST', '/nhan-vien', driver, 201)['trangThai'] == 'DANG_LAM_VIEC'
    call('POST', '/nhan-vien', assistant, 201)  # Same name, different IDs and contacts.
    call('POST', '/nhan-vien', driver, 409)
    call('POST', '/nhan-vien', profile('AUD_DUPMAIL', 63, email=driver['email'].upper()), 409)
    call('POST', '/nhan-vien', profile('AUD_DUPPHONE', 63, soDienThoai=driver['soDienThoai']), 409)
    call('POST', '/nhan-vien', profile('AUD_BADMAIL', 63, email='invalid'), 400)
    call('POST', '/nhan-vien', profile('AUD_LICENSE', 63, soBangLai=None), 400)
    call('POST', '/nhan-vien', profile('AUD_EXPIRY', 63, ngayHetHanBangLai=None), 400)
    call('POST', '/nhan-vien', profile('AUD_DOB', 63, ngaySinh=str(date.today() + timedelta(days=1))), 400)
    call('POST', '/nhan-vien', profile('AUD_OWNER', 63, maNhaXe=ident('MISSING')), 404)
    call('PUT', '/nhan-vien/' + driver['maNhanVien'], driver | dict(email=assistant['email']), 409)
    assert call('GET', '/nhan-vien/' + driver['maNhanVien'])['email'] == driver['email']
    call('PUT', '/nhan-vien/' + driver['maNhanVien'], driver | dict(maNhanVien=ident('NEW_ID')), 400)
    call('PUT', '/nhan-vien/' + driver['maNhanVien'], driver | dict(maNhaXe=ident('B')), 409)
    updated = call('PUT', '/nhan-vien/' + driver['maNhanVien'], driver | dict(hoTen='Nguyen Van Audit'))
    assert updated['hoTen'] == call('GET', '/nhan-vien/' + driver['maNhanVien'])['hoTen']
    call('PUT', '/nhan-vien/' + driver['maNhanVien'], driver | dict(trangThai='TAM_NGHI'))
    call('PUT', '/nhan-vien/' + driver['maNhanVien'], driver)  # Back to active.
    # Active by itself does not forbid deletion: assignments/account links do.
    call('DELETE', '/nhan-vien/' + driver['maNhanVien'], expected=204)
    call('DELETE', '/nhan-vien/' + assistant['maNhanVien'], expected=204)
    call('GET', '/nhan-vien/' + driver['maNhanVien'], expected=404)
    linked = profile('AUD_LINKED', 64, 'PHU_XE')
    call('POST', '/nhan-vien', linked, 201)
    other_auth.sql(f"UPDATE nguoi_dung SET ma_nhan_vien='{linked['maNhanVien']}' WHERE ma_nguoi_dung='{other_auth.user_id}'")
    try:
        call('DELETE', '/nhan-vien/' + linked['maNhanVien'], expected=409)
    finally:
        other_auth.sql(f"UPDATE nguoi_dung SET ma_nhan_vien=NULL WHERE ma_nguoi_dung='{other_auth.user_id}'")
    call('DELETE', '/nhan-vien/' + linked['maNhanVien'], expected=204)


def main():
    auth.start(); other_auth.start()
    call('GET', '/nhan-vien', expected=401, anonymous=True)
    for owner in ('A', 'B'):
        auth.sql(f"INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,trang_thai) VALUES ('{ident(owner)}','Operator {owner}','HOAT_DONG')")
    for name, owner, role in [('D', 'A', 'TAI_XE'), ('P', 'A', 'PHU_XE'), ('D2', 'A', 'TAI_XE'), ('FOREIGN', 'B', 'TAI_XE'), ('TEMP', 'A', 'PHU_XE')]:
        call('POST', '/nhan-vien', employee(name, owner, role), 201)
    call('POST', '/nhan-vien', employee('D'), 409)
    call('POST', '/nhan-vien', employee('D') | dict(maNhanVien=ident('INVALID'), soDienThoai='bad'), 400)
    call('PUT', '/nhan-vien/' + ident('TEMP'), employee('TEMP', role='PHU_XE') | dict(trangThai='TAM_NGHI'))
    call('DELETE', '/nhan-vien/' + ident('TEMP'), expected=204)
    call('GET', '/nhan-vien/' + ident('TEMP'), expected=404)
    assert len(call('GET', '/nhan-vien?q=' + ident('D2'))) == 1
    profile_audit()
    auth.sql(f"INSERT INTO tinh_thanh(ma_tinh_thanh,ten_tinh_thanh) VALUES ('{ident('CITY')}','City');"
             f"INSERT INTO ben_xe(ma_ben_xe,ten_ben_xe,dia_chi,ma_tinh_thanh,trang_thai) VALUES"
             f"('{ident('S1')}','First','First','{ident('CITY')}','HOAT_DONG'),('{ident('S2')}','Second','Second','{ident('CITY')}','HOAT_DONG');")
    for route, start, end, hours in [('R', 'S1', 'S2', 4), ('BACK', 'S2', 'S1', 4), ('LONG', 'S1', 'S2', 5)]:
        call('POST', '/tuyen-xe', dict(maTuyen=ident(route), tenTuyen=route, tramKhoiHanh=ident(start), tramDen=ident(end),
             thoiGianKhoiHanh='08:00', thoiGianDuKien=hours, giaCoBan=180000, trangThai='DANG_KHAI_THAC', maNhaXe=ident('A')), 201)
    call('POST', '/loai-xe', dict(maLoaiXe=ident('TYPE'), tenLoaiXe='29 seats', soChoNgoi=29, soTang=1), 201)
    call('POST', '/xe', dict(maXe=ident('BUS'), bienSo='51B-' + PREFIX[4:12].upper() + '.01', maLoaiXe=ident('TYPE'), maNhaXe=ident('A'),
         namSanXuat=2024, ngayDangKiem='2020-01-01', hanDangKiem='2040-01-01', trangThai='SAN_SANG'), 201)
    day=str(date.today() + timedelta(days=30))
    for name, route, time, vehicle in [('C1', 'R', '08:00', ident('BUS')), ('C2', 'BACK', '12:30', ident('BUS')),
                                      ('OVERLAP', 'R', '09:00', None), ('LONGTRIP', 'LONG', '08:00', None)]:
        call('POST', '/chuyen-xe', dict(maChuyen=ident(name), maTuyen=ident(route), ngayKhoiHanh=day, gioKhoiHanh=time, maXe=vehicle), 201)
    assignment=dict(maPhanCong=ident('PC'), maChuyen=ident('C1'), maNhanVien=ident('D'), vaiTro='TAI_XE_CHINH')
    preview=call('GET','/nhan-vien/'+ident('D')+'/dieu-kien?maChuyen='+ident('C1')+'&vaiTro=TAI_XE_CHINH')
    assert preview['duDieuKienCoBan'] and not preview['heThongKiemChungGioNghi']
    saved_csrf=auth.csrf; auth.csrf=None
    try:
        call('POST','/phan-cong',assignment,403)
    finally:
        auth.csrf=saved_csrf
    call('POST', '/phan-cong', assignment, 201)
    updated=call('PUT', '/phan-cong/' + ident('PC'), assignment | dict(ghiChu='Updated through T11 API'))
    assert updated['ghiChu']=='Updated through T11 API'
    call('PUT', '/nhan-vien/' + ident('D2'), employee('D2') | dict(trangThai='TAM_NGHI'))
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('INACTIVE'), maNhanVien=ident('D2'), maChuyen=ident('OVERLAP')), 409)
    call('PUT', '/nhan-vien/' + ident('D2'), employee('D2') | dict(ngayHetHanBangLai=str(date.today())))
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('EXPIRED'), maNhanVien=ident('D2'), maChuyen=ident('OVERLAP')), 409)
    call('PUT', '/nhan-vien/' + ident('D2'), employee('D2'))
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('WRONG_ROLE'), maNhanVien=ident('P')), 409)
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('SECOND_MAIN'), maNhanVien=ident('D2')), 409)
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('MISSING_EMP'), maNhanVien=ident('MISSING')), 404)
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('MISSING_TRIP'), maChuyen=ident('MISSING')), 404)
    call('PUT', '/nhan-vien/' + ident('D'), employee('D') | dict(trangThai='TAM_NGHI'), 409)
    call('DELETE', '/nhan-vien/' + ident('D'), expected=409)
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('DUP')), 409)
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('WRONG'), maNhanVien=ident('FOREIGN')), 409)
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('COLLISION'), maChuyen=ident('OVERLAP')), 409)
    assert len(call('GET', '/nhan-vien/' + ident('D') + '/phan-cong')) == 1
    call('POST', '/phan-cong', assignment | dict(maPhanCong=ident('RETURN'), maChuyen=ident('C2')), 201)
    long_assignment=assignment | dict(maPhanCong=ident('LONGPC'), maChuyen=ident('LONGTRIP'), maNhanVien=ident('D2'))
    assert call('GET','/nhan-vien/'+ident('D2')+'/dieu-kien?maChuyen='+ident('LONGTRIP')+'&vaiTro=TAI_XE_CHINH')['canKiemTraKeHoachLaiNghi']
    call('POST', '/phan-cong', long_assignment, 409)
    call('POST', '/phan-cong', long_assignment | dict(xacNhanKeHoachLaiNghi=True), 201)
    auth.sql(f"UPDATE nguoi_dung SET loai_tai_khoan='NHA_XE',ma_nha_xe='{ident('A')}' WHERE ma_nguoi_dung='{auth.user_id}'")
    call('GET', '/nhan-vien/' + ident('FOREIGN'), expected=403)
    call('GET', '/nhan-vien?maNhaXe=' + ident('B'), expected=403)
    call('POST', '/nhan-vien', employee('TEMP', owner='B', role='PHU_XE'), 403)
    auth.sql(f"UPDATE nguoi_dung SET loai_tai_khoan='NHAN_VIEN',ma_nha_xe=NULL,ma_nhan_vien='{ident('D')}' WHERE ma_nguoi_dung='{auth.user_id}'")
    assert len(call('GET', '/nhan-vien')) == 1
    call('GET', '/nhan-vien/' + ident('FOREIGN'), expected=403)
    call('PUT', '/nhan-vien/' + ident('D'), employee('D'), 403)
    auth.sql(f"UPDATE nguoi_dung SET loai_tai_khoan='ADMIN',ma_nhan_vien=NULL WHERE ma_nguoi_dung='{auth.user_id}'")
    # Independent users/sessions race for the same employee on two overlapping trips.
    call('DELETE', '/phan-cong/' + ident('LONGPC'), expected=204)
    def compete(index):
        target=ident('OVERLAP') if index == 0 else ident('LONGTRIP')
        body=assignment | dict(maPhanCong=ident('RACE' + str(index)), maChuyen=target,
                               maNhanVien=ident('D2'), xacNhanKeHoachLaiNghi=True)
        actor=auth if index == 0 else other_auth
        try:
            call('POST', '/phan-cong', body, 201, actor=actor)
            return 201
        except AssertionError as error:
            if error.args[0][2] == 409:
                return 409
            raise
    with ThreadPoolExecutor(max_workers=2) as executor:
        assert sorted(executor.map(compete, range(2))) == [201, 409]
    print(f'PASS: {checks} T11 HTTP checks; cookie/CSRF, CRUD, scope, connection and concurrent assignments.')


def cleanup():
    if auth.user_id:
        auth.sql(f"UPDATE nguoi_dung SET ma_nhan_vien=NULL,ma_nha_xe=NULL,loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung='{auth.user_id}'")
        for table, key in [('phan_cong_chuyen_xe', 'ma_phan_cong'), ('chuyen_xe', 'ma_chuyen'), ('ghe', 'ma_xe'),
                           ('xe', 'ma_xe'), ('loai_xe', 'ma_loai_xe'), ('tuyen_xe', 'ma_tuyen'), ('ben_xe', 'ma_ben_xe'),
                           ('tinh_thanh', 'ma_tinh_thanh'), ('nhan_vien', 'ma_nhan_vien'), ('nha_xe', 'ma_nha_xe')]:
            auth.sql(f"DELETE FROM {table} WHERE left({key},{len(PREFIX)})='{PREFIX}'")
    other_auth.close(); auth.close()


if __name__ == '__main__':
    try:
        main()
    finally:
        cleanup()
