// Chạy từ root: node members/LeNguyenThanhHien/testing/frontend/management_smoke.cjs
// Kiểm tra FE bằng API giả lập; không tạo/xóa dữ liệu backend hoặc xác nhận nghiệp vụ DB.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const base = process.env.FRONTEND_URL || 'http://127.0.0.1:3000';
const cities = [
  { maTinhThanh: 'HCM', tenTinhThanh: 'Hồ Chí Minh', khuVuc: 'Miền Nam' },
  { maTinhThanh: 'CT', tenTinhThanh: 'Cần Thơ', khuVuc: 'Miền Nam' },
];
const stations = cities.map((city, index) => ({
  maBenXe: `BX${index + 1}`,
  tenBenXe: `Bến ${index + 1}`,
  maTinhThanh: city.maTinhThanh,
  diaChi: 'Địa chỉ thử',
  soDienThoai: null,
  trangThai: 'HOAT_DONG',
}));
const owners = [{ maNhaXe: 'NX1', tenNhaXe: 'Nhà xe thử', trangThai: 'HOAT_DONG' }];
const kinds = [{ maLoaiXe: 'L2', tenLoaiXe: 'Xe 2 chỗ', soChoNgoi: 2, soTang: 1, moTa: null }];
const route = {
  maTuyen: 'R1',
  tenTuyen: 'Hồ Chí Minh - Cần Thơ',
  tramKhoiHanh: 'BX1',
  tramDen: 'BX2',
  maNhaXe: 'NX1',
  thoiGianKhoiHanh: '08:00:00',
  khoangCachKM: 170,
  thoiGianDuKien: 6,
  giaCoBan: 100000,
  moTa: null,
  trangThai: 'DANG_KHAI_THAC',
  tramTrungGian: [
    { maDiemDung: 'S1', tenDiemDung: 'Điểm một', diaChi: 'Địa chỉ 1', thuTu: 1, thoiGianDung: 5, loaiDiemDung: 'CA_HAI' },
    { maDiemDung: 'S2', tenDiemDung: 'Điểm hai', diaChi: 'Địa chỉ 2', thuTu: 2, thoiGianDung: 5, loaiDiemDung: 'CA_HAI' },
  ],
};
const vehicle = {
  maXe: 'X1',
  bienSo: '51B-123.45',
  maLoaiXe: 'L2',
  maNhaXe: 'NX1',
  soChoNgoi: 2,
  namSanXuat: 2025,
  ngayDangKiem: '2026-01-01',
  hanDangKiem: '2035-01-01',
  dungTichXang: null,
  tocDo: null,
  trangThai: 'SAN_SANG',
  dangKiemConHieuLuc: true,
  soDoHopLe: true,
  coTheSuaSoDo: true,
  soGheThucTe: 2,
};
function draft(code) {
  return {
    maChuyen: code,
    maTuyen: 'R1',
    tenTuyen: route.tenTuyen,
    maNhaXe: 'NX1',
    tenNhaXe: owners[0].tenNhaXe,
    maXe: 'X1',
    bienSo: vehicle.bienSo,
    ngayKhoiHanh: '2030-01-01',
    gioKhoiHanh: '08:00:00',
    gioDenDuKien: '2030-01-01T14:00:00',
    giaVeDuKien: '100000',
    trangThai: 'NHAP',
    ngungNhanDatVeLuc: '2030-01-01T07:00:00',
    phutDungNhanDatVe: 60,
    coTheDatVe: false,
    daHetHanDatVe: false,
    coTheSua: true,
    coTheXoa: true,
    coTheHuy: true,
    nhanVienPhanCong: [],
  };
}

