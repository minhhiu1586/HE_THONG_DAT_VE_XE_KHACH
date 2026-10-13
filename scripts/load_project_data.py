"""Load the project dataset without resetting an existing database; publish through T12 APIs."""
import argparse
import http.cookiejar
from http.client import HTTPException
import json
import os
from pathlib import Path
import re
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener, HTTPCookieProcessor

ROOT = Path(__file__).resolve().parents[1]
SEEDS = ('001_catalog.sql', '002_accounts_staff_fleet.sql', '003_routes_trips.sql')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', default='datvexe')
    parser.add_argument('--container', default='datvexe-postgres')
    parser.add_argument('--user', default='datvexe_user')
    parser.add_argument('--api', default='http://localhost:8080/api')
    parser.add_argument('--activate', action='store_true', help='Configure .env and restart the existing compose services')
    parser.add_argument('--load-only', action='store_true', help='Load schema/data; leave trips unpublished')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', args.database):
        parser.error('Invalid database name')

    def sql(statement, database=None):
        command = ['docker', 'exec', '-e', 'PGTZ=Asia/Ho_Chi_Minh', '-i', args.container, 'psql', '-U', args.user, '-d', database or args.database, '-v', 'ON_ERROR_STOP=1', '-tA']
        result = subprocess.run(command, input=statement, text=True, encoding='utf-8', capture_output=True, check=False)
        if result.returncode:
            raise RuntimeError(result.stderr)
        return result.stdout.strip()

    exists = sql(f"SELECT count(*) FROM pg_database WHERE datname='{args.database}'", 'postgres')
    if exists != '1':
        sql(f'CREATE DATABASE "{args.database}"', 'postgres')
    schema = '\n'.join((ROOT / 'docs/database/schema' / file).read_text(encoding='utf-8') for file in
                       ('001_route_trip.sql', '002_vehicle_alignment.sql', '003_account_auth.sql'))
    data = '\n'.join((ROOT / 'docs/database/seed' / file).read_text(encoding='utf-8') for file in SEEDS)
    data = re.sub(r'^(BEGIN|COMMIT);\s*$', '', data, flags=re.M)
    sql('BEGIN;\n' + schema + '\n' + data + '\nCOMMIT;')
    print('Loaded project schema/data; existing rows and account passwords preserved.', flush=True)
    if args.load_only:
        return
    if args.activate:
        env_path = ROOT / '.env'
        content = env_path.read_text(encoding='utf-8') if env_path.exists() else ''
        backup = ROOT / 'src/backend/target/project-data.env.previous'
        backup.parent.mkdir(parents=True, exist_ok=True)
        if not backup.exists():
            backup.write_text(content, encoding='utf-8')
        for key, value in [('POSTGRES_DB', args.database), ('DATABASE_URL', f'jdbc:postgresql://localhost:5432/{args.database}')]:
            line = key + '=' + value
            content = re.sub(r'^' + key + r'=.*$', lambda _: line, content, flags=re.M) if re.search(r'^' + key + '=', content, re.M) else content.rstrip() + '\n' + line + '\n'
        env_path.write_text(content.lstrip('\n'), encoding='utf-8')
        subprocess.run(['docker', 'compose', 'up', '-d'], cwd=ROOT, check=True)

    opener = build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))
    csrf = None

    def request(method, path, body=None):
        headers = {'Content-Type': 'application/json', 'X-Requested-With': 'datvexe'}
        if csrf:
            headers['X-CSRF-Token'] = csrf
        req = Request(args.api.rstrip('/') + path, method=method, headers=headers,
                      data=None if body is None else json.dumps(body).encode('utf-8'))
        try:
            with opener.open(req, timeout=30) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except HTTPError as error:
            message = error.read().decode('utf-8', errors='replace')
            raise RuntimeError(f'{method} {path}: HTTP {error.code} {message[:500]}') from error

    for attempt in range(30):
        try:
            session = request('POST', '/auth/dang-nhap', {'email': 'admin1@datvexe.test', 'matKhau': os.environ.get('PROJECT_ADMIN_PASSWORD', 'DatVeXe2026!')})
            csrf = session['csrfToken']
            break
        except (URLError, RuntimeError, HTTPException):
            if attempt == 29:
                raise RuntimeError('Project login failed. Verify backend database, startup and admin password.')
            time.sleep(2)

    # No direct UPDATE to sale status: T12 checks all vehicle/staff/route conditions.
    trips = sql("SELECT ma_chuyen FROM chuyen_xe WHERE ma_chuyen ~ '^CX_(FUTA|TB|KUMHO|HL|CT)_[12]_[0-9]{8}_[12]$' AND trang_thai IN ('NHAP','CHUA_KHOI_HANH') AND ngay_khoi_hanh>CURRENT_DATE ORDER BY ngay_khoi_hanh,gio_khoi_hanh,ma_chuyen").splitlines()
    try:
        for index, trip in enumerate(trips, 1):
            request('POST', '/quan-ly/chuyen-xe/' + trip + '/mo-ban')
            if index % 10 == 0 or index == len(trips):
                print(f'Published {index}/{len(trips)} trips through T12.', flush=True)
    finally:
        if csrf:
            request('POST', '/auth/dang-xuat')
    print('Project data ready. Run tests/backend/project_data_check.py to verify.', flush=True)


if __name__ == '__main__':
    main()
