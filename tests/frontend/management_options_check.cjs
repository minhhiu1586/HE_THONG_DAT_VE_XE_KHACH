// Run from repository root: node tests/frontend/management_options_check.cjs
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const frontend = path.resolve(__dirname, '../../apps/frontend');
const ts = require('typescript');
const compiled = ts.transpileModule(fs.readFileSync(path.resolve(__dirname, '../../shared/frontend/src/lib/management-options.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const mod = { exports: {} };
new Function('exports', 'module', compiled)(mod.exports, mod);
const routes = [
  { maTuyen: 'B', tenTuyen: 'TP. Hồ Chí Minh - Cần Thơ', maNhaXe: 'TB' },
  { maTuyen: 'A', tenTuyen: 'TP. Hồ Chí Minh - Cần Thơ', maNhaXe: 'FUTA' },
  { maTuyen: 'D', tenTuyen: 'Đà Lạt - TP. Hồ Chí Minh', maNhaXe: 'TB' },
  { maTuyen: 'C', tenTuyen: 'Cần Thơ - TP. Hồ Chí Minh', maNhaXe: 'FUTA' },
  { maTuyen: 'A', tenTuyen: 'TP. Hồ Chí Minh - Cần Thơ', maNhaXe: 'FUTA' },
  { maTuyen: 'E', tenTuyen: 'TP. Hồ Chí Minh - Cần Thơ', maNhaXe: 'FUTA' },
];
const owners = [{ maNhaXe: 'FUTA', tenNhaXe: 'Phương Trang' }, { maNhaXe: 'TB', tenNhaXe: 'Thành Bưởi' }];
const result = mod.exports.routeChoices(routes, owners);
assert.equal(result.length, 5);
assert.equal(new Set(result.map(item => item.label)).size, 5);
assert.equal(new Set(result.map(item => item.value)).size, 5);
assert.deepEqual(result.map(item => item.value), ['C', 'D', 'A', 'E', 'B']);
assert.equal(routes.length, 6);
assert(result.find(item => item.value === 'B').label.includes('Thành Bưởi'));
const filters = mod.exports.routeFilterChoices(routes);
assert.deepEqual(filters.map(item => item.label), ['Cần Thơ - TP. Hồ Chí Minh', 'Đà Lạt - TP. Hồ Chí Minh', 'TP. Hồ Chí Minh - Cần Thơ']);
assert.equal(filters[2].value, 'A,B,E');
console.log('PASS: duplicate IDs removed; distinct operators and same-operator routes preserved; Vietnamese A-Z; input unchanged.');
