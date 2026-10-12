// T10 browser smoke. Requires Playwright/Chromium, local Tomcat and PostgreSQL.
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const base = process.env.FRONTEND_URL || 'http://localhost:3000';
const prefix = `T10UI_${crypto.randomBytes(5).toString('hex')}`;
const email = `${prefix}@example.test`, password = crypto.randomBytes(12).toString('hex') + 'Aa1';
const typeId = `${prefix}_L`, vehicleId = `${prefix}_X`;
let browser, userId;
function sql(statement) {
  execFileSync('docker', ['exec', '-i', process.env.DB_CONTAINER || 'datvexe-postgres', 'psql', '-U', process.env.DB_USER || 'datvexe_user',
    '-d', process.env.DB_NAME || 'datvexe', '-v', 'ON_ERROR_STOP=1', '-c', statement], { stdio: ['ignore', 'ignore', 'pipe'] });
}
(async () => {
  try {
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
    const page = await browser.newPage();
    await page.goto(`${base}/dang-ky`);
    await page.getByLabel('Họ tên').fill('Quản trị T10');
    await page.getByLabel('Số điện thoại').fill(`09${crypto.randomInt(10000000, 99999999)}`);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByLabel('Xác nhận mật khẩu').fill(password);
    const registered = page.waitForResponse(response => response.url().endsWith('/api/auth/dang-ky') && response.status() === 201);
    await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
    userId = (await (await registered).json()).maNguoiDung;
    sql(`UPDATE nguoi_dung SET loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung='${userId}'`);
    await page.goto(`${base}/dang-nhap`);
    await page.getByLabel('Email').fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.waitForURL(url => url.pathname === '/tai-khoan');
    await page.getByRole('link', { name: 'Quản trị', exact: true }).click();
    const navigation = page.getByRole('navigation', { name: 'Điều hướng quản lý' });
    await navigation.getByRole('link', { name: 'Nhà xe', exact: true }).waitFor();
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await navigation.isVisible(), false);
    await page.getByRole('button', { name: 'Menu quản lý', exact: true }).click();
    await navigation.getByRole('link', { name: 'Xe và sơ đồ ghế', exact: true }).click();
    assert.equal(await page.getByRole('button', { name: 'Menu quản lý', exact: true }).getAttribute('aria-expanded'), 'false');
    await page.setViewportSize({ width: 1440, height: 1000 });
    assert.equal(await navigation.getByRole('link', { name: 'Xe và sơ đồ ghế', exact: true }).getAttribute('aria-current'), 'page');
    await page.getByRole('heading', { name: 'Quản lý xe và sơ đồ ghế' }).waitFor();
    await page.getByRole('button', { name: 'Loại xe', exact: true }).click();
    for (const field of await page.locator('form input[required]:enabled').all()) {
      const message = await field.evaluate(input => { const old = input.value; input.value = ''; input.setCustomValidity(''); input.reportValidity(); const message = input.validationMessage; input.value = old; input.setCustomValidity(''); return message; });
      assert(message.startsWith('Vui lòng nhập'), message);
    }
    await page.getByLabel('Mã loại xe').fill(typeId); await page.getByLabel('Tên loại xe').fill('Xe kiểm thử T10');
    await page.getByLabel('Sức chứa').fill('4'); await page.getByLabel('Số tầng').fill('2');
    await page.getByRole('button', { name: 'Thêm loại xe', exact: true }).click();
    await page.getByText('Đã thêm loại xe.', { exact: true }).waitFor();
    await page.getByLabel('Tìm loại xe', { exact: true }).fill(`${prefix}_NO_MATCH`);
    const searched = page.waitForResponse(response => response.url().includes('/api/loai-xe?q=') && response.status() === 200);
    await page.getByRole('button', { name: 'Tìm loại xe', exact: true }).click();
    await searched;
    await page.waitForFunction(id => !Array.from(document.querySelectorAll('tbody tr')).some(row => row.textContent.includes(id)), typeId);
    await page.getByRole('button', { name: 'Xe và ghế', exact: true }).click();
    assert.equal(await page.locator('p').filter({ hasText: 'Đã thêm loại xe.' }).count(), 0);
    await page.getByLabel('Mã xe', { exact: true }).fill(vehicleId); await page.getByLabel('Biển số', { exact: true }).fill(`51B-${prefix.replace('_', '-')}`);
    const form = page.locator('form').filter({ has: page.getByLabel('Mã xe', { exact: true }) });
    await form.locator('select').nth(0).selectOption(typeId); await form.locator('select').nth(1).selectOption('DEMO_NX');
    await page.getByLabel('Ngày đăng kiểm', { exact: true }).fill('2020-01-01');
    await page.getByLabel('Hạn đăng kiểm', { exact: true }).fill('2100-01-01');
    await page.getByLabel('Dung tích nhiên liệu (lít)', { exact: true }).fill('100');
    await page.getByRole('button', { name: 'Thêm xe', exact: true }).click();
    await page.getByText('Đã thêm xe và tạo sơ đồ ghế.', { exact: true }).waitFor();
    await page.getByRole('heading', { name: 'Sơ đồ ghế · 4/4 chỗ', exact: true }).waitFor();
    await page.getByRole('heading', { name: 'Tầng 2', exact: true }).waitFor();
    await page.getByText('Dung tích nhiên liệu: 100 lít', { exact: false }).waitFor();
    await page.getByLabel('Mã/vị trí ghế 1', { exact: true }).fill('A1');
    await page.getByLabel('Trạng thái ghế 1', { exact: true }).selectOption('BAO_TRI');
    await page.getByRole('button', { name: 'Lưu sơ đồ ghế', exact: true }).click();
    await page.getByText('Đã lưu sơ đồ ghế.', { exact: true }).waitFor();
    await page.reload();
    await page.getByRole('row').filter({ hasText: vehicleId }).getByRole('button', { name: 'Chi tiết / ghế', exact: true }).click();
    await page.getByRole('heading', { name: 'Sơ đồ ghế · 4/4 chỗ', exact: true }).waitFor();
    assert.equal(await page.getByLabel('Mã/vị trí ghế 1', { exact: true }).inputValue(), 'A1');
    assert.equal(await page.getByLabel('Trạng thái ghế 1', { exact: true }).inputValue(), 'BAO_TRI');
    await page.getByLabel('Mã/vị trí ghế 2', { exact: true }).fill('A1');
    await page.getByRole('button', { name: 'Lưu sơ đồ ghế', exact: true }).click();
    await page.locator('p[role="alert"]').filter({ hasText: 'trùng' }).waitFor();
    await page.getByRole('button', { name: 'Tải lại sơ đồ', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[aria-label="Mã/vị trí ghế 2"]')?.value !== 'A1');
    assert.notEqual(await page.getByLabel('Mã/vị trí ghế 2', { exact: true }).inputValue(), 'A1');
    await page.getByRole('row').filter({ hasText: vehicleId }).getByRole('button', { name: 'Sửa', exact: true }).click();
    await form.locator('select').nth(2).selectOption('BAO_TRI');
    await page.getByRole('button', { name: 'Lưu xe', exact: true }).click();
    await page.getByText('Đã cập nhật xe.', { exact: true }).waitFor();
    assert((await page.getByRole('row').filter({ hasText: vehicleId }).textContent()).includes('Bảo trì'));
    console.log('PASS: T10 browser validation, type search preserves vehicle catalog and floors, vehicle creation, inspection, two-floor layout, seat maintenance, duplicate rollback, reload and vehicle status');
  } finally {
    if (browser) await browser.close();
    sql(`DELETE FROM ghe WHERE ma_xe='${vehicleId}';DELETE FROM xe WHERE ma_xe='${vehicleId}';DELETE FROM loai_xe WHERE ma_loai_xe='${typeId}';`);
    if (userId) sql(`DELETE FROM phien_dang_nhap WHERE ma_nguoi_dung='${userId}';DELETE FROM nguoi_dung WHERE ma_nguoi_dung='${userId}';`);
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
