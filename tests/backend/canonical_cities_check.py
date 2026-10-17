# CSDL thử riêng được tạo rồi xóa; không sửa CSDL đang dùng.
import subprocess,pathlib,os
root=pathlib.Path(__file__).resolve().parents[2]
container=os.environ.get('DB_CONTAINER','datvexe-postgres')
user=os.environ.get('DB_USER','datvexe_user')
db='city_check_'+str(os.getpid())
def run(args,**kw):return subprocess.run(args,check=True,text=True,capture_output=True,**kw)
def sql(s):return run(['docker','exec','-i',container,'psql','-U',user,'-d',db,'-v','ON_ERROR_STOP=1','-Atq'],input=s).stdout.strip()
run(['docker','exec',container,'createdb','-U',user,db])
try:
 for p in sorted((root/'database/migrations').glob('*.sql')):
  if p.name.startswith('017'):continue
  sql(p.read_text())
 seed=(root/'database/seed/001_catalog.sql').read_text()
 sql(seed)
 sql("""INSERT INTO tinh_thanh SELECT 'DEMO_'||ma_tinh_thanh,ten_tinh_thanh,khu_vuc FROM tinh_thanh WHERE ma_tinh_thanh IN ('CT','HCM','KH','LD');
 INSERT INTO tinh_thanh SELECT 'T06_'||ma_tinh_thanh,ten_tinh_thanh,khu_vuc FROM tinh_thanh WHERE ma_tinh_thanh IN ('DN','HN');
 UPDATE ben_xe SET ma_tinh_thanh='DEMO_'||ma_tinh_thanh WHERE ma_tinh_thanh IN ('CT','HCM','KH','LD');
 UPDATE ben_xe SET ma_tinh_thanh='T06_'||ma_tinh_thanh WHERE ma_tinh_thanh IN ('DN','HN');
 INSERT INTO tinh_thanh VALUES ('OLD_X','Tỉnh riêng','X'),('X','Tỉnh riêng','X');
 """)
 before=sql('SELECT ma_ben_xe,ten_ben_xe,dia_chi FROM ben_xe ORDER BY ma_ben_xe;')
 migration=(root/'database/migrations/017_canonical_cities.sql').read_text()
 sql('BEGIN;'+migration+'COMMIT;')
 assert sql('SELECT count(*) FROM tinh_thanh;')=='8'
 assert sql("SELECT count(*) FROM ben_xe WHERE ma_tinh_thanh ~ '^(DEMO_|T06_)';")=='0'
 assert before==sql('SELECT ma_ben_xe,ten_ben_xe,dia_chi FROM ben_xe ORDER BY ma_ben_xe;')
 sql(seed);sql('BEGIN;'+migration+'COMMIT;')
 assert sql('SELECT count(*) FROM tinh_thanh;')=='8'
 try:sql("INSERT INTO tinh_thanh VALUES ('DUP','  can   tho  ',NULL);")
 except subprocess.CalledProcessError as e:assert 'uq_tinh_thanh_name_key' in e.stderr
 else:raise AssertionError('duplicate accepted')
 # Một tỉnh chỉ có mã demo cũng được chuyển về mã chuẩn.
 sql("DELETE FROM ben_xe WHERE ma_tinh_thanh='CT'; DELETE FROM tinh_thanh WHERE ma_tinh_thanh='CT'; INSERT INTO tinh_thanh VALUES ('DEMO_CT','Cần Thơ',NULL);")
 sql('BEGIN;'+migration+'COMMIT;')
 assert sql("SELECT ma_tinh_thanh FROM tinh_thanh WHERE ten_tinh_thanh='Cần Thơ';")=='CT'
 sql(seed)
 for name in ('002_accounts_staff_fleet.sql','003_routes_trips.sql'):
  sql((root/'database/seed'/name).read_text())
 # Có chuyến thật trong seed: chuyển bến sang mã cũ, rồi gộp lại.
 sql("DROP INDEX uq_tinh_thanh_name_key; INSERT INTO tinh_thanh SELECT 'DEMO_CT',ten_tinh_thanh,khu_vuc FROM tinh_thanh WHERE ma_tinh_thanh='CT'; UPDATE ben_xe SET ma_tinh_thanh='DEMO_CT' WHERE ma_tinh_thanh='CT';")
 snapshot="SELECT row_to_json(c)::text FROM chuyen_xe c ORDER BY ma_chuyen;"
 trips_before=sql(snapshot)
 assert trips_before
 sql('BEGIN;'+migration+'COMMIT;')
 assert trips_before==sql(snapshot)
 assert int(sql("SELECT count(*) FROM chuyen_xe c JOIN tuyen_xe t ON t.ma_tuyen=c.ma_tuyen JOIN ben_xe b ON b.ma_ben_xe=t.tram_khoi_hanh WHERE b.ma_tinh_thanh='CT';"))>0
 print('PASS: 6 duplicate groups merged, canonical-only and demo-only supported, stations preserved, seed reload and rerun safe, normalized duplicate rejected.')
finally:run(['docker','exec',container,'dropdb','-U',user,db])
