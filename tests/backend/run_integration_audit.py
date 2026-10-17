"""Run existing HTTP suites sequentially and retain logs/results for review."""
from datetime import datetime, timedelta, timezone
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'apps/backend/target/integration-audit'
SUITES = [
    ('accounts', 'account_auth_smoke.py'),
    ('employees', 'employee_assignment_smoke.py'),
    ('routes', 'api_smoke.py'),
    ('vehicles', 'vehicle_management_smoke.py'),
    ('trip-sale', 'trip_sale_smoke.py'),
    ('booking', 'booking_order_smoke.py'),
    ('pricing', 'booking_pricing_smoke.py'),
    ('promotions-paymethods', 'paymethod_discount_smoke.py'),
    ('payment', 'payment_vnpay_smoke.py'),
    ('refund', 'ticket_refund_smoke.py'),
]


def snapshot():
    result = {}
    for table in ('nha_xe', 'nhan_vien', 'nguoi_dung', 'xe', 'ghe', 'tuyen_xe', 'ben_xe'):
        statement = f"SELECT count(*)||':'||md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text)::text,'[]')) FROM {table} r"
        value = subprocess.check_output(['docker', 'exec', '-i', os.environ.get('DB_CONTAINER', 'datvexe-postgres'),
                                         'psql', '-U', 'datvexe_user', '-d', os.environ.get('DB_NAME', 'datvexe'),
                                         '-tA', '-v', 'ON_ERROR_STOP=1', '-c', statement], text=True)
        result[table] = value.strip()
    return result


OUT.mkdir(parents=True, exist_ok=True)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--only', nargs='+', choices=[name for name, _ in SUITES], help='Rerun selected suites after a targeted correction')
args = parser.parse_args()
selected = [(name, script) for name, script in SUITES if not args.only or name in args.only]
report_path = OUT / ('results.json' if not args.only else 'results-selected.json')
before = snapshot()
report = {'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
          'started_at': datetime.now(timezone(timedelta(hours=7))).isoformat(), 'before': before, 'suites': []}
for name, script in selected:
    print(f'START: {name}', flush=True)
    start = time.monotonic()
    log = OUT / (name + '.log')
    with log.open('wb') as output:
        run = subprocess.run([sys.executable, str(ROOT / 'tests/backend' / script)], cwd=ROOT,
                             env=os.environ | {'PYTHONUTF8': '1', 'PYTHONIOENCODING': 'utf-8'},
                             stdout=output, stderr=subprocess.STDOUT)
    item = {'name': name, 'status': 'PASS' if run.returncode == 0 else 'FAIL',
            'seconds': round(time.monotonic() - start, 1), 'exit_code': run.returncode,
            'log': str(log.relative_to(ROOT))}
    report['suites'].append(item)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"{item['status']}: {name} ({item['seconds']}s); {item['log']}", flush=True)
    if run.returncode:
        print(log.read_text(encoding='utf-8', errors='replace')[-1800:], flush=True)
report['after'] = snapshot()
report['core_data_preserved'] = report['after'] == before
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print('Core data preserved:', report['core_data_preserved'], flush=True)
sys.exit(0 if report['core_data_preserved'] and all(row['exit_code'] == 0 for row in report['suites']) else 1)
