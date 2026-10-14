// T22 browser integration. Use only a disposable database with migrations 001–014.
const {chromium}=require('playwright');
const {execFileSync}=require('node:child_process');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
if(!process.env.DB_CONTAINER||!process.env.DB_NAME)throw Error('Specify DB_CONTAINER and DB_NAME for a disposable test database.');
const base=process.env.FRONTEND_URL||'http://localhost:3022';
const prefix='T22UI_'+crypto.randomBytes(3).toString('hex')+'_';
const id=x=>prefix+x,password=crypto.randomBytes(12).toString('hex')+'Aa1';
let browser;
function sql(text){execFileSync('docker',['exec','-i',process.env.DB_CONTAINER,'psql','-U',process.env.DB_USER||'postgres','-d',process.env.DB_NAME,'-v','ON_ERROR_STOP=1'],{input:text,stdio:['pipe','ignore','pipe']});}
(async()=>{
 try{
  browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
  const customer=await browser.newContext(),admin=await browser.newContext();
  async function register(context,kind){
   const email=id(kind)+'@example.test';
   let r=await context.request.post(base+'/api/auth/dang-ky',{data:{hoTen:'Kiểm thử '+kind,email,soDienThoai:'09'+crypto.randomInt(10000000,99999999),matKhau:password},headers:{'X-Requested-With':'datvexe'}});
   assert.equal(r.status(),201,await r.text());const user=(await r.json()).maNguoiDung;
   if(kind==='admin')sql(`update nguoi_dung set loai_tai_khoan='ADMIN' where ma_nguoi_dung='${user}'`);
   r=await context.request.post(base+'/api/auth/dang-nhap',{data:{email,matKhau:password},headers:{'X-Requested-With':'datvexe'}});assert.equal(r.status(),200,await r.text());return user;
  }
  const buyer=await register(customer,'customer');await register(admin,'admin');
  sql(`insert into nha_xe(ma_nha_xe,ten_nha_xe) values('${id('NX')}','Nhà xe T22');
   insert into tinh_thanh(ma_tinh_thanh,ten_tinh_thanh) values('${id('CITY')}','T22');
   insert into ben_xe(ma_ben_xe,ten_ben_xe,dia_chi,ma_tinh_thanh,trang_thai) values('${id('S')}','Bến đi','A','${id('CITY')}','HOAT_DONG'),('${id('D')}','Bến đến','B','${id('CITY')}','HOAT_DONG');
   insert into tuyen_xe(ma_tuyen,ten_tuyen,tram_khoi_hanh,tram_den,gia_co_ban,trang_thai,ma_nha_xe) values('${id('R')}','Chuyến kiểm thử T22','${id('S')}','${id('D')}',100000,'DANG_KHAI_THAC','${id('NX')}');
   insert into xe(ma_xe,bien_so,so_cho_ngoi,trang_thai,ma_nha_xe) values('${id('X')}','${id('PLATE')}',1,'SAN_SANG','${id('NX')}');
   insert into ghe(ma_ghe,vi_tri,trang_thai,ma_xe) values('${id('G')}','A1','HOAT_DONG','${id('X')}');
   insert into chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai) values('${id('C')}','${id('R')}','${id('X')}',current_date-1,'08:00',(current_date-1)+time '10:00','HOAN_TAT');
   insert into don_dat_ve(ma_don,ma_nguoi_dung,ngay_dat,khach_dat_ten,khach_dat_so_dien_thoai,kenh_dat,trang_thai) values('${id('O')}','${buyer}',now(),'Khách kiểm thử','0900000000','WEB','DA_THANH_TOAN');
   insert into phuong_thuc_thanh_toan(ma_phuong_thuc,ten_phuong_thuc,trang_thai) values('${id('PAYMETHOD')}','Kiểm thử','HOAT_DONG');
   insert into thanh_toan(ma_giao_dich,ma_don,ma_phuong_thuc,so_tien,trang_thai) values('${id('PAY')}','${id('O')}','${id('PAYMETHOD')}',100000,'THANH_CONG');
   insert into ve_xe(ma_ve,ma_chuyen,ma_ghe,ma_don,ho_ten_khach_hang,so_dien_thoai_khach_hang,gia_ve,trang_thai,ma_qr) values('${id('V')}','${id('C')}','${id('G')}','${id('O')}','Khách kiểm thử','0900000000',100000,'DA_XUAT_VE','${id('QR')}');`);
  const page=await customer.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(base+'/tai-khoan');
  const editor=page.locator('article').filter({has:page.getByRole('heading',{name:'Đánh giá chuyến · Chuyến kiểm thử T22'})});
  await editor.getByLabel('Số sao').selectOption('5');
  const script='<script>window.t22Injected=true</script>';
  await editor.getByLabel(/Nhận xét/).fill(script);
  await editor.getByRole('button',{name:'Gửi đánh giá',exact:true}).click();
  await editor.getByRole('button',{name:'Sửa đánh giá',exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>window.t22Injected),undefined);
  const pending=await (await customer.request.get(base+'/api/danh-gia/cong-khai?maChuyen='+id('C'))).json();assert.equal(pending.totalItems,0);
  const manager=await admin.newPage();await manager.goto(base+'/quan-tri/danh-gia');
  await manager.getByLabel('Mã chuyến').fill(id('C'));
  await manager.getByRole('button',{name:'Xem chi tiết',exact:true}).click();
  await manager.getByRole('button',{name:'Duyệt đánh giá',exact:true}).click();
  await manager.getByText('Đã cập nhật đánh giá.',{exact:true}).waitFor();
  const publicPage=await browser.newPage();await publicPage.goto(base+'/tim-chuyen/'+id('C'));
  await publicPage.getByText(script,{exact:true}).waitFor();assert.equal(await publicPage.evaluate(()=>window.t22Injected),undefined);
  let pub=await (await customer.request.get(base+'/api/danh-gia/cong-khai?maChuyen='+id('C'))).json();assert.equal(pub.rating,5);
  assert.ok(!JSON.stringify(pub).includes('example.test'));
  await page.reload();await editor.getByRole('button',{name:'Sửa đánh giá',exact:true}).click();await editor.getByRole('button',{name:'Lưu chỉnh sửa',exact:true}).click();await editor.getByText('Đã lưu đánh giá.',{exact:true}).waitFor();
  pub=await (await customer.request.get(base+'/api/danh-gia/cong-khai?maChuyen='+id('C'))).json();assert.equal(pub.rating,5);
  await editor.getByRole('button',{name:'Sửa đánh giá',exact:true}).click();await editor.getByLabel('Số sao').selectOption('4');await editor.getByLabel(/Nhận xét/).fill('Chuyến đi tốt');await editor.getByRole('button',{name:'Lưu chỉnh sửa',exact:true}).click();await editor.getByRole('button',{name:'Sửa đánh giá',exact:true}).waitFor();
  pub=await (await customer.request.get(base+'/api/danh-gia/cong-khai?maChuyen='+id('C'))).json();assert.equal(pub.totalItems,0);
  await manager.reload();await manager.getByLabel('Mã chuyến').fill(id('C'));await manager.getByRole('button',{name:'Xem chi tiết',exact:true}).click();await manager.getByRole('button',{name:'Duyệt đánh giá',exact:true}).click();await manager.getByText('Đã cập nhật đánh giá.',{exact:true}).waitFor();
  await manager.getByLabel('Lý do ẩn').fill('Ẩn kiểm thử');await manager.getByRole('button',{name:'Ẩn đánh giá',exact:true}).click();await manager.getByText('Đã cập nhật đánh giá.',{exact:true}).waitFor();
  pub=await (await customer.request.get(base+'/api/danh-gia/cong-khai?maChuyen='+id('C'))).json();assert.equal(pub.rating,null);
  sql(`update ve_xe set trang_thai='DA_HOAN' where ma_ve='${id('V')}'`);await page.reload();await editor.getByRole('button',{name:'Xóa đánh giá',exact:true}).waitFor();assert.equal(await editor.getByRole('button',{name:'Sửa đánh giá',exact:true}).count(),0);
  page.once('dialog',d=>d.accept());await editor.getByRole('button',{name:'Xóa đánh giá',exact:true}).click();await editor.getByText('Đã xóa',{exact:false}).first().waitFor();assert.equal(await editor.getByRole('button',{name:'Gửi đánh giá',exact:true}).count(),0);
  await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  assert.deepEqual(errors,[]);console.log('T22 browser: tạo/sửa/duyệt/ẩn/xóa, rating, XSS, bảo vệ thông tin và mobile — PASS');
 }finally{if(browser)await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
