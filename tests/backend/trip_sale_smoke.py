"""T12 real HTTP/PostgreSQL tests. Use a disposable development database; own fixtures only."""
import json
import os
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from auth_fixture import AdminFixture

BASE=os.environ.get('API_BASE_URL','http://localhost:8080/api').rstrip('/')
MANAGE='/quan-ly/chuyen-xe'
PREFIX='T12_'+uuid.uuid4().hex[:8]+'_'
auth=AdminFixture(BASE);other=AdminFixture(BASE)
checks=0;counter_lock=threading.Lock()
day=(datetime.now(timezone(timedelta(hours=7))).date()+timedelta(days=30)).isoformat()
def ident(code):return PREFIX+code
def sql(statement):auth.sql(statement)
def call(method,path,body=None,expected=200,actor=auth,anonymous=False,status=False):
    global checks
    request=Request(BASE+path,method=method,headers=actor.headers(),data=None if body is None else json.dumps(body).encode())
    try:response=(urlopen if anonymous else actor.opener.open)(request,timeout=30)
    except HTTPError as error:response=error
    with response:
        raw=response.read();data=json.loads(raw) if raw else None
        allowed=expected if isinstance(expected,tuple) else (expected,)
        assert response.status in allowed,(method,path,expected,response.status,raw[:800])
        if response.status>=400:assert all(k in data for k in ('code','message','details','timestamp','traceId')),data
        with counter_lock:checks+=1
        return (response.status,data) if status else data
def draft(code,vehicle='X1',route='R',**changes):
    return dict(maChuyen=ident(code),maTuyen=ident(route),maXe=ident(vehicle) if vehicle else None,ngayKhoiHanh=day,gioKhoiHanh='08:00')|changes
def vehicle(code,owner='A',**changes):
    return dict(maXe=ident(code),bienSo='51B-'+PREFIX.replace('_','-')+code.replace('_','-'),maLoaiXe=ident('L'),maNhaXe=ident(owner),ngayDangKiem='2020-01-01',hanDangKiem='2100-01-01',trangThai='SAN_SANG')|changes
def crew(code):
    for i in range(1,4):
        suffix=code+'E'+str(i);assistant=i==3
        phone='09'+str(int(uuid.uuid5(uuid.NAMESPACE_DNS,ident(suffix)).hex[:8],16)%100000000).zfill(8)
        call('POST','/nhan-vien',dict(maNhanVien=ident(suffix),hoTen='Nhân viên kiểm thử '+str(i),soDienThoai=phone,email=ident(suffix).lower()+'@example.test',maNhaXe=ident('A'),
            loaiNhanVien='PHU_XE' if assistant else 'TAI_XE',trangThai='DANG_LAM_VIEC',soBangLai=None if assistant else '123456789012',ngayHetHanBangLai=None if assistant else '2100-01-01'),201)
        call('POST','/phan-cong',dict(maPhanCong=ident(code+'P'+str(i)),maChuyen=ident(code),maNhanVien=ident(suffix),vaiTro={1:'TAI_XE_CHINH',2:'TAI_XE_PHU',3:'PHU_XE'}[i],xacNhanKeHoachLaiNghi=True,ghiChu='Kế hoạch kiểm thử T12'),201)
def detail(code):return call('GET',MANAGE+'/'+ident(code))
def report(code):return call('GET',MANAGE+'/'+ident(code)+'/dieu-kien-mo-ban')
def blocked(code,key):
    result=report(code);assert not result['duDieuKien'];assert any(c['maDieuKien']==key and not c['dat'] for c in result['dieuKien'])
    call('POST',MANAGE+'/'+ident(code)+'/mo-ban',expected=409)