(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium',
    headless: true,
    args: ['--no-sandbox'],
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('dialog', dialog => dialog.accept());
  let seats = [
    { maGhe: 'G1', viTri: '01', tang: 1, trangThai: 'HOAT_DONG' },
    { maGhe: 'G2', viTri: '02', tang: 1, trangThai: 'HOAT_DONG' },
  ];
  const trips = [draft('C1'), draft('C2')];
  const calls = [];
  let role = 'ADMIN',
    denySale = false,
    denyLayout = false,
    failCitySearch = false;
  let cancellationPending = true;
  const violations = [];
  await page.route('**/api/**', async interception => {
    const req = interception.request();
    const url = new URL(req.url()),
      path = url.pathname.slice(4),
      method = req.method();
    const body = req.postDataJSON();
    calls.push({ path, method, body, query: url.search });
    const respond = (data, status = 200) => interception.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
    if (method !== 'GET' && req.headers()['x-csrf-token'] !== 'test-csrf') violations.push(`${method} ${path} thiếu CSRF`);
    if (path === '/tai-khoan/me')
      return respond({
        nguoiDung: { maNguoiDung: 'U1', hoTen: 'Người thử', email: 'ui@example.test', loaiTaiKhoan: role, trangThai: 'HOAT_DONG' },
        csrfToken: 'test-csrf',
        hetHan: '2035-01-01T00:00:00Z',
      });
    if (path === '/tinh-thanh') {
      if (failCitySearch && url.searchParams.has('q')) return respond({ message: 'Không tải được tỉnh/thành.' }, 503);
      return respond(url.searchParams.get('q') ? [] : cities);
    }
    if (path === '/ben-xe') return respond(stations);
    if (path === '/tuyen-xe') {
      if (method === 'POST') return respond({ ...route, ...body }, 201);
      return respond([route]);
    }
    if (path === '/tuyen-xe/R1/diem-dung/sap-xep') {
      const old = [...route.tramTrungGian];
      route.tramTrungGian = body.maDiemDungTheoThuTu.map((id, index) => ({
        ...old.find(stop => stop.maDiemDung === id),
        thuTu: index + 1,
      }));
      return respond(route.tramTrungGian);
    }
    if (path === '/tuyen-xe/R1/diem-dung' && method === 'POST') {
      route.tramTrungGian.push(body);
      return respond(body, 201);
    }
    if (path === '/xe/pham-vi') return respond(owners);
    if (path === '/loai-xe') return respond(url.searchParams.get('q') ? [] : kinds);
    if (path === '/xe') return respond([vehicle]);
    if (path === '/xe/X1') return respond(vehicle);
    if (path === '/xe/X1/ghe') return respond(seats);
    if (path === '/xe/X1/so-do-ghe') {
      if (denyLayout) return respond({ message: 'Sơ đồ đã có lịch sử vé/giữ chỗ; không được thay đổi.' }, 409);
      seats = body.danhSachGhe;
      return respond(seats);
    }
    if (path === '/quan-ly/chuyen-xe') {
      if (method === 'POST') {
        const item = { ...draft(body.maChuyen), ...body };
        trips.push(item);
        return respond(item, 201);
      }
      return respond({ items: trips, page: 1, totalPages: 1, totalItems: trips.length });
    }
    const match = path.match(/^\/quan-ly\/chuyen-xe\/([^/]+)(?:\/(.+))?$/);
    if (match) {
      const item = trips.find(trip => trip.maChuyen === match[1]),
        action = match[2];
      if (!item) return respond({ message: 'Không tìm thấy chuyến.' }, 404);
      if (!action) return respond(item);
      if (action === 'dieu-kien-mo-ban')
        return respond({
          duDieuKien: !denySale,
          ngungNhanDatVeLuc: item.ngungNhanDatVeLuc,
          dieuKien: [{ maDieuKien: 'XE', tenDieuKien: 'Xe và sơ đồ ghế', dat: !denySale, lyDo: denySale ? 'Xe đang bảo trì.' : null }],
        });
      if (action === 'vong-doi')
        return respond({
          maChuyen: item.maChuyen,
          trangThai: item.trangThai,
          tongQuanHoan: {
            tongSoVeBiAnhHuong: 0,
            soVeCanHoan: 0,
            soVeDaHoan: 0,
            tongTienDuKien: '0',
            tongTienDaHoan: '0',
            soYeuCauThanhCong: 0,
            soYeuCauCanTheoDoi: 0,
          },
          lyLich: [],
          thongBao: [],
          khachBiAnhHuong:
            item.trangThai === 'DA_HUY'
              ? [
                  {
                    maDon: 'D1',
                    trangThaiXuLy: cancellationPending ? 'CAN_DOI_SOAT' : 'HOAN_TAT',
                    lyDoXuLy: '',
                    lyDo: 'Xe gặp sự cố',
                    soVeConHieuLuc: 0,
                    veBiAnhHuong: [],
                    yeuCauHoan: [],
                  },
                ]
              : [],
        });
      if (action === 'mo-ban' && denySale) return respond({ message: 'Xe đang bảo trì.' }, 409);
      if (action === 'xu-ly-hoan') cancellationPending = false;
      else item.trangThai = { 'mo-ban': 'DANG_MO_BAN', 'bat-dau': 'DANG_CHAY', 'hoan-thanh': 'HOAN_TAT', huy: 'DA_HUY' }[action];
      item.coTheDatVe = item.trangThai === 'DANG_MO_BAN';
      item.coTheSua = item.trangThai === 'NHAP';
      item.coTheHuy = ['NHAP', 'DANG_MO_BAN'].includes(item.trangThai);
      return respond(item);
    }
    if (/^\/chuyen-xe\/[^/]+\/hanh-khach$/.test(path))
      return respond({
        maChuyen: path.split('/')[2],
        tenTuyen: route.tenTuyen,
        tenNhaXe: owners[0].tenNhaXe,
        bienSo: vehicle.bienSo,
        ngayKhoiHanh: '2030-01-01',
        gioKhoiHanh: '08:00:00',
        tongVe: 0,
        daXuatVe: 0,
        daHoan: 0,
        daHuy: 0,
        danhSachHanhKhach: [],
      });
    violations.push(`API chưa được khai báo: ${method} ${path}`);
    return respond({ message: 'Unexpected mock endpoint' }, 500);
  });
  const button = name => page.getByRole('button', { name, exact: true });
  const section = heading => page.locator('section').filter({ has: page.getByRole('heading', { name: heading, exact: true }) });
  const latest = (path, method) => calls.filter(call => call.path === path && call.method === method).at(-1);
  try {
    // Form mới phải gửi null khi chưa gán xe và giữ giờ đến được tính từ thời lượng tuyến.
    await page.goto(`${base}/quan-tri/chuyen-xe`);
    await button('Tạo chuyến nháp').waitFor();
    await page.getByLabel('Mã chuyến', { exact: true }).fill('UI_NEW');
    await page.getByLabel('Tuyến xe', { exact: true }).selectOption('R1');
    await page.getByLabel('Ngày khởi hành', { exact: true }).fill('2030-01-01');
    await page.getByLabel('Giờ khởi hành', { exact: true }).fill('08:00');
    await button('Tạo chuyến nháp').click();
    await page.getByRole('heading', { name: 'Chi tiết chuyến · UI_NEW', exact: true }).waitFor();
    assert.equal(latest('/quan-ly/chuyen-xe', 'POST').body.maXe, null);
    assert.equal(latest('/quan-ly/chuyen-xe', 'POST').body.gioDenDuKien, '2030-01-01T14:00:00');
    const details = section('Chi tiết chuyến · UI_NEW');
    denySale = true; // Thay đổi phía server sau khi FE đã tải bảng điều kiện.
    await details.getByRole('button', { name: 'Mở bán chuyến', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'Xe đang bảo trì.' }).waitFor();
    assert.equal(trips.at(-1).trangThai, 'NHAP');
    assert.equal(await details.getByRole('button', { name: 'Mở bán chuyến', exact: true }).isDisabled(), true);
    denySale = false;
    await details.getByRole('button', { name: 'Kiểm tra lại điều kiện', exact: true }).click();
    await details.getByRole('button', { name: 'Mở bán chuyến', exact: true }).click();
    await details.getByRole('button', { name: 'Bắt đầu chuyến', exact: true }).click();
    await details.getByRole('button', { name: 'Hoàn tất chuyến', exact: true }).click();
    await page.getByRole('status').filter({ hasText: 'Đã hoàn tất chuyến.' }).waitFor();
    assert.equal(trips.at(-1).trangThai, 'HOAN_TAT');
    await details.getByRole('button', { name: 'Xem danh sách hành khách', exact: true }).click();
    await page.getByText('Chưa có hành khách nào đặt vé trên chuyến này.', { exact: true }).waitFor();
    await page.getByRole('dialog').locator('footer').getByRole('button', { name: 'Đóng', exact: true }).click();
    const row = page.getByRole('row').filter({ hasText: 'C2' });
    await row.getByRole('button', { name: 'Chi tiết / mở bán', exact: true }).click();
    const cancelDetails = section('Chi tiết chuyến · C2');
    await cancelDetails.getByRole('button', { name: 'Hủy chuyến', exact: true }).click();
    await page.getByLabel('Lý do hủy chuyến', { exact: true }).fill('Xe gặp sự cố');
    await button('Xác nhận hủy chuyến').click();
    await cancelDetails.getByRole('button', { name: 'Tiếp tục xử lý', exact: true }).click();
    await page.getByRole('status').filter({ hasText: 'Đã cập nhật trạng thái xử lý hoàn/đối soát.' }).waitFor();
    assert.deepEqual(latest('/quan-ly/chuyen-xe/C2/huy', 'POST').body, { lyDo: 'Xe gặp sự cố' });
    assert.equal(cancellationPending, false);
    console.log('PASS trips: draft payload, sale rejection/refresh, open/start/complete, passenger modal, cancel/retry.');

    // Lọc danh mục không được làm mất lựa chọn trong form và sơ đồ ghế.
    await page.goto(`${base}/quan-tri/xe`);
    await button('Loại xe').click();
    await page.getByLabel('Tìm loại xe', { exact: true }).fill('Không có');
    await button('Tìm loại xe').click();
    await button('Xe và ghế').click();
    await page.locator('select[data-validation-label="loại xe"]').selectOption('L2');
    assert.equal(await page.getByLabel('Số ghế', { exact: true }).inputValue(), '2');
    await button('Chi tiết / ghế').click();
    await page.getByLabel('Mã/vị trí ghế 1', { exact: true }).fill('A1');
    await button('Lưu sơ đồ ghế').click();
    await page.getByRole('status').filter({ hasText: 'Đã lưu sơ đồ ghế.' }).waitFor();
    assert.deepEqual(
      seats.map(seat => seat.maGhe),
      ['G1', 'G2'],
    );
    assert.equal(seats[0].viTri, 'A1');
    denyLayout = true;
    await page.getByLabel('Mã/vị trí ghế 1', { exact: true }).fill('A2');
    await button('Lưu sơ đồ ghế').click();
    await page.getByRole('alert').filter({ hasText: 'Sơ đồ đã có lịch sử vé/giữ chỗ' }).waitFor();
    assert.equal(seats[0].viTri, 'A1');
    await button('Tải lại sơ đồ').click();
    await page.waitForFunction(() => document.querySelector('[aria-label="Mã/vị trí ghế 1"]')?.value === 'A1');
    console.log('PASS vehicles: independent type catalog, whole-layout payload, rejected save, reload persisted layout.');

    await page.goto(`${base}/quan-tri/tuyen-duong`);
    await button('Tỉnh/thành').click();
    await page.getByRole('textbox', { name: 'Tìm tỉnh thành', exact: true }).fill('Không có');
    await button('Tìm').click();
    await page.getByText('Chưa có tỉnh/thành phù hợp.', { exact: true }).waitFor();
    failCitySearch = true;
    await button('Tìm').click();
    await page.getByRole('alert').filter({ hasText: 'Không tải được tỉnh/thành.' }).waitFor();
    failCitySearch = false;
    await button('Bến xe').click();
    const stationForm = section('Bến xe').locator('form');
    assert.equal(await stationForm.locator('select[data-validation-label="tỉnh/thành"]').locator('option').count(), 3);
    await button('Tuyến xe').click();
    await page.locator('select[data-validation-label="bến khởi hành"]').selectOption('BX1');
    await section('Tạo tuyến xe').locator('select[data-validation-label="bến đến"]').selectOption('BX2');
    await page.getByLabel('Mã tuyến', { exact: true }).fill('UI_ROUTE');
    await page.getByLabel('Tên tuyến', { exact: true }).fill('Tuyến thử');
    await page.getByLabel('Thời lượng dự kiến (giờ)', { exact: true }).fill('6');
    await page.getByLabel('Giá cơ bản (đồng)', { exact: true }).fill('120000');
    await section('Tạo tuyến xe')
      .locator('form')
      .evaluate(form => {
        form.requestSubmit();
        form.requestSubmit();
      });
    await page.getByRole('status').filter({ hasText: 'Đã tạo tuyến xe.' }).waitFor();
    assert.equal(calls.filter(call => call.path === '/tuyen-xe' && call.method === 'POST').length, 1);
    const savedRoute = latest('/tuyen-xe', 'POST').body;
    assert.equal(savedRoute.giaCoBan, 120000);
    assert.equal(savedRoute.thoiGianDuKien, 6);
    assert.equal(savedRoute.khoangCachKM, null);
    assert.equal(savedRoute.moTa, null);
    await button('↑').last().click();
    await page.getByRole('status').filter({ hasText: 'Đã đổi thứ tự điểm dừng.' }).waitFor();
    assert.deepEqual(latest('/tuyen-xe/R1/diem-dung/sap-xep', 'PUT').body, { maDiemDungTheoThuTu: ['S2', 'S1'] });
    await page.getByLabel('Mã điểm dừng', { exact: true }).fill('S3');
    await page.getByLabel('Tên điểm dừng', { exact: true }).fill('Điểm ba');
    // Giới hạn locator vào panel điểm dừng để tránh nhãn địa chỉ của form khác.
    await section('Điểm dừng · R1').getByLabel('Địa chỉ', { exact: true }).fill('Địa chỉ 3');
    await button('Thêm điểm dừng').click();
    await page.getByRole('status').filter({ hasText: 'Đã thêm điểm dừng.' }).waitFor();
    assert.equal(latest('/tuyen-xe/R1/diem-dung', 'POST').body.thuTu, 3);
    assert.equal(latest('/tuyen-xe/R1/diem-dung', 'POST').body.thoiGianDung, 0);
    console.log('PASS routes: independent catalogs, filter API error, route payload, reorder IDs, stop creation payload.');
    role = 'KHACH_HANG';
    await page.goto(`${base}/quan-tri/xe`);
    await page.getByRole('alert').filter({ hasText: 'Bạn không có quyền truy cập khu vực quản lý.' }).waitFor();
    assert.deepEqual(violations, []);
    assert.deepEqual(errors, []);
    console.log('PASS auth: customer blocked; write requests include CSRF; no browser exceptions or unexpected APIs.');
  } catch (error) {
    console.error('Browser exceptions:', errors);
    console.error('Page:', await page.locator('body').innerText());
    throw error;
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
