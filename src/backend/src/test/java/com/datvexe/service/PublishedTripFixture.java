package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.security.RequestIdentity;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** Enriches legacy positive sale/search fixtures, then uses the actual T12 opening service. */
public final class PublishedTripFixture {
    private PublishedTripFixture() {}
    public static void open(EntityManagerFactory factory,String code) {
        var context=RequestIdentity.get();RequestIdentity.clear();
        try {
            try(var em=factory.createEntityManager()) {
                em.getTransaction().begin();var trip=em.find(ChuyenXe.class,code);
                if(trip.tuyenXe.nhaXe==null) {
                    if(trip.xe!=null)trip.tuyenXe.nhaXe=trip.xe.nhaXe;
                    else {
                        NhaXe owner=em.find(NhaXe.class,"PUBLISHED_NX");
                        if(owner==null){owner=new NhaXe();owner.maNhaXe="PUBLISHED_NX";owner.tenNhaXe="Published fixture";em.persist(owner);}
                        trip.tuyenXe.nhaXe=owner;
                    }
                }
                if(trip.xe==null) {
                    LoaiXe type=em.find(LoaiXe.class,"PUBLISHED_L");
                    if(type==null){type=new LoaiXe();type.maLoaiXe="PUBLISHED_L";type.tenLoaiXe="Fixture";type.soChoNgoi=4;em.persist(type);}
                    var vehicle=new Xe();vehicle.maXe="PUB_"+code;vehicle.bienSo="PUB-"+code;
                    vehicle.loaiXe=type;vehicle.nhaXe=trip.tuyenXe.nhaXe;vehicle.soChoNgoi=4;
                    vehicle.ngayDangKiem=LocalDate.of(2020,1,1);vehicle.hanDangKiem=LocalDate.of(2100,1,1);em.persist(vehicle);
                    for(int i=1;i<=4;i++){var seat=new Ghe();seat.maGhe=vehicle.maXe+"_"+i;seat.xe=vehicle;seat.viTri="G"+i;vehicle.danhSachGhe.add(seat);em.persist(seat);}
                    trip.xe=vehicle;
                }
                if(em.createQuery("select count(p) from PhanCongChuyenXe p where p.chuyenXe=:trip",Long.class).setParameter("trip",trip).getSingleResult()==0) {
                    for(int i=1;i<=3;i++) {
                        String id=UUID.nameUUIDFromBytes((code+"-crew-"+i).getBytes(StandardCharsets.UTF_8)).toString();
                        var staff=new NhanVien();staff.maNhanVien=id;staff.hoTen="Crew "+i;staff.email=id+"@example.test";
                        staff.soDienThoai="TEST-"+code+"-"+i;staff.nhaXe=trip.tuyenXe.nhaXe;
                        staff.loaiNhanVien=i==3?LoaiNhanVien.PHU_XE:LoaiNhanVien.TAI_XE;
                        staff.soBangLai=i==3?null:"123456789012";staff.ngayHetHanBangLai=i==3?null:LocalDate.of(2100,1,1);em.persist(staff);
                        var assignment=new PhanCongChuyenXe();assignment.maPhanCong=id;assignment.chuyenXe=trip;assignment.nhanVien=staff;
                        assignment.vaiTro=i==1?VaiTroChuyen.TAI_XE_CHINH:i==2?VaiTroChuyen.TAI_XE_PHU:VaiTroChuyen.PHU_XE;
                        assignment.ghiChu=AssignmentRules.reviewPrefix(trip)+"TEST] Kế hoạch kiểm thử";em.persist(assignment);
                    }
                }
                em.getTransaction().commit();
            }
            new TripService(factory).open(code);
        } finally {if(context!=null)RequestIdentity.set(context);}
    }
}
