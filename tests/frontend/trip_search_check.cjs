// Chạy từ thư mục gốc: node tests/frontend/trip_search_check.cjs
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const frontend = path.resolve(__dirname, '../../apps/frontend');
const ts = require('typescript');
const compiled = ts.transpileModule(fs.readFileSync(path.resolve(__dirname, '../../members/NguyenTheAn/frontend/src/booking/lib/api.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const mod = { exports: {} };
const calls = [];
const result = [{ maChuyen: 'CX_1' }, { maChuyen: 'CX_2' }];
new Function('exports', 'module', 'fetch', 'process', 'window', compiled)(
  mod.exports, mod,
  async url => {
    calls.push(url);
    return { ok: true, json: async () => url === '/api/tinh-thanh'
      ? [{ maTinhThanh: 'CT', tenTinhThanh: 'Cần Thơ' }]
      : result };
  }, { env: {} }, {}
);
(async () => {
  assert.deepEqual(await mod.exports.getSearchOptions(), [
    { value: 'CITY:CT', label: 'Cần Thơ', kind: 'CITY' },
  ]);
  assert.deepEqual(calls, ['/api/tinh-thanh']);
  calls.length = 0;
  assert.deepEqual(await mod.exports.searchTrips('CITY:CT', 'CITY:HCM', '2026-10-15'), result);
  assert.equal(calls.length, 1);
  const url = new URL(calls[0], 'http://localhost');
  assert.equal(url.pathname, '/api/chuyen-xe');
  assert.equal(url.searchParams.get('from'), 'CITY:CT');
  assert.equal(url.searchParams.get('to'), 'CITY:HCM');
  assert.equal(url.searchParams.get('date'), '2026-10-15');
  console.log('PASS: địa điểm chỉ gọi API tỉnh; tìm chuyến gọi đúng một request, giữ nguyên kết quả.');
})().catch(error => { console.error(error); process.exitCode = 1; });
