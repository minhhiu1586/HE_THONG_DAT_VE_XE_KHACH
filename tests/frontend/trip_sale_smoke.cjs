// T12 production-browser integration with real T11/T12 APIs and disposable PostgreSQL fixtures.
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const base = process.env.FRONTEND_URL || 'http://localhost:3000';
const prefix = `T12UI_${crypto.randomBytes(4).toString('hex')}_`;
const id = suffix => prefix + suffix;
const email = id('admin').toLowerCase() + '@example.test', password = crypto.randomBytes(12).toString('hex') + 'Aa1';
const day = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
const nextDay = new Date(Date.parse(day + 'T00:00:00Z') + 86400000).toISOString().slice(0, 10);
let browser, userId;
function sql(statement) {
  execFileSync('docker', ['exec', '-i', process.env.DB_CONTAINER || 'datvexe-postgres', 'psql', '-U', process.env.DB_USER || 'datvexe_user',
    '-d', process.env.DB_NAME || 'datvexe', '-v', 'ON_ERROR_STOP=1', '-c', statement], { stdio: ['ignore', 'ignore', 'pipe'] });
}
(async () => {
  try {
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    await page.goto(`${base}/dang-ky`);
    await page.getByLabel('Họ tên', { exact: true }).fill('Quản trị kiểm thử T12');
    await page.getByLabel('Số điện thoại', { exact: true }).fill(`09${crypto.randomInt(10000000, 99999999)}`);
    await page.getByLabel('Email', { exact: true }).fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(password);
    const registered = page.waitForResponse(r => r.url().endsWith('/api/auth/dang-ky') && r.status() === 201);
    await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
    userId = (await (await registered).json()).maNguoiDung;
    sql(`UPDATE nguoi_dung SET loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung='${userId}'`);
    await page.goto(`${base}/dang-nhap`);
    await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.waitForURL(url => url.pathname === '/tai-khoan');
    const session = await (await page.request.get(`${base}/api/tai-khoan/me`)).json();
    async function api(method, path, data, expected = 200) {
      const response = await page.request.fetch(`${base}/api${path}`, { method, data,
        headers: { 'X-Requested-With': 'datvexe', 'X-CSRF-Token': session.csrfToken } });
      assert.equal(response.status(), expected, `${method} ${path}: ${await response.text()}`);
      return response.status() === 204 ? null : response.json();
    }
    await api('POST', '/quan-tri/nha-xe', { maNhaXe: id('NX'), tenNhaXe: 'Nhà xe kiểm thử T12' }, 201);
    await api('PUT', `/quan-tri/nha-xe/${id('NX')}/trang-thai`, { trangThai: 'HOAT_DONG' });
    await api('POST', '/tinh-thanh', { maTinhThanh: id('CITY'), tenTinhThanh: 'T12' }, 201);
    for (const code of ['S', 'D']) await api('POST', '/ben-xe', { maBenXe: id(code), tenBenXe: code, diaChi: code, maTinhThanh: id('CITY') }, 201);
    await api('POST', '/tuyen-xe', { maTuyen: id('R'), tenTuyen: 'Tuyến kiểm thử T12', tramKhoiHanh: id('S'), tramDen: id('D'), thoiGianDuKien: 7, giaCoBan: 100000, trangThai: 'DANG_KHAI_THAC', maNhaXe: id('NX') }, 201);
    await api('POST', '/loai-xe', { maLoaiXe: id('L'), tenLoaiXe: '4 ghế', soChoNgoi: 4, soTang: 2 }, 201);
    await api('POST', '/xe', { maXe: id('X'), bienSo: `51B-${prefix.replaceAll('_', '-')}`, maLoaiXe: id('L'), maNhaXe: id('NX'), ngayDangKiem: '2020-01-01', hanDangKiem: '2100-01-01', trangThai: 'SAN_SANG' }, 201);
    await page.getByRole('link', { name: 'Quản trị', exact: true }).click();
    const navigation = page.getByRole('navigation', { name: 'Điều hướng quản lý' });
    await navigation.getByRole('link', { name: 'Chuyến xe và mở bán', exact: true }).click();
    await page.getByRole('heading', { name: 'Quản lý chuyến xe và mở bán', exact: true }).waitFor();
    const message = await page.getByLabel('Mã chuyến', { exact: true }).evaluate(input => { input.reportValidity(); return input.validationMessage; });
    assert(message.startsWith('Vui lòng nhập'), message);
    await page.getByLabel('Mã chuyến', { exact: true }).fill(id('C'));
    await page.getByLabel('Tuyến xe', { exact: true }).selectOption(id('R'));
    await page.getByLabel('Xe dự kiến (không bắt buộc)', { exact: true }).selectOption(id('X'));
    await page.getByLabel('Ngày khởi hành', { exact: true }).fill(day);
    await page.getByLabel('Giờ khởi hành', { exact: true }).fill('22:00');
    assert.equal(await page.getByLabel('Ngày giờ đến dự kiến', { exact: true }).inputValue(), `${nextDay}T05:00`);
    await page.getByRole('button', { name: 'Tạo chuyến nháp', exact: true }).click();
    await page.getByText('Đã tạo chuyến nháp. Chuyến chưa được mở bán.', { exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Mở bán chuyến', exact: true }).isDisabled(), true);
    assert((await api('GET', `/chuyen-xe?from=${id('S')}&to=${id('D')}&date=${day}`)).length === 0);
    for (let i = 1; i <= 3; i++) {
      const assistant = i === 3;
      await api('POST', '/nhan-vien', { maNhanVien: id(`E${i}`), hoTen: `Nhân viên kiểm thử ${i}`, email: id(`E${i}`).toLowerCase() + '@example.test',
        soDienThoai: `09${crypto.randomInt(10000000, 99999999)}`, maNhaXe: id('NX'), loaiNhanVien: assistant ? 'PHU_XE' : 'TAI_XE', trangThai: 'DANG_LAM_VIEC',
        soBangLai: assistant ? null : '123456789012', ngayHetHanBangLai: assistant ? null : '2100-01-01' }, 201);
      await api('POST', '/phan-cong', { maPhanCong: id(`P${i}`), maChuyen: id('C'), maNhanVien: id(`E${i}`), vaiTro: { 1: 'TAI_XE_CHINH', 2: 'TAI_XE_PHU', 3: 'PHU_XE' }[i],
        xacNhanKeHoachLaiNghi: true, ghiChu: 'Kế hoạch kiểm thử T12' }, 201);
    }
    await page.getByRole('button', { name: 'Kiểm tra lại điều kiện', exact: true }).click();
    await page.waitForFunction(() => Array.from(document.querySelectorAll('button')).some(b => b.textContent === 'Mở bán chuyến' && !b.disabled));
    await page.getByText('Nhân viên kiểm thử 3', { exact: true }).waitFor();
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Mở bán chuyến', exact: true }).click();
    await page.getByText('Đã mở bán chuyến.', { exact: true }).waitFor();
    const row = page.getByRole('row').filter({ hasText: id('C') });
    assert((await row.textContent()).includes('Đang mở bán'));
    assert.equal(await row.getByRole('button', { name: 'Sửa', exact: true }).isDisabled(), true);
    assert.equal(await row.getByRole('button', { name: 'Xóa', exact: true }).isDisabled(), true);
    assert.equal((await api('GET', `/chuyen-xe?from=${id('S')}&to=${id('D')}&date=${day}`))[0].maChuyen, id('C'));
    assert.equal((await api('GET', `/chuyen-xe/${id('C')}/ghe`)).danhSachGhe.length, 4);
    await page.getByLabel('Lọc tuyến', { exact: true }).selectOption(id('R'));
    await page.getByLabel('Lọc trạng thái', { exact: true }).selectOption('NHAP');
    await page.getByRole('button', { name: 'Lọc chuyến', exact: true }).click();
    await page.getByText('Không có chuyến phù hợp.', { exact: true }).waitFor();
    await page.setViewportSize({ width: 390, height: 844 });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
    assert.equal(await navigation.isVisible(), false);
    await page.getByRole('button', { name: 'Menu quản lý', exact: true }).click();
    assert.equal(await navigation.getByRole('link', { name: 'Chuyến xe và mở bán', exact: true }).getAttribute('aria-current'), 'page');
    await page.setViewportSize({ width: 1440, height: 1000 });
    sql(`UPDATE nguoi_dung SET loai_tai_khoan='NHA_XE',ma_nha_xe='${id('NX')}' WHERE ma_nguoi_dung='${userId}'`);
    await page.reload(); await navigation.getByRole('link', { name: 'Chuyến xe và mở bán', exact: true }).waitFor();
    assert.equal(await navigation.getByRole('link', { name: 'Tài khoản', exact: true }).count(), 0);
    assert.equal(await navigation.getByRole('link', { name: 'Nhà xe', exact: true }).count(), 0);
    sql(`UPDATE nguoi_dung SET loai_tai_khoan='KHACH_HANG',ma_nha_xe=NULL WHERE ma_nguoi_dung='${userId}'`);
    await page.reload(); await page.getByRole('alert').filter({ hasText: 'không có quyền' }).waitFor();
    console.log('PASS: T12 browser Vietnamese validation, overnight draft, real T11 crew, eligibility and publication, filters, read-only published trip, operator/customer menu and mobile layout.');
  } finally {
    if (browser) await browser.close();
    if (userId) sql(`UPDATE nguoi_dung SET ma_nha_xe=NULL,loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung='${userId}'`);
    for (const [table, key] of [['phan_cong_chuyen_xe', 'ma_phan_cong'], ['chuyen_xe', 'ma_chuyen'], ['ghe', 'ma_xe'], ['xe', 'ma_xe'], ['loai_xe', 'ma_loai_xe'],
      ['tuyen_xe', 'ma_tuyen'], ['ben_xe', 'ma_ben_xe'], ['tinh_thanh', 'ma_tinh_thanh'], ['nhan_vien', 'ma_nhan_vien'], ['nha_xe', 'ma_nha_xe']]) {
      sql(`DELETE FROM ${table} WHERE left(${key},${prefix.length})='${prefix}'`);
    }
    if (userId) sql(`DELETE FROM phien_dang_nhap WHERE ma_nguoi_dung='${userId}';DELETE FROM nguoi_dung WHERE ma_nguoi_dung='${userId}'`);
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
