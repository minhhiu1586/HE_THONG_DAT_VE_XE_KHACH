"""Positive sale fixtures for legacy regression scripts; invokes actual T11 and T12 APIs."""
import hashlib

def publish_trip(auth,trip_id):
    trip=auth.send('GET','/chuyen-xe/'+trip_id)
    route=auth.send('GET','/tuyen-xe/'+trip['maTuyen'])
    assert trip['maXe'] and route['maNhaXe'],'A published fixture needs a vehicle and an operator'
    for index in range(1,4):
        code=trip_id+'_E'+str(index);assistant=index==3
        phone='09'+str(int(hashlib.sha256(code.encode()).hexdigest()[:8],16)%100000000).zfill(8)
        auth.send('POST','/nhan-vien',dict(maNhanVien=code,hoTen='Nhân viên kiểm thử '+str(index),soDienThoai=phone,email=code.lower()+'@example.test',maNhaXe=route['maNhaXe'],
            loaiNhanVien='PHU_XE' if assistant else 'TAI_XE',trangThai='DANG_LAM_VIEC',soBangLai=None if assistant else '123456789012',ngayHetHanBangLai=None if assistant else '2100-01-01'))
        auth.send('POST','/phan-cong',dict(maPhanCong=trip_id+'_P'+str(index),maChuyen=trip_id,maNhanVien=code,vaiTro={1:'TAI_XE_CHINH',2:'TAI_XE_PHU',3:'PHU_XE'}[index],xacNhanKeHoachLaiNghi=True,ghiChu='Kế hoạch cho dữ liệu kiểm thử'))
    return auth.send('POST','/quan-ly/chuyen-xe/'+trip_id+'/mo-ban')
