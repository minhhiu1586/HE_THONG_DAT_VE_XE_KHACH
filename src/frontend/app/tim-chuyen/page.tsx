import SearchResults from '@/components/Search/SearchResults';
import CompactSearch from '@/components/Search/CompactSearch';
import styles from '@/components/Search/Search.module.css';
import { getSearchOptions, searchTrips, type SearchOption, type SearchTrip } from '@/lib/api';

function todayInVietnam() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

function resolveLocation(value: string, options: SearchOption[]) {
  if (/^(CITY|STATION):/.test(value)) return value;
  const byCode = options.find(option => option.value.slice(option.value.indexOf(':') + 1).toLowerCase() === value.trim().toLowerCase());
  if (byCode) return byCode.value;
  const byName = options.find(option => option.label.toLowerCase() === value.trim().toLowerCase());
  return byName?.value ?? value.trim();
}

export default async function SearchPage({ searchParams }: { searchParams: Promise<{ [key: string]: string | undefined }> }) {
  const params = await searchParams;
  const initialFrom = params.from ?? '';
  const initialTo = params.to ?? '';
  const date = params.date || todayInVietnam();
  let options: SearchOption[] = [];
  let optionsError = '';
  try {
    options = await getSearchOptions();
  } catch (error) {
    optionsError = error instanceof Error ? error.message : 'Không thể kết nối máy chủ.';
  }

  let trips: SearchTrip[] = [];
  let searchError = '';
  if (initialFrom && initialTo) {
    try {
      trips = await searchTrips(resolveLocation(initialFrom, options), resolveLocation(initialTo, options), date);
    } catch (error) {
      searchError = error instanceof Error ? error.message : 'Không thể tìm chuyến lúc này.';
    }
  }

  const selectedFrom = initialFrom ? resolveLocation(initialFrom, options) : '';
  const selectedTo = initialTo ? resolveLocation(initialTo, options) : '';

  return <main className={styles.searchPage}>
    <div className="container">
      <h1 className={styles.pageTitle}>Tìm chuyến xe</h1>
      <CompactSearch key={`${selectedFrom}|${selectedTo}|${date}`} initialFrom={selectedFrom} initialTo={selectedTo} initialDate={date} options={options} />
      {optionsError && <p className={styles.error} role="alert">Không tải được danh mục địa điểm: {optionsError}</p>}
      {searchError && <p className={styles.error} role="alert">Không tìm được chuyến: {searchError}</p>}
      {!initialFrom || !initialTo ? <p className={styles.info}>Chọn điểm đi, điểm đến và ngày đi để xem các chuyến đang mở bán.</p> : !searchError && <SearchResults key={`${selectedFrom}|${selectedTo}|${date}`} initialTrips={trips} />}
    </div>
  </main>;
}
