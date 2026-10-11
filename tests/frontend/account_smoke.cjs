// Requires Playwright and Chromium; uses only uniquely generated development users.
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const base = process.env.FRONTEND_URL || 'http://localhost:3000';
const tag = crypto.randomBytes(8).toString('hex');
const email = `qa_browser_${tag}@example.test`, password = crypto.randomBytes(20).toString('hex') + 'Aa1';
let userId, targetId, browser;
const changedPassword = crypto.randomBytes(20).toString("hex") + "Aa2";
function sql(statement) { execFileSync('docker', ['exec', '-i', process.env.DB_CONTAINER || 'datvexe-postgres', 'psql', '-U', 'datvexe_user', '-d', 'datvexe', '-v', 'ON_ERROR_STOP=1', '-c', statement], { stdio: ['ignore', 'ignore', 'pipe'] }); }
async function shown(locator) { await locator.waitFor({ state: 'visible', timeout: 15000 }); }
(async () => {
  try {
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
    const context = await browser.newContext(); const page = await context.newPage();
    page.on('response', async response => {
      if (response.url().endsWith('/api/auth/dang-ky') && response.status() === 201) userId = (await response.json()).maNguoiDung;
    });
    await page.goto(base + '/tai-khoan'); await page.waitForURL(url => url.pathname === '/dang-nhap' && url.searchParams.has('next'));
    await page.goto(base + '/dang-ky');
    await page.getByLabel('Họ tên', {exact:true}).fill('Kiểm thử trình duyệt');
    await page.getByLabel('Số điện thoại', {exact:true}).fill('09' + crypto.randomInt(10000000, 99999999));
    await page.getByLabel('Email', {exact:true}).fill(email);
    await page.getByLabel('Mật khẩu', {exact:true}).fill(password);
    await page.getByLabel('Xác nhận mật khẩu', {exact:true}).fill(password + 'wrong');
    await page.getByRole('button', {name:'Đăng ký',exact:true}).click();
    await shown(page.getByRole('alert').filter({hasText:'Hai mật khẩu chưa trùng nhau'}));
    await page.getByLabel('Xác nhận mật khẩu', {exact:true}).fill(password);
    await page.getByRole('button', {name:'Đăng ký',exact:true}).click();
    await shown(page.getByText('Đăng ký thành công.', {exact:false}));
    await page.goto(base + '/dang-nhap');
    await page.getByLabel('Email', {exact:true}).fill(email);
    await page.getByLabel('Mật khẩu', {exact:true}).fill('WrongPassword123');
    await page.getByRole('button', {name:'Đăng nhập',exact:true}).click(); await shown(page.getByRole('alert'));
    await page.getByLabel('Mật khẩu', {exact:true}).fill(password);
    await page.getByRole('button', {name:'Đăng nhập',exact:true}).click(); await page.waitForURL(url=>url.pathname==='/tai-khoan');
    await shown(page.getByRole('heading',{name:'Hồ sơ cá nhân'}));
    assert.equal(await page.evaluate(()=>localStorage.length),0);
    const cookies = await context.cookies(base + '/api/tai-khoan/me');
    assert(cookies.find(c=>c.name==='datvexe_session' && c.httpOnly && c.sameSite==='Lax'));
    await page.getByLabel('Họ tên',{exact:true}).fill('Hồ sơ trình duyệt đã đổi');
    await page.getByRole('button',{name:'Lưu hồ sơ',exact:true}).click(); await shown(page.getByText('Đã cập nhật hồ sơ.',{exact:true}));
    await page.reload(); await shown(page.getByRole('heading',{name:'Hồ sơ cá nhân'}));
    assert.equal(await page.getByLabel('Họ tên',{exact:true}).inputValue(),'Hồ sơ trình duyệt đã đổi');
    // Confirm password validation and successful revocation through the actual UI.
    await page.getByLabel('Mật khẩu hiện tại',{exact:true}).fill(password);
    await page.getByLabel('Mật khẩu mới',{exact:true}).fill(changedPassword);
    await page.getByLabel('Xác nhận mật khẩu mới',{exact:true}).fill(changedPassword + 'wrong');
    await page.getByRole('button',{name:'Đổi mật khẩu',exact:true}).click();
    await shown(page.getByRole('alert').filter({hasText:'Hai mật khẩu mới chưa trùng nhau'}));
    await page.getByLabel('Xác nhận mật khẩu mới',{exact:true}).fill(changedPassword);
    await page.getByLabel('Mật khẩu hiện tại',{exact:true}).fill('WrongPassword123');
    await page.getByRole('button',{name:'Đổi mật khẩu',exact:true}).click();
    await shown(page.getByRole('alert').filter({hasText:'Mật khẩu hiện tại không đúng'}));
    await page.getByLabel('Mật khẩu hiện tại',{exact:true}).fill(password);
    await page.getByRole('button',{name:'Đổi mật khẩu',exact:true}).click();
    await page.waitForURL(url=>url.pathname==='/dang-nhap');
    await shown(page.getByText('Đổi mật khẩu thành công.',{exact:false}).first());
    assert.equal((await context.request.get(base+'/api/tai-khoan/me')).status(),401);
    await page.getByLabel('Email',{exact:true}).fill(email); await page.getByLabel('Mật khẩu',{exact:true}).fill(changedPassword);
    await page.getByRole('button',{name:'Đăng nhập',exact:true}).click(); await page.waitForURL(url=>url.pathname==='/tai-khoan');
    await page.goto(base + '/quan-tri/tai-khoan'); await shown(page.getByText('Bạn không có quyền quản lý tài khoản.',{exact:false}));
    sql(`UPDATE nguoi_dung SET trang_thai='KHOA' WHERE ma_nguoi_dung='${userId}'`);
    await page.goto(base + '/tai-khoan'); await page.waitForURL(url => url.pathname === '/dang-nhap' && url.searchParams.has('next'));
    await shown(page.getByText('Vui lòng đăng nhập để tiếp tục.',{exact:false}).first());
    sql(`UPDATE nguoi_dung SET trang_thai='HOAT_DONG',loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung='${userId}'`);
    await page.getByLabel('Email',{exact:true}).fill(email); await page.getByLabel('Mật khẩu',{exact:true}).fill(changedPassword);
    await page.getByRole('button',{name:'Đăng nhập',exact:true}).click(); await page.waitForURL(url=>url.pathname==='/tai-khoan');
    await page.getByRole('link',{name:'Quản lý trạng thái tài khoản'}).click();
    await shown(page.getByRole('heading',{name:'Quản lý tài khoản'}));
    const targetEmail = `qa_target_${tag}@example.test`;
    const targetContext = await browser.newContext();
    const registered = await targetContext.request.post(base+'/api/auth/dang-ky',{headers:{'X-Requested-With':'datvexe'},data:{hoTen:'Tài khoản kiểm thử khóa',email:targetEmail,soDienThoai:'09'+crypto.randomInt(10000000,99999999),matKhau:password}});
    assert.equal(registered.status(),201); targetId=(await registered.json()).maNguoiDung;
    assert.equal((await targetContext.request.post(base+'/api/auth/dang-nhap',{headers:{'X-Requested-With':'datvexe'},data:{email:targetEmail,matKhau:password}})).status(),200);
    await page.reload();
    const row = page.getByRole('row').filter({hasText:targetEmail});
    await shown(row); await row.getByRole('button',{name:'Khóa',exact:true}).click();
    await shown(page.getByText('Đã khóa tài khoản và thu hồi các phiên đăng nhập.',{exact:true}));
    assert.equal((await targetContext.request.get(base+'/api/tai-khoan/me')).status(),401);
    await row.getByRole('button',{name:'Mở khóa',exact:true}).click();
    await shown(page.getByText('Đã mở khóa tài khoản.',{exact:true}));
    assert.equal((await targetContext.request.post(base+'/api/auth/dang-nhap',{headers:{'X-Requested-With':'datvexe'},data:{email:targetEmail,matKhau:password}})).status(),200);
    const selfRow = page.getByRole('row').filter({hasText:email});
    assert.equal(await selfRow.getByRole('button',{name:'Khóa',exact:true}).isDisabled(),true);
    await targetContext.close();
    await page.getByRole('button',{name:'Đăng xuất',exact:true}).first().click(); await page.waitForURL(base + '/');
    assert.equal((await context.request.get(base+'/api/tai-khoan/me')).status(),401);
    console.log('PASS: browser registration, validation, login, profile persistence, password change, locked-session notice, role UI, admin lock/unlock and logout');
  } finally {
    if (browser) await browser.close();
    if (targetId) sql(`DELETE FROM phien_dang_nhap WHERE ma_nguoi_dung='${targetId}'; DELETE FROM nguoi_dung WHERE ma_nguoi_dung='${targetId}'`);
    if (userId) sql(`DELETE FROM phien_dang_nhap WHERE ma_nguoi_dung='${userId}'; DELETE FROM nguoi_dung WHERE ma_nguoi_dung='${userId}'`);
  }
})().catch(error=>{ console.error(error); process.exitCode=1; });
