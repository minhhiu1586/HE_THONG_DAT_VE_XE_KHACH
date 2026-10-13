import type { CityRecord, SearchOption, StationRecord } from './api';

export function normalizeLocation(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim().replace(/\s+/g, ' ');
}

export function groupSearchOptions(cities: CityRecord[], stations: StationRecord[]): SearchOption[] {
  const cityById = new Map(cities.map(city => [city.maTinhThanh, city.tenTinhThanh]));
  const options = new Map<string, SearchOption>();
  const add = (key: string, option: SearchOption) => {
    const current = options.get(key);
    if (current) current.aliases = [...new Set([...(current.aliases ?? [current.value]), option.value])].sort();
    else options.set(key, { ...option, aliases: [option.value] });
  };
  for (const city of cities) add('CITY:' + normalizeLocation(city.tenTinhThanh), { value: 'CITY:' + city.maTinhThanh, label: city.tenTinhThanh, kind: 'CITY' });
  for (const station of stations) {
    const city = cityById.get(station.maTinhThanh) ?? '';
    const label = station.tenBenXe + (city ? ' · ' + city : '');
    add('STATION:' + normalizeLocation(label) + ':' + normalizeLocation(station.diaChi), { value: 'STATION:' + station.maBenXe, label, detail: station.diaChi, cityValue: 'CITY:' + station.maTinhThanh, kind: 'STATION' });
  }
  return [...options.values()].map(option => {
    const aliases = option.aliases ?? [option.value];
    const preferred = aliases.find(value => !/:(DEMO_|T06_)/.test(value)) ?? aliases[0];
    return { ...option, value: preferred };
  }).sort((a, b) => a.kind === b.kind ? a.label.localeCompare(b.label, 'vi') : a.kind === 'CITY' ? -1 : 1);
}
