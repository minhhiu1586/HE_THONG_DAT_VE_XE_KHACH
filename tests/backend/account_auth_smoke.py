"""T07 HTTP/session/error checks against development PostgreSQL and Tomcat."""
import json
import urllib.request
import urllib.error
from auth_fixture import AdminFixture
import os
import uuid

BASE = os.environ.get('API_BASE_URL', 'http://localhost:8080/api').rstrip('/')
checks = 0
clients = []
PREFIX = 'AUTH_' + uuid.uuid4().hex[:10]

def call(client, method, path, body=None, expected=200, csrf=True):
    global checks
    headers = client.headers()
    if not csrf:
        headers.pop('X-CSRF-Token', None)
    request = urllib.request.Request(BASE + path, method=method, headers=headers,
        data=None if body is None else json.dumps(body).encode())
    try:
        response = client.opener.open(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        assert response.code == expected, (method, path, response.code, raw[:500])
        result = json.loads(raw) if raw else None
        if expected >= 400:
            assert {'code', 'message', 'details', 'timestamp', 'traceId'} <= result.keys(), result
            assert response.headers.get('X-Trace-Id') == result['traceId']
            assert 'matKhau' not in raw.decode() or result['code'] == 'VALIDATION_ERROR'
        checks += 1
        return result

def new_client():
    client = AdminFixture(BASE)
    clients.append(client)
    return client

try:
    anon = new_client()
    call(anon, 'GET', '/tai-khoan/me', expected=401)
    call(anon, 'GET', '/xe', expected=401)
    call(anon, 'POST', '/auth/dang-ky', {}, 400)
    admin = new_client().start()
    user = new_client()
    body = {'hoTen':'Người kiểm thử', 'email':user.email, 'soDienThoai':'098' + ''.join(__import__('secrets').choice('0123456789') for _ in range(7)), 'matKhau':user.password}
    call(user, 'POST', '/auth/dang-ky', body | {'role':'ADMIN'}, 400)
    profile = call(user, 'POST', '/auth/dang-ky', body, 201)
    user.user_id = profile['maNguoiDung']
    assert profile['loaiTaiKhoan'] == 'KHACH_HANG' and 'matKhau' not in profile
    call(user, 'POST', '/auth/dang-ky', body, 409)
    call(user, 'POST', '/auth/dang-nhap', {'email':user.email,'matKhau':'WrongPassword123'}, 401)
    login = call(user, 'POST', '/auth/dang-nhap', {'email':user.email,'matKhau':user.password})
    user.csrf = login['csrfToken']
    cookie = next(c for c in user.jar if c.name == 'datvexe_session')
    assert cookie.path == '/api' and 'HttpOnly' in cookie._rest and cookie._rest.get('SameSite') == 'Lax'
    call(user, 'GET', '/tai-khoan/me')
    call(user, 'GET', '/quan-tri/tai-khoan', expected=403)
    call(user, 'GET', '/xe', expected=403)
    call(user, 'PUT', '/tai-khoan/' + admin.user_id, {}, 403)
    update = {key:profile.get(key) for key in ['hoTen','email','soDienThoai','gioiTinh','ngaySinh','cccd']}
    call(user, 'PUT', '/tai-khoan/me', update, 403, csrf=False)
    call(user, 'PUT', '/tai-khoan/me', update | {'loaiTaiKhoan':'ADMIN'}, 400)
    call(user, 'PUT', '/tai-khoan/me', update | {'trangThai':'HOAT_DONG'}, 400)
    call(user, 'PUT', '/tai-khoan/me', update | {'userId':admin.user_id}, 400)
    update['hoTen'] = 'Hồ sơ đã cập nhật'
    assert call(user, 'PUT', '/tai-khoan/me', update)['hoTen'] == update['hoTen']
    call(user, 'PUT', '/tai-khoan/me/mat-khau', {'matKhauHienTai':'WrongPassword123','matKhauMoi':'ChangedPassword123'}, 400)
    call(user, 'PUT', '/tai-khoan/me/mat-khau', {'matKhauHienTai':user.password,'matKhauMoi':'ChangedPassword123'}, 204)
    call(user, 'GET', '/tai-khoan/me', expected=401)
    user.password = 'ChangedPassword123'
    user.csrf = call(user, 'POST', '/auth/dang-nhap', {'email':user.email,'matKhau':user.password})['csrfToken']
    call(admin, 'PUT', '/quan-tri/tai-khoan/' + user.user_id + '/trang-thai', {'trangThai':'KHOA'})
    call(user, 'GET', '/tai-khoan/me', expected=401)
    call(user, 'POST', '/auth/dang-nhap', {'email':user.email,'matKhau':user.password}, 401)
    call(admin, 'PUT', '/quan-tri/tai-khoan/' + user.user_id + '/trang-thai', {'trangThai':'HOAT_DONG'})
    user.csrf = call(user, 'POST', '/auth/dang-nhap', {'email':user.email,'matKhau':user.password})['csrfToken']
    user.sql(f"UPDATE phien_dang_nhap SET tao_luc=now()-interval '2 hours', het_han=now()-interval '1 second' WHERE ma_nguoi_dung='{user.user_id}'")
    call(user, 'GET', '/tai-khoan/me', expected=401)
    user.csrf = call(user, 'POST', '/auth/dang-nhap', {'email':user.email,'matKhau':user.password})['csrfToken']
    old_cookie = list(user.jar)[0]
    call(user, 'POST', '/auth/dang-xuat', expected=204)
    user.jar.set_cookie(old_cookie)
    call(user, 'GET', '/tai-khoan/me', expected=401)
    operator = new_client().start()
    owners = [PREFIX + '_A', PREFIX + '_B']
    admin.sql("INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,trang_thai) VALUES " +
              ','.join(f"('{owner}','Scope audit','HOAT_DONG')" for owner in owners))
    call(admin, 'POST', '/loai-xe', dict(maLoaiXe=PREFIX + '_L', tenLoaiXe='Scope vehicle', soChoNgoi=29, soTang=1), 201)
    for index, owner in enumerate(owners):
        call(admin, 'POST', '/xe', dict(maXe=PREFIX + '_X' + str(index), bienSo=PREFIX.replace('_', '-') + '-' + str(index),
             maLoaiXe=PREFIX + '_L', maNhaXe=owner, namSanXuat=2024, ngayDangKiem='2020-01-01',
             hanDangKiem='2040-01-01', trangThai='SAN_SANG'), 201)
    operator.sql(f"UPDATE nguoi_dung SET loai_tai_khoan='NHA_XE',ma_nha_xe='{owners[0]}' WHERE ma_nguoi_dung='{operator.user_id}'")
    call(operator, 'GET', '/xe/' + PREFIX + '_X0')
    call(operator, 'GET', '/xe/' + PREFIX + '_X1', expected=403)
    assert all(v['maNhaXe'] == owners[0] for v in call(operator, 'GET', '/xe'))
    call(operator, 'POST', '/xe', {'maNhaXe': owners[1]}, 403)
    call(operator, 'POST', '/phan-cong', {'maChuyen':1,'maNhanVien':PREFIX + '_TX','vaiTro':'TAI_XE_CHINH'}, 400)
    operator.sql(f"UPDATE nguoi_dung SET ma_nha_xe='{owners[1]}' WHERE ma_nguoi_dung='{operator.user_id}'")
    assert all(v['maNhaXe'] == owners[1] for v in call(operator, 'GET', '/xe'))
    print(f'PASS: {checks} HTTP checks for account validation, CSRF, roles, lock, expiry, password, logout replay and operator scope')
finally:
    for client in reversed(clients):
        client.close()
    if clients:
        for table, key in [('ghe', 'ma_xe'), ('xe', 'ma_xe'), ('loai_xe', 'ma_loai_xe'), ('nha_xe', 'ma_nha_xe')]:
            clients[0].sql(f"DELETE FROM {table} WHERE left({key},{len(PREFIX)})='{PREFIX}'")
