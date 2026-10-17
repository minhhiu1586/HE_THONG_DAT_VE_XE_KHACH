'use client';
import {useEffect,useState} from 'react';
import {accountRequest,errorMessage} from '@members/NguyenMinhHieu/account/lib/account-api';
import styles from '@members/NguyenTheAn/booking/components/TripDetails.module.css';
import accountStyles from '@members/NguyenMinhHieu/account/components/Account.module.css';
import reviewStyles from '@members/NguyenMinhHieu/reviews/components/Reviews.module.css';
import {ReviewPage} from '@members/NguyenMinhHieu/reviews/components/review-types';

export default function PublicReviews({maChuyen}:{maChuyen:string}) {
  const [data,setData]=useState<ReviewPage|null>(null),[page,setPage]=useState(1),[error,setError]=useState('');
  useEffect(()=>{let active=true;accountRequest<ReviewPage>(`/danh-gia/cong-khai?maChuyen=${encodeURIComponent(maChuyen)}&page=${page}`)
    .then(value=>{if(active){setData(value);setError('');}}).catch(cause=>{if(active)setError(errorMessage(cause));});return()=>{active=false;};},[maChuyen,page]);
  return <section className={styles.stops} aria-label="Đánh giá chuyến xe"><h2>Đánh giá chuyến xe</h2>
    {error?<p role="alert">{error}</p>:!data?<p role="status">Đang tải đánh giá…</p>:<>
      <p>{data.rating==null?'Chưa có đánh giá được duyệt':`${Number(data.rating).toLocaleString('vi-VN',{maximumFractionDigits:1})}/5 sao · ${data.totalItems} đánh giá`}</p>
      {data.items.map(review=><article className={reviewStyles.publicReview} key={review.maDanhGia}><p><strong>{review.soSao}/5 sao</strong> · {review.tenHienThi} · {new Date(review.ngayDanhGia).toLocaleDateString('vi-VN')}</p><p>{review.binhLuan}</p></article>)}
      {data.totalPages>1&&<div><button className={accountStyles.secondary} disabled={page<=1} onClick={()=>setPage(page-1)}>Trước</button> Trang {page}/{data.totalPages} <button className={accountStyles.secondary} disabled={page>=data.totalPages} onClick={()=>setPage(page+1)}>Sau</button></div>}
    </>}
  </section>;
}
