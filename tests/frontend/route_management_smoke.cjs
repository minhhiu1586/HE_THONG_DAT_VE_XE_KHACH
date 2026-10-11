// Requires a local Next.js app, Tomcat/PostgreSQL development stack, Playwright and Chromium.
// Creates and cleans a uniquely prefixed admin and geographic fixtures.
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const base = process.env.FRONTEND_URL || 'http://localhost:3000';
const tag = crypto.randomBytes(6).toString('hex');
const code = `T08_${tag}`;
const email = `qa_t08_${tag}@example.test`;
const password = crypto.randomBytes(12).toString('hex') + 'Aa1';
const ids = { user: null, city: `${code}_CITY`, stationA: `${code}_A`, stationB: `${code}_B`, route: `${code}_ROUTE`, stopA: `${code}_STOP_A`, stopB: `${code}_STOP_B` };
let browser;
function sql(statement) {
  execFileSync('docker', ['exec', '-i', process.env.DB_CONTAINER || 'datvexe-postgres', 'psql', '-U', process.env.DB_USER || 'datvexe_user', '-d', process.env.DB_NAME || 'datvexe', '-v', 'ON_ERROR_STOP=1', '-c', statement], { stdio: ['ignore', 'ignore', 'pipe'] });
}
(async () => {
  try {
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
    const page = await browser.newPage();
    page.on('response', response => { if (response.url().includes('/api/auth/dang-ky')) console.log(`HTTP đăng ký: ${response.status()}`); });
    page.on('response', async response => {
      if (response.url().endsWith('/api/auth/dang-ky') && response.status() === 201) ids.user = (await response.json()).maNguoiDung;
    });
    await page.goto(`${base}/dang-ky`);
    await page.getByLabel('Họ tên').fill('Quản trị T08');
    await page.getByLabel('Số điện thoại').fill(`09${crypto.randomInt(10000000, 99999999)}`);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByLabel('Xác nhận mật khẩu').fill(password);
    await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
    const outcome = await Promise.race([
      page.getByText('Đăng ký thành công', { exact: false }).waitFor().then(() => 'success'),
      (async () => { const alert = page.locator('p[role="alert"]').first(); await alert.waitFor(); return `error: ${await alert.textContent()}`; })(),
    ]);
    if (outcome !== 'success') throw new Error(outcome);
    assert(ids.user, 'Không nhận được mã tài khoản từ API đăng ký');
    sql(`UPDATE nguoi_dung SET loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung='${ids.user}'`);
    await page.goto(`${base}/dang-nhap`);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.waitForURL(url => url.pathname === '/tai-khoan');
    await page.goto(`${base}/quan-tri/tuyen-duong`);
    await page.getByRole('heading', { name: 'Quản lý tuyến đường' }).waitFor();

    async function checkVietnameseValidation() {
      for (const field of await page.locator('form input[required]:enabled, form select[required]:enabled, form textarea[required]:enabled').all()) {
        const result = await field.evaluate(input => {
          const previous = input.value;
          input.value = ''; input.setCustomValidity(''); input.reportValidity();
          const message = input.validationMessage;
          input.value = previous; input.setCustomValidity('');
          return { message, label: input.dataset.validationLabel };
        });
        assert(result.message.startsWith('Vui lòng ') && result.message.includes(result.label), result);
      }
      for (const field of await page.locator('form input:not([required]):enabled, form textarea:not([required]):enabled').all()) {
        assert(await field.evaluate(input => { const previous = input.value; input.value = ''; input.setCustomValidity(''); const valid = input.checkValidity(); input.value = previous; return valid; }), 'Trường tùy chọn phải cho phép bỏ trống');
      }
    }
    await page.getByRole('button', { name: 'Tỉnh/thành', exact: true }).click();
    await checkVietnameseValidation();
    await page.getByLabel('Mã tỉnh/thành').fill(ids.city);
    await page.getByLabel('Tên', { exact: true }).fill('Tỉnh kiểm thử T08');
    await page.getByLabel('Khu vực').fill('Miền kiểm thử');
    await page.getByRole('button', { name: 'Thêm tỉnh/thành', exact: true }).click();
    await page.getByRole('status').filter({ hasText: 'Đã thêm tỉnh/thành phố' }).waitFor();

    await page.getByRole('button', { name: 'Bến xe', exact: true }).click();
    assert.equal(await page.locator('p[role="status"]').count(), 0, 'Đổi tab phải xóa thông báo thành công');
    await checkVietnameseValidation();
    async function stationForm(id, name) {
      await page.getByLabel('Mã bến').fill(id);
      await page.getByLabel('Tên bến').fill(name);
      await page.getByLabel('Địa chỉ').fill('Địa chỉ thử nghiệm');
      await page.getByLabel('Tỉnh/thành').last().selectOption(ids.city);
      await page.getByRole('button', { name: 'Thêm bến xe', exact: true }).click();
      await page.getByRole('status').filter({ hasText: 'Đã thêm bến xe' }).waitFor();
    }
    await stationForm(ids.stationA, 'Bến đi thử nghiệm');
    await stationForm(ids.stationB, 'Bến đến thử nghiệm');
    await page.getByRole('button', { name: 'Tuyến xe', exact: true }).click();
    await checkVietnameseValidation();
    const routeStartOptions = await page.getByLabel('Bến khởi hành').locator('option').evaluateAll(options => options.map(option => option.value));
    assert(routeStartOptions.includes(ids.stationA) && routeStartOptions.includes(ids.stationB), routeStartOptions);
    await page.getByLabel('Mã tuyến').fill(ids.route);
    await page.getByLabel('Tên tuyến').fill('Tuyến kiểm thử T08');
    await page.getByLabel('Bến khởi hành').selectOption(ids.stationA);
    await page.locator('form').filter({ has: page.getByLabel('Mã tuyến') }).locator('select').nth(1).selectOption(ids.stationB);
    await page.getByLabel('Thời lượng dự kiến (giờ)').fill('2.5');
    await page.getByLabel('Giá cơ bản (đồng)').fill('125000');
    await page.getByRole('button', { name: 'Tạo tuyến', exact: true }).click();
    await page.getByRole('status').filter({ hasText: 'Đã tạo tuyến xe' }).waitFor();
    await page.getByText('Tuyến chưa có điểm dừng trung gian').waitFor();
    assert.equal(await page.getByLabel(`Trạng thái tuyến ${ids.route}`).evaluate(element => element.tagName), 'SPAN', 'Trạng thái danh sách chỉ được hiển thị');
    await page.getByRole('row').filter({ hasText: ids.route }).getByRole('button', { name: 'Sửa', exact: true }).click();
    assert.equal(await page.locator('p[role="status"]').count(), 0, 'Bắt đầu sửa phải xóa thông báo cũ');
    await page.getByLabel('Trạng thái khai thác').selectOption('DANG_KHAI_THAC');
    await page.getByRole('button', { name: 'Lưu trạng thái', exact: true }).click();
    await page.getByRole('status').filter({ hasText: 'Đã cập nhật trạng thái tuyến' }).waitFor();

    await checkVietnameseValidation();
    async function addStop(id, name) {
      await page.getByLabel('Mã điểm dừng').fill(id);
      await page.getByLabel('Tên điểm dừng').fill(name);
      await page.getByLabel('Địa chỉ').last().fill(`${name}, địa chỉ thử`);
      await page.getByLabel('Thời gian dừng (phút)').fill('5');
      await page.getByRole('button', { name: 'Thêm điểm dừng', exact: true }).click();
      await page.getByRole('status').filter({ hasText: 'Đã thêm điểm dừng' }).waitFor();
    }
    await addStop(ids.stopA, 'Điểm dừng A');
    await addStop(ids.stopB, 'Điểm dừng B');
    await page.locator('ol li').filter({ hasText: 'Điểm dừng A' }).getByRole('button', { name: 'Sửa' }).click();
    await page.getByLabel('Tên điểm dừng').fill('Điểm dừng A đã sửa');
    await page.getByRole('button', { name: 'Lưu điểm dừng' }).click();
    await page.getByRole('status').filter({ hasText: 'Đã cập nhật điểm dừng' }).waitFor();
    await page.locator('ol li').nth(1).getByRole('button', { name: '↑' }).click();
    await page.getByRole('status').filter({ hasText: 'Đã đổi thứ tự điểm dừng' }).waitFor();
    const ordered = await page.locator('ol li').allTextContents();
    assert(ordered[0].includes('Điểm dừng B') && ordered[1].includes('Điểm dừng A đã sửa'), ordered);
    await page.getByRole('button', { name: 'Thêm điểm dừng', exact: true }).click();
    await page.getByLabel('Mã điểm dừng').fill(ids.stopA);
    await page.getByLabel('Tên điểm dừng').fill('Trùng mã');
    await page.getByLabel('Địa chỉ').last().fill('Địa chỉ');
    await page.getByRole('button', { name: 'Thêm điểm dừng', exact: true }).click();
    await page.locator('p[role="alert"]').waitFor();
    await page.getByRole('button', { name: 'Bến xe', exact: true }).click();
    assert.equal(await page.locator('p[role="alert"]').count(), 0, 'Đổi tab phải xóa thông báo lỗi');
    console.log('PASS: Vietnamese required-field validation, optional fields, notification clearing, read-only route status');
    console.log('PASS: browser province, station, route status, stop update and ordering');
  } finally {
    if (browser) await browser.close();
    try {
      sql(`DELETE FROM phien_dang_nhap WHERE ma_nguoi_dung='${ids.user || ''}';`);
      sql(`DELETE FROM diem_dung WHERE ma_tuyen='${ids.route}'; DELETE FROM tuyen_xe WHERE ma_tuyen='${ids.route}'; DELETE FROM ben_xe WHERE ma_ben_xe IN ('${ids.stationA}','${ids.stationB}'); DELETE FROM tinh_thanh WHERE ma_tinh_thanh='${ids.city}';`);
      if (ids.user) sql(`DELETE FROM nguoi_dung WHERE ma_nguoi_dung='${ids.user}'`);
    } catch (error) { console.error('Dọn fixture tự động gặp lỗi:', error.message); process.exitCode = 1; }
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
