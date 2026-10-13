"""Verify reset, backup recovery, rollback and reload using disposable PostgreSQL databases."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[2]
PREFIX = 'seed_reset_' + uuid.uuid4().hex[:10]
DATABASES = [PREFIX, PREFIX + '_restore']


def sql(database, statement):
    result = subprocess.run(['docker', 'exec', '-i', 'datvexe-postgres', 'psql', '-U',
                             'datvexe_user', '-d', database, '-v', 'ON_ERROR_STOP=1', '-tA'],
                            input=statement, text=True, encoding='utf-8', capture_output=True, check=True)
    return result.stdout.strip()


try:
    for database in DATABASES:
        sql('postgres', f'CREATE DATABASE "{database}"')
    sql(PREFIX, "CREATE TABLE legacy_probe (value text); INSERT INTO legacy_probe VALUES ('old data');")
    subprocess.run([sys.executable, str(ROOT / 'scripts/load_project_data.py'), '--database', PREFIX,
                    '--reset', '--load-only'], cwd=ROOT, check=True)
    for table, count in {'nhan_vien': 35, 'nguoi_dung': 47, 'nha_xe': 5, 'xe': 10,
                         'ghe': 320, 'tuyen_xe': 20, 'chuyen_xe': 210, 'phan_cong_chuyen_xe': 490}.items():
        assert sql(PREFIX, f'SELECT count(*) FROM {table}') == str(count), table
    assert sql(PREFIX, "SELECT to_regclass('public.legacy_probe') IS NULL") == 't'
    backup = sorted((ROOT / 'src/backend/target/database-backups').glob(PREFIX + '_*.dump'))[-1]
    subprocess.run(['docker', 'exec', '-i', 'datvexe-postgres', 'pg_restore', '-U', 'datvexe_user',
                    '-d', DATABASES[1], '--exit-on-error'], input=backup.read_bytes(), check=True)
    assert sql(DATABASES[1], 'SELECT value FROM legacy_probe') == 'old data'
    subprocess.run([sys.executable, str(ROOT / 'scripts/load_project_data.py'), '--database', PREFIX,
                    '--load-only'], cwd=ROOT, check=True)
    assert sql(PREFIX, 'SELECT count(*) FROM nhan_vien') == '35'

    # Force a SQL failure after DROP SCHEMA; the same transaction must preserve old data.
    spec = importlib.util.spec_from_file_location('loader', ROOT / 'scripts/load_project_data.py')
    loader = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loader)
    with tempfile.TemporaryDirectory(prefix='seed-reset-test-') as temp:
        loader.ROOT = Path(temp)
        schema_dir = loader.ROOT / 'docs/database/schema'
        seed_dir = loader.ROOT / 'docs/database/seed'
        schema_dir.mkdir(parents=True)
        seed_dir.mkdir(parents=True)
        (schema_dir / '001_invalid.sql').write_text('SELECT * FROM nonexistent_reset_test_table;', encoding='utf-8')
        for seed in loader.SEEDS:
            (seed_dir / seed).write_text('', encoding='utf-8')
        sys.argv = ['loader', '--database', PREFIX, '--reset', '--load-only']
        try:
            loader.main()
        except RuntimeError:
            pass
        else:
            raise AssertionError('Invalid schema unexpectedly succeeded')
    assert sql(PREFIX, 'SELECT count(*) FROM nhan_vien') == '35'
    print('PASS: canonical counts, legacy removal, verified backup restore, reload and SQL rollback.')
finally:
    for database in DATABASES:
        sql('postgres', f'DROP DATABASE IF EXISTS "{database}" WITH (FORCE)')
