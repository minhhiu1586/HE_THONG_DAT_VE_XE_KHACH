'use client';
import {useCallback,useEffect,useState} from 'react';
import {useAuth} from '@members/NguyenMinhHieu/account/components/AuthProvider';
import {errorMessage} from '@members/NguyenMinhHieu/account/lib/account-api';
import styles from '@members/LeNguyenThanhHien/trips/components/TripManagement.module.css';
import {Review,ReviewPage,reviewStates} from '@members/NguyenMinhHieu/reviews/components/review-types';

type History={maNguoiThaoTac:string;thaoTac:string;trangThaiCu:string;trangThaiMoi:string;lyDo:string;thoiDiem:string};
export default function ReviewModeration() {
  const {request,user}=useAuth();
  const [data,setData]=useState<ReviewPage|null>(null),[state,setState]=useState('CHO_DUYET'),[trip,setTrip]=useState(''),[page,setPage]=useState(1);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[selected,setSelected]=useState<Review|null>(null),[history,setHistory]=useState<History[]>([]),[reason,setReason]=useState('');
  const load=useCallback(async()=>{const q=new URLSearchParams({trangThai:state,maChuyen:trip,page:String(page)});setData(await request<ReviewPage>(`/quan-ly/danh-gia?${q}`));},[request,state,trip,page]);
  useEffect(()=>{let active=true;const q=new URLSearchParams({trangThai:state,maChuyen:trip,page:String(page)});
    request<ReviewPage>(`/quan-ly/danh-gia?${q}`).then(value=>{if(active){setData(value);setError('');}}).catch(cause=>{if(active)setError(errorMessage(cause));});return()=>{active=false;};},[request,state,trip,page]);
  async function detail(review:Review){setError('');try{setHistory(await request<History[]>(`/danh-gia/${review.maDanhGia}/lich-su`));setSelected(review);setReason('');}catch(cause){setError(errorMessage(cause));}}
  async function change(action:'duyet'|'an'|'delete') {
    if(!selected||busy)return;
    if(action==='delete'&&!window.confirm('Xóa mềm đánh giá này? Đánh giá sẽ không còn hiển thị và tính điểm công khai.'))return;
    setBusy(true);setError('');setNotice('');
    try{const updated=await request<Review>(`/danh-gia/${selected.maDanhGia}${action==='delete'?'':`/${action}`}`,action==='delete'?'DELETE':'POST',action==='delete'?undefined:{lyDo:reason});
      await load();await detail(updated);setNotice('Đã cập nhật đánh giá.');
    }catch(cause){setError(errorMessage(cause));}finally{setBusy(false);}
  }
  if(user?.loaiTaiKhoan!=='ADMIN'&&user?.loaiTaiKhoan!=='NHA_XE')return null;
  return <div className={styles.page}><header className={styles.heading}><p>QUẢN TRỊ VẬN HÀNH</p><h1>Kiểm duyệt đánh giá</h1><p>Duyệt nhận xét chuyến xe và quản lý nội dung công khai trong phạm vi của bạn.</p></header>
    {error&&<p className={styles.error} role="alert">{error}</p>}{notice&&<p className={styles.success} role="status">{notice}</p>}
    <section className={styles.panel}><h2>Danh sách đánh giá</h2><div className={styles.filters}>
      <label>Trạng thái<select value={state} onChange={e=>{setState(e.target.value);setPage(1);setSelected(null);}}><option value="">Tất cả chưa xóa</option>{Object.entries(reviewStates).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
      <label>Mã chuyến<input value={trip} onChange={e=>{setTrip(e.target.value);setPage(1);setSelected(null);}} placeholder="Lọc theo mã chuyến"/></label>
    </div>{!data?<p>Đang tải đánh giá…</p>:<><div className={styles.scroll}><table><thead><tr><th>Chuyến</th><th>Số sao</th><th>Nhận xét</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{data.items.map(review=><tr key={review.maDanhGia}><td>{review.maChuyen}</td><td>{review.soSao}/5</td><td>{review.binhLuan}</td><td>{reviewStates[review.trangThai??'']}</td><td><button disabled={busy} onClick={()=>void detail(review)}>Xem chi tiết</button></td></tr>)}</tbody></table></div>{!data.items.length&&<p>Không có đánh giá phù hợp.</p>}<div className={styles.buttons}><button disabled={page<=1||busy} onClick={()=>setPage(page-1)}>Trước</button><span>Trang {page}</span><button disabled={page>=data.totalPages||busy} onClick={()=>setPage(page+1)}>Sau</button></div></>}
    </section>{selected&&<section className={styles.panel}><h2>Chi tiết đánh giá · {selected.maChuyen}</h2><p>{selected.soSao}/5 sao · {reviewStates[selected.trangThai??'']}</p><p>{selected.binhLuan}</p>
      {selected.trangThai!=='DA_XOA'&&<><div className={styles.form}><label className={styles.wide}>Lý do ẩn<input value={reason} maxLength={1000} onChange={e=>setReason(e.target.value)}/></label></div><div className={styles.buttons}><button disabled={busy||selected.trangThai==='DA_DUYET'} onClick={()=>void change('duyet')}>Duyệt đánh giá</button><button disabled={busy||!reason.trim()||selected.trangThai==='AN'} onClick={()=>void change('an')}>Ẩn đánh giá</button><button disabled={busy} onClick={()=>void change('delete')}>Xóa mềm</button></div></>}
      <h3>Lịch sử thao tác</h3><ul className={styles.checks}>{history.map((item,index)=><li key={index}><strong>{item.thaoTac}</strong><p>{item.maNguoiThaoTac} · {item.thoiDiem} · {reviewStates[item.trangThaiMoi]??item.trangThaiMoi}{item.lyDo?` · ${item.lyDo}`:''}</p></li>)}</ul>
    </section>}
  </div>;
}