def search(date=day):return call('GET','/chuyen-xe?from='+ident('S')+'&to='+ident('D')+'&date='+date,anonymous=True)
def main():
    call('GET',MANAGE,expected=401,anonymous=True)
    for owner in ('A','B'):sql(f"INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,trang_thai) VALUES ('{ident(owner)}','Nhà xe {owner}','HOAT_DONG')")
    call('POST','/tinh-thanh',dict(maTinhThanh=ident('CITY'),tenTinhThanh='T12'),201)
    for station in ('S','D'):call('POST','/ben-xe',dict(maBenXe=ident(station),tenBenXe=station,diaChi=station,maTinhThanh=ident('CITY')),201)
    for route,owner,start,end in [('R','A','S','D'),('RETURN','A','D','S'),('RB','B','S','D')]:
        call('POST','/tuyen-xe',dict(maTuyen=ident(route),tenTuyen='Tuyến kiểm thử '+route,tramKhoiHanh=ident(start),tramDen=ident(end),thoiGianKhoiHanh='08:00',thoiGianDuKien=7,giaCoBan=100000,trangThai='DANG_KHAI_THAC',maNhaXe=ident(owner)),201)
    call('POST','/loai-xe',dict(maLoaiXe=ident('L'),tenLoaiXe='4 ghế',soChoNgoi=4,soTang=2),201)
    for code,owner in [('X1','A'),('X2','A'),('CLOSE_X','A'),('XB','B')]:call('POST','/xe',vehicle(code,owner),201)
    created=call('POST',MANAGE,draft('C1'),201);assert created['trangThai']=='NHAP' and created['nhanVienPhanCong']==[] and created['giaVeDuKien']=='100000' and not created['coTheDatVe']
    assert search()==[];blocked('C1','NHAN_SU')
    for changes,expected in [(dict(maTuyen=ident('MISSING')),404),(dict(maXe=ident('MISSING')),404),(dict(giaVeDuKien=-1),400),
                             (dict(giaVeDuKien=200000),400),(dict(gioDenDuKien=day+'T07:59:00'),400),(dict(gioDenDuKien=day+'T08:00:00'),400),
                             (dict(maChuyen=123),400),(dict(ngayKhoiHanh=None),400),(dict(role='ADMIN'),400)]:
        call('POST',MANAGE,draft('INVALID')|changes,expected)
    assert call('GET',MANAGE+'?maNhaXe='+ident('A'))['totalItems']==1
    night=call('POST',MANAGE,draft('NIGHT',vehicle=None,gioKhoiHanh='22:00'),201)
    assert night['gioDenDuKien']==(datetime.fromisoformat(day)+timedelta(days=1)).strftime('%Y-%m-%dT05:00:00')
    assert call('PUT',MANAGE+'/'+ident('NIGHT'),draft('NIGHT',vehicle=None,gioKhoiHanh='21:00'))['gioDenDuKien'].endswith('T04:00:00')
    call('DELETE',MANAGE+'/'+ident('NIGHT'),expected=204)
    call('GET',MANAGE+'?page=0',expected=400);call('GET',MANAGE+'?trangThai=INVALID',expected=400)
    crew('C1');assert report('C1')['duDieuKien']
    # Mutate only this run's fixtures to prove all checks are evaluated from current DB values.
    for table,column,key,state,normal,condition in [('tuyen_xe','trang_thai','R','NGUNG_KHAI_THAC','DANG_KHAI_THAC','TUYEN_BEN'),
        ('ben_xe','trang_thai','D','BAO_TRI','HOAT_DONG','TUYEN_BEN'),('nha_xe','trang_thai','A','TAM_NGUNG','HOAT_DONG','NHA_XE'),
        ('xe','trang_thai','X1','BAO_TRI','SAN_SANG','XE_DANG_KIEM')]:
        pk={'tuyen_xe':'ma_tuyen','ben_xe':'ma_ben_xe','nha_xe':'ma_nha_xe','xe':'ma_xe'}[table]
        sql(f"UPDATE {table} SET {column}='{state}' WHERE {pk}='{ident(key)}'");blocked('C1',condition)
        assert detail('C1')['trangThai']=='NHAP';sql(f"UPDATE {table} SET {column}='{normal}' WHERE {pk}='{ident(key)}'")
    sql(f"UPDATE xe SET han_dang_kiem=DATE '{day}'-1 WHERE ma_xe='{ident('X1')}'");blocked('C1','XE_DANG_KIEM')
    sql(f"UPDATE xe SET han_dang_kiem=DATE '2100-01-01' WHERE ma_xe='{ident('X1')}'")
    seats=call('GET','/xe/'+ident('X1')+'/ghe');seat=seats[0]['maGhe']
    sql(f"UPDATE ghe SET tang=3 WHERE ma_ghe='{seat}'");blocked('C1','XE_DANG_KIEM');sql(f"UPDATE ghe SET tang={seats[0]['tang']} WHERE ma_ghe='{seat}'")
    sql(f"UPDATE nhan_vien SET ngay_het_han_bang_lai=DATE '{day}'-1 WHERE ma_nhan_vien='{ident('C1E1')}'");blocked('C1','NHAN_SU')
    sql(f"UPDATE nhan_vien SET ngay_het_han_bang_lai=DATE '2100-01-01',loai_nhan_vien='DIEU_HANH' WHERE ma_nhan_vien='{ident('C1E1')}'");blocked('C1','NHAN_SU')
    sql(f"UPDATE nhan_vien SET loai_nhan_vien='TAI_XE' WHERE ma_nhan_vien='{ident('C1E1')}'")
    assert call('POST',MANAGE+'/'+ident('C1')+'/mo-ban')['trangThai']=='DANG_MO_BAN'
    assert call('POST',MANAGE+'/'+ident('C1')+'/mo-ban')['trangThai']=='DANG_MO_BAN'
    assert [c['maChuyen'] for c in search()]==[ident('C1')]
    call('PUT',MANAGE+'/'+ident('C1'),draft('C1',gioKhoiHanh='10:00'),409)
    call('PUT','/chuyen-xe/'+ident('C1'),dict(ngayKhoiHanh=day,gioKhoiHanh='10:00'),409)
    call('POST',MANAGE,draft('UPDATE',route='RETURN',gioKhoiHanh='16:00'),201)
    call('PUT',MANAGE+'/'+ident('UPDATE'),draft('UPDATE',route='RETURN',gioKhoiHanh='10:00'),409)
    assert detail('UPDATE')['gioKhoiHanh']=='16:00:00'
    # Two independent administrator sessions race to publish two drafts with one vehicle.
    for code in ('RACE1','RACE2'):call('POST',MANAGE,draft(code,vehicle='X2'),201);crew(code)
    def publish(item):
        code,actor=item;return call('POST',MANAGE+'/'+ident(code)+'/mo-ban',expected=(200,409),actor=actor,status=True)
    with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(publish,[('RACE1',auth),('RACE2',other)]))
    assert sorted(r[0] for r in results)==[200,409]
    winner=results[0][1]['maChuyen'] if results[0][0]==200 else results[1][1]['maChuyen']
    assert call('GET',MANAGE+'?maXe='+ident('X2')+'&trangThai=DANG_MO_BAN')['totalItems']==1
    held=call('GET','/xe/'+ident('X2')+'/ghe')[0]['maGhe']
    sql(f"INSERT INTO giu_cho(ma_giu_cho,ma_chuyen,ma_ghe,ngay_dat,thoi_diem_bat_dau,thoi_diem_het_han,trang_thai) VALUES ('{ident('H')}', '{winner}','{held}',LOCALTIMESTAMP,LOCALTIMESTAMP,LOCALTIMESTAMP+INTERVAL '15 minutes','DANG_GIU')")
    call('PUT','/chuyen-xe/'+winner+'/xe',dict(maXe=ident('CLOSE_X')),409)
    call('POST',MANAGE+'/'+winner+'/huy',expected=409)
    sql(f"INSERT INTO don_dat_ve(ma_don,ma_nguoi_dung,ngay_dat,tong_tien,tien_giam,khach_dat_ten,khach_dat_so_dien_thoai,kenh_dat,trang_thai) VALUES ('{ident('O')}','{auth.user_id}',LOCALTIMESTAMP,100000,0,'Test','0901234567','WEB','DA_THANH_TOAN');"
        f"INSERT INTO ve_xe(ma_ve,ma_chuyen,ma_ghe,ma_don,ho_ten_khach_hang,so_dien_thoai_khach_hang,gia_ve,trang_thai) VALUES ('{ident('V')}','{ident('C1')}','{seat}','{ident('O')}','Test','0901234567',100000,'DA_XUAT_VE')")
    call('DELETE',MANAGE+'/'+ident('C1'),expected=409);call('POST',MANAGE+'/'+ident('C1')+'/huy',expected=409)
    call('PUT','/chuyen-xe/'+ident('C1')+'/xe',dict(maXe=ident('CLOSE_X')),409)
    call('PUT','/xe/'+ident('X1')+'/ghe/'+seat,dict(viTri='RENAMED',tang=1,trangThai='HOAT_DONG'),409)
    assert detail('C1')['maXe']==ident('X1') and detail('C1')['gioKhoiHanh']=='08:00:00'
    # Represent a previously opened trip whose booking window has ended; never trust its state alone.
    start=datetime.now(timezone(timedelta(hours=7))).replace(tzinfo=None,second=0,microsecond=0)+timedelta(minutes=15)
    call('POST',MANAGE,draft('CLOSED',vehicle='CLOSE_X',ngayKhoiHanh=start.date().isoformat(),gioKhoiHanh=start.time().isoformat()),201);crew('CLOSED')
    sql(f"UPDATE chuyen_xe SET trang_thai='DANG_MO_BAN' WHERE ma_chuyen='{ident('CLOSED')}'")
    blocked('CLOSED','HAN_DAT_VE');assert detail('CLOSED')['daHetHanDatVe'] and not detail('CLOSED')['coTheDatVe'];assert all(c['maChuyen']!=ident('CLOSED') for c in search(start.date().isoformat()))
    call('GET','/chuyen-xe/'+ident('CLOSED')+'/ghe',expected=409,anonymous=True)
    call('POST',MANAGE,draft('FOREIGN',vehicle='XB',route='RB'),201)
    sql(f"UPDATE nguoi_dung SET loai_tai_khoan='NHA_XE',ma_nha_xe='{ident('A')}' WHERE ma_nguoi_dung='{auth.user_id}'")
    assert all(c['maNhaXe']==ident('A') for c in call('GET',MANAGE)['items'])
    assert call('POST',MANAGE,draft('OWN_NX'),201)['maNhaXe']==ident('A')
    call('GET',MANAGE+'?maNhaXe='+ident('B'),expected=403)
    for method,end,body in [('GET','',None),('PUT','',draft('FOREIGN',vehicle='XB',route='RB')),('POST','/mo-ban',None),('DELETE','',None)]:
        call(method,MANAGE+'/'+ident('FOREIGN')+end,body,403)
    call('POST',MANAGE,draft('ESCAPE',vehicle='XB',route='RB'),403)
    sql(f"UPDATE nguoi_dung SET loai_tai_khoan='KHACH_HANG',ma_nha_xe=NULL WHERE ma_nguoi_dung='{auth.user_id}'")
    call('GET',MANAGE,expected=403);call('POST',MANAGE+'/'+ident('C1')+'/mo-ban',expected=403)
    print(f'PASS: {checks} T12 HTTP checks; draft/T11/publish, overnight, eligibility, cutoff, history, scope and concurrent opening.')
def cleanup():
    if auth.user_id:sql(f"UPDATE nguoi_dung SET loai_tai_khoan='ADMIN',ma_nha_xe=NULL WHERE ma_nguoi_dung='{auth.user_id}'")
    for table,key in [('giu_cho','ma_giu_cho'),('ve_xe','ma_ve'),('don_dat_ve','ma_don'),('phan_cong_chuyen_xe','ma_phan_cong'),('chuyen_xe','ma_chuyen'),
                     ('ghe','ma_xe'),('xe','ma_xe'),('loai_xe','ma_loai_xe'),('tuyen_xe','ma_tuyen'),('ben_xe','ma_ben_xe'),('tinh_thanh','ma_tinh_thanh'),('nhan_vien','ma_nhan_vien'),('nha_xe','ma_nha_xe')]:
        sql(f"DELETE FROM {table} WHERE left({key},{len(PREFIX)})='{PREFIX}'")
if __name__=='__main__':
    try:auth.start();other.start();main()
    finally:cleanup();auth.close();other.close()
