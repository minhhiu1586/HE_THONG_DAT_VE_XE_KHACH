const alphabet = new Intl.Collator('vi', { numeric: true, sensitivity: 'base' });
const normalizedName = (label: string) => label.normalize('NFC').toLocaleLowerCase('vi').replace(/\s+/g, ' ').trim();

export function routeFilterChoices(routes: { maTuyen: string; tenTuyen: string }[]) {
  const groups = new Map<string, { label: string; ids: string[] }>();
  for (const route of sortedUnique(routes, item => item.maTuyen, item => item.tenTuyen)) {
    const key = normalizedName(route.tenTuyen);
    const group = groups.get(key) ?? { label: route.tenTuyen.trim(), ids: [] };
    group.ids.push(route.maTuyen);
    groups.set(key, group);
  }
  return [...groups.values()].map(group => ({ label: group.label, value: group.ids.sort().join(',') }))
    .sort((a, b) => alphabet.compare(a.label, b.label));
}

// Deduplicate by identity: two operators may legitimately run routes with the same name.
export function sortedUnique<T>(items: T[], id: (item: T) => string, label: (item: T) => string): T[] {
  return [...new Map(items.map(item => [id(item), item])).values()]
    .sort((a, b) => alphabet.compare(label(a), label(b)) || alphabet.compare(id(a), id(b)));
}

export function routeChoices(
  routes: { maTuyen: string; tenTuyen: string; maNhaXe: string | null }[],
  operators: { maNhaXe: string; tenNhaXe: string }[],
) {
  const owners = new Map(operators.map(owner => [owner.maNhaXe, owner.tenNhaXe]));
  const choices = sortedUnique(routes, route => route.maTuyen, route => route.tenTuyen).map(route => ({
    value: route.maTuyen,
    label: `${route.tenTuyen.trim()} · ${route.maNhaXe ? owners.get(route.maNhaXe) ?? route.maNhaXe : 'Chưa có nhà xe'}`,
    disabled: !route.maNhaXe,
  }));
  const counts = new Map<string, number>();
  const normalized = normalizedName;
  choices.forEach(choice => counts.set(normalized(choice.label), (counts.get(normalized(choice.label)) ?? 0) + 1));
  // Preserve distinct routes of the same operator; show their codes only when needed to distinguish them.
  return choices.map(choice => ({ ...choice, label: counts.get(normalized(choice.label))! > 1 ? `${choice.label} · ${choice.value}` : choice.label }))
    .sort((a, b) => alphabet.compare(a.label, b.label) || alphabet.compare(a.value, b.value));
}
