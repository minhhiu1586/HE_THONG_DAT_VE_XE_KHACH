"""T10 real HTTP/PostgreSQL regression. Run only on a local development database.
Creates uniquely prefixed resources and cleans them in finally. Requires schema 001/002/003.
"""
import json
import os
import uuid
import subprocess
import urllib.request
import urllib.error
from auth_fixture import AdminFixture

BASE = os.environ.get('API_BASE_URL', 'http://localhost:8080/api').rstrip('/')
AUTH = AdminFixture(BASE)
PREFIX = 'T10_' + uuid.uuid4().hex[:10] + '_'
checks = 0
def ident(name): return PREFIX + name
def sql(statement):
    subprocess.run(['docker', 'exec', '-i', os.environ.get('DB_CONTAINER', 'datvexe-postgres'), 'psql', '-U', os.environ.get('DB_USER', 'datvexe_user'),
                    '-d', os.environ.get('DB_NAME', 'datvexe'), '-v', 'ON_ERROR_STOP=1', '-c', statement], check=True, stdout=subprocess.DEVNULL)
def call(method, path, body=None, expected=200, authenticated=True):
    global checks
    req = urllib.request.Request(BASE + path, method=method, headers=AUTH.headers(), data=None if body is None else json.dumps(body).encode())
    try:
        response = (AUTH.opener.open if authenticated else urllib.request.urlopen)(req, timeout=20)
    except urllib.error.HTTPError as error: response = error
    with response:
        raw = response.read(); assert response.status == expected, (method, path, expected, response.status, raw[:800])
        result = json.loads(raw) if raw else None
        if expected >= 400: assert all(key in result for key in ('code', 'message', 'details', 'timestamp', 'traceId')), result
        checks += 1; return result
def vehicle(name, owner='A', **changes):
    return dict(maXe=ident(name), bienSo='51B-' + PREFIX.replace('_', '-') + name, maLoaiXe=ident('L'), maNhaXe=ident(owner), soChoNgoi=4,
                ngayDangKiem='2020-01-01', hanDangKiem='2100-01-01', namSanXuat=2024, dungTichXang=100, tocDo=60, trangThai='SAN_SANG') | changes
def cleanup():
    if AUTH.user_id:
        sql(f"UPDATE nguoi_dung SET loai_tai_khoan='ADMIN',ma_nha_xe=NULL WHERE ma_nguoi_dung='{AUTH.user_id}'")
    for table, key in [('giu_cho','ma_giu_cho'),('ve_xe','ma_ve'),('don_dat_ve','ma_don'),('chuyen_xe','ma_chuyen'),('ghe','ma_ghe'),('xe','ma_xe'),
                       ('loai_xe','ma_loai_xe'),('tuyen_xe','ma_tuyen'),('ben_xe','ma_ben_xe'),('tinh_thanh','ma_tinh_thanh'),('nha_xe','ma_nha_xe')]:
        if table == 'ghe':
            sql(f"DELETE FROM ghe WHERE ma_xe LIKE '{PREFIX}%' AND left(ma_xe,{len(PREFIX)})='{PREFIX}'")
        else: sql(f"DELETE FROM {table} WHERE left({key},{len(PREFIX)})='{PREFIX}'")
