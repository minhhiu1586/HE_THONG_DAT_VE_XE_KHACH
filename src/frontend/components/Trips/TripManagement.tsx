'use client';

import { FormEvent, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/components/Account/AuthProvider';
import { errorMessage } from '@/lib/account-api';
import { vietnameseValidation, clearValidation } from '@/lib/form-validation';
import { useManagementFocus } from '@/lib/use-management-focus';
import { routeChoices, routeFilterChoices, sortedUnique } from '@/lib/management-options';
import TripPassengerModal from './TripPassengerModal';
import styles from './TripManagement.module.css';

type Route={maTuyen:string;tenTuyen:string;maNhaXe:string|null;giaCoBan:number;thoiGianDuKien:number;trangThai:string};
type Operator={maNhaXe:string;tenNhaXe:string};
type Vehicle={maXe:string;bienSo:string;maNhaXe:string;trangThai:string};
type Crew={maPhanCong:string;maNhanVien:string;hoTen:string;vaiTro:string;trangThai:string;ngayHetHanBangLai:string|null;ghiChu:string|null};
type Trip={maChuyen:string;maTuyen:string;tenTuyen:string;maNhaXe:string;tenNhaXe:string;maXe:string|null;bienSo:string|null;ngayKhoiHanh:string;gioKhoiHanh:string;gioDenDuKien:string;giaVeDuKien:string;trangThai:string;ngungNhanDatVeLuc:string;phutDungNhanDatVe:number;coTheDatVe:boolean;daHetHanDatVe:boolean;coTheSua:boolean;coTheXoa:boolean;coTheHuy:boolean;nhanVienPhanCong:Crew[]};
type Page={items:Trip[];page:number;totalPages:number;totalItems:number};
type Report={duDieuKien:boolean;ngungNhanDatVeLuc:string;dieuKien:{maDieuKien:string;tenDieuKien:string;dat:boolean;lyDo:string|null}[]};
type Lifecycle={maChuyen:string;trangThai:string;tongQuanHoan:{tongSoVeBiAnhHuong:number;soVeCanHoan:number;soVeDaHoan:number;tongTienDuKien:string;tongTienDaHoan:string;soYeuCauThanhCong:number;soYeuCauCanTheoDoi:number};lyLich:{trangThaiCu:string;trangThaiMoi:string;maNguoiThaoTac:string;vaiTro:string;lyDo:string;thoiDiem:string}[];khachBiAnhHuong:{veBiAnhHuong:{maVe:string;trangThai:string;giaThucTra:string}[];maDon:string;trangThaiXuLy:string;lyDoXuLy:string;lyDo:string;soVeConHieuLuc:number;yeuCauHoan:{maHoanTien:string;trangThai:string;soTienHoan:string;lyDoLoi:string}[]}[];thongBao:{trangThai:string;soLuong:number}[]};
const states:Record<string,string>={NHAP:'Nháp',CHUA_KHOI_HANH:'Chưa mở bán (dữ liệu cũ)',DANG_MO_BAN:'Đang mở bán',DANG_CHAY:'Đang chạy',HOAN_TAT:'Hoàn tất',DA_HUY:'Đã hủy'};
const crewRoles:Record<string,string>={TAI_XE_CHINH:'Tài xế chính',TAI_XE_PHU:'Tài xế phụ',PHU_XE:'Phụ xe',TAI_XE:'Tài xế'};
const empty={maChuyen:'',maTuyen:'',maXe:'',ngayKhoiHanh:'',gioKhoiHanh:'',gioDenDuKien:''};
const API='/quan-ly/chuyen-xe';
function estimated(date:string,time:string,hours:number|undefined) {
  if(!date || !time || !hours)return '';
  const value=new Date(`${date}T${time}:00Z`);
  if(Number.isNaN(value.getTime()))return '';
  return new Date(value.getTime()+Math.round(hours*60)*60000).toISOString().slice(0,16);
}
function dateTime(value:string){return value.replace('T',' ').slice(0,16);}
function stateLabel(trip:Trip){return trip.trangThai==='DANG_MO_BAN'&&!trip.coTheDatVe?(trip.daHetHanDatVe?'Đã hết hạn nhận vé':'Tạm không đủ điều kiện bán'):states[trip.trangThai];}
export default function TripManagement(){
  const {request}=useAuth();
  const [routes,setRoutes]=useState<Route[]>([]),[vehicles,setVehicles]=useState<Vehicle[]>([]);
  const [operators,setOperators]=useState<Operator[]>([]);
  const [page,setPage]=useState<Page>({items:[],page:1,totalPages:0,totalItems:0});
  const [form,setForm]=useState(empty),[editing,setEditing]=useState(false);
  const [filters,setFilters]=useState({maTuyen:'',maXe:'',trangThai:'',tuNgay:'',denNgay:''});
  const [selected,setSelected]=useState<Trip|null>(null),[report,setReport]=useState<Report|null>(null);
  const [lifecycle,setLifecycle]=useState<Lifecycle|null>(null),[cancelReason,setCancelReason]=useState(''),[confirmCancel,setConfirmCancel]=useState(false);
  const [viewingPassengersTrip, setViewingPassengersTrip] = useState<string | null>(null);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[success,setSuccess]=useState('');
  const [initialLoading,setInitialLoading]=useState(true);const waiting=busy||initialLoading;
  const sending=useRef(false),revision=useRef(0);
  const [formTarget, focusForm]=useManagementFocus<HTMLElement>(),[detailTarget,focusDetail]=useManagementFocus<HTMLElement>();
  function clearNotice(){revision.current++;setError('');setSuccess('');}
  function interaction(event:React.SyntheticEvent<HTMLDivElement>){if(event.target instanceof Element && event.target.closest('button,input,select,a'))clearNotice();}
  async function load(number=1){
    const params=new URLSearchParams({page:String(number),size:'20'});
    Object.entries(filters).forEach(([key,value])=>{if(value)params.set(key,value);});
    setPage(await request<Page>(`${API}?${params}`));
  }
  async function action(operation:()=>Promise<void>,message=''){
    if(sending.current||initialLoading)return;clearNotice();const version=revision.current;sending.current=true;setBusy(true);
    try{await operation();if(version===revision.current)setSuccess(message);}
    catch(failure){if(version===revision.current)setError(errorMessage(failure));}
    finally{sending.current=false;setBusy(false);}
  }
  useEffect(()=>{
    let active=true;
    Promise.all([request<Route[]>('/tuyen-xe'),request<Vehicle[]>('/xe'),request<Page>(`${API}?page=1&size=20`),request<Operator[]>('/xe/pham-vi')])
      .then(([r,v,p,o])=>{if(active){setRoutes(sortedUnique(r,item=>item.maTuyen,item=>item.tenTuyen));setVehicles(sortedUnique(v,item=>item.maXe,item=>item.bienSo));setPage(p);setOperators(sortedUnique(o,item=>item.maNhaXe,item=>item.tenNhaXe));}})
      .catch(failure=>{if(active)setError(errorMessage(failure));})
      .finally(()=>{if(active)setInitialLoading(false);});
    return()=>{active=false;};
  },[request]);
  async function detail(code:string,navigate=true){
    const [trip,checks,history]=await Promise.all([request<Trip>(`${API}/${encodeURIComponent(code)}`),request<Report>(`${API}/${encodeURIComponent(code)}/dieu-kien-mo-ban`),request<Lifecycle>(`${API}/${encodeURIComponent(code)}/vong-doi`)]);
    setSelected(trip);setReport(checks);setLifecycle(history);setConfirmCancel(false);
    if(navigate)focusDetail();
  }
  const route=routes.find(r=>r.maTuyen===form.maTuyen);
  const choices=routeChoices(routes,operators);
  const filterChoices=routeFilterChoices(routes);
  const arrival=form.gioDenDuKien || estimated(form.ngayKhoiHanh,form.gioKhoiHanh,route?.thoiGianDuKien);
  function edit(trip:Trip){setEditing(true);setForm({maChuyen:trip.maChuyen,maTuyen:trip.maTuyen,maXe:trip.maXe??'',ngayKhoiHanh:trip.ngayKhoiHanh,gioKhoiHanh:trip.gioKhoiHanh.slice(0,5),gioDenDuKien:trip.gioDenDuKien.slice(0,16)});focusForm('input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not(:disabled)');}
  function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();void action(async()=>{
      const saved=await request<Trip>(editing?`${API}/${encodeURIComponent(form.maChuyen)}`:API,editing?'PUT':'POST',
        {...form,maXe:form.maXe||null,gioDenDuKien:arrival?`${arrival}:00`:null});
      setForm(empty);setEditing(false);await load();await detail(saved.maChuyen);
    },editing?'Đã cập nhật chuyến nháp.':'Đã tạo chuyến nháp. Chuyến chưa được mở bán.');
  }
  return <div className={styles.page} onClickCapture={interaction} onFocusCapture={interaction} onInputCapture={interaction} onChangeCapture={interaction}>
    <header className={styles.heading}><p className={styles.eyebrow}>QUẢN TRỊ VẬN HÀNH</p><h1>Quản lý chuyến xe và mở bán</h1><p>Tạo lịch chạy, hoàn tất phân công và kiểm tra điều kiện trước khi mở bán.</p></header>
    {waiting&&<p role="status">{initialLoading?'Đang tải dữ liệu…':'Đang xử lý…'}</p>}{error&&<p role="alert" className={styles.error}>{error}</p>}{success&&<p role="status" className={styles.success}>{success}</p>}
    <section ref={formTarget} className={styles.panel}><h2 data-management-focus tabIndex={-1}>{editing?'Sửa chuyến nháp':'Tạo chuyến nháp'}</h2>
      <form className={styles.form} onSubmit={submit} onInvalid={vietnameseValidation} onInput={clearValidation} onChange={clearValidation}>
        <label>Mã chuyến<input data-validation-label="mã chuyến" required maxLength={40} pattern="[A-Za-z0-9_-]+" disabled={editing} value={form.maChuyen} onChange={e=>setForm({...form,maChuyen:e.target.value})}/></label>
        <label>Tuyến xe<select aria-label="Tuyến xe" data-validation-label="tuyến xe" required value={form.maTuyen} onChange={e=>setForm({...form,maTuyen:e.target.value,maXe:'',gioDenDuKien:''})}><option value="">Chọn tuyến</option>{choices.map(choice=><option key={choice.value} value={choice.value} disabled={choice.disabled}>{choice.label}</option>)}</select></label>
        <label>Xe dự kiến (không bắt buộc)<select aria-label="Xe dự kiến (không bắt buộc)" value={form.maXe} onChange={e=>setForm({...form,maXe:e.target.value})}><option value="">Chưa gán xe</option>{vehicles.filter(v=>v.maNhaXe===route?.maNhaXe).map(v=><option key={v.maXe} value={v.maXe}>{v.bienSo} · {v.maXe}</option>)}</select></label>
        <label>Nhà xe vận hành<input readOnly value={operators.find(o=>o.maNhaXe===route?.maNhaXe)?.tenNhaXe??route?.maNhaXe??''}/></label>
        <label>Ngày khởi hành<input data-validation-label="ngày khởi hành" required type="date" value={form.ngayKhoiHanh} onChange={e=>setForm({...form,ngayKhoiHanh:e.target.value,gioDenDuKien:''})}/></label>
        <label>Giờ khởi hành<input data-validation-label="giờ khởi hành" required type="time" value={form.gioKhoiHanh} onChange={e=>setForm({...form,gioKhoiHanh:e.target.value,gioDenDuKien:''})}/></label>
        <label>Ngày giờ đến dự kiến<input data-validation-label="ngày giờ đến dự kiến" required type="datetime-local" value={arrival} onChange={e=>setForm({...form,gioDenDuKien:e.target.value})}/></label>
        <label>Giá vé dự kiến (đồng)<input readOnly value={route?.giaCoBan?.toLocaleString('vi-VN')??''}/></label>
        <p className={styles.wide}>Giá lấy từ tuyến. Có thể lưu nháp khi chưa gán xe hoặc chưa phân công đủ nhân viên.</p>
        <div className={`${styles.buttons} ${styles.wide}`}><button disabled={waiting}>{editing?'Lưu chuyến nháp':'Tạo chuyến nháp'}</button>{editing&&<button type="button" onClick={()=>{setEditing(false);setForm(empty);}}>Hủy sửa</button>}</div>
      </form>
    </section>
    <section className={styles.panel}><h2>Danh sách chuyến</h2>
      <form className={styles.filters} onSubmit={e=>{e.preventDefault();void action(()=>load());}}>
        <label>Lọc tuyến<select aria-label="Lọc tuyến" value={filters.maTuyen} onChange={e=>setFilters({...filters,maTuyen:e.target.value})}><option value="">Tất cả tuyến</option>{filterChoices.map(choice=><option key={choice.value} value={choice.value}>{choice.label}</option>)}</select></label>
        <label>Lọc xe<select aria-label="Lọc xe" value={filters.maXe} onChange={e=>setFilters({...filters,maXe:e.target.value})}><option value="">Tất cả xe</option>{vehicles.map(v=><option key={v.maXe} value={v.maXe}>{v.bienSo}</option>)}</select></label>
        <label>Lọc trạng thái<select aria-label="Lọc trạng thái" value={filters.trangThai} onChange={e=>setFilters({...filters,trangThai:e.target.value})}><option value="">Tất cả trạng thái</option>{Object.entries(states).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
        <label>Từ ngày<input type="date" value={filters.tuNgay} onChange={e=>setFilters({...filters,tuNgay:e.target.value})}/></label><label>Đến ngày<input type="date" value={filters.denNgay} onChange={e=>setFilters({...filters,denNgay:e.target.value})}/></label>
        <button disabled={waiting}>Lọc chuyến</button>
      </form>
      <div className={styles.scroll}><table><thead><tr><th>Mã chuyến / tuyến</th><th>Khởi hành → đến</th><th>Xe</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{page.items.map(trip=><tr key={trip.maChuyen}>
        <td>{trip.maChuyen}<small>{trip.tenTuyen}</small><small>{trip.tenNhaXe}</small></td><td>{trip.ngayKhoiHanh} {trip.gioKhoiHanh.slice(0,5)}<small>→ {dateTime(trip.gioDenDuKien)}</small></td><td>{trip.bienSo??'Chưa gán'}</td><td>{stateLabel(trip)}</td><td className={styles.buttons}>
          <button disabled={waiting} onClick={()=>void action(()=>detail(trip.maChuyen))}>Chi tiết / mở bán</button>
          <button disabled={waiting} onClick={()=>setViewingPassengersTrip(trip.maChuyen)}>Hành khách & Vé</button>
          <button disabled={waiting||!trip.coTheSua} onClick={()=>edit(trip)}>Sửa</button>
          <button disabled={waiting||!trip.coTheXoa} onClick={()=>{if(window.confirm(`Xóa chuyến nháp ${trip.maChuyen}?`))void action(async()=>{await request(`${API}/${encodeURIComponent(trip.maChuyen)}`,'DELETE');await load();if(selected?.maChuyen===trip.maChuyen){setSelected(null);setReport(null);}},'Đã xóa chuyến nháp.');}}>Xóa</button>
        </td></tr>)}{page.items.length===0&&<tr><td colSpan={5}>Không có chuyến phù hợp.</td></tr>}</tbody></table></div>
      <div className={styles.buttons}><button disabled={waiting||page.page<=1} onClick={()=>void action(()=>load(page.page-1))}>Trang trước</button><span>Trang {page.page} · {page.totalItems} chuyến</span><button disabled={waiting||page.page>=page.totalPages} onClick={()=>void action(()=>load(page.page+1))}>Trang sau</button></div>
    </section>
    {selected&&<section ref={detailTarget} className={styles.panel}><h2 data-management-focus tabIndex={-1}>Chi tiết chuyến · {selected.maChuyen}</h2><p>{selected.tenTuyen} · {selected.tenNhaXe} · {selected.bienSo??'Chưa gán xe'}</p>
      <p>Trạng thái: <strong>{stateLabel(selected)}</strong>. Giá dự kiến: {Number(selected.giaVeDuKien).toLocaleString('vi-VN')} đồng.</p>
      <p>Ngừng nhận đặt vé: <strong>{dateTime(selected.ngungNhanDatVeLuc)}</strong> ({selected.phutDungNhanDatVe} phút trước khởi hành, giờ Việt Nam).</p>
      <h3>Nhân viên đã phân công</h3><div className={styles.scroll}><table><thead><tr><th>Họ tên</th><th>Vai trò</th><th>Trạng thái</th><th>Hạn bằng lái</th></tr></thead><tbody>{selected.nhanVienPhanCong.map(c=><tr key={c.maPhanCong}><td>{c.hoTen}</td><td>{crewRoles[c.vaiTro]??c.vaiTro}</td><td>{c.trangThai==='DANG_LAM_VIEC'?'Đang làm việc':c.trangThai}</td><td>{c.ngayHetHanBangLai??'Không áp dụng'}</td></tr>)}{selected.nhanVienPhanCong.length===0&&<tr><td colSpan={4}>Chưa có nhân viên được phân công. Cần hoàn tất phân công trước khi mở bán.</td></tr>}</tbody></table></div>
      <h3>Điều kiện mở bán</h3>{report&&<ul className={styles.checks}>{report.dieuKien.map(check=><li key={check.maDieuKien} className={check.dat?styles.passed:styles.failed}><span aria-hidden="true">{check.dat?'✓':'✕'}</span><div><strong>{check.tenDieuKien}</strong><small>{check.dat?'Đạt':check.lyDo}</small></div></li>)}</ul>}
      <div className={styles.buttons}><button disabled={waiting} onClick={()=>void action(()=>detail(selected.maChuyen,false))}>Kiểm tra lại điều kiện</button>
        <button disabled={waiting} onClick={()=>setViewingPassengersTrip(selected.maChuyen)}>Xem danh sách hành khách</button>
        <button disabled={waiting||!report?.duDieuKien||selected.trangThai==='DANG_MO_BAN'} onClick={()=>{if(window.confirm(`Xác nhận mở bán chuyến ${selected.maChuyen}?`))void action(async()=>{
          try{await request(`${API}/${encodeURIComponent(selected.maChuyen)}/mo-ban`,'POST');}catch(failure){await detail(selected.maChuyen);throw failure;}
          await detail(selected.maChuyen,false);await load(page.page);
        },'Đã mở bán chuyến.');}}>Mở bán chuyến</button>
        <button disabled={waiting||selected.trangThai!=='DANG_MO_BAN'} onClick={()=>void action(async()=>{await request(`${API}/${encodeURIComponent(selected.maChuyen)}/bat-dau`,'POST',{});await detail(selected.maChuyen,false);await load(page.page);},'Đã bắt đầu chuyến.')}>Bắt đầu chuyến</button>
        <button disabled={waiting||selected.trangThai!=='DANG_CHAY'} onClick={()=>void action(async()=>{await request(`${API}/${encodeURIComponent(selected.maChuyen)}/hoan-thanh`,'POST',{});await detail(selected.maChuyen,false);await load(page.page);},'Đã hoàn tất chuyến.')}>Hoàn tất chuyến</button>
        <button disabled={waiting||!selected.coTheHuy} onClick={()=>{setCancelReason('');setConfirmCancel(true);}}>Hủy chuyến</button></div>
      {confirmCancel&&<form className={styles.form} onSubmit={event=>{event.preventDefault();void action(async()=>{await request(`${API}/${encodeURIComponent(selected.maChuyen)}/huy`,'POST',{lyDo:cancelReason});setConfirmCancel(false);await detail(selected.maChuyen,false);await load(page.page);},'Chuyến đã hủy. Các khoản hoàn và đối soát được theo dõi riêng bên dưới.');}}>
        <label className={styles.wide}>Lý do hủy chuyến<input required minLength={3} maxLength={1000} value={cancelReason} onChange={event=>setCancelReason(event.target.value)} placeholder="Nhập lý do để thông báo và lưu lịch sử"/></label>
        <p className={`${styles.wide} ${styles.error}`}>Xác nhận hủy sẽ ngừng giữ chỗ/đặt vé. Khoản đã thu được đưa vào xử lý hoàn hoặc đối soát; hủy chuyến không đồng nghĩa hoàn tiền thành công.</p>
        <div className={`${styles.buttons} ${styles.wide}`}><button disabled={waiting}>Xác nhận hủy chuyến</button><button type="button" disabled={waiting} onClick={()=>setConfirmCancel(false)}>Quay lại</button></div>
      </form>}
      <h3>Khách bị ảnh hưởng và xử lý hoàn/đối soát</h3>
      {lifecycle&&<p>Vé bị ảnh hưởng: {lifecycle.tongQuanHoan.tongSoVeBiAnhHuong} · Cần hoàn: {lifecycle.tongQuanHoan.soVeCanHoan} · Vé đã hoàn: {lifecycle.tongQuanHoan.soVeDaHoan} · Đã hoàn: {Number(lifecycle.tongQuanHoan.tongTienDaHoan).toLocaleString('vi-VN')}đ · Yêu cầu thành công: {lifecycle.tongQuanHoan.soYeuCauThanhCong} · Cần theo dõi: {lifecycle.tongQuanHoan.soYeuCauCanTheoDoi}</p>}
      {lifecycle?.khachBiAnhHuong.length?<div className={styles.scroll}><table><thead><tr><th>Mã đơn</th><th>Vé còn hiệu lực</th><th>Trạng thái xử lý</th><th>Yêu cầu hoàn</th><th>Lý do hủy</th><th>Thao tác</th></tr></thead><tbody>{lifecycle.khachBiAnhHuong.map(item=><tr key={item.maDon}><td>{item.maDon}{item.veBiAnhHuong.map(ticket=><small key={ticket.maVe}>{ticket.maVe} · {ticket.trangThai}</small>)}</td><td>{item.soVeConHieuLuc}</td><td>{item.trangThaiXuLy==='CHO_XU_LY'?'Chờ xử lý hoàn':item.trangThaiXuLy==='CAN_DOI_SOAT'?'Cần đối soát thanh toán':item.trangThaiXuLy==='HOAN_TAT'?'Đã xử lý':'Không cần hoàn'}{item.lyDoXuLy&&<small>{item.lyDoXuLy}</small>}</td><td>{item.yeuCauHoan.map(refund=><small key={refund.maHoanTien}>{refund.maHoanTien} · {refund.trangThai} · {Number(refund.soTienHoan).toLocaleString('vi-VN')}đ{refund.lyDoLoi?` · ${refund.lyDoLoi}`:''}</small>)}</td><td>{item.lyDo}</td><td>{['CHO_XU_LY','CAN_DOI_SOAT'].includes(item.trangThaiXuLy)&&<button disabled={waiting} onClick={()=>void action(async()=>{await request(`${API}/${encodeURIComponent(selected.maChuyen)}/xu-ly-hoan`,'POST',{});await detail(selected.maChuyen,false);},'Đã cập nhật trạng thái xử lý hoàn/đối soát.')}>Tiếp tục xử lý</button>}</td></tr>)}</tbody></table></div>:<p>Chưa có đơn hàng bị ảnh hưởng.</p>}
      <p>Thông báo email: {lifecycle?.thongBao.map(item=>`${item.trangThai}: ${item.soLuong}`).join(' · ')||'chưa có'}.</p>
      <h3>Lịch sử vòng đời</h3>{lifecycle?.lyLich.length?<ul className={styles.checks}>{lifecycle.lyLich.map((item,index)=><li key={`${item.thoiDiem}-${index}`} className={styles.passed}><div><strong>{states[item.trangThaiCu]??item.trangThaiCu} → {states[item.trangThaiMoi]??item.trangThaiMoi}</strong><small>{item.maNguoiThaoTac} · {item.vaiTro} · {item.thoiDiem}{item.lyDo?` · ${item.lyDo}`:''}</small></div></li>)}</ul>:<p>Chưa có thao tác vòng đời được ghi nhận.</p>}
    </section>}
    {viewingPassengersTrip && (
      <TripPassengerModal
        maChuyen={viewingPassengersTrip}
        onClose={() => setViewingPassengersTrip(null)}
      />
    )}
  </div>;
}