def main():
    call('GET','/xe',expected=401,authenticated=False)
    for name in ('A','B'): sql(f"INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,trang_thai) VALUES ('{ident(name)}','Test {name}','HOAT_DONG')")
    kind = dict(maLoaiXe=ident('L'),tenLoaiXe='Loại xe T10',soChoNgoi=4,soTang=2)
    call('POST','/loai-xe',kind,201);call('POST','/loai-xe',kind,409)
    call('POST','/loai-xe',kind | dict(maLoaiXe=ident('BAD'),soChoNgoi=0),400)
    call('GET','/loai-xe/' + ident('L'))
    call('PUT','/loai-xe/' + ident('L'),kind | dict(tenLoaiXe='Loại xe đã sửa'))
    assert any(x['maLoaiXe']==ident('L') for x in call('GET','/loai-xe?q=' + ident('L')))
    call('POST','/xe',vehicle('X1'),201);call('POST','/xe',vehicle('X2','B'),201)
    call('POST','/xe',vehicle('X3') | dict(bienSo=vehicle('X1')['bienSo'].lower()),409)
    call('POST','/xe',vehicle('X3',maNhaXe=ident('MISSING')),404)
    call('POST','/xe',vehicle('X3',maLoaiXe=ident('MISSING')),404)
    call('POST','/xe',vehicle('X3',soChoNgoi=0),400)
    call('POST','/xe',vehicle('X3',soChoNgoi=-1),400)
    call('POST','/xe',vehicle('X3',soChoNgoi=4.5),400)
    call('POST','/xe',vehicle('X3',soChoNgoi=5),400)
    call('POST','/xe',vehicle('X3',maXe=123),400)
    call('POST','/xe',vehicle('X3',maXe='pham-vi'),400)
    call('POST','/xe',vehicle('X3',trangThai=1),400)
    call('POST','/xe',vehicle('X3',dungTichXang=-1),400)
    call('POST','/xe',vehicle('X3',ngayDangKiem='2030-01-02',hanDangKiem='2030-01-01'),400)
    call('DELETE','/loai-xe/' + ident('L'),expected=409)
    call('PUT','/loai-xe/' + ident('L'),kind | dict(soChoNgoi=6),409)
    assert call('GET','/xe/' + ident('X1'))['soChoNgoi']==4
    assert len(call('GET','/xe?q='+ident('X1')+'&maLoaiXe='+ident('L')+'&maNhaXe='+ident('A')+'&trangThai=SAN_SANG'))==1
    path='/xe/'+ident('X1');seats=call('GET',path+'/ghe');assert len(seats)==4 and {s['tang'] for s in seats}=={1,2}
    before=[dict(s) for s in seats]
    seats=[{k:v for k,v in s.items() if k != "maXe"} for s in seats]
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=seats[:3]),400)
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=seats+[dict(maGhe=None,viTri='EXTRA',tang=1,trangThai='HOAT_DONG')]),400)
    invalid=[dict(s) for s in seats];invalid[3]['tang']=3
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=invalid),400)
    invalid=[dict(s) for s in seats];invalid[3]['viTri']=invalid[0]['viTri'].lower()
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=invalid),400)
    invalid=[dict(s) for s in seats];invalid[3]['maGhe']=invalid[0]['maGhe']
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=invalid),400)
    invalid=[dict(s) for s in seats];invalid[3]['maGhe']=123
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=invalid),400)
    assert call('GET',path+'/ghe')==before
    for name in ('X1','X2'):
        layout=[{k:v for k,v in seat.items() if k != 'maXe'} for seat in call('GET','/xe/'+ident(name)+'/ghe')];layout[0]['viTri']='A1'
        call('PUT','/xe/'+ident(name)+'/so-do-ghe',dict(danhSachGhe=layout))
    seats=[{k:v for k,v in seat.items() if k != 'maXe'} for seat in call('GET',path+'/ghe')];seats[-1]['trangThai']='BAO_TRI'
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=seats))
    restored=[dict(s) for s in seats];restored[-1]['trangThai']='HOAT_DONG'
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=restored))
    assert all(s['trangThai']=='HOAT_DONG' for s in call('GET',path+'/ghe'))
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=seats))
    assert call('GET',path)['soDoHopLe'] is True
    call('DELETE',path+'/ghe/'+seats[0]['maGhe'],expected=409)
    call('POST','/tinh-thanh',dict(maTinhThanh=ident('CITY'),tenTinhThanh='T10'),201)
    for name in ('START','END'): call('POST','/ben-xe',dict(maBenXe=ident(name),tenBenXe=name,diaChi='Test',maTinhThanh=ident('CITY')),201)
    call('POST','/tuyen-xe',dict(maTuyen=ident('R'),tenTuyen='T10',tramKhoiHanh=ident('START'),tramDen=ident('END'),thoiGianDuKien=2,
                               thoiGianKhoiHanh='08:00',giaCoBan=100000,trangThai='DANG_KHAI_THAC',maNhaXe=ident('A')),201)
    call('GET','/tuyen-xe/'+ident('R')+'/ghe',expected=404)
    trip = dict(maChuyen=ident('C1'),maTuyen=ident('R'),ngayKhoiHanh='2030-01-01',gioKhoiHanh='08:00',maXe=ident('X1'))
    call('PUT',path,vehicle('X1',trangThai='BAO_TRI'))
    call('POST','/chuyen-xe',trip,409)
    call('PUT',path,vehicle('X1',hanDangKiem='2029-12-31'))
    call('POST','/chuyen-xe',trip,409)
    call('PUT',path,vehicle('X1'))
    call('POST','/chuyen-xe',trip,201)
    call('POST','/chuyen-xe',trip | dict(maChuyen=ident('C2'),ngayKhoiHanh='2030-01-02'),201)
    call('POST','/xe',vehicle('X3'),201)
    order,ticket=ident('O'),ident('V')
    sql(f"INSERT INTO don_dat_ve(ma_don,ma_nguoi_dung,ngay_dat,tong_tien,tien_giam,khach_dat_ten,khach_dat_so_dien_thoai,kenh_dat,trang_thai) VALUES "
        f"('{order}','{AUTH.user_id}',LOCALTIMESTAMP,100000,0,'Test','0901234567','WEB','DA_THANH_TOAN');"
        f"INSERT INTO ve_xe(ma_ve,ma_chuyen,ma_ghe,ma_don,ho_ten_khach_hang,so_dien_thoai_khach_hang,gia_ve,trang_thai) VALUES "
        f"('{ticket}','{ident('C1')}','{seats[0]['maGhe']}','{order}','Test','0901234567',100000,'DA_XUAT_VE')")
    first=call('GET','/chuyen-xe/'+ident('C1')+'/ghe',authenticated=False)['danhSachGhe']
    second=call('GET','/chuyen-xe/'+ident('C2')+'/ghe',authenticated=False)['danhSachGhe']
    assert next(s for s in first if s['maGhe']==seats[0]['maGhe'])['tinhTrangKhaDung']=='DA_BAN'
    assert next(s for s in second if s['maGhe']==seats[0]['maGhe'])['tinhTrangKhaDung']=='TRONG'
    assert any(s['tinhTrangKhaDung']=='KHONG_BAN' for s in first)
    call('PUT','/chuyen-xe/'+ident('C1')+'/xe',dict(maXe=ident('X3')),409)
    call('PUT',path,vehicle('X1',hanDangKiem='2029-12-31'),409)
    changed=[dict(s) for s in seats];changed[0]['viTri']='RENAMED'
    call('PUT',path+'/so-do-ghe',dict(danhSachGhe=changed),409)
    call('PUT',path+'/ghe/'+seats[0]['maGhe'],dict(viTri='RENAMED',tang=1,trangThai='BAO_TRI'),409)
    call('DELETE',path+'/ghe/'+seats[0]['maGhe'],expected=409)
    call('DELETE',path,expected=409)
    assert call('GET','/chuyen-xe/'+ident('C1'))['maXe']==ident('X1')
    sql(f"UPDATE nguoi_dung SET loai_tai_khoan='NHA_XE',ma_nha_xe='{ident('A')}' WHERE ma_nguoi_dung='{AUTH.user_id}'")
    assert all(x['maNhaXe']==ident('A') for x in call('GET','/xe'))
    assert call('GET','/xe?maNhaXe='+ident('B'))==[]
    call('GET','/loai-xe')
    call('GET','/xe/'+ident('X2'),expected=403)
    call('PUT','/xe/'+ident('X2'),vehicle('X2','B',trangThai='BAO_TRI'),403)
    call('PUT','/xe/'+ident('X2')+'/so-do-ghe',dict(danhSachGhe=[]),403)
    call('POST','/loai-xe',kind | dict(maLoaiXe=ident('DENIED')),403)
    call('POST','/xe',vehicle('X4','B'),403)
    own=vehicle('X4');own.pop('maNhaXe');assert call('POST','/xe',own,201)['maNhaXe']==ident('A')
    call('PUT','/xe/'+ident('X4'),own | dict(trangThai='BAO_TRI'))
    own_map=call('GET','/xe/'+ident('X4')+'/ghe')
    own_map=[{k:v for k,v in seat.items() if k != 'maXe'} for seat in own_map]
    own_map[0]['trangThai']='BAO_TRI'
    call('PUT','/xe/'+ident('X4')+'/so-do-ghe',dict(danhSachGhe=own_map))
    assert len(call('GET','/xe/pham-vi'))==1
    sql(f"UPDATE nguoi_dung SET loai_tai_khoan='KHACH_HANG',ma_nha_xe=NULL WHERE ma_nguoi_dung='{AUTH.user_id}'")
    call('GET','/xe',expected=403)
    print(f'PASS: T10 validation, atomic layout, inspection, maintenance, per-trip seats, sold-trip protection and scope; {checks} HTTP checks')
if __name__=='__main__':
    try: AUTH.start();main()
    finally: cleanup();AUTH.close()
